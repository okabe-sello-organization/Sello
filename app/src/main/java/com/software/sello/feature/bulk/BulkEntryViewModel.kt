package com.software.sello.feature.bulk

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.EntryBatchPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedExpenses
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.ExpenseBatchCommands
import com.software.sello.domain.port.ExpenseBatchRejection
import com.software.sello.domain.port.ExpenseLine
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.LineRefusal
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SnapshotState
import com.software.sello.presentation.carry.CarryCorrectionText
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.submission.AmountDraftSerializer
import com.software.sello.presentation.submission.LocalDateSerializer
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
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
import kotlinx.serialization.Serializable

/**
 * Anotar varios for expenses: several lines typed on one receipt and saved through
 * [ExpenseBatchCommands] as one operation, every line or none.
 *
 * The lines, the highlighted line, the date and the operation of a save in progress are
 * kept in [saved], so rotation or the process being killed neither loses the receipt nor
 * sends it twice: an operation found pending on start is asked about, never sent again.
 * The receipt is announced once storage confirmed it, and the sheet then closes.
 *
 * Route arguments ([KEY_PREFILL_CATEGORY] and the others) are hints from where it was
 * opened, checked like any other input.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BulkEntryViewModel(
    private val saved: SavedStateHandle,
    private val commands: ExpenseBatchCommands,
    snapshots: MonthlySnapshots,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: Corrections
) : ViewModel() {
    private val correctionText = CarryCorrectionText(money)

    /** What came with the list of later months, sent with the receipt it was shown for (CP20). */
    private var confirmation: CorrectionConfirmation? = null

    /** The lines of the receipt last sent, by key, in the order storage numbers them. */
    private var sentKeys: List<Int> = emptyList()

    @Serializable
    private data class Line(
        val key: Int,
        @Serializable(with = AmountDraftSerializer::class) val amount: AmountDraft,
        val categoryId: String?,
        val note: String
    )

    @Serializable
    private data class Draft(
        val lines: List<Line>,
        val highlighted: Int,
        @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
        val nextKey: Int
    )

    /** A line taken off the draft and where it was, for Deshacer. */
    private data class Removed(val line: Line, val index: Int)

    /** Everything that is not the draft itself. */
    private data class Progress(
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        val datePickerOpen: Boolean = false,
        val busy: Boolean = false,
        val error: BulkError? = null,
        val confirmDiscard: Boolean = false,
        val removed: Removed? = null,
        /** Lines storage refused, by key, until they change. */
        val refused: Map<Int, LineProblem> = emptyMap(),
        val correction: BulkCorrection? = null,
        val recorded: Boolean = false,
        val closed: Boolean = false,
        val attempt: Int = 0
    )

    private val drafts = SavedDraft(saved, KEY_DRAFT, Draft.serializer())
    private val submission = Submission(saved, KEY_OPERATION, ids)

    private val draft = MutableStateFlow(drafts.read()?.let(::sane) ?: opening())
    private val progress = MutableStateFlow(Progress())

    /** The note being written in its composer; kept in [saved] so a restart reopens it. */
    private val noteDraft: StateFlow<String?> = saved.getStateFlow(KEY_NOTE_DRAFT, null)

    /** The month of the receipt's date: its categories and the generation to send. */
    private val month: StateFlow<SnapshotState> =
        combine(
            draft.map { YearMonth.from(it.date) }.distinctUntilChanged(),
            progress.map { it.attempt }.distinctUntilChanged()
        ) { month, _ -> month }
            .flatMapLatest(snapshots::observe)
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)

    val state: StateFlow<BulkEntryState> =
        combine(draft, progress, month, clock.today, noteDraft) {
                draft,
                progress,
                month,
                today,
                note
            ->
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
        // "Today" is fixed when the sheet opens, so a draft restored tomorrow keeps its date.
        if (drafts.read() == null) drafts.write(draft.value)
        // A receipt saved, or a save in flight, from before the process stopped.
        viewModelScope.launch {
            when {
                saved.get<Boolean>(KEY_SAVED) == true -> announce()
                submission.pending -> recover()
            }
        }
    }

    /**
     * The first draft: what was typed in Anotar becomes line one and a second line, in the
     * same category, waits for the next amount; otherwise one empty line, in the category
     * it was opened from, if any.
     */
    private fun opening(): Draft {
        val today = clock.today.value.date
        val category = saved.get<String>(KEY_PREFILL_CATEGORY)
        // A hint that is not valid is dropped, not shown as an error the person did not make.
        val amount = saved.get<String>(KEY_PREFILL_AMOUNT)?.let(AmountDraft::paste)
            ?.takeIf { it.toTransactionAmount() is Outcome.Success }
        val note = saved.get<String>(KEY_PREFILL_NOTE)?.takeIf { Note.of(it) is Outcome.Success }
        val date = saved.get<String>(KEY_PREFILL_DATE)?.let(::parseDate)
            ?.takeIf { !it.isAfter(today) } ?: today
        return if (amount != null) {
            Draft(
                listOf(
                    Line(0, amount, category, note.orEmpty()),
                    Line(1, AmountDraft.Empty, category, "")
                ),
                highlighted = 1,
                date = date,
                nextKey = 2
            )
        } else {
            Draft(listOf(Line(0, AmountDraft.Empty, category, note.orEmpty())), 0, date, 1)
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    /** A restored draft always has 1 to 30 lines and highlights one of them. */
    private fun sane(draft: Draft): Draft {
        val lines = draft.lines.take(EntryBatchPolicy.MAX_LINES)
        if (lines.isEmpty()) return opening()
        return draft.copy(
            lines = lines,
            highlighted = draft.highlighted.coerceIn(0, lines.lastIndex),
            nextKey = maxOf(draft.nextKey, lines.maxOf { it.key } + 1)
        )
    }

    private fun edit(change: (Draft) -> Draft) {
        draft.update(change)
        drafts.write(draft.value)
    }

    /** Changes the highlighted line; a refusal of it no longer holds once it changed. */
    private fun editLine(change: (Line) -> Line) {
        val now = draft.value
        val key = now.lines[now.highlighted].key
        edit {
            it.copy(
                lines = it.lines.mapIndexed { i, line ->
                    if (i ==
                        it.highlighted
                    ) {
                        change(line)
                    } else {
                        line
                    }
                }
            )
        }
        progress.update { it.copy(refused = it.refused - key) }
    }

    fun onAction(action: BulkEntryAction) {
        val now = state.value
        when (action) {
            is BulkEntryAction.Digit -> if (now.editable) {
                key(
                    highlightedAmount().digit(action.digit)
                )
            }

            BulkEntryAction.TripleZero -> if (now.editable) key(highlightedAmount().tripleZero())

            BulkEntryAction.Backspace -> if (now.editable) {
                editLine { it.copy(amount = it.amount.backspace()) }
                progress.update { it.copy(atLimit = false) }
            }

            BulkEntryAction.ClearAmount -> if (now.editable) {
                editLine { it.copy(amount = AmountDraft.Empty) }
                progress.update { it.copy(atLimit = false) }
            }

            is BulkEntryAction.CategoryPicked ->
                if (now.editable && now.categories.any { it.id == action.id }) {
                    editLine { it.copy(categoryId = action.id) }
                }

            is BulkEntryAction.Select -> if (now.editable && action.index in now.lines.indices) {
                edit { it.copy(highlighted = action.index) }
                progress.update { it.copy(atLimit = false) }
            }

            is BulkEntryAction.Remove -> if (now.editable) remove(action.index)

            BulkEntryAction.UndoRemove -> if (now.editable) undoRemove()

            BulkEntryAction.RemoveSeen -> progress.update { it.copy(removed = null) }

            BulkEntryAction.AddLine -> if (now.canAdd) {
                edit {
                    val above = it.lines.last()
                    it.copy(
                        lines = it.lines + Line(
                            it.nextKey,
                            AmountDraft.Empty,
                            above.categoryId,
                            ""
                        ),
                        highlighted = it.lines.size,
                        nextKey = it.nextKey + 1
                    )
                }
                // A full receipt has no room for the line just removed: Deshacer ends.
                val full = draft.value.lines.size >= EntryBatchPolicy.MAX_LINES
                progress.update {
                    it.copy(atLimit = false, removed = it.removed.takeUnless { full })
                }
            }

            BulkEntryAction.OpenDatePicker ->
                if (now.editable) progress.update { it.copy(datePickerOpen = true) }

            BulkEntryAction.CloseDatePicker -> progress.update { it.copy(datePickerOpen = false) }

            // A day after today cannot be picked. A refusal about the old date no longer holds.
            is BulkEntryAction.DatePicked -> if (now.editable) {
                if (!action.date.isAfter(now.today)) edit { it.copy(date = action.date) }
                progress.update {
                    it.copy(
                        datePickerOpen = false,
                        refused = emptyMap(),
                        error = it.error.takeUnless { error -> error in DateErrors }
                    )
                }
            }

            BulkEntryAction.OpenNote -> if (now.editable) {
                saved[KEY_NOTE_DRAFT] = draft.value.lines[draft.value.highlighted].note
            }

            is BulkEntryAction.NoteDraftChanged ->
                if (saved.get<String>(KEY_NOTE_DRAFT) != null) saved[KEY_NOTE_DRAFT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            BulkEntryAction.NoteDone -> {
                val written = saved.get<String>(KEY_NOTE_DRAFT)
                if (written != null && now.editable && Note.of(written) is Outcome.Success) {
                    editLine { it.copy(note = written) }
                    saved[KEY_NOTE_DRAFT] = null
                }
            }

            BulkEntryAction.NoteCancelled -> saved[KEY_NOTE_DRAFT] = null

            BulkEntryAction.Submit -> if (now.canSubmit) submit()

            BulkEntryAction.Retry -> when {
                now.busy -> Unit
                submission.pending -> viewModelScope.launch { recover() }
                else -> progress.update { it.copy(attempt = it.attempt + 1) }
            }

            BulkEntryAction.Back -> when {
                now.busy -> Unit
                now.recorded -> progress.update { it.copy(closed = true) }
                now.typedLines > 0 -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            BulkEntryAction.DiscardConfirmed ->
                progress.update { it.copy(confirmDiscard = false, closed = true) }

            BulkEntryAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }

            BulkEntryAction.CorrectionConfirmed -> if (now.correction != null && now.editable) {
                progress.update { it.copy(correction = null) }
                submit()
            }

            BulkEntryAction.CorrectionDismissed -> if (!now.busy) {
                confirmation = null
                progress.update { it.copy(correction = null) }
            }
        }
    }

    private fun highlightedAmount() = draft.value.lines[draft.value.highlighted].amount

    private fun key(change: AmountDraft.Change) {
        editLine { it.copy(amount = change.draft) }
        progress.update {
            if (change.limitReached) {
                it.copy(limitPulse = it.limitPulse + 1, atLimit = true)
            } else {
                it.copy(atLimit = false)
            }
        }
    }

    /** The last line stays: a receipt always has one. */
    private fun remove(index: Int) {
        val now = draft.value
        if (now.lines.size <= 1 || index !in now.lines.indices) return
        val line = now.lines[index]
        val lines = now.lines - line
        val highlighted = when {
            index < now.highlighted -> now.highlighted - 1
            index == now.highlighted -> minOf(index, lines.lastIndex)
            else -> now.highlighted
        }
        edit { it.copy(lines = lines, highlighted = highlighted) }
        progress.update {
            it.copy(
                removed = Removed(line, index),
                refused = it.refused - line.key,
                atLimit = false
            )
        }
    }

    private fun undoRemove() {
        val removed = progress.value.removed ?: return
        val now = draft.value
        if (now.lines.size >= EntryBatchPolicy.MAX_LINES) return
        val at = removed.index.coerceIn(0, now.lines.size)
        edit {
            it.copy(
                lines = it.lines.toMutableList().apply { add(at, removed.line) },
                highlighted = at
            )
        }
        progress.update { it.copy(removed = null) }
    }

    private fun submit() {
        val snapshot = (month.value as? ReadState.Ready)?.value ?: return
        val now = draft.value
        val offered = eligible(snapshot).map { it.id }.toSet()
        // Only lines with an amount are sent; each in a category the receipt offers.
        val sent = now.lines.mapNotNull { line ->
            val amount = (line.amount.toTransactionAmount() as? Outcome.Success)?.value
                ?: return@mapNotNull null
            val category = (line.categoryId?.let(CategoryId::of) as? Outcome.Success)?.value
                ?.takeIf { it in offered } ?: return
            val note = Note.of(line.note) as? Outcome.Success ?: return
            line.key to ExpenseLine(category, amount, note.value)
        }
        if (sent.isEmpty()) return
        sentKeys = sent.map { it.first }
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val settled = submission.send(commands::find) { operation ->
                commands.create(
                    CreateExpenses(
                        operation,
                        snapshot.generation,
                        now.date,
                        sent.map { it.second },
                        confirmation
                    )
                )
            }
            settle(settled)
        }
    }

    private suspend fun settle(settled: Settled<CommittedExpenses, ExpenseBatchRejection>?) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) progress.update { it.copy(busy = false) }

            // Only a receipt counts as saved.
            is Settled.Committed -> {
                confirmation = null
                saved[KEY_SAVED] = true
                announce()
            }

            is Settled.Unknown -> progress.update {
                it.copy(busy = false, error = BulkError.Unknown)
            }

            Settled.NotCommitted -> progress.update { it.copy(busy = false, error = null) }

            // Certain: nothing was written. The draft stays.
            is Settled.Rejected -> when (val reason = settled.reason) {
                is CommandRejection.StaleGeneration -> failed(BulkError.DataChanged)

                CommandRejection.OperationConflict,
                is CommandRejection.VersionConflict,
                is CommandRejection.StorageFailed -> failed(BulkError.NotSaved)

                is CommandRejection.Specific -> refused(reason.reason)
            }
        }
    }

    private fun failed(error: BulkError) = progress.update { it.copy(busy = false, error = error) }

    private fun refused(reason: ExpenseBatchRejection) {
        when (reason) {
            is ExpenseBatchRejection.LineCount -> failed(BulkError.NotSaved)

            is ExpenseBatchRejection.Lines -> {
                val reasons = reason.refused.map { it.reason }
                when {
                    reasons.any { it is LineRefusal.FutureDate } -> failed(BulkError.FutureDate)

                    reasons.any { it == LineRefusal.InvalidDate } -> failed(BulkError.InvalidDate)

                    else -> {
                        // Each refused line by its place among the lines that were sent.
                        val marked = reason.refused.mapNotNull { line ->
                            val key = sentKeys.getOrNull(line.position) ?: return@mapNotNull null
                            val problem = when (line.reason) {
                                LineRefusal.CategoryArchived -> LineProblem.CategoryArchived
                                LineRefusal.CategoryMissing -> LineProblem.CategoryMissing
                                else -> LineProblem.MonthFull
                            }
                            key to problem
                        }.toMap()
                        progress.update {
                            it.copy(busy = false, error = BulkError.NotSaved, refused = marked)
                        }
                    }
                }
            }

            // It reaches a closed month of a carry chain: the list first, then the same
            // receipt with what came with it (CP20, SC34).
            is ExpenseBatchRejection.CorrectionUnconfirmed -> {
                val changed = confirmation != null
                confirmation = reason.confirmation
                val names = snapshotOf(month.value)?.categories.orEmpty()
                    .associate { it.category.id to it.category }
                progress.update {
                    it.copy(
                        busy = false,
                        correction = BulkCorrection(
                            correctionText.list(reason.correction, names),
                            changed
                        )
                    )
                }
            }

            is ExpenseBatchRejection.CarryUnavailable -> failed(BulkError.CarryUnavailable)
        }
    }

    /** Asks storage how the saved operation ended, with its own ID. Only a receipt counts. */
    private suspend fun recover() {
        progress.update { it.copy(busy = true, error = null) }
        settle(submission.recover(commands::find))
    }

    /**
     * Says what was saved, then closes. The draft cannot change while a save is pending,
     * so its lines with an amount are exactly the receipt storage confirmed.
     */
    private fun announce() {
        progress.update { it.copy(busy = true, error = null, recorded = true) }
        val amounts = amounts(draft.value)
        corrections.expensesRecorded(
            CorrectionNotice.ExpensesRecorded(amounts.size, money.format(total(amounts)))
        )
        progress.update { it.copy(busy = false, closed = true) }
    }

    private fun amounts(draft: Draft): List<TransactionAmount> =
        draft.lines.mapNotNull { (it.amount.toTransactionAmount() as? Outcome.Success)?.value }

    /** 30 lines of at most 999.999.999.999 pesos each always fit; the sum is still checked. */
    private fun total(amounts: List<TransactionAmount>) =
        when (val sum = ExactTotal.of(Currency.COP, amounts.map { it.money }).toMoney()) {
            is Outcome.Success -> sum.value
            is Outcome.Failure -> error("A receipt's total cannot be held: ${sum.error}")
        }

    /** The categories a line can be put in: those not archived. */
    private fun eligible(snapshot: MonthlySnapshot) =
        snapshot.categories.map { it.category }.filter { !it.archived }

    private fun snapshotOf(read: SnapshotState): MonthlySnapshot? = when (read) {
        is ReadState.Ready -> read.value
        is ReadState.Failed -> read.lastGood
        ReadState.Loading -> null
    }

    private fun render(
        draft: Draft,
        progress: Progress,
        month: SnapshotState,
        today: LocalDate,
        writing: String?
    ): BulkEntryState {
        val snapshot = snapshotOf(month)
        val categories = snapshot?.let(::eligible).orEmpty()
            .map { BulkCategory(it.id.value, it.name.value, it.icon.value) }
        val known = snapshot != null
        val lines = draft.lines.map { line ->
            val amount = (line.amount.toTransactionAmount() as? Outcome.Success)?.value
            val category = categories.firstOrNull { it.id == line.categoryId }
            BulkLineUi(
                key = line.key.toString(),
                category = category,
                amount = amount?.let { money.format(it.money) },
                note = line.note,
                problem = progress.refused[line.key]
                    ?: LineProblem.NoCategory.takeIf { known && amount != null && category == null }
            )
        }
        val amounts = amounts(draft)
        val noteDraft = writing?.takeIf { !progress.recorded }
        val checkedNote = noteDraft?.let(Note::of) as? Outcome.Failure
        return BulkEntryState(
            lines = lines,
            highlighted = draft.highlighted,
            date = draft.date,
            today = today,
            loading = !progress.recorded && !known && month is ReadState.Loading,
            readFailed = !progress.recorded && month is ReadState.Failed,
            categories = categories,
            total = money.format(total(amounts)),
            count = amounts.size,
            missingCategories = lines.count { it.problem == LineProblem.NoCategory },
            refusedLines = lines.count { progress.refused.containsKey(it.key.toInt()) },
            atLimit = progress.atLimit,
            limitPulse = progress.limitPulse,
            datePickerOpen = progress.datePickerOpen,
            noteDraft = noteDraft,
            noteDraftTooLong = checkedNote?.error is TextError.TooLong,
            noteDraftNotAllowed = checkedNote != null && checkedNote.error !is TextError.TooLong,
            noteDraftLength = noteDraft?.trim()?.let { it.codePointCount(0, it.length) } ?: 0,
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            removed = progress.removed?.let { removed ->
                RemovedLine(categories.firstOrNull { it.id == removed.line.categoryId }?.name)
            },
            correction = progress.correction,
            recorded = progress.recorded,
            closed = progress.closed
        )
    }

    companion object {
        /** Route arguments: untrusted hints for the first draft, checked like any other input. */
        const val KEY_PREFILL_CATEGORY = "categoryId"
        const val KEY_PREFILL_AMOUNT = "amount"
        const val KEY_PREFILL_NOTE = "note"
        const val KEY_PREFILL_DATE = "date"

        private const val KEY_DRAFT = "bulk.expense.draft"
        private const val KEY_NOTE_DRAFT = "bulk.expense.note.draft"
        private const val KEY_OPERATION = "bulk.expense.operation"
        private const val KEY_SAVED = "bulk.expense.saved"

        private val DateErrors = setOf(BulkError.FutureDate, BulkError.InvalidDate)
    }
}
