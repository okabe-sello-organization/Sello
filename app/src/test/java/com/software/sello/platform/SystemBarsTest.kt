package com.software.sello.platform

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.R
import com.software.sello.designsystem.component.Blotter
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The bar icons follow what Sello draws under them, not the phone's dark mode
 * (SELLO-171): the status bar reads on the band or the desk, the navigation bar on the
 * theme's paper.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Whether the status bar and the navigation bar draw dark icons. */
    private fun darkIcons(): Pair<Boolean, Boolean> {
        compose.waitForIdle()
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        return bars.isAppearanceLightStatusBars to bars.isAppearanceLightNavigationBars
    }

    /** Sello in the [dark] or light theme, as MainActivity shows it. */
    private fun show(dark: () -> Boolean, screen: @Composable () -> Unit) {
        compose.setContent {
            SystemBarsFollowTheme(dark()) {
                SelloTheme(darkTheme = dark(), reducedMotion = true) { screen() }
            }
        }
    }

    /** A band screen, as Recibo and Configuración draw it. */
    @Composable
    private fun BandScreen() =
        SelloScaffold(title = "Configuración", blotter = Blotter.Short) { Box {} }

    /** An entry form or editor with its bar on the desk, as Anotar varios draws it. */
    @Composable
    private fun DeskScreen() = SelloScaffold(title = "Anotar varios") { Box {} }

    @Test
    fun `a light phone with Sello Oscuro draws light icons on the desk`() {
        show({ true }) { DeskScreen() }

        assertEquals(false to false, darkIcons())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `a dark phone with Sello Claro draws dark icons on the desk`() {
        show({ false }) { DeskScreen() }

        assertEquals(true to true, darkIcons())
    }

    @Test
    fun `the band keeps white status icons in Claro while the navigation bar follows the theme`() {
        show({ false }) { BandScreen() }

        assertEquals(false to true, darkIcons())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `the band keeps white status icons in Oscuro`() {
        show({ true }) { BandScreen() }

        assertEquals(false to false, darkIcons())
    }

    @Test
    fun `a screen shown over another takes the status bar and gives it back when it leaves`() {
        var editing by mutableStateOf(false)
        show({ false }) {
            BandScreen()
            if (editing) DeskScreen()
        }
        assertEquals(false to true, darkIcons())

        editing = true
        assertEquals(true to true, darkIcons())

        editing = false
        assertEquals(false to true, darkIcons())
    }

    @Test
    fun `the first frame on the band already has white status icons`() {
        compose.mainClock.autoAdvance = false
        show({ false }) { BandScreen() }

        // Read before any later frame: what the band said must be applied with it.
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        assertEquals(false, bars.isAppearanceLightStatusBars)
    }

    @Test
    fun `changing the theme updates the icons at once`() {
        var dark by mutableStateOf(false)
        show({ dark }) { DeskScreen() }
        assertEquals(true to true, darkIcons())

        dark = true
        assertEquals(false to false, darkIcons())

        dark = false
        assertEquals(true to true, darkIcons())
    }

    /** The colour of a theme's window; Theme.Sello's is drawn before Compose's first frame. */
    private fun windowBackground(theme: Int = R.style.Theme_Sello): Int {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val attrs = context.obtainStyledAttributes(
            theme,
            intArrayOf(android.R.attr.windowBackground)
        )
        return try {
            attrs.getColor(0, 0)
        } finally {
            attrs.recycle()
        }
    }

    @Test
    @Config(qualifiers = "+night")
    fun `a dark phone starts on the dark desk`() {
        assertEquals(0xFF090D1C.toInt(), windowBackground())
    }

    @Test
    fun `a light phone starts on the platform's light window as before`() {
        assertEquals(
            windowBackground(android.R.style.Theme_Material_Light_NoActionBar),
            windowBackground()
        )
    }
}
