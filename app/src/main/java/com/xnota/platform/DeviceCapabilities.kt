package com.xnota.platform

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent

/** Device input-capability probes, used to pick sensible first-run defaults. */
object DeviceCapabilities {

    /**
     * Whether the device exposes a genuine stylus/pen digitizer.
     *
     * Used to decide whether finger-draw should default on: a device with no pen
     * cannot draw at all under the default finger-pans behaviour, so we enable it
     * automatically there. This is a hardware-capability query (via [InputManager]),
     * independent of whatever happens to be touching the screen right now.
     *
     * Deliberately conservative about returning `true`, because a spurious "has
     * stylus" is the costly mistake — it would leave a pen-less user unable to draw.
     * A standalone stylus input device (e.g. a Samsung S Pen digitizer, which is a
     * separate device from the touchscreen) is trusted outright; the main touchscreen
     * frequently advertises `SOURCE_STYLUS` in a combined bitmask even with no pen, so
     * there the source bit is trusted only when a pen-only motion axis (hover distance
     * or tilt — absent on plain capacitive panels) corroborates a real digitizer.
     * Any probe failure falls back to "no stylus" so finger-draw still gets enabled.
     */
    fun hasStylus(context: Context): Boolean = try {
        val im = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        im != null && im.inputDeviceIds.any { id ->
            im.getInputDevice(id)?.let { isStylusDevice(it) } == true
        }
    } catch (_: Exception) {
        false
    }

    private fun isStylusDevice(dev: InputDevice): Boolean {
        // A paired external pen is a distinct, trustworthy signal on its own.
        if (dev.supportsSource(InputDevice.SOURCE_BLUETOOTH_STYLUS)) return true
        if (!dev.supportsSource(InputDevice.SOURCE_STYLUS)) return false
        // A standalone stylus device (not the touchscreen itself) is a real digitizer.
        if (!dev.supportsSource(InputDevice.SOURCE_TOUCHSCREEN)) return true
        // Otherwise it's the touchscreen advertising stylus in a combined bitmask;
        // trust it only when a pen-only axis (hover distance / tilt — absent on plain
        // capacitive panels) backs it up. A digitizer registers those ranges under a
        // *combined* source (e.g. TOUCHSCREEN | STYLUS), so test the bit rather than
        // querying getMotionRange(axis, SOURCE_STYLUS), which matches the source exactly.
        return dev.motionRanges.any { r ->
            (r.axis == MotionEvent.AXIS_DISTANCE || r.axis == MotionEvent.AXIS_TILT) &&
                (r.source and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS
        }
    }
}
