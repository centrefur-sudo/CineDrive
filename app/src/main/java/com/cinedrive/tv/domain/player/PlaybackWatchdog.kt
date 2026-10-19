package com.cinedrive.tv.domain.player

/**
 * Pure stall-decision logic for the player (v1.2.1 debugging pass).
 *
 * Real-world failure mode on low-end TV SoCs (armv7 + MediaCodec): LibVLC
 * reports Playing, the pipeline silently dies — the HUD clock freezes, the
 * frame stays stuck and OK-pause does nothing visible. Historically there
 * was no recovery; the user had to BACK out and retry.
 *
 * The watchdog samples two clocks while the engine is alive:
 *  - progress clock: last TimeChanged-with-advancing-position / Playing /
 *    LengthChanged event — "is the media actually moving",
 *  - event clock: last LibVLC event of ANY kind — "is the pipeline alive".
 *
 * Two distinct stalls are recognised:
 *  - PLAYING stall: hasStarted && playing, but no progress for
 *    [PROGRESS_STALL_MS] → one pause/play kick usually restarts MediaCodec;
 *  - OPEN stall: playback never started AND no events at all for
 *    [OPEN_STALL_MS] → the input thread is wedged mid-open; a re-open
 *    (stop + setMedia + play) is the only way out.
 *
 * Recovery is budgeted ([MAX_RECOVERIES]): after that, a further stall is
 * escalated to Fatal so the UI can surface the hardware-decoding advice.
 * All inputs are plain values so CI can exercise every branch on the JVM.
 */
object PlaybackWatchdog {

    /** Playing but clock frozen this long → kick the player. */
    const val PROGRESS_STALL_MS = 10_000L

    /** Still opening and the pipeline went completely quiet this long → re-open. */
    const val OPEN_STALL_MS = 30_000L

    /** One mid-play kick + one re-open per session before giving up. */
    const val MAX_RECOVERIES = 2

    enum class Action { None, Recover, Fatal }

    /**
     * @param hasStarted       LibVLC has produced at least one Playing event.
     * @param playing          engine currently in the playing state.
     * @param pausedByUser     user explicitly paused — never "recover" that.
     * @param timeSinceProgressMs  ms since the last progress evidence.
     * @param timeSinceEventMs     ms since the last LibVLC event of any kind.
     * @param recoveries       recovery kicks already spent this session.
     */
    fun onTick(
        hasStarted: Boolean,
        playing: Boolean,
        pausedByUser: Boolean,
        timeSinceProgressMs: Long,
        timeSinceEventMs: Long,
        recoveries: Int,
    ): Action {
        if (pausedByUser) return Action.None

        // Mid-play stall: state says playing but the media clock is frozen.
        if (hasStarted && playing && timeSinceProgressMs >= PROGRESS_STALL_MS) {
            return if (recoveries >= MAX_RECOVERIES) Action.Fatal else Action.Recover
        }

        // Open stall: never produced a frame and the pipeline went silent.
        if (!hasStarted && timeSinceEventMs >= OPEN_STALL_MS) {
            return if (recoveries >= MAX_RECOVERIES) Action.Fatal else Action.Recover
        }

        return Action.None
    }
}
