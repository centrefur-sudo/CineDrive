package com.cinedrive.tv.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cinedrive.tv.data.db.dao.MediaItemDao
import com.cinedrive.tv.data.db.dao.ShowDao
import com.cinedrive.tv.data.db.dao.VolumeDao
import com.cinedrive.tv.data.db.entity.MediaItemEntity
import com.cinedrive.tv.data.db.entity.ShowEntity
import com.cinedrive.tv.data.db.entity.VolumeEntity

/**
 * The on-device media cache.
 *
 * v1 tables:
 *  • volumes     — registry of physical USB drives (key = volume UUID)
 *  • media_items — every indexed video file + metadata + playback state
 *  • shows       — show root folders for episode grouping
 *
 * Schema JSON is exported to app/schemas/ (room.schemaLocation in the Gradle
 * KSP args) so future migrations can be validated against history.
 */
@Database(
    entities = [
        VolumeEntity::class,
        MediaItemEntity::class,
        ShowEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CineDriveDatabase : RoomDatabase() {

    abstract fun volumeDao(): VolumeDao

    abstract fun mediaItemDao(): MediaItemDao

    abstract fun showDao(): ShowDao

    companion object {
        const val NAME = "cinedrive.db"
    }
}
