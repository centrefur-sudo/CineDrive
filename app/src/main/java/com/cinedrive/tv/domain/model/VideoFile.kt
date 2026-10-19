package com.cinedrive.tv.domain.model

/**
 * A discovered video file plus everything that could be bound to it from the
 * file system alone (sidecar subtitles, artwork, .nfo path).
 *
 * .nfo *content* is parsed in Step 3 and merged into the Room cache; here we
 * only record where the file lives.
 */
data class VideoFile(
    /** Volume this file belongs to — pairs with [UsbVolume.uuid]. */
    val volumeUuid: String,
    /** Absolute path — the natural unique key within a volume. */
    val path: String,
    /** File name without extension, e.g. "Big Buck Bunny". */
    val displayName: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    /** Absolute paths of sidecar subtitles (.srt/.vtt sharing the base name). */
    val subtitles: List<String> = emptyList(),
    /** poster.jpg / cover.jpg / folder.jpg found next to the file. */
    val posterPath: String? = null,
    /** fanart.jpg / backdrop.jpg found next to the file. */
    val fanartPath: String? = null,
    /** &lt;name&gt;.nfo next to the file, or movie.nfo in the same directory. */
    val nfoPath: String? = null,
    /**
     * Absolute path of the nearest tvshow.nfo above (or beside) this file,
     * inherited down the directory tree — the show root marker used by the
     * Step 3 classifier/indexer for episode grouping.
     */
    val showNfoPath: String? = null,
)
