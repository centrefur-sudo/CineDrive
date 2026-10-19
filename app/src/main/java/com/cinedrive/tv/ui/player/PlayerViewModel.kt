package com.cinedrive.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind
import com.cinedrive.tv.domain.player.EpisodeNavigator
import com.cinedrive.tv.domain.player.PlaybackMath
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.playback.PlaybackEvent
import com.cinedrive.tv.playback.PlaybackState
import com.cinedrive.tv.playback.PlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.view.ViewGroup

/**
 * Player session orchestrator. Owns one [PlayerEngine] instance, bridges it
 * to the Room-backed library, and implements the spec's playback rules:
 *
 *  - resume from the saved progress point (engine applies it post-duration),
 *  - periodic progress persist while playing (every [SAVE_INTERVAL_MS]) plus
 *    a save on every pause and on exit — the continue-watching row stays true
 *    to within a few seconds no matter how playback ends,
 *  - the 90 %-watched rule ([PlaybackMath.isWatched]) — watched is sticky:
 *    once set it never un-sets by re-watching partially,
 *  - auto-next-episode for EPISODE items ([EpisodeNavigator]) after a
 *    natural end.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: LibraryRepository,
    engineFactory: PlayerEngine.Factory,
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle["itemId"])

    private val engine: PlayerEngine = engineFactory.create()

    val engineState: StateFlow<PlaybackState> = engine.state

    private val _item = MutableStateFlow<MediaItem?>(null)
    val item: StateFlow<MediaItem?> = _item.asStateFlow()

    private val _hudVisible = MutableStateFlow(true)
    val hudVisible: StateFlow<Boolean> = _hudVisible.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _seekFeedback = MutableStateFlow<String?>(null)
    val seekFeedback: StateFlow<String?> = _seekFeedback.asStateFlow()

    private val _fatalError = MutableStateFlow<String?>(null)
    val fatalError: StateFlow<String?> = _fatalError.asStateFlow()

    /** Emits the next episode's itemId after a natural end. */
    private val _playNext = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val playNext: SharedFlow<Long> = _playNext.asSharedFlow()

    /** Sticky watched flag — never flipped back to false by playback. */
    private var watched = false
    private var itemLoaded = false

    private var hudJob: kotlinx.coroutines.Job? = null
    private var feedbackJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val item = repository.mediaItem(itemId)
            if (item == null) {
                _fatalError.value = "This item is no longer in the library."
                return@launch
            }
            _item.value = item
            watched = item.watched
            itemLoaded = true
            engine.prepare(
                uri = item.path,
                subtitleFiles = item.subtitles,
                startPositionMs = item.resumePositionMs,
            )
        }

        viewModelScope.launch {
            engine.events.collect { event ->
                when (event) {
                    PlaybackEvent.Ended -> onEnded()
                    is PlaybackEvent.Error -> _fatalError.value = event.message
                }
            }
        }

        // Persist on every playing → paused transition (Back, Home, pause).
        viewModelScope.launch {
            engineState.map { it.playing }.distinctUntilChanged().collect { playing ->
                if (!playing && itemLoaded) persistProgress()
            }
        }

        // Periodic heartbeat while playing bounds any progress loss to
        // SAVE_INTERVAL_MS even if the process is killed mid-playback.
        viewModelScope.launch {
            while (isActive) {
                delay(SAVE_INTERVAL_MS)
                if (engineState.value.playing && itemLoaded) persistProgress()
            }
        }
    }

    override fun onCleared() {
        engine.release()
        super.onCleared()
    }

    // ── Transport (D-pad entry points) ──────────────────────────────────────

    fun togglePlayPause() {
        engine.togglePlayPause()
        pokeHud()
    }

    fun seekBy(deltaMs: Long) {
        engine.seekBy(deltaMs)
        showSeekFeedback(PlaybackMath.seekFeedbackLabel(deltaMs))
    }

    fun setAudioTrack(trackId: Int) {
        engine.setAudioTrack(trackId)
    }

    fun setSubtitleTrack(trackId: Int) {
        engine.setSubtitleTrack(trackId)
    }

    fun nudgeAudioDelay(deltaMs: Long) = engine.nudgeAudioDelay(deltaMs)

    fun nudgeSubtitleDelay(deltaMs: Long) = engine.nudgeSubtitleDelay(deltaMs)

    // ── HUD & settings overlay ──────────────────────────────────────────────

    fun openSettings() {
        _settingsOpen.value = true
        pokeHud()
    }

    fun closeSettings() {
        _settingsOpen.value = false
        pokeHud()
    }

    fun toggleSettings() {
        if (_settingsOpen.value) closeSettings() else openSettings()
    }

    /** Any remote interaction re-shows the HUD and restarts the hide timer. */
    fun pokeHud() {
        _hudVisible.value = true
        hudJob?.cancel()
        hudJob = viewModelScope.launch {
            delay(HUD_TIMEOUT_MS)
            if (!_settingsOpen.value) _hudVisible.value = false
        }
    }

    private fun showSeekFeedback(label: String) {
        _seekFeedback.value = label
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(SEEK_FEEDBACK_MS)
            _seekFeedback.value = null
        }
        pokeHud()
    }

    // ── Exit & progress persistence ─────────────────────────────────────────

    /** Back key: save progress first, then leave the player. */
    fun exit(onExit: () -> Unit) {
        viewModelScope.launch {
            if (itemLoaded) persistProgress()
            onExit()
        }
    }

    private suspend fun persistProgress() {
        val snapshot = engineState.value
        if (PlaybackMath.isWatched(snapshot.positionMs, snapshot.durationMs)) {
            watched = true
        }
        // Watched items clear the resume point (0 = finished per the domain
        // model) so "Play" restarts them from the beginning.
        val positionToSave = if (watched) 0L else snapshot.positionMs
        repository.saveProgress(itemId, positionToSave, watched)
    }

    private fun onEnded() {
        // Natural end ⇒ always finished: watched + cleared resume point.
        watched = true
        viewModelScope.launch {
            repository.saveProgress(itemId, 0L, watched = true)
            maybePlayNext()
        }
    }

    private suspend fun maybePlayNext() {
        val current = _item.value ?: return
        if (current.kind != MediaItemKind.EPISODE) return
        val folder = current.showFolderPath ?: return
        val episodes = repository
            .observeEpisodesOfShow(current.volumeUuid, folder)
            .first()
        val next = EpisodeNavigator.nextEpisode(current, episodes) ?: return
        _playNext.tryEmit(next.id)
    }

    // ── Video surface passthrough ───────────────────────────────────────────

    fun attachVideo(container: ViewGroup) = engine.attachVideo(container)

    fun detachVideo() = engine.detachVideo()

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
        const val HUD_TIMEOUT_MS = 3_500L
        const val SEEK_FEEDBACK_MS = 800L
    }
}
