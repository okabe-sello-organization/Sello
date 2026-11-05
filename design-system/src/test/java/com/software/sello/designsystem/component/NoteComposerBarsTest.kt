package com.software.sello.designsystem.component

import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.LocalStatusBarInks
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.StatusBarInk
import com.software.sello.designsystem.theme.StatusBarInks
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The note composer's window covers the status bar but leaves the screen under it in
 * view: its status icons read on that screen, its navigation icons on the theme (SELLO-171).
 */
@RunWith(AndroidJUnit4::class)
class NoteComposerBarsTest {
    @get:Rule
    val rule = createComposeRule()

    /** A note composer over a screen that says [underStatusBar] reads under the status bar. */
    private fun show(dark: Boolean, underStatusBar: @Composable () -> Color) {
        rule.setContent {
            CompositionLocalProvider(LocalStatusBarInks provides StatusBarInks()) {
                SelloTheme(darkTheme = dark, reducedMotion = true) {
                    StatusBarInk(underStatusBar())
                    NoteComposer(
                        AnnotatedString("Gasto en Comida"), "Taxi", {}, "4/60",
                        "Opcional", null, "Listo", "Cancelar", {}, {}
                    )
                }
            }
        }
    }

    /** Whether the composer's window asks for dark status and navigation icons. */
    private fun darkIcons(): Pair<Boolean, Boolean> {
        rule.waitForIdle()
        val window = rule.onAllNodes(isRoot()).fetchSemanticsNodes()
            .mapNotNull {
                ((it.root as ViewRootForTest).view.parent as? DialogWindowProvider)?.window
            }
            .single()
        val appearance = window.insetsController!!.systemBarsAppearance
        return (appearance and APPEARANCE_LIGHT_STATUS_BARS != 0) to
            (appearance and APPEARANCE_LIGHT_NAVIGATION_BARS != 0)
    }

    @Test
    fun overTheBandInClaroTheStatusIconsStayWhite() {
        show(dark = false) { SelloTheme.colors.onBand }

        assertEquals(false to true, darkIcons())
    }

    @Test
    fun overTheBandInOscuroBothStayLight() {
        show(dark = true) { SelloTheme.colors.onBand }

        assertEquals(false to false, darkIcons())
    }

    @Test
    fun overTheDeskInClaroBothAreDark() {
        show(dark = false) { SelloTheme.colors.ink }

        assertEquals(true to true, darkIcons())
    }
}
