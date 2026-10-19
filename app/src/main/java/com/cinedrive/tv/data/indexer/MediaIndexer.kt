package com.cinedrive.tv.data.indexer

import androidx.room.withTransaction
import com.cinedrive.tv.data.db.CineDriveDatabase
import com.cinedrive.tv.data.db.dao.MediaItemDao
import com.cinedrive.tv.data.db.dao.ShowDao
import com.cinedrive.tv.data.db.dao.VolumeDao
import com.cinedrive.tv.data.db.entity.MediaItemEntity
import com.cinedrive.tv.data.db.entity.ShowEntity
import com.cinedrive.tv.data.db.entity.VolumeEntity
import com.cinedrive.tv.data.metadata.NfoData
import com.cinedrive.tv.data.metadata.NfoKind
import com.cinedrive.tv.data.metadata.NfoParser
import com.cinedrive.tv.data.scanner.MediaClassifier
import com.cinedrive.tv.data.scanner.VideoFileScanner
import com.cinedrive.tv.domain.model.MediaItemKind
import com.cinedrive.tv.domain.model.UsbVolume
import com.cinedrive.tv.domain.model.VideoFile
import com.cinedrive.tv.domain.repository.LibraryIndexResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Per-file plan produced by pass 1 (classification). */
private data class Planned(
    val file: VideoFile,
    val nfo: NfoData?,
    val decision: MediaClassifier.Decision,
)

/** Mutable metadata accumulator for one show folder (pass 2). */
private class ShowAccumulator {
    var title: String? = null
    var plot: String? = null
    var year: Int? = null
    var posterPath: String? = null
    var fanartPath: String? = null
    var nfoPath: String? = null
}

/**
 * Orchestrates one full index run of a USB volume into the Room cache:
 *
 *   scanner.walk → nfoParser.parse → MediaClassifier → Room transaction
 *
 * Guarantees:
 *  • NFO files are parsed once per unique path (tvshow.nfo is shared by every
 *    episode of the show, so caching matters on 200-episode drives).
 *  • Playback state (resume/watched/last-played) is re-attached from the
 *    existing rows — re-scans refresh metadata, never the user's progress.
 *  • Rows whose file disappeared since the last scan are pruned in the same
 *    transaction, using the [MediaItemEntity.lastIndexedAtMs] stamp.
 *  • Everything lands in ONE transaction: a crash mid-index leaves the
 *    previous consistent library intact.
 */
@Singleton
class MediaIndexer @Inject constructor(
    private val db: CineDriveDatabase,
    private val scanner: VideoFileScanner,
    private val nfoParser: NfoParser,
    private val volumeDao: VolumeDao,
    private val mediaItemDao: MediaItemDao,
    private val showDao: ShowDao,
) {

    suspend fun index(
        volume: UsbVolume,
        onProgress: suspend (indexed: Int) -> Unit = {},
    ): LibraryIndexResult = withContext(Dispatchers.IO) {
        val scanStartedAt = System.currentTimeMillis()
        val files = scanner.scan(File(volume.mountPath), volume.uuid, onProgress)

        // NFO parse cache — null results are cached too (missing/broken NFO).
        val nfoCache = HashMap<String, NfoData?>()

        fun parseNfo(path: String?): NfoData? {
            if (path == null) return null
            return nfoCache.getOrPut(path) {
                val file = File(path)
                if (!file.isFile) return@getOrPut null
                try {
                    nfoParser.parse(file.readText())
                } catch (error: Throwable) {
                    null // drive yanked mid-read — treat as missing metadata
                }
            }
        }

        // Pass 1: classify every file.
        val planned = files.map { file ->
            coroutineContext.ensureActive()
            val nfo = parseNfo(file.nfoPath)
            val parent = file.path.substringBeforeLast('/', missingDelimiterValue = file.path)
            val grandparent = parent.substringBeforeLast('/', missingDelimiterValue = parent)
                .takeIf { it.isNotEmpty() && it != parent }
            val decision = MediaClassifier.classify(
                fileNameWithoutExtension = file.displayName,
                nfo = nfo,
                showNfoFolderPath = file.showNfoPath?.let { File(it).parent },
                parentDirPath = parent,
                grandparentDirPath = grandparent,
            )
            Planned(file, nfo, decision)
        }

        // Pass 2: accumulate show metadata from tvshow.nfo and episodes.
        val showAccumulators = LinkedHashMap<String, ShowAccumulator>()

        for (plan in planned) {
            if (plan.decision.kind != MediaItemKind.EPISODE) continue
            coroutineContext.ensureActive()
            val key = plan.decision.showFolderPath
                ?: plan.file.path.substringBeforeLast('/', missingDelimiterValue = plan.file.path)
            val acc = showAccumulators.getOrPut(key) { ShowAccumulator() }

            if (plan.file.showNfoPath != null && acc.nfoPath == null) {
                acc.nfoPath = plan.file.showNfoPath
                val showNfo = parseNfo(plan.file.showNfoPath)
                if (showNfo?.kind == NfoKind.TV_SHOW) {
                    acc.title = showNfo.title
                    acc.plot = showNfo.plot ?: showNfo.outline
                    acc.year = showNfo.year
                    acc.posterPath = showNfo.thumb
                }
            }
            if (acc.title == null && plan.decision.showTitle != null) {
                acc.title = plan.decision.showTitle
            }
            // First episode's local artwork as a fallback (path-sorted scan
            // order makes this deterministic).
            if (acc.posterPath == null) acc.posterPath = plan.file.posterPath
            if (acc.fanartPath == null) acc.fanartPath = plan.file.fanartPath
        }

        // Pass 3: build entity rows with playback-state merge.
        val playbackByPath = mediaItemDao.playbackSlices(volume.uuid).associateBy { it.path }
        val entities = planned.map { plan ->
            val file = plan.file
            val nfo = plan.nfo
            val decision = plan.decision
            val isEpisode = decision.kind == MediaItemKind.EPISODE
            val previous = playbackByPath[file.path]
            val showKey = decision.showFolderPath
                ?: file.path.substringBeforeLast('/', missingDelimiterValue = file.path)
            MediaItemEntity(
                id = previous?.id ?: 0,
                volumeUuid = volume.uuid,
                path = file.path,
                displayName = file.displayName,
                kind = decision.kind.name,
                title = nfo?.title ?: cleanTitle(file.displayName),
                plot = nfo?.plot ?: nfo?.outline,
                director = nfo?.director,
                year = nfo?.year,
                rating = nfo?.rating,
                runtimeMin = nfo?.runtimeMin,
                showTitle = if (isEpisode) decision.showTitle else null,
                season = if (isEpisode) decision.season else null,
                episode = if (isEpisode) decision.episode else null,
                showFolderPath = if (isEpisode) showKey else null,
                subtitles = file.subtitles,
                posterPath = file.posterPath ?: nfo?.thumb,
                fanartPath = file.fanartPath,
                thumbPath = previous?.thumbPath,
                nfoPath = file.nfoPath,
                resumePositionMs = previous?.resumePositionMs ?: 0,
                watched = previous?.watched ?: false,
                lastPlayedAtMs = previous?.lastPlayedAtMs,
                sizeBytes = file.sizeBytes,
                lastModifiedMs = file.lastModifiedMs,
                lastIndexedAtMs = scanStartedAt,
            )
        }

        val showEntities = showAccumulators.map { (folder, acc) ->
            ShowEntity(
                volumeUuid = volume.uuid,
                folderPath = folder,
                title = acc.title ?: cleanTitle(File(folder).name),
                plot = acc.plot,
                year = acc.year,
                posterPath = acc.posterPath,
                fanartPath = acc.fanartPath,
                nfoPath = acc.nfoPath,
                lastIndexedAtMs = scanStartedAt,
            )
        }

        // Pass 4: single transaction — volumes registry, shows, items, prune.
        val now = System.currentTimeMillis()
        db.withTransaction {
            if (volumeDao.getByUuid(volume.uuid) == null) {
                volumeDao.upsert(
                    VolumeEntity(
                        volumeUuid = volume.uuid,
                        displayName = volume.displayName,
                        lastMountPath = volume.mountPath,
                        firstSeenAtMs = now,
                        lastSeenAtMs = now,
                        isRemovable = volume.isRemovable,
                    )
                )
            } else {
                volumeDao.touch(volume.uuid, volume.mountPath, now)
            }
            showDao.upsertAll(showEntities)
            mediaItemDao.upsertAll(entities)
            mediaItemDao.pruneStale(volume.uuid, scanStartedAt)
            showDao.pruneStale(volume.uuid, scanStartedAt)
        }

        LibraryIndexResult(
            filesScanned = entities.size,
            movies = entities.count { it.kind == MediaItemKind.MOVIE.name },
            episodes = entities.count { it.kind == MediaItemKind.EPISODE.name },
            shows = showEntities.size,
        )
    }

    /**
     * "Big.Buck.Bunny_2008" → "Big Buck Bunny 2008". No release-name
     * tag-stripping at v1: those heuristics misclassify more than they fix;
     * the .nfo title is the real fix path.
     */
    private fun cleanTitle(displayName: String): String =
        displayName.replace('_', ' ').replace('.', ' ')
            .trim()
            .trim { it == '-' }
            .trim()
}
