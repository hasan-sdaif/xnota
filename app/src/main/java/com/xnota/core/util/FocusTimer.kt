package com.xnota.core.util

/**
 * The focus timer (the fork): a pomodoro-shaped state machine the editor's HUD drives. Pure —
 * the HUD owns the clock and hands seconds to [tick], so the transitions the tests pin are the
 * ones the device makes. A work phase runs [workSeconds]; its end starts a break of
 * [breakSeconds], whose end starts the next work phase; each finished work phase steps
 * [completedWork]. Pausing freezes the countdown; reset returns to a fresh stopped WORK phase.
 */
class FocusTimer(
    val workSeconds: Int = DEFAULT_WORK_SECONDS,
    val breakSeconds: Int = DEFAULT_BREAK_SECONDS,
) {
    /** Which stretch of the cycle is counting down. */
    enum class Phase { WORK, BREAK }

    /** Whether the countdown is running (true) or paused/stopped (false). */
    var running: Boolean = false
        private set

    /** The phase the countdown is in; a reset timer starts in WORK, stopped. */
    var phase: Phase = Phase.WORK
        private set

    /** Seconds left in [phase], always in `0..length`. */
    var secondsRemaining: Int = workSeconds
        private set

    /** Work phases finished since the last reset. */
    var completedWork: Int = 0
        private set

    /** Start (from stopped) or resume (from pause). Idempotent while already running. */
    fun start() {
        running = true
    }

    /** Pause the countdown; [start] resumes it where it stood. */
    fun pause() {
        running = false
    }

    /** Stop everything: back to a fresh WORK phase with no completed count. */
    fun reset() {
        running = false
        phase = Phase.WORK
        secondsRemaining = workSeconds
        completedWork = 0
    }

    /**
     * Advance the countdown by [dt] seconds (the HUD's clock hands in what passed). Only a
     * running timer moves; a phase that runs out flips to its successor, a finished work phase
     * counting to [completedWork]. Returns the phase that *ended* with this tick, or null when
     * nothing ended — the HUD rings its tone on the non-null one.
     */
    fun tick(dt: Int): Phase? {
        if (!running || dt <= 0) return null
        var ended: Phase? = null
        var left = secondsRemaining - dt
        while (left <= 0) {
            ended = phase
            if (phase == Phase.WORK) completedWork++
            val nextLength = if (phase == Phase.WORK) breakSeconds else workSeconds
            phase = if (phase == Phase.WORK) Phase.BREAK else Phase.WORK
            // Carry the overshoot into the successor phase, so a big dt still flips only once per
            // phase boundary it truly crosses.
            left += nextLength
            if (nextLength <= 0) break
        }
        secondsRemaining = left.coerceIn(0, if (phase == Phase.WORK) workSeconds else breakSeconds)
        return ended
    }

    /** The phase's full length in seconds. */
    val phaseLength: Int
        get() = if (phase == Phase.WORK) workSeconds else breakSeconds

    companion object {
        const val DEFAULT_WORK_SECONDS = 25 * 60
        const val DEFAULT_BREAK_SECONDS = 5 * 60
    }
}
