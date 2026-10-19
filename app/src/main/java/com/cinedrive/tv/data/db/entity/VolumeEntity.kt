package com.cinedrive.tv.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted USB volume registry, keyed by the volume UUID — the stable
 * identity of a physical drive across re-plugs and TV ports.
 *
 * Rows are created on first sight of a drive and kept when the drive is
 * unplugged, so metadata and progress survive detach/reattach cycles. The
 * last known mount path is only a hint (mount points change per port).
 */
@Entity(tableName = "volumes")
data class VolumeEntity(
    @PrimaryKey val volumeUuid: String,
    val displayName: String,
    /** Last mount path seen for this drive (port-dependent, may be stale). */
    val lastMountPath: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val isRemovable: Boolean,
)
