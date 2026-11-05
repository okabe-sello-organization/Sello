package com.software.sello.platform

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The bar icons follow the theme chosen in Sello, not the phone's dark mode (SELLO-171). */
@RunWith(AndroidJUnit4::class)
class SystemBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Whether the status and navigation bars draw dark icons, as a pair. */
    private fun darkIcons(): Pair<Boolean, Boolean> {
        val window = compose.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        return bars.isAppearanceLightStatusBars to bars.isAppearanceLightNavigationBars
    }

    @Test
    fun `a light phone with Sello Oscuro draws light icons`() {
        compose.setContent { SystemBarsFollowTheme(dark = true) }
        compose.waitForIdle()

        assertEquals(false to false, darkIcons())
    }

    @Test
    @Config(qualifiers = "+night")
    fun `a dark phone with Sello Claro draws dark icons`() {
        compose.setContent { SystemBarsFollowTheme(dark = false) }
        compose.waitForIdle()

        assertEquals(true to true, darkIcons())
    }

    @Test
    fun `changing the theme updates the icons at once`() {
        var dark by mutableStateOf(false)
        compose.setContent { SystemBarsFollowTheme(dark) }
        compose.waitForIdle()
        assertEquals(true to true, darkIcons())

        dark = true
        compose.waitForIdle()
        assertEquals(false to false, darkIcons())

        dark = false
        compose.waitForIdle()
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
