package com.cinedrive.tv.data.scanner

import com.cinedrive.tv.data.metadata.NfoData
import com.cinedrive.tv.data.metadata.NfoKind
import com.cinedrive.tv.domain.model.MediaItemKind
import java.io.File

/**
 * Decides whether a video file is a movie or an episode, and where the
 * episode's show root lives. Pure logic — no file I/O — so it is fully unit
 * testable.
 *
 * Decision order (Kodi-compatible):
 *  1. A parseable episode .nfo wins outright (values from XML, filename as
 *     fallback for missing season/episode).
 *  2. A parseable movie .nfo wins even if the file name says S01E02 (people
 *     misname files; explicit metadata beats heuristics).
 *  3. No .nfo → the file name pattern decides: "S01E02", "1x02" etc.
 *     means episode, anything else means movie.
 *
 * Show root resolution for episodes (most specific first):
 *  • directory holding the inherited tvshow.nfo (scanner provides the path)
 *  • parent directory, unless it looks like "Season 01" — then the
 *    grandparent (Kodi's Season-subfolder layout)
 */
object MediaClassifier {

    /** "S01E02", "S01.E02", "s01_e02", "S01E02A"… */
    private val SXXEXX = Regex(
        pattern = """S(\d{1,2})[\._\- ]?E(\d{1,3})""",
        options = RegexOption.IGNORE_CASE,
    )

    /** "1x02", "1x05b" (Babylon-5 style), requires separators around it. */
    private val NUMXNUM = Regex(
        pattern = """(?:^|[\._\- ])(\d{1,2})x(\d{1,3})(?:$|[\._\- ]|[A-Za-z])""",
        options = RegexOption.IGNORE_CASE,
    )

    /** Kodi season folder layout: "Season 1", "Season 01", "S01". */
    private val SEASON_DIR = Regex(
        pattern = """^(?:Season[\._\- ]*(\d{1,2})|S(\d{1,2}))$""",
        options = RegexOption.IGNORE_CASE,
    )

    /** Filename-based season/episode match, or null if not episode-shaped. */
    fun seasonEpisodeOf(fileNameWithoutExtension: String): Pair<Int, Int>? {
        SXXEXX.find(fileNameWithoutExtension)?.let { match ->
            return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
        }
        NUMXNUM.find(fileNameWithoutExtension)?.let { match ->
            return match.groupValues[1].toIntOrNull() to match.groupValues[2].toIntOrNull()
        }
        return null
    }

    /** True when the directory name is a Kodi "Season xx" folder. */
    fun isSeasonFolder(dirName: String): Boolean = SEASON_DIR.containsMatchIn(dirName.trim())

    /**
     * @param fileNameWithoutExtension e.g. "Breaking Bad S01E02"
     * @param nfo parsed .nfo of this file (own <name>.nfo or movie.nfo), or null
     * @param showNfoFolderPath absolute directory holding the inherited
     *   tvshow.nfo, or null when the drive has none above this file
     * @param parentDirPath absolute directory containing the video file
     * @param grandparentDirPath directory above [parentDirPath], or null
     */
    fun classify(
        fileNameWithoutExtension: String,
        nfo: NfoData?,
        showNfoFolderPath: String?,
        parentDirPath: String,
        grandparentDirPath: String?,
    ): Decision {
        // 1. Episode NFO is authoritative.
        if (nfo?.kind == NfoKind.EPISODE) {
            val fromFile = seasonEpisodeOf(fileNameWithoutExtension)
            return Decision(
                kind = MediaItemKind.EPISODE,
                season = nfo.season ?: fromFile?.first,
                episode = nfo.episode ?: fromFile?.second,
                showTitle = nfo.showTitle ?: cleanFolderName(File(parentDirPath).name),
                showFolderPath = showNfoFolderPath
                    ?: resolveShowFolder(parentDirPath, grandparentDirPath),
            )
        }

        // 2. Movie NFO is authoritative — overrides filename patterns.
        if (nfo?.kind == NfoKind.MOVIE) {
            return Decision(kind = MediaItemKind.MOVIE)
        }

        // 3. Heuristic on the file name (and inherited tvshow.nfo presence).
        val pattern = seasonEpisodeOf(fileNameWithoutExtension)
        return if (pattern != null) {
            Decision(
                kind = MediaItemKind.EPISODE,
                season = pattern.first,
                episode = pattern.second,
                showTitle = cleanFolderName(
                    File(resolveShowFolder(parentDirPath, grandparentDirPath) ?: parentDirPath).name
                ),
                showFolderPath = showNfoFolderPath
                    ?: resolveShowFolder(parentDirPath, grandparentDirPath),
            )
        } else {
            Decision(kind = MediaItemKind.MOVIE)
        }
    }

    /**
     * Show root for an episode without tvshow.nfo: a "Season xx" parent
     * promotes the grandparent to show root; otherwise the parent is it.
     */
    private fun resolveShowFolder(parentDirPath: String, grandparentDirPath: String?): String? =
        if (isSeasonFolder(File(parentDirPath).name)) grandparentDirPath else parentDirPath

    /**
     * Folder name → show title: "Breaking.Bad.S01" → "Breaking Bad S01".
     * Dots/underscores become spaces; stray leading/trailing dashes go away.
     */
    fun cleanFolderName(dirName: String): String =
        dirName.replace('_', ' ').replace('.', ' ')
            .trim()
            .trim { it == '-' }
            .trim()

    data class Decision(
        val kind: MediaItemKind,
        val season: Int? = null,
        val episode: Int? = null,
        val showTitle: String? = null,
        /** Key into the shows table; null for movies. */
        val showFolderPath: String? = null,
    )
}
