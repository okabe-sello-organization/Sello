package com.software.sello.feature.recurrence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmountError
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.AmountDraftError
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.DeleteSchedule
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ResumeSchedule
import com.software.sello.domain.port.ScheduleCommand
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.AmountProblem
import com.software.sello.feature.expense.EntryCategory
import com.software.sello.feature.expense.NoteProblem
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.ScheduleChange
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatDraft
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Changes, pauses, resumes or deletes one schedule through [schedules]
 * (docs/design/v2 Pagos e ingresos fijos, recurrence-examples rules 7, 8 and 10). The
 * schedule is read once; the draft, the version it was read at and any command in
 * flight are kept in [saved], so a restart neither loses the draft nor sends twice, and
 * a change is only ever sent against the version this screen showed. Nothing recorded
 * changes: new terms apply from tomorrow, and deleting keeps every recorded entry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorViewModel(
    private val saved: SavedStateHandle,
    private val schedules: ScheduleCommands,
    private val reads: ScheduleReads,
    snapshots: MonthlySnapshots,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: ExpenseCorrections
) : ViewModel() {
    private data class Progress(
        val stored: StoredSchedule? = null,
        val loadFailed: Boolean = false,
        val busy: Boolean = false,
        val error: ScheduleEditorError? = null,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        val confirmDelete: Boolean = false,
        val confirmDiscard: Boolean = false,
        val closed: Boolean = false,
        val attempt: Int = 0
    )

    private val id: ScheduleId? =
        (saved.get<String>(KEY_SCHEDULE)?.let(ScheduleId::of) as? Outcome.Success)?.value
    private val repeat = RepeatDraft(saved, "schedule")
    private val progress = MutableStateFlow(Progress())

    /** Every saved draft field, so any change is noticed. */
    private val draft = combine(
        saved.getStateFlow<String?>(AMOUNT, null),
        saved.getStateFlow<String?>(CATEGORY, null),
        saved.getStateFlow<String?>(SOURCE, null),
        saved.getStateFlow<String?>(OTHER, null),
        saved.getStateFlow<String?>(NOTE, null)
    ) { _, _, _, _, _ -> Unit }

    private val writing = combine(
        saved.getStateFlow<String?>(COMPOSER, null),
        saved.getStateFlow<String?>(COMPOSER_TEXT, null),
        saved.getStateFlow<String?>(START, null),
        saved.getStateFlow(START_PICKER, false),
        repeat.changes
    ) { _, _, _, _, _ -> Unit }

    private val month = combine(
        clock.today.map { YearMonth.from(it.date) }.distinctUntilChanged(),
        progress.map { it.attempt }.distinctUntilChanged()
    ) { month, _ -> month }.flatMapLatest(snapshots::observe)
        .stateIn(viewModelScope, SharingStarted.Eagerly, SnapshotState.Loading)

    val state: StateFlow<ScheduleEditorState> =
        combine(progress, month, clock.today, draft, writing) { progress, month, today, _, _ ->
            render(progress, month, today.date)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(progress.value, month.value, clock.today.value.date)
        )

    init {
        viewModelScope.launch {
            if (saved.get<String>(OPERATION) != null) recover() else load()
        }
    }

    /**
     * Reads the schedule. The first time, the draft starts as what is stored; a restored
     * draft is kept, and refused if the schedule changed since it was started.
     */
    private suspend fun load() {
        val id = id ?: return progress.update { it.copy(error = ScheduleEditorError.ScheduleGone) }
        when (val book = reads.book()) {
            is Outcome.Failure -> progress.update { it.copy(loadFailed = true) }

            is Outcome.Success -> {
                val stored = book.value.schedules.firstOrNull { it.schedule.id == id }
                    ?: return progress.update {
                        it.copy(loadFailed = false, error = ScheduleEditorError.ScheduleGone)
                    }
                val version = saved.get<Long>(VERSION)
                if (version == null) {
                    start(stored)
                } else if (version != stored.version) {
                    return progress.update {
                        it.copy(
                            stored = stored,
                            loadFailed = false,
                            error = ScheduleEditorError.ScheduleChanged
                        )
                    }
                }
                progress.update { it.copy(stored = stored, loadFailed = false) }
            }
        }
    }

    private fun start(stored: StoredSchedule) {
        val entry = stored.schedule.entry
        saved[VERSION] = stored.version
        saved[AMOUNT] = entry.amount.money.minorUnits.toString()
        saved[NOTE] = entry.note?.value.orEmpty()
        when (entry) {
            is ScheduledEntry.Payment -> saved[CATEGORY] = entry.categoryId.value

            is ScheduledEntry.Income -> {
                saved[SOURCE] = entry.source.key
                saved[OTHER] = (entry.source as? IncomeSource.Other)?.name?.value.orEmpty()
            }
        }
        repeat.keep(
            RepeatChoice.of(stored.schedule.recurrence.rule, stored.schedule.recurrence.end)
        )
    }

    fun onAction(action: ScheduleEditorAction) {
        val now = state.value
        when (action) {
            is ScheduleEditorAction.Digit ->
                if (now.editable) key(amountDraft().digit(action.digit))

            ScheduleEditorAction.TripleZero -> if (now.editable) key(amountDraft().tripleZero())

            ScheduleEditorAction.Backspace -> if (now.editable) setAmount(amountDraft().backspace())

            ScheduleEditorAction.ClearAmount -> if (now.editable) setAmount(AmountDraft.Empty)

            is ScheduleEditorAction.Paste ->
                if (now.editable) setAmount(AmountDraft.paste(action.text))

            is ScheduleEditorAction.CategoryPicked ->
                if (now.editable && now.categories.any { it.id == action.id }) {
                    saved[CATEGORY] = action.id
                    progress.update { it.copy(error = null) }
                }

            is ScheduleEditorAction.SourcePicked ->
                if (now.editable && now.income && action.key in IncomeSource.keys) {
                    saved[SOURCE] = action.key
                }

            is ScheduleEditorAction.OpenComposer -> if (now.editable) {
                saved[COMPOSER_TEXT] = when (action.what) {
                    ScheduleComposer.Note -> saved[NOTE] ?: ""
                    ScheduleComposer.SourceName -> saved[OTHER] ?: ""
                }
                saved[COMPOSER] = action.what.name
            }

            is ScheduleEditorAction.ComposerChanged ->
                if (saved.get<String>(COMPOSER) != null) saved[COMPOSER_TEXT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            ScheduleEditorAction.ComposerDone -> {
                val what = composer() ?: return
                val text = saved.get<String>(COMPOSER_TEXT).orEmpty()
                if (now.editable && composerProblem(what, text) == null) {
                    saved[if (what == ScheduleComposer.Note) NOTE else OTHER] = text
                    closeComposer()
                }
            }

            ScheduleEditorAction.ComposerCancelled -> closeComposer()

            ScheduleEditorAction.OpenStartPicker -> if (now.editable) saved[START_PICKER] = true

            ScheduleEditorAction.CloseStartPicker -> saved[START_PICKER] = false

            // New terms start after today (rule 7); an earlier day is not taken.
            is ScheduleEditorAction.StartPicked -> {
                if (now.editable && action.date.isAfter(now.today)) {
                    saved[START] = action.date.toString()
                }
                saved[START_PICKER] = false
            }

            is ScheduleEditorAction.Repeat -> if (now.editable) {
                repeat.onAction(action.action, now.start ?: now.today)
                progress.update { it.copy(error = null) }
            }

            ScheduleEditorAction.Save -> if (now.canSave) change(now)

            ScheduleEditorAction.Pause -> if (now.editable &&
                !now.paused
            ) {
                send(ScheduleChange.Paused)
            }

            ScheduleEditorAction.Resume -> if (now.editable &&
                now.paused
            ) {
                send(ScheduleChange.Resumed)
            }

            ScheduleEditorAction.Delete ->
                if (now.editable) progress.update { it.copy(confirmDelete = true) }

            ScheduleEditorAction.DeleteConfirmed -> if (now.editable && now.confirmDelete) {
                progress.update { it.copy(confirmDelete = false) }
                send(ScheduleChange.Deleted)
            }

            ScheduleEditorAction.KeepIt -> progress.update { it.copy(confirmDelete = false) }

            ScheduleEditorAction.Retry -> when {
                now.busy -> Unit

                saved.get<String>(OPERATION) != null -> viewModelScope.launch { recover() }

                else -> {
                    progress.update { it.copy(attempt = it.attempt + 1) }
                    viewModelScope.launch { load() }
                }
            }

            ScheduleEditorAction.Back -> when {
                now.busy || saved.get<String>(OPERATION) != null -> Unit

                now.editable && now.missing != ScheduleMissing.Changes ->
                    progress.update { it.copy(confirmDiscard = true) }

                else -> progress.update { it.copy(closed = true) }
            }

            ScheduleEditorAction.DiscardConfirmed ->
                progress.update { it.copy(confirmDiscard = false, closed = true) }

            ScheduleEditorAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }
        }
    }

    private fun amountDraft(): AmountDraft =
        saved.get<String>(AMOUNT)?.let(AmountDraft::paste) ?: AmountDraft.Empty

    private fun setAmount(amount: AmountDraft) {
        saved[AMOUNT] = when (amount) {
            AmountDraft.Empty -> null
            is AmountDraft.Digits -> amount.digits
            is AmountDraft.Rejected -> amount.original
        }
        progress.update { it.copy(atLimit = false) }
    }

    private fun key(change: AmountDraft.Change) {
        setAmount(change.draft)
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1, atLimit = true) }
        }
    }

    private fun composer(): ScheduleComposer? =
        ScheduleComposer.entries.firstOrNull { it.name == saved.get<String>(COMPOSER) }

    private fun closeComposer() {
        saved[COMPOSER] = null
        saved[COMPOSER_TEXT] = null
    }

    private fun composerProblem(what: ScheduleComposer, text: String): NoteProblem? {
        val error = when (what) {
            ScheduleComposer.Note -> (Note.of(text) as? Outcome.Failure)?.error

            ScheduleComposer.SourceName -> (SourceName.of(text) as? Outcome.Failure)?.error
                ?.takeIf { it != TextError.Required }
        }
        return error?.let(::problem)
    }

    private fun problem(error: TextError) = when (error) {
        is TextError.TooLong -> NoteProblem.TooLong
        else -> NoteProblem.NotAllowed
    }

    /** The entry the draft describes, or null while something in it is not valid. */
    private fun entry(stored: StoredSchedule): ScheduledEntry? {
        val amount = (amountDraft().toTransactionAmount() as? Outcome.Success)?.value ?: return null
        val note = (Note.of(saved[NOTE] ?: "") as? Outcome.Success)?.value
        if (Note.of(saved[NOTE] ?: "") is Outcome.Failure) return null
        return when (stored.schedule.entry) {
            is ScheduledEntry.Payment -> {
                val category =
                    (saved.get<String>(CATEGORY)?.let(CategoryId::of) as? Outcome.Success)?.value
                        ?: return null
                ScheduledEntry.Payment(category, amount, note)
            }

            is ScheduledEntry.Income -> {
                val key = saved.get<String>(SOURCE) ?: return null
                val source = (
                    IncomeSource.of(
                        key,
                        saved.get<String>(OTHER).takeIf {
                            key ==
                                IncomeSource.Other.KEY
                        }
                    )
                        as? Outcome.Success
                    )?.value ?: return null
                ScheduledEntry.Income(source, amount, note)
            }
        }
    }

    /**
     * The start a change is sent with: the stored one while neither the rule nor the
     * start changes; else the day picked, or tomorrow, since new terms start after today.
     */
    private fun start(stored: StoredSchedule, today: LocalDate): LocalDate {
        val picked = (saved.get<String>(START)?.let(EffectiveDates::parseDate) as? Outcome.Success)
            ?.value
        if (picked != null) return picked
        val rule = repeat.choice?.rule()
        val recurrence = stored.schedule.recurrence
        return if (rule != null && rule != recurrence.rule && !recurrence.start.isAfter(today)) {
            today.plusDays(1)
        } else {
            recurrence.start
        }
    }

    private fun change(now: ScheduleEditorState) {
        val stored = progress.value.stored ?: return
        val snapshot = (month.value as? SnapshotState.Ready)?.snapshot ?: return
        val entry = entry(stored) ?: return
        val rule = repeat.choice?.rule() ?: return
        val start = start(stored, now.today)
        submit(
            ScheduleChange.Changed,
            ChangeSchedule(
                newOperation(ScheduleChange.Changed),
                snapshot.generation,
                stored.schedule.id,
                stored.version,
                entry,
                rule,
                start,
                repeat.choice?.end
            )
        )
    }

    private fun send(change: ScheduleChange) {
        val stored = progress.value.stored ?: return
        val snapshot = (month.value as? SnapshotState.Ready)?.snapshot ?: return
        val operation = newOperation(change)
        val id = stored.schedule.id
        val generation = snapshot.generation
        submit(
            change,
            when (change) {
                ScheduleChange.Paused -> PauseSchedule(operation, generation, id, stored.version)
                ScheduleChange.Resumed -> ResumeSchedule(operation, generation, id, stored.version)
                else -> DeleteSchedule(operation, generation, id, stored.version)
            }
        )
    }

    /** The identifier is saved before the command leaves, so this attempt is never sent twice. */
    private fun newOperation(change: ScheduleChange): OperationId {
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[OPERATION_KIND] = change.name
        return operation
    }

    private fun submit(change: ScheduleChange, command: ScheduleCommand) {
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val outcome = schedules.submit(command)) {
                is ScheduleCommandOutcome.Committed -> done(change)

                is ScheduleCommandOutcome.OutcomeUnknown -> recover()

                is ScheduleCommandOutcome.Rejected -> {
                    saved[OPERATION] = null
                    saved[OPERATION_KIND] = null
                    progress.update { it.copy(busy = false, error = error(outcome.reason)) }
                }
            }
        }
    }

    private fun error(reason: ScheduleRejection) = when (reason) {
        is ScheduleRejection.StaleGeneration -> ScheduleEditorError.DataChanged

        ScheduleRejection.ScheduleMissing -> ScheduleEditorError.ScheduleGone

        is ScheduleRejection.VersionConflict -> ScheduleEditorError.ScheduleChanged

        ScheduleRejection.CatchUpPending -> ScheduleEditorError.CatchUpPending

        ScheduleRejection.CategoryMissing, ScheduleRejection.CategoryArchived ->
            ScheduleEditorError.CategoryGone

        is ScheduleRejection.StartTooEarly -> ScheduleEditorError.StartTooEarly

        is ScheduleRejection.EndTooEarly -> ScheduleEditorError.EndTooEarly

        else -> ScheduleEditorError.NotSaved
    }

    /** Asks storage how the saved operation ended. Only a receipt counts as done. */
    private suspend fun recover() {
        val operation = (saved.get<String>(OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value
        val change = ScheduleChange.entries.firstOrNull {
            it.name ==
                saved.get<String>(OPERATION_KIND)
        }
        if (operation == null || change == null) {
            saved[OPERATION] = null
            saved[OPERATION_KIND] = null
            return load()
        }
        progress.update { it.copy(busy = true, error = null) }
        when (val found = schedules.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, error = ScheduleEditorError.Unknown)
            }

            is Outcome.Success -> if (found.value != null) {
                done(change)
            } else {
                saved[OPERATION] = null
                saved[OPERATION_KIND] = null
                progress.update { it.copy(busy = false) }
                if (progress.value.stored == null) load()
            }
        }
    }

    private fun done(change: ScheduleChange) {
        saved[OPERATION] = null
        saved[OPERATION_KIND] = null
        val income = saved.get<String>(SOURCE) != null
        corrections.scheduleChanged(CorrectionNotice.ScheduleChanged(change, income))
        progress.update { it.copy(busy = false, closed = true) }
    }

    private fun render(
        progress: Progress,
        month: SnapshotState,
        today: LocalDate
    ): ScheduleEditorState {
        val stored = progress.stored
        val snapshot = (month as? SnapshotState.Ready)?.snapshot
        val payment = stored?.schedule?.entry as? ScheduledEntry.Payment
        val categories = snapshot?.categories.orEmpty()
            .map { it.category }
            .filter { !it.archived || it.id == payment?.categoryId }
        val amount = amountDraft()
        val valid = (amount.toTransactionAmount() as? Outcome.Success)?.value
        val note = saved.get<String>(NOTE).orEmpty()
        val noteProblem = (Note.of(note) as? Outcome.Failure)?.error?.let(::problem)
        val composer = composer()
        val composerText = saved.get<String>(COMPOSER_TEXT).orEmpty()
        val start = stored?.let { start(it, today) }
        val line = stored?.let {
            ScheduleListPolicy.lines(listOf(it), today, YearMonth.from(today))
        }
            ?.single()
        val archived = payment?.let { own -> categories.firstOrNull { it.id == own.categoryId } }
            ?.takeIf { it.archived }
        val choice = repeat.choice
        val entry = stored?.let(::entry)
        val changed = stored != null && entry != null && choice?.rule() != null && (
            entry != stored.schedule.entry ||
                choice.rule() != stored.schedule.recurrence.rule ||
                choice.end != stored.schedule.recurrence.end ||
                start != stored.schedule.recurrence.start
            )
        val repeatUi = repeat.ui(maxOf(start ?: today, today))
        return ScheduleEditorState(
            loading = !progress.loadFailed && progress.error == null &&
                (stored == null || month is SnapshotState.Loading),
            loadFailed = progress.loadFailed || month is SnapshotState.Failed,
            income = stored?.schedule?.entry is ScheduledEntry.Income,
            amount = valid?.let { money.format(it.money) },
            amountText = (amount as? AmountDraft.Rejected)?.original,
            amountProblem = (amount as? AmountDraft.Rejected)?.error?.let(::amountProblem),
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            categories = categories.map {
                EntryCategory(it.id.value, it.name.value, it.icon.value)
            },
            categoryId = saved.get<String>(CATEGORY)?.takeIf { id ->
                categories.any {
                    it.id.value ==
                        id
                }
            },
            sourceKeys = IncomeSource.keys,
            sourceKey = saved.get<String>(SOURCE).orEmpty(),
            otherName = saved.get<String>(OTHER).orEmpty(),
            note = note,
            noteProblem = noteProblem,
            composer = composer,
            composerDraft = composerText,
            composerProblem = composer?.let { composerProblem(it, composerText) },
            start = start,
            today = today,
            startPickerOpen = saved.get<Boolean>(START_PICKER) == true,
            repeat = repeatUi,
            pausedFrom = line?.pausedFrom,
            pausedByArchive = archived?.name?.value.takeIf { line?.pausedFrom != null },
            next = line?.next,
            missing = when {
                stored == null -> ScheduleMissing.Changes

                valid == null -> ScheduleMissing.Amount

                payment != null && saved.get<String>(CATEGORY)
                    ?.let { id -> categories.any { it.id.value == id && !it.archived } } != true ->
                    ScheduleMissing.Category

                stored.schedule.entry is ScheduledEntry.Income && entry == null &&
                    noteProblem == null -> ScheduleMissing.SourceName

                noteProblem != null -> ScheduleMissing.Note

                choice?.rule() == null || repeatUi.next == null -> ScheduleMissing.Repeat

                !changed -> ScheduleMissing.Changes

                else -> null
            },
            busy = progress.busy,
            error = progress.error,
            confirmDelete = progress.confirmDelete,
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun amountProblem(error: AmountDraftError) = when (error) {
        is AmountDraftError.Range -> when (error.error) {
            TransactionAmountError.NotPositive -> AmountProblem.Zero
            TransactionAmountError.AboveMaximum -> AmountProblem.TooLarge
            is TransactionAmountError.UnsupportedCurrency -> AmountProblem.NotAnAmount
        }

        else -> AmountProblem.NotAnAmount
    }

    companion object {
        /** Route argument: the schedule to change. */
        const val KEY_SCHEDULE = "scheduleId"

        private const val VERSION = "schedule.version"
        private const val AMOUNT = "schedule.amount"
        private const val CATEGORY = "schedule.category"
        private const val SOURCE = "schedule.source"
        private const val OTHER = "schedule.other"
        private const val NOTE = "schedule.note"
        private const val START = "schedule.start"
        private const val START_PICKER = "schedule.startPicker"
        private const val COMPOSER = "schedule.composer"
        private const val COMPOSER_TEXT = "schedule.composer.text"
        private const val OPERATION = "schedule.operation"
        private const val OPERATION_KIND = "schedule.operation.kind"
    }
}
