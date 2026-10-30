package com.software.sello.feature.expense

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.carry.CarryCorrectionList
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatUi
import java.time.LocalDate
import java.time.YearMonth

/** Why the amount as typed or pasted cannot be used. The text is kept as it was. */
enum class AmountProblem { NotAnAmount, Zero, TooLarge }

enum class NoteProblem { TooLong, NotAllowed }

/**
 * What still has to be filled in before the expense can be recorded, most important
 * first. [Changes] is only for a recorded expense being corrected: nothing differs yet
 * from what is stored, so there is nothing to save.
 */
enum class Missing { Amount, Category, Note, Repeat, Changes }

/** Why recording did not finish. None of these means the expense was saved. */
enum class EntryError {
    /** Storage failed and nothing was written. */
    NotSaved,

    /** The chosen category no longer accepts expenses; another must be chosen. */
    CategoryGone,

    /** The date is after the current day. */
    FutureDate,

    /** The date cannot be stored. No screen offers such a date; a caller could send one. */
    InvalidDate,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The expense being corrected no longer exists. */
    ExpenseGone,

    /** The expense being corrected was changed since this form read it. */
    ExpenseChanged,

    /** Deleting failed and the expense is still there. */
    NotDeleted,

    /** Whether the deletion was saved could not be found out. */
    DeleteUnknown,

    /** A schedule starts today or later; the date was earlier. */
    StartTooEarly,

    /** A schedule's end is on or after its start and today. */
    EndTooEarly,

    /** The following payments' new terms were refused; nothing was written. */
    FollowingNotSaved,

    /** Saving would leave a month whose carry cannot be calculated (ADR 0020 CP23). */
    CarryUnavailable
}

/**
 * The later months saving this expense changes, shown before it is saved (ADR 0020 CP20,
 * CP21). With [changed] the list was read again because the data moved (SC34).
 */
data class EntryCorrection(val list: CarryCorrectionList, val changed: Boolean)

data class EntryCategory(val id: String, val name: String, val iconKey: String)

/**
 * What is typed so far, handed to Anotar varios to become its line one: the amount's
 * digits, the chosen category, the note and the date. Each may be missing.
 */
data class ExpenseSeveral(
    val amount: String?,
    val categoryId: String?,
    val note: String?,
    val date: LocalDate
)

/**
 * The entry form. [amount] is present only for a valid amount; [amountText] is what
 * was pasted when it could not be used. Nothing typed is ever changed into something else.
 */
data class ExpenseEntryState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val amount: MoneyTextValue? = null,
    val amountText: String? = null,
    val amountProblem: AmountProblem? = null,
    /** Goes up each time a key is refused because twelve digits is the most there can be. */
    val limitPulse: Int = 0,
    /** The last key was refused for that reason: "Máximo 12 dígitos" until the next one counts. */
    val atLimit: Boolean = false,
    val categories: List<EntryCategory> = emptyList(),
    val categoryId: String? = null,
    val date: LocalDate,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val note: String = "",
    val noteProblem: NoteProblem? = null,
    /**
     * The note being written in its composer, or null while the composer is closed. It
     * becomes [note] only when finished within the limit; [noteDraftLength] counts it
     * as people see it.
     */
    val noteDraft: String? = null,
    val noteDraftProblem: NoteProblem? = null,
    val noteDraftLength: Int = 0,
    val missing: Missing? = Missing.Amount,
    val busy: Boolean = false,
    val error: EntryError? = null,
    val confirmDiscard: Boolean = false,
    /**
     * The new expense storage confirmed, by its identifier. Once it is set nothing can be
     * sent again; the form closes and the caller says what was saved.
     */
    val recorded: String? = null,
    /** The form is finished and should close. */
    val closed: Boolean = false,
    /** A recorded expense is being corrected, not a new one written. */
    val editing: Boolean = false,
    /**
     * Months other than the current one whose figures saving or deleting would change:
     * the month the expense is recorded in and the month the draft would move it to.
     */
    val pastMonths: List<YearMonth> = emptyList(),
    /** Se repite, offered only for a new expense. */
    val repeat: RepeatUi = RepeatUi(),
    /** Saving waits for Solo este or Este y los siguientes about [following]. */
    val askFollowing: Boolean = false,
    val following: FollowingSchedule? = null,
    /** The following payments already took the new terms; this expense did not yet. */
    val followingSaved: Boolean = false,
    /** Saving waits for the list of later months it changes to be confirmed. */
    val correction: EntryCorrection? = null,
    /** Varios was pressed: the form gives way to Anotar varios with what is typed. */
    val several: ExpenseSeveral? = null
) {
    val canSubmit: Boolean
        get() = !loading && !loadFailed && !busy && missing == null && error !in Blocking &&
            recorded == null

    /**
     * While saving, while a save's outcome is unknown, or once the expense is known to
     * be gone or changed underneath this form, the draft must not change.
     */
    val editable: Boolean get() = !busy && error !in Blocking && recorded == null

    /**
     * Varios is offered for a new expense, not one repeating or being corrected, and not
     * while a pasted amount is refused: it would be lost on the way.
     */
    val canOpenSeveral: Boolean
        get() = !editing && editable && repeat.choice == null && amountText == null

    /** A recorded expense can be deleted from here once it has been read. */
    val canDelete: Boolean get() = editing && !loading && !loadFailed && editable

    val category: EntryCategory? get() = categories.firstOrNull { it.id == categoryId }

    private companion object {
        /** After these nothing in the form can be sent as it is. */
        val Blocking = setOf(EntryError.Unknown, EntryError.ExpenseGone, EntryError.ExpenseChanged)
    }
}

sealed interface ExpenseEntryAction {
    data class Digit(val digit: Int) : ExpenseEntryAction

    data object TripleZero : ExpenseEntryAction

    data object Backspace : ExpenseEntryAction

    data object ClearAmount : ExpenseEntryAction

    /** Text from the clipboard, exactly as it was. */
    data class Paste(val text: String) : ExpenseEntryAction

    data class CategoryPicked(val id: String) : ExpenseEntryAction

    data object OpenDatePicker : ExpenseEntryAction

    data object CloseDatePicker : ExpenseEntryAction

    data class DatePicked(val date: LocalDate) : ExpenseEntryAction

    data class NoteChanged(val text: String) : ExpenseEntryAction

    /** Open the note's composer on what is written now. */
    data object OpenNote : ExpenseEntryAction

    data class NoteDraftChanged(val text: String) : ExpenseEntryAction

    /** Keep what the composer holds as the note, if it is within the limit. */
    data object NoteDone : ExpenseEntryAction

    /** Close the composer; the note stays as it was. */
    data object NoteCancelled : ExpenseEntryAction

    /** The category editor, opened from Nueva, created this category: choose it. */
    data class CategoryCreated(val id: String) : ExpenseEntryAction

    /** Varios: continue on Anotar varios, with what is typed as its line one. */
    data object Several : ExpenseEntryAction

    data object Submit : ExpenseEntryAction

    /** Reload the form, or ask again about a save whose outcome is unknown. */
    data object Retry : ExpenseEntryAction

    data object Back : ExpenseEntryAction

    data object DiscardConfirmed : ExpenseEntryAction

    data object KeepEditing : ExpenseEntryAction

    /** Delete the recorded expense being corrected. */
    data object Delete : ExpenseEntryAction

    /** Se repite's row and sheet. */
    data class Repeat(val action: RepeatAction) : ExpenseEntryAction

    /** Solo este ([alsoFollowing] false) or Este y los siguientes. */
    data class FollowingChosen(val alsoFollowing: Boolean) : ExpenseEntryAction

    /** Volver a editar: nothing is saved. */
    data object FollowingDismissed : ExpenseEntryAction

    /** The list of later months is confirmed: the same change is saved with it. */
    data object CorrectionConfirmed : ExpenseEntryAction

    /** The list is not confirmed: nothing is saved and the draft stays. */
    data object CorrectionDismissed : ExpenseEntryAction
}
