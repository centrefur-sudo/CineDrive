package com.cinedrive.tv.domain.player

import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the auto-next-episode picker: same-season walking, season
 * rollover (incl. missing seasons and Kodi specials), the unnumbered
 * fallback and non-episode rejection.
 */
class EpisodeNavigatorTest {

    private fun episode(id: Long, season: Int, ep: Int) = MediaItem(
        id = id,
        volumeUuid = "vol",
        path = "/x/S${"%02d".format(season)}E${"%02d".format(ep)}.mkv",
        displayName = "S${"%02d".format(season)}E${"%02d".format(ep)}",
        kind = MediaItemKind.EPISODE,
        title = "Episode $id",
        plot = null,
        director = null,
        year = null,
        rating = null,
        runtimeMin = 42,
        season = season,
        episode = ep,
        sizeBytes = 1,
        lastModifiedMs = 0,
    )

    private fun unnumbered(id: Long, name: String) = MediaItem(
        id = id,
        volumeUuid = "vol",
        path = "/x/$name",
        displayName = name,
        kind = MediaItemKind.EPISODE,
        title = name,
        plot = null,
        director = null,
        year = null,
        rating = null,
        runtimeMin = 42,
        sizeBytes = 1,
        lastModifiedMs = 0,
    )

    private fun movie(id: Long) = episode(id, season = 1, ep = 1).copy(kind = MediaItemKind.MOVIE)

    @Test
    fun `next episode within the same season`() {
        val current = episode(1, 1, 1)
        val next = EpisodeNavigator.nextEpisode(
            current,
            listOf(current, episode(2, 1, 2), episode(3, 1, 3)),
        )
        assertEquals(2L, next?.id)
    }

    @Test
    fun `numbering gaps are skipped`() {
        val current = episode(1, 1, 1)
        val next = EpisodeNavigator.nextEpisode(
            current,
            listOf(current, episode(3, 1, 3)),
        )
        assertEquals(3L, next?.id)
    }

    @Test
    fun `season rollover picks the first episode of the next season`() {
        val current = episode(5, 1, 5)
        val next = EpisodeNavigator.nextEpisode(
            current,
            listOf(
                episode(1, 1, 1), episode(5, 1, 5),
                episode(6, 2, 1), episode(7, 2, 2),
            ),
        )
        assertEquals(6L, next?.id)
    }

    @Test
    fun `missing seasons are skipped`() {
        val current = episode(1, 1, 1)
        val next = EpisodeNavigator.nextEpisode(
            current,
            listOf(current, episode(9, 3, 1), episode(10, 3, 2)),
        )
        assertEquals(9L, next?.id)
    }

    @Test
    fun `specials roll into season one`() {
        val special = episode(1, 0, 1)
        val next = EpisodeNavigator.nextEpisode(
            special,
            listOf(special, episode(2, 1, 1)),
        )
        assertEquals(2L, next?.id)
    }

    @Test
    fun `last episode of the show has no successor`() {
        val last = episode(2, 1, 2)
        assertNull(EpisodeNavigator.nextEpisode(last, listOf(episode(1, 1, 1), last)))
    }

    @Test
    fun `movies never auto-next`() {
        val film = movie(1)
        assertNull(EpisodeNavigator.nextEpisode(film, listOf(film, movie(2))))
    }

    @Test
    fun `unnumbered episodes fall back to list order`() {
        val a = unnumbered(1, "a - Pilot")
        val b = unnumbered(2, "b - Middle")
        val c = unnumbered(3, "c - Finale")
        assertEquals(3L, EpisodeNavigator.nextEpisode(b, listOf(a, b, c))?.id)
        assertNull(EpisodeNavigator.nextEpisode(c, listOf(a, b, c)))
    }
}
