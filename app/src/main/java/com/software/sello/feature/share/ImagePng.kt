package com.software.sello.feature.share

import androidx.compose.ui.graphics.ImageBitmap

/** Writes an image drawn on screen as PNG bytes, off the main thread. */
fun interface ImagePng {
    /** Null when it cannot be encoded. */
    suspend fun encode(image: ImageBitmap): ByteArray?
}
