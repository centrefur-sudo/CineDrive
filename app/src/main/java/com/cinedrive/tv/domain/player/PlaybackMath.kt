package com.cinedrive.tv.domain.player

import kotlin.math.abs

/**
 * Pure playback arithmetic for the player (Step 5) — no Android or LibVLC
 * types, so the rules are unit-testable on the JVM (CI runs
 * testDebugUnitTest). The player UI, the ViewModel and the LibVLC engine all
 * share these exact rules; there is deliberately a single implementation.
 */
object PlaybackMath {

    /**
     * The 90 %-watched rule from the spec: once playback reaches 90 % of the
     * runtime, the item counts as seen — the player then clears the resume
     * point and flips the watched flag in one persist.
     */
    const val WATCHED_THRESHOLD = 0.9f

    /**
     * A resume point within the last two seconds is treated as "finished",
     * not "barely started" — restart from zero instead of playing 1 s of
     * credits.
     */
    private const val RESUME_TAIL_MS = 2_000L

    /** When the duration is known, seeks can never leave half a second of tail. */
    private const val SEEK_MARGIN_MS = 500L

    fun isWatched(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs * WATCHED_THRESHOLD

    /**
     * Clamp a seek to valid bounds. With an unknown duration (header not yet
     * parsed) only the lower bound is enforced.
     */
    fun seekTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
        val target = currentMs + deltaMs
        val upper = if (durationMs > 0) (durationMs - SEEK_MARGIN_MS).coerceAtLeast(0) else Long.MAX_VALUE
        return target.coerceIn(0L, upper)
    }

    /**
     * Where playback should actually start from a saved resume point:
     *  - zero / negative points start from the beginning,
     *  - points inside the tail window restart from zero (treat as finished),
     *  - everything else is clamped to leave a short margin at the end.
     * Called by the engine once the real duration is known.
     */
    fun resumeStartPosition(resumeMs: Long, durationMs: Long): Long {
        if (resumeMs <= 0) return 0
        if (durationMs <= 0) return resumeMs
        if (resumeMs >= durationMs - RESUME_TAIL_MS) return 0
        return resumeMs.coerceAtMost(durationMs - SEEK_MARGIN_MS)
    }

    /** "1:23:45" / "12:05" HUD clock; negative inputs clamp to zero. */
    fun formatClock(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }

    /** "→ +10 s" style seek feedback; whole minutes collapse to "min". */
    fun seekFeedbackLabel(deltaMs: Long): String {
        val magnitude = abs(deltaMs)
        val label = if (magnitude >= 60_000 && magnitude % 60_000 == 0L) {
            "${magnitude / 60_000} min"
        } else {
            "${magnitude / 1000} s"
        }
        return (if (deltaMs < 0) "-" else "+") + label
    }

    /** "+0.2 s" offset value for the quick-settings rows. */
    fun formatDelayLabel(delayMs: Long): String {
        val sign = when {
            delayMs > 0 -> "+"
            delayMs < 0 -> "-"
            else -> ""
        }
        val tenths = abs(delayMs) / 100.0
        return sign + "%.1f s".format(tenths)
    }
}
