package com.cinedrive.tv.domain.player

import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind

/**
 * "Up next" picker for the auto-next-episode feature. A pure function over
 * the show's episode list (already volume-scoped by the repository), so the
 * season-walk rules live in one tested place:
 *
 *  1. next numbered episode inside the current season,
 *  2. else the first episode of the closest later season (season 0 specials
 *     sort below season 1, mirroring the Kodi convention used in Step 4),
 *  3. else the positional successor in season/episode/file order — the
 *     safety net for libraries whose episodes carry no S/E numbers at all.
 */
object EpisodeNavigator {

    fun nextEpisode(current: MediaItem, episodes: List<MediaItem>): MediaItem? {
        if (current.kind != MediaItemKind.EPISODE) return null
        val pool = episodes.filter { it.kind == MediaItemKind.EPISODE && it.id != current.id }
        if (pool.isEmpty()) return null

        val season = current.season
        val episode = current.episode

        if (season != null && episode != null) {
            // 1) Next numbered episode in the same season.
            pool.firstOrNull { it.season == season && it.episode != null && it.episode > episode }
                ?.let { return it }

            // 2) First episode of the closest later season.
            pool.filter { (it.season ?: Int.MIN_VALUE) > season }
                .minByOrNull { it.season ?: Int.MIN_VALUE }
                ?.let { nextSeason ->
                    return pool.filter { it.season == nextSeason.season }
                        .minWithOrNull(compareBy({ it.episode ?: Int.MAX_VALUE }, { it.displayName }))
                }
        }

        // 3) Positional fallback for unnumbered libraries.
        val ordered = (pool + current).sortedWith(
            compareBy(
                { it.season ?: Int.MAX_VALUE },
                { it.episode ?: Int.MAX_VALUE },
                { it.displayName },
            ),
        )
        val currentIndex = ordered.indexOfFirst { it.id == current.id }
        return ordered.getOrNull(currentIndex + 1)
    }
}
