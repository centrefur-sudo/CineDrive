package com.cinedrive.tv.data.metadata

/**
 * Which Kodi .nfo flavour was detected in a file.
 */
enum class NfoKind { MOVIE, EPISODE, TV_SHOW }

/**
 * The subset of Kodi .nfo metadata CineDrive consumes.
 *
 * Sources merged by the parser:
 *  • legacy tags       — <title>, <plot>, <outline>, <year>, <runtime>…
 *  • modern ratings    — <ratings><rating name="…" default="true"><value>7.5</value>
 *  • legacy rating     — <rating>7.5</rating>
 *  • dates             — <premiered>/<aired> "2001-09-22" → year 2001
 *  • stream details    — <durationinseconds> as a runtime fallback
 */
data class NfoData(
    val kind: NfoKind,
    val title: String? = null,
    val originalTitle: String? = null,
    /** <plot>; the indexer falls back to [outline] when null. */
    val plot: String? = null,
    val outline: String? = null,
    /** All <director> values joined with ", ". */
    val director: String? = null,
    /** From <year>, else the leading year of <premiered>/<aired>. */
    val year: Int? = null,
    val rating: Float? = null,
    val runtimeMin: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val showTitle: String? = null,
    /** First non-spoiler <thumb> (local path or URL). */
    val thumb: String? = null,
)
