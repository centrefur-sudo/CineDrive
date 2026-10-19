package com.cinedrive.tv.domain.repository

import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.model.VideoFile
import kotlinx.coroutines.flow.StateFlow

/**
 * Storage-facing API consumed by the presentation layer. Hides every
 * Android-specific detail (StorageManager, mount broadcasts, permission
 * intents) behind an engine-agnostic abstraction, so the browse UI and the
 * player never touch the framework directly.
 */
interface StorageRepository {

    /** Live list of known volumes; updated on attach / detach / remount. */
    val volumes: StateFlow<List<UsbVolume>>

    /** Current permission state; the UI re-evaluates on every resume. */
    fun currentAccessLevel(): StorageAccessLevel

    /** Begin observing mount/unmount events. Idempotent. */
    fun startWatching()

    /** Stop observing (called when the app leaves the foreground). */
    fun stopWatching()

    /**
     * Recursively index every supported video file on the given volume,
     * binding subtitles, artwork and .nfo paths along the way.
     *
     * @param onProgress reports the running count of indexed files,
     *   invoked once per directory that yielded videos.
     * @throws IllegalStateException if the volume is not attached/mounted.
     */
    suspend fun indexVolume(
        volumeUuid: String,
        onProgress: suspend (indexed: Int) -> Unit = {},
    ): List<VideoFile>
}
