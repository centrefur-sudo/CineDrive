package com.cinedrive.tv.domain.repository

import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.Show
import kotlinx.coroutines.flow.Flow

/**
 * Result of indexing one volume into the Room cache.
 */
data class LibraryIndexResult(
    /** Video files found on the volume (equals rows written). */
    val filesScanned: Int,
    val movies: Int,
    val episodes: Int,
    /** Distinct show folders discovered. */
    val shows: Int,
)

/**
 * The library-facing domain API: everything the browse UI and player need
 * from the persisted media cache. Implementations hide Room entirely —
 * callers see only domain models and never a Room type.
 *
 * All observe* methods return cold Flows that re-emit whenever the underlying
 * tables change (re-scan, progress write), so the UI stays live across
 * indexing runs without manual refresh.
 */
interface LibraryRepository {

    /**
     * Scan the volume (via [com.cinedrive.tv.domain.repository.StorageRepository]
     * semantics), parse .nfo metadata, classify movies vs episodes, group shows
     * and persist everything in one transaction.
     *
     * Idempotent: re-running refreshes metadata, preserves playback progress
     * and removes rows for files that no longer exist.
     */
    suspend fun indexVolume(
        volumeUuid: String,
        onProgress: suspend (indexed: Int) -> Unit = {},
    ): LibraryIndexResult

    fun observeMovies(volumeUuid: String): Flow<List<MediaItem>>

    fun observeEpisodes(volumeUuid: String): Flow<List<MediaItem>>

    fun observeShows(volumeUuid: String): Flow<List<Show>>

    /** Episodes of one show, ordered by season → episode → path. */
    fun observeEpisodesOfShow(volumeUuid: String, showFolderPath: String): Flow<List<MediaItem>>

    /** Items with saved progress that are not yet finished, newest first. */
    fun observeContinueWatching(volumeUuid: String): Flow<List<MediaItem>>

    suspend fun mediaItem(id: Long): MediaItem?

    /** Persist the resume offset from the player (Step 5). */
    suspend fun saveProgress(mediaItemId: Long, positionMs: Long, watched: Boolean)

    /** Toggle the watched flag (user action or the 90 % rule). */
    suspend fun markWatched(mediaItemId: Long, watched: Boolean)
}
