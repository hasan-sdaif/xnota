package com.xnota.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerTest {

    @Test fun aFreshTimerIsStoppedInWorkAtFullLength() {
        val t = FocusTimer()
        assertFalse(t.running)
        assertEquals(FocusTimer.Phase.WORK, t.phase)
        assertEquals(25 * 60, t.secondsRemaining)
        assertEquals(0, t.completedWork)
    }

    @Test fun ticksOnlyCountWhileRunning() {
        val t = FocusTimer()
        assertNull(t.tick(30))
        assertEquals(25 * 60, t.secondsRemaining)
        t.start()
        assertNull(t.tick(30))
        assertEquals(25 * 60 - 30, t.secondsRemaining)
        t.pause()
        assertNull(t.tick(30))
        assertEquals(25 * 60 - 30, t.secondsRemaining)
        t.start()
        assertNull(t.tick(10))
        assertEquals(25 * 60 - 40, t.secondsRemaining)
    }

    @Test fun finishingWorkCountsItAndStartsTheBreak() {
        val t = FocusTimer(workSeconds = 10, breakSeconds = 5)
        t.start()
        assertEquals(FocusTimer.Phase.WORK, t.tick(10))
        assertEquals(1, t.completedWork)
        assertEquals(FocusTimer.Phase.BREAK, t.phase)
        assertEquals(5, t.secondsRemaining)
    }

    @Test fun finishingTheBreakStartsTheNextWorkWithoutCounting() {
        val t = FocusTimer(workSeconds = 10, breakSeconds = 5)
        t.start()
        t.tick(10)
        assertEquals(FocusTimer.Phase.BREAK, t.tick(5))
        assertEquals(FocusTimer.Phase.WORK, t.phase)
        assertEquals(10, t.secondsRemaining)
        assertEquals(1, t.completedWork)
    }

    @Test fun aBigTickCarriesItsOvershootIntoTheSuccessor() {
        val t = FocusTimer(workSeconds = 10, breakSeconds = 5)
        t.start()
        // 12 seconds: work ends with 2 of break burned off the top.
        assertEquals(FocusTimer.Phase.WORK, t.tick(12))
        assertEquals(FocusTimer.Phase.BREAK, t.phase)
        assertEquals(3, t.secondsRemaining)
    }

    @Test fun resetClearsEverything() {
        val t = FocusTimer(workSeconds = 10, breakSeconds = 5)
        t.start()
        t.tick(10)
        t.reset()
        assertFalse(t.running)
        assertEquals(FocusTimer.Phase.WORK, t.phase)
        assertEquals(10, t.secondsRemaining)
        assertEquals(0, t.completedWork)
    }

    @Test fun customLengthsAreHonoured() {
        val t = FocusTimer(workSeconds = 50 * 60, breakSeconds = 10 * 60)
        assertEquals(50 * 60, t.phaseLength)
        t.start()
        assertEquals(FocusTimer.Phase.WORK, t.tick(50 * 60))
        assertEquals(10 * 60, t.phaseLength)
        assertEquals(FocusTimer.Phase.BREAK, t.phase)
    }

    @Test fun manyCyclesCountEveryWorkPhase() {
        val t = FocusTimer(workSeconds = 2, breakSeconds = 1)
        t.start()
        var works = 0
        for (i in 1..9) {
            val ended = t.tick(1)
            if (ended == FocusTimer.Phase.WORK) works++
        }
        // Nine seconds of a 3-second cycle: work ends on ticks 2, 5 and 8.
        assertEquals(3, works)
        assertEquals(3, t.completedWork)
        assertTrue(t.secondsRemaining >= 0)
    }
}
