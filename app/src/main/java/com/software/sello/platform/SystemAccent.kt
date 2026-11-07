package com.software.sello.platform

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.software.sello.designsystem.theme.hueOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the phone's accent colour, the one its wallpaper gives, offers Sistema. */
sealed interface PhoneColor {
    /** Android 11 has no accent colour: Sistema is not offered there. */
    data object None : PhoneColor

    /**
     * A grey accent, as a monochrome style gives: no hue to take. Sistema is still offered,
     * and Sello is drawn in Cobalto until the phone has a colour again.
     */
    data object Grey : PhoneColor

    /** The accent's hue, a whole OKLCH degree from 0 to 359. */
    data class Hue(val degrees: Int) : PhoneColor
}

/** The hue Sistema takes from the phone, or null when it has none to give. */
val PhoneColor.hue: Int? get() = (this as? PhoneColor.Hue)?.degrees

/** Reads the phone's accent colour now. */
fun interface SystemAccent {
    fun read(): PhoneColor
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

    override fun read(): PhoneColor = if (hasAccent) {
        hueOf(context.getColor(android.R.color.system_accent1_500))
            ?.let(PhoneColor::Hue)
            ?: PhoneColor.Grey
    } else {
        PhoneColor.None
    }
}

/**
 * The phone's colour as Sello last read it, for Sistema. The phone's colours change while
 * Sello is away, so it is read when Sello starts and again by [refresh] each time Sello is
 * back on screen; the ink follows without touching any screen's state.
 */
class SystemColor(private val accent: SystemAccent) {
    private val read = MutableStateFlow(accent.read())
    val color: StateFlow<PhoneColor> = read.asStateFlow()

    fun refresh() {
        read.value = accent.read()
    }
}
