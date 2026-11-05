package com.software.sello.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.platform.SystemBarsFollowTheme
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The month picker opens over the band, which its scrim only dims: the status icons stay
 * white, and the navigation icons follow the theme of the sheet's paper (SELLO-171).
 */
@RunWith(AndroidJUnit4::class)
class MonthPickerBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(
        ShellState(
            currentMonth = YearMonth.of(2026, 10),
            selectedMonth = YearMonth.of(2026, 10),
            monthPickerOpen = false,
            pickerYear = 2026,
            pendingEntry = null
        )
    )

    /** Recibo's band first, then the picker opened over it, as a tap on the month does. */
    private fun show(dark: Boolean) {
        compose.setContent {
            SystemBarsFollowTheme(dark) {
                SelloTheme(darkTheme = dark, reducedMotion = true) {
                    ShellScreen(state, monthNames(), {}) { Box {} }
                }
            }
        }
        compose.waitForIdle()
        state = state.copy(monthPickerOpen = true)
    }

    /** Whether the sheet's window asks for dark status and navigation icons. */
    private fun sheetDarkIcons(): Pair<Boolean, Boolean> {
        compose.waitForIdle()
        val window = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
            .mapNotNull {
                ((it.root as ViewRootForTest).view.parent as? DialogWindowProvider)?.window
            }
            .single()
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        return bars.isAppearanceLightStatusBars to bars.isAppearanceLightNavigationBars
    }

    @Test
    fun inClaroTheStatusIconsStayWhiteOverTheBand() {
        show(dark = false)

        assertEquals(false to true, sheetDarkIcons())
    }

    @Test
    fun inOscuroBothStayLight() {
        show(dark = true)

        assertEquals(false to false, sheetDarkIcons())
    }
}
