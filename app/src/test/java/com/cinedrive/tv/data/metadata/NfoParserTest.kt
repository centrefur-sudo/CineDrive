package com.cinedrive.tv.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfoParserTest {

    private val parser = NfoParser()

    @Test
    fun `parses modern movie nfo with ratings block and premiered date`() {
        val nfo = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes" ?>
            <movie>
              <title>Big Buck Bunny</title>
              <originaltitle>Peuterbig</originaltitle>
              <plot>A large bunny takes revenge on three rodents.</plot>
              <outline>A bunny takes revenge.</outline>
              <ratings>
                <rating name="imdb" default="true" max="10"><value>7.6</value></rating>
                <rating name="themoviedb" max="10"><value>6.9</value></rating>
              </ratings>
              <premiered>2008-04-10</premiered>
              <runtime>10</runtime>
              <director>Sacha Goedegebure</director>
              <thumb spoof="false">https://example.com/poster.jpg</thumb>
              <thumb spoiler="true">https://example.com/spoiler.jpg</thumb>
            </movie>
        """.trimIndent()

        val data = parser.parse(nfo)!!

        assertEquals(NfoKind.MOVIE, data.kind)
        assertEquals("Big Buck Bunny", data.title)
        assertEquals("Peuterbig", data.originalTitle)
        assertEquals("A large bunny takes revenge on three rodents.", data.plot)
        assertEquals("Sacha Goedegebure", data.director)
        // Year extracted from <premiered>; default rating wins over tmdb.
        assertEquals(2008, data.year)
        assertEquals(7.6f, data.rating)
        assertEquals(10, data.runtimeMin)
        // First non-spoiler thumb.
        assertEquals("https://example.com/poster.jpg", data.thumb)
    }

    @Test
    fun `parses legacy movie nfo with plain rating and year`() {
        val nfo = """
            <movie>
              <title>Rocketman</title>
              <rating>7.3</rating>
              <year>2019</year>
              <runtime>121 min</runtime>
              <director>Dexter Fletcher</director>
              <director>Another One</director>
            </movie>
        """.trimIndent()

        val data = parser.parse(nfo)!!

        assertEquals(NfoKind.MOVIE, data.kind)
        assertEquals(7.3f, data.rating)
        assertEquals(2019, data.year)
        // "121 min" → 121.
        assertEquals(121, data.runtimeMin)
        // Multiple directors joined.
        assertEquals("Dexter Fletcher, Another One", data.director)
    }

    @Test
    fun `parses episode nfo with season episode and showtitle`() {
        val nfo = """
            <episodedetails>
              <title>War of the Coprophages</title>
              <showtitle>The X-Files</showtitle>
              <season>3</season>
              <episode>12</episode>
              <aired>1996-01-05</aired>
              <plot>Mulder investigates robotic insect sightings.</plot>
              <durationinseconds>2700</durationinseconds>
            </episodedetails>
        """.trimIndent()

        val data = parser.parse(nfo)!!

        assertEquals(NfoKind.EPISODE, data.kind)
        assertEquals("War of the Coprophages", data.title)
        assertEquals("The X-Files", data.showTitle)
        assertEquals(3, data.season)
        assertEquals(12, data.episode)
        assertEquals(1996, data.year)
        // 2700 s → 45 min via stream-details fallback.
        assertEquals(45, data.runtimeMin)
    }

    @Test
    fun `parses tvshow nfo`() {
        val nfo = """
            <tvshow>
              <title>Breaking Bad</title>
              <plot>A chemistry teacher turns to cooking meth.</plot>
              <premiered>2008-01-20</premiered>
              <ratings><rating name="imdb" default="true" max="10"><value>9.5</value></rating></ratings>
              <thumb>https://example.com/show.jpg</thumb>
            </tvshow>
        """.trimIndent()

        val data = parser.parse(nfo)!!

        assertEquals(NfoKind.TV_SHOW, data.kind)
        assertEquals("Breaking Bad", data.title)
        assertEquals(2008, data.year)
        assertEquals(9.5f, data.rating)
        assertEquals("https://example.com/show.jpg", data.thumb)
    }

    @Test
    fun `survives BOM leading junk and trailing garbage after root`() {
        val nfo = "\uFEFF   some notes in the file\n<movie><title>OK</title><year>2001</year></movie>\n<bogus/>"
        val data = parser.parse(nfo)!!
        assertEquals("OK", data.title)
        assertEquals(2001, data.year)
    }

    @Test
    fun `returns null for malformed xml`() {
        assertNull(parser.parse("<movie><title>Broken"))
        assertNull(parser.parse(""))
        assertNull(parser.parse("not xml at all"))
        assertNull(parser.parse("<html><body>wrong root</body></html>"))
    }

    @Test
    fun `plot falls back outline is kept separately`() {
        val nfo = "<movie><outline>Short line</outline></movie>"
        val data = parser.parse(nfo)!!
        assertNull(data.plot)
        assertEquals("Short line", data.outline)
    }
}
