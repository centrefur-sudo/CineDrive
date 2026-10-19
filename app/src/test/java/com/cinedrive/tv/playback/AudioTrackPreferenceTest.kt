package com.cinedrive.tv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * English-default audio rule: the container label decides, first match wins,
 * and unlabelled tracks keep the LibVLC default (null = no override).
 */
class AudioTrackPreferenceTest {

    private fun track(id: Int, name: String) = TrackInfo(id, name)

    @Test
    fun `prefers the labelled English track over earlier tracks`() {
        val tracks = listOf(
            track(0, "Track 1 - [Japanese] (AAC 2.0)"),
            track(1, "Track 2 - [English] (AC3 5.1)"),
            track(2, "Track 3 - [Commentary] (AAC 2.0)"),
        )
        assertEquals(1, LibVlcPlayerEngine.preferredEnglishTrack(tracks)?.id)
    }

    @Test
    fun `matches common English label spellings`() {
        val labels = listOf(
            "English",
            "ENG",
            "eng",
            "Track 1 - [English] (DTS)",
            "English (AC3 5.1)",
            "ENGLISH",
        )
        labels.forEach { label ->
            assertEquals(label, 7, LibVlcPlayerEngine.preferredEnglishTrack(listOf(track(7, label)))?.id)
        }
    }

    @Test
    fun `no false positives on other languages or commentary`() {
        val tracks = listOf(
            track(0, "Japanese"),
            track(1, "Deutsch"),
            track(2, "Français"),
            track(3, "Commentary"),
            track(4, "Undetermined"),
            track(5, "Stereo"),
        )
        assertNull(LibVlcPlayerEngine.preferredEnglishTrack(tracks))
    }

    @Test
    fun `first English track wins when several exist`() {
        val tracks = listOf(
            track(0, "English (AC3)"),
            track(1, "Director's English commentary"),
        )
        assertEquals(0, LibVlcPlayerEngine.preferredEnglishTrack(tracks)?.id)
    }

    @Test
    fun `empty list has no preference`() {
        assertNull(LibVlcPlayerEngine.preferredEnglishTrack(emptyList()))
    }
}
