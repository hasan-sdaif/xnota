package com.xnota.canvas

import android.os.Handler
import android.os.Looper
import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import com.xnota.core.history.AddPageAuto
import com.xnota.core.history.Command
import com.xnota.core.history.CompositeCommand
import com.xnota.core.history.FlowEditParagraph
import com.xnota.core.history.History
import com.xnota.core.history.ParaSnapshot
import com.xnota.core.model.Page
import com.xnota.core.text.CellIndex
import com.xnota.core.text.FindReplace
import com.xnota.core.text.caretPreviewSpan
import com.xnota.core.text.CharStyle
import com.xnota.core.text.FlowEditor
import com.xnota.core.text.FlowFrame
import com.xnota.core.text.FlowHit
import com.xnota.core.text.FlowPos
import com.xnota.core.text.FlowRange
import com.xnota.core.text.InputRules
import com.xnota.core.text.ListKind
import com.xnota.core.text.ListMarkers
import com.xnota.core.text.Paragraph
import com.xnota.core.text.SelShape
import com.xnota.core.text.SlashCommands
import com.xnota.core.text.SmartQuotes
import com.xnota.core.text.TextFlow
import com.xnota.core.text.wordRangeAt
import kotlin.math.abs

/**
 * The inline-flow caret: session lifecycle (lift the flow for live drawing, show
 * the IME), tap-to-caret with empty-line fill below the text, drag selection,
 * double-tap word selection, checkbox toggling, typing bursts coalesced into one
 * undo step, and automatic page append when typing overflows the last page.
 * Owned by the Editor; the InteractionController routes TEXT-tool gestures here.
 */
class FlowTextController(
    private val state: CanvasState,
    private val history: History,
    private val flow: () -> TextFlow,
    private val frame: () -> FlowFrame?,
    private val slotHeight: () -> Double,
    /** A mutation landed; republish the layout ([live] = a caret session is open). */
    private val onChanged: (live: Boolean) -> Unit,
    /** A coalesced burst (one undo step) was pushed: refresh chrome/autosave/stream. */
    private val onFlushed: () -> Unit,
    private val onSessionChanged: (Boolean) -> Unit,
    private val onViewChanged: () -> Unit,
    private val requestRender: () -> Unit,
) {
    var active = false
        private set

    var selection: FlowRange = FlowRange.caret(FlowPos.START)
        private set(value) {
            field = value
            onCaretChanged()
        }

    /** Fired whenever the caret/selection moves (the host's format bar keys on it). */
    var onCaretChanged: () -> Unit = {}

    /** Style for the next typed run (set by the format bar on a collapsed caret). */
    var pendingStyle: CharStyle? = null

    /**
     * The style text typed at a position must take, when the host knows better
     * than the flow does. A formula's closing edge is both beside the equation
     * and at the end of its LaTeX, and only the host tracks which of the two the
     * caret arrived at, so only the host can say whether typing there goes in.
     */
    var styleAt: (FlowPos) -> CharStyle? = { null }

    /** Whether typed markdown markers convert (the text tool's Markdown shortcuts toggle). */
    var markdownInput = true

    /** Whether "/" opens the command menu (the text tool's Slash commands toggle). */
    var slashCommands = true

    /** Whether straight quotes land as typographic ones as they are typed (the fork). */
    var smartQuotes = true

    /** Whether Enter after a typed list marker carries the marker onto the new line (the fork). */
    var autoLists = true

    /** Whether the caret's line stays vertically centred while typing (the fork). */
    var typewriterScrolling = false

    /** A soft key click to play as text lands (the fork's typing sounds); null-silent by default. */
    var onKeyClick: () -> Unit = {}

    /** Words just typed (the fork's daily word goal counts them as they land). */
    var onWordsTyped: (Int) -> Unit = {}

    /** Claims Enter while the slash menu is open, so the host commits its top entry. */
    var onSlashEnter: () -> Boolean = { false }

    /**
     * An applied edit waiting to be folded into the next commit, so a two-part change
     * is one undo step. The slash menu uses it: deleting the query and running the
     * command it named are separate edits, but undoing them separately would leave
     * the user looking at half a command.
     */
    var pendingPrefix: Command? = null

    /** Installed by the input layer to mirror caret/selection moves to the IME. */
    var imeSync: () -> Unit = {}

    /** Installed by the input layer: a committed edit landed (reconcile the IME mirror). */
    var onEdited: () -> Unit = {}

    /** Installed by the input layer: a typed edit landed, which a key event may have made behind the mirror. */
    var onTyped: () -> Unit = {}

    /** Installed by the input layer: re-show the soft keyboard (a caret tap wants it back). */
    var requestIme: () -> Unit = {}

    /** Long-press armed a selection: a short haptic tick. */
    var onHaptic: () -> Unit = {}

    /** A long-press gesture finished: open the editing context menu at this viewport point. */
    var onContextMenu: (Pt) -> Unit = {}

    /** A long press held a table itself (fired while the finger is still down): open its menu. */
    var onTableHold: (com.xnota.core.text.FlowTable) -> Unit = {}

    // a long press that held the table itself (a rule, padding, empty cell space): no text selection
    private var tableHold = false

    /** While true (a table in structure-edit mode) presses skip the text: a tap calls [onGatedTap], a drag pans. */
    var gated: () -> Boolean = { false }

    var onGatedTap: () -> Unit = {}

    private var pressGated = false

    /** Set when an edit landed differently from the plain-text replace the IME mirror assumed. */
    var mirrorStale = false

    /** Metrics of the font [pendingStyle] resolves to, so the caret previews it before typing. */
    var caretMetricsFor: ((CharStyle) -> com.xnota.core.pal.LineMetrics)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val idleFlush = Runnable { flushBurst() }

    // one coalesced typing burst: a single paragraph edited since the last flush
    private var burstPara: Paragraph? = null
    private var burstBefore: ParaSnapshot? = null
    private val burstExtras = mutableListOf<Command>()

    // press/drag gesture state: a short drag pans the document (handed back to the
    // interaction controller); selection arms only after a LONG PRESS, then extends by drag
    private var pressAnchor: FlowPos? = null
    private var pressHit: FlowHit? = null
    private var pressViewport = Pt(0.0, 0.0)
    private var pressContent = Pt(0.0, 0.0)
    private var selectionArmed = false
    private var armedAnchor: FlowRange? = null
    private var lastTapAtMs = 0L
    private var lastTapViewport = Pt(0.0, 0.0)
    private val longPressRun = Runnable { onLongPressFired() }

    // selection handles + edge autoscroll while extending a selection
    private val handles = TextHandles(state)
    private var draggingHandle: TextHandles.Handle? = null
    private var handleFixed: FlowPos? = null
    private var handleGrabOffset = Pt(0.0, 0.0)

    // --- session ---

    fun startSession(range: FlowRange) {
        if (active) {
            selection = range
            imeSync()
            requestRender()
            return
        }
        active = true
        selection = range
        state.flowLifted = true
        invalidateFlowPages()
        onSessionChanged(true)
        requestRender()
    }

    fun endSession() {
        handler.removeCallbacks(longPressRun)
        handles.stopAutoscroll()
        draggingHandle = null
        handleFixed = null
        if (!active) return
        flushBurst()
        active = false
        state.flowLifted = false
        invalidateFlowPages()
        onSessionChanged(false)
        requestRender()
    }

    private fun invalidateFlowPages() {
        val f = frame() ?: return
        val pages = state.document.pages
        f.pagesWithLines().forEach { i -> pages.getOrNull(i)?.let(state::invalidatePage) }
    }

    // --- gestures (routed by the InteractionController while the TEXT tool is armed) ---

    fun pressAt(content: Pt, viewport: Pt) {
        pressViewport = viewport
        pressContent = content
        pressGated = gated()
        tableHold = false
        if (pressGated) return
        selectionArmed = false
        armedAnchor = null
        // Grabbing a selection handle resizes the selection from its other end.
        if (active && !selection.collapsed) {
            grabHandle(viewport)?.let { h ->
                val r = selection.normalized()
                draggingHandle = h
                handleFixed = if (h == TextHandles.Handle.START) r.end else r.start
                val moving = if (h == TextHandles.Handle.START) r.start else r.end
                handleGrabOffset = caretAnchorViewport(moving)
                    ?.let { Pt(it.x - viewport.x, it.y - viewport.y) } ?: Pt(0.0, 0.0)
                return
            }
        }
        val (pi, local) = state.pagePointAt(content) ?: return
        val hit = frame()?.hitTest(pi, local) ?: return
        pressHit = hit
        pressAnchor = when (hit) {
            is FlowHit.Caret -> hit.pos
            is FlowHit.Checkbox -> FlowPos(hit.paraIndex, 0)
            FlowHit.BeyondEnd -> flow().endPos()
        }
        handler.removeCallbacks(longPressRun)
        handler.postDelayed(longPressRun, LONG_PRESS_MS)
    }

    /**
     * Long press while still: arm selection on the word under the finger (the
     * release opens the menu). A press on a table itself rather than its text
     * selects nothing and opens the table's menu right away.
     */
    private fun onLongPressFired() {
        val anchor = pressAnchor ?: return
        val held = state.pagePointAt(pressContent)?.let { (pi, local) ->
            frame()?.tablePressAt(pi, local, TABLE_RULE_SLOP_DP * state.devicePxPerDp / state.zoom)
        }
        if (held != null && !held.second) {
            tableHold = true
            onHaptic()
            onTableHold(held.first)
            requestRender()
            return
        }
        if (!active) startSession(FlowRange.caret(anchor))
        if (pressHit == FlowHit.BeyondEnd) {
            // Below the text: place the caret on the pressed line (empty-line fill) instead.
            tapBeyondEnd(pressContent)
            selectionArmed = true
            armedAnchor = FlowRange.caret(selection.end)
        } else {
            val word = wordRangeAt(flow(), anchor).normalized()
            selectionArmed = true
            armedAnchor = word
            selection = if (word.collapsed) FlowRange.caret(anchor) else word
        }
        onHaptic()
        imeSync()
        requestRender()
    }

    /**
     * Drag while pressed. A grabbed handle or an armed (long-pressed) drag extends
     * the selection, autoscrolling near the viewport edges; a plain drag past the
     * slop is a document pan: returns true so the interaction controller hands the
     * rest of the gesture to its pan mode.
     */
    fun dragTo(content: Pt, viewport: Pt): Boolean {
        if (pressGated) return viewport.distanceTo(pressViewport) > DRAG_SLOP
        if (tableHold) return false
        if (draggingHandle != null || selectionArmed) {
            updateDragSelection(viewport)
            handles.autoscroll(viewport) { vp ->
                onViewChanged()
                updateDragSelection(vp)
                requestRender()
            }
            requestRender()
            return false
        }
        if (viewport.distanceTo(pressViewport) <= DRAG_SLOP) return false
        handler.removeCallbacks(longPressRun)
        pressAnchor = null
        pressHit = null
        return true
    }

    /** Re-derive the moving selection end from the finger's viewport point. */
    private fun updateDragSelection(viewport: Pt) {
        if (draggingHandle != null) {
            // The teardrop hangs below its line: keep the grip offset from the grab, so
            // the caret follows the line the handle marks, not the line under the finger.
            val at = Pt(viewport.x + handleGrabOffset.x, viewport.y + handleGrabOffset.y)
            val pos = caretPosAt(state.viewportToContent(at)) ?: return
            selection = FlowRange(handleFixed ?: return, pos)
            return
        }
        val pos = caretPosAt(state.viewportToContent(viewport)) ?: return
        val a = armedAnchor ?: FlowRange.caret(pressAnchor ?: return)
        selection = if (pos < a.start) FlowRange(a.end, pos) else FlowRange(a.start, pos)
    }

    fun release(content: Pt, viewport: Pt, timeMs: Long) {
        handler.removeCallbacks(longPressRun)
        handles.stopAutoscroll()
        if (pressGated) {
            pressGated = false
            onGatedTap()
            requestRender()
            return
        }
        if (tableHold) {
            tableHold = false
            pressHit = null
            pressAnchor = null
            return
        }
        val hit = pressHit
        pressHit = null
        pressAnchor = null
        if (draggingHandle != null) {
            draggingHandle = null
            handleFixed = null
            imeSync()
            requestRender()
            onContextMenu(viewport)
            return
        }
        if (selectionArmed) {
            selectionArmed = false
            armedAnchor = null
            imeSync()
            requestRender()
            onContextMenu(viewport)
            return
        }
        when (hit) {
            null -> Unit
            is FlowHit.Checkbox -> {
                flushBurst()
                commitEdit(FlowEditor(flow()).toggleChecked(hit.paraIndex), null)
            }
            is FlowHit.Caret -> {
                val isDouble = timeMs - lastTapAtMs < DOUBLE_TAP_MS &&
                    viewport.distanceTo(lastTapViewport) < DOUBLE_TAP_SLOP
                lastTapAtMs = timeMs
                lastTapViewport = viewport
                if (active) placeCaret(hit.pos) else startSession(FlowRange.caret(hit.pos))
                requestIme()
                if (isDouble) {
                    selection = wordRangeAt(flow(), hit.pos)
                    imeSync()
                    requestRender()
                }
            }
            FlowHit.BeyondEnd -> {
                lastTapAtMs = timeMs
                lastTapViewport = viewport
                tapBeyondEnd(content)
                requestIme()
            }
        }
    }

    /** Place the caret from a programmatic point (menu paste): tap semantics, no gestures. */
    fun tapAt(content: Pt) {
        val (pi, local) = state.pagePointAt(content) ?: return
        when (val hit = frame()?.hitTest(pi, local)) {
            null -> Unit
            is FlowHit.Caret ->
                if (active) placeCaret(hit.pos) else startSession(FlowRange.caret(hit.pos))
            is FlowHit.Checkbox ->
                if (active) placeCaret(FlowPos(hit.paraIndex, 0)) else startSession(FlowRange.caret(FlowPos(hit.paraIndex, 0)))
            FlowHit.BeyondEnd -> tapBeyondEnd(content)
        }
    }

    /** A tap below the flow end: fill the gap with empty lines so the caret lands there. */
    private fun tapBeyondEnd(content: Pt) {
        val (pi, local) = state.pagePointAt(content) ?: return
        val f = frame() ?: return
        var count = f.emptyLinesToReach(pi, local.y, slotHeight())
        if (flow().paragraphs.isEmpty() && count <= 0) count = 1
        if (!active) startSession(FlowRange.caret(flow().endPos()))
        if (count <= 0) {
            placeCaret(flow().endPos())
            return
        }
        flushBurst()
        val (cmd, caret) = FlowEditor(flow()).appendEmptyLines(count)
        commitEdit(cmd, caret)
    }

    fun placeCaret(pos: FlowPos) {
        flushBurst()
        pendingStyle = null
        selection = FlowRange.caret(pos)
        imeSync()
        ensureCaretVisible()
        requestRender()
    }

    fun setSelection(range: FlowRange, syncIme: Boolean = true) {
        selection = range
        if (syncIme) imeSync()
        requestRender()
    }

    // ── Find & replace + word count (the fork's text power) ─────────────────────

    /** The flow being edited, for the editor's find bar and counters. */
    fun flow(): TextFlow = flow.invoke()

    /** Every match of [needle] in the flow's text, as global (plainText) ranges, in order. */
    fun findAll(needle: String, caseSensitive: Boolean, wholeWord: Boolean, ignoreDiacritics: Boolean): List<IntRange> =
        FindReplace.findAll(flow().plainText(), needle, caseSensitive, wholeWord, ignoreDiacritics)
            .map { m -> m.start until m.end }

    /** The first match at or after [fromGlobal] (wrapping), as a global range, or null. */
    fun findNext(needle: String, fromGlobal: Int, caseSensitive: Boolean, wholeWord: Boolean, ignoreDiacritics: Boolean): IntRange? {
        val all = findAll(needle, caseSensitive, wholeWord, ignoreDiacritics)
        if (all.isEmpty()) return null
        return all.firstOrNull { it.first >= fromGlobal } ?: all.first()
    }

    /** The last match at or before [fromGlobal] (wrapping), as a global range, or null. */
    fun findPrev(needle: String, fromGlobal: Int, caseSensitive: Boolean, wholeWord: Boolean, ignoreDiacritics: Boolean): IntRange? {
        val all = findAll(needle, caseSensitive, wholeWord, ignoreDiacritics)
        if (all.isEmpty()) return null
        return all.lastOrNull { it.last < fromGlobal } ?: all.last()
    }

    /**
     * Replace every match with [replacement], as one undo step: matches are applied
     * right-to-left so each one's global range stays valid as the text shifts. The caret
     * lands where the first match's replacement ends (or stays put when nothing matched).
     */
    fun replaceAllMatches(needle: String, replacement: String, caseSensitive: Boolean, wholeWord: Boolean, ignoreDiacritics: Boolean): Int {
        val matches = findAll(needle, caseSensitive, wholeWord, ignoreDiacritics)
        if (matches.isEmpty()) return 0
        var caret: FlowPos? = null
        for (range in matches.asReversed()) {
            val from = flow().posAtGlobal(range.first)
            val to = flow().posAtGlobal(range.last + 1)
            val (cmd, after) = FlowEditor(flow()).replaceRange(FlowRange(from, to), replacement)
            commitEdit(cmd, after)
            if (caret == null) caret = after
        }
        return matches.size
    }

    /** Select the flow range of a global [range] (a find bar's hit), moving the caret to it. */
    fun selectGlobal(range: IntRange) {
        val from = flow().posAtGlobal(range.first)
        val to = flow().posAtGlobal(range.last + 1)
        setSelection(FlowRange(from, to))
    }

    /** The flow's word/character/paragraph counts (the fork's word counter). */
    fun wordCounts(): com.xnota.core.text.WordCount.Counts =
        com.xnota.core.text.WordCount.count(flow().plainText())

    fun selectAll() {
        if (flow().paragraphs.isEmpty()) return
        selection = FlowRange(FlowPos.START, flow().endPos())
        imeSync()
        requestRender()
    }

    // --- edits ---

    /**
     * Replace [range] with [text] (the IME/typing path). Single-paragraph edits
     * coalesce into the open burst; anything structural flushes first and lands
     * as its own undo step. Returns the caret position after the edit.
     */
    fun applyReplace(range: FlowRange, text: String, style: CharStyle? = null): FlowPos {
        val r = range.normalized()
        if (r.start.para != r.end.para) {
            val cells = CellIndex(flow().paragraphs)
            if (cells.shapeOf(r) != SelShape.Text) {
                // The model will not do what the mirror just did: resync it afterwards.
                mirrorStale = true
                // Only a selection the user made may cross cells; a caret's backspace or
                // delete eating a cell or table boundary is refused.
                if (r != selection.normalized()) return selection.end
            }
        }
        val para = flow().paragraphs.getOrNull(r.start.para)
        if (text == "\n" && r.collapsed) {
            if (slashCommands && onSlashEnter()) return selection.end
            InputRules.forEnter(flow(), r.start, markdownInput)?.let { return applyRule(it, r.start) }
            // The fork's auto-continue: Enter after a typed (not model) list marker carries the
            // marker over; Enter on the bare marker drops it and stays put.
            if (autoLists) autoContinueList(r.start)?.let { return it }
        }
        // The fork's smart quotes: the straight quotes a keyboard sends become typographic ones
        // as they land (curly for Latin, guillemets for Arabic). One-for-one characters, so the
        // IME mirror's offsets stay valid; its text reconciles on the next pass.
        val typedText = if (smartQuotes && text.isNotEmpty() && '\n' !in text && r.start.para == r.end.para) {
            SmartQuotes.transform(text, charBefore(r.start), arabicAround(r.start, text))
        } else {
            text
        }
        // The armed style is only spent on text that actually lands; a deletion keeps it.
        val effStyle = style
            ?: (if (text.isNotEmpty()) pendingStyle?.also { pendingStyle = null } else null)
            ?: (if (text.isNotEmpty()) styleAt(r.start) else null)
        if (r.start.para == r.end.para && '\n' !in typedText && para != null) {
            if (burstPara !== para) {
                flushBurst()
                burstPara = para
                burstBefore = ParaSnapshot.of(para)
            }
            val (_, caret) = FlowEditor(flow()).replaceRange(r, typedText, effStyle)
            onChanged(active)
            burstExtras += autoAppendPages()
            selection = FlowRange.caret(caret)
            onTyped()
            if (typedText.isNotEmpty()) {
                onKeyClick()
                countWordsTyped(typedText)
            }
            if (markdownInput) InputRules.forTyped(flow(), caret, typedText)?.let { return applyRule(it, caret) }
            if (slashCommands && typedText == "/") {
                SlashCommands.escapeAt(flow(), caret)?.let { return dropSlash(caret.para, it) }
            }
            handler.removeCallbacks(idleFlush)
            handler.postDelayed(idleFlush, BURST_IDLE_MS)
            ensureCaretVisible()
            requestRender()
            return caret
        }
        flushBurst()
        // A typed paragraph break lands the caret on a fresh line with no left
        // neighbour to inherit from, so carry the typing style over as pending
        // (commitEdit -> placeCaret clears it, hence re-armed after).
        val leavingHeading = para != null && para.headingLevel > 0 && r.start.offset >= para.length
        val carry = if (typedText.endsWith("\n") && !leavingHeading) {
            effStyle ?: FlowEditor(flow()).charStyleAt(r.start)
        } else {
            null
        }
        val (cmd, caret) = FlowEditor(flow()).replaceRange(r, typedText, effStyle)
        commitEdit(cmd, caret)
        if (carry != null && carry != CharStyle.DEFAULT) pendingStyle = carry
        return caret
    }

    /**
     * The fork's auto-continue: the marker the paragraph's plain text starts with, carried onto
     * the line Enter is about to make — or dropped, when the marker is the whole line. Only
     * typed markers apply (the model's own list continuation is the paragraph's ListKind split,
     * which markdown conversion and the list toolbar own), so a paragraph carrying a model list
     * or living in a table is left to them. Returns the caret after the edit, or null when the
     * plain Enter should go through as it is.
     */
    private fun autoContinueList(at: FlowPos): FlowPos? {
        val para = flow().paragraphs.getOrNull(at.para) ?: return null
        if (para.table != null || para.list != ListKind.NONE || para.codeLang != null) return null
        val line = para.plainText()
        val cont = ListMarkers.continuation(line) ?: return null
        flushBurst()
        mirrorStale = true // the model is about to diverge from what the IME sent
        if (cont.isEmpty()) {
            // A bare marker: Enter removes it rather than making another empty item.
            val marker = ListMarkers.detect(line) ?: return null
            val (cmd, caret) = FlowEditor(flow()).replaceRange(FlowRange(FlowPos(at.para, 0), FlowPos(at.para, marker.text.length)), "")
            commitEdit(cmd, caret)
            return caret
        }
        val (cmd, caret) = FlowEditor(flow()).replaceRange(FlowRange.caret(at), "\n$cont")
        commitEdit(cmd, caret)
        return caret
    }

    /** The character just before [pos], or null at a paragraph start. */
    private fun charBefore(pos: FlowPos): Char? {
        val para = flow().paragraphs.getOrNull(pos.para) ?: return null
        if (pos.offset <= 0) return null
        val text = para.plainText()
        return text.getOrNull(pos.offset - 1)
    }

    /** Whether the text around [pos] reads Arabic (for the quotes' guillemet variant). */
    private fun arabicAround(pos: FlowPos, typed: String): Boolean {
        if (typed.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC }) return true
        return charBefore(pos)?.let { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC } ?: false
    }

    /** Whitespace-separated words in a typed run, for the daily goal. */
    private fun countWordsTyped(typed: String) {
        val n = typed.split(WORD_BREAK).count { it.isNotEmpty() }
        if (n > 0) onWordsTyped(n)
    }

    /**
     * Collapse a just-typed "//" to one literal slash. Flushing first makes it its
     * own undo step, so one undo brings the second slash back, and the mirror is
     * stale because the IME still believes it sent two.
     */
    private fun dropSlash(para: Int, offset: Int): FlowPos {
        flushBurst()
        val caret = FlowPos(para, offset)
        val cmd = FlowEditor(flow()).deleteRange(FlowRange(caret, FlowPos(para, offset + 1))).first
        mirrorStale = true
        commitEdit(cmd, caret)
        return caret
    }

    /**
     * Commit an input rule detected at [at]. The typed markers flush as their own
     * undo step first, so one undo puts them back as plain text, and the mirror is
     * marked stale because the model is about to diverge from what the IME sent.
     */
    private fun applyRule(rule: InputRules.Rule, at: FlowPos): FlowPos {
        flushBurst()
        val result = InputRules.apply(flow(), at, rule)
        mirrorStale = true
        commitEdit(result.command, result.caret)
        pendingStyle = result.pending
        return result.caret
    }

    /**
     * Replace [range] from OUTSIDE the IME mirror (menu paste, programmatic edits):
     * always its own undo step through [commitEdit], so the mirror reconciles.
     */
    fun replaceExternal(range: FlowRange, text: String, style: CharStyle? = null): FlowPos {
        flushBurst()
        val (cmd, caret) = FlowEditor(flow()).replaceRange(range.normalized(), text, style)
        commitEdit(cmd, caret)
        return caret
    }

    /** Apply an already-built (and applied) command from the format bar / menus. */
    fun commitEdit(cmd: Command?, caretTo: FlowPos?) {
        val prefix = pendingPrefix?.also { pendingPrefix = null }
        val head = when {
            prefix == null -> cmd
            cmd == null -> prefix
            else -> CompositeCommand(listOf(prefix, cmd))
        }
        if (head == null) {
            caretTo?.let { placeCaret(it) }
            return
        }
        onChanged(active)
        val extras = autoAppendPages()
        history.push(if (extras.isEmpty()) head else CompositeCommand(listOf(head) + extras))
        caretTo?.let { selection = FlowRange.caret(it) }
        onEdited()
        onFlushed()
        ensureCaretVisible()
        requestRender()
    }

    /** Push the open typing burst (plus any auto-added pages) as one undo step. */
    fun flushBurst() {
        handler.removeCallbacks(idleFlush)
        val para = burstPara ?: return
        val before = burstBefore
        burstPara = null
        burstBefore = null
        val extras = burstExtras.toList()
        burstExtras.clear()
        val edit = if (before != null && !before.matches(para)) {
            FlowEditParagraph(para, before, ParaSnapshot.of(para))
        } else {
            null
        }
        val cmds = listOfNotNull(edit) + extras
        when {
            cmds.isEmpty() -> return
            cmds.size == 1 -> history.push(cmds[0])
            else -> history.push(CompositeCommand(cmds))
        }
        onFlushed()
    }

    /** Append pages, each the size the note gives an added page, until the published layout stops overflowing. */
    private fun autoAppendPages(): List<Command> {
        val need = frame()?.extraPagesNeeded ?: 0
        if (need <= 0) return emptyList()
        val doc = state.document
        val out = mutableListOf<Command>()
        repeat(need) {
            val (w, h) = doc.blankPageSize(doc.pages.lastOrNull() ?: return out)
            out += AddPageAuto(doc, Page(w, h), doc.pages.size).also { it.redo() }
        }
        state.relayout()
        onChanged(active)
        return out
    }

    // --- geometry ---

    fun ensureCaretVisible() {
        if (!active) return
        val f = frame() ?: return
        val (pi, rect) = f.caretRect(selection.end) ?: return
        if (state.pageRects.getOrNull(pi) == null) return
        val cr = state.fromPageSpaceRect(pi, rect)
        val topV = state.contentToViewport(Pt(0.0, cr.top)).y
        val bottomV = state.contentToViewport(Pt(0.0, cr.bottom)).y
        val margin = CARET_MARGIN * state.devicePxPerDp
        val top = state.insetTop + margin
        val bottom = state.viewportH - state.insetBottom - margin
        if (bottom <= top) return
        val dy = when {
            // The fork's typewriter scrolling: the caret's line is held at the viewport's middle
            // while typing, so the eye never chases the text down the screen.
            typewriterScrolling -> {
                val middle = (topV + bottomV) / 2.0
                val centre = state.insetTop + (state.viewportH - state.insetTop - state.insetBottom) / 2.0
                middle - centre
            }
            bottomV > bottom -> bottomV - bottom
            topV < top -> topV - top
            else -> 0.0
        }
        if (abs(dy) < 1.0) return
        state.scrollBy(0.0, dy)
        onViewChanged()
        requestRender()
    }

    /** The caret's on-screen rect (viewport px), for the IME's cursor anchor. */
    fun caretViewportRect(): Rect? {
        val f = frame() ?: return null
        val (pi, rect) = f.caretRect(selection.end) ?: return null
        if (state.pageRects.getOrNull(pi) == null) return null
        val cr = state.fromPageSpaceRect(pi, rect)
        val tl = state.contentToViewport(Pt(cr.left, cr.top))
        return Rect(tl.x, tl.y, cr.w * state.zoom, cr.h * state.zoom)
    }

    /** Selection highlight + caret + drag handles, drawn in the interaction overlay (content space). */
    fun drawOverlay(r: com.xnota.core.pal.Renderer) {
        if (!active) return
        val f = frame() ?: return
        val accent = state.palette.accent
        if (!selection.collapsed) {
            for ((pi, rect) in f.selectionRects(selection)) {
                if (state.pageRects.getOrNull(pi) == null) continue
                r.fillRect(state.fromPageSpaceRect(pi, rect), accent.withAlpha(70))
            }
            val norm = selection.normalized()
            handleCenter(norm.start, isStart = true)?.let { handles.draw(r, it, isStart = true, accent) }
            handleCenter(norm.end, isStart = false)?.let { handles.draw(r, it, isStart = false, accent) }
        } else {
            val (pi, cr) = f.caretRect(selection.end) ?: return
            if (state.pageRects.getOrNull(pi) == null) return
            var top = cr.top
            var height = cr.h
            // A pending style (size bumped before typing) previews on the caret itself,
            // as the line box the first character it styles is going to leave behind.
            pendingStyle?.let { pending ->
                f.placedLineFor(selection.end)?.second?.let { line ->
                    caretMetricsFor?.invoke(pending)?.let { m ->
                        val span = caretPreviewSpan(line.top, line.held, m)
                        top = span.first
                        height = span.second
                    }
                }
            }
            val w = (FlowFrame.CARET_WIDTH / state.zoom).coerceAtLeast(0.75)
            r.fillRect(state.fromPageSpaceRect(pi, Rect(cr.left - w / 2.0, top, w, height)), accent)
        }
    }

    /** Viewport point of the line middle at [pos]: the spot a handle drag really targets. */
    private fun caretAnchorViewport(pos: FlowPos): Pt? {
        val f = frame() ?: return null
        val (pi, cr) = f.caretRect(pos) ?: return null
        if (state.pageRects.getOrNull(pi) == null) return null
        return state.contentToViewport(state.fromPageSpace(pi, Pt(cr.left, (cr.top + cr.bottom) / 2.0)))
    }

    /** Content-space centre of the teardrop handle for the caret at [pos]. */
    private fun handleCenter(pos: FlowPos, isStart: Boolean): Pt? {
        val f = frame() ?: return null
        val (pi, cr) = f.caretRect(pos) ?: return null
        if (state.pageRects.getOrNull(pi) == null) return null
        // Hang the handle below the caret's *display* rect, so it reads screen-down even
        // when the page is rotated.
        val crC = state.fromPageSpaceRect(pi, cr)
        return handles.center(Pt(crC.left, crC.bottom), isStart)
    }

    /** The handle a press at [viewport] grabs, preferring the nearer of the two. */
    private fun grabHandle(viewport: Pt): TextHandles.Handle? {
        val norm = selection.normalized()
        return handles.grab(viewport, handleCenter(norm.start, isStart = true), handleCenter(norm.end, isStart = false))
    }

    private fun caretPosAt(content: Pt): FlowPos? {
        val (pi, local) = state.pagePointAt(content) ?: return null
        return when (val hit = frame()?.hitTest(pi, local) ?: return null) {
            is FlowHit.Caret -> hit.pos
            is FlowHit.Checkbox -> FlowPos(hit.paraIndex, 0)
            FlowHit.BeyondEnd -> flow().endPos()
        }
    }


    companion object {
        const val DRAG_SLOP = 14.0
        const val DOUBLE_TAP_MS = 350L
        const val DOUBLE_TAP_SLOP = 32.0
        const val CARET_MARGIN = 24.0
        const val BURST_IDLE_MS = 2000L
        val LONG_PRESS_MS = android.view.ViewConfiguration.getLongPressTimeout().toLong()

        /** A long press this close to a table rule holds the table, even over text. */
        const val TABLE_RULE_SLOP_DP = 6.0

        /** What separates the words of a typed run (the daily goal's counter). */
        private val WORD_BREAK = Regex("\\s+")
    }
}
