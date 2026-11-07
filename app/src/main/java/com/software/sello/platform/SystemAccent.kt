package com.software.sello.platform

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.software.sello.designsystem.theme.hueOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The hue of the phone's accent colour, the one its wallpaper gives, as a whole OKLCH degree
 * from 0 to 359; null when the phone has none: before Android 12, or a grey accent.
 */
fun interface SystemAccent {
    fun hue(): Int?
}

/**
 * Reads `system_accent1_500`, the accent at its middle tone, from [context]'s resources, which
 * follow the phone's colours. [sdk] is the Android version Sello runs on.
 */
class AndroidSystemAccent(
    private val context: Context,
    private val sdk: Int = Build.VERSION.SDK_INT
) : SystemAccent {
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    private val hasAccent: Boolean get() = sdk >= Build.VERSION_CODES.S

    override fun hue(): Int? = if (hasAccent) {
        hueOf(context.getColor(android.R.color.system_accent1_500))
    } else {
        null
    }
}

/**
 * The phone's colour as Sello last read it, for Sistema: [hue] is the accent's, or null with
 * none. The phone's colours change while Sello is away, so it is read when Sello starts and
 * again by [refresh] each time Sello is back on screen; the ink follows without touching any
 * screen's state.
 */
class SystemColor(private val accent: SystemAccent) {
    private val read = MutableStateFlow(accent.hue())
    val hue: StateFlow<Int?> = read.asStateFlow()

    fun refresh() {
        read.value = accent.hue()
    }
}
