package com.software.sello.navigation

import com.software.sello.NightlyOnly
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.navigation.UiAcceptanceSweep.Window
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The sweep on every pull request: the smallest phone window and the shortest
 * landscape one, in light and dark.
 */
@RunWith(Parameterized::class)
class UiAcceptanceSweepTest(window: Window) : UiAcceptanceSweep(window) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun windows() = listOf(
            Window("compact 320x640, Cobalto light", 320, 640, SelloInk.Cobalto, false),
            Window("short landscape 640x360, Cobalto dark", 640, 360, SelloInk.Cobalto, true)
        )
    }
}

/** The other three windows of the matrix, in the nightly and manual runs only. */
@NightlyOnly
@RunWith(Parameterized::class)
class UiAcceptanceSweepNightlyTest(window: Window) : UiAcceptanceSweep(window) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun windows() = listOf(
            Window("short landscape 640x360, Violeta dark", 640, 360, SelloInk.Violeta, true),
            Window("medium 700x900, Violeta light", 700, 900, SelloInk.Violeta, false),
            Window("expanded 1000x700, Cobalto light", 1000, 700, SelloInk.Cobalto, false)
        )
    }
}
