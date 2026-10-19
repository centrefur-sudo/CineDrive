package com.cinedrive.tv.data.repository

import com.cinedrive.tv.data.db.dao.MediaItemDao
import com.cinedrive.tv.data.db.dao.ShowDao
import com.cinedrive.tv.data.db.entity.MediaItemEntity
import com.cinedrive.tv.data.db.entity.ShowEntity
import com.cinedrive.tv.data.indexer.MediaIndexer
import com.cinedrive.tv.domain.model.MediaItem
import com.cinedrive.tv.domain.model.MediaItemKind
import com.cinedrive.tv.domain.model.Show
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.repository.LibraryIndexResult
import com.cinedrive.tv.domain.repository.LibraryRepository
import com.cinedrive.tv.domain.repository.StorageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed implementation of the library API. The only class in the app
 * that maps Room entities ↔ domain models, so the boundary stays one-way:
 * presentation never touches a Room type.
 */
@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val storage: StorageRepository,
    private val indexer: MediaIndexer,
    private val mediaItemDao: MediaItemDao,
    private val showDao: ShowDao,
) : LibraryRepository {

    override suspend fun indexVolume(
        volumeUuid: String,
        onProgress: suspend (indexed: Int) -> Unit,
    ): LibraryIndexResult {
        val volume: UsbVolume = storage.volumes.value.firstOrNull { it.uuid == volumeUuid }
            ?: error("Volume $volumeUuid is not attached")
        check(volume.isMounted) { "Volume $volumeUuid is not mounted" }
        return indexer.index(volume, onProgress)
    }

    override fun observeMovies(volumeUuid: String): Flow<List<MediaItem>> =
        mediaItemDao.observeMovies(volumeUuid).map { list -> list.map { it.toDomain() } }

    override fun observeEpisodes(volumeUuid: String): Flow<List<MediaItem>> =
        mediaItemDao.observeEpisodes(volumeUuid).map { list -> list.map { it.toDomain() } }

    override fun observeShows(volumeUuid: String): Flow<List<Show>> =
        showDao.observeByVolume(volumeUuid).map { list -> list.map { it.toDomain() } }

    override fun observeEpisodesOfShow(
        volumeUuid: String,
        showFolderPath: String,
    ): Flow<List<MediaItem>> =
        mediaItemDao.observeEpisodesOfShow(volumeUuid, showFolderPath)
            .map { list -> list.map { it.toDomain() } }

    override fun observeContinueWatching(volumeUuid: String): Flow<List<MediaItem>> =
        mediaItemDao.observeContinueWatching(volumeUuid).map { list -> list.map { it.toDomain() } }

    override suspend fun mediaItem(id: Long): MediaItem? =
        mediaItemDao.getById(id)?.toDomain()

    override suspend fun saveProgress(mediaItemId: Long, positionMs: Long, watched: Boolean) {
        mediaItemDao.updateProgress(
            id = mediaItemId,
            positionMs = positionMs,
            watched = watched,
            lastPlayedAtMs = System.currentTimeMillis(),
        )
    }

    override suspend fun markWatched(mediaItemId: Long, watched: Boolean) {
        mediaItemDao.setWatched(mediaItemId, watched)
    }
}

/** Entity → domain mapping. Kind strings are written by [MediaIndexer]. */
internal fun MediaItemEntity.toDomain(): MediaItem = MediaItem(
    id = id,
    volumeUuid = volumeUuid,
    path = path,
    displayName = displayName,
    kind = MediaItemKind.valueOf(kind),
    title = title,
    plot = plot,
    director = director,
    year = year,
    rating = rating,
    runtimeMin = runtimeMin,
    showTitle = showTitle,
    season = season,
    episode = episode,
    showFolderPath = showFolderPath,
    subtitles = subtitles,
    posterPath = posterPath,
    fanartPath = fanartPath,
    nfoPath = nfoPath,
    resumePositionMs = resumePositionMs,
    watched = watched,
    lastPlayedAtMs = lastPlayedAtMs,
    sizeBytes = sizeBytes,
    lastModifiedMs = lastModifiedMs,
)

internal fun ShowEntity.toDomain(): Show = Show(
    volumeUuid = volumeUuid,
    folderPath = folderPath,
    title = title,
    plot = plot,
    year = year,
    posterPath = posterPath,
    fanartPath = fanartPath,
    nfoPath = nfoPath,
)
