package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.GoalPlace
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Nueva meta looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** A plan with its four places: the form with everything filled, ready to save. */
    private val planned = GoalEditorState(
        loading = false,
        name = "Viaje a la costa",
        nameLength = 16,
        iconKey = "flight",
        amount = plain("6.000.000"),
        targetMonth = YearMonth.of(2027, 12),
        pace = PlanPace(plain("461.538"), 13),
        places = listOf(
            YearMonth.of(2027, 3),
            YearMonth.of(2027, 6),
            YearMonth.of(2027, 9),
            YearMonth.of(2027, 12)
        ).zip(GoalPlace.entries).map { (month, place) ->
            PlannedPlace(PlaceModel(place, PlaceLook.Empty, null), PlanCaption.Month(month))
        }
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            GoalEditorScreen(planned) {}
        }
        rule.captureStill("src/test/screenshots/goal-editor-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** Editar meta with a save whose outcome is unknown: Comprobar only, no ⋮ to delete. */
    private fun unresolvedSave(look: Look) {
        rule.showSello(look) {
            GoalEditorScreen(
                planned.copy(
                    editing = true,
                    changed = true,
                    saveError = GoalSaveError.Unknown,
                    unresolved = true
                )
            ) {}
        }
        rule.captureStill("src/test/screenshots/goal-editor-unresolved-${look.label}.png")
    }

    @Test
    fun unresolvedSaveLight() = unresolvedSave(Look())

    @Test
    fun unresolvedSaveDark() = unresolvedSave(Look(dark = true))

    private fun shot(name: String, state: GoalEditorState, look: Look, tap: String? = null) {
        rule.showSello(look) { GoalEditorScreen(state) {} }
        if (tap != null) rule.onNodeWithTag(tap).revealed().performClick()
        rule.captureStill("src/test/screenshots/goal-editor-$name-${look.label}.png")
    }

    /** Editar meta as it opens: nothing changed, ¿Ya la lograste? at the foot. */
    private val editing = planned.copy(editing = true, movementCount = 3)

    @Test fun editingLight() = shot("editing", editing, Look())

    @Test fun editingDark() = shot("editing", editing, Look(dark = true))

    @Test fun iconSheetLight() = shot("icon-sheet", planned, Look(), GOAL_EDITOR_ICON_TAG)

    @Test
    fun iconSheetDark() = shot("icon-sheet", planned, Look(dark = true), GOAL_EDITOR_ICON_TAG)

    private val monthSheet = planned.copy(choosingMonth = true, pickerYear = 2027)

    @Test fun monthSheetLight() = shot("month-sheet", monthSheet, Look())

    @Test fun monthSheetDark() = shot("month-sheet", monthSheet, Look(dark = true))

    /** A completed goal: only its name and icon change. */
    private val completed = editing.copy(completed = true)

    @Test fun completedLight() = shot("completed", completed, Look())

    @Test fun completedDark() = shot("completed", completed, Look(dark = true))

    private val loading = GoalEditorState()

    @Test fun loadingLight() = shot("loading", loading, Look())

    @Test fun loadingDark() = shot("loading", loading, Look(dark = true))

    private val failed = GoalEditorState(loading = false, loadFailed = true, editing = true)

    @Test fun failedLight() = shot("failed", failed, Look())

    @Test fun failedDark() = shot("failed", failed, Look(dark = true))

    private val refused = planned.copy(saveError = GoalSaveError.NotSaved)

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    private val discard = planned.copy(changed = true, confirmDiscard = true)

    @Test fun discardLight() = shot("discard", discard, Look())

    @Test fun discardDark() = shot("discard", discard, Look(dark = true))

    private val delete = editing.copy(confirmDelete = true)

    @Test fun deleteLight() = shot("delete", delete, Look())

    @Test fun deleteDark() = shot("delete", delete, Look(dark = true))

    /** Nueva meta as it opens: nothing chosen, the hints in order. */
    private val empty = GoalEditorState(loading = false)

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    /** A pasted name past 40 characters, kept with why it cannot be saved. */
    private val tooLong = planned.copy(
        name = "Viaje a la costa con toda la familia en diciembre",
        nameLength = 49,
        nameError = GoalNameError.TooLong
    )

    @Test fun tooLongLight() = shot("too-long", tooLong, Look())

    @Test fun tooLongDark() = shot("too-long", tooLong, Look(dark = true))

    @Test fun busyLight() = shot("busy", planned.copy(busy = true), Look())

    @Test fun busyDark() = shot("busy", planned.copy(busy = true), Look(dark = true))

    /** ¿Ya la lograste? could not be saved: said inside that slip. */
    private val completionFailed =
        editing.copy(completion = CompletionState.Failed(GoalDetailError.NotSaved))

    @Test fun completionFailedLight() = shot("completion-failed", completionFailed, Look())

    @Test
    fun completionFailedDark() = shot("completion-failed", completionFailed, Look(dark = true))
}
