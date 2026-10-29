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
    bitmap.captureRoboImage(path)
}
