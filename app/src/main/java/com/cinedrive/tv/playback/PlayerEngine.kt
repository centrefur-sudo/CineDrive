package com.cinedrive.tv.playback

import android.view.ViewGroup
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** One selectable audio / subtitle track as reported by the engine. */
data class TrackInfo(
    /** Engine-native track id (LibVLC ES id) — opaque outside the engine. */
    val id: Int,
    val name: String,
)

/**
 * Immutable snapshot of everything the player UI renders: transport state,
 * clock, and the track/offset values shown in quick settings. Emitted on
 * every LibVLC event so the HUD progress bar stays live.
 */
data class PlaybackState(
    /** True once the engine has started producing frames at least once. */
    val hasStarted: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val audioTracks: List<TrackInfo> = emptyList(),
    /** Engine id of the active audio track, or null while none is selected. */
    val selectedAudioTrack: Int? = null,
    val subtitleTracks: List<TrackInfo> = emptyList(),
    /** Engine id of the active subtitle track; -1 means subtitles disabled. */
    val selectedSubtitleTrack: Int? = null,
    val audioDelayMs: Long = 0,
    val subtitleDelayMs: Long = 0,
)

sealed interface PlaybackEvent {
    /** Natural end of media — triggers the watched rule and auto-next. */
    data object Ended : PlaybackEvent

    data class Error(val message: String) : PlaybackEvent
}

/**
 * Engine-agnostic playback contract. LibVLC is the Step 5 backend; a Media3
 * backend could implement the same interface later without the UI, the
 * ViewModel or the domain rules ever noticing.
 *
 * Threading: methods are called from the main thread; [state] and [events]
 * are Compose-safe flows.
 */
interface PlayerEngine {

    val state: StateFlow<PlaybackState>
    val events: SharedFlow<PlaybackEvent>

    /**
     * Loads media and starts playback as soon as the video surface is
     * attached (attach order with [attachVideo] does not matter).
     * External subtitle files are attached as side-car tracks; the first
     * (best, per the Step 2 scanner ordering) is auto-selected.
     * [startPositionMs] is applied only once the player reports Playing AND
     * seekable — seeking earlier wedges MediaCodec on several TV SoCs.
     * [hwDecoder] = false forces software decoding (escape hatch for
     * devices whose MediaCodec stalls).
     */
    fun prepare(
        uri: String,
        subtitleFiles: List<String> = emptyList(),
        startPositionMs: Long = 0,
        hwDecoder: Boolean = true,
    )

    fun play()
    fun pause()
    fun togglePlayPause()

    fun seekTo(positionMs: Long)
    fun seekBy(deltaMs: Long)

    fun setAudioTrack(trackId: Int)

    /** [trackId] = -1 disables subtitles. */
    fun setSubtitleTrack(trackId: Int)

    fun nudgeAudioDelay(deltaMs: Long)
    fun nudgeSubtitleDelay(deltaMs: Long)

    /** Renders video into [container]; the engine owns the view it adds. */
    fun attachVideo(container: ViewGroup)
    fun detachVideo()

    fun release()

    /** One engine instance per playback session. */
    interface Factory {
        fun create(): PlayerEngine
    }
}
