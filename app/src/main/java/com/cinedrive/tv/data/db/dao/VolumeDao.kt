package com.cinedrive.tv.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.cinedrive.tv.data.db.entity.VolumeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VolumeDao {

    @Upsert
    suspend fun upsert(volume: VolumeEntity)

    @Query("SELECT * FROM volumes ORDER BY displayName")
    fun observeAll(): Flow<List<VolumeEntity>>

    @Query("SELECT * FROM volumes WHERE volumeUuid = :uuid")
    suspend fun getByUuid(uuid: String): VolumeEntity?

    @Query("SELECT * FROM volumes WHERE volumeUuid = :uuid")
    fun observeByUuid(uuid: String): Flow<VolumeEntity?>

    /** Refresh the last-seen stamp without touching firstSeenAtMs. */
    @Query("UPDATE volumes SET lastSeenAtMs = :timestampMs, lastMountPath = :mountPath WHERE volumeUuid = :uuid")
    suspend fun touch(uuid: String, mountPath: String, timestampMs: Long)
}
