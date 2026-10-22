package com.xnota.core.stroke

/**
 * Undo coalescing (the fork's `undo_coalescing` preference, on by default): a stroke begun within
 * a short window of the previous pen-up merges into the previous stroke's **undo unit**, because
 * the two were written as one burst of thought — a dot of an i, a crossbar, a quick correction —
 * and one undo should take back the burst, not one letter of it.
 *
 * Pure class with the clock injected, so the timing semantics are unit-testable; the two editors
 * hold one instance each and consult it at their stroke-commit sites. Merging is the history
 * stack's business ([com.xnota.core.history.History.push] folding the new command into the top);
 * this class only decides *when*.
 */
class StrokeUndoCoalescer(
    /** The window: strokes begun within this many ms of the previous pen-up coalesce. */
    val windowMs: Long = DEFAULT_WINDOW_MS,
    /** The clock the editor supplies; injectable for tests. */
    val clock: () -> Long = { System.currentTimeMillis() },
) {
    private var lastPenUpMs = Long.MIN_VALUE

    /**
     * Whether a stroke that began at [beganAtMs] joins the previous stroke's undo unit: the
     * previous pen-up is known and within the window. A stroke that began *before* the previous
     * pen-up (an interleaved gesture, a crossed hand) never coalesces.
     */
    fun shouldCoalesce(beganAtMs: Long): Boolean =
        lastPenUpMs != Long.MIN_VALUE && beganAtMs in lastPenUpMs..(lastPenUpMs + windowMs)

    /**
     * Record a pen-up, opening (or reopening) the window. Called once per committed stroke; the
     * commit's own clock read is the default.
     */
    fun notePenUp(atMs: Long = clock()) {
        lastPenUpMs = atMs
    }

    /** Forget the window — an undo, a page change, anything that makes "previous stroke" meaningless. */
    fun reset() {
        lastPenUpMs = Long.MIN_VALUE
    }

    companion object {
        /**
         * The window, ~a third of a second: fast enough that a deliberate pause starts a fresh
         * undo unit, slow enough to cover the gaps of hurried writing and dotting.
         */
        const val DEFAULT_WINDOW_MS = 350L
    }
}
