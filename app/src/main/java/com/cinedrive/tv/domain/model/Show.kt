package com.cinedrive.tv.domain.model

/**
 * A TV show row: one per show folder (directory holding tvshow.nfo, or the
 * root of a Season-subfolder tree whose episodes matched the SxxExx pattern).
 *
 * Shows are deliberately per-volume (key = volumeUuid + folderPath): the same
 * drive in any TV port maps to the same show rows because the volume UUID is
 * stable.
 */
data class Show(
    val volumeUuid: String,
    /** Absolute path of the show root directory — the identity of the show. */
    val folderPath: String,
    val title: String,
    val plot: String?,
    val year: Int?,
    /** Local folder.jpg/poster, or tvshow.nfo <thumb> URL fallback. */
    val posterPath: String?,
    val fanartPath: String?,
    val nfoPath: String?,
)
