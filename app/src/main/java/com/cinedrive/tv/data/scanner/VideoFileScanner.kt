package com.cinedrive.tv.data.scanner

import com.cinedrive.tv.domain.model.VideoFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Recursive file-system walker for USB volumes.
 *
 * Design notes:
 *  • java.io.File walks — valid on API 29 (legacy storage) and API 30+
 *    (All-files-access). MediaStore is deliberately NOT used: TV boxes keep
 *    notoriously incomplete media indexes for removable drives, and we need
 *    Kodi-style sidecar files (nfo/poster/fanart) that MediaStore ignores.
 *  • Exactly one listFiles() per directory; subtitle/artwork/nfo binding is
 *    resolved from that single snapshot, so every directory is touched once.
 *  • Cancellation-aware: the coroutine state is checked between directories,
 *    so an unplug mid-scan or a navigate-away cancels promptly.
 *  • Deterministic output: results are sorted by path, which keeps Room
 *    upserts stable and makes tests readable.
 */
@Singleton
class VideoFileScanner @Inject constructor() {

    suspend fun scan(
        root: File,
        volumeUuid: String,
        onProgress: suspend (indexed: Int) -> Unit = {},
    ): List<VideoFile> = withContext(Dispatchers.IO) {
        require(root.isDirectory) { "Volume root is not a directory: $root" }
        val found = ArrayList<VideoFile>(256)
        walkDirectory(root, volumeUuid, found, onProgress)
        found.sortBy { it.path }
        found
    }

    private suspend fun walkDirectory(
        dir: File,
        volumeUuid: String,
        out: MutableList<VideoFile>,
        onProgress: suspend (Int) -> Unit,
        depth: Int = 0,
        inheritedShowNfo: File? = null,
    ) {
        coroutineContext.ensureActive()
        if (depth > MAX_DEPTH || dir.name in SKIP_DIRS || dir.name.startsWith(".")) return

        val entries = dir.listFiles() ?: return
        if (entries.isEmpty()) return

        val videos = ArrayList<File>()
        val subs = ArrayList<File>()
        val images = ArrayList<File>()
        var movieNfo: File? = null
        var showNfo: File? = null

        for (entry in entries) {
            if (entry.isDirectory) continue
            when (entry.extension.lowercase()) {
                in VIDEO_EXTS -> videos += entry
                in SUBTITLE_EXTS -> subs += entry
                in IMAGE_EXTS -> images += entry
                NFO_EXT -> when {
                    entry.nameWithoutExtension.equals(MOVIE_NFO, true) -> movieNfo = entry
                    entry.nameWithoutExtension.equals(TVSHOW_NFO, true) -> showNfo = entry
                }
            }
        }

        // The nearest tvshow.nfo wins: a Season subfolder inside a show tree
        // inherits it, a nested show root overrides it.
        val effectiveShowNfo = showNfo ?: inheritedShowNfo

        for (video in videos) {
            val base = video.nameWithoutExtension
            out += VideoFile(
                volumeUuid = volumeUuid,
                path = video.absolutePath,
                displayName = base,
                sizeBytes = video.length(),
                lastModifiedMs = video.lastModified(),
                // Exact-name subtitle first ("Movie.srt" beats "Movie.en.srt"),
                // then alphabetical — deterministic for auto-select in Step 5.
                subtitles = subs
                    .filter { matchesSubtitle(base, it.nameWithoutExtension) }
                    .sortedWith(
                        compareBy(
                            { !it.nameWithoutExtension.equals(base, true) },
                            { it.name.lowercase() }
                        )
                    )
                    .map { it.absolutePath },
                posterPath = images
                    .firstOrNull { it.nameWithoutExtension.lowercase() in POSTER_NAMES }
                    ?.absolutePath,
                fanartPath = images
                    .firstOrNull { it.nameWithoutExtension.lowercase() in FANART_NAMES }
                    ?.absolutePath,
                // Per-file "<name>.nfo" wins; Kodi's movie.nfo is the fallback
                // for single-movie folders. tvshow.nfo is consumed in Step 3
                // when show/season grouping is applied.
                nfoPath = entries
                    .firstOrNull {
                        it.isFile &&
                            it.extension.equals(NFO_EXT, true) &&
                            it.nameWithoutExtension.equals(base, true)
                    }
                    ?.absolutePath
                    ?: movieNfo?.absolutePath,
                showNfoPath = effectiveShowNfo?.absolutePath,
            )
        }
        if (videos.isNotEmpty()) onProgress(out.size)

        for (child in entries) {
            if (child.isDirectory && child.name !in SKIP_DIRS && !child.name.startsWith(".")) {
                walkDirectory(child, volumeUuid, out, onProgress, depth + 1, effectiveShowNfo)
            }
        }
    }

    /**
     * "Movie" matches "Movie.srt", "Movie.en.srt", "Movie.forced.vtt" — the
     * Kodi convention of language/flag suffixes after a dot.
     */
    private fun matchesSubtitle(videoBase: String, subtitleBase: String): Boolean =
        subtitleBase.equals(videoBase, true) ||
            subtitleBase.startsWith("$videoBase.", true)

    private companion object {
        /** Spec set: .mp4, .mkv, .avi, .webm. */
        val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "webm")
        val SUBTITLE_EXTS = setOf("srt", "vtt")
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp")
        const val NFO_EXT = "nfo"
        val POSTER_NAMES = setOf("poster", "cover", "folder")
        val FANART_NAMES = setOf("fanart", "backdrop")
        const val MOVIE_NFO = "movie"
        const val TVSHOW_NFO = "tvshow"

        val SKIP_DIRS = setOf(
            "lost+found", "LOST.DIR", "Android", "\$RECYCLE.BIN",
            "System Volume Information", "recycle",
        )
        const val MAX_DEPTH = 12
    }
}
