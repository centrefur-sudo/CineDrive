package com.cinedrive.tv.data.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import androidx.core.content.ContextCompat
import com.cinedrive.tv.domain.model.UsbVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live USB volume registry — the heart of the "Storage & USB Engine" spec.
 *
 * Event sources, by OS version:
 *
 *  • API 30+ (Android 11…14+): [StorageManager.registerStorageVolumeCallback].
 *    The callback hands us a snapshot [StorageVolume]; per the platform docs
 *    the argument is only a hint — we always re-enumerate via
 *    [StorageManager.getStorageVolumes] for authoritative state.
 *    (Note: the method was renamed from `registerVolumeCallback` in the
 *    original API 30 to `registerStorageVolumeCallback`; on compileSdk 35
 *    only the new name exists.)
 *
 *  • API 29 (Android 10): StorageVolumeCallback does not exist and
 *    ACTION_MEDIA_MOUNTED is no longer delivered to manifest receivers, so we
 *    register a runtime receiver (protected system broadcasts are still
 *    delivered to context-registered receivers here).
 *
 * Drive identity: FAT/exFAT USB drives get a volume UUID ("0403-0201") which
 * is stable across re-plugs — this is the key the Room layer (Step 3) uses to
 * restore playback progress per physical device.
 */
@Singleton
class UsbVolumeWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _volumes = MutableStateFlow<List<UsbVolume>>(emptyList())

    /** Latest known volume list; always safe to read from any thread. */
    val volumes: StateFlow<List<UsbVolume>> = _volumes.asStateFlow()

    private val started = AtomicBoolean(false)
    private var volumeCallback: StorageManager.StorageVolumeCallback? = null
    private var legacyReceiver: BroadcastReceiver? = null

    /** Begin observing mount events and take an initial snapshot. Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager =
                context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            val callback = object : StorageManager.StorageVolumeCallback() {
                override fun onStateChanged(volume: StorageVolume) = refresh()
            }
            volumeCallback = callback
            storageManager.registerStorageVolumeCallback(context.mainExecutor, callback)
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) = refresh()
            }
            legacyReceiver = receiver
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addDataScheme("file")
            }
            ContextCompat.registerReceiver(
                context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        refresh()
    }

    /** Stop observing. Safe to call repeatedly; [start] can re-arm later. */
    fun stop() {
        if (!started.compareAndSet(true, false)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volumeCallback?.let { callback ->
                val storageManager =
                    context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                storageManager.unregisterStorageVolumeCallback(callback)
            }
        }
        legacyReceiver?.let { runCatching { context.unregisterReceiver(it) } }
        volumeCallback = null
        legacyReceiver = null
        _volumes.value = emptyList()
    }

    /**
     * Re-enumerates every volume. Cheap — one StorageManager query plus a
     * handful of stat calls — so it is safe to call from every event.
     */
    fun refresh() {
        val storageManager =
            context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        _volumes.value =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) detectModern(storageManager)
            else detectLegacy(storageManager)
    }

    /**
     * API 30+: StorageManager reports uuid / state / directory directly.
     * Volumes without a UUID (emulated internal storage) are not USB drives
     * and are excluded.
     */
    private fun detectModern(storageManager: StorageManager): List<UsbVolume> =
        storageManager.storageVolumes.mapNotNull { volume ->
            val uuid = volume.uuid ?: return@mapNotNull null
            val directory = volume.directory ?: return@mapNotNull null
            UsbVolume(
                uuid = uuid,
                displayName = volume.getDescription(context) ?: uuid,
                mountPath = directory.absolutePath,
                isRemovable = volume.isRemovable,
                isMounted = volume.state == Environment.MEDIA_MOUNTED,
            )
        }

    /**
     * API 29 fallback: StorageVolume.directory is not public yet, and the
     * broadcast only carries the mount point. AOSP mounts removable drives at
     * /storage/<uuid> where the directory name IS the volume UUID, so we
     * reconcile the /storage listing with StorageManager metadata.
     */
    private fun detectLegacy(storageManager: StorageManager): List<UsbVolume> {
        val managedByUuid = storageManager.storageVolumes.associateBy { it.uuid }
        val usbMountShape = Regex("^[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}$")
        return File("/storage")
            .listFiles()
            ?.filter { it.isDirectory && usbMountShape.matches(it.name) }
            ?.map { dir ->
                val uuid = dir.name
                val managed = managedByUuid[uuid]
                UsbVolume(
                    uuid = uuid,
                    displayName = managed?.getDescription(context) ?: uuid,
                    mountPath = dir.absolutePath,
                    isRemovable = managed?.isRemovable ?: true,
                    isMounted = dir.canRead(),
                )
            }
            .orEmpty()
    }
}
