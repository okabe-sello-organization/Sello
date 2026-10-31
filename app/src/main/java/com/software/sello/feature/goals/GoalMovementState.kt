package com.software.sello.feature.goals

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.movement.MovementNoteProblem
import java.time.LocalDate

/** A goal offered to contribute to or withdraw from, in Metas' order, with its percentage. */
@Immutable
data class GoalChoice(
    val id: String,
    val name: String,
    val iconKey: String,
    val percentage: Long,
    val saved: MoneyTextValue
)

/**
 * What is typed so far, handed to Aportar varios for [goalId] to become its line one: Aportar
 * or Retirar, the amount's digits, the note and the date. The amount and note may be missing.
 */
@Immutable
data class GoalSeveral(
    val goalId: String,
    val withdrawal: Boolean,
    val amount: String?,
    val note: String?,
    val date: LocalDate
)

/** The later retiro that a correction would leave short (MT15). */
@Immutable
data class BlockingWithdrawal(val amount: MoneyTextValue, val date: LocalDate)

/** Why the movement was not saved. None of these means it was. */
enum class GoalMovementError {
    NotSaved,
    FutureDate,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The goal or the movement no longer exists. */
    Gone,

    /** The goal changed since it was read; it is read again and can be sent again. */
    Changed,

    /** A completed goal is read-only. */
    Completed,
    NotDeleted
}

/**
 * Aportar or Retirar. [amount] is present only for a valid amount; [amountText] is what
 * was pasted when it could not be used. [overSaved] is what the chosen goal can give when
 * a retiro asks for more; [blockedBy] the later retiro a correction would leave short.
 */
@Immutable
data class GoalMovementState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val editing: Boolean = false,
    val withdrawal: Boolean = false,
    val amount: MoneyTextValue? = null,
    val amountText: String? = null,
    val limitPulse: Int = 0,
    val atLimit: Boolean = false,
    val goals: List<GoalChoice> = emptyList(),
    val goalId: String? = null,
    val overSaved: MoneyTextValue? = null,
    val blockedBy: BlockingWithdrawal? = null,
    val date: LocalDate,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val note: String = "",
    val composerOpen: Boolean = false,
    val composerDraft: String = "",
    val composerProblem: MovementNoteProblem? = null,
    val composerLength: Int = 0,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    val busy: Boolean = false,
    val error: GoalMovementError? = null,
    val confirmDiscard: Boolean = false,
    /** The form is finished and should close. */
    val closed: Boolean = false,
    /** Varios was pressed: the form gives way to Aportar varios with what is typed. */
    val several: GoalSeveral? = null
) {
    val goal: GoalChoice? get() = goals.firstOrNull { it.id == goalId }

    /** While saving, or once nothing can be sent as it is, the draft holds still. */
    val editable: Boolean
        get() = !busy && !closed && error !in Blocking

    val canSubmit: Boolean
        get() = !loading && !loadFailed && editable && amount != null && goal != null &&
            overSaved == null && blockedBy == null && (!editing || changed)

    val canDelete: Boolean get() = editing && !loading && !loadFailed && editable

    /**
     * Varios is offered for a new movement once a goal is chosen, and not while a pasted
     * amount is refused: it would be lost on the way.
     */
    val canOpenSeveral: Boolean
        get() = !editing && !loading && !loadFailed && editable && goal != null &&
            amountText == null

    private companion object {
        val Blocking = setOf(
            GoalMovementError.Unknown,
            GoalMovementError.DataChanged,
            GoalMovementError.Gone,
            GoalMovementError.Completed
        )
    }
}

sealed interface GoalMovementAction {
    data class Kind(val withdrawal: Boolean) : GoalMovementAction

    data class Digit(val digit: Int) : GoalMovementAction

    data object TripleZero : GoalMovementAction

    data object Backspace : GoalMovementAction

    data object ClearAmount : GoalMovementAction

    /** Text from the clipboard, exactly as it was. */
    data class Paste(val text: String) : GoalMovementAction

    data class GoalPicked(val id: String) : GoalMovementAction

    data object OpenDatePicker : GoalMovementAction

    data object CloseDatePicker : GoalMovementAction

    data class DatePicked(val date: LocalDate) : GoalMovementAction

    data object OpenComposer : GoalMovementAction

    data class ComposerChanged(val text: String) : GoalMovementAction

    data object ComposerDone : GoalMovementAction

    data object ComposerCancelled : GoalMovementAction

    data object Submit : GoalMovementAction

    data object Retry : GoalMovementAction

    data object Delete : GoalMovementAction

    /** Varios: continue on Aportar varios, with what is typed as its line one. */
    data object Several : GoalMovementAction

    data object Back : GoalMovementAction

    data object DiscardConfirmed : GoalMovementAction

    data object KeepEditing : GoalMovementAction
}
