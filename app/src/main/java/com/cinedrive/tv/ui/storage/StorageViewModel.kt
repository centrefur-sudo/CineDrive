package com.cinedrive.tv.ui.storage

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.data.storage.StoragePermissionManager
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.domain.repository.StorageRepository
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
 * Step 3 smoke-test ViewModel: proves the full pipeline end-to-end on real
 * hardware — volume watcher → permission gate → scanner → NFO parse →
 * classifier → Room transaction. The per-volume stats line reads straight
 * from [LibraryIndexResult]; the continue-watching count proves the
 * playback-state columns survive a rescan once Step 5 writes progress.
 * Replaced by the browse UI's ViewModels in Step 4.
 *
 * NOTE: exposing permission Intents through the ViewModel is a deliberate
 * shortcut for this temporary screen only; Step 4 moves permission UX into
 * the UI layer properly.
 */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val storage: StorageRepository,
    private val library: LibraryRepository,
    private val permissions: StoragePermissionManager,
) : ViewModel() {

    val volumes: StateFlow<List<UsbVolume>> =
        storage.volumes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _access = MutableStateFlow(storage.currentAccessLevel())
    val access: StateFlow<StorageAccessLevel> = _access.asStateFlow()

    /** Human-readable per-volume library stats, e.g. "3 movies · 2 shows · 12 eps". */
    private val _libraryStats = MutableStateFlow<Map<String, String>>(emptyMap())
    val libraryStats: StateFlow<Map<String, String>> = _libraryStats.asStateFlow()

    /** Total items already in the Room cache, across all volumes. */
    private val _cachedItems = MutableStateFlow(0)
    val cachedItems: StateFlow<Int> = _cachedItems.asStateFlow()

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
        _libraryStats.value = emptyMap()
        indexNewVolumes()
    }

    private fun indexNewVolumes() {
        if (_access.value != StorageAccessLevel.GRANTED) return
        viewModelScope.launch {
            volumes.value
                .filter { it.isMounted && it.uuid !in indexed }
                .forEach { volume ->
                    try {
                        val result = library.indexVolume(volume.uuid)
                        indexed += volume.uuid
                        val parts = buildList {
                            if (result.movies > 0) add("${result.movies} movies")
                            if (result.shows > 0) add("${result.shows} shows")
                            if (result.episodes > 0) add("${result.episodes} eps")
                        }
                        _libraryStats.update {
                            it + (volume.uuid to parts.ifEmpty { listOf("empty drive") }
                                .joinToString(" · "))
                        }
                        _cachedItems.value = _cachedItems.value + result.filesScanned
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
