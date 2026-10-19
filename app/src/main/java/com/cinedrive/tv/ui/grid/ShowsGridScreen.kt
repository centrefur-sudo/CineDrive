package com.cinedrive.tv.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cinedrive.tv.domain.model.Show
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.sync.LibrarySyncController
import com.cinedrive.tv.ui.components.PosterCard
import com.cinedrive.tv.ui.components.artModel
import com.cinedrive.tv.ui.theme.Bg
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class ShowsGridViewModel @Inject constructor(
    library: LibraryRepository,
    sync: LibrarySyncController,
) : ViewModel() {

    private val mountedUuids = sync.state
        .map { it.mountedUuids }
        .distinctUntilChanged()

    val shows: StateFlow<List<Show>> =
        combine(library.observeAllShows(), mountedUuids) { shows, mounted ->
            shows.filter { it.volumeUuid in mounted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/**
 * Full-page TV show collection. Selecting a show drills into its
 * season/episode page.
 */
@Composable
fun ShowsGridScreen(
    onBack: () -> Unit,
    onOpenShow: (volumeUuid: String, folderPath: String) -> Unit,
    viewModel: ShowsGridViewModel = hiltViewModel(),
) {
    val shows by viewModel.shows.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Bg)) {
        GridTopBar(title = "TV Shows", count = shows.size, onBack = onBack)
        if (shows.isEmpty()) {
            GridEmpty()
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 56.dp, end = 56.dp, top = 12.dp, bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                items(shows, key = { "${it.volumeUuid}:${it.folderPath}" }) { show ->
                    PosterCard(
                        title = show.title,
                        artModel = artModel(show.posterPath ?: show.fanartPath),
                        onClick = { onOpenShow(show.volumeUuid, show.folderPath) },
                    )
                }
            }
        }
    }
}
