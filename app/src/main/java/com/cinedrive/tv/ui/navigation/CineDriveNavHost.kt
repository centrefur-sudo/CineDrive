package com.cinedrive.tv.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cinedrive.tv.ui.detail.MediaDetailScreen
import com.cinedrive.tv.ui.grid.MoviesGridScreen
import com.cinedrive.tv.ui.grid.ShowsGridScreen
import com.cinedrive.tv.ui.home.HomeScreen
import com.cinedrive.tv.ui.player.PlayerPlaceholderScreen
import com.cinedrive.tv.ui.show.ShowDetailScreen

/**
 * Route table. Show keys are passed as query arguments (URL-encoded) because
 * folder paths contain "/" — path segments with encoded slashes are a
 * navigation gotcha, query args are bulletproof.
 */
object Routes {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val SHOWS = "shows"
    const val SHOW_DETAIL = "show?volumeUuid={volumeUuid}&folder={folder}"
    const val ITEM_DETAIL = "item/{itemId}"
    const val PLAYER = "player/{itemId}"

    fun showDetail(volumeUuid: String, folderPath: String): String =
        "show?volumeUuid=${Uri.encode(volumeUuid)}&folder=${Uri.encode(folderPath)}"

    fun itemDetail(itemId: Long): String = "item/$itemId"

    fun player(itemId: Long): String = "player/$itemId"
}

@Composable
fun CineDriveNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onOpenItem = { id -> navController.navigate(Routes.itemDetail(id)) },
                onOpenShow = { uuid, folder ->
                    navController.navigate(Routes.showDetail(uuid, folder))
                },
                onSeeAllMovies = { navController.navigate(Routes.MOVIES) },
                onSeeAllShows = { navController.navigate(Routes.SHOWS) },
            )
        }

        composable(Routes.MOVIES) {
            MoviesGridScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { id -> navController.navigate(Routes.itemDetail(id)) },
            )
        }

        composable(Routes.SHOWS) {
            ShowsGridScreen(
                onBack = { navController.popBackStack() },
                onOpenShow = { uuid, folder ->
                    navController.navigate(Routes.showDetail(uuid, folder))
                },
            )
        }

        composable(
            route = Routes.SHOW_DETAIL,
            arguments = listOf(
                navArgument("volumeUuid") {
                    type = NavType.StringType
                    defaultValue = ""
                },
                navArgument("folder") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ShowDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenEpisode = { id -> navController.navigate(Routes.itemDetail(id)) },
            )
        }

        composable(
            route = Routes.ITEM_DETAIL,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) {
            MediaDetailScreen(
                onBack = { navController.popBackStack() },
                onPlay = { id -> navController.navigate(Routes.player(id)) },
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) {
            PlayerPlaceholderScreen(onBack = { navController.popBackStack() })
        }
    }
}
