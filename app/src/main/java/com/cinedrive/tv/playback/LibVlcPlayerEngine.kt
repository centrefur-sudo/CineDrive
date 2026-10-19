package com.cinedrive.tv.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import com.cinedrive.tv.domain.player.PlaybackMath
import com.cinedrive.tv.domain.player.PlaybackWatchdog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * LibVLC-backed [PlayerEngine]. One instance per playback session — created
 * by [Factory], released by the owning ViewModel's onCleared().
 *
 * LibVLC notes baked into this class (v1.2.1 debugging pass — see worklog):
 *  - events arrive on the main thread, so state updates are cheap and safe;
 *  - the resume position is ONLY applied once the player is Playing AND
 *    isSeekable (LibVLC reports seekable after the input thread finished
 *    opening) — seeking during the open window wedges MediaCodec on several
 *    armv7 TV SoCs: frozen frame, clock stuck, pause dead. This mirrors the
 *    official VLC app, which guards every seek with isSeekable();
 *  - the English-default audio switch is deferred ~0.5 s after the player
 *    is fully up for the same reason (a track switch mid-init restarts the
 *    audio output while the decoder is still initialising);
 *  - EncounteredError carries no message and can fire for a single failing
 *    elementary stream while the rest plays — it is retried once while
 *    nothing has started yet and ignored once frames flowed (the watchdog
 *    decides whether playback really died);
 *  - a stall watchdog kicks a frozen-but-"playing" pipeline with an
 *    absolute pause + play (MediaCodec often un-wedges) and re-opens media
 *    whose input thread never produced a single event; after the recovery
 *    budget it escalates so the UI can offer the HW-decode switch;
 *  - the video surface is a SurfaceView inside [VLCVideoLayout] (the
 *    hardware overlay path — the right choice for TV SoCs);
 *  - `media.release()` after `mediaPlayer.setMedia()` is correct because the
 *    native player retains its own reference (libvlc_media_player_set_media).
 */
class LibVlcPlayerEngine(
    private val context: Context,
    private val libVlc: LibVLC,
) : PlayerEngine {

    private val mediaPlayer = MediaPlayer(libVlc)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var videoLayout: VLCVideoLayout? = null
    private var attachedContainer: ViewGroup? = null

    /** Surface handshake: views attached to the player / play requested yet. */
    private var viewsAttached = false
    private var playRequested = false

    /** Bumped on every prepare()/release() — invalidates deferred callbacks. */
    private var sessionGen = 0
    private var released = false

    /** Resume point, applied once LibVLC is Playing AND seekable. */
    private var pendingStartMs = 0L

    /** Everything a stall re-open needs to rebuild the current media. */
    private var lastMediaUri: Uri? = null
    private var lastSubtitles: List<String> = emptyList()
    private var lastHwDecoder = true

    /** English-default audio: applied once per session, never after the user
     *  picked a track themselves in quick settings. */
    private var audioDefaultApplied = false
    private var userAudioOverride = false

    /** One transient decoder failure gets exactly one automatic re-open. */
    private var openRetryUsed = false

    /** Watchdog bookkeeping — monotonic [SystemClock.elapsedRealtime] ms. */
    private var lastProgressAt = 0L
    private var lastEventAt = 0L
    private var recoveries = 0
    private var pausedByUser = false

    private val watchdogScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watchdogJob: Job? = null

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    init {
        mediaPlayer.setEventListener { event -> onVlcEvent(event) }
    }

    // ── Session lifecycle ───────────────────────────────────────────────────

    override fun prepare(
        uri: String,
        subtitleFiles: List<String>,
        startPositionMs: Long,
        hwDecoder: Boolean,
    ) {
        sessionGen++
        watchdogJob?.cancel()
        // All handler posts are session-scoped (audio default, error retry).
        mainHandler.removeCallbacksAndMessages(null)
        pendingStartMs = startPositionMs
        lastSubtitles = subtitleFiles
        lastHwDecoder = hwDecoder
        audioDefaultApplied = false
        userAudioOverride = false
        openRetryUsed = false
        recoveries = 0
        pausedByUser = false
        playRequested = true

        val mediaUri = if (uri.contains("://")) Uri.parse(uri) else Uri.fromFile(File(uri))
        lastMediaUri = mediaUri

        // Re-prepare on a live session (HW-decode toggle, stall re-open):
        // stop the old input first so ES/track state can't bleed across.
        runCatching { mediaPlayer.stop() }

        val media = Media(libVlc, mediaUri)
        media.setHWDecoderEnabled(hwDecoder, false)
        mediaPlayer.media = media
        media.release()
        // MediaPlayer.addSlave(type, uri, select) → libvlc_media_add_slave;
        // the native layer honours `select`, so the best side-car (first in
        // the scanner's ordering) is auto-selected and the rest join the
        // subtitle list for manual selection in quick settings.
        subtitleFiles.forEachIndexed { index, path ->
            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, path, index == 0)
        }

        val now = SystemClock.elapsedRealtime()
        lastProgressAt = now
        lastEventAt = now

        if (viewsAttached) {
            // Surface already up (re-prepare path) — maybeAttachSurface()
            // would no-op, so start explicitly.
            mediaPlayer.play()
        } else {
            maybeAttachSurface()
        }
        startWatchdog()
    }

    override fun play() {
        playRequested = true
        pausedByUser = false
        if (viewsAttached) {
            // Resume path (after pause / after end-of-prepare attach).
            if (!mediaPlayer.isPlaying) mediaPlayer.play()
        } else {
            // Attach will start playback once the surface handshake is done.
            maybeAttachSurface()
        }
    }

    override fun pause() {
        pausedByUser = true
        if (mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            pausedByUser = true
            mediaPlayer.pause()
        } else {
            playRequested = true
            pausedByUser = false
            mediaPlayer.play()
        }
    }

    override fun release() {
        released = true
        sessionGen++
        watchdogJob?.cancel()
        watchdogScope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        mediaPlayer.setEventListener(null)
        runCatching { mediaPlayer.stop() }
        runCatching { mediaPlayer.detachViews() }
        mediaPlayer.release()
        viewsAttached = false
        videoLayout = null
        attachedContainer = null
    }

    // ── Transport ───────────────────────────────────────────────────────────

    override fun seekTo(positionMs: Long) {
        if (!mediaPlayer.isSeekable) return
        val target = PlaybackMath.seekTarget(mediaPlayer.time, positionMs - mediaPlayer.time, mediaPlayer.length)
        mediaPlayer.setTime(target)
        _state.update { it.copy(positionMs = target) }
    }

    override fun seekBy(deltaMs: Long) {
        if (!mediaPlayer.isSeekable) return
        val target = PlaybackMath.seekTarget(mediaPlayer.time, deltaMs, mediaPlayer.length)
        mediaPlayer.setTime(target)
        _state.update { it.copy(positionMs = target) }
    }

    override fun setAudioTrack(trackId: Int) {
        userAudioOverride = true
        mediaPlayer.setAudioTrack(trackId)
        _state.update { it.copy(selectedAudioTrack = trackId.takeIf { id -> id >= 0 }) }
    }

    override fun setSubtitleTrack(trackId: Int) {
        mediaPlayer.setSpuTrack(trackId)
        _state.update { it.copy(selectedSubtitleTrack = trackId) }
    }

    override fun nudgeAudioDelay(deltaMs: Long) {
        val currentMs = mediaPlayer.getAudioDelay() / 1000
        val newMs = (currentMs + deltaMs).coerceIn(DELAY_MIN_MS, DELAY_MAX_MS)
        mediaPlayer.setAudioDelay(newMs * 1000)
        _state.update { it.copy(audioDelayMs = newMs) }
    }

    override fun nudgeSubtitleDelay(deltaMs: Long) {
        val currentMs = mediaPlayer.getSpuDelay() / 1000
        val newMs = (currentMs + deltaMs).coerceIn(DELAY_MIN_MS, DELAY_MAX_MS)
        mediaPlayer.setSpuDelay(newMs * 1000)
        _state.update { it.copy(subtitleDelayMs = newMs) }
    }

    // ── Video surface ───────────────────────────────────────────────────────

    override fun attachVideo(container: ViewGroup) {
        attachedContainer = container
        if (videoLayout == null) {
            videoLayout = VLCVideoLayout(context).also { layout ->
                container.addView(
                    layout,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        }
        maybeAttachSurface()
    }

    override fun detachVideo() {
        if (viewsAttached) {
            viewsAttached = false
            mediaPlayer.detachViews()
        }
        attachedContainer = null
        videoLayout = null
    }

    /**
     * attachViews() needs a view that is attached to a window (the Compose
     * AndroidView factory runs before attachment), so the call is deferred
     * to onAttachedToWindow when necessary — and playback starts right after.
     */
    private fun maybeAttachSurface() {
        val container = attachedContainer ?: return
        val layout = videoLayout ?: return
        if (!viewsAttached && container.isAttachedToWindow) {
            // subtitles=true → text subs get their own surface; texture=false
            // → SurfaceView hardware overlay path for TV SoCs.
            mediaPlayer.attachViews(layout, null, true, false)
            viewsAttached = true
            if (playRequested && !mediaPlayer.isPlaying) mediaPlayer.play()
        } else if (!viewsAttached && !container.isAttachedToWindow) {
            container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    container.removeOnAttachStateChangeListener(this)
                    maybeAttachSurface()
                }

                override fun onViewDetachedFromWindow(v: View) = Unit
            })
        }
    }

    // ── Stall watchdog ──────────────────────────────────────────────────────

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = watchdogScope.launch {
            while (isActive) {
                delay(WATCHDOG_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                when (
                    PlaybackWatchdog.onTick(
                        hasStarted = _state.value.hasStarted,
                        playing = _state.value.playing,
                        pausedByUser = pausedByUser,
                        timeSinceProgressMs = now - lastProgressAt,
                        timeSinceEventMs = now - lastEventAt,
                        recoveries = recoveries,
                    )
                ) {
                    PlaybackWatchdog.Action.None -> Unit
                    PlaybackWatchdog.Action.Recover -> recoverFromStall()
                    PlaybackWatchdog.Action.Fatal -> {
                        _events.tryEmit(PlaybackEvent.Error(STALL_MESSAGE))
                        return@launch
                    }
                }
            }
        }
    }

    /**
     * The one recovery kick per stall: mid-play, an absolute pause + play
     * restarts a wedged MediaCodec; before first frame, only a full re-open
     * of the media can unwedge the input thread.
     */
    private suspend fun recoverFromStall() {
        recoveries++
        if (_state.value.hasStarted) {
            runCatching { mediaPlayer.pause() }
            delay(RECOVERY_KICK_DELAY_MS)
            runCatching { mediaPlayer.play() }
        } else {
            reopenMedia()
        }
        val now = SystemClock.elapsedRealtime()
        lastProgressAt = now
        lastEventAt = now
    }

    private fun reopenMedia() {
        val uri = lastMediaUri ?: return
        runCatching { mediaPlayer.stop() }
        val media = Media(libVlc, uri)
        media.setHWDecoderEnabled(lastHwDecoder, false)
        mediaPlayer.media = media
        media.release()
        lastSubtitles.forEachIndexed { index, path ->
            mediaPlayer.addSlave(IMedia.Slave.Type.Subtitle, path, index == 0)
        }
        mediaPlayer.play()
    }

    // ── LibVLC events → state ───────────────────────────────────────────────

    private fun onVlcEvent(event: MediaPlayer.Event) {
        val now = SystemClock.elapsedRealtime()
        lastEventAt = now
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                lastProgressAt = now
                pausedByUser = false
                _state.update { it.copy(hasStarted = true, playing = true, buffering = false) }
                refreshTracks()
                tryApplyResume()
            }

            MediaPlayer.Event.Paused -> _state.update { it.copy(playing = false) }

            MediaPlayer.Event.Stopped -> _state.update { it.copy(playing = false, buffering = false) }

            MediaPlayer.Event.EndReached -> _events.tryEmit(PlaybackEvent.Ended)

            MediaPlayer.Event.EncounteredError -> onDecoderError(now)

            MediaPlayer.Event.Buffering ->
                _state.update { it.copy(buffering = event.buffering < 100f) }

            MediaPlayer.Event.TimeChanged -> {
                lastProgressAt = now
                _state.update { it.copy(positionMs = event.timeChanged) }
                tryApplyResume()
            }

            MediaPlayer.Event.LengthChanged -> {
                lastProgressAt = now
                _state.update { it.copy(durationMs = event.lengthChanged) }
            }

            MediaPlayer.Event.SeekableChanged -> {
                // The demuxer just finished opening — the ONLY safe moment
                // for the resume seek (see class kdoc).
                if (event.seekable) tryApplyResume()
            }

            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> refreshTracks()
        }
    }

    /**
     * EncounteredError carries no message and fires for a single failing
     * elementary stream (e.g. one broken side-car subtitle) while playback
     * continues fine — so:
     *  - before first frame: one automatic re-open (transient MediaCodec
     *    init failures clear on a second attempt), then the fatal panel;
     *  - after first frame: ignored here — the watchdog decides whether
     *    playback really died.
     */
    private fun onDecoderError(now: Long) {
        if (_state.value.hasStarted) return
        if (openRetryUsed) {
            _events.tryEmit(PlaybackEvent.Error(DECODE_MESSAGE))
            return
        }
        openRetryUsed = true
        val gen = sessionGen
        mainHandler.postDelayed({
            if (gen != sessionGen || released) return@postDelayed
            reopenMedia()
            lastProgressAt = SystemClock.elapsedRealtime()
            lastEventAt = lastProgressAt
        }, OPEN_RETRY_DELAY_MS)
    }

    /** Track lists can only be read once the ES are known — never before. */
    private fun refreshTracks() {
        val audio = mediaPlayer.audioTracks.orEmpty()
            .filter { it.id >= 0 }
            .map { TrackInfo(it.id, it.name ?: "Audio track") }
        val spu = mediaPlayer.spuTracks.orEmpty()
            .filter { it.id >= 0 }
            .map { TrackInfo(it.id, it.name ?: "Subtitle track") }
        _state.update { current ->
            current.copy(
                audioTracks = audio,
                subtitleTracks = spu,
                selectedAudioTrack = current.selectedAudioTrack
                    ?: mediaPlayer.getAudioTrack().takeIf { id -> id >= 0 },
                selectedSubtitleTrack = current.selectedSubtitleTrack ?: mediaPlayer.getSpuTrack(),
                audioDelayMs = current.audioDelayMs.takeIf { it != 0L }
                    ?: (mediaPlayer.getAudioDelay() / 1000),
                subtitleDelayMs = current.subtitleDelayMs.takeIf { it != 0L }
                    ?: (mediaPlayer.getSpuDelay() / 1000),
            )
        }
        maybeApplyDefaultAudioTrack(audio)
    }

    /**
     * Multi-audio files: default to the English track when the container
     * labels one ("English", "ENG", "Track 2 - [English]" …). Runs once per
     * session, before any user choice — a manual pick from quick settings
     * sets [userAudioOverride] and is never second-guessed.
     */
    private fun maybeApplyDefaultAudioTrack(audio: List<TrackInfo>) {
        if (audioDefaultApplied || userAudioOverride || audio.isEmpty()) return
        // setAudioTrack() is a silent no-op before playback starts, and a
        // track switch during the open/seek window restarts the audio output
        // while MediaCodec is still initialising (wedges several TV SoCs) —
        // wait until the player is Playing AND seekable, then defer the
        // actual switch a little longer.
        if (!mediaPlayer.isPlaying || !mediaPlayer.isSeekable) return
        audioDefaultApplied = true
        val preferred = preferredEnglishTrack(audio) ?: return
        if (preferred.id == mediaPlayer.getAudioTrack()) return
        val gen = sessionGen
        mainHandler.postDelayed({
            if (gen != sessionGen || released || userAudioOverride) return@postDelayed
            runCatching { mediaPlayer.setAudioTrack(preferred.id) }
            _state.update { it.copy(selectedAudioTrack = preferred.id) }
        }, AUDIO_DEFAULT_DELAY_MS)
    }

    /**
     * Apply the resume position exactly once, and only when LibVLC is
     * Playing and reports seekable — retried from TimeChanged and
     * SeekableChanged until every condition is met.
     */
    private fun tryApplyResume() {
        val start = pendingStartMs
        if (start <= 0) return
        if (!mediaPlayer.isPlaying || !mediaPlayer.isSeekable) return
        val length = mediaPlayer.length
        if (length <= 0) return
        pendingStartMs = 0
        val target = PlaybackMath.resumeStartPosition(start, length)
        if (target > 0) {
            mediaPlayer.setTime(target)
            _state.update { it.copy(positionMs = target) }
        }
    }

    companion object {
        private const val DELAY_MIN_MS = -60_000L
        private const val DELAY_MAX_MS = 60_000L

        /** Watchdog sampling rate — 2× tighter than the stall thresholds. */
        private const val WATCHDOG_TICK_MS = 2_500L

        /** Absolute-pause dwell before the recovery play() kick. */
        private const val RECOVERY_KICK_DELAY_MS = 250L

        /** Delay before the automatic re-open after a pre-frame decode error. */
        private const val OPEN_RETRY_DELAY_MS = 400L

        /** English-audio switch deferral after the player is fully up. */
        private const val AUDIO_DEFAULT_DELAY_MS = 500L

        private const val STALL_MESSAGE =
            "Playback stalled and could not be recovered automatically. " +
                "If this keeps happening on this file, open quick settings " +
                "(Menu key or hold OK) and switch Hardware decoding off."
        private const val DECODE_MESSAGE =
            "The file could not be decoded after a retry. If this keeps " +
                "happening on this file, open quick settings (Menu key or " +
                "hold OK) and switch Hardware decoding off."

        /** Substring (case-insensitive) that identifies English track labels:
         *  covers "English", "ENG", "Track 1 - [English] (AC3 5.1)"… —
         *  no false hits in real container labels ("Japanese", "Deutsch",
         *  "Français", "Commentary"…). */
        const val ENGLISH_TRACK_HINT = "eng"

        /** Pure decision: which listed track should be the default. */
        fun preferredEnglishTrack(tracks: List<TrackInfo>): TrackInfo? =
            tracks.firstOrNull { it.name.contains(ENGLISH_TRACK_HINT, ignoreCase = true) }
    }

    /**
     * Hilt factory. LibVLC is [Lazy]-injected so its natives load on the
     * first playback session, not at app start.
     */
    @Singleton
    class Factory @Inject constructor(
        @ApplicationContext private val context: Context,
        private val libVlc: dagger.Lazy<LibVLC>,
    ) : PlayerEngine.Factory {
        override fun create(): PlayerEngine = LibVlcPlayerEngine(context, libVlc.get())
    }
}
