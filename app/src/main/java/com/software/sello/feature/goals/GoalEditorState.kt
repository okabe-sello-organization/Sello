package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.GoalPlace
import java.time.YearMonth

/** Why the name cannot be used yet. Nothing is said before something is typed. */
enum class GoalNameError { TooLong, NotAllowed }

/** Why saving did not finish. */
enum class GoalSaveError {
    /** Certainly not saved. */
    NotSaved,

    /** Not known whether it was saved: asked about again before anything else is sent. */
    Unknown,

    /** The goal or the history changed since the form read it; it is read again. */
    Changed
}

/** How the form ended. Kept in state so a rotation cannot lose it. */
sealed interface GoalEditorResult {
    data class Created(val goalId: String) : GoalEditorResult

    data object Updated : GoalEditorResult

    data object Deleted : GoalEditorResult

    data object Discarded : GoalEditorResult
}

/** Under a stamp while creating or editing: earned already, or the month it could arrive. */
sealed interface PlanCaption {
    data object Earned : PlanCaption

    data class Month(val month: YearMonth) : PlanCaption
}

@Immutable
data class PlannedPlace(val place: PlaceModel, val caption: PlanCaption?)

/** "Son [monthly] al mes durante [months] meses": whole pesos, rounded up. */
@Immutable
data class PlanPace(val monthly: MoneyTextValue, val months: Int)

/**
 * Nueva meta and Editar meta. [name] is exactly what was typed and [amount] what the pad
 * built; an empty amount is not zero. A target month is optional; [pace] is a plan with
 * its assumption, never a promise, and exists only with a month, an amount and something
 * still missing.
 */
@Immutable
data class GoalEditorState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val editing: Boolean = false,
    /** A completed goal can only be deleted. */
    val completed: Boolean = false,
    val name: String = "",
    val nameLength: Int = 0,
    val nameError: GoalNameError? = null,
    val iconKey: String? = null,
    val amount: MoneyTextValue? = null,
    /** Bumped when a key was refused for length, so the amount can shake. */
    val amountPulse: Int = 0,
    val padOpen: Boolean = false,
    val targetMonth: YearMonth? = null,
    val choosingMonth: Boolean = false,
    val pickerYear: Int = 2026,
    /** The first month that can be chosen: the one after today's. */
    val firstMonth: YearMonth = YearMonth.of(2026, 11),
    val pace: PlanPace? = null,
    /** With a month, an amount and nothing left to save for. */
    val nothingMissing: Boolean = false,
    val places: List<PlannedPlace> = GoalPlace.entries.map {
        PlannedPlace(PlaceModel(it, PlaceLook.Empty, null), null)
    },
    val movementCount: Int = 0,
    val busy: Boolean = false,
    val saveError: GoalSaveError? = null,
    val confirmDelete: Boolean = false,
    val confirmDiscard: Boolean = false,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    val result: GoalEditorResult? = null,
    /** ¿Ya la lograste?: Marcar como lograda in progress, or its big moment on screen. */
    val completion: CompletionState = CompletionState.Idle
) {
    /**
     * Crear meta waits for a name, an icon and an amount; Guardar cambios also for a
     * change. Once the form has ended, nothing more is sent.
     */
    val canSubmit: Boolean
        get() = result == null && !loading && !loadFailed && !busy && !completed &&
            nameLength in 1..MAX_NAME &&
            nameError == null && iconKey != null && amount != null &&
            saveError != GoalSaveError.Unknown && (!editing || changed)

    companion object {
        const val MAX_NAME = 40
    }
}

sealed interface GoalEditorAction {
    data class NameChanged(val text: String) : GoalEditorAction

    data class IconPicked(val key: String) : GoalEditorAction

    data object OpenPad : GoalEditorAction

    data object ClosePad : GoalEditorAction

    data class Digit(val digit: Int) : GoalEditorAction

    data object TripleZero : GoalEditorAction

    data object Backspace : GoalEditorAction

    data object ClearAmount : GoalEditorAction

    data object ChooseMonth : GoalEditorAction

    data class ShowYear(val year: Int) : GoalEditorAction

    data class PickMonth(val month: YearMonth) : GoalEditorAction

    data object CloseMonthPicker : GoalEditorAction

    data object NoDate : GoalEditorAction

    data object Submit : GoalEditorAction

    data object Retry : GoalEditorAction

    data object Back : GoalEditorAction

    data object DiscardConfirmed : GoalEditorAction

    data object KeepEditing : GoalEditorAction

    data object AskToDelete : GoalEditorAction

    data object CancelDelete : GoalEditorAction

    data object Delete : GoalEditorAction

    data object MarkComplete : GoalEditorAction

    /** The big moment is closed: the form ends and the goal shows it is lograda. */
    data object CelebrationDone : GoalEditorAction
}
