package com.software.sello.feature.bulk

import androidx.compose.runtime.Immutable
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.policy.EntryBatchPolicy
import com.software.sello.presentation.carry.CarryCorrectionList
import java.time.LocalDate

/** What every line of the receipt is: the Gasto/Ingreso switch sets it for the whole receipt. */
enum class BulkKind { Expense, Income }

/** A category a line can be put in. */
data class BulkCategory(val id: String, val name: String, val iconKey: String)

/**
 * A line's income source: one of [IncomeSource.keys]. [name] is Otro's name, null while it
 * has none or for any other source.
 */
data class BulkSource(val key: String, val name: String?) {
    val other: Boolean get() = key == IncomeSource.Other.KEY
}

/** What the composer above the keyboard is writing for the highlighted line. */
enum class BulkComposer { Note, SourceName }

/** Why one line cannot be saved as it is. */
enum class LineProblem {
    /** It has an amount and no category. */
    NoCategory,

    /** An income with an amount and no source. */
    NoSource,

    /** An income with an amount whose source is Otro, still without a name. */
    NoSourceName,

    /** Storage refused it: its category is archived. */
    CategoryArchived,

    /** Storage refused it: its category no longer exists. */
    CategoryMissing,

    /** Storage refused it: the month's total cannot hold it. */
    MonthFull
}

/** Why saving the receipt did not finish. None of these means anything was saved. */
enum class BulkError {
    /** Nothing was written; the draft stays. */
    NotSaved,

    /** The receipt's date is after today. */
    FutureDate,

    /** The receipt's date cannot be stored. */
    InvalidDate,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** Saving would leave a month whose carry cannot be calculated (ADR 0020 CP23). */
    CarryUnavailable,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown
}

/**
 * One line of the receipt as the screen shows it. [category] is null while it has none
 * that can take an expense, [source] while it has no income source (each is null on a
 * receipt of the other kind); [amount] is null while nothing is typed.
 */
data class BulkLineUi(
    val key: String,
    val category: BulkCategory?,
    val amount: MoneyTextValue?,
    val note: String,
    val problem: LineProblem? = null,
    val source: BulkSource? = null
)

/** The later months saving the receipt changes, shown before it is saved (ADR 0020 CP20). */
data class BulkCorrection(val list: CarryCorrectionList, val changed: Boolean)

/** A line just removed from the draft, which Deshacer puts back. */
data class RemovedLine(val categoryName: String?, val source: BulkSource? = null)

/**
 * Anotar varios: several expenses or several incomes ([kind]) on one receipt, one date,
 * saved together. There is always at least one line and at most [EntryBatchPolicy.MAX_LINES];
 * [highlighted] is always one of them, the line the pad, the chips and the note apply to.
 */
@Immutable
data class BulkEntryState(
    val kind: BulkKind = BulkKind.Expense,
    val lines: List<BulkLineUi>,
    val highlighted: Int,
    val date: LocalDate,
    val today: LocalDate,
    /** The categories have not been read yet and none were read before. */
    val loading: Boolean = true,
    /** Reading the categories failed; the lines stay. */
    val readFailed: Boolean = false,
    val categories: List<BulkCategory> = emptyList(),
    /** The income sources a line can take, in the approved order; empty for expenses. */
    val sources: List<String> = emptyList(),
    /** The sum of the lines with an amount. */
    val total: MoneyTextValue,
    /** Lines with an amount: the ones that would be saved. */
    val count: Int = 0,
    /** Lines with an amount and no category, or no source (or Otro without a name). */
    val missingCategories: Int = 0,
    /** Lines storage refused, still as they were refused. */
    val refusedLines: Int = 0,
    /** The last key was refused because twelve digits is the most a line takes. */
    val atLimit: Boolean = false,
    val limitPulse: Int = 0,
    val datePickerOpen: Boolean = false,
    /**
     * The highlighted line's note, or Otro's name ([composing]), being written; null while
     * the composer is closed.
     */
    val noteDraft: String? = null,
    val composing: BulkComposer = BulkComposer.Note,
    val noteDraftTooLong: Boolean = false,
    val noteDraftNotAllowed: Boolean = false,
    /** Otro's name is empty: it needs one. */
    val noteDraftRequired: Boolean = false,
    val noteDraftLength: Int = 0,
    val busy: Boolean = false,
    val error: BulkError? = null,
    val confirmDiscard: Boolean = false,
    /** Switching Gasto/Ingreso with lines typed waits for this to be confirmed. */
    val confirmSwitch: Boolean = false,
    val removed: RemovedLine? = null,
    val correction: BulkCorrection? = null,
    /** Storage confirmed the receipt; nothing can be sent again. */
    val recorded: Boolean = false,
    /** The sheet is finished and should close. */
    val closed: Boolean = false
) {
    /** While saving, while its outcome is unknown, or once saved, the draft must not change. */
    val editable: Boolean get() = !busy && error != BulkError.Unknown && !recorded

    val canSubmit: Boolean
        get() = editable && !loading && !readFailed && count > 0 && missingCategories == 0 &&
            refusedLines == 0 && correction == null

    /** Another line can be added: there is room, and the last one has an amount. */
    val canAdd: Boolean
        get() = editable && lines.size < EntryBatchPolicy.MAX_LINES && lines.last().amount != null

    val atLineLimit: Boolean get() = lines.size >= EntryBatchPolicy.MAX_LINES

    /** Lines with something typed, which leaving would lose. */
    val typedLines: Int get() = lines.count { it.amount != null || it.note.isNotBlank() }

    val highlightedLine: BulkLineUi get() = lines[highlighted]
}

sealed interface BulkEntryAction {
    data class Digit(val digit: Int) : BulkEntryAction

    data object TripleZero : BulkEntryAction

    data object Backspace : BulkEntryAction

    data object ClearAmount : BulkEntryAction

    data class CategoryPicked(val id: String) : BulkEntryAction

    /** The Gasto/Ingreso switch: asks first when lines are typed. */
    data class KindPicked(val kind: BulkKind) : BulkEntryAction

    /** Pasar a ingreso / Pasar a gasto: amounts and notes stay; categories and sources go. */
    data object SwitchConfirmed : BulkEntryAction

    /** Seguir con gastos / Seguir con ingresos. */
    data object SwitchDismissed : BulkEntryAction

    /** An income source chip; Otro opens the composer for its name. */
    data class SourcePicked(val key: String) : BulkEntryAction

    /** Cambiar on Otro's name. */
    data object OpenSourceName : BulkEntryAction

    /** Tapping a line: the pad, the chips and the note now apply to it. */
    data class Select(val index: Int) : BulkEntryAction

    data class Remove(val index: Int) : BulkEntryAction

    /** Deshacer: the line just removed goes back where it was. */
    data object UndoRemove : BulkEntryAction

    /** The Deshacer notice went away. */
    data object RemoveSeen : BulkEntryAction

    /** Otro renglón. */
    data object AddLine : BulkEntryAction

    data object OpenDatePicker : BulkEntryAction

    data object CloseDatePicker : BulkEntryAction

    data class DatePicked(val date: LocalDate) : BulkEntryAction

    data object OpenNote : BulkEntryAction

    data class NoteDraftChanged(val text: String) : BulkEntryAction

    data object NoteDone : BulkEntryAction

    data object NoteCancelled : BulkEntryAction

    data object Submit : BulkEntryAction

    /** Read the categories again, or ask again about a save whose outcome is unknown. */
    data object Retry : BulkEntryAction

    data object Back : BulkEntryAction

    data object DiscardConfirmed : BulkEntryAction

    data object KeepEditing : BulkEntryAction

    /** The list of later months is confirmed: the same receipt is saved with it. */
    data object CorrectionConfirmed : BulkEntryAction

    /** The list is not confirmed: nothing is saved and the draft stays. */
    data object CorrectionDismissed : BulkEntryAction
}
