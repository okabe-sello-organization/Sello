package com.software.sello.designsystem.component

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.IntRect

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
