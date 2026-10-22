package com.xnota.core.model

import com.xnota.core.FakeTextMeasurer
import com.xnota.core.geometry.Affine
import com.xnota.core.geometry.Flip
import com.xnota.core.geometry.Pt
import com.xnota.core.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.PI

class ImageFlipTest {

    private fun image(angle: Double = 0.0) =
        ImageItem(ImageData(File("unused.png"), 40, 20), Rect(100.0, 50.0, 40.0, 20.0), angle = angle)

    private fun assertSameCorners(want: List<Pt>, got: List<Pt>) {
        for (w in want) assertTrue("missing $w in $got", got.any { it.distanceTo(w) < 1e-9 })
    }

    @Test fun aMirrorFlipsThePictureWhereItLands() {
        val img = image()
        img.applyTransform(Affine.scaleAbout(Pt(0.0, 0.0), -1.0, 1.0))
        assertEquals(Flip.X, img.flip)
        assertEquals(0.0, img.angle, 0.0)
        assertEquals(Rect(-140.0, 50.0, 40.0, 20.0), img.rect)
        val up = image()
        up.applyTransform(Affine.scaleAbout(up.rect.center, 1.0, -1.0))
        assertEquals("upside down, not half a turn", Flip.Y, up.flip)
        assertEquals(0.0, up.angle, 0.0)
    }

    @Test fun aTurnedImageTurnsTheOtherWay() {
        for (angle in listOf(0.4, 2.0, -2.6)) {
            val img = image(angle)
            val t = Affine.scaleAbout(img.rect.center, -1.0, 1.0)
            val want = img.corners().map(t::apply)
            img.applyTransform(t)
            assertSameCorners(want, img.corners())
            assertTrue("$angle stays nearer upright: ${img.angle}", kotlin.math.abs(img.angle) <= PI / 2.0 + 1e-9)
        }
    }

    @Test fun twoMirrorsComeBack() {
        val img = image(0.3)
        val t = Affine.scaleAbout(Pt(10.0, 0.0), -1.0, 1.0)
        img.applyTransform(t)
        img.applyTransform(t)
        assertEquals(Flip.NONE, img.flip)
        assertEquals(0.3, img.angle, 1e-12)
        assertEquals(100.0, img.rect.x, 1e-9)
    }

    @Test fun undoAndCopiesKeepTheFlip() {
        val img = image()
        val snap = img.snapshotGeometry()
        img.applyTransform(Affine.scaleAbout(img.rect.center, -1.0, 1.0))
        assertEquals(Flip.X, (img.deepCopy(FakeTextMeasurer()) as ImageItem).flip)
        img.restoreGeometry(snap)
        assertEquals(Flip.NONE, img.flip)
    }
}
