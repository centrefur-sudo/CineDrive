package com.cinedrive.tv.di

import com.cinedrive.tv.data.repository.LibraryRepositoryImpl
import com.cinedrive.tv.data.repository.StorageRepositoryImpl
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.domain.repository.StorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: StorageRepositoryImpl): StorageRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository
}
