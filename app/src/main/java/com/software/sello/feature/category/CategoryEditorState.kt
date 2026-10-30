package com.software.sello.feature.category

import com.software.sello.designsystem.component.MoneyTextValue
import java.time.YearMonth

/** Why the name cannot be used. */
enum class NameError { Required, TooLong, NotAllowed, Taken }

/** Why the limit cannot be used. The text typed is kept as it was. */
enum class LimitError { Required, NotAnAmount }

/**
 * Why saving did not finish. For a new category none of these means it was created.
 * For an existing one, [CategoryEditorState.savedParts] says what was saved before this.
 */
enum class SaveError {
    /** Storage failed and nothing more was written. */
    NotSaved,

    /** The data was replaced meanwhile (restore or reset). Trying again uses the new data. */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The category was changed since this form read it. The person chooses what to do. */
    Conflict,

    /** The category being edited no longer exists. */
    CategoryGone,

    /**
     * Archiving waited for a fixed payment of the category that is due, and Sello could
     * not record it first: nothing was archived.
     */
    PaymentsPending
}

/** One part of a category, saved by its own command. */
enum class EditPart { Name, Icon, Limit }

/**
 * What archiving or unarchiving would do, asked before it is done. [expensesThisMonth]
 * is null when it could not be counted. [resumesBudget] is for a category archived in
 * an earlier month: unarchiving gives it its last limit again from [currentMonth].
 * [discardsChanges] says the form has changes that will not be saved.
 */
data class ArchiveQuestion(
    val unarchive: Boolean,
    val expensesThisMonth: Int?,
    val currentMonth: YearMonth,
    val resumesBudget: Boolean,
    val discardsChanges: Boolean
)

/** A limit offered as a shortcut: drawn as [label], such as 200.000, and read as [spoken]. */
data class LimitSuggestion(val pesos: Long, val label: String, val spoken: String)

/**
 * The month's total limit were the draft's limit saved: [changes] says whether that
 * differs from the total the month has now.
 */
data class LimitTotal(val month: YearMonth, val amount: MoneyTextValue, val changes: Boolean)

/** How the editor ended. Kept in state so a rotation cannot lose it. */
sealed interface EditorResult {
    /** A receipt confirmed the category. [categoryId] is its real identifier. */
    data class Created(val categoryId: String) : EditorResult

    /** Every change to an existing category was confirmed by its receipt. */
    data object Updated : EditorResult

    /** A receipt confirmed that the category was archived, or unarchived. */
    data object Archived : EditorResult

    data object Unarchived : EditorResult

    data object Discarded : EditorResult
}

/**
 * The new-category form. [name] and [limitText] are exactly what was typed. The limit
 * is either [unlimited], which is only ever chosen, or the amount in [limitText]; an
 * empty amount is never read as zero or as no limit. [limitText] is kept while
 * [unlimited] is on and is not sent then.
 */
data class CategoryEditorState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    /** Opened because recording an expense needs a category first. */
    val forEntry: Boolean = false,
    val name: String = "",
    val nameError: NameError? = null,
    val iconKeys: List<String> = emptyList(),
    val iconKey: String = "",
    /** Icons other categories have, archived ones too: they can still be chosen. */
    val usedIconKeys: Set<String> = emptySet(),
    val unlimited: Boolean = false,
    val limitText: String = "",
    /** [limitText] drawn as an amount, or null while it is empty or not one. */
    val limitAmount: MoneyTextValue? = null,
    val limitError: LimitError? = null,
    val suggestions: List<LimitSuggestion> = emptyList(),
    /** Shown under the limit; null while the draft's limit is not usable or cannot be added. */
    val limitTotal: LimitTotal? = null,
    val busy: Boolean = false,
    val saveError: SaveError? = null,
    val confirmDiscard: Boolean = false,
    val result: EditorResult? = null,
    /** An existing category is being changed, not a new one created. */
    val editing: Boolean = false,
    val archived: Boolean = false,
    /** Editing: the month whose limit this form shows and changes. */
    val targetMonth: YearMonth? = null,
    /** [targetMonth] is over: only that month's limit changes, no other and no default. */
    val historical: Boolean = false,
    /** The limit cannot be changed here: an archived category has no limit going forward. */
    val limitLocked: Boolean = false,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    /** What the last attempt saved before it stopped, in order. */
    val savedParts: List<EditPart> = emptyList(),
    val archiveQuestion: ArchiveQuestion? = null,
    /** Editing the month in progress: the carry setting. Null where it is not offered. */
    val carry: CarryCard? = null,
    /** A carry question waiting for an answer: turning on, a drop or a correction list. */
    val carryQuestion: CarryQuestion? = null,
    /** Why the last carry change, or a limit while carrying, was refused. */
    val carryProblem: CarryProblemText? = null,
    /** The last carry change that has its receipt. */
    val carryDone: CarryDone? = null
) {
    /**
     * Nothing can be sent without a name, with a name known to be taken, while
     * sending, or before the form is ready. An existing category also needs a change.
     */
    val canSubmit: Boolean
        get() = !loading && !loadFailed && !busy && name.isNotBlank() &&
            nameError != NameError.Taken && saveError !in Blocking && (!editing || changed)

    /**
     * While saving, while a save's outcome is unknown, or once the category is known to
     * have changed or gone underneath this form, the draft must not change.
     */
    val editable: Boolean get() = !busy && saveError !in Blocking

    /** An existing category that has been read can be archived or unarchived from here. */
    val canArchive: Boolean get() = editing && !loading && !loadFailed && editable

    /**
     * Carry is changed by its own command (CP28), never with the form's changes: only while
     * nothing else is waiting to be saved.
     */
    val canChangeCarry: Boolean
        get() = canArchive && !changed && carry != null && carry !is CarryCard.Unreadable

    private companion object {
        val Blocking = setOf(SaveError.Unknown, SaveError.Conflict, SaveError.CategoryGone)
    }
}

sealed interface CategoryEditorAction {
    data class NameChanged(val text: String) : CategoryEditorAction

    data class IconPicked(val key: String) : CategoryEditorAction

    data class UnlimitedChanged(val unlimited: Boolean) : CategoryEditorAction

    /** Pasted text for the limit, kept exactly as it came. */
    data class LimitChanged(val text: String) : CategoryEditorAction

    /** A Sello pad digit, 0–9, on the limit. */
    data class LimitDigit(val digit: Int) : CategoryEditorAction

    data object LimitTripleZero : CategoryEditorAction

    data object LimitBackspace : CategoryEditorAction

    /** A long press on backspace: the limit is emptied. */
    data object LimitCleared : CategoryEditorAction

    /** One of the suggested limits, in whole pesos. */
    data class SuggestionPicked(val pesos: Long) : CategoryEditorAction

    data object Submit : CategoryEditorAction

    /** Reload the form, or ask again about a save whose outcome is unknown. */
    data object Retry : CategoryEditorAction

    data object Back : CategoryEditorAction

    data object DiscardConfirmed : CategoryEditorAction

    data object KeepEditing : CategoryEditorAction

    /** Ask to archive the category, or to unarchive an archived one. Nothing is done yet. */
    data object Archive : CategoryEditorAction

    data object ArchiveConfirmed : CategoryEditorAction

    data object ArchiveDismissed : CategoryEditorAction

    /** After a conflict: read the category again and start from what it is now. */
    data object ReloadDiscardingDraft : CategoryEditorAction

    /** After a conflict: read the category again and keep what was typed, to save over it. */
    data object ReloadKeepingDraft : CategoryEditorAction

    /** Ask to turn carry on from this month. Nothing is sent yet. */
    data object EnableCarry : CategoryEditorAction

    /** Turn carry off: with a balance, Sello asks first with the exact figures. */
    data object DisableCarry : CategoryEditorAction

    /** The carry question shown is answered yes: what it names is sent. */
    data object CarryConfirmed : CategoryEditorAction

    /** The carry question shown is answered no: nothing changes. */
    data object CarryDismissed : CategoryEditorAction
}
