package com.software.sello.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A blank capture can never become a baseline nor pass as one. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PixelCaptureTest {
    /** A bitmap of exactly [colours] distinct colours, in bands. */
    private fun bands(colours: Int): Bitmap {
        val bands = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)
        bands.eraseColor(Color.rgb(0, 0, 0))
        val canvas = Canvas(bands)
        val paint = Paint()
        for (band in 1 until colours) {
            paint.color = Color.rgb(band, 0, 0)
            canvas.drawRect(0f, band * 10f, 320f, band * 10f + 10f, paint)
        }
        return bands
    }

    @Test
    fun aBlankBitmapFailsAndRecordsNothing() {
        val path = "build/pixel-capture-test/blank.png"
        File(path).delete()

        assertThrows(AssertionError::class.java) { bands(1).captureBaseline(path) }
        assertFalse(File(path).exists())
    }

    @Test
    fun aBitmapOfTooFewColoursIsBlank() {
        assertThrows(AssertionError::class.java) {
            requireDrawn(bands(FEWEST_COLOURS - 1), "bands.png")
        }
    }

    @Test
    fun aBitmapOfEnoughColoursIsDrawn() {
        requireDrawn(bands(FEWEST_COLOURS), "bands.png")
    }
}
