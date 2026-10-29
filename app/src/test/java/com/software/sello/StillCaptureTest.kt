package com.software.sello

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A blank capture can never become a baseline nor pass as one. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StillCaptureTest {
    @get:Rule
    val rule = createComposeRule()

    private fun bitmap() = Bitmap.createBitmap(320, 640, Bitmap.Config.ARGB_8888)

    @Test
    fun aBitmapOfOneColourIsBlank() {
        val blank = bitmap().apply { eraseColor(Color.WHITE) }

        val error = assertThrows(AssertionError::class.java) { requireDrawn(blank, "blank.png") }

        assertTrue(
            error.message!!,
            error.message!!.contains("blank.png is blank: 1 distinct colours")
        )
    }

    /** A bitmap of exactly [colours] distinct colours, in bands. */
    private fun bands(colours: Int): Bitmap {
        val bands = bitmap().apply { eraseColor(Color.rgb(0, 0, 0)) }
        val canvas = Canvas(bands)
        val paint = Paint()
        for (band in 1 until colours) {
            paint.color = Color.rgb(band, 0, 0)
            canvas.drawRect(0f, band * 10f, 320f, band * 10f + 10f, paint)
        }
        return bands
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

    @Test
    fun anEmptyScreenFailsTheCaptureAndRecordsNothing() {
        val path = "build/still-capture-test/empty.png"
        File(path).delete()
        rule.showSello { Box(Modifier.fillMaxSize()) }

        assertThrows(AssertionError::class.java) { rule.captureStill(path) }
        assertFalse(File(path).exists())
    }
}
