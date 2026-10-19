package com.cinedrive.tv.ui

import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind
import com.cinedrive.tv.ui.show.SeasonBucket
import com.cinedrive.tv.ui.show.groupBySeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the show-detail season grouping (Kodi specials convention).
 */
class SeasonGrouperTest {

    private fun episode(
        season: Int?,
        number: Int?,
        name: String = "ep",
    ) = MediaItem(
        id = 0,
        volumeUuid = "uuid",
        path = "/x/$name.mkv",
        displayName = name,
        kind = MediaItemKind.EPISODE,
        title = name,
        plot = null,
        director = null,
        year = null,
        rating = null,
        runtimeMin = null,
        showTitle = "Show",
        season = season,
        episode = number,
        sizeBytes = 1,
        lastModifiedMs = 0,
    )

    @Test
    fun `empty input yields no buckets`() {
        assertTrue(groupBySeason(emptyList()).isEmpty())
    }

    @Test
    fun `buckets are ordered by season ascending`() {
        val buckets = groupBySeason(
            listOf(
                episode(2, 1, "s2e1"),
                episode(1, 2, "s1e2"),
                episode(1, 1, "s1e1"),
            )
        )
        assertEquals(listOf(1, 2), buckets.map { it.season })
        assertEquals(listOf("s1e1", "s1e2"), buckets[0].episodes.map { it.displayName })
    }

    @Test
    fun `missing season becomes specials and sorts last`() {
        val buckets = groupBySeason(
            listOf(
                episode(null, null, "extra"),
                episode(1, 1, "s1e1"),
                episode(0, 3, "special3"),
            )
        )
        // Season 1 first; the specials bucket (season 0, incl. unknown) last.
        assertEquals(1, buckets[0].season)
        assertEquals(
            listOf(0),
            buckets.drop(1).map { it.season },
        )
        val specials = buckets.last().episodes.map { it.displayName }
        assertEquals(listOf("special3", "extra"), specials)
    }

    @Test
    fun `numbered episodes come before unnumbered within a season`() {
        val buckets = groupBySeason(
            listOf(
                episode(1, null, "zzz-unnumbered"),
                episode(1, 2, "b-e2"),
                episode(1, 10, "a-e10"),
                episode(1, 1, "c-e1"),
            )
        )
        assertEquals(
            listOf("c-e1", "b-e2", "a-e10", "zzz-unnumbered"),
            buckets.single().episodes.map { it.displayName },
        )
    }

    @Test
    fun `single season collapses to one bucket`() {
        val buckets = groupBySeason(listOf(episode(1, 1), episode(1, 2)))
        assertEquals(1, buckets.size)
        assertTrue(buckets[0] is SeasonBucket)
        assertEquals(2, buckets[0].episodes.size)
    }

    @Test
    fun `season zero is the specials bucket label case`() {
        val buckets = groupBySeason(listOf(episode(0, 1, "sp1"), episode(0, 2, "sp2")))
        assertEquals(listOf(0), buckets.map { it.season })
        assertEquals(2, buckets[0].episodes.size)
    }
}
