package com.cinedrive.tv.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.sync.LibrarySyncState
import com.cinedrive.tv.sync.VolumeIndexState
import com.cinedrive.tv.ui.components.EmptySearchIcon
import com.cinedrive.tv.ui.components.PosterCard
import com.cinedrive.tv.ui.components.SeeAllCard
import com.cinedrive.tv.ui.components.SectionHeader
import com.cinedrive.tv.ui.components.StateBlock
import com.cinedrive.tv.ui.components.UsbIcon
import com.cinedrive.tv.ui.components.WideCard
import com.cinedrive.tv.ui.components.artModel
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.TextSecondary
import com.cinedrive.tv.ui.util.episodeBadge
import com.cinedrive.tv.ui.util.progressFraction

/**
 * Home = the 10-foot library browser: continue-watching row, movies row and
 * TV shows row, with the storage permission gate and per-drive sync status
 * handled up top. Selecting a poster opens the detail page; the full
 * collections live behind the "All" tiles.
 */
@Composable
fun HomeScreen(
    onOpenItem: (Long) -> Unit,
    onOpenShow: (volumeUuid: String, folderPath: String) -> Unit,
    onSeeAllMovies: () -> Unit,
    onSeeAllShows: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val movies by viewModel.movies.collectAsStateWithLifecycle()
    val shows by viewModel.shows.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(Bg)) {
        LibraryHeader(sync, onRescan = viewModel::rescan)
        when (sync.access) {
            StorageAccessLevel.GRANTED -> HomeBody(
                sync = sync,
                continueWatching = continueWatching,
                movies = movies,
                shows = shows,
                onOpenItem = onOpenItem,
                onOpenShow = onOpenShow,
                onSeeAllMovies = onSeeAllMovies,
                onSeeAllShows = onSeeAllShows,
            )

            StorageAccessLevel.NEEDS_ALL_FILES_ACCESS -> {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { viewModel.refreshAccess() }
                PermissionGate(
                    message = "Full storage access is required to browse USB drives.",
                    actionLabel = "Grant all-files access",
                ) { launcher.launch(viewModel.allFilesAccessIntent()) }
            }

            StorageAccessLevel.NEEDS_LEGACY_READ_PERMISSION -> {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { viewModel.refreshAccess() }
                PermissionGate(
                    message = "Storage permission is required to browse USB drives.",
                    actionLabel = "Grant storage permission",
                ) { launcher.launch(viewModel.legacyPermissions()) }
            }
        }
    }
}

/**
 * Library content or the empty/indexing states, with navigation callbacks
 * supplied by the NavHost.
 */
@Composable
fun HomeBody(
    sync: LibrarySyncState,
    continueWatching: List<MediaItem>,
    movies: List<MediaItem>,
    shows: List<com.cinedrive.tv.domain.model.Show>,
    onOpenItem: (Long) -> Unit,
    onOpenShow: (volumeUuid: String, folderPath: String) -> Unit,
    onSeeAllMovies: () -> Unit,
    onSeeAllShows: () -> Unit,
) {
    when {
        sync.volumes.isEmpty() -> Centered {
            StateBlock(
                icon = UsbIcon(),
                title = "No USB drive detected",
                hint = "Plug in a USB drive that contains your movies or TV shows.",
            )
        }

        movies.isEmpty() && shows.isEmpty() && continueWatching.isEmpty() -> Centered {
            when {
                sync.anyIndexing -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Indexing your drives…",
                        color = TextSecondary,
                        fontSize = 15.sp,
                    )
                }

                else -> StateBlock(
                    icon = EmptySearchIcon(),
                    title = "Library is empty",
                    hint = "No supported videos (.mp4, .mkv, .avi, .webm) were found " +
                        "on the attached drives. Try Rescan.",
                )
            }
        }

        else -> LibraryRows(
            continueWatching = continueWatching,
            movies = movies,
            shows = shows,
            onOpenItem = onOpenItem,
            onOpenShow = onOpenShow,
            onSeeAllMovies = onSeeAllMovies,
            onSeeAllShows = onSeeAllShows,
        )
    }
}

@Composable
private fun LibraryRows(
    continueWatching: List<MediaItem>,
    movies: List<MediaItem>,
    shows: List<com.cinedrive.tv.domain.model.Show>,
    onOpenItem: (Long) -> Unit,
    onOpenShow: (String, String) -> Unit,
    onSeeAllMovies: () -> Unit,
    onSeeAllShows: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue-watching") {
                Column {
                    SectionHeader("Continue watching", continueWatching.size)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(continueWatching, key = { it.id }) { item ->
                            WideCard(
                                title = item.title,
                                subtitle = item.showTitle ?: item.year?.toString(),
                                badge = episodeBadge(item.season, item.episode),
                                artModel = artModel(item.fanartPath ?: item.posterPath),
                                progress = progressFraction(item.resumePositionMs, item.runtimeMin),
                                width = 220.dp,
                                onClick = { onOpenItem(item.id) },
                            )
                        }
                    }
                }
            }
        }

        if (movies.isNotEmpty()) {
            item(key = "movies") {
                Column {
                    SectionHeader("Movies", movies.size)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(movies, key = { it.id }) { item ->
                            PosterCard(
                                title = item.title,
                                artModel = artModel(item.posterPath ?: item.fanartPath),
                                watched = item.watched,
                                progress = progressFraction(item.resumePositionMs, item.runtimeMin),
                                width = 132.dp,
                                onClick = { onOpenItem(item.id) },
                            )
                        }
                        item(key = "see-all-movies") {
                            SeeAllCard(
                                label = "All movies",
                                count = movies.size,
                                onClick = onSeeAllMovies,
                                modifier = Modifier.width(132.dp),
                            )
                        }
                    }
                }
            }
        }

        if (shows.isNotEmpty()) {
            item(key = "shows") {
                Column {
                    SectionHeader("TV Shows", shows.size)
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(shows, key = { "${it.volumeUuid}:${it.folderPath}" }) { show ->
                            PosterCard(
                                title = show.title,
                                artModel = artModel(show.posterPath ?: show.fanartPath),
                                width = 132.dp,
                                onClick = { onOpenShow(show.volumeUuid, show.folderPath) },
                            )
                        }
                        item(key = "see-all-shows") {
                            SeeAllCard(
                                label = "All shows",
                                count = shows.size,
                                onClick = onSeeAllShows,
                                modifier = Modifier.width(132.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(state: LibrarySyncState, onRescan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "CineDrive",
            color = Accent,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            text = statusLine(state),
            color = TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.weight(1f))
        if (state.access == StorageAccessLevel.GRANTED && state.volumes.isNotEmpty()) {
            Button(onClick = onRescan) { Text("Rescan library") }
        }
    }
}

private fun statusLine(state: LibrarySyncState): String {
    if (state.volumes.isEmpty()) return "no drive"
    return state.volumes.joinToString("   ·   ") { vs ->
        val name = vs.volume.displayName.ifBlank { "USB drive" }
        when (val s = vs.state) {
            is VolumeIndexState.Indexing -> "Indexing $name… ${s.indexedFiles}"
            VolumeIndexState.Queued -> "Waiting to index $name…"
            is VolumeIndexState.Done ->
                if (s.files > 0) "$name — ${s.files} files" else "$name — ready"
            is VolumeIndexState.Failed -> "Index failed on $name — ${s.message}"
            VolumeIndexState.Unmounted -> "$name — unplugged"
        }
    }
}

@Composable
private fun PermissionGate(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        content()
    }
}
