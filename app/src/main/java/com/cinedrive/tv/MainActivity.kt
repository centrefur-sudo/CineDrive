package com.cinedrive.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cinedrive.tv.sync.LibrarySyncController
import com.cinedrive.tv.ui.navigation.CineDriveNavHost
import com.cinedrive.tv.ui.theme.CineDriveTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Single-activity Compose host (landscape, LEANBACK_LAUNCHER entry point).
 *
 * The activity itself only does two things:
 *  1. hosts the navigation graph, and
 *  2. nudges [LibrarySyncController] on every ON_RESUME — returning from the
 *     all-files-access settings page (or from background with a newly
 *     plugged drive) re-checks permissions and kicks off indexing.
 *
 * Everything else (permission gate UI, browse screens, sync status) lives in
 * the ui/ packages; the controller is app-scoped and keeps indexing while
 * the user navigates between screens.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var sync: LibrarySyncController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CineDriveTheme {
                CineDriveNavHost()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                sync.refreshAccess()
            }
        }
    }
}
