package com.cinedrive.tv.domain.model

/**
 * Two-tier permission model covering the Android 10 → 14+ range:
 *
 *  • API 29 (Android 10): MANAGE_EXTERNAL_STORAGE does not exist. Full access
 *    to removable drives comes from READ_EXTERNAL_STORAGE plus the manifest
 *    flag requestLegacyExternalStorage="true".
 *  • API 30+ (Android 11…14+): raw java.io.File access to removable drives
 *    requires "All files access" (Environment.isExternalStorageManager()) —
 *    the same gate VLC and Kodi use.
 *
 * SAF directory pickers are deliberately not used: they are unusable with a
 * D-pad remote on a 10-foot UI.
 */
enum class StorageAccessLevel {
    /** Everything granted; raw file walks of USB volumes will work. */
    GRANTED,

    /** Android 11+: "All files access" has not been granted yet. */
    NEEDS_ALL_FILES_ACCESS,

    /** Android 10: READ_EXTERNAL_STORAGE has not been granted yet. */
    NEEDS_LEGACY_READ_PERMISSION,
}
