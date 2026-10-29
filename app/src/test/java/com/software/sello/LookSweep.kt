package com.software.sello

import com.software.sello.designsystem.theme.SelloInk

/** A window of [width] × [height] dp. */
data class SweepWindow(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"
}

/** One rendering of a screen: its theme ([ink], [dark]) and layout inputs ([scale], [window]). */
data class SweepLook(
    val ink: SelloInk,
    val dark: Boolean,
    val scale: Float,
    val window: SweepWindow
) {
    override fun toString() = "$ink ${if (dark) "dark" else "light"} at $scale in $window"
}

/**
 * The looks a screen is checked in. Layout depends on the font scale and the window;
 * ink and dark mode only change colours. So every scale × window is rendered once, in
 * the default theme, and every other theme once more at the first scale and window.
 * Rendering each theme at every size would show nothing new and cost three times more.
 */
fun sweepLooks(
    windows: List<SweepWindow>,
    scales: List<Float> = listOf(1f, 1.3f)
): List<SweepLook> {
    val default = SweepLook(SelloInk.Cobalto, false, scales.first(), windows.first())
    val layouts = scales.flatMap { scale ->
        windows.map { default.copy(scale = scale, window = it) }
    }
    val themes = SelloInk.entries.flatMap { ink ->
        listOf(false, true).map { dark -> default.copy(ink = ink, dark = dark) }
    }
    return layouts + themes.filterNot { it == default }
}
