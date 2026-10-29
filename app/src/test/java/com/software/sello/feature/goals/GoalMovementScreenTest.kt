package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Aportar and Retirar as they are drawn: the chips carry their percentage, a retiro above
 * what is saved says so in the label's place and waits, a correction a later retiro needs
 * says which one, and the action stays whole on a narrow phone and wider windows at 1.0,
 * 1.3 and 2.0 font scales in all four themes.
 */
@RunWith(AndroidJUnit4::class)
class GoalMovementScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<GoalMovementAction>()
    private val today = LocalDate.of(2026, 10, 23)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val goals = listOf(
        GoalChoice("a", "Viaje a la costa", "flight", 57, plain("1.710.000")),
        GoalChoice("b", "Celular nuevo", "phone_iphone", 37, plain("450.000"))
    )

    private val aporte = GoalMovementState(
        loading = false,
        amount = plain("999.999.999.999"),
        goals = goals,
        goalId = "a",
        date = today,
        today = today
    )

    private fun show(state: GoalMovementState) {
        rule.showSello { GoalMovementScreen(state) { actions += it } }
    }

    @Test
    fun theChipsCarryTheirPercentageAndTheChosenGoalSaysWhatItHolds() {
        show(aporte.copy(amount = plain("350.000")))

        rule.onNodeWithText("57 %", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Llevas 1.710.000 guardados en esta meta.").assertExists()
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).assertIsEnabled()
        rule.onNodeWithText("Celular nuevo", useUnmergedTree = true).performClick()
        assertEquals(listOf<GoalMovementAction>(GoalMovementAction.GoalPicked("b")), actions)
    }

    @Test
    fun mt08ARetiroAboveWhatIsSavedSaysSoInTheLabelsPlaceAndWaits() {
        show(
            aporte.copy(
                withdrawal = true,
                amount = plain("1.710.001"),
                overSaved = plain("1.710.000")
            )
        )

        rule.onNodeWithText("Más de lo guardado (1.710.000)", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Retiro en pesos", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithText("Registrar retiro", useUnmergedTree = true).assertExists()
    }

    @Test
    fun mt15ACorrectionALaterRetiroNeedsSaysWhichRetiro() {
        show(
            aporte.copy(
                editing = true,
                changed = true,
                amount = plain("299.999"),
                blockedBy = BlockingWithdrawal(plain("300.000"), LocalDate.of(2026, 10, 20))
            )
        )

        rule.onNodeWithText(
            "No se puede: el retiro de 300.000 del 20 oct usa este aporte. " +
                "Borra o corrige ese retiro primero.",
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(GOAL_MOVEMENT_KIND_TAG).assertDoesNotExist()
    }

    @Test
    // A screen larger than every window below, so each window is laid out whole.
    @Config(qualifiers = "w900dp-h900dp-mdpi")
    fun theActionStaysWholeInEveryThemeSizeAndWindow() {
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, 1f, SweepWindow(320, 640)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        GoalMovementScreen(aporte) { actions += it }
                    }
                }
            }
        }
        for (each in sweepLooks(listOf(SweepWindow(320, 640), SweepWindow(600, 640)))) {
            look = each
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            val submit = rule.onNodeWithTag(GOAL_MOVEMENT_SUBMIT_TAG).assertIsEnabled()
                .getUnclippedBoundsInRoot()
            assertTrue("$each: action at $submit", submit.bottom <= window.bottom + 0.5.dp)
            assertTrue("$each: action at $submit", submit.right <= window.right + 0.5.dp)
            val amount = rule.onNodeWithTag(GOAL_MOVEMENT_AMOUNT_TAG).getUnclippedBoundsInRoot()
            assertTrue("$each: amount at $amount", amount.right <= window.right + 0.5.dp)
        }
    }
}
