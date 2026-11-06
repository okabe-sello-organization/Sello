package com.software.sello.feature.category

import com.software.sello.designsystem.component.MoneyTextValue
import java.time.YearMonth

/** Why the name cannot be used. */
enum class NameError { Required, TooLong, NotAllowed, Taken }

/** Why the limit cannot be used. The text typed is kept as it was. */
enum class LimitError { Required, NotAnAmount, TooLarge }

/**
 * Why saving did not finish. None of these means anything was saved: a category's
 * changes are saved together or not at all.
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
    PaymentsPending,

    /**
     * The month the form showed ended before the save: nothing was written, and the form
     * now shows the month in progress ([CategoryEditorState.monthEnded] says which ended).
     */
    MonthEnded
}

/** The month the form showed ended before [carry] was changed, or else the limit saved. */
data class MonthEnded(val month: YearMonth, val carry: Boolean)

/** One part of a category that differs from storage; Guardar saves them all at once. */
enum class EditPart { Name, Icon, Limit, Carry }

/**
 * Carry turned [on] or off in the draft, for [month], and not saved yet. [forgets] is the
 * balance carried in that turning off was confirmed to forget, when there is one.
 */
data class CarryPending(val on: Boolean, val month: YearMonth, val forgets: MoneyTextValue?)

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

    /** A receipt confirmed that the category was deleted: its detail closes too. */
    data object Deleted : EditorResult

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
    /** With [SaveError.MonthEnded]: which month ended, and what it stopped. */
    val monthEnded: MonthEnded? = null,
    /** The limit cannot be changed here: an archived category has no limit going forward. */
    val limitLocked: Boolean = false,
    /** Editing: something differs from what is stored. */
    val changed: Boolean = false,
    val archiveQuestion: ArchiveQuestion? = null,
    /** Editing the month in progress: the carry setting. Null where it is not offered. */
    val carry: CarryCard? = null,
    /** A carry question waiting for an answer: turning on, a drop or a correction list. */
    val carryQuestion: CarryQuestion? = null,
    /** Why the last carry change, or a limit while carrying, was refused. */
    val carryProblem: CarryProblemText? = null,
    /** Carry turned on or off in the draft: saved with the rest by Guardar. */
    val carryPending: CarryPending? = null,
    /** Deleting the category, while it is being asked, sent or checked (ADR 0024). */
    val removal: RemovalState? = null
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

    /** The top bar's ⋮ (archive, delete): on an existing category read and still there. */
    val showsMenu: Boolean
        get() = editing && !loading && !loadFailed && saveError !in Blocking

    /** Deleting can start: nothing else is being saved, asked or checked. */
    val canDelete: Boolean
        get() = canArchive && removal == null && archiveQuestion == null && carryQuestion == null

    /**
     * Carry is part of the draft, saved with the rest (SELLO-188). It is never offered on a
     * card that could not be read, nor turned on while the draft has no limit (CP3).
     */
    val canChangeCarry: Boolean
        get() = canArchive && carry != null && carry !is CarryCard.Unreadable &&
            !(unlimited && carry is CarryCard.Off && carryPending == null)

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

    /** Ask to turn carry on from this month, or undo the draft's turning off. Nothing is sent. */
    data object EnableCarry : CategoryEditorAction

    /** Turn carry off in the draft: with a balance, Sello asks first with the exact figures. */
    data object DisableCarry : CategoryEditorAction

    /** The carry question shown is answered yes: the draft takes it, or the save goes on. */
    data object CarryConfirmed : CategoryEditorAction

    /** The carry question shown is answered no: nothing changes. */
    data object CarryDismissed : CategoryEditorAction
}

/** Deleting the category (ADR 0024): each answer to the slip or sheet on screen. */
sealed interface RemovalAction : CategoryEditorAction {
    /** Borrar categoría in the ⋮: its contents are counted first. Nothing is deleted yet. */
    data object Start : RemovalAction

    /** Cancelar, Back or a tap outside: nothing is deleted. */
    data object Dismiss : RemovalAction

    /** Moverlos a otra categoría: the categories that can take them. */
    data object Move : RemovalAction

    /** Borrar todo, as a choice: what goes, and the word to type. */
    data object DeleteAll : RemovalAction

    /** Archívala: the archive question instead. */
    data object ArchiveInstead : RemovalAction

    data class Pick(val categoryId: String) : RemovalAction

    /** What is typed to confirm Borrar todo, kept exactly. */
    data class Word(val text: String) : RemovalAction

    /** Borrar, Mover y borrar, Borrar todo or the confirmed correction list: sent once. */
    data object Confirm : RemovalAction

    /** A failure slip's way forward: Revisar, Elegir otra, Reintentar or Comprobar de nuevo. */
    data object Next : RemovalAction
}
