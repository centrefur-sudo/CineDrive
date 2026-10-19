package com.cinedrive.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cinedrive.tv.domain.model.StorageAccessLevel
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.ui.storage.StorageViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * Step 3 smoke-test screen — proves the storage + library engine on real
 * hardware: permission gate → live USB volume list → scan/NFO/classify →
 * Room cache with per-volume library stats.
 * Replaced by the full TV browse UI in Step 4.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: StorageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StorageSmokeScreen(viewModel)
        }
    }
}

private val Background = Color(0xFF0B0E14)
private val Panel = Color(0xFF141A28)
private val Accent = Color(0xFFFFB300)
private val TextPrimary = Color(0xFFF2F4F8)
private val TextSecondary = Color(0xFF9AA3B2)
private val Ok = Color(0xFF8BC34A)

@Composable
private fun StorageSmokeScreen(viewModel: StorageViewModel) {
    val access by viewModel.access.collectAsState()
    val volumes by viewModel.volumes.collectAsState()
    val libraryStats by viewModel.libraryStats.collectAsState()
    val cachedItems by viewModel.cachedItems.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Re-check permissions and pick up newly attached drives on every resume.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshAccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp)
    ) {
        Text(
            text = "CineDrive · Step 3 — library database",
            color = Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        when (access) {
            StorageAccessLevel.GRANTED -> VolumePanel(
                volumes = volumes,
                libraryStats = libraryStats,
                cachedItems = cachedItems,
                onRescan = viewModel::rescan,
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

@Composable
private fun PermissionGate(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Text(message, color = TextPrimary, fontSize = 16.sp)
    Spacer(Modifier.height(16.dp))
    Button(onClick = onAction) { Text(actionLabel) }
}

@Composable
private fun VolumePanel(
    volumes: List<UsbVolume>,
    libraryStats: Map<String, String>,
    cachedItems: Int,
    onRescan: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(onClick = onRescan) { Text("Rescan") }
        Text(
            text = "Attached USB volumes: ${volumes.size}  ·  Room cache: $cachedItems items",
            color = TextPrimary,
            fontSize = 16.sp
        )
    }
    Spacer(Modifier.height(16.dp))

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(volumes, key = { it.uuid }) { volume ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Panel)
                    .padding(16.dp)
            ) {
                Text(
                    text = "● ${volume.displayName}  [${volume.uuid}]",
                    color = if (volume.isMounted) Ok else TextSecondary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(text = volume.mountPath, color = TextSecondary, fontSize = 13.sp)
                libraryStats[volume.uuid]?.let {
                    Text(text = "Indexed: $it", color = Accent, fontSize = 13.sp)
                }
            }
        }
    }
}
