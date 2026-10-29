package com.software.sello

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Saves the screen the rule shows as a Roborazzi image, drawing it here instead of asking
 * Roborazzi to copy the window. The window copy came back as the empty window for some
 * screens, differently from one run to the next, though the screen was composed and a
 * draw by hand showed it whole; it also made a baseline recorded blank pass its own check.
 * Windows opened over the screen, such as a sheet, are drawn on top, each where it sits.
 * Every app screenshot goes through here, and a blank drawing fails the test before
 * Roborazzi records or compares it (see [requireDrawn]).
 */
fun ComposeContentTestRule.captureStill(path: String) {
    waitForIdle()
    val roots = onAllNodes(isRoot()).fetchSemanticsNodes().map { it.root as View }
    val screen = roots.first()
    val bitmap = Bitmap.createBitmap(screen.width, screen.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val origin = IntArray(2).also(screen::getLocationOnScreen)
    for (view in roots) {
        val at = IntArray(2).also(view::getLocationOnScreen)
        canvas.save()
        canvas.translate((at[0] - origin[0]).toFloat(), (at[1] - origin[1]).toFloat())
        view.draw(canvas)
        canvas.restore()
    }
    requireDrawn(bitmap, path)
    bitmap.captureRoboImage(path)
}

/**
 * The fewest colours a real screen has. Text alone is anti-aliased into hundreds; the
 * sparsest baseline, a one-line bar, has over 300. A blank capture has one or two.
 */
const val FEWEST_COLOURS = 32

/**
 * Fails when [bitmap] has fewer than [FEWEST_COLOURS] distinct colours: nothing was drawn,
 * so it must never be recorded as a baseline nor compared with one.
 */
fun requireDrawn(bitmap: Bitmap, path: String) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val seen = HashSet<Int>()
    for (pixel in pixels) {
        if (seen.add(pixel) && seen.size >= FEWEST_COLOURS) return
    }
    throw AssertionError(
        "$path is blank: ${seen.size} distinct colours in ${bitmap.width}x${bitmap.height} px, " +
            "fewer than $FEWEST_COLOURS. The screen was not drawn; nothing is recorded or compared."
    )
}
