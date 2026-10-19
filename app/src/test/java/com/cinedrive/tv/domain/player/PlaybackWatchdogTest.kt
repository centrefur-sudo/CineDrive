package com.cinedrive.tv.domain.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWatchdogTest {

    private fun tick(
        hasStarted: Boolean = false,
        playing: Boolean = false,
        pausedByUser: Boolean = false,
        sinceProgress: Long = 0L,
        sinceEvent: Long = 0L,
        recoveries: Int = 0,
    ) = PlaybackWatchdog.onTick(
        hasStarted = hasStarted,
        playing = playing,
        pausedByUser = pausedByUser,
        timeSinceProgressMs = sinceProgress,
        timeSinceEventMs = sinceEvent,
        recoveries = recoveries,
    )

    @Test
    fun `healthy playing media needs no action`() {
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(hasStarted = true, playing = true, sinceProgress = 1_000),
        )
    }

    @Test
    fun `frozen clock while playing recovers at threshold`() {
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(hasStarted = true, playing = true, sinceProgress = PlaybackWatchdog.PROGRESS_STALL_MS - 1),
        )
        assertEquals(
            PlaybackWatchdog.Action.Recover,
            tick(hasStarted = true, playing = true, sinceProgress = PlaybackWatchdog.PROGRESS_STALL_MS),
        )
    }

    @Test
    fun `stall after exhausting the recovery budget is fatal`() {
        assertEquals(
            PlaybackWatchdog.Action.Recover,
            tick(hasStarted = true, playing = true, sinceProgress = 11_000, recoveries = 1),
        )
        assertEquals(
            PlaybackWatchdog.Action.Fatal,
            tick(hasStarted = true, playing = true, sinceProgress = 11_000, recoveries = 2),
        )
    }

    @Test
    fun `silent open is recovered then fatal`() {
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(sinceEvent = PlaybackWatchdog.OPEN_STALL_MS - 1),
        )
        assertEquals(
            PlaybackWatchdog.Action.Recover,
            tick(sinceEvent = PlaybackWatchdog.OPEN_STALL_MS),
        )
        assertEquals(
            PlaybackWatchdog.Action.Fatal,
            tick(sinceEvent = PlaybackWatchdog.OPEN_STALL_MS + 5_000, recoveries = 2),
        )
    }

    @Test
    fun `recent events while opening are not a stall`() {
        // Long files over USB can legally open for tens of seconds while
        // Buffering events keep arriving.
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(sinceProgress = 20_000, sinceEvent = 500),
        )
    }

    @Test
    fun `user pause is never recovered`() {
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(hasStarted = true, playing = false, pausedByUser = true, sinceProgress = 60_000),
        )
        // Even a paused-before-first-frame session (no events for a minute).
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(pausedByUser = true, sinceEvent = 60_000),
        )
    }

    @Test
    fun `buffering state is not a mid-play stall`() {
        // playing=false while buffering → no kick (branch one needs playing).
        assertEquals(
            PlaybackWatchdog.Action.None,
            tick(hasStarted = true, playing = false, sinceProgress = 20_000),
        )
    }
}
