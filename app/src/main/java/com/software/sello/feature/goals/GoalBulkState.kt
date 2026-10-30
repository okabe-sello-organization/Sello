package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.BULK_RECEIPT_MAX_LINES
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.movement.MovementNoteProblem
import java.time.LocalDate

/**
 * One line of Aportar varios. [withdrawal] is null until Aportar or Retirar is chosen;
 * [amount] is present only for a valid amount. [available] is what the goal had for this
 * retiro when storage refused it; [futureDate] marks a date storage found after today.
 */
@Immutable
data class GoalBulkLine(
    val key: String,
    val withdrawal: Boolean?,
    val amount: MoneyTextValue?,
    val date: LocalDate,
    val note: String = "",
    val available: MoneyTextValue? = null,
    val futureDate: Boolean = false
) {
    /** It has an amount but no type: it blocks saving. */
    val missingType: Boolean get() = amount != null && withdrawal == null

    val refused: Boolean get() = available != null || futureDate
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

    /** The goal no longer exists. */
    Gone,

    /** The goal changed since it was read; it is read again and can be sent again. */
    Changed,

    /** A completed goal is read-only. */
    Completed
}

/**
 * Aportar varios: several aportes and retiros of one goal on one receipt, each with its
 * date, saved whole or not at all. [highlighted] is the line the pad types into, always one
 * of [lines], which holds 1 to [BULK_RECEIPT_MAX_LINES]. [total] is the net (aportes −
 * retiros) of the lines with an amount and a type.
 */
@Immutable
data class GoalBulkState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val goalName: String? = null,
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

    val refused: Int get() = lines.count { it.refused }

    val full: Boolean get() = lines.size >= BULK_RECEIPT_MAX_LINES

    /** Lines with something typed, which leaving would lose. */
    val typed: Int get() = lines.count { it.amount != null || it.note.isNotBlank() }

    /** While saving, or once nothing can be sent as it is, the draft holds still. */
    val editable: Boolean
        get() = !loading && !loadFailed && !busy && !closed && error !in Blocking

    val canAdd: Boolean get() = editable && !full && lines.all { it.amount != null }

    val canSubmit: Boolean
        get() = editable && counted > 0 && missing == 0 && refused == 0

    private companion object {
        val Blocking = setOf(
            GoalBulkError.Unknown,
            GoalBulkError.DataChanged,
            GoalBulkError.Gone,
            GoalBulkError.Completed
        )
    }
}

sealed interface GoalBulkAction {
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

    /** Asks again about an unknown outcome, or reads the goal again after a failed read. */
    data object Retry : GoalBulkAction

    data object Back : GoalBulkAction

    data object DiscardConfirmed : GoalBulkAction

    data object KeepEditing : GoalBulkAction
}
