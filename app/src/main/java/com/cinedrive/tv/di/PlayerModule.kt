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
                // NOTE: "--no-drop-late-frames" / "--no-skip-frames" were
                // deliberately REMOVED (v1.2.1). With frame dropping fully
                // disabled a slow MediaCodec decoder builds unbounded
                // latency and the pipeline hard-freezes (stuck frame, dead
                // clock, pause unresponsive) — the exact failure seen on
                // armv7 boxes. VLC's adaptive late-frame dropping is the
                // correct default; scrubbing stays fine via the explicit
                // seek guards in the engine.
            ),
        )
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerEngineModule {

    @Binds
    abstract fun bindPlayerEngineFactory(impl: LibVlcPlayerEngine.Factory): PlayerEngine.Factory
}
