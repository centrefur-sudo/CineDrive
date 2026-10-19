package com.cinedrive.tv.ui.show

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.Show
import com.cinedrive.tv.domain.repository.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * UI state of the show detail page: the show row plus its episodes bucketed
 * into seasons. When more than one season exists the chip row selects the
 * visible season; with a single season everything is shown at once.
 */
data class ShowDetailUiState(
    val show: Show? = null,
    val seasons: List<SeasonBucket> = emptyList(),
    /** Currently selected season, or null when there is nothing to select. */
    val selectedSeason: Int? = null,
    val visibleEpisodes: List<MediaItem> = emptyList(),
    val totalEpisodes: Int = 0,
) {
    val hasSeasonChips: Boolean get() = seasons.size > 1
}

@HiltViewModel
class ShowDetailViewModel @Inject constructor(
    library: LibraryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val volumeUuid: String = checkNotNull(savedStateHandle["volumeUuid"])
    private val folderPath: String = checkNotNull(savedStateHandle["folder"])

    private val show: StateFlow<Show?> = flow {
        emit(library.show(volumeUuid, folderPath))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val episodes: StateFlow<List<MediaItem>> =
        library.observeEpisodesOfShow(volumeUuid, folderPath)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selection = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<ShowDetailUiState> =
        combine(show, episodes, selection) { show, episodes, selected ->
            val buckets = groupBySeason(episodes)
            val effectiveSelection = selected
                ?.takeIf { wanted -> buckets.any { it.season == wanted } }
                ?: buckets.firstOrNull()?.season
            val visible = when {
                buckets.size <= 1 -> episodes
                else -> buckets
                    .firstOrNull { it.season == effectiveSelection }
                    ?.episodes
                    ?: episodes
            }
            ShowDetailUiState(
                show = show,
                seasons = buckets,
                selectedSeason = if (buckets.size > 1) effectiveSelection else null,
                visibleEpisodes = visible,
                totalEpisodes = episodes.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShowDetailUiState())

    /** Season chip tap. */
    fun selectSeason(season: Int) {
        selection.value = season
    }
}
