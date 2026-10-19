package com.cinedrive.tv.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.sync.LibrarySyncController
import com.cinedrive.tv.ui.components.EmptySearchIcon
import com.cinedrive.tv.ui.components.PosterCard
import com.cinedrive.tv.ui.components.StateBlock
import com.cinedrive.tv.ui.components.artModel
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.TextSecondary
import com.cinedrive.tv.ui.util.progressFraction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Shared state plumbing for the two full-page grids: the same mounted-volume
 * filtering the home screen applies, one row type per screen.
 */
open class LibraryGridViewModel(
    library: LibraryRepository,
    sync: LibrarySyncController,
) : ViewModel() {

    private val mountedUuids = sync.state
        .map { it.mountedUuids }
        .distinctUntilChanged()

    val movies: StateFlow<List<MediaItem>> =
        combine(library.observeAllMovies(), mountedUuids) { items, mounted ->
            items.filter { it.volumeUuid in mounted }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@HiltViewModel
class MoviesGridViewModel @Inject constructor(
    library: LibraryRepository,
    sync: LibrarySyncController,
) : LibraryGridViewModel(library, sync)

/**
 * Full-page movie collection, alphabetically across all mounted drives.
 * Back is the remote's Back key (plus the on-screen Back button for
 * emulator/mouse use).
 */
@Composable
fun MoviesGridScreen(
    onBack: () -> Unit,
    onOpenItem: (Long) -> Unit,
    viewModel: MoviesGridViewModel = hiltViewModel(),
) {
    val movies by viewModel.movies.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Bg)) {
        GridTopBar(title = "Movies", count = movies.size, onBack = onBack)
        if (movies.isEmpty()) {
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
                items(movies, key = { it.id }) { item ->
                    PosterCard(
                        title = item.title,
                        artModel = artModel(
                            item.posterPath ?: item.thumbPath ?: item.fanartPath
                        ),
                        watched = item.watched,
                        progress = progressFraction(item.resumePositionMs, item.runtimeMin),
                        onClick = { onOpenItem(item.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun GridTopBar(title: String, count: Int, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onBack) { Text("Back") }
        Spacer(Modifier.width(20.dp))
        Text(
            text = title,
            color = Accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "$count",
            color = TextSecondary,
            fontSize = 14.sp,
        )
    }
}

@Composable
internal fun GridEmpty() {
    // fillMaxSize fills the height remaining below the top bar inside the
    // Column of each grid screen (weight() would need a ColumnScope here).
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        StateBlock(
            icon = EmptySearchIcon(),
            title = "Nothing here yet",
            hint = "This collection is empty. Plug in a drive or run a rescan from Home.",
        )
    }
}
