package com.cinedrive.tv.data.db.entity

import androidx.room.Entity

/**
 * A show root directory (Kodi-style): tvshow.nfo folder, or the derived root
 * of a Season-subfolder tree when only episode files with SxxExx names exist.
 *
 * Key is (volumeUuid, folderPath); episodes reference it via
 * [MediaItemEntity.showFolderPath].
 */
@Entity(tableName = "shows", primaryKeys = ["volumeUuid", "folderPath"])
data class ShowEntity(
    val volumeUuid: String,
    /** Absolute path of the show root directory. */
    val folderPath: String,
    val title: String,
    val plot: String?,
    val year: Int?,
    val posterPath: String?,
    val fanartPath: String?,
    val nfoPath: String?,
    /** Same pruning mechanism as MediaItemEntity.lastIndexedAtMs. */
    val lastIndexedAtMs: Long,
)
