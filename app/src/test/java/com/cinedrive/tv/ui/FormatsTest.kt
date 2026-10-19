package com.cinedrive.tv.ui

import com.cinedrive.tv.ui.util.episodeBadge
import com.cinedrive.tv.ui.util.formatResumeMs
import com.cinedrive.tv.ui.util.progressFraction
import com.cinedrive.tv.ui.util.seasonLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the pure browse formatting helpers.
 */
class FormatsTest {

    // ── progressFraction ───────────────────────────────────────────────────

    @Test
    fun `no resume position means no bar`() {
        assertNull(progressFraction(0, 45))
        assertNull(progressFraction(-5, 45))
    }

    @Test
    fun `unknown runtime means no bar`() {
        assertNull(progressFraction(1000, null))
        assertNull(progressFraction(1000, 0))
    }

    @Test
    fun `fraction is clamped into the zero-one range`() {
        assertEquals(0.5f, progressFraction(30 * 60_000L, 60)!!, 0.0001f)
        assertEquals(1f, progressFraction(10 * 60 * 60_000L, 60)!!, 0.0001f)
    }

    // ── formatResumeMs ─────────────────────────────────────────────────────

    @Test
    fun `zero and negative resume labels are empty`() {
        assertEquals("", formatResumeMs(0))
        assertEquals("", formatResumeMs(-1))
    }

    @Test
    fun `minutes only below one hour`() {
        assertEquals("13 min", formatResumeMs(13 * 60_000L + 40_000L))
    }

    @Test
    fun `hours and minutes`() {
        assertEquals("1 h 13 min", formatResumeMs(73 * 60_000L))
        assertEquals("2 h", formatResumeMs(120 * 60_000L))
    }

    // ── season / episode labels ────────────────────────────────────────────

    @Test
    fun `season zero is Specials`() {
        assertEquals("Specials", seasonLabel(0))
        assertEquals("Season 1", seasonLabel(1))
        assertEquals("Season 12", seasonLabel(12))
    }

    @Test
    fun `episode badge handles all combinations`() {
        assertEquals("S01E04", episodeBadge(1, 4))
        assertEquals("S12E03", episodeBadge(12, 3))
        assertEquals("S01", episodeBadge(1, null))
        assertEquals("E04", episodeBadge(null, 4))
        assertNull(episodeBadge(null, null))
    }
}
