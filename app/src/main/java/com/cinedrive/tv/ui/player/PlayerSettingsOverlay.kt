package com.cinedrive.tv.ui.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cinedrive.tv.domain.player.PlaybackMath
import com.cinedrive.tv.playback.PlaybackState
import com.cinedrive.tv.playback.TrackInfo
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Panel
import com.cinedrive.tv.ui.theme.PanelBright
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary

/**
 * Quick settings panel (Menu key / long-press OK): audio track, subtitle
 * track incl. "Off", and the ±100 ms audio/subtitle offset nudges from the
 * spec. Lives on the right edge so the video keeps playing behind it.
 *
 * While the panel is open the D-pad navigates rows; LEFT/RIGHT on an offset
 * row adjusts the delay, OK selects the focused track, BACK closes.
 */
@Composable
internal fun PlayerSettingsOverlay(
    state: PlaybackState,
    firstRowFocus: FocusRequester,
    onAudioTrack: (Int) -> Unit,
    onSubtitleTrack: (Int) -> Unit,
    onNudgeAudio: (Long) -> Unit,
    onNudgeSubtitle: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(400.dp)
            .background(Panel.copy(alpha = 0.97f))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        Text(
            text = "Quick Settings",
            color = Accent,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(18.dp))

        SectionLabel("Audio")
        if (state.audioTracks.isEmpty()) {
            HintLine("No audio track information yet")
        } else {
            state.audioTracks.forEachIndexed { index, track ->
                SettingsRow(
                    label = track.name,
                    selected = state.selectedAudioTrack == track.id,
                    modifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier,
                    onClick = { onAudioTrack(track.id) },
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Subtitles")
        // Anchor row for overlay focus when the audio list is empty.
        SettingsRow(
            label = "Off",
            selected = state.selectedSubtitleTrack == null || state.selectedSubtitleTrack == -1,
            modifier = if (state.audioTracks.isEmpty()) Modifier.focusRequester(firstRowFocus) else Modifier,
            onClick = { onSubtitleTrack(-1) },
        )
        state.subtitleTracks.forEach { track ->
            SettingsRow(
                label = track.name,
                selected = state.selectedSubtitleTrack == track.id,
                onClick = { onSubtitleTrack(track.id) },
            )
        }

        Spacer(Modifier.height(18.dp))
        SectionLabel("Audio offset")
        OffsetRow(
            label = "Audio sync",
            valueMs = state.audioDelayMs,
            onNudge = onNudgeAudio,
        )

        Spacer(Modifier.height(18.dp))
        SectionLabel("Subtitle offset")
        OffsetRow(
            label = "Subtitle sync",
            valueMs = state.subtitleDelayMs,
            onNudge = onNudgeSubtitle,
        )

        Spacer(Modifier.height(22.dp))
        HintLine("BACK closes · OK selects · Left/Right adjusts offsets")
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun HintLine(text: String) {
    Text(text = text, color = TextSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(6.dp))
}

/**
 * The overlay's focusable primitive — same deterministic pattern as the
 * browse cards (plain clickable + interaction source, scale + amber border),
 * tuned for dense rows instead of posters. [onKeyLeftRight] receives key
 * events while focused; returning false lets the focus engine move on.
 */
@Composable
private fun SettingsRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onKeyLeftRight: ((androidx.compose.ui.input.key.KeyEvent) -> Boolean)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "rowScale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.dp else 0.dp,
        animationSpec = tween(durationMillis = 100),
        label = "rowBorder",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) PanelBright else androidx.compose.ui.graphics.Color.Transparent)
            .border(borderWidth, Accent, RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (focused && onKeyLeftRight != null &&
                    event.type == KeyEventType.KeyDown
                ) {
                    onKeyLeftRight(event)
                } else {
                    false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        // Selection marker (engine track id of the active row).
        Text(
            text = if (selected) "●" else " ",
            color = Accent,
            fontSize = 12.sp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) Accent else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = trailing,
                color = if (trailing != "0.0 s") Accent else TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** Offset row: LEFT/RIGHT nudges ±100 ms, OK resets to zero. */
@Composable
private fun OffsetRow(
    label: String,
    valueMs: Long,
    onNudge: (Long) -> Unit,
) {
    SettingsRow(
        label = label,
        selected = valueMs != 0L,
        trailing = PlaybackMath.formatDelayLabel(valueMs),
        onClick = { onNudge(-valueMs) }, // OK resets to 0
        onKeyLeftRight = { event ->
            when (event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    onNudge(-OFFSET_STEP_MS)
                    true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    onNudge(OFFSET_STEP_MS)
                    true
                }

                else -> false
            }
        },
    )
}

private const val OFFSET_STEP_MS = 100L
