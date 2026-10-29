package com.software.sello

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme

/*
 * Screen tests on the JVM (ADR 0022). A test class runs with
 * `@RunWith(AndroidJUnit4::class)` and `createComposeRule()`; Robolectric gives it the
 * screen of `src/test/resources/robolectric.properties`: API 37 and 320x640 dp at one
 * pixel per dp, the hosted emulator's screen.
 */

/** How a screen is shown: the ink, light or dark, the font scale and, if set, a window. */
data class Look(
    val ink: SelloInk = SelloInk.Cobalto,
    val dark: Boolean = false,
    val fontScale: Float = 1f,
    /** A window of this size inside the screen; null for the whole screen. */
    val window: DpSize? = null
) {
    /** A short name for a screenshot file, such as `cobalto-dark-font2.0`. */
    val label get() = "${ink.name.lowercase()}-${if (dark) "dark" else "light"}-font$fontScale"
}

/** Shows [content] in Sello's theme as [look] says, with motion reduced so nothing is mid-animation. */
fun ComposeContentTestRule.showSello(look: Look = Look(), content: @Composable () -> Unit) {
    setContent {
        val density = LocalDensity.current.density
        CompositionLocalProvider(LocalDensity provides Density(density, look.fontScale)) {
            SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                val window = look.window
                if (window == null) content() else Box(Modifier.requiredSize(window)) { content() }
            }
        }
    }
}

/**
 * Scrolls to this node when a scrolling container holds it, and leaves it be otherwise,
 * as the device tests' helper of the same name does.
 */
fun SemanticsNodeInteraction.revealed(): SemanticsNodeInteraction {
    var parent = fetchSemanticsNode().parent
    while (parent != null) {
        if (SemanticsActions.ScrollBy in parent.config) return performScrollTo()
        parent = parent.parent
    }
    return this
}
