package com.cinedrive.tv.data.scanner

import com.cinedrive.tv.data.metadata.NfoData
import com.cinedrive.tv.data.metadata.NfoKind
import com.cinedrive.tv.domain.model.MediaItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {

    @Test
    fun `sxxexx and nxnxn filename patterns are recognized`() {
        assertEquals(1 to 2, MediaClassifier.seasonEpisodeOf("Show S01E02 - Title"))
        assertEquals(10 to 305, MediaClassifier.seasonEpisodeOf("show.s10.e305.720p"))
        assertEquals(1 to 2, MediaClassifier.seasonEpisodeOf("Show 1x02"))
        assertEquals(4 to 17, MediaClassifier.seasonEpisodeOf("4x17 Out of Time"))
        assertNull(MediaClassifier.seasonEpisodeOf("Big Buck Bunny"))
        assertNull(MediaClassifier.seasonEpisodeOf("2012 Movie"))
    }

    @Test
    fun `season folders are detected`() {
        assertTrue(MediaClassifier.isSeasonFolder("Season 1"))
        assertTrue(MediaClassifier.isSeasonFolder("Season 01"))
        assertTrue(MediaClassifier.isSeasonFolder("S02"))
        assertFalse(MediaClassifier.isSeasonFolder("Season 1 Specials"))
        assertFalse(MediaClassifier.isSeasonFolder("Extras"))
    }

    @Test
    fun `episode nfo wins and fills missing season or episode from filename`() {
        val nfo = NfoData(
            kind = NfoKind.EPISODE,
            title = "Cat's in the Bag…",
            season = null,
            episode = null,
            showTitle = "Breaking Bad",
        )

        val decision = MediaClassifier.classify(
            fileNameWithoutExtension = "Breaking Bad S01E02",
            nfo = nfo,
            showNfoFolderPath = "/usb/BB",
            parentDirPath = "/usb/BB/Season 1",
            grandparentDirPath = "/usb/BB",
        )

        assertEquals(MediaItemKind.EPISODE, decision.kind)
        // NFO lacked both numbers → filename fallback.
        assertEquals(1, decision.season)
        assertEquals(2, decision.episode)
        assertEquals("Breaking Bad", decision.showTitle)
        assertEquals("/usb/BB", decision.showFolderPath)
    }

    @Test
    fun `movie nfo overrides episode-shaped filename`() {
        val nfo = NfoData(kind = NfoKind.MOVIE, title = "9x05 Movie Title")

        val decision = MediaClassifier.classify(
            fileNameWithoutExtension = "9x05 mislabeled",
            nfo = nfo,
            showNfoFolderPath = null,
            parentDirPath = "/usb/Movies",
            grandparentDirPath = "/usb",
        )

        assertEquals(MediaItemKind.MOVIE, decision.kind)
        assertNull(decision.showFolderPath)
    }

    @Test
    fun `sxxexx without nfo becomes episode with season parent promotion`() {
        val decision = MediaClassifier.classify(
            fileNameWithoutExtension = "X-Files S03E12",
            nfo = null,
            showNfoFolderPath = null,
            parentDirPath = "/usb/X-Files/Season 03",
            grandparentDirPath = "/usb/X-Files",
        )

        assertEquals(MediaItemKind.EPISODE, decision.kind)
        assertEquals(3, decision.season)
        assertEquals(12, decision.episode)
        // "Season 03" parent promotes the grandparent to show root.
        assertEquals("/usb/X-Files", decision.showFolderPath)
        assertEquals("X-Files", decision.showTitle)
    }

    @Test
    fun `sxxexx with tvshow nfo uses nfo folder as show root`() {
        val decision = MediaClassifier.classify(
            fileNameWithoutExtension = "S02E05",
            nfo = null,
            showNfoFolderPath = "/usb/Show Name",
            parentDirPath = "/usb/Show Name/Season 02",
            grandparentDirPath = "/usb/Show Name",
        )

        assertEquals(MediaItemKind.EPISODE, decision.kind)
        assertEquals("/usb/Show Name", decision.showFolderPath)
    }

    @Test
    fun `plain file without nfo is a movie`() {
        val decision = MediaClassifier.classify(
            fileNameWithoutExtension = "Big Buck Bunny",
            nfo = null,
            showNfoFolderPath = null,
            parentDirPath = "/usb/Movies",
            grandparentDirPath = "/usb",
        )

        assertEquals(MediaItemKind.MOVIE, decision.kind)
        assertNull(decision.showFolderPath)
    }

    @Test
    fun `folder names are cleaned into titles`() {
        assertEquals("Breaking Bad S01", MediaClassifier.cleanFolderName("Breaking.Bad_S01"))
        assertEquals("One Piece", MediaClassifier.cleanFolderName("One.Piece-"))
    }
}
