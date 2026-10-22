package com.xnota.canvas

import android.view.KeyEvent
import android.view.MotionEvent

/**
 * Whether a stylus side button is currently down.
 *
 * Pens do not agree on how to report this, and getting it right took several releases of
 * device-specific work, so the infinite canvas latches it through this rather than working it out
 * again. Three delivery routes are covered:
 *
 *  - the touch stream's `buttonState`, which most pens use;
 *  - the hovering generic-motion stream, for pens that report the button only while hovering and
 *    never in a touch event;
 *  - a `KeyEvent`, which Bluetooth and USI pens use, including one vendor code with no standard
 *    mapping ([InteractionController.VENDOR_HELD_BUTTON_KEYCODE]).
 *
 * A fourth quirk is handled higher up and needs nothing here: some Samsung builds tag a
 * button-held stroke with proprietary action codes instead of DOWN/MOVE/UP, and `MainActivity`
 * rewrites those before dispatch, so any view in the tree gets a normal touch stream.
 *
 * The button masks and the vendor keycode are read from [InteractionController] rather than copied,
 * so the two canvases cannot drift apart on which buttons count.
 */
class StylusButtonLatch {

    /** True while a side button is held, from whichever stream reported it. */
    var held = false
        private set

    /** Which one, for two-button pens (the fork): 0 = none, 1 = the primary/legacy button,
     *  2 = the *second* side button (BUTTON_STYLUS_SECONDARY / KEYCODE_STYLUS_BUTTON_SECONDARY).
     *  Pens with a single button, and every pre-API-35 delivery route, always report 1. */
    var heldButton = 0
        private set

    /**
     * Latch the button off the generic-motion (hover) stream. This **assigns** rather than ORs, so
     * a pen that reports on both this stream and as a key event cannot latch stuck on. Returns true
     * when the button has just gone up, so a caller can end a hovering gesture.
     */
    fun onGenericMotion(e: MotionEvent): Boolean {
        if (e.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return false
        heldButton = buttonOf(e.buttonState)
        held = heldButton != 0
        return !held
    }

    /**
     * Latch the button off a key event. Returns true when [keyCode] was a stylus side button, so
     * the host consumes it instead of letting it fall through as a normal key.
     */
    fun onKey(keyCode: Int, down: Boolean): Boolean {
        if (!isStylusButtonKey(keyCode)) return false
        held = down
        heldButton = if (down) if (keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY) 2 else 1 else 0
        return true
    }

    fun reset() {
        held = false
        heldButton = 0
    }

    /** Whether [e] is a stylus contact with the side button down, on either route. */
    fun heldFor(e: MotionEvent): Boolean =
        e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            ((e.buttonState and InteractionController.STYLUS_BUTTON_MASK) != 0 || held)

    /** Whether [e] carries the *second* side button (touch stream or latch); single-button pens
     *  never report it, so this is false everywhere but a genuine two-button press. */
    fun secondaryFor(e: MotionEvent): Boolean =
        e.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS &&
            ((e.buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0 || (held && heldButton == 2))

    companion object {
        /** Which button a touch-stream buttonState says is down; 2 only for the stylus-specific
         *  secondary bit, 1 for every other stylus-button bit (single-button and legacy routes). */
        fun buttonOf(buttonState: Int): Int =
            if ((buttonState and MotionEvent.BUTTON_STYLUS_SECONDARY) != 0) 2
            else if ((buttonState and InteractionController.STYLUS_BUTTON_MASK) != 0) 1
            else 0

        fun isStylusButtonKey(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_PRIMARY ||
                keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_SECONDARY ||
                keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_TERTIARY ||
                keyCode == KeyEvent.KEYCODE_STYLUS_BUTTON_TAIL ||
                keyCode == InteractionController.VENDOR_HELD_BUTTON_KEYCODE
    }
}
