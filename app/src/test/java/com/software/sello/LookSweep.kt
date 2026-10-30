package com.software.sello

import com.software.sello.designsystem.theme.SelloInk

/** A window of [width] × [height] dp. */
data class SweepWindow(val width: Int, val height: Int) {
    override fun toString() = "${width}x$height"
}

/** One rendering of a screen: its theme ([ink], [dark]) and its [window]. */
data class SweepLook(val ink: SelloInk, val dark: Boolean, val window: SweepWindow) {
    override fun toString() = "$ink ${if (dark) "dark" else "light"} in $window"
}

/**
 * The looks a screen is checked in. Layout depends on the window; ink and dark mode only
 * change colours. So every window is rendered once, in the default theme, and every other
 * theme once more in the first window. Rendering each theme in every window would show
 * nothing new and cost three times more.
 */
fun sweepLooks(windows: List<SweepWindow>): List<SweepLook> {
    val default = SweepLook(SelloInk.Cobalto, false, windows.first())
    val layouts = windows.map { default.copy(window = it) }
    val themes = SelloInk.entries.flatMap { ink ->
        listOf(false, true).map { dark -> default.copy(ink = ink, dark = dark) }
    }
    return layouts + themes.filterNot { it == default }
}
