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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GoalPlace
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Nueva meta as it is drawn: the plan says its assumption, the action waits, and the
 * largest amount, the longest name and every caption stay whole on a narrow phone and
 * wider windows, at 1.0, 1.3 and 2.0 font scales, in all four themes.
 */
@RunWith(AndroidJUnit4::class)
class GoalEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<GoalEditorAction>()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val planned = GoalEditorState(
        loading = false,
        name = "a".repeat(40),
        nameLength = 40,
        iconKey = "home",
        amount = plain("999.999.999.999"),
        targetMonth = YearMonth.of(2028, 12),
        pace = PlanPace(plain("38.461.538.462"), 26),
        places = listOf(
            YearMonth.of(2027, 5),
            YearMonth.of(2027, 11),
            YearMonth.of(2028, 6),
            YearMonth.of(2028, 12)
        ).zip(GoalPlace.entries).map { (month, place) ->
            PlannedPlace(PlaceModel(place, PlaceLook.Empty, null), PlanCaption.Month(month))
        }
    )

    private data class Look(val ink: SelloInk, val dark: Boolean, val scale: Float, val width: Int)

    @Test
    fun thePlanStaysWholeAndTheActionReachableInEveryThemeSizeAndWindow() {
        var look by mutableStateOf(Look(SelloInk.Cobalto, false, 1f, 320))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(Modifier.requiredSize(look.width.dp, 640.dp).testTag("window")) {
                        GoalEditorScreen(planned) { actions += it }
                    }
                }
            }
        }
        val looks = SelloInk.entries.flatMap { ink ->
            listOf(false, true).flatMap { dark ->
                listOf(1f, 1.3f, 2f).flatMap { scale ->
                    listOf(320, 600).map { width -> Look(ink, dark, scale, width) }
                }
            }
        }
        for (each in looks) {
            look = each
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            val submit = rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertIsEnabled()
                .getUnclippedBoundsInRoot()
            assertTrue("$each: action at $submit", submit.bottom <= window.bottom + 0.5.dp)
            val amount = rule.onNodeWithContentDescription(
                "999.999.999.999 pesos",
                substring = true
            )
                .performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("$each: amount at $amount", amount.right <= window.right + 0.5.dp)
            val monthly = rule.onNodeWithTag("goal.editor.monthly").performScrollTo()
                .getUnclippedBoundsInRoot()
            assertTrue("$each: monthly at $monthly", monthly.right <= window.right + 0.5.dp)
            rule.onNodeWithText("Son 38.461.538.462 al mes durante 26 meses.").assertExists()
            rule.onNodeWithTag("goal.editor.places").performScrollTo()
            rule.onNodeWithText("dic 28", useUnmergedTree = true).assertExists()
            rule.onNodeWithText("40/40", useUnmergedTree = true).assertExists()
        }
    }

    @Test
    fun theActionWaitsAndTheAmountOpensThePad() {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                GoalEditorScreen(planned.copy(iconKey = null)) { actions += it }
            }
        }

        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(GOAL_EDITOR_AMOUNT_TAG).performClick()
        rule.onNodeWithContentDescription("Viaje").performClick()

        assertEquals(
            listOf(GoalEditorAction.OpenPad, GoalEditorAction.IconPicked("flight")),
            actions
        )
    }

    @Test
    fun aNameTooLongKeepsTheTextAndSaysWhy() {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                GoalEditorScreen(
                    planned.copy(
                        name = "a".repeat(41),
                        nameLength = 41,
                        nameError = GoalNameError.TooLong
                    )
                ) { actions += it }
            }
        }

        rule.onNodeWithText("Máximo 40 caracteres. Tu texto sigue aquí.", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("41/40", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aCompletedGoalOffersOnlyDeletion() {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                GoalEditorScreen(
                    planned.copy(editing = true, completed = true, movementCount = 4)
                ) {
                    actions += it
                }
            }
        }

        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(GOAL_EDITOR_AMOUNT_TAG).assertDoesNotExist()
        rule.onNodeWithText("Se borran la meta y sus 4 movimientos. Esto no se puede deshacer.")
            .assertExists()
        rule.onNodeWithText("Borrar meta").performScrollTo().performClick()
        assertEquals(listOf(GoalEditorAction.AskToDelete), actions)
    }
}
