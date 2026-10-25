package com.software.sello.feature.income

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatUi
import java.time.LocalDate
import java.time.YearMonth

/** Why the amount as typed or pasted cannot be used. The text is kept as it was. */
enum class IncomeAmountProblem { NotAnAmount, Zero, TooLarge }

enum class IncomeTextProblem { TooLong, NotAllowed }

/** What the composer docked above the keyboard is writing. */
enum class IncomeComposer { Note, SourceName }

/**
 * What still has to be filled in before the income can be recorded, most important
 * first. [SourceName] is for "Otro", which needs a name. [Changes] is only for a
 * recorded income being corrected: nothing differs yet from what is stored.
 */
enum class IncomeMissing { Amount, SourceName, Note, Repeat, Changes }

/** Why recording did not finish. None of these means the income was saved. */
enum class IncomeFormError {
    /** Storage failed and nothing was written. */
    NotSaved,

    /** The date is after the current day. */
    FutureDate,

    /** The date cannot be stored. */
    InvalidDate,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The income being corrected no longer exists. */
    IncomeGone,

    /** The income being corrected was changed since this form read it. */
    IncomeChanged,

    /** Deleting failed and the income is still there. */
    NotDeleted,

    /** A schedule starts today or later; the date was earlier. */
    StartTooEarly,

    /** A schedule's end is on or after its start and today. */
    EndTooEarly,

    /** The following incomes' new terms were refused; nothing was written. */
    FollowingNotSaved
}

/**
 * The income form. [amount] is present only for a valid amount; [amountText] is what
 * was pasted when it could not be used. [sourceKey] is one of [sourceKeys]; [otherName]
 * is what was typed for "Otro" and is kept, but not sent, while another source is chosen.
 */
data class IncomeFormState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val amount: MoneyTextValue? = null,
    val amountText: String? = null,
    val amountProblem: IncomeAmountProblem? = null,
    /** Goes up each time a key is refused because twelve digits is the most there can be. */
    val limitPulse: Int = 0,
    /** The last key was refused for that reason: "Máximo 12 dígitos" until the next one counts. */
    val atLimit: Boolean = false,
    val sourceKeys: List<String> = emptyList(),
    val sourceKey: String = "",
    val otherName: String = "",
    val otherNameProblem: IncomeTextProblem? = null,
    val date: LocalDate,
    val today: LocalDate,
    val datePickerOpen: Boolean = false,
    val note: String = "",
    val noteProblem: IncomeTextProblem? = null,
    /**
     * The composer open for the note or Otro's name, or null. What it holds becomes that
     * text only when finished within its limit; [composerLength] counts it as people see it.
     */
    val composer: IncomeComposer? = null,
    val composerDraft: String = "",
    val composerProblem: IncomeTextProblem? = null,
    val composerLength: Int = 0,
    val missing: IncomeMissing? = IncomeMissing.Amount,
    val busy: Boolean = false,
    val generationChanged: Boolean = false,
    val error: IncomeFormError? = null,
    val confirmDiscard: Boolean = false,
    /**
     * The new income storage confirmed, by its identifier. Once it is set nothing can be
     * sent again; the form closes and the caller says what was saved.
     */
    val recorded: String? = null,
    /** The form is finished and should close. */
    val closed: Boolean = false,
    /** A recorded income is being corrected, not a new one written. */
    val editing: Boolean = false,
    /** Months other than the current one whose income saving or deleting would change. */
    val pastMonths: List<YearMonth> = emptyList(),
    /** Se repite, offered only for a new income. */
    val repeat: RepeatUi = RepeatUi(),
    /** Saving waits for Solo este or Este y los siguientes about [following]. */
    val askFollowing: Boolean = false,
    val following: FollowingSchedule? = null,
    /** The following incomes already took the new terms; this income did not yet. */
    val followingSaved: Boolean = false
) {
    val canSubmit: Boolean
        get() = !loading && !loadFailed && !busy && !generationChanged && missing == null &&
            error !in Blocking && recorded == null

    /** While saving, or once nothing here can be sent as it is, the draft must not change. */
    val editable: Boolean get() = !busy && !generationChanged && error !in Blocking &&
        recorded == null

    val canDelete: Boolean get() = editing && !loading && !loadFailed && editable

    val needsOtherName: Boolean get() = sourceKey == OTHER

    companion object {
        /** The key of the source that carries a name of its own. */
        const val OTHER = "other"

        private val Blocking = setOf(
            IncomeFormError.Unknown,
            IncomeFormError.IncomeGone,
            IncomeFormError.IncomeChanged
        )
    }
}

sealed interface IncomeFormAction {
    data class Digit(val digit: Int) : IncomeFormAction

    data object TripleZero : IncomeFormAction

    data object Backspace : IncomeFormAction

    data object ClearAmount : IncomeFormAction

    /** Text from the clipboard, exactly as it was. */
    data class Paste(val text: String) : IncomeFormAction

    data class SourcePicked(val key: String) : IncomeFormAction

    data class OtherNameChanged(val text: String) : IncomeFormAction

    data object OpenDatePicker : IncomeFormAction

    data object CloseDatePicker : IncomeFormAction

    data class DatePicked(val date: LocalDate) : IncomeFormAction

    data class NoteChanged(val text: String) : IncomeFormAction

    /** Open the composer on the note, or on Otro's name, as written now. */
    data class OpenComposer(val what: IncomeComposer) : IncomeFormAction

    data class ComposerChanged(val text: String) : IncomeFormAction

    /** Keep what the composer holds, if it is within the limit. */
    data object ComposerDone : IncomeFormAction

    /** Close the composer; the text stays as it was. */
    data object ComposerCancelled : IncomeFormAction

    data object Submit : IncomeFormAction

    /** Reload the form, or ask again about a save whose outcome is unknown. */
    data object Retry : IncomeFormAction

    data object Back : IncomeFormAction

    data object DiscardConfirmed : IncomeFormAction

    data object KeepEditing : IncomeFormAction

    /** Delete the recorded income being corrected. */
    data object Delete : IncomeFormAction

    /** Se repite's row and sheet. */
    data class Repeat(val action: RepeatAction) : IncomeFormAction

    /** Solo este ([alsoFollowing] false) or Este y los siguientes. */
    data class FollowingChosen(val alsoFollowing: Boolean) : IncomeFormAction

    /** Volver a editar: nothing is saved. */
    data object FollowingDismissed : IncomeFormAction
}
