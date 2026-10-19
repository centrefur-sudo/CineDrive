package com.cinedrive.tv.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore by preferencesDataStore(name = "playback_prefs")

/**
 * Playback-wide user preferences (v1.2.1). Kept deliberately tiny — one
 * DataStore file, one key per setting, defaults that match the happy path.
 *
 * The hardware-decoding switch exists because MediaCodec on some armv7 TV
 * boxes wedges mid-playback (frozen frame, dead clock); software decoding
 * is the escape hatch, and the choice must survive app restarts so a
 * re-open of the same file keeps the working decoder.
 */
@Singleton
class PlaybackPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val hwKey = booleanPreferencesKey("hardware_decoding")

    /** Defaults to ON — hardware decoding is right for the vast majority. */
    val hardwareDecoding: Flow<Boolean> = context.playbackDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs -> prefs[hwKey] ?: true }

    suspend fun setHardwareDecoding(enabled: Boolean) {
        context.playbackDataStore.edit { prefs -> prefs[hwKey] = enabled }
    }
}
