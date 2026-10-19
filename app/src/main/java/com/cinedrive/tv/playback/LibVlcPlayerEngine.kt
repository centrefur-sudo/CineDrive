package com.cinedrive.tv.playback

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import com.cinedrive.tv.domain.player.PlaybackMath
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * LibVLC-backed [PlayerEngine]. One instance per playback session — created
 * by [Factory], released by the owning ViewModel's onCleared().
 *
 * LibVLC notes baked into this class:
 *  - events arrive on the main thread, so state updates are cheap and safe;
 *  - delays are handled in microseconds natively but exposed in ms here;
 *  - the video surface is a SurfaceView inside [VLCVideoLayout] (the hardware
 *    overlay path — the right choice for TV SoCs; TextureView costs a GPU copy);
 *  - `media.release()` after `mediaPlayer.setMedia()` is correct because the
 *    native player retains its own reference (libvlc_media_player_set_media).
 */
class LibVlcPlayerEngine(
    private val context: Context,
    private val libVlc: LibVLC,
) : PlayerEngine {

    private val mediaPlayer = MediaPlayer(libVlc)
    private var videoLayout: VLCVideoLayout? = null
    private var attachedContainer: ViewGroup? = null

    /** Surface handshake: views attached to the player / play requested yet. */
    private var viewsAttached = false
    private var playRequested = false

    /** Resume point, applied once LibVLC reports the real duration. */
    private var pendingStartMs = 0L

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<PlaybackEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<PlaybackEvent> = _events.asSharedFlow()

    init {
        mediaPlayer.setEventListener { event -> onVlcEvent(event) }
    }

    // ── Session lifecycle ───────────────────────────────────────────────────

    override fun prepare(uri: String, subtitleFiles: List<String>, startPositionMs: Long) {
        pendingStartMs = startPositionMs
        val mediaUri = if (uri.contains("://")) Uri.parse(uri) else Uri.fromFile(File(uri))
        val media = Media(libVlc, mediaUri)
        media.setHWDecoderEnabled(true, false)
        subtitleFiles.forEachIndexed { index, path ->
            // IMedia.Slave type 0 = subtitle. Only the best side-car (first in
            // the scanner's ordering) is auto-selected; the rest join the
            // subtitle list for manual selection in quick settings.
            media.addSlave(SLAVE_TYPE_SUBTITLE, Uri.fromFile(File(path)), index == 0)
        }
        mediaPlayer.media = media
        media.release()
        playRequested = true
        maybeAttachSurface()
    }

    override fun play() {
        playRequested = true
        if (viewsAttached) {
            // Resume path (after pause / after end-of-prepare attach).
            if (!mediaPlayer.isPlaying) mediaPlayer.play()
        } else {
            // Attach will start playback once the surface handshake is done.
            maybeAttachSurface()
        }
    }

    override fun pause() {
        if (mediaPlayer.isPlaying) mediaPlayer.pause()
    }

    override fun togglePlayPause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        } else {
            playRequested = true
            mediaPlayer.play()
        }
    }

    override fun release() {
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        mediaPlayer.detachViews()
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

    // ── LibVLC events → state ───────────────────────────────────────────────

    private fun onVlcEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                _state.update { it.copy(hasStarted = true, playing = true, buffering = false) }
                refreshTracks()
                applyPendingStart()
            }

            MediaPlayer.Event.Paused -> _state.update { it.copy(playing = false) }

            MediaPlayer.Event.Stopped -> _state.update { it.copy(playing = false, buffering = false) }

            MediaPlayer.Event.EndReached -> _events.tryEmit(PlaybackEvent.Ended)

            MediaPlayer.Event.EncounteredError ->
                _events.tryEmit(PlaybackEvent.Error("The file could not be decoded"))

            MediaPlayer.Event.Buffering ->
                _state.update { it.copy(buffering = event.buffering < 100f) }

            MediaPlayer.Event.TimeChanged ->
                _state.update { it.copy(positionMs = event.timeChanged) }

            MediaPlayer.Event.LengthChanged -> {
                _state.update { it.copy(durationMs = event.lengthChanged) }
                applyPendingStart()
            }

            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> refreshTracks()
        }
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
    }

    private fun applyPendingStart() {
        val start = pendingStartMs
        if (start <= 0) return
        val length = mediaPlayer.length
        if (length <= 0) return // duration not known yet — try again on LengthChanged
        pendingStartMs = 0
        val target = PlaybackMath.resumeStartPosition(start, length)
        if (target > 0) {
            mediaPlayer.setTime(target)
            _state.update { it.copy(positionMs = target) }
        }
    }

    private companion object {
        /** org.videolan.libvlc.interfaces.IMedia.Slave.TYPE_SUBTITLE */
        const val SLAVE_TYPE_SUBTITLE = 0

        /** ±60 s delay window, 100 ms per nudge — plenty for lip-sync fixes. */
        const val DELAY_MIN_MS = -60_000L
        const val DELAY_MAX_MS = 60_000L
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
