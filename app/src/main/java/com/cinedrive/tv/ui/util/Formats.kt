package com.cinedrive.tv.ui.util

/**
 * Pure formatting/browsing helpers — kept free of Android and Compose types
 * so they are unit-testable on the JVM (CI runs testDebugUnitTest).
 */

/** 0..1 resume progress for the card overlay, or null when not drawable. */
fun progressFraction(resumePositionMs: Long, runtimeMin: Int?): Float? {
    if (resumePositionMs <= 0) return null
    if (runtimeMin == null || runtimeMin <= 0) return null
    return (resumePositionMs.toFloat() / (runtimeMin * 60_000f)).coerceIn(0f, 1f)
}

/** "13 min" / "1 h 13 min" for the resume affordance; "" for nothing. */
fun formatResumeMs(positionMs: Long): String {
    if (positionMs <= 0) return ""
    val totalMinutes = positionMs / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$totalMinutes min"
    }
}

/** Human label for a season number (Kodi convention: season 0 = specials). */
fun seasonLabel(season: Int): String =
    if (season == 0) "Specials" else "Season $season"

/** "S01E04" episode badge, tolerant of unknown numbers. */
fun episodeBadge(season: Int?, episode: Int?): String? = when {
    season == null && episode == null -> null
    episode == null -> "S%02d".format(season)
    season == null -> "E%02d".format(episode)
    else -> "S%02dE%02d".format(season, episode)
}
