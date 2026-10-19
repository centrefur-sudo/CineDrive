package com.cinedrive.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cinedrive.tv.data.db.entity.ShowEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {

    @Upsert
    suspend fun upsertAll(shows: List<ShowEntity>)

    @Query("SELECT * FROM shows WHERE volumeUuid = :volumeUuid ORDER BY title COLLATE NOCASE")
    fun observeByVolume(volumeUuid: String): Flow<List<ShowEntity>>

    @Query("SELECT * FROM shows WHERE volumeUuid = :volumeUuid AND folderPath = :folderPath")
    suspend fun getByKey(volumeUuid: String, folderPath: String): ShowEntity?

    @Query("SELECT COUNT(*) FROM shows WHERE volumeUuid = :volumeUuid")
    suspend fun countForVolume(volumeUuid: String): Int

    /** Same stamp-based pruning as media_items. */
    @Query("DELETE FROM shows WHERE volumeUuid = :volumeUuid AND lastIndexedAtMs < :scanStartedAtMs")
    suspend fun pruneStale(volumeUuid: String, scanStartedAtMs: Long)
}
