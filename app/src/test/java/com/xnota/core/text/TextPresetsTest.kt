package com.xnota.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextPresetsTest {

    @Test fun titleIsDoubleTheBaseAndBold() {
        val s = TextPresets.apply(TextPresets.Kind.TITLE, CharStyle(), 12.0)
        assertEquals(24.0, s.sizePt!!, 1e-9)
        assertEquals(true, s.bold)
    }

    @Test fun headingIsOneAndAHalfTimesTheBaseAndBold() {
        val s = TextPresets.apply(TextPresets.Kind.HEADING, CharStyle(), 12.0)
        assertEquals(18.0, s.sizePt!!, 1e-9)
        assertEquals(true, s.bold)
    }

    @Test fun bodyClearsTheSizeAndTheBold() {
        val s = TextPresets.apply(TextPresets.Kind.BODY, CharStyle(bold = true, sizePt = 30.0), 12.0)
        assertEquals(false, s.bold)
        assertNull(s.sizePt)
    }

    @Test fun captionIsThreeQuartersAndNeverBold() {
        val s = TextPresets.apply(TextPresets.Kind.CAPTION, CharStyle(bold = true), 12.0)
        assertEquals(9.0, s.sizePt!!, 1e-9)
        assertEquals(false, s.bold)
    }

    @Test fun presetsScaleWithTheFlowNotTheScreen() {
        // A tablet's larger default lands a proportionally larger title, not the same 24 pt.
        val phone = TextPresets.apply(TextPresets.Kind.TITLE, CharStyle(), 12.0)
        val tablet = TextPresets.apply(TextPresets.Kind.TITLE, CharStyle(), 16.0)
        assertEquals(32.0, tablet.sizePt!!, 1e-9)
        assertEquals(phone.sizePt!! * (16.0 / 12.0), tablet.sizePt!!, 1e-9)
    }

    @Test fun sizesKeepTheSteppersBounds() {
        // A base already at the cap stays at it rather than doubling past what typing allows.
        val s = TextPresets.apply(TextPresets.Kind.TITLE, CharStyle(), 96.0)
        assertEquals(TextPresets.MAX_PT, s.sizePt!!, 1e-9)
        val tiny = TextPresets.apply(TextPresets.Kind.CAPTION, CharStyle(), 6.0)
        assertEquals(TextPresets.MIN_PT, tiny.sizePt!!, 1e-9)
    }

    @Test fun presetsKeepTheStyleTheyDoNotOwn() {
        // A preset resizes; it does not erase the colour, face or emphasis already on the text.
        val base = CharStyle(italic = true, color = com.xnota.core.model.Rgba(1, 2, 3))
        val s = TextPresets.apply(TextPresets.Kind.HEADING, base, 12.0)
        assertEquals(true, s.italic)
        assertEquals(base.color, s.color)
    }
}
