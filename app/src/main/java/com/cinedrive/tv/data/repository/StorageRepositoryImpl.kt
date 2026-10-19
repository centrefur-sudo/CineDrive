package com.cinedrive.tv.data.repository

import com.cinedrive.tv.data.scanner.VideoFileScanner
import com.cinedrive.tv.data.storage.StoragePermissionManager
import com.cinedrive.tv.data.storage.UsbVolumeWatcher
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.model.VideoFile
import com.cinedrive.tv.domain.repository.StorageRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

/**
 * Single implementation of the storage-facing domain API. Wires together the
 * volume watcher (events), permission manager (access) and file scanner
 * (indexing). Step 3 adds the Room cache around [indexVolume].
 */
@Singleton
class StorageRepositoryImpl @Inject constructor(
    private val watcher: UsbVolumeWatcher,
    private val scanner: VideoFileScanner,
    private val permissions: StoragePermissionManager,
) : StorageRepository {

    override val volumes: StateFlow<List<UsbVolume>>
        get() = watcher.volumes

    override fun currentAccessLevel(): StorageAccessLevel =
        permissions.currentAccessLevel()

    override fun startWatching() = watcher.start()

    override fun stopWatching() = watcher.stop()

    override suspend fun indexVolume(
        volumeUuid: String,
        onProgress: suspend (indexed: Int) -> Unit,
    ): List<VideoFile> {
        val volume = volumes.value.firstOrNull { it.uuid == volumeUuid }
            ?: error("Volume $volumeUuid is not attached")
        check(volume.isMounted) { "Volume $volumeUuid is not mounted" }
        return scanner.scan(File(volume.mountPath), volumeUuid, onProgress)
    }
}
