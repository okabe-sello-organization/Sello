package com.software.sello.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.IntRect
import com.github.takahirom.roborazzi.captureRoboImage

/*
 * The node's pixels on the JVM. Compose's own `captureToImage` waits for a frame that
 * Robolectric's paused main looper never delivers, so this draws the compose view into a
 * bitmap and cuts the node out of it. Needs `@GraphicsMode(GraphicsMode.Mode.NATIVE)`.
 */
fun SemanticsNodeInteraction.captureToImage(): ImageBitmap = drawnBitmap().asImageBitmap()

/** The node cut out of its window as a bitmap; see [captureToImage]. */
fun SemanticsNodeInteraction.drawnBitmap(): Bitmap {
    val node = fetchSemanticsNode()
    val view = (node.root as ViewRootForTest).view
    val whole = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(whole))
    val bounds = node.boundsInRoot
    val area = IntRect(
        bounds.left.toInt(),
        bounds.top.toInt(),
        bounds.right.toInt(),
        bounds.bottom.toInt()
    )
    return Bitmap.createBitmap(whole, area.left, area.top, area.width, area.height)
}

/**
 * Saves this bitmap as a Roborazzi image, after [requireDrawn]: a blank drawing fails the
 * test before it is recorded as a baseline or compared with one.
 */
fun Bitmap.captureBaseline(path: String) {
    requireDrawn(this, path)
    captureRoboImage(path)
}

/**
 * The fewest colours a real group of components has. Text alone is anti-aliased into
 * hundreds; the sparsest baseline has over 500. A blank capture has one or two. The
 * app's screenshot helper keeps the same rule.
 */
const val FEWEST_COLOURS = 32

/** Fails when [bitmap] has fewer than [FEWEST_COLOURS] distinct colours: nothing was drawn. */
fun requireDrawn(bitmap: Bitmap, path: String) {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val seen = HashSet<Int>()
    for (pixel in pixels) {
        if (seen.add(pixel) && seen.size >= FEWEST_COLOURS) return
    }
    throw AssertionError(
        "$path is blank: ${seen.size} distinct colours in ${bitmap.width}x${bitmap.height} px, " +
            "fewer than $FEWEST_COLOURS. Nothing was drawn; nothing is recorded or compared."
    )
}
