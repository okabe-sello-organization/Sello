package com.software.sello.platform

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.feature.recibo.ImagePng
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.withContext

/** PNG through Android's own encoder, which is lossless. */
class AndroidImagePng(private val dispatchers: DispatcherProvider) : ImagePng {
    override suspend fun encode(image: ImageBitmap): ByteArray? = withContext(dispatchers.default) {
        val drawn = image.asAndroidBitmap()
        // A bitmap kept in graphics memory is copied out before it is encoded.
        val bitmap = if (drawn.config == Bitmap.Config.HARDWARE) {
            drawn.copy(Bitmap.Config.ARGB_8888, false) ?: return@withContext null
        } else {
            drawn
        }
        val out = ByteArrayOutputStream()
        if (bitmap.compress(
                Bitmap.CompressFormat.PNG,
                PNG_QUALITY,
                out
            )
        ) {
            out.toByteArray()
        } else {
            null
        }
    }

    private companion object {
        /** Ignored for PNG, which is lossless. */
        const val PNG_QUALITY = 100
    }
}
