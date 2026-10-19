package com.cinedrive.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.ErrorOrange
import com.cinedrive.tv.ui.theme.OkGreen
import com.cinedrive.tv.ui.theme.Panel
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary
import java.io.File

/**
 * The card is the single interactive primitive of the browse UI. It is
 * implemented on plain Compose focus primitives (clickable + interaction
 * source) instead of tv-material Surface so focus scale/border behaviour is
 * fully deterministic across D-pad navigation.
 */
@Composable
private fun FocusCard(
    onClick: () -> Unit,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    onFocused: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) onFocused?.invoke()
    }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "cardScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = tween(durationMillis = 120),
        label = "cardBorder",
    )
    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CardShape)
            .background(Panel)
            .border(borderWidth, Accent, CardShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        content = content,
    )
}

private val CardShape = RoundedCornerShape(12.dp)

/** First usable Coil model for an artwork path (local file or NFO thumb URL). */
fun artModel(path: String?): Any? = when {
    path == null -> null
    path.startsWith("http://") || path.startsWith("https://") -> path
    else -> File(path)
}

/**
 * 2:3 poster card used in movie grids and the home "Movies" row.
 */
@Composable
fun PosterCard(
    title: String,
    artModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    progress: Float? = null,
    watched: Boolean = false,
) {
    Column(modifier = modifier.then(width?.let { Modifier.width(it) } ?: Modifier)) {
        FocusCard(onClick = onClick, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth()) {
            CardArtwork(model = artModel, fallbackIcon = Icons.Filled.Movie)
            if (watched) WatchedBadge()
            progress?.let { ProgressBar(it) }
        }
        Spacer(Modifier.height(8.dp))
        CardTitle(title)
    }
}

/**
 * 16:9 wide card used for continue-watching and episode grids. Shows an
 * optional "S01E04" badge and a secondary line under the artwork.
 *
 * [onFocused] fires when D-pad focus lands on the card — the show screen
 * uses it to drive the focused-episode description panel.
 */
@Composable
fun WideCard(
    title: String,
    artModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    badge: String? = null,
    subtitle: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    Column(modifier = modifier.then(width?.let { Modifier.width(it) } ?: Modifier)) {
        FocusCard(
            onClick = onClick,
            aspectRatio = 16f / 9f,
            modifier = Modifier.fillMaxWidth(),
            onFocused = onFocused,
        ) {
            CardArtwork(model = artModel, fallbackIcon = Icons.Filled.OndemandVideo)
            badge?.let { Badge(it) }
            if (watched) WatchedBadge()
            progress?.let { ProgressBar(it) }
        }
        Spacer(Modifier.height(8.dp))
        CardTitle(title)
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Trailing tile of a home row that jumps into the full grid, e.g.
 * "All movies (37)".
 */
@Composable
fun SeeAllCard(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        FocusCard(onClick = onClick, aspectRatio = 2f / 3f, modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "$count titles",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        // Keep the card grid aligned with poster cards that carry a title.
        Text(
            text = "",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

// ── Card internals ─────────────────────────────────────────────────────────

@Composable
private fun CardArtwork(model: Any?, fallbackIcon: ImageVector) {
    Box(Modifier.fillMaxSize().background(Bg)) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (model == null) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = Panel,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(42.dp),
            )
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun Badge(text: String) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(Accent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFF141414),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun BoxScope.WatchedBadge() {
    Icon(
        imageVector = Icons.Filled.CheckCircle,
        contentDescription = "Watched",
        tint = OkGreen,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(18.dp),
    )
}

@Composable
private fun BoxScope.ProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .height(4.dp)
            .background(Color(0x66000000)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .fillMaxHeight()
                .background(Accent),
        )
    }
}

// ── Screen-level shared pieces ─────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        if (count != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$count",
                color = TextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

/**
 * Full-bleed fanart with a vertical fade into the page background; used as
 * the top canvas of detail screens.
 */
@Composable
fun Backdrop(model: Any?, height: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0x660B0E14), Bg),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY,
                    )
                ),
        )
    }
}

/**
 * Empty/error state: big icon + title + optional hint, centred for 10-foot
 * reading distance.
 */
@Composable
fun StateBlock(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                color = TextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun UsbIcon(): ImageVector = Icons.Filled.Usb
fun EmptySearchIcon(): ImageVector = Icons.Filled.SearchOff
fun WarningIcon(): ImageVector = Icons.Filled.WarningAmber
