package com.cinedrive.tv.di

import android.content.Context
import androidx.room.Room
import com.cinedrive.tv.data.db.CineDriveDatabase
import com.cinedrive.tv.data.db.dao.MediaItemDao
import com.cinedrive.tv.data.db.dao.ShowDao
import com.cinedrive.tv.data.db.dao.VolumeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the Room database and its DAOs. One process-wide instance; WAL
 * journaling (Room default) keeps writes from the indexer cheap while the UI
 * is reading live Flows.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CineDriveDatabase =
        Room.databaseBuilder(context, CineDriveDatabase::class.java, CineDriveDatabase.NAME)
            .build()

    @Provides
    fun provideVolumeDao(db: CineDriveDatabase): VolumeDao = db.volumeDao()

    @Provides
    fun provideMediaItemDao(db: CineDriveDatabase): MediaItemDao = db.mediaItemDao()

    @Provides
    fun provideShowDao(db: CineDriveDatabase): ShowDao = db.showDao()
}
