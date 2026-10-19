package com.cinedrive.tv.domain.model

/**
 * A fully resolved playable item from the on-device Room cache.
 *
 * This is the read model consumed by the browse UI (Step 4) and the player
 * (Step 5). It merges four sources of truth:
 *  1. the file system   — path, size, sidecar bindings (scanner, Step 2)
 *  2. the .nfo metadata — title, plot, director, year, rating, S/E numbers
 *  3. the volume UUID   — which physical drive this lives on
 *  4. playback state    — resume position, watched flag, last-played time
 *
 * Playback state survives re-scans: re-indexing the same drive refreshes
 * metadata but never resets the user's progress.
 */
data class MediaItem(
    val id: Long,
    /** Volume UUID this item belongs to — pairs with [UsbVolume.uuid]. */
    val volumeUuid: String,
    /** Absolute path on the volume — unique within the volume. */
    val path: String,
    /** File name without extension, e.g. "S01E04 - War of the Coprophages". */
    val displayName: String,
    val kind: MediaItemKind,
    /** Best display title: .nfo title, else cleaned-up file name. */
    val title: String,
    /** Long description from <plot>, falling back to <outline>. */
    val plot: String?,
    /** Comma-joined director list from the .nfo. */
    val director: String?,
    val year: Int?,
    val rating: Float?,
    /** Runtime in minutes (.nfo <runtime> or stream details). */
    val runtimeMin: Int?,
    /** Show title for episodes (from tvshow.nfo, episode .nfo or folder). */
    val showTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    /** For episodes: key into the shows table (folder of tvshow.nfo). */
    val showFolderPath: String? = null,
    /** Sidecar subtitles, best match first (scanner ordering). */
    val subtitles: List<String> = emptyList(),
    /** Local poster file, or first .nfo <thumb> URL as a fallback. */
    val posterPath: String? = null,
    val fanartPath: String? = null,
    /** Auto-extracted video frame for titles without sidecar/NFO art. */
    val thumbPath: String? = null,
    val nfoPath: String? = null,
    /** Resume offset in ms; 0 = never started / finished. */
    val resumePositionMs: Long = 0,
    /** 90 % watched rule is applied by the player in Step 5. */
    val watched: Boolean = false,
    val lastPlayedAtMs: Long? = null,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)
