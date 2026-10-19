package com.cinedrive.tv.ui.show

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.ui.components.Backdrop
import com.cinedrive.tv.ui.components.WideCard
import com.cinedrive.tv.ui.components.artModel
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.Panel
import com.cinedrive.tv.ui.theme.PanelBright
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary
import com.cinedrive.tv.ui.util.episodeBadge
import com.cinedrive.tv.ui.util.progressFraction
import com.cinedrive.tv.ui.util.seasonLabel

/**
 * Show → seasons → episodes. Fanart canvas on top, season chip row, episode
 * grid below. Episodes open the shared media detail page (same one movies
 * use), which is also where playback launches in Step 5.
 */
@Composable
fun ShowDetailScreen(
    onBack: () -> Unit,
    onOpenEpisode: (Long) -> Unit,
    viewModel: ShowDetailViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val show = ui.show

    // D-pad focus drives the description panel: whichever episode card is
    // focused gets its .nfo plot shown below the season chips.
    var focusedEpisodeId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(ui.visibleEpisodes) {
        if (focusedEpisodeId !in ui.visibleEpisodes.map { it.id }) {
            focusedEpisodeId = ui.visibleEpisodes.firstOrNull()?.id
        }
    }
    val focusedEpisode = ui.visibleEpisodes.firstOrNull { it.id == focusedEpisodeId }
        ?: ui.visibleEpisodes.firstOrNull()

    Box(Modifier.fillMaxSize().background(Bg)) {
        Backdrop(
            model = artModel(show?.fanartPath ?: show?.posterPath),
            height = 340.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 56.dp, top = 20.dp),
            ) {
                Button(onClick = onBack) { Text("Back") }
            }

            Spacer(Modifier.height(120.dp))

            Column(Modifier.padding(horizontal = 56.dp)) {
                if (show != null) {
                    Text(
                        text = show.title,
                        color = TextPrimary,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = showMetaLine(show.year, ui),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!show.plot.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = show.plot,
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (ui.hasSeasonChips) {
                    Spacer(Modifier.height(14.dp))
                    SeasonChipRow(
                        seasons = ui.seasons,
                        selected = ui.selectedSeason,
                        onSelect = viewModel::selectSeason,
                    )
                }

                focusedEpisode?.let { episode ->
                    Spacer(Modifier.height(14.dp))
                    EpisodeInfoPanel(episode)
                }

                Spacer(Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(210.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(ui.visibleEpisodes, key = { it.id }) { episode ->
                        WideCard(
                            title = episode.title,
                            badge = episodeBadge(episode.season, episode.episode),
                            artModel = artModel(
                                episode.thumbPath
                                    ?: episode.fanartPath
                                    ?: episode.posterPath
                                    ?: show?.fanartPath
                                    ?: show?.posterPath
                            ),
                            progress = progressFraction(
                                episode.resumePositionMs,
                                episode.runtimeMin,
                            ),
                            watched = episode.watched,
                            onFocused = { focusedEpisodeId = episode.id },
                            onClick = { onOpenEpisode(episode.id) },
                        )
                    }
                }
            }
        }
    }
}

private fun showMetaLine(year: Int?, ui: ShowDetailUiState): String =
    buildList {
        year?.let { add(it.toString()) }
        val realSeasons = ui.seasons.count { it.season > 0 }
        if (realSeasons > 0) add("$realSeasons season" + if (realSeasons > 1) "s" else "")
        if (ui.seasons.any { it.season == 0 }) add("Specials")
        add("${ui.totalEpisodes} episode" + if (ui.totalEpisodes == 1) "" else "s")
    }.joinToString("   ·   ")

/**
 * Fixed-height description strip for the focused episode: badge + title on
 * line one, the episode's .nfo <plot> beneath. Fixed height so the grid
 * does not jump when focus moves between episodes with long/short plots.
 */
@Composable
private fun EpisodeInfoPanel(episode: MediaItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val badge = episodeBadge(episode.season, episode.episode)
        Text(
            text = if (badge != null) "$badge  ·  ${episode.title}" else episode.title,
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = episode.plot?.takeIf { it.isNotBlank() }
                ?: "No description for this episode — add a .nfo next to the file.",
            color = TextSecondary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeasonChipRow(
    seasons: List<SeasonBucket>,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(seasons, key = { it.season }) { bucket ->
            SeasonChip(
                label = seasonLabel(bucket.season) + "  (${bucket.episodes.size})",
                selected = bucket.season == selected,
                onClick = { onSelect(bucket.season) },
            )
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = when {
        selected -> Accent
        focused -> TextSecondary
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) PanelBright else Panel)
            .border(2.dp, borderColor, RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) Accent else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
