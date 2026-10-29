package com.software.sello.feature.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmountError
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.AmountDraftError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.DeleteResult
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatDraft
import com.software.sello.presentation.recurrence.RepeatUi
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Records one expense through the real command. The draft, the identifier of a save
 * in progress and the saved expense's identifier are kept in [saved], so rotation or
 * the process being killed neither loses the draft nor records the expense twice.
 * A new expense is announced only once a receipt confirmed it, in the words of what
 * storage holds, and the form then closes.
 *
 * The preview comes from the shared snapshot and policy. Nothing is added or
 * compared here.
 *
 * With [KEY_EDITED_EXPENSE] it corrects that recorded expense instead. The draft
 * starts as what storage holds, read once; what was read, with its version, is kept in
 * [saved] too, so a restored draft is still compared with and sent against the
 * expense as it was when the form opened, never against a later one it has not shown.
 * Saving is an edit of that version, deleting goes through [corrections], and
 * neither is ever turned into recording a new expense.
 *
 * With Se repite on, a new expense becomes a schedule instead: [schedules] keeps it and
 * catch-up records each payment on its date, this one included when it starts today.
 * Nothing is recorded here. The choice is kept in [saved] like the rest of the draft.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseEntryViewModel(
    private val saved: SavedStateHandle,
    private val commands: ExpenseCommands,
    private val reads: ExpenseReads,
    snapshots: MonthlySnapshots,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: ExpenseCorrections,
    private val schedules: ScheduleCommands,
    private val scheduleReads: ScheduleReads
) : ViewModel() {
    private data class Draft(
        val amount: AmountDraft,
        val categoryId: String?,
        val date: LocalDate,
        val note: String
    )

    /** Everything that is not the draft itself. */
    private data class Progress(
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        val datePickerOpen: Boolean = false,
        val noteProblem: NoteProblem? = null,
        val busy: Boolean = false,
        val error: EntryError? = null,
        val confirmDiscard: Boolean = false,
        val recorded: String? = null,
        val closed: Boolean = false,
        val attempt: Int = 0,
        /** Correcting: the recorded expense as it was read, or null until it has been. */
        val original: Original? = null,
        val originalUnreadable: Boolean = false,
        /** The schedule that recorded the expense being corrected, while it still exists. */
        val following: StoredSchedule? = null,
        /** Saving waits for Solo este or Este y los siguientes. */
        val askFollowing: Boolean = false
    )

    /** The recorded expense as the form first read it. Notes are compared as stored. */
    private data class Original(
        val version: Long,
        val digits: String,
        val categoryId: String,
        val date: LocalDate,
        val note: String?
    )

    private val editedExpense: ExpenseId? =
        (saved.get<String>(KEY_EDITED_EXPENSE)?.let(ExpenseId::of) as? Outcome.Success)?.value
    private val editing = saved.get<String>(KEY_EDITED_EXPENSE) != null

    private val draft = MutableStateFlow(
        Draft(
            amount = saved.get<String>(KEY_AMOUNT)?.let(AmountDraft::paste) ?: prefilledAmount(),
            categoryId = saved[KEY_CATEGORY],
            date = savedDate() ?: clock.today.value.date,
            note = saved[KEY_NOTE] ?: ""
        )
    )
    private val progress = MutableStateFlow(Progress(original = savedOriginal()))
    private val repeat = RepeatDraft(saved, "expense")

    init {
        // "Today" is fixed when the form opens, so a draft restored tomorrow is still
        // dated the day it was started unless the person changes it.
        if (saved.get<String>(KEY_DATE) == null) saved[KEY_DATE] = draft.value.date.toString()
        // Opened from Pagos e ingresos fijos: Se repite's sheet opens once, on today.
        if (saved.get<Boolean>(KEY_PREFILL_REPEAT) == true &&
            saved.get<Boolean>(KEY_REPEAT_OFFERED) != true && !editing
        ) {
            saved[KEY_REPEAT_OFFERED] = true
            repeat.onAction(RepeatAction.Open, draft.value.date)
        }
    }

    /** The month the expense would be dated in, so the preview is about the right month. */
    private val month: StateFlow<SnapshotState> =
        combine(
            draft.map { YearMonth.from(it.date) }.distinctUntilChanged(),
            progress.map { it.attempt }.distinctUntilChanged()
        ) { month, _ -> month }
            .flatMapLatest(snapshots::observe)
            .stateIn(viewModelScope, SharingStarted.Eagerly, SnapshotState.Loading)

    /** The note being written in its composer; kept in [saved] so a restart reopens it. */
    private val noteDraft: StateFlow<String?> = saved.getStateFlow(KEY_NOTE_DRAFT, null)

    val state: StateFlow<ExpenseEntryState> =
        combine(
            draft,
            progress,
            month,
            clock.today,
            // Se repite is read from saved state when rendering; this only says it changed.
            combine(noteDraft, repeat.changes) { note, _ -> note }
        ) { draft, progress, month, today, note ->
            render(draft, progress, month, today.date, note)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(
                draft.value,
                progress.value,
                month.value,
                clock.today.value.date,
                noteDraft.value
            )
        )

    init {
        viewModelScope.launch {
            month.collect { state ->
                if (state is SnapshotState.Ready) chooseCategoryOnce(state.snapshot)
            }
        }
        // A saved expense or a save in flight from before the process stopped.
        viewModelScope.launch {
            val savedExpense = saved.get<String>(KEY_SAVED_EXPENSE)
            when {
                savedExpense != null -> announce(savedExpense)
                saved.get<String>(KEY_SAVED_SCHEDULE) != null -> announceSchedule()
                saved.get<String>(KEY_OPERATION) != null -> recover()
            }
        }
        if (editing && progress.value.original == null) viewModelScope.launch { loadOriginal() }
        if (editing && saved.get<String>(KEY_ORIGIN_SCHEDULE) != null) {
            viewModelScope.launch { loadFollowing() }
        }
    }

    private fun savedOriginal(): Original? {
        val version = saved.get<Long>(KEY_ORIGINAL_VERSION) ?: return null
        val date =
            (
                saved.get<String>(
                    KEY_ORIGINAL_DATE
                )?.let(EffectiveDates::parseDate) as? Outcome.Success
                )
                ?.value ?: return null
        return Original(
            version = version,
            digits = saved.get<String>(KEY_ORIGINAL_AMOUNT) ?: return null,
            categoryId = saved.get<String>(KEY_ORIGINAL_CATEGORY) ?: return null,
            date = date,
            note = saved[KEY_ORIGINAL_NOTE]
        )
    }

    /**
     * Reads the recorded expense once and starts the draft from it. A draft that was
     * restored already has what it was started from and is never read over.
     */
    private suspend fun loadOriginal() {
        val id = editedExpense
            ?: return progress.update { it.copy(error = EntryError.ExpenseGone) }
        when (val read = reads.byId(id)) {
            is Outcome.Failure -> progress.update { it.copy(originalUnreadable = true) }

            is Outcome.Success -> {
                val expense = read.value
                    ?: return progress.update { it.copy(error = EntryError.ExpenseGone) }
                val original = Original(
                    version = expense.version,
                    digits = expense.amount.money.minorUnits.toString(),
                    categoryId = expense.categoryId.value,
                    date = expense.date,
                    note = expense.note?.value
                )
                saved[KEY_ORIGINAL_VERSION] = original.version
                saved[KEY_ORIGINAL_AMOUNT] = original.digits
                saved[KEY_ORIGINAL_CATEGORY] = original.categoryId
                saved[KEY_ORIGINAL_DATE] = original.date.toString()
                saved[KEY_ORIGINAL_NOTE] = original.note
                saved[KEY_ORIGIN_SCHEDULE] = expense.origin?.schedule?.value
                setAmount(AmountDraft.paste(original.digits))
                setCategory(original.categoryId)
                saved[KEY_DATE] = original.date.toString()
                saved[KEY_NOTE] = original.note.orEmpty()
                draft.update { it.copy(date = original.date, note = original.note.orEmpty()) }
                progress.update { it.copy(original = original, originalUnreadable = false) }
                if (expense.origin != null) loadFollowing()
            }
        }
    }

    /**
     * Reads the schedule that recorded the expense being corrected. While it exists,
     * saving new terms asks whether they also apply to the payments still to come. A read
     * that fails asks nothing: correcting this one expense is always possible.
     */
    private suspend fun loadFollowing() {
        val id = saved.get<String>(KEY_ORIGIN_SCHEDULE) ?: return
        val book = (scheduleReads.book() as? Outcome.Success)?.value ?: return
        val following = book.schedules.firstOrNull { it.schedule.id.value == id }
        progress.update { it.copy(following = following) }
    }

    private fun prefilledAmount(): AmountDraft {
        val text = saved.get<String>(KEY_PREFILL_AMOUNT) ?: return AmountDraft.Empty
        // A prefilled amount that is not valid is dropped, not shown as an error the
        // person did not make.
        return AmountDraft.paste(text).takeIf { it is AmountDraft.Digits } ?: AmountDraft.Empty
    }

    private fun savedDate(): LocalDate? =
        (saved.get<String>(KEY_DATE)?.let(EffectiveDates::parseDate) as? Outcome.Success)?.value

    /**
     * The categories an expense can be put in. [keeping] is the category a recorded
     * expense is already in: it may stay there even if that category was archived since.
     */
    private fun eligible(snapshot: MonthlySnapshot, keeping: String? = null) = snapshot.categories
        .filter { !it.category.archived || it.category.id.value == keeping }
        .map { it.category }

    /**
     * The first time the categories are known and none is chosen: the one the caller
     * asked for, else the one used last, else the only one there is. One that cannot
     * take an expense is never chosen.
     */
    private suspend fun chooseCategoryOnce(snapshot: MonthlySnapshot) {
        // A recorded expense has its category; none is chosen for it.
        if (editing) return
        if (saved.get<Boolean>(KEY_CATEGORY_DEFAULTED) == true) return
        saved[KEY_CATEGORY_DEFAULTED] = true
        if (draft.value.categoryId != null) return
        val ids = eligible(snapshot).map { it.id.value }
        // Only a caller's request is chosen ("Anotar aquí", a link); otherwise none is,
        // so Anotar waits until the person picks one.
        val choice = saved.get<String>(KEY_PREFILL_CATEGORY)?.takeIf { it in ids }
        if (choice != null && draft.value.categoryId == null) setCategory(choice)
    }

    private fun setCategory(id: String?) {
        saved[KEY_CATEGORY] = id
        draft.update { it.copy(categoryId = id) }
    }

    private fun setAmount(amount: AmountDraft) {
        saved[KEY_AMOUNT] = when (amount) {
            AmountDraft.Empty -> null
            is AmountDraft.Digits -> amount.digits
            is AmountDraft.Rejected -> amount.original
        }
        draft.update { it.copy(amount = amount) }
    }

    fun onAction(action: ExpenseEntryAction) {
        val now = state.value
        when (action) {
            is ExpenseEntryAction.Digit -> if (now.editable) {
                key(
                    draft.value.amount.digit(action.digit)
                )
            }

            ExpenseEntryAction.TripleZero -> if (now.editable) key(draft.value.amount.tripleZero())

            ExpenseEntryAction.Backspace -> if (now.editable) {
                setAmount(draft.value.amount.backspace())
                progress.update { it.copy(atLimit = false) }
            }

            ExpenseEntryAction.ClearAmount -> if (now.editable) {
                setAmount(AmountDraft.Empty)
                progress.update { it.copy(atLimit = false) }
            }

            is ExpenseEntryAction.Paste -> if (now.editable) {
                setAmount(AmountDraft.paste(action.text))
                progress.update { it.copy(atLimit = false) }
            }

            is ExpenseEntryAction.CategoryPicked ->
                if (now.editable && now.categories.any { it.id == action.id }) {
                    setCategory(action.id)
                    progress.update { it.copy(error = null) }
                }

            ExpenseEntryAction.OpenDatePicker ->
                if (now.editable) progress.update { it.copy(datePickerOpen = true) }

            ExpenseEntryAction.CloseDatePicker -> progress.update {
                it.copy(datePickerOpen = false)
            }

            is ExpenseEntryAction.DatePicked -> if (now.editable) {
                // A day after today cannot be picked, and while Se repite is on a day before
                // it cannot: a schedule starts today or later. The picker closes either way.
                val fits = if (repeat.choice != null) {
                    !action.date.isBefore(now.today)
                } else {
                    !action.date.isAfter(now.today)
                }
                if (fits) {
                    saved[KEY_DATE] = action.date.toString()
                    draft.update { it.copy(date = action.date) }
                }
                progress.update { it.copy(datePickerOpen = false, error = null) }
            }

            is ExpenseEntryAction.NoteChanged -> if (now.editable) {
                saved[KEY_NOTE] = action.text
                draft.update { it.copy(note = action.text) }
            }

            ExpenseEntryAction.OpenNote -> if (now.editable) {
                saved[KEY_NOTE_DRAFT] =
                    draft.value.note
            }

            is ExpenseEntryAction.NoteDraftChanged ->
                if (saved.get<String>(KEY_NOTE_DRAFT) != null) saved[KEY_NOTE_DRAFT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            ExpenseEntryAction.NoteDone -> {
                val written = saved.get<String>(KEY_NOTE_DRAFT)
                if (written != null && now.editable && Note.of(written) is Outcome.Success) {
                    saved[KEY_NOTE] = written
                    draft.update { it.copy(note = written) }
                    saved[KEY_NOTE_DRAFT] = null
                }
            }

            ExpenseEntryAction.NoteCancelled -> saved[KEY_NOTE_DRAFT] = null

            // Created from Nueva: chosen once the month can offer it, unless the form has
            // moved on to saving meanwhile.
            is ExpenseEntryAction.CategoryCreated -> if (!editing) {
                viewModelScope.launch {
                    month.first { read ->
                        read is SnapshotState.Ready &&
                            eligible(read.snapshot).any { it.id.value == action.id }
                    }
                    if (state.value.editable) setCategory(action.id)
                }
            }

            ExpenseEntryAction.Submit -> if (now.canSubmit && now.editable) submit()

            ExpenseEntryAction.Retry -> when {
                now.busy -> Unit

                saved.get<String>(KEY_OPERATION) != null -> viewModelScope.launch { recover() }

                else -> {
                    if (progress.value.originalUnreadable) viewModelScope.launch { loadOriginal() }
                    progress.update { it.copy(attempt = it.attempt + 1) }
                }
            }

            ExpenseEntryAction.Back -> when {
                now.busy -> Unit
                now.recorded != null -> progress.update { it.copy(closed = true) }
                isTyped() -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            ExpenseEntryAction.DiscardConfirmed ->
                progress.update { it.copy(confirmDiscard = false, closed = true) }

            ExpenseEntryAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }

            ExpenseEntryAction.Delete -> if (now.canDelete) delete()

            is ExpenseEntryAction.FollowingChosen -> if (now.askFollowing && now.editable) {
                saved[KEY_FOLLOWING] = action.alsoFollowing
                progress.update { it.copy(askFollowing = false) }
                submit()
            }

            ExpenseEntryAction.FollowingDismissed -> progress.update {
                it.copy(askFollowing = false)
            }

            is ExpenseEntryAction.Repeat -> if (now.editable && !editing) {
                repeat.onAction(action.action, draft.value.date)
                keepDateFitting(now.today)
                progress.update { it.copy(error = null) }
            }
        }
    }

    /** A schedule starts today or later; a single expense is dated today or earlier. */
    private fun keepDateFitting(today: LocalDate) {
        val date = draft.value.date
        val on = repeat.choice != null
        if ((on && date.isBefore(today)) || (!on && date.isAfter(today))) {
            saved[KEY_DATE] = today.toString()
            draft.update { it.copy(date = today) }
        }
    }

    private fun key(change: AmountDraft.Change) {
        setAmount(change.draft)
        progress.update {
            if (change.limitReached) {
                it.copy(limitPulse = it.limitPulse + 1, atLimit = true)
            } else {
                it.copy(atLimit = false)
            }
        }
    }

    /** Whether leaving would lose something: anything typed, or any change to a recorded expense. */
    private fun isTyped(): Boolean {
        if (!editing) {
            return draft.value.amount != AmountDraft.Empty ||
                draft.value.note.isNotBlank() || repeat.choice != null
        }
        val original = progress.value.original ?: return false
        return changed(draft.value, original)
    }

    /** Compared as it would be stored: a note differing only in outer spaces is the same note. */
    private fun changed(draft: Draft, original: Original): Boolean {
        val note = (Note.of(draft.note) as? Outcome.Success)?.value?.value
        return (draft.amount as? AmountDraft.Digits)?.digits != original.digits ||
            draft.categoryId != original.categoryId ||
            draft.date != original.date ||
            (Note.of(draft.note) is Outcome.Failure || note != original.note)
    }

    private fun delete() {
        val id = editedExpense ?: return
        val original = progress.value.original ?: return
        val snapshot = (month.value as? SnapshotState.Ready)?.snapshot ?: return
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val error = when (corrections.delete(id, original.version, snapshot.generation)) {
                DeleteResult.Deleted -> return@launch progress.update {
                    it.copy(busy = false, closed = true)
                }

                DeleteResult.Gone -> EntryError.ExpenseGone

                DeleteResult.Changed -> EntryError.ExpenseChanged

                DeleteResult.DataChanged -> EntryError.DataChanged

                DeleteResult.Failed -> EntryError.NotDeleted
            }
            progress.update { it.copy(busy = false, error = error) }
        }
    }

    /** Amount, category or note: what a schedule's following payments can take on. */
    private fun termsChanged(draft: Draft, original: Original): Boolean {
        val note = (Note.of(draft.note) as? Outcome.Success)?.value?.value
        return (draft.amount as? AmountDraft.Digits)?.digits != original.digits ||
            draft.categoryId != original.categoryId || note != original.note
    }

    private fun submit() {
        val original = progress.value.original
        val following = progress.value.following
        // New terms on an expense a schedule recorded: Solo este or Este y los siguientes.
        if (editing && original != null && following != null &&
            saved.get<Boolean>(KEY_FOLLOWING) == null && termsChanged(draft.value, original)
        ) {
            return progress.update { it.copy(askFollowing = true) }
        }
        if (editing && saved.get<Boolean>(KEY_FOLLOWING) == true &&
            saved.get<Boolean>(KEY_FOLLOWING_SAVED) != true
        ) {
            return submitFollowing()
        }
        val draft = draft.value
        val snapshot = (month.value as? SnapshotState.Ready)?.snapshot ?: return
        val amount = (draft.amount.toTransactionAmount() as? Outcome.Success)?.value ?: return
        // The category as the form shows it chosen: one that is offered, or none.
        val chosen = state.value.categoryId
        val category = (chosen?.let(CategoryId::of) as? Outcome.Success)?.value ?: return
        val note = (Note.of(draft.note) as? Outcome.Success ?: return).value
        // The identifier is saved before the command leaves, so whatever happens next
        // this attempt can be recognised and never recorded twice.
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OPERATION] = operation.value
        progress.update { it.copy(busy = true, error = null) }
        val choice = repeat.choice
        if (choice != null && !editing) {
            val rule = choice.rule() ?: return progress.update { it.copy(busy = false) }
            saved[KEY_OPERATION_SCHEDULE] = true
            viewModelScope.launch {
                settleSchedule(
                    schedules.submit(
                        CreateSchedule(
                            operation,
                            snapshot.generation,
                            ScheduledEntry.Payment(category, amount, note),
                            rule,
                            draft.date,
                            choice.end
                        )
                    )
                )
            }
            return
        }
        val edited = editedExpense
        viewModelScope.launch {
            settle(
                if (edited != null && original != null) {
                    commands.edit(
                        EditExpense(
                            operation,
                            snapshot.generation,
                            edited,
                            original.version,
                            category,
                            amount,
                            draft.date,
                            note
                        )
                    )
                } else {
                    commands.create(
                        CreateExpense(
                            operation,
                            snapshot.generation,
                            category,
                            amount,
                            draft.date,
                            note
                        )
                    )
                }
            )
        }
    }

    private suspend fun settle(outcome: ExpenseCommandOutcome) {
        when (outcome) {
            is ExpenseCommandOutcome.Committed -> saved(outcome.expense.expenseId)

            is ExpenseCommandOutcome.OutcomeUnknown -> recover()

            is ExpenseCommandOutcome.Rejected -> {
                // Certain: nothing was written, so this attempt is over. The draft stays,
                // and Solo este or Este y los siguientes is asked again unless the
                // following payments already changed.
                saved[KEY_OPERATION] = null
                if (saved.get<Boolean>(KEY_FOLLOWING_SAVED) != true) saved[KEY_FOLLOWING] = null
                val error = when (outcome.reason) {
                    ExpenseRejection.CategoryMissing, ExpenseRejection.CategoryArchived -> {
                        setCategory(null)
                        EntryError.CategoryGone
                    }

                    is ExpenseRejection.FutureDate -> EntryError.FutureDate

                    ExpenseRejection.InvalidDate -> EntryError.InvalidDate

                    is ExpenseRejection.StaleGeneration -> EntryError.DataChanged

                    ExpenseRejection.ExpenseMissing -> EntryError.ExpenseGone

                    is ExpenseRejection.VersionConflict -> EntryError.ExpenseChanged

                    else -> EntryError.NotSaved
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /**
     * The schedule's new terms go first: if they are refused nothing at all was written.
     * The expense is corrected only once they are confirmed (see [followingSaved]).
     */
    private fun submitFollowing() {
        val following = progress.value.following ?: return
        val snapshot = (month.value as? SnapshotState.Ready)?.snapshot ?: return
        val draft = draft.value
        val amount = (draft.amount.toTransactionAmount() as? Outcome.Success)?.value ?: return
        val category = (state.value.categoryId?.let(CategoryId::of) as? Outcome.Success)?.value
            ?: return
        val note = (Note.of(draft.note) as? Outcome.Success ?: return).value
        val recurrence = following.schedule.recurrence
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[KEY_OPERATION] = operation.value
        saved[KEY_OPERATION_SCHEDULE] = true
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (
                val outcome = schedules.submit(
                    ChangeSchedule(
                        operation,
                        snapshot.generation,
                        following.schedule.id,
                        following.version,
                        ScheduledEntry.Payment(category, amount, note),
                        recurrence.rule,
                        recurrence.start,
                        recurrence.end
                    )
                )
            ) {
                is CommandOutcome.Committed -> followingSaved()

                is CommandOutcome.OutcomeUnknown -> recover()

                is CommandOutcome.Rejected -> {
                    saved[KEY_OPERATION] = null
                    saved[KEY_OPERATION_SCHEDULE] = null
                    saved[KEY_FOLLOWING] = null
                    progress.update {
                        it.copy(busy = false, error = EntryError.FollowingNotSaved)
                    }
                }
            }
        }
    }

    /** The following payments took the new terms; now this expense is corrected. */
    private fun followingSaved() {
        saved[KEY_OPERATION] = null
        saved[KEY_OPERATION_SCHEDULE] = null
        saved[KEY_FOLLOWING_SAVED] = true
        progress.update { it.copy(busy = false) }
        submit()
    }

    private suspend fun settleSchedule(outcome: ScheduleCommandOutcome) {
        when (outcome) {
            is CommandOutcome.Committed -> scheduleSaved(outcome.value.scheduleId)

            is CommandOutcome.OutcomeUnknown -> recover()

            is CommandOutcome.Rejected -> {
                // Certain: nothing was written. The draft and the choice stay.
                saved[KEY_OPERATION] = null
                saved[KEY_OPERATION_SCHEDULE] = null
                val error = when (val reason = outcome.reason) {
                    is CommandRejection.StaleGeneration -> EntryError.DataChanged

                    CommandRejection.OperationConflict,
                    is CommandRejection.VersionConflict,
                    is CommandRejection.StorageFailed -> EntryError.NotSaved

                    is CommandRejection.Specific -> when (reason.reason) {
                        ScheduleRejection.CategoryMissing, ScheduleRejection.CategoryArchived -> {
                            setCategory(null)
                            EntryError.CategoryGone
                        }

                        is ScheduleRejection.StartTooEarly -> EntryError.StartTooEarly

                        is ScheduleRejection.EndTooEarly -> EntryError.EndTooEarly

                        else -> EntryError.NotSaved
                    }
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    private suspend fun scheduleSaved(id: ScheduleId) {
        saved[KEY_SAVED_SCHEDULE] = id.value
        saved[KEY_OPERATION] = null
        saved[KEY_OPERATION_SCHEDULE] = null
        announceSchedule()
    }

    /**
     * Says what storage holds for the saved schedule, read back, then closes. A read that
     * fails still says it was saved: it was, and nothing more is sent.
     */
    private suspend fun announceSchedule() {
        val id = saved.get<String>(KEY_SAVED_SCHEDULE)
        progress.update { it.copy(busy = true, error = null, recorded = id) }
        val stored = (scheduleReads.book() as? Outcome.Success)?.value?.schedules
            ?.firstOrNull { it.schedule.id.value == id }?.schedule
        val read = month.first { it !is SnapshotState.Loading }
        val payment = stored?.entry as? ScheduledEntry.Payment
        val category = payment?.let {
            (read as? SnapshotState.Ready)?.snapshot?.categories
                ?.firstOrNull { line -> line.category.id == it.categoryId }?.category
        }
        corrections.scheduleSaved(
            CorrectionNotice.ScheduleSaved(
                name = payment?.note?.value ?: category?.name?.value,
                rule = stored?.recurrence?.rule,
                start = stored?.recurrence?.start,
                income = false
            )
        )
        progress.update { it.copy(busy = false, closed = true) }
    }

    /** Asks storage how the saved operation ended. Only a receipt counts as saved. */
    private suspend fun recover() {
        val operation = (saved.get<String>(KEY_OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value
        if (operation == null) {
            saved[KEY_OPERATION] = null
            saved[KEY_OPERATION_SCHEDULE] = null
            return progress.update { it.copy(busy = false) }
        }
        progress.update { it.copy(busy = true, error = null) }
        if (saved.get<Boolean>(KEY_OPERATION_SCHEDULE) == true) {
            return when (val found = schedules.find(operation)) {
                is Outcome.Failure -> progress.update {
                    it.copy(busy = false, error = EntryError.Unknown)
                }

                is Outcome.Success -> {
                    val committed = found.value
                    if (committed != null) {
                        if (editing) followingSaved() else scheduleSaved(committed.scheduleId)
                    } else {
                        saved[KEY_OPERATION] = null
                        saved[KEY_OPERATION_SCHEDULE] = null
                        progress.update { it.copy(busy = false, error = null) }
                    }
                }
            }
        }
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, error = EntryError.Unknown)
            }

            is Outcome.Success -> {
                val committed = found.value
                if (committed != null) {
                    saved(committed.expenseId)
                } else {
                    saved[KEY_OPERATION] = null
                    progress.update { it.copy(busy = false, error = null) }
                }
            }
        }
    }

    private suspend fun saved(expenseId: ExpenseId) {
        if (editing) {
            // A correction has no receipt of its own: the lists show the expense as it
            // is now, and the notice says the change was saved.
            saved[KEY_OPERATION] = null
            if (saved.get<Boolean>(KEY_FOLLOWING_SAVED) == true) {
                corrections.scheduleChanged(
                    CorrectionNotice.ScheduleChanged(ScheduleChange.Changed, income = false)
                )
            } else {
                corrections.updated()
            }
            return progress.update { it.copy(busy = false, error = null, closed = true) }
        }
        saved[KEY_SAVED_EXPENSE] = expenseId.value
        saved[KEY_OPERATION] = null
        announce(expenseId.value)
    }

    /**
     * Reads the stored expense back and says what storage holds, not the draft; then the
     * form closes. A read that fails still says it was saved: it was, and nothing more is
     * sent.
     */
    private suspend fun announce(expenseId: String) {
        progress.update { it.copy(busy = true, error = null, recorded = expenseId) }
        val id = (ExpenseId.of(expenseId) as? Outcome.Success)?.value
        val expense = (id?.let { reads.byId(it) } as? Outcome.Success)?.value
        // The category's name comes from the same read the form uses; wait for it once.
        val read = month.first { it !is SnapshotState.Loading }
        val category = expense?.let {
            (read as? SnapshotState.Ready)?.snapshot?.categories
                ?.firstOrNull { line -> line.category.id == it.categoryId }?.category
        }
        corrections.recorded(
            CorrectionNotice.ExpenseRecorded(
                amount = expense?.let { money.format(it.amount.money) },
                categoryName = category?.name?.value
            )
        )
        progress.update { it.copy(busy = false, closed = true) }
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        month: SnapshotState,
        today: LocalDate,
        writing: String?
    ): ExpenseEntryState {
        val snapshot = (month as? SnapshotState.Ready)?.snapshot
        val original = progress.original
        val categories = snapshot?.let { eligible(it, original?.categoryId) }.orEmpty()
            .map { EntryCategory(it.id.value, it.name.value, it.icon.value) }
        val amount = (draft.amount.toTransactionAmount() as? Outcome.Success)?.value
        val noteProblem = problem(draft.note)
        val noteDraft = writing?.takeIf { progress.recorded == null }
        val chosen = draft.categoryId?.takeIf { id -> categories.any { it.id == id } }
        val waitingForOriginal = editing && original == null && progress.error == null
        val currentMonth = YearMonth.from(today)
        val repeating = if (editing) RepeatUi() else repeat.ui(draft.date)
        return ExpenseEntryState(
            loading = progress.recorded == null && !progress.originalUnreadable &&
                (month is SnapshotState.Loading || waitingForOriginal),
            loadFailed = progress.recorded == null &&
                (month is SnapshotState.Failed || progress.originalUnreadable),
            amount = amount?.let { money.format(it.money) },
            amountText = (draft.amount as? AmountDraft.Rejected)?.original,
            amountProblem = (draft.amount as? AmountDraft.Rejected)?.let { problem(it.error) },
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            categories = categories,
            categoryId = chosen,
            date = draft.date,
            today = today,
            datePickerOpen = progress.datePickerOpen,
            note = draft.note,
            noteProblem = noteProblem,
            noteDraft = noteDraft,
            noteDraftProblem = noteDraft?.let(::problem),
            noteDraftLength = noteDraft?.trim()?.let { it.codePointCount(0, it.length) } ?: 0,
            missing = when {
                amount == null -> Missing.Amount
                chosen == null -> Missing.Category
                noteProblem != null -> Missing.Note
                repeating.choice != null && repeating.next == null -> Missing.Repeat
                original != null && !changed(draft, original) -> Missing.Changes
                else -> null
            },
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            recorded = progress.recorded,
            closed = progress.closed,
            editing = editing,
            repeat = repeating,
            askFollowing = progress.askFollowing,
            following = progress.following?.let { following ->
                val payment = following.schedule.entry as? ScheduledEntry.Payment
                FollowingSchedule(
                    name = payment?.note?.value ?: categories
                        .firstOrNull { it.id == payment?.categoryId?.value }?.name,
                    rule = following.schedule.recurrence.rule,
                    start = following.schedule.recurrence.start
                )
            },
            followingSaved = saved.get<Boolean>(KEY_FOLLOWING_SAVED) == true,
            pastMonths = original?.let {
                listOf(YearMonth.from(it.date), YearMonth.from(draft.date)).distinct()
                    .filter { month -> month != currentMonth }
            }.orEmpty()
        )
    }

    private fun problem(note: String): NoteProblem? = when (val checked = Note.of(note)) {
        is Outcome.Success -> null

        is Outcome.Failure -> when (checked.error) {
            is TextError.TooLong -> NoteProblem.TooLong
            else -> NoteProblem.NotAllowed
        }
    }

    private fun problem(error: AmountDraftError) = when (error) {
        is AmountDraftError.Range -> when (error.error) {
            TransactionAmountError.NotPositive -> AmountProblem.Zero
            TransactionAmountError.AboveMaximum -> AmountProblem.TooLarge
            is TransactionAmountError.UnsupportedCurrency -> AmountProblem.NotAnAmount
        }

        else -> AmountProblem.NotAnAmount
    }

    companion object {
        /** Route arguments: untrusted hints for the draft, checked like any other input. */
        const val KEY_PREFILL_CATEGORY = "categoryId"
        const val KEY_PREFILL_AMOUNT = "amount"
        const val KEY_PREFILL_REPEAT = "repeat"

        /** Route argument: the recorded expense to correct. Without it the form records a new one. */
        const val KEY_EDITED_EXPENSE = "expenseId"

        private const val KEY_AMOUNT = "expense.amount"
        private const val KEY_CATEGORY = "expense.category"
        private const val KEY_CATEGORY_DEFAULTED = "expense.categoryDefaulted"
        private const val KEY_DATE = "expense.date"
        private const val KEY_NOTE = "expense.note"
        private const val KEY_NOTE_DRAFT = "expense.note.draft"
        private const val KEY_OPERATION = "expense.operation"
        private const val KEY_SAVED_EXPENSE = "expense.saved"
        private const val KEY_OPERATION_SCHEDULE = "expense.operation.schedule"
        private const val KEY_SAVED_SCHEDULE = "expense.saved.schedule"
        private const val KEY_ORIGIN_SCHEDULE = "expense.origin.schedule"
        private const val KEY_REPEAT_OFFERED = "expense.repeat.offered"
        private const val KEY_FOLLOWING = "expense.following"
        private const val KEY_FOLLOWING_SAVED = "expense.following.saved"
        private const val KEY_ORIGINAL_VERSION = "expense.original.version"
        private const val KEY_ORIGINAL_AMOUNT = "expense.original.amount"
        private const val KEY_ORIGINAL_CATEGORY = "expense.original.category"
        private const val KEY_ORIGINAL_DATE = "expense.original.date"
        private const val KEY_ORIGINAL_NOTE = "expense.original.note"
    }
}
