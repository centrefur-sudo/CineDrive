package com.cinedrive.tv.data.storage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.cinedrive.tv.domain.model.StorageAccessLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Permission gate for the two storage regimes in the Android 10 → 14+ range
 * (see [StorageAccessLevel] for the full rationale).
 */
@Singleton
class StoragePermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun currentAccessLevel(): StorageAccessLevel = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            if (Environment.isExternalStorageManager()) StorageAccessLevel.GRANTED
            else StorageAccessLevel.NEEDS_ALL_FILES_ACCESS

        else ->
            if (hasLegacyRead()) StorageAccessLevel.GRANTED
            else StorageAccessLevel.NEEDS_LEGACY_READ_PERMISSION
    }

    /** Runtime permissions to request on Android 10 devices. */
    fun legacyPermissions(): Array<String> =
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    /**
     * Intent into the "All files access" settings page. Prefer the deep link
     * straight to our package; some OEM builds drop the extra, in which case
     * fall back to the list page where the user picks the app by name.
     */
    fun allFilesAccessIntent(): Intent {
        val deepLink = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        return if (deepLink.resolveActivity(context.packageManager) != null) {
            deepLink
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        }
    }

    private fun hasLegacyRead(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
