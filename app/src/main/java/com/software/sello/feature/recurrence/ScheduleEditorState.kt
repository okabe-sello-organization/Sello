package com.software.sello.feature.recurrence

import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatUi
import java.time.LocalDate

/** Why the amount as typed or pasted cannot be used. The text is kept as it was. */
enum class ScheduleAmountProblem { NotAnAmount, Zero, TooLarge }

/** Why a note or Otro's name cannot be kept. */
enum class ScheduleTextProblem { TooLong, NotAllowed }

/** A category a payment can be put in, as its chip shows it. */
data class ScheduleCategory(val id: String, val name: String, val iconKey: String)

/** What still has to be right before the change can be saved, most important first. */
enum class ScheduleMissing { Amount, Category, SourceName, Note, Repeat, Changes }

/** Why a change, a pause, a resume or the deletion did not happen. None of these wrote anything. */
enum class ScheduleEditorError {
    NotSaved,

    /** The data was replaced meanwhile (restore or reset). */
    DataChanged,

    /** It is not known yet whether it was saved; asking again will tell. */
    Unknown,

    /** The schedule no longer exists. */
    ScheduleGone,

    /** The schedule changed since this screen read it. */
    ScheduleChanged,

    /** Catch-up has not yet recorded what is due; changing now would lose or alter it. */
    CatchUpPending,

    /** The chosen category no longer exists or is archived. */
    CategoryGone,

    /** A new rule or start begins after today. */
    StartTooEarly,

    /** An end is on or after today and the start. */
    EndTooEarly
}

/**
 * One schedule being changed, in the entry's layout (docs/design/v2 Pagos e ingresos
 * fijos). [income] says which kind it is; a payment stays a payment. [start] is the day
 * a changed rule starts from: the stored start while the rule is unchanged.
 */
data class ScheduleEditorState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val income: Boolean = false,
    val amount: MoneyTextValue? = null,
    val amountText: String? = null,
    val amountProblem: ScheduleAmountProblem? = null,
    val limitPulse: Int = 0,
    val atLimit: Boolean = false,
    val categories: List<ScheduleCategory> = emptyList(),
    val categoryId: String? = null,
    val sourceKeys: List<String> = emptyList(),
    val sourceKey: String = "",
    val otherName: String = "",
    val note: String = "",
    val noteProblem: ScheduleTextProblem? = null,
    /** The note or Otro's name being written in its composer; null while it is closed. */
    val composer: ScheduleComposer? = null,
    val composerDraft: String = "",
    val composerProblem: ScheduleTextProblem? = null,
    val start: LocalDate? = null,
    val today: LocalDate,
    val startPickerOpen: Boolean = false,
    val repeat: RepeatUi = RepeatUi(),
    val pausedFrom: LocalDate? = null,
    /** The archived category that paused it, when that is why it is paused. */
    val pausedByArchive: String? = null,
    val next: LocalDate? = null,
    val missing: ScheduleMissing? = ScheduleMissing.Changes,
    val busy: Boolean = false,
    val error: ScheduleEditorError? = null,
    val confirmDelete: Boolean = false,
    val confirmDiscard: Boolean = false,
    val closed: Boolean = false
) {
    val paused: Boolean get() = pausedFrom != null

    val editable: Boolean
        get() = !loading && !loadFailed && !busy && error !in Blocking && !closed

    val canSave: Boolean get() = editable && missing == null

    val category: ScheduleCategory? get() = categories.firstOrNull { it.id == categoryId }

    private companion object {
        val Blocking = setOf(
            ScheduleEditorError.Unknown,
            ScheduleEditorError.ScheduleGone,
            ScheduleEditorError.ScheduleChanged,
            ScheduleEditorError.DataChanged
        )
    }
}

enum class ScheduleComposer { Note, SourceName }

sealed interface ScheduleEditorAction {
    data class Digit(val digit: Int) : ScheduleEditorAction

    data object TripleZero : ScheduleEditorAction

    data object Backspace : ScheduleEditorAction

    data object ClearAmount : ScheduleEditorAction

    data class Paste(val text: String) : ScheduleEditorAction

    data class CategoryPicked(val id: String) : ScheduleEditorAction

    data class SourcePicked(val key: String) : ScheduleEditorAction

    data class OpenComposer(val what: ScheduleComposer) : ScheduleEditorAction

    data class ComposerChanged(val text: String) : ScheduleEditorAction

    data object ComposerDone : ScheduleEditorAction

    data object ComposerCancelled : ScheduleEditorAction

    data object OpenStartPicker : ScheduleEditorAction

    data object CloseStartPicker : ScheduleEditorAction

    data class StartPicked(val date: LocalDate) : ScheduleEditorAction

    data class Repeat(val action: RepeatAction) : ScheduleEditorAction

    data object Save : ScheduleEditorAction

    data object Pause : ScheduleEditorAction

    data object Resume : ScheduleEditorAction

    data object Delete : ScheduleEditorAction

    data object DeleteConfirmed : ScheduleEditorAction

    data object KeepIt : ScheduleEditorAction

    data object Retry : ScheduleEditorAction

    data object Back : ScheduleEditorAction

    data object DiscardConfirmed : ScheduleEditorAction

    data object KeepEditing : ScheduleEditorAction
}
