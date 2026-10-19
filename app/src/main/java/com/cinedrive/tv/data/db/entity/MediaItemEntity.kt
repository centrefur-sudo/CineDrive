package com.cinedrive.tv.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One playable video file with merged file-system, .nfo and playback state.
 *
 * Identity: (volumeUuid, path) is uniquely indexed — the same file seen in a
 * later scan updates its row instead of duplicating it. Playback columns
 * (resumePositionMs / watched / lastPlayedAtMs) are merged in by the indexer
 * from the previous row so re-scans never reset progress.
 *
 * [lastIndexedAtMs] powers O(1) pruning of stale rows: after a scan finishes,
 * every row of that volume not touched by the scan is deleted (the file was
 * removed from the drive or renamed).
 */
@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["volumeUuid", "path"], unique = true),
        Index(value = ["volumeUuid"]),
        Index(value = ["showFolderPath"]),
    ],
)
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumeUuid: String,
    val path: String,
    val displayName: String,
    /** MediaItemKind.name — enum kept out of Room for schema stability. */
    val kind: String,
    val title: String,
    val plot: String?,
    val director: String?,
    val year: Int?,
    val rating: Float?,
    val runtimeMin: Int?,
    val showTitle: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val showFolderPath: String? = null,
    /** Newline-joined subtitle paths, best match first (see Converters). */
    val subtitles: List<String> = emptyList(),
    val posterPath: String? = null,
    val fanartPath: String? = null,
    val nfoPath: String? = null,
    val resumePositionMs: Long = 0,
    val watched: Boolean = false,
    val lastPlayedAtMs: Long? = null,
    val sizeBytes: Long = 0,
    val lastModifiedMs: Long = 0,
    /** Scan timestamp — rows from older scans are pruned. */
    val lastIndexedAtMs: Long = 0,
)
