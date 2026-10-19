package com.cinedrive.tv.ui.storage

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.model.VideoFile
import com.cinedrive.tv.domain.repository.StorageRepository
import com.cinedrive.tv.data.storage.StoragePermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Step 2 smoke-test ViewModel: proves the engine end-to-end on real hardware
 * (watcher → permission gate → scanner) before the browse UI exists.
 * Replaced by the browse UI's ViewModels in Step 4.
 *
 * NOTE: exposing permission Intents through the ViewModel is a deliberate
 * shortcut for this temporary screen only; Step 4 moves permission UX into
 * the UI layer properly.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storage: StorageRepository,
    private val permissions: StoragePermissionManager,
) : ViewModel() {

    val volumes: StateFlow<List<UsbVolume>> =
        storage.volumes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _access = MutableStateFlow(storage.currentAccessLevel())
    val access: StateFlow<StorageAccessLevel> = _access.asStateFlow()

    /** Files indexed per volume uuid, for the smoke-test panel. */
    private val _indexCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val indexCounts: StateFlow<Map<String, Int>> = _indexCounts.asStateFlow()

    /** Peek at the first results, to eyeball subtitle/artwork binding. */
    private val _firstFiles = MutableStateFlow<List<VideoFile>>(emptyList())
    val firstFiles: StateFlow<List<VideoFile>> = _firstFiles.asStateFlow()

    private val indexed = mutableSetOf<String>()

    init {
        storage.startWatching()
        // Re-run whenever the volume list changes (initial snapshot, plug in,
        // unplug → re-plug).
        viewModelScope.launch {
            volumes.collect { indexNewVolumes() }
        }
    }

    /** Call from Activity.onResume() — user may have just granted access. */
    fun refreshAccess() {
        _access.value = storage.currentAccessLevel()
        indexNewVolumes()
    }

    fun allFilesAccessIntent(): Intent = permissions.allFilesAccessIntent()

    fun legacyPermissions(): Array<String> = permissions.legacyPermissions()

    fun rescan() {
        indexed.clear()
        _indexCounts.value = emptyMap()
        _firstFiles.value = emptyList()
        indexNewVolumes()
    }

    private fun indexNewVolumes() {
        if (_access.value != StorageAccessLevel.GRANTED) return
        viewModelScope.launch {
            volumes.value
                .filter { it.isMounted && it.uuid !in indexed }
                .forEach { volume ->
                    try {
                        val files = storage.indexVolume(volume.uuid)
                        indexed += volume.uuid
                        _indexCounts.update { it + (volume.uuid to files.size) }
                        if (_firstFiles.value.isEmpty()) {
                            _firstFiles.value = files.take(5)
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (error: Throwable) {
                        // Drive yanked mid-scan or transient IO error: allow a
                        // retry on the next trigger instead of dying silently.
                        indexed -= volume.uuid
                    }
                }
        }
    }
}
