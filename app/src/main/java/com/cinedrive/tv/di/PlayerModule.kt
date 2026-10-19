package com.cinedrive.tv.di

import android.content.Context
import com.cinedrive.tv.playback.LibVlcPlayerEngine
import com.cinedrive.tv.playback.PlayerEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.videolan.libvlc.LibVLC

/**
 * Playback wiring: one process-wide LibVLC instance (heavy natives, loaded
 * lazily on the first session) plus the [PlayerEngine.Factory] binding that
 * keeps the UI backend-agnostic.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideLibVlc(@ApplicationContext context: Context): LibVLC =
        LibVLC(
            context,
            arrayListOf(
                // Pitch-preserving rate changes (audio-time-stretch).
                "--audio-time-stretch",
                // Lighter H.264 loop filter → measurably fewer dropped frames
                // on low-end TV SoCs; invisible on healthy hardware.
                "--avcodec-skiploopfilter=1",
                // Prefer completeness over latency for local files.
                "--no-drop-late-frames",
                "--no-skip-frames",
            ),
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerEngineModule {

    @Binds
    abstract fun bindPlayerEngineFactory(impl: LibVlcPlayerEngine.Factory): PlayerEngine.Factory
}
