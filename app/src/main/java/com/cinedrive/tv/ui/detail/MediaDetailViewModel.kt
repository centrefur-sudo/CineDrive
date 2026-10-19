package com.cinedrive.tv.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Detail page for any [MediaItem] (movie or episode). The item streams live
 * from Room, so a watched-toggle or a progress write (Step 5 player)
 * updates the page without manual refreshing.
 */
@HiltViewModel
class MediaDetailViewModel @Inject constructor(
    private val library: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: Long = checkNotNull(savedStateHandle["itemId"])

    val item: StateFlow<MediaItem?> = library.observeMediaItem(itemId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Toggle the watched flag; Room invalidation refreshes the whole UI. */
    fun toggleWatched() {
        val current = item.value ?: return
        viewModelScope.launch {
            library.markWatched(itemId, !current.watched)
        }
    }
}
