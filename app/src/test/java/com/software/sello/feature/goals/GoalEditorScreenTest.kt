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
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.SweepLook
import com.software.sello.SweepWindow
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GoalPlace
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.sweepLooks
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Nueva meta as it is drawn: the plan says its assumption, the action waits, and the
 * largest amount, the longest name and every caption stay whole on a narrow phone and
 * wider windows, in all four themes.
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

    @Test
    // A screen larger than every window below, so each window is laid out whole.
    @Config(qualifiers = "w900dp-h900dp-mdpi")
    fun thePlanStaysWholeAndTheActionReachableInEveryThemeAndWindow() {
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, SweepWindow(320, 640)))
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        GoalEditorScreen(planned) { actions += it }
                    }
                }
            }
        }
        for (each in sweepLooks(listOf(SweepWindow(320, 640), SweepWindow(600, 640)))) {
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
                .revealed().getUnclippedBoundsInRoot()
            assertTrue("$each: amount at $amount", amount.right <= window.right + 0.5.dp)
            val monthly = rule.onNodeWithTag("goal.editor.monthly").revealed()
                .getUnclippedBoundsInRoot()
            assertTrue("$each: monthly at $monthly", monthly.right <= window.right + 0.5.dp)
            rule.onNodeWithText("Son 38.461.538.462 al mes durante 26 meses.").assertExists()
            rule.onNodeWithTag("goal.editor.places").revealed()
            rule.onNodeWithText("dic 28", useUnmergedTree = true).assertExists()
            rule.onNodeWithText("40/40", useUnmergedTree = true).assertExists()
        }
    }

    private fun show(state: GoalEditorState) = rule.showSello {
        GoalEditorScreen(state) {
            actions +=
                it
        }
    }

    @Test
    fun theActionSaysWhatIsMissingAndThePadIsAlwaysThere() {
        show(planned.copy(iconKey = null))

        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(GOAL_EDITOR_MISSING_TAG).assertIsDisplayed()
        rule.onNodeWithText("Elige un ícono.").assertIsDisplayed()
        rule.onNode(hasText("7") and hasAnyAncestor(hasTestTag(GOAL_EDITOR_PAD_TAG)))
            .performClick()

        assertEquals(listOf<GoalEditorAction>(GoalEditorAction.Digit(7)), actions)
    }

    @Test
    fun theEmptyCircleInvitesAChoiceAndOpensTheSameThirtyFiveIconsAsACategory() {
        show(planned.copy(iconKey = null))

        rule.onNodeWithTag(GOAL_EDITOR_ICON_TAG).assertContentDescriptionEquals("Elegir ícono")
            .performClick()
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag(GOAL_EDITOR_ICONS_TAG)) and isSelectable()
        ).assertCountEquals(35)
        rule.onNodeWithContentDescription("Ahorro").assertExists()
        rule.onNodeWithContentDescription("Viaje").performClick()

        rule.onNodeWithTag(GOAL_EDITOR_ICONS_TAG).assertDoesNotExist()
        assertEquals(listOf<GoalEditorAction>(GoalEditorAction.IconPicked("flight")), actions)
    }

    @Test
    fun theMonthIsInTheAmountsCornerAndItsSheetOffersSinFechaBesideItsTitle() {
        show(planned.copy(targetMonth = null, pace = null))

        rule.onNodeWithTag("goal.editor.month")
            .assertContentDescriptionEquals("Para cuándo: Sin fecha. Cambiar").performClick()
        assertEquals(listOf<GoalEditorAction>(GoalEditorAction.ChooseMonth), actions)
    }

    @Test
    fun sinFechaSitsOnTheMonthSheetsTitleLineAndReportsItself() {
        show(planned.copy(choosingMonth = true, pickerYear = 2028))

        val title = rule.onNodeWithText("Para cuándo").getUnclippedBoundsInRoot()
        val noDate = rule.onNodeWithTag("goal.editor.noDate").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        // One line: the button beside the title, to its right.
        assertTrue("title $title, button $noDate", noDate.top < title.bottom)
        assertTrue("title $title, button $noDate", noDate.bottom > title.top)
        assertTrue("title $title, button $noDate", noDate.left >= title.right)
        rule.onNodeWithTag("goal.editor.noDate").performClick()

        assertEquals(listOf<GoalEditorAction>(GoalEditorAction.NoDate), actions)
    }

    @Test
    fun theStampsAreCentredWithEqualRoomOnEitherSide() {
        show(planned)

        val row = rule.onNodeWithTag("goal.editor.places").revealed().getUnclippedBoundsInRoot()
        val first = rule.onNodeWithText("25 %", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val last = rule.onNodeWithText("Meta", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val left = first.left - row.left
        val right = row.right - last.right
        assertTrue("left $left, right $right", (left - right).value in -2f..2f)
        // Not huddled at the start: the four share the width, with room before the first.
        assertTrue("left $left of the row", left > 4.dp)
    }

    @Test
    fun aNewGoalOpensOnTheNameAndAGoalBeingChangedDoesNot() {
        show(GoalEditorState(loading = false))
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Nombre").assertIsFocused()
    }

    @Test
    fun whileSavingTheNameCannotBeEdited() {
        show(planned.copy(busy = true))

        rule.onNodeWithContentDescription("Nombre").assertIsNotEnabled()
    }

    @Test
    fun aGoalBeingChangedDoesNotTakeFocus() {
        show(planned.copy(editing = true))
        rule.waitForIdle()
        rule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    @Test
    fun aNameTooLongKeepsTheTextAndSaysWhy() {
        rule.showSello {
            GoalEditorScreen(
                planned.copy(
                    name = "a".repeat(41),
                    nameLength = 41,
                    nameError = GoalNameError.TooLong
                )
            ) { actions += it }
        }

        rule.onNodeWithText("Máximo 40 caracteres. Tu texto sigue aquí.", useUnmergedTree = true)
            .assertExists()
        rule.onNodeWithText("41/40", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aCompletedGoalOffersOnlyDeletion() {
        rule.showSello {
            GoalEditorScreen(
                planned.copy(editing = true, completed = true, movementCount = 4)
            ) {
                actions += it
            }
        }

        rule.onNodeWithTag(GOAL_EDITOR_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(GOAL_EDITOR_AMOUNT_TAG).assertDoesNotExist()
        rule.onNodeWithText("Se borran la meta y sus 4 movimientos. Esto no se puede deshacer.")
            .assertExists()
        rule.onNodeWithText("Borrar meta").revealed().performClick()
        assertEquals(listOf(GoalEditorAction.AskToDelete), actions)
    }
}
