package com.cinedrive.tv.ui.show

import com.cinedrive.tv.domain.model.MediaItem

/** One season of a show, episodes in playback order. */
data class SeasonBucket(
    val season: Int,
    val episodes: List<MediaItem>,
)

/**
 * Kodi-style season grouping for the show detail page.
 *
 *  • Episodes with no season number land in season 0 (specials) — the Kodi
 *    convention — and the specials bucket sorts LAST so real seasons come
 *    first.
 *  • Within a bucket: episode number ascending; episodes without a number
 *    keep display order after the numbered ones.
 */
fun groupBySeason(episodes: List<MediaItem>): List<SeasonBucket> {
    if (episodes.isEmpty()) return emptyList()
    return episodes
        .groupBy { it.season ?: 0 }
        .map { (season, eps) ->
            val ordered = eps.sortedWith(
                compareBy({ it.episode ?: Int.MAX_VALUE }, { it.displayName })
            )
            SeasonBucket(season, ordered)
        }
        .sortedBy { if (it.season == 0) Int.MAX_VALUE else it.season }
}
