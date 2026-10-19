package com.cinedrive.tv.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `subtitle list round-trips`() {
        val list = listOf("/storage/0403-0201/Movie.srt", "/storage/0403-0201/Movie.en.vtt")
        assertEquals(list, converters.toStringList(converters.fromStringList(list)))
    }

    @Test
    fun `empty list round-trips`() {
        assertEquals(emptyList<String>(), converters.toStringList(converters.fromStringList(emptyList())))
    }

    @Test
    fun `stored form is newline joined`() {
        assertEquals("a.srt\nb.vtt", converters.fromStringList(listOf("a.srt", "b.vtt")))
        assertTrue(converters.fromStringList(listOf("only.srt")).contains('\n').not())
    }
}
