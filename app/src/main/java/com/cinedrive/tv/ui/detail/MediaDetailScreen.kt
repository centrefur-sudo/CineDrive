package com.cinedrive.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import coil.compose.AsyncImage
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind
import com.cinedrive.tv.ui.components.Backdrop
import com.cinedrive.tv.ui.components.artModel
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.OkGreen
import com.cinedrive.tv.ui.theme.Panel
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary
import com.cinedrive.tv.ui.util.episodeBadge
import com.cinedrive.tv.ui.util.formatResumeMs

/**
 * Detail page for a movie or a single episode: NFO metadata (title, year,
 * rating, runtime, director, plot) over the fanart canvas, with the
 * play/resume affordance and a watched toggle. The Play button routes to the
 * player destination (real playback lands in Step 5).
 */
@Composable
fun MediaDetailScreen(
    onBack: () -> Unit,
    onPlay: (itemId: Long) -> Unit,
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val media = item

    Box(Modifier.fillMaxSize().background(Bg)) {
        Backdrop(
            model = artModel(
                media?.fanartPath
                    ?: media?.thumbPath
                    ?: media?.posterPath
            ),
            height = 360.dp,
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

            if (media == null) {
                Spacer(Modifier.height(48.dp))
                Text(
                    text = "Loading…",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(start = 56.dp),
                )
            } else {
                DetailContent(
                    media = media,
                    onPlay = { onPlay(media.id) },
                    onToggleWatched = viewModel::toggleWatched,
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    media: MediaItem,
    onPlay: () -> Unit,
    onToggleWatched: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 56.dp, top = 56.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        // Poster column
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .height(285.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel),
            ) {
                val poster = artModel(
                    media.posterPath ?: media.thumbPath ?: media.fanartPath
                )
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            if (media.watched) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "WATCHED",
                    color = OkGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Metadata column
        Column(Modifier.weight(1f)) {
            Text(
                text = eyebrowLine(media),
                color = Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = media.title,
                color = TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (media.plot != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = media.plot,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = metaLine(media),
                color = TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onPlay) {
                    Icon(
                        imageVector = when {
                            media.watched -> Icons.Filled.Replay
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = playLabel(media))
                }
                Button(onClick = onToggleWatched) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (media.watched) OkGreen else TextPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (media.watched) "Mark unwatched" else "Mark watched")
                }
            }

            val resume = formatResumeMs(media.resumePositionMs)
            if (resume.isNotEmpty() && !media.watched) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Resume from $resume",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

private fun eyebrowLine(media: MediaItem): String = when (media.kind) {
    MediaItemKind.EPISODE ->
        listOfNotNull(
            media.showTitle,
            episodeBadge(media.season, media.episode),
        ).joinToString("   ·   ")

    MediaItemKind.MOVIE -> "Movie"
}

private fun metaLine(media: MediaItem): String = listOfNotNull(
    media.year?.toString(),
    media.rating?.let { String.format("%.1f / 10", it) },
    media.runtimeMin?.let { "$it min" },
    media.director?.let { "Dir. $it" },
).joinToString("   ·   ")

private fun playLabel(media: MediaItem): String = when {
    media.watched -> "Play again"
    media.resumePositionMs > 0 -> "Resume"
    else -> "Play"
}
