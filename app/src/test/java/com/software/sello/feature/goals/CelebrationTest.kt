package com.software.sello.feature.goals

import android.os.Build
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.height
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The big moment follows the reference's timeline and ends on its final screen. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationTest {
    @get:Rule
    val rule = createComposeRule()

    private val places = listOf("25 %", "50 %", "75 %", "Meta").map {
        MilestoneSpec(it, "$it, logrado", MilestoneState.Earned)
    }

    private fun show(reduced: Boolean, dark: Boolean = false) {
        rule.setContent {
            SelloTheme(darkTheme = dark, reducedMotion = reduced) {
                CelebrationOverlay(
                    "¡Lo lograste!", "Meta lograda", "Vacunas de Luna", SelloIcon.Pets,
                    MoneyTextValue(MoneySign.None, "", "400.000", "400.000 pesos"),
                    "30 sep 2026 · 4 aportes", places, "Listo", "Ver mi meta", {}, {}
                )
            }
        }
    }

    @Test
    fun stampsTitlePlateAndActionsArriveAtTheReferenceTimes() {
        rule.mainClock.autoAdvance = false
        show(reduced = false)
        // Stamps land at 0.3, 0.65, 1.0 and 1.4 s; the title rises at 2.4 s.
        rule.mainClock.advanceTimeBy(1_200)
        rule.onNodeWithContentDescription("75 %, logrado").assertExists()
        rule.onNodeWithContentDescription("Meta, logrado").assertDoesNotExist()
        rule.onNodeWithText("¡Lo lograste!").assertDoesNotExist()
        // The plate is stamped at 2.75 s; the actions fade in at 3.1 s.
        rule.mainClock.advanceTimeBy(1_400)
        rule.onNodeWithContentDescription("Meta, logrado").assertExists()
        rule.onNodeWithText("¡Lo lograste!").assertExists()
        rule.onNodeWithText("Listo").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(1_000)
        rule.onNodeWithContentDescription("400.000 pesos").assertExists()
        rule.onNodeWithText("Listo").assertExists()
    }

    @Test
    fun reducedMotionOpensOnTheFinalScreenWithTheActionsAtTheBottom() {
        show(reduced = true)
        places.forEach { rule.onNodeWithContentDescription(it.description).assertExists() }
        rule.onNodeWithText("¡Lo lograste!").assertExists()
        val screen = rule.onNode(isRoot() and hasAnyDescendant(hasText("Listo")))
            .getUnclippedBoundsInRoot()
        val done = rule.onNodeWithText("Listo").getUnclippedBoundsInRoot()
        val view = rule.onNodeWithText("Ver mi meta").getUnclippedBoundsInRoot()
        // Listo and Ver mi meta are pinned to the bottom of the screen, below the plate.
        assertTrue(view.top > done.bottom)
        assertTrue(screen.bottom - view.bottom < screen.height / 8)
    }

    /** Whether the celebration's window asks for dark status-bar icons (for a light bar). */
    private fun darkStatusIcons(): Boolean {
        val root = rule.onNode(
            isRoot() and hasAnyDescendant(hasContentDescription("25 %, logrado"))
        )
            .fetchSemanticsNode().root as ViewRootForTest
        val window = (root.view.parent as DialogWindowProvider).window
        // Android 11 applies the older light-bar flag; its appearance getter stays 0.
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR != 0
        } else {
            window.insetsController!!.systemBarsAppearance and APPEARANCE_LIGHT_STATUS_BARS != 0
        }
    }

    @Test
    fun statusBarIconsTurnWhiteAsTheGreenFloodsIn() {
        rule.mainClock.autoAdvance = false
        show(reduced = false)
        // On the pale desk the status bar keeps dark icons; the ink turns white from 1.9 s.
        rule.mainClock.advanceTimeBy(1_000)
        assertTrue(darkStatusIcons())
        rule.mainClock.advanceTimeBy(1_600)
        assertFalse(darkStatusIcons())
    }

    @Test
    fun aDarkDeskKeepsWhiteStatusIconsThroughout() {
        rule.mainClock.autoAdvance = false
        show(reduced = false, dark = true)
        rule.mainClock.advanceTimeBy(1_000)
        assertFalse(darkStatusIcons())
    }
}
