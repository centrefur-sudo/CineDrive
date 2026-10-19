package com.cinedrive.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import com.cinedrive.tv.ui.detail.MediaDetailViewModel
import com.cinedrive.tv.ui.theme.Accent
import com.cinedrive.tv.ui.theme.Bg
import com.cinedrive.tv.ui.theme.TextPrimary
import com.cinedrive.tv.ui.theme.TextSecondary

/**
 * Step 4 navigation stub for the player destination: the full route
 * (itemId → LibVLC session) already exists, so Step 5 only swaps this
 * composable for the real player and every Play/Resume button keeps
 * working unchanged.
 */
@Composable
fun PlayerPlaceholderScreen(
    onBack: () -> Unit,
    viewModel: MediaDetailViewModel = hiltViewModel(),
) {
    val item by viewModel.item.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = item?.title ?: "Playback",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Playback arrives in Step 5 — LibVLC engine, D-pad transport " +
                "controls, quick settings (audio/subtitle tracks) and " +
                "auto-next-episode.",
            color = TextSecondary,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "…or press the Back key on the remote",
            color = Accent,
            fontSize = 12.sp,
        )
    }
}
