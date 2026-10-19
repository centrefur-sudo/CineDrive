package com.cinedrive.tv.domain.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the shared playback rules (90 %-watched, seek clamping,
 * resume policy, HUD clocks and labels).
 */
class PlaybackMathTest {

    // ── isWatched (90 % rule) ───────────────────────────────────────────

    @Test
    fun `below 90 percent is not watched`() {
        assertFalse(PlaybackMath.isWatched(8_900, 10_000))
    }

    @Test
    fun `at 90 percent is watched`() {
        assertTrue(PlaybackMath.isWatched(9_000, 10_000))
    }

    @Test
    fun `unknown duration is never watched`() {
        assertFalse(PlaybackMath.isWatched(3_600_000, 0))
    }

    // ── seekTarget ──────────────────────────────────────────────────────

    @Test
    fun `seek clamps to zero at the start`() {
        assertEquals(0, PlaybackMath.seekTarget(5_000, -60_000, 100_000))
    }

    @Test
    fun `seek clamps near the end`() {
        // Duration 100 s → upper bound 99.5 s.
        assertEquals(99_500, PlaybackMath.seekTarget(98_000, 10_000, 100_000))
    }

    @Test
    fun `seek with unknown duration has no upper clamp`() {
        assertEquals(500_000, PlaybackMath.seekTarget(400_000, 100_000, 0))
    }

    // ── resumeStartPosition ─────────────────────────────────────────────

    @Test
    fun `zero resume starts at zero`() {
        assertEquals(0, PlaybackMath.resumeStartPosition(0, 100_000))
    }

    @Test
    fun `resume with unknown duration passes through`() {
        assertEquals(42_000, PlaybackMath.resumeStartPosition(42_000, 0))
    }

    @Test
    fun `resume inside the tail window restarts`() {
        // 99.5 s of a 100 s file = finished → restart.
        assertEquals(0, PlaybackMath.resumeStartPosition(99_500, 100_000))
    }

    @Test
    fun `normal resume inside the safe range passes through`() {
        // 90 s of a 100 s file: below the 2 s tail-restart window and under
        // the seek-margin bound — respected as-is.
        assertEquals(90_000, PlaybackMath.resumeStartPosition(90_000, 100_000))
    }

    // ── formatClock ─────────────────────────────────────────────────────

    @Test
    fun `clock renders minutes and seconds`() {
        assertEquals("00:00", PlaybackMath.formatClock(0))
        assertEquals("00:59", PlaybackMath.formatClock(59_999))
        assertEquals("12:05", PlaybackMath.formatClock(12 * 60_000 + 5_000))
    }

    @Test
    fun `clock renders hours without leading zero`() {
        assertEquals("1:02:03", PlaybackMath.formatClock(3_723_000))
    }

    @Test
    fun `negative clock clamps to zero`() {
        assertEquals("00:00", PlaybackMath.formatClock(-5))
    }

    // ── seekFeedbackLabel ───────────────────────────────────────────────

    @Test
    fun `seek feedback renders seconds`() {
        assertEquals("+10 s", PlaybackMath.seekFeedbackLabel(10_000))
        assertEquals("-10 s", PlaybackMath.seekFeedbackLabel(-10_000))
    }

    @Test
    fun `seek feedback collapses whole minutes`() {
        assertEquals("+1 min", PlaybackMath.seekFeedbackLabel(60_000))
        assertEquals("-2 min", PlaybackMath.seekFeedbackLabel(-120_000))
    }

    @Test
    fun `seek feedback keeps non-round values in seconds`() {
        assertEquals("+90 s", PlaybackMath.seekFeedbackLabel(90_000))
    }

    // ── formatDelayLabel ────────────────────────────────────────────────

    @Test
    fun `delay labels show sign and tenths`() {
        assertEquals("0.0 s", PlaybackMath.formatDelayLabel(0))
        assertEquals("+0.2 s", PlaybackMath.formatDelayLabel(200))
        assertEquals("-0.3 s", PlaybackMath.formatDelayLabel(-300))
        assertEquals("+1.5 s", PlaybackMath.formatDelayLabel(1_500))
    }
}
