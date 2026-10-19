package com.cinedrive.tv.domain.model

/**
 * A storage volume as seen by the app.
 *
 * USB drives are identified by their volume UUID (e.g. "0403-0201"). That
 * UUID is the stable key used to persist playback progress and metadata per
 * physical device (Room, Step 3) — re-plugging the same drive into any TV
 * port restores its watch state, while a reformatted drive (new UUID) is
 * correctly treated as a fresh library.
 */
data class UsbVolume(
    /** FAT/exFAT volume UUID, e.g. "0403-0201". Unique per formatted drive. */
    val uuid: String,
    /** Human-friendly label from the OS (volume label or generic name). */
    val displayName: String,
    /** Absolute mount path readable by the app, e.g. /storage/0403-0201. */
    val mountPath: String,
    /** True for removable media (USB sticks, card readers). */
    val isRemovable: Boolean = true,
    /** True when currently mounted and readable. */
    val isMounted: Boolean = true,
)
