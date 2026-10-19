package com.cinedrive.tv.ui.player

import android.app.Activity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.ErrorOrange
import com.cinedrive.tv.ui.theme.OkGreen
import com.cinedrive.tv.ui.theme.Panel
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary
import com.cinedrive.tv.ui.util.episodeBadge
import com.cinedrive.tv.domain.player.PlaybackMath

/**
 * Full-screen LibVLC player. All interaction is D-pad driven:
 *
 *  - OK / Enter        → play / pause (long-press opens quick settings)
 *  - LEFT / RIGHT      → ±10 s (hold to scrub at ±2 s per key repeat)
 *  - UP / DOWN         → ±1 min (hold scrubs at ±10 s per repeat)
 *  - MENU              → quick settings (audio/subtitle tracks + offsets)
 *  - BACK              → save progress and exit (closes the overlay first)
 *  - Media keys        → play/pause/FF/Rew as labelled on TV remotes
 *
 * The first key press on a hidden HUD performs its action AND re-shows the
 * HUD — no "wasted" tap to wake the controls.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onPlayNext: (Long) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()
    val state by viewModel.engineState.collectAsStateWithLifecycle()
    val hudVisible by viewModel.hudVisible.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val seekFeedback by viewModel.seekFeedback.collectAsStateWithLifecycle()
    val fatalError by viewModel.fatalError.collectAsStateWithLifecycle()
    val hwEnabled by viewModel.hwEnabled.collectAsStateWithLifecycle()

    // Keep the panel awake during playback (spec: FLAG_KEEP_SCREEN_ON).
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Auto-next-episode: replace the current player entry so BACK from the
    // next episode returns to the browse UI, not the previous episode.
    LaunchedEffect(Unit) {
        viewModel.playNext.collect { nextId -> onPlayNext(nextId) }
    }

    val rootFocus = remember { FocusRequester() }
    val overlayRowFocus = remember { FocusRequester() }
    var centerLongPress by remember { mutableStateOf(false) }

    // Focus robustness: requestFocus can silently fail while the first
    // frame is still attaching to the window — without focus the D-pad
    // (including OK = pause) is dead. Retry until the root really owns it.
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        repeat(FOCUS_RETRY_MAX) {
            if (rootFocused) return@LaunchedEffect
            runCatching { rootFocus.requestFocus() }
            kotlinx.coroutines.delay(FOCUS_RETRY_DELAY_MS)
        }
    }
    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            kotlinx.coroutines.delay(80)
            // Throws if the anchor row never composed (e.g. no tracks yet).
            runCatching { overlayRowFocus.requestFocus() }
        } else {
            rootFocus.requestFocus()
        }
    }

    fun handleKey(event: androidx.compose.ui.input.key.KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        val isDown = event.type == KeyEventType.KeyDown
        val isUp = event.type == KeyEventType.KeyUp
        val repeat = event.nativeKeyEvent.repeatCount

        return when {
            // ── Seek keys (transport mode only; inside the overlay the
            //    D-pad belongs to the settings rows) ─────────────────────
            isDown && code == KeyEvent.KEYCODE_DPAD_LEFT && !settingsOpen -> {
                viewModel.seekBy(if (repeat == 0) -SEEK_SHORT_MS else -SEEK_REPEAT_MS)
                true
            }

            isDown && code == KeyEvent.KEYCODE_DPAD_RIGHT && !settingsOpen -> {
                viewModel.seekBy(if (repeat == 0) SEEK_SHORT_MS else SEEK_REPEAT_MS)
                true
            }

            isDown && code == KeyEvent.KEYCODE_DPAD_UP && !settingsOpen -> {
                viewModel.seekBy(if (repeat == 0) SEEK_LONG_MS else SEEK_REPEAT_MS * 5)
                true
            }

            isDown && code == KeyEvent.KEYCODE_DPAD_DOWN && !settingsOpen -> {
                viewModel.seekBy(if (repeat == 0) -SEEK_LONG_MS else -SEEK_REPEAT_MS * 5)
                true
            }

            // ── OK / Enter: short press toggles, long press (first key
            //    repeat ≈ 500 ms hold) opens quick settings ───────────────
            isDown && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) -> {
                if (!settingsOpen) {
                    if (repeat == 1) {
                        centerLongPress = true
                        viewModel.openSettings()
                    }
                    true
                } else {
                    false
                }
            }

            isUp && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) -> {
                if (!settingsOpen) {
                    val fire = !centerLongPress
                    centerLongPress = false
                    if (fire) viewModel.togglePlayPause()
                    true
                } else {
                    false
                }
            }

            // ── MENU: quick settings toggle ──────────────────────────────
            isUp && (code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SETTINGS) -> {
                viewModel.toggleSettings()
                true
            }

            // ── Media keys on TV remotes ─────────────────────────────────
            isUp && code == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                viewModel.togglePlayPause(); true
            }

            isUp && code == KeyEvent.KEYCODE_MEDIA_PLAY -> {
                if (!state.playing) viewModel.togglePlayPause(); true
            }

            isUp && code == KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                if (state.playing) viewModel.togglePlayPause(); true
            }

            isUp && code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                viewModel.seekBy(SEEK_SHORT_MS); true
            }

            isUp && code == KeyEvent.KEYCODE_MEDIA_REWIND -> {
                viewModel.seekBy(-SEEK_SHORT_MS); true
            }

            else -> false
        }
    }

    // Back: close the overlay first, otherwise persist and leave.
    BackHandler {
        if (settingsOpen) {
            viewModel.closeSettings()
        } else {
            viewModel.exit(onExit = onBack)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { event -> handleKey(event) },
    ) {
        // LibVLC video surface (SurfaceView hardware overlay path).
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FrameLayout(ctx).also { container -> viewModel.attachVideo(container) }
            },
            onRelease = { viewModel.detachVideo() },
        )

        // Loading (before first frames) and buffering states.
        if (!state.hasStarted && fatalError == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = item?.let { "Opening ${it.title}…" } ?: "Opening…",
                    color = TextSecondary,
                    fontSize = 15.sp,
                )
            }
        } else if (state.buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Accent,
            )
        }

        // Seek feedback bubble.
        AnimatedVisibility(
            visible = seekFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = seekFeedback.orEmpty(),
                color = TextPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color(0xCC141A28), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }

        // Fatal playback error.
        if (fatalError != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Panel, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Playback error", color = ErrorOrange, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = fatalError.orEmpty(),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text("Press BACK to exit", color = Accent, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tip: Menu (or hold OK) → Hardware decoding → Off helps on boxes where video freezes",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Quick settings overlay (right panel).
        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            PlayerSettingsOverlay(
                state = state,
                hwEnabled = hwEnabled,
                firstRowFocus = overlayRowFocus,
                onAudioTrack = viewModel::setAudioTrack,
                onSubtitleTrack = viewModel::setSubtitleTrack,
                onNudgeAudio = viewModel::nudgeAudioDelay,
                onNudgeSubtitle = viewModel::nudgeSubtitleDelay,
                onToggleHw = viewModel::toggleHardwareDecoding,
            )
        }

        // Transport HUD.
        AnimatedVisibility(
            visible = hudVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            PlayerHud(
                title = item?.title.orEmpty(),
                badge = item?.let { episodeBadge(it.season, it.episode) },
                showTitle = item?.showTitle,
                watched = item?.watched == true,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                playing = state.playing,
            )
        }
    }

    // First interaction hint while media loads.
    LaunchedEffect(state.hasStarted) {
        if (state.hasStarted) viewModel.pokeHud()
    }
}

@Composable
private fun PlayerHud(
    title: String,
    badge: String?,
    showTitle: String?,
    watched: Boolean,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
) {
    val fraction = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0xE60B0E14),
                ),
            )
            .padding(horizontal = 56.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (badge != null) {
                Text(badge, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (showTitle != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    text = showTitle,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (watched) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "WATCHED",
                    color = OkGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Color.White.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .background(Accent),
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = PlaybackMath.formatClock(positionMs),
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "  /  ${PlaybackMath.formatClock(durationMs)}",
                color = TextSecondary,
                fontSize = 15.sp,
            )
            if (!playing) {
                Spacer(Modifier.width(14.dp))
                Text("Paused", color = TextSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "OK Play   ·   Left/Right ±10 s   ·   Up/Down ±1 min   ·   Menu Settings",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

private const val SEEK_SHORT_MS = 10_000L
private const val SEEK_LONG_MS = 60_000L
private const val SEEK_REPEAT_MS = 2_000L
private const val FOCUS_RETRY_MAX = 25
private const val FOCUS_RETRY_DELAY_MS = 200L
