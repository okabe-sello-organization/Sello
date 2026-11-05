package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.BULK_RECEIPT_MAX_LINES
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.movement.MovementNoteProblem
import java.time.LocalDate

/**
 * One line of Aportar varios. [goalId] is the goal it names, and [goalName] that goal's
 * name while it is active: null until a goal is chosen, and once the goal it names was
 * completed or deleted, so it must be chosen again. [withdrawal] is null until Aportar or
 * Retirar is chosen; [amount] is present only for a valid amount. [available] is what its
 * goal had for this retiro when storage refused it; [futureDate] marks a date storage found
 * after today, and [invalidDate] one it cannot keep.
 */
@Immutable
data class GoalBulkLine(
    val key: String,
    val goalId: String?,
    val goalName: String?,
    val withdrawal: Boolean?,
    val amount: MoneyTextValue?,
    val date: LocalDate,
    val note: String = "",
    val available: MoneyTextValue? = null,
    val futureDate: Boolean = false,
    val invalidDate: Boolean = false
) {
    /** It has an amount but no type: it blocks saving. */
    val missingType: Boolean get() = amount != null && withdrawal == null

    /** It has an amount but no active goal: it blocks saving. */
    val missingGoal: Boolean get() = amount != null && goalName == null

    val refused: Boolean get() = available != null || futureDate || invalidDate
}

/** The line just removed from the draft, while its Deshacer is offered. */
@Immutable
data class RemovedGoalLine(val withdrawal: Boolean?)

/** Why the receipt was not saved. None of these means it was. */
enum class GoalBulkError {
    /** Nothing was written; the draft stays and can be sent again. */
    NotSaved,

    /** A line was refused, and is marked; nothing was written. */
    Refused,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /**
     * A goal changed, was completed or deleted since it was read; the goals are read again,
     * a line whose goal is no longer active asks for one, and the receipt can be sent again.
     */
    Changed
}

/** An active goal a line can name, as its chip shows it. */
@Immutable
data class GoalBulkChoice(val id: String, val name: String, val iconKey: String)

/**
 * Aportar varios: several aportes and retiros of one goal or several on one receipt, each
 * line with its goal and date, saved whole or not at all. [goals] are the active goals a
 * line can name. [goalName] is the one goal the lines name, and null once they name none or
 * more than one. [highlighted] is the line the pad types into, always one of [lines], which
 * holds 1 to [BULK_RECEIPT_MAX_LINES]. [total] is the net (aportes − retiros) of the lines
 * with an amount, an active goal and a type.
 */
@Immutable
data class GoalBulkState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val goalName: String? = null,
    val goals: List<GoalBulkChoice> = emptyList(),
    val lines: List<GoalBulkLine>,
    val highlighted: Int = 0,
    val total: MoneyTextValue?,
    val today: LocalDate,
    val atAmountLimit: Boolean = false,
    val limitPulse: Int = 0,
    val datePickerOpen: Boolean = false,
    val composerOpen: Boolean = false,
    val composerDraft: String = "",
    val composerProblem: MovementNoteProblem? = null,
    val composerLength: Int = 0,
    val removed: RemovedGoalLine? = null,
    val busy: Boolean = false,
    val error: GoalBulkError? = null,
    val confirmDiscard: Boolean = false,
    /** The receipt is finished and the screen should close. */
    val closed: Boolean = false
) {
    val line: GoalBulkLine get() = lines[highlighted]

    /** The lines that will be saved: those with an amount. */
    val counted: Int get() = lines.count { it.amount != null }

    val missing: Int get() = lines.count { it.missingType }

    val missingGoals: Int get() = lines.count { it.missingGoal }

    val refused: Int get() = lines.count { it.refused }

    /** Read, and no goal is left to name: the person can only leave. */
    val noActiveGoal: Boolean get() = !loading && !loadFailed && goals.isEmpty()

    val full: Boolean get() = lines.size >= BULK_RECEIPT_MAX_LINES

    /** Lines with something typed, which leaving would lose. */
    val typed: Int get() = lines.count { it.amount != null || it.note.isNotBlank() }

    /** While saving, or once nothing can be sent as it is, the draft holds still. */
    val editable: Boolean
        get() = !loading && !loadFailed && !busy && !closed && error !in Blocking

    val canAdd: Boolean get() = editable && !full && lines.all { it.amount != null }

    val canSubmit: Boolean
        get() = editable && counted > 0 && missing == 0 && missingGoals == 0 && refused == 0

    private companion object {
        val Blocking = setOf(GoalBulkError.Unknown, GoalBulkError.DataChanged)
    }
}

sealed interface GoalBulkAction {
    /** The highlighted line names the goal [id]. */
    data class GoalPicked(val id: String) : GoalBulkAction

    data class Kind(val withdrawal: Boolean) : GoalBulkAction

    data class Digit(val digit: Int) : GoalBulkAction

    data object TripleZero : GoalBulkAction

    data object Backspace : GoalBulkAction

    data object ClearAmount : GoalBulkAction

    data class Select(val index: Int) : GoalBulkAction

    data object AddLine : GoalBulkAction

    data class Remove(val index: Int) : GoalBulkAction

    data object UndoRemove : GoalBulkAction

    data object OpenDatePicker : GoalBulkAction

    data object CloseDatePicker : GoalBulkAction

    data class DatePicked(val date: LocalDate) : GoalBulkAction

    data object OpenComposer : GoalBulkAction

    data class ComposerChanged(val text: String) : GoalBulkAction

    data object ComposerDone : GoalBulkAction

    data object ComposerCancelled : GoalBulkAction

    data object Submit : GoalBulkAction

    /** Asks again about an unknown outcome, or reads the goals again after a failed read. */
    data object Retry : GoalBulkAction

    data object Back : GoalBulkAction

    data object DiscardConfirmed : GoalBulkAction

    data object KeepEditing : GoalBulkAction
}
