package com.software.sello.feature.income

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.IncomeSourceError
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
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.IncomeRejection
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.ScheduleRejection
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Records one income, or corrects a recorded one, through the real commands. With Se
 * repite on, a new income becomes a schedule instead: [schedules] keeps it and catch-up
 * records each one on its date. Draft, choice and attempt are kept in [saved].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncomeFormViewModel(
    private val saved: SavedStateHandle,
    private val commands: IncomeCommands,
    private val reads: IncomeReads,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: Corrections,
    private val schedules: ScheduleCommands,
    private val scheduleReads: ScheduleReads
) : ViewModel() {
    private data class Draft(
        val amount: AmountDraft,
        val source: String,
        val other: String,
        val date: LocalDate,
        val note: String
    )

    private data class Original(val version: Long, val draft: Draft)

    private data class Progress(
        val original: Original? = null,
        val loadFailed: Boolean = false,
        val busy: Boolean = false,
        val generationChanged: Boolean = false,
        val error: IncomeFormError? = null,
        val recorded: String? = null,
        val closed: Boolean = false,
        val confirmDiscard: Boolean = false,
        val datePickerOpen: Boolean = false,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        val attempt: Int = 0,
        /** The schedule that recorded the income being corrected, while it still exists. */
        val following: StoredSchedule? = null,
        /** Saving waits for Solo este or Este y los siguientes. */
        val askFollowing: Boolean = false
    )

    private val editing = saved.get<String>(KEY_EDITED_INCOME) != null
    private val repeat = RepeatDraft(saved, "income")
    private val edited = saved.get<String>(KEY_EDITED_INCOME)?.let {
        (IncomeId.of(it) as? Outcome.Success)?.value
    }
    private val draft = MutableStateFlow(
        Draft(
            saved.get<String>(AMOUNT)?.let(AmountDraft::paste) ?: AmountDraft.Empty,
            saved[SOURCE] ?: "salary",
            saved[OTHER] ?: "",
            saved.get<String>(DATE)?.let(LocalDate::parse) ?: clock.today.value.date,
            saved[NOTE] ?: ""
        )
    )
    private val progress = MutableStateFlow(
        Progress(
            original = saved.get<Long>(VERSION)?.let {
                Original(
                    it,
                    Draft(
                        AmountDraft.paste(requireNotNull(saved[ORIGINAL_AMOUNT])),
                        requireNotNull(saved[ORIGINAL_SOURCE]),
                        saved[ORIGINAL_OTHER] ?: "",
                        LocalDate.parse(requireNotNull(saved[ORIGINAL_DATE])),
                        saved[ORIGINAL_NOTE] ?: ""
                    )
                )
            }
        )
    )
    private val month = combine(
        draft.map { YearMonth.from(it.date) }.distinctUntilChanged(),
        progress.map { it.attempt }.distinctUntilChanged()
    ) { month, _ -> month }.flatMapLatest(reads::observeMonth)
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)

    /** What the composer is writing and its text; kept in [saved] so a restart reopens it. */
    private val composer: StateFlow<Pair<IncomeComposer, String>?> = combine(
        saved.getStateFlow<String?>(COMPOSER, null),
        saved.getStateFlow(COMPOSER_TEXT, "")
    ) { what: String?, text: String -> writing(what, text) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, composerNow())

    private fun composerNow(): Pair<IncomeComposer, String>? =
        writing(saved[COMPOSER], saved[COMPOSER_TEXT] ?: "")

    private fun writing(what: String?, text: String): Pair<IncomeComposer, String>? =
        IncomeComposer.entries.firstOrNull { it.name == what }?.let { it to text }

    val state: StateFlow<IncomeFormState> = combine(
        draft,
        progress,
        month,
        clock.today,
        // Se repite is read from saved state when rendering; this only says it changed.
        combine(composer, repeat.changes) { writing, _ -> writing }
    ) { draft, progress, month, today, writing ->
        render(draft, progress, month, today.date, writing)
    }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(
                draft.value,
                progress.value,
                month.value,
                clock.today.value.date,
                composerNow()
            )
        )

    init {
        saved[DATE] = draft.value.date.toString()
        // Opened from Pagos e ingresos fijos: Se repite's sheet opens once, on today.
        if (saved.get<Boolean>(KEY_PREFILL_REPEAT) == true &&
            saved.get<Boolean>(REPEAT_OFFERED) != true && !editing
        ) {
            saved[REPEAT_OFFERED] = true
            repeat.onAction(RepeatAction.Open, draft.value.date)
        }
        viewModelScope.launch {
            month.collect { state ->
                if (state is ReadState.Ready) {
                    val generation = saved.get<Long>(GENERATION)
                    if (generation == null) {
                        saved[GENERATION] = state.value.generation
                    } else if (generation != state.value.generation) {
                        progress.update {
                            it.copy(error = IncomeFormError.DataChanged, generationChanged = true)
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            when {
                saved.get<String>(SAVED) != null -> announce(requireNotNull(saved[SAVED]))
                saved.get<String>(SAVED_SCHEDULE) != null -> announceSchedule()
                saved.get<String>(OPERATION) != null -> recover()
                editing && progress.value.original == null -> loadOriginal()
            }
            if (editing && saved.get<String>(ORIGIN_SCHEDULE) != null) loadFollowing()
        }
    }

    private fun change(next: Draft) {
        // Any key that changes the amount takes "Máximo 12 dígitos" away.
        if (next.amount != draft.value.amount) progress.update { it.copy(atLimit = false) }
        saved[AMOUNT] = when (val amount = next.amount) {
            AmountDraft.Empty -> null
            is AmountDraft.Digits -> amount.digits
            is AmountDraft.Rejected -> amount.original
        }
        saved[SOURCE] = next.source
        saved[OTHER] = next.other
        saved[DATE] = next.date.toString()
        saved[NOTE] = next.note
        draft.value = next
    }

    fun onAction(action: IncomeFormAction) {
        val now = state.value
        when (action) {
            is IncomeFormAction.Digit -> if (now.editable) {
                key(
                    draft.value.amount.digit(action.digit)
                )
            }

            IncomeFormAction.TripleZero -> if (now.editable) key(draft.value.amount.tripleZero())

            IncomeFormAction.Backspace -> if (now.editable) {
                change(
                    draft.value.copy(amount = draft.value.amount.backspace())
                )
            }

            IncomeFormAction.ClearAmount -> if (now.editable) {
                change(
                    draft.value.copy(amount = AmountDraft.Empty)
                )
            }

            is IncomeFormAction.Paste -> if (now.editable) {
                change(
                    draft.value.copy(amount = AmountDraft.paste(action.text))
                )
            }

            is IncomeFormAction.SourcePicked -> if (now.editable &&
                action.key in IncomeSource.keys
            ) {
                change(draft.value.copy(source = action.key))
            }

            is IncomeFormAction.OtherNameChanged -> if (now.editable) {
                change(
                    draft.value.copy(other = action.text)
                )
            }

            is IncomeFormAction.NoteChanged -> if (now.editable) {
                change(
                    draft.value.copy(note = action.text)
                )
            }

            IncomeFormAction.OpenDatePicker -> if (now.editable) {
                progress.update {
                    it.copy(datePickerOpen = true)
                }
            }

            IncomeFormAction.CloseDatePicker -> progress.update { it.copy(datePickerOpen = false) }

            is IncomeFormAction.DatePicked -> if (now.editable) {
                // While Se repite is on, today or later: a schedule starts no earlier.
                val fits = if (repeat.choice != null) {
                    !action.date.isBefore(now.today)
                } else {
                    !action.date.isAfter(now.today)
                }
                if (fits) change(draft.value.copy(date = action.date))
                progress.update { it.copy(datePickerOpen = false, error = null) }
            }

            IncomeFormAction.Submit -> if (now.canSubmit && now.editable) submit()

            IncomeFormAction.Retry -> if (!now.busy) {
                viewModelScope.launch {
                    if (saved.get<String>(OPERATION) != null) {
                        recover()
                    } else {
                        if (progress.value.loadFailed) loadOriginal()
                        progress.update { it.copy(attempt = it.attempt + 1) }
                    }
                }
            }

            IncomeFormAction.Back -> when {
                now.busy || saved.get<String>(OPERATION) != null -> Unit
                now.recorded != null -> progress.update { it.copy(closed = true) }
                typed() -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            IncomeFormAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }

            IncomeFormAction.DiscardConfirmed -> if (!now.busy &&
                saved.get<String>(OPERATION) == null
            ) {
                progress.update {
                    it.copy(confirmDiscard = false, closed = true)
                }
            }

            IncomeFormAction.Delete -> if (now.canDelete) delete()

            is IncomeFormAction.OpenComposer -> if (now.editable) {
                saved[COMPOSER_TEXT] = when (action.what) {
                    IncomeComposer.Note -> draft.value.note
                    IncomeComposer.SourceName -> draft.value.other
                }
                saved[COMPOSER] = action.what.name
            }

            is IncomeFormAction.ComposerChanged ->
                if (saved.get<String>(COMPOSER) != null) saved[COMPOSER_TEXT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            IncomeFormAction.ComposerDone -> composer.value?.let { (what, text) ->
                if (now.editable && composerProblem(what, text) == null) {
                    change(
                        when (what) {
                            IncomeComposer.Note -> draft.value.copy(note = text)
                            IncomeComposer.SourceName -> draft.value.copy(other = text)
                        }
                    )
                    closeComposer()
                }
            }

            IncomeFormAction.ComposerCancelled -> closeComposer()

            is IncomeFormAction.FollowingChosen -> if (now.askFollowing && now.editable) {
                saved[FOLLOWING] = action.alsoFollowing
                progress.update { it.copy(askFollowing = false) }
                submit()
            }

            IncomeFormAction.FollowingDismissed -> progress.update { it.copy(askFollowing = false) }

            is IncomeFormAction.Repeat -> if (now.editable && !editing) {
                repeat.onAction(action.action, draft.value.date)
                // A schedule starts today or later; a single income is dated today or earlier.
                val date = draft.value.date
                val on = repeat.choice != null
                if ((on && date.isBefore(now.today)) || (!on && date.isAfter(now.today))) {
                    change(draft.value.copy(date = now.today))
                }
                progress.update { it.copy(error = null) }
            }
        }
    }

    private fun key(change: AmountDraft.Change) {
        change(draft.value.copy(amount = change.draft))
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1, atLimit = true) }
        }
    }

    private fun source(draft: Draft) = IncomeSource.of(
        draft.source,
        draft.other.takeIf { draft.source == "other" }
    )

    private fun changed(draft: Draft, original: Original): Boolean =
        draft.amount != original.draft.amount || draft.date != original.draft.date ||
            source(draft) != source(original.draft) ||
            Note.of(draft.note) != Note.of(original.draft.note)

    private fun typed() = progress.value.original?.let { changed(draft.value, it) }
        ?: (
            draft.value.amount != AmountDraft.Empty || draft.value.note.isNotBlank() ||
                draft.value.other.isNotBlank() || repeat.choice != null
            )

    private suspend fun loadOriginal() {
        val id = edited ?: return progress.update { it.copy(error = IncomeFormError.IncomeGone) }
        when (val result = reads.byId(id)) {
            is Outcome.Failure -> progress.update { it.copy(loadFailed = true) }

            is Outcome.Success -> {
                val income =
                    result.value
                        ?: return progress.update { it.copy(error = IncomeFormError.IncomeGone) }
                val original = Draft(
                    AmountDraft.paste(income.amount.money.minorUnits.toString()),
                    income.source.key,
                    (income.source as? IncomeSource.Other)?.name?.value.orEmpty(),
                    income.date,
                    income.note?.value.orEmpty()
                )
                saved[VERSION] = income.version
                saved[ORIGINAL_AMOUNT] = income.amount.money.minorUnits.toString()
                saved[ORIGINAL_SOURCE] = original.source
                saved[ORIGINAL_OTHER] = original.other
                saved[ORIGINAL_DATE] = original.date.toString()
                saved[ORIGINAL_NOTE] = original.note
                saved[ORIGIN_SCHEDULE] = income.origin?.schedule?.value
                change(original)
                progress.update {
                    it.copy(original = Original(income.version, original), loadFailed = false)
                }
                if (income.origin != null) loadFollowing()
            }
        }
    }

    /**
     * Reads the schedule that recorded the income being corrected. While it exists,
     * saving new terms asks whether they also apply to the incomes still to come. A read
     * that fails asks nothing: correcting this one income is always possible.
     */
    private suspend fun loadFollowing() {
        val id = saved.get<String>(ORIGIN_SCHEDULE) ?: return
        val book = (scheduleReads.book() as? Outcome.Success)?.value ?: return
        progress.update { progress ->
            progress.copy(following = book.schedules.firstOrNull { it.schedule.id.value == id })
        }
    }

    /** Amount, source or note: what a schedule's following incomes can take on. */
    private fun termsChanged(draft: Draft, original: Original): Boolean =
        draft.amount != original.draft.amount || source(draft) != source(original.draft) ||
            Note.of(draft.note) != Note.of(original.draft.note)

    private fun submit() {
        val original = progress.value.original
        // New terms on an income a schedule recorded: Solo este or Este y los siguientes.
        if (editing && original != null && progress.value.following != null &&
            saved.get<Boolean>(FOLLOWING) == null && termsChanged(draft.value, original)
        ) {
            return progress.update { it.copy(askFollowing = true) }
        }
        if (editing && saved.get<Boolean>(FOLLOWING) == true &&
            saved.get<Boolean>(FOLLOWING_SAVED) != true
        ) {
            return submitFollowing()
        }
        val current = draft.value
        val generation = saved.get<Long>(GENERATION) ?: return
        val amount = (current.amount.toTransactionAmount() as? Outcome.Success)?.value ?: return
        val source = (source(current) as? Outcome.Success)?.value ?: return
        val note = (Note.of(current.note) as? Outcome.Success ?: return).value
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[DELETING] = false
        progress.update { it.copy(busy = true, error = null) }
        val choice = repeat.choice
        if (choice != null && !editing) {
            val rule = choice.rule() ?: return progress.update { it.copy(busy = false) }
            saved[OPERATION_SCHEDULE] = true
            viewModelScope.launch {
                settleSchedule(
                    schedules.submit(
                        CreateSchedule(
                            operation,
                            generation,
                            ScheduledEntry.Income(source, amount, note),
                            rule,
                            current.date,
                            choice.end
                        )
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            settle(
                if (editing && original != null && edited != null) {
                    commands.edit(
                        EditIncome(
                            operation,
                            generation,
                            edited,
                            original.version,
                            amount,
                            source,
                            current.date,
                            note
                        )
                    )
                } else {
                    commands.create(
                        CreateIncome(operation, generation, amount, source, current.date, note)
                    )
                }
            )
        }
    }

    private suspend fun settle(outcome: IncomeCommandOutcome) {
        when (outcome) {
            is CommandOutcome.Committed -> committed(outcome.value.incomeId)

            is CommandOutcome.OutcomeUnknown -> recover()

            is CommandOutcome.Rejected -> {
                saved[OPERATION] = null
                // Asked again unless the following incomes already changed.
                if (saved.get<Boolean>(FOLLOWING_SAVED) != true) saved[FOLLOWING] = null
                val error = when (val reason = outcome.reason) {
                    is CommandRejection.StaleGeneration -> IncomeFormError.DataChanged

                    is CommandRejection.VersionConflict -> IncomeFormError.IncomeChanged

                    CommandRejection.OperationConflict,
                    is CommandRejection.StorageFailed -> IncomeFormError.NotSaved

                    is CommandRejection.Specific -> when (reason.reason) {
                        is IncomeRejection.FutureDate -> IncomeFormError.FutureDate
                        IncomeRejection.InvalidDate -> IncomeFormError.InvalidDate
                        IncomeRejection.IncomeMissing -> IncomeFormError.IncomeGone
                        else -> IncomeFormError.NotSaved
                    }
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /**
     * The schedule's new terms go first: if they are refused nothing at all was written.
     * The income is corrected only once they are confirmed.
     */
    private fun submitFollowing() {
        val following = progress.value.following ?: return
        val generation = saved.get<Long>(GENERATION) ?: return
        val current = draft.value
        val amount = (current.amount.toTransactionAmount() as? Outcome.Success)?.value ?: return
        val source = (source(current) as? Outcome.Success)?.value ?: return
        val note = (Note.of(current.note) as? Outcome.Success ?: return).value
        val recurrence = following.schedule.recurrence
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[OPERATION_SCHEDULE] = true
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (
                val outcome = schedules.submit(
                    ChangeSchedule(
                        operation,
                        generation,
                        following.schedule.id,
                        following.version,
                        ScheduledEntry.Income(source, amount, note),
                        recurrence.rule,
                        recurrence.start,
                        recurrence.end
                    )
                )
            ) {
                is CommandOutcome.Committed -> followingSaved()

                is CommandOutcome.OutcomeUnknown -> recover()

                is CommandOutcome.Rejected -> {
                    saved[OPERATION] = null
                    saved[OPERATION_SCHEDULE] = null
                    saved[FOLLOWING] = null
                    progress.update {
                        it.copy(busy = false, error = IncomeFormError.FollowingNotSaved)
                    }
                }
            }
        }
    }

    /** The following incomes took the new terms; now this income is corrected. */
    private fun followingSaved() {
        saved[OPERATION] = null
        saved[OPERATION_SCHEDULE] = null
        saved[FOLLOWING_SAVED] = true
        progress.update { it.copy(busy = false) }
        submit()
    }

    private suspend fun settleSchedule(outcome: ScheduleCommandOutcome) {
        when (outcome) {
            is CommandOutcome.Committed -> scheduleSaved(outcome.value.scheduleId)

            is CommandOutcome.OutcomeUnknown -> recover()

            is CommandOutcome.Rejected -> {
                saved[OPERATION] = null
                saved[OPERATION_SCHEDULE] = null
                val error = when (val reason = outcome.reason) {
                    is CommandRejection.StaleGeneration -> IncomeFormError.DataChanged

                    CommandRejection.OperationConflict,
                    is CommandRejection.VersionConflict,
                    is CommandRejection.StorageFailed -> IncomeFormError.NotSaved

                    is CommandRejection.Specific -> when (reason.reason) {
                        is ScheduleRejection.StartTooEarly -> IncomeFormError.StartTooEarly
                        is ScheduleRejection.EndTooEarly -> IncomeFormError.EndTooEarly
                        else -> IncomeFormError.NotSaved
                    }
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    private suspend fun scheduleSaved(id: ScheduleId) {
        saved[SAVED_SCHEDULE] = id.value
        saved[OPERATION] = null
        saved[OPERATION_SCHEDULE] = null
        announceSchedule()
    }

    /** Says what storage holds for the saved schedule, read back, then closes. */
    private suspend fun announceSchedule() {
        val id = saved.get<String>(SAVED_SCHEDULE)
        progress.update { it.copy(busy = true, error = null, recorded = id) }
        val stored = (scheduleReads.book() as? Outcome.Success)?.value?.schedules
            ?.firstOrNull { it.schedule.id.value == id }?.schedule
        val income = stored?.entry as? ScheduledEntry.Income
        corrections.scheduleSaved(
            CorrectionNotice.ScheduleSaved(
                name = income?.note?.value ?: income?.source?.let { it as? IncomeSource.Other }
                    ?.name?.value,
                rule = stored?.recurrence?.rule,
                start = stored?.recurrence?.start,
                income = true,
                sourceKey = income?.source?.key
            )
        )
        progress.update { it.copy(busy = false, closed = true) }
    }

    private suspend fun recover() {
        val operation =
            (saved.get<String>(OPERATION)?.let(OperationId::of) as? Outcome.Success)?.value
                ?: return
        progress.update { it.copy(busy = true, error = null) }
        if (saved.get<Boolean>(OPERATION_SCHEDULE) == true) {
            return when (val found = schedules.find(operation)) {
                is Outcome.Failure -> progress.update {
                    it.copy(busy = false, error = IncomeFormError.Unknown)
                }

                is Outcome.Success -> {
                    val committed = found.value
                    if (committed != null) {
                        if (editing) followingSaved() else scheduleSaved(committed.scheduleId)
                    } else {
                        saved[OPERATION] = null
                        saved[OPERATION_SCHEDULE] = null
                        progress.update { it.copy(busy = false, error = null) }
                    }
                }
            }
        }
        when (val result = commands.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, error = IncomeFormError.Unknown)
            }

            is Outcome.Success -> if (result.value != null) {
                val committed = requireNotNull(result.value)
                if (saved.get<Boolean>(DELETING) == true) {
                    corrections.incomeDeleted(committed.receipt)
                    saved[OPERATION] = null
                    progress.update { it.copy(busy = false, error = null, closed = true) }
                } else {
                    committed(committed.incomeId)
                }
            } else {
                saved[OPERATION] = null
                progress.update { it.copy(busy = false, error = null) }
            }
        }
    }

    private suspend fun committed(id: IncomeId) {
        if (editing) {
            saved[OPERATION] = null
            if (saved.get<Boolean>(FOLLOWING_SAVED) == true) {
                corrections.scheduleChanged(
                    CorrectionNotice.ScheduleChanged(ScheduleChange.Changed, income = true)
                )
            } else {
                corrections.incomeUpdated()
            }
            progress.update { it.copy(busy = false, error = null, closed = true) }
        } else {
            saved[SAVED] = id.value
            saved[OPERATION] = null
            announce(id.value)
        }
    }

    /**
     * Reads the stored income back and says what storage holds, not the draft; then the
     * form closes. A read that fails still says it was saved: it was, and nothing more is
     * sent.
     */
    private suspend fun announce(id: String) {
        progress.update { it.copy(busy = true, error = null, recorded = id) }
        val parsed = (IncomeId.of(id) as? Outcome.Success)?.value
        val income = (parsed?.let { reads.byId(it) } as? Outcome.Success)?.value
        corrections.incomeRecorded(
            CorrectionNotice.IncomeRecorded(income?.let { money.format(it.amount.money) })
        )
        progress.update { it.copy(busy = false, closed = true) }
    }

    private fun delete() {
        val id = edited ?: return
        val original = progress.value.original ?: return
        val generation = saved.get<Long>(GENERATION) ?: return
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[DELETING] = true
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (
                val result = corrections.deleteIncome(
                    DeleteIncome(operation, generation, id, original.version)
                )
            ) {
                is CommandOutcome.Committed -> {
                    saved[OPERATION] = null
                    progress.update { it.copy(busy = false, closed = true) }
                }

                is CommandOutcome.OutcomeUnknown -> recover()

                is CommandOutcome.Rejected -> {
                    saved[OPERATION] = null
                    val error = when (result.reason) {
                        Specific(IncomeRejection.IncomeMissing) -> IncomeFormError.IncomeGone
                        is CommandRejection.VersionConflict -> IncomeFormError.IncomeChanged
                        is CommandRejection.StaleGeneration -> IncomeFormError.DataChanged
                        else -> IncomeFormError.NotDeleted
                    }
                    progress.update { it.copy(busy = false, error = error) }
                }
            }
        }
    }

    private fun closeComposer() {
        saved[COMPOSER] = null
        saved[COMPOSER_TEXT] = null
    }

    /** An empty name is allowed here: "Otro" then still says it needs one. */
    private fun composerProblem(what: IncomeComposer, text: String): IncomeTextProblem? {
        val error = when (what) {
            IncomeComposer.Note -> (Note.of(text) as? Outcome.Failure)?.error

            IncomeComposer.SourceName -> (SourceName.of(text) as? Outcome.Failure)?.error
                ?.takeIf { it != TextError.Required }
        }
        return error?.let(::textProblem)
    }

    private fun textProblem(error: TextError): IncomeTextProblem = when (error) {
        is TextError.TooLong -> IncomeTextProblem.TooLong
        else -> IncomeTextProblem.NotAllowed
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        month: IncomeMonthState,
        today: LocalDate,
        writing: Pair<IncomeComposer, String>?
    ): IncomeFormState {
        val amount = (draft.amount.toTransactionAmount() as? Outcome.Success)?.value
        val source = source(draft)
        val otherProblem = (source as? Outcome.Failure)?.error?.let {
            (it as? IncomeSourceError.InvalidOtherName)?.reason?.let(
                ::textProblem
            )
        }
        val noteProblem = (Note.of(draft.note) as? Outcome.Failure)?.error?.let(::textProblem)
        val waiting = editing && progress.original == null && progress.error == null
        val repeating = if (editing) RepeatUi() else repeat.ui(draft.date)
        return IncomeFormState(
            loading =
                progress.recorded == null && !progress.loadFailed &&
                    (month is ReadState.Loading || waiting),
            loadFailed =
                progress.recorded == null &&
                    (month is ReadState.Failed || progress.loadFailed),
            amount = amount?.let { money.format(it.money) },
            amountText = (draft.amount as? AmountDraft.Rejected)?.original,
            amountProblem = if (draft.amount is AmountDraft.Rejected) {
                when (val result = draft.amount.toTransactionAmount()) {
                    is Outcome.Failure -> when ((result.error as? AmountDraftError.Range)?.error) {
                        TransactionAmountError.NotPositive -> IncomeAmountProblem.Zero
                        TransactionAmountError.AboveMaximum -> IncomeAmountProblem.TooLarge
                        else -> IncomeAmountProblem.NotAnAmount
                    }

                    else -> IncomeAmountProblem.NotAnAmount
                }
            } else {
                null
            },
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            sourceKeys = IncomeSource.keys,
            sourceKey = draft.source,
            otherName = draft.other,
            otherNameProblem = otherProblem,
            date = draft.date,
            today = today,
            datePickerOpen = progress.datePickerOpen,
            note = draft.note,
            noteProblem = noteProblem,
            composer = writing?.first?.takeIf { progress.recorded == null },
            composerDraft = writing?.second.orEmpty(),
            composerProblem = writing?.let { (what, text) -> composerProblem(what, text) },
            composerLength = writing?.second?.trim()?.let { it.codePointCount(0, it.length) } ?: 0,
            missing = when {
                amount == null -> IncomeMissing.Amount

                source is Outcome.Failure -> IncomeMissing.SourceName

                noteProblem != null -> IncomeMissing.Note

                repeating.choice != null && repeating.next == null -> IncomeMissing.Repeat

                progress.original != null && !changed(
                    draft,
                    progress.original
                ) -> IncomeMissing.Changes

                else -> null
            },
            busy = progress.busy,
            generationChanged = progress.generationChanged,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            recorded = progress.recorded,
            closed = progress.closed,
            editing = editing,
            repeat = repeating,
            askFollowing = progress.askFollowing,
            following = progress.following?.let { following ->
                val income = following.schedule.entry as? ScheduledEntry.Income
                FollowingSchedule(
                    name = income?.note?.value
                        ?: (income?.source as? IncomeSource.Other)?.name?.value,
                    rule = following.schedule.recurrence.rule,
                    start = following.schedule.recurrence.start,
                    sourceKey = income?.source?.key
                )
            },
            followingSaved = saved.get<Boolean>(FOLLOWING_SAVED) == true,
            pastMonths = progress.original?.let {
                listOf(YearMonth.from(it.draft.date), YearMonth.from(draft.date)).distinct()
                    .filter { month -> month != YearMonth.from(today) }
            }.orEmpty()
        )
    }

    companion object {
        /** Route argument: the recorded income to correct; absent for a new income. */
        const val KEY_EDITED_INCOME = "incomeId"

        /** Route argument: open with Se repite's sheet. */
        const val KEY_PREFILL_REPEAT = "repeat"
        private const val AMOUNT = "income.amount"
        private const val SOURCE = "income.source"
        private const val OTHER = "income.other"
        private const val DATE = "income.date"
        private const val NOTE = "income.note"
        private const val COMPOSER = "income.composer"
        private const val COMPOSER_TEXT = "income.composer.text"
        private const val OPERATION = "income.operation"
        private const val DELETING = "income.deleting"
        private const val GENERATION = "income.generation"
        private const val SAVED = "income.saved"
        private const val OPERATION_SCHEDULE = "income.operation.schedule"
        private const val SAVED_SCHEDULE = "income.saved.schedule"
        private const val ORIGIN_SCHEDULE = "income.origin.schedule"
        private const val REPEAT_OFFERED = "income.repeat.offered"
        private const val FOLLOWING = "income.following"
        private const val FOLLOWING_SAVED = "income.following.saved"
        private const val VERSION = "income.original.version"
        private const val ORIGINAL_AMOUNT = "income.original.amount"
        private const val ORIGINAL_SOURCE = "income.original.source"
        private const val ORIGINAL_OTHER = "income.original.other"
        private const val ORIGINAL_DATE = "income.original.date"
        private const val ORIGINAL_NOTE = "income.original.note"
    }
}
