package com.software.sello.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The ribbon over a past month, drawn by the frame the way the app draws it: on a tab,
 * on a pushed screen and on a category's own screen, but not on Configuración, and
 * never in the current month. On the JVM (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
// Real text measurement: the layout below depends on how wide the words are.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PastMonthRibbonShellTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ShellAction>()
    private val october = YearMonth.of(2026, 10)

    private fun viewing(selected: YearMonth, current: YearMonth = october, detail: String? = null) =
        ShellState(
            currentMonth = current,
            selectedMonth = selected,
            monthPickerOpen = false,
            pickerYear = selected.year,
            pendingEntry = null,
            detailCategoryId = detail
        )

    private var state by mutableStateOf(viewing(YearMonth.of(2026, 9)))

    private enum class Screen { Tab, Pushed, Settings, Detail }

    @Composable
    private fun Frame(screen: Screen) {
        val content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit =
            { padding -> Text("contenido", Modifier.padding(padding).testTag("content")) }
        when (screen) {
            Screen.Tab -> ShellScreen(
                state,
                monthNames(),
                { actions += it },
                navigation = SelloNavigation(
                    listOf(
                        SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                        SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                    ),
                    "recibo"
                ) {},
                content = content
            )

            Screen.Pushed -> ShellScreen(
                state,
                monthNames(),
                { actions += it },
                onBack = {},
                screenTitle = "Ingresos",
                content = content
            )

            Screen.Settings -> ShellScreen(
                state,
                monthNames(),
                { actions += it },
                onBack = {},
                screenTitle = "Configuración",
                pastMonthRibbon = false,
                content = content
            )

            Screen.Detail -> ShellScreen(
                state,
                monthNames(),
                { actions += it },
                detail = ShellDetail("Alimentación", content = content),
                content = content
            )
        }
    }

    private fun show(screen: Screen) {
        rule.showSello(Look()) { Frame(screen) }
    }

    private val ribbon get() = rule.onNodeWithTag(PAST_MONTH_RIBBON_TAG)

    @Test
    fun aPastMonthOnATabSaysWhichMonthItIsAndOneTapGoesBack() {
        show(Screen.Tab)

        ribbon.assertIsDisplayed()
        rule.onNodeWithText("Estás viendo septiembre").assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
        rule.onNodeWithText("Volver a octubre").assertHasClickAction().performClick()

        assertEquals(listOf<ShellAction>(ShellAction.ReturnToCurrentMonth), actions)
    }

    @Test
    fun theRibbonSitsUnderTheMonthBarAndAboveTheContent() {
        show(Screen.Tab)

        val bar = rule.onNodeWithTag(MONTH_SWITCHER_TAG).getUnclippedBoundsInRoot()
        val shown = ribbon.getUnclippedBoundsInRoot()
        val content = rule.onNodeWithTag("content").getUnclippedBoundsInRoot()
        assertTrue("$shown under $bar", shown.top >= bar.bottom)
        assertTrue("$content under $shown", content.top >= shown.bottom)
    }

    @Test
    fun theCurrentMonthHasNoRibbonAndItGoesWhenTheMonthIsCurrentAgain() {
        show(Screen.Tab)
        ribbon.assertExists()

        state = viewing(october)
        rule.waitForIdle()

        ribbon.assertDoesNotExist()
        rule.onNodeWithText("Volver a octubre").assertDoesNotExist()
    }

    @Test
    fun whenTheMonthTurnsTheReturnNamesTheNewCurrentMonth() {
        show(Screen.Tab)

        state = viewing(YearMonth.of(2026, 9), current = YearMonth.of(2026, 11))
        rule.waitForIdle()

        rule.onNodeWithText("Volver a noviembre").assertIsDisplayed()
    }

    @Test
    fun aMonthOfAnotherYearSaysItsYear() {
        state = viewing(YearMonth.of(2025, 9))
        show(Screen.Tab)

        rule.onNodeWithText("Estás viendo septiembre de 2025").assertIsDisplayed()
        rule.onNodeWithText("Volver a octubre").assertIsDisplayed()
    }

    @Test
    fun aPushedScreenThatShowsTheMonthHasTheRibbonUnderItsTitle() {
        show(Screen.Pushed)

        rule.onNodeWithText("Estás viendo septiembre").assertIsDisplayed()
        rule.onNodeWithText("Volver a octubre").performClick()

        assertEquals(listOf<ShellAction>(ShellAction.ReturnToCurrentMonth), actions)
    }

    @Test
    fun aCategoryOpenedOnItsOwnScreenHasTheRibbonToo() {
        state = viewing(YearMonth.of(2026, 9), detail = "00000000-0000-4000-8000-000000000001")
        show(Screen.Detail)

        rule.onNodeWithText("Alimentación").assertExists()
        ribbon.assertIsDisplayed()
    }

    @Test
    fun configuracionShowsNothingOfTheMonthAndHasNoRibbon() {
        show(Screen.Settings)

        rule.onNodeWithTag("content").assertExists()
        ribbon.assertDoesNotExist()
    }
}
