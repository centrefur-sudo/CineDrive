package com.cinedrive.tv.data.scanner

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class VideoFileScannerTest {

    @get:Rule
    val tmp = org.junit.rules.TemporaryFolder()

    @Test
    fun `binds subtitles artwork and per-file nfo to sibling video`() = runTest {
        val dir = tmp.newFolder("Movies")
        File(dir, "Big Buck Bunny.mkv").writeText("v")
        File(dir, "Big Buck Bunny.en.srt").writeText("s")
        File(dir, "Big Buck Bunny.forced.vtt").writeText("s")
        File(dir, "poster.jpg").writeText("p")
        File(dir, "fanart.jpg").writeText("f")
        File(dir, "Big Buck Bunny.nfo").writeText("n")

        val files = VideoFileScanner().scan(tmp.root, "TEST-UUID")

        assertEquals(1, files.size)
        val file = files.single()
        assertEquals("TEST-UUID", file.volumeUuid)
        assertEquals("Big Buck Bunny", file.displayName)
        // Exact-name subtitle ranks first, then alphabetical.
        assertEquals(
            listOf(
                File(dir, "Big Buck Bunny.en.srt").absolutePath,
                File(dir, "Big Buck Bunny.forced.vtt").absolutePath,
            ),
            file.subtitles
        )
        assertEquals(File(dir, "poster.jpg").absolutePath, file.posterPath)
        assertEquals(File(dir, "fanart.jpg").absolutePath, file.fanartPath)
        assertEquals(File(dir, "Big Buck Bunny.nfo").absolutePath, file.nfoPath)
    }

    @Test
    fun `falls back to movie_nfo when per-file nfo is missing`() = runTest {
        val dir = tmp.newFolder("SingleMovie")
        File(dir, "Rocketman.mp4").writeText("v")
        File(dir, "movie.nfo").writeText("n")

        val file = VideoFileScanner().scan(tmp.root, "UUID-1").single()

        assertEquals(File(dir, "movie.nfo").absolutePath, file.nfoPath)
    }

    @Test
    fun `video without extras yields empty bindings`() = runTest {
        val dir = tmp.newFolder("Bare")
        File(dir, "clip.webm").writeText("v")

        val file = VideoFileScanner().scan(tmp.root, "UUID-2").single()

        assertEquals(0, file.subtitles.size)
        assertNull(file.posterPath)
        assertNull(file.fanartPath)
        assertNull(file.nfoPath)
    }

    @Test
    fun `skips hidden junk and system directories`() = runTest {
        val root = tmp.newFolder("Root")
        listOf(".hidden", "lost+found", "LOST.DIR", "Android", "System Volume Information")
            .forEach { name ->
                val junk = File(root, name).apply { mkdirs() }
                File(junk, "nope.mp4").writeText("v")
            }
        File(root, "visible.mkv").writeText("v")

        val files = VideoFileScanner().scan(root, "UUID-3")

        assertEquals(listOf("visible"), files.map { it.displayName })
    }

    @Test
    fun `indexes nested season folders recursively`() = runTest {
        val show = File(tmp.newFolder("Shows"), "Gravity Falls")
        val season = File(show, "Season 01").apply { mkdirs() }
        File(season, "S01E01.mkv").writeText("v")
        File(show, "poster.jpg").writeText("p") // show-level poster, episode dir has none

        val files = VideoFileScanner().scan(tmp.root, "UUID-4")

        assertEquals(1, files.size)
        assertNull(files.single().posterPath) // binding is same-directory only
    }

    @Test
    fun `respects max depth cap`() = runTest {
        var dir = tmp.newFolder("Deep")
        repeat(14) { dir = File(dir, "L$it").apply { mkdirs() } }
        File(dir, "too-deep.mp4").writeText("v")

        val files = VideoFileScanner().scan(tmp.root, "UUID-5")

        assertEquals(0, files.size)
    }

    @Test
    fun `episodes inherit tvshow_nfo from show root`() = runTest {
        val show = File(tmp.newFolder("Shows"), "Gravity Falls")
        val season = File(show, "Season 01").apply { mkdirs() }
        File(show, "tvshow.nfo").writeText("show")
        File(season, "S01E01.mkv").writeText("v")
        File(season, "S01E02.mkv").writeText("v")
        // A nested show overrides the inherited marker.
        val nested = File(show, "Nested Show").apply { mkdirs() }
        File(nested, "tvshow.nfo").writeText("nested")
        File(nested, "S01E01.mkv").writeText("v")

        val files = VideoFileScanner().scan(tmp.root, "UUID-6").sortedBy { it.path }

        assertEquals(3, files.size)
        val season01 = files.filter { it.path.contains("Season 01") }
        season01.forEach {
            assertEquals(File(show, "tvshow.nfo").absolutePath, it.showNfoPath)
        }
        files.first { it.path.contains("Nested Show") }.let {
            assertEquals(File(nested, "tvshow.nfo").absolutePath, it.showNfoPath)
        }
    }
}
