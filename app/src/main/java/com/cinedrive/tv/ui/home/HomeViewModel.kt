package com.cinedrive.tv.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.Show
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.sync.LibrarySyncController
import com.cinedrive.tv.sync.LibrarySyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home browse screen state: live library rows merged across every mounted
 * volume plus the sync/permission status for the header and gate.
 *
 * Rows re-emit automatically on re-index (Room invalidation) and on
 * unmount: rows whose volume UUID is not currently mounted are filtered out
 * here, while the rows themselves stay in Room so progress survives a
 * re-plug.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val sync: LibrarySyncController,
) : ViewModel() {

    val syncState: StateFlow<LibrarySyncState> = sync.state

    private val mountedUuids = sync.state
        .map { it.mountedUuids }
        .distinctUntilChanged()

    val continueWatching: StateFlow<List<MediaItem>> =
        combine(library.observeAllContinueWatching(), mountedUuids) { items, mounted ->
            items.filter { it.volumeUuid in mounted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val movies: StateFlow<List<MediaItem>> =
        combine(library.observeAllMovies(), mountedUuids) { items, mounted ->
            items.filter { it.volumeUuid in mounted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val shows: StateFlow<List<Show>> =
        combine(library.observeAllShows(), mountedUuids) { shows, mounted ->
            shows.filter { it.volumeUuid in mounted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Called when a permission launcher returns. */
    fun refreshAccess() = sync.refreshAccess()

    fun rescan() = sync.rescanAll()

    fun allFilesAccessIntent(): Intent = sync.allFilesAccessIntent()

    fun legacyPermissions(): Array<String> = sync.legacyPermissions()
}
