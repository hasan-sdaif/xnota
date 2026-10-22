package com.xnota.core.history

/**
 * A linear undo/redo stack (spec 07 §1). A new edit clears the redo branch.
 * Commands operate on the in-memory model by identity, so item/page references
 * must remain stable across an undo/redo cycle.
 *
 * Bounded at [limit] commands, oldest dropped first. A command holds the model it can put back:
 * [EraseItems] pins every erased stroke and [ReplacePageItems] pins a page's whole item list on
 * each area-eraser drag, so an unbounded stack retains a session's worth of deleted ink. That
 * competes for the heap with everything else and makes whatever allocates next the thing that
 * fails. The redo stack needs no cap of its own: it only ever holds what undo popped.
 */
class History(private val limit: Int = DEFAULT_LIMIT) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** The command the next [undo]/[redo] would apply, so a caller can read what it is about to
     *  disturb (see [Command.touched]) before and after the swap. */
    val nextUndo: Command? get() = undoStack.lastOrNull()
    val nextRedo: Command? get() = redoStack.lastOrNull()

    /** Record an already-applied edit and invalidate any redo branch. */
    fun push(command: Command) {
        undoStack.addLast(command)
        while (undoStack.size > limit) undoStack.removeFirst() // the oldest edit becomes unreachable
        redoStack.clear()
    }

    /**
     * Fold [command] into the last pushed edit as one undo unit (the fork's undo coalescing for
     * rapid back-to-back strokes): the pair becomes a composite, so one undo takes back the whole
     * burst. Fails (and the caller falls back to a plain [push]) when the stack is empty. The redo
     * branch clears exactly as a push would.
     */
    fun tryCoalesce(command: Command): Boolean {
        val last = undoStack.lastOrNull() ?: return false
        undoStack.removeLast()
        undoStack.addLast(CompositeCommand(listOf(last, command)))
        redoStack.clear()
        return true
    }

    fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        command.undo()
        redoStack.addLast(command)
    }

    fun redo() {
        val command = redoStack.removeLastOrNull() ?: return
        command.redo()
        undoStack.addLast(command)
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    companion object {
        /** Deep enough that no one undoes past it in practice, shallow enough to bound the heap. */
        const val DEFAULT_LIMIT = 200
    }
}
