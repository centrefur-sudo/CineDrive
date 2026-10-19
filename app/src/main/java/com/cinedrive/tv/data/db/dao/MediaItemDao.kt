package com.cinedrive.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cinedrive.tv.data.db.entity.MediaItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Projection of just the playback-state columns, used by the indexer to
 * re-attach progress onto re-scanned rows without clobbering it.
 */
data class PlaybackSlice(
    val id: Long,
    val path: String,
    val resumePositionMs: Long,
    val watched: Boolean,
    val lastPlayedAtMs: Long?,
    /** Generated frame from the previous row — re-scans must not lose it. */
    val thumbPath: String?,
)

/** Files still missing card art after sidecar/NFO binding (thumb pass input). */
data class ThumbSlice(
    val id: Long,
    val path: String,
    val sizeBytes: Long,
)

/** Lightweight count projection for the smoke screen / library stats. */
data class KindCount(val kind: String, val count: Int)

@Dao
interface MediaItemDao {

    @Upsert
    suspend fun upsertAll(items: List<MediaItemEntity>)

    @Query(
        "SELECT id, path, resumePositionMs, watched, lastPlayedAtMs, thumbPath " +
            "FROM media_items WHERE volumeUuid = :volumeUuid"
    )
    suspend fun playbackSlices(volumeUuid: String): List<PlaybackSlice>

    @Query("SELECT * FROM media_items WHERE volumeUuid = :volumeUuid ORDER BY title COLLATE NOCASE")
    fun observeByVolume(volumeUuid: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE volumeUuid = :volumeUuid AND kind = 'MOVIE' " +
            "ORDER BY title COLLATE NOCASE"
    )
    fun observeMovies(volumeUuid: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE volumeUuid = :volumeUuid AND kind = 'EPISODE' " +
            "ORDER BY showTitle COLLATE NOCASE, season, episode, path"
    )
    fun observeEpisodes(volumeUuid: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE volumeUuid = :volumeUuid AND showFolderPath = :showFolderPath " +
            "ORDER BY season, episode, displayName"
    )
    fun observeEpisodesOfShow(volumeUuid: String, showFolderPath: String): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE volumeUuid = :volumeUuid " +
            "AND resumePositionMs > 0 AND watched = 0 " +
            "ORDER BY lastPlayedAtMs DESC LIMIT 20"
    )
    fun observeContinueWatching(volumeUuid: String): Flow<List<MediaItemEntity>>

    /**
     * Cross-volume queries for the browse UI (Step 4). The UI filters out
     * rows whose volume is currently unmounted, so a yanked drive empties
     * from the screen even before the next prune pass.
     */
    @Query("SELECT * FROM media_items WHERE kind = 'MOVIE' ORDER BY title COLLATE NOCASE")
    fun observeAllMovies(): Flow<List<MediaItemEntity>>

    @Query(
        "SELECT * FROM media_items WHERE resumePositionMs > 0 AND watched = 0 " +
            "ORDER BY lastPlayedAtMs DESC LIMIT 20"
    )
    fun observeAllContinueWatching(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id = :id")
    fun observeById(id: Long): Flow<MediaItemEntity?>

    @Query("SELECT COUNT(*) FROM media_items WHERE volumeUuid = :volumeUuid")
    suspend fun countForVolume(volumeUuid: String): Int

    @Query(
        "SELECT kind, COUNT(*) AS count FROM media_items " +
            "WHERE volumeUuid = :volumeUuid GROUP BY kind"
    )
    suspend fun countsByKind(volumeUuid: String): List<KindCount>

    /**
     * Playback-state write path from the player (Step 5): position, watched
     * flag and last-played stamp in one statement. The 90 %-watched rule is
     * computed by the caller.
     */
    @Query(
        "UPDATE media_items SET resumePositionMs = :positionMs, watched = :watched, " +
            "lastPlayedAtMs = :lastPlayedAtMs WHERE id = :id"
    )
    suspend fun updateProgress(id: Long, positionMs: Long, watched: Boolean, lastPlayedAtMs: Long)

    @Query("UPDATE media_items SET watched = :watched WHERE id = :id")
    suspend fun setWatched(id: Long, watched: Boolean)

    /** Thumbnail write path — one row per extracted frame, UI Flow re-emits. */
    @Query("UPDATE media_items SET thumbPath = :thumbPath WHERE id = :id")
    suspend fun setThumbPath(id: Long, thumbPath: String)

    /** Titles with no sidecar/NFO art and no generated frame yet. */
    @Query(
        "SELECT id, path, sizeBytes FROM media_items " +
            "WHERE volumeUuid = :volumeUuid AND posterPath IS NULL AND thumbPath IS NULL"
    )
    suspend fun missingArtSlices(volumeUuid: String): List<ThumbSlice>

    /**
     * Prune rows for files that vanished from the drive: anything not touched
     * by the current scan (older stamp) is deleted. O(1) bind vars, no giant
     * NOT IN lists.
     */
    @Query(
        "DELETE FROM media_items WHERE volumeUuid = :volumeUuid AND lastIndexedAtMs < :scanStartedAtMs"
    )
    suspend fun pruneStale(volumeUuid: String, scanStartedAtMs: Long)
}
