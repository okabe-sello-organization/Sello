package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.BULK_RECEIPT_MAX_LINES
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalMovementLine
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ReceiptGoal
import com.software.sello.domain.port.RecordGoalMovements
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.movement.SavedNoteComposer
import com.software.sello.presentation.read.LastGoodRead
import com.software.sello.presentation.submission.AmountDraftSerializer
import com.software.sello.presentation.submission.LocalDateSerializer
import com.software.sello.presentation.submission.SavedDraft
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Aportar varios: up to 30 aportes and retiros, each line naming its active goal and
 * carrying its date, sent as one [RecordGoalMovements] that records all of them or none,
 * at the version read of every goal it names. The draft (lines, the highlighted one, an
 * open note) and the operation in flight live in [saved]. A refused retiro is marked on its
 * line with what its goal had available and the draft stays; an unknown outcome is asked
 * about with its own identifier before anything else is sent. A commit closes the screen
 * with one confirmation that celebrates, one after the other, each goal the receipt earned
 * something for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalBulkViewModel(
    private val saved: SavedStateHandle,
    reads: GoalReads,
    private val commands: GoalCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: Corrections
) : ViewModel() {
    @Serializable
    private data class LineDraft(
        val key: String,
        /** Null until a goal is chosen. */
        val goal: String?,
        @Serializable(with = AmountDraftSerializer::class) val amount: AmountDraft,
        val withdrawal: Boolean?,
        @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
        val note: String
    )

    @Serializable
    private data class Draft(val lines: List<LineDraft>, val highlighted: Int, val nextKey: Int)

    /** A line as drafts stored it before each line named its goal (before SELLO-141). */
    @Serializable
    private data class OneGoalLine(
        val key: String,
        @Serializable(with = AmountDraftSerializer::class) val amount: AmountDraft,
        val withdrawal: Boolean?,
        @Serializable(with = LocalDateSerializer::class) val date: LocalDate,
        val note: String
    )

    @Serializable
    private data class OneGoalDraft(
        val lines: List<OneGoalLine>,
        val highlighted: Int,
        val nextKey: Int
    )

    /** Why storage refused a line, kept by the line's key until the draft changes. */
    private sealed interface Refusal {
        data class AboveSaved(val available: Money) : Refusal

        data object FutureDate : Refusal

        data object InvalidDate : Refusal
    }

    private data class Removed(val line: LineDraft, val index: Int)

    private data class Progress(
        val busy: Boolean = false,
        val error: GoalBulkError? = null,
        val closed: Boolean = false,
        val confirmDiscard: Boolean = false,
        val datePickerOpen: Boolean = false,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        val refusals: Map<String, Refusal> = emptyMap(),
        val removed: Removed? = null
    )

    /** The goal it was opened from, line one's goal; null when opened from the Metas list. */
    private val goalId: String? = saved[KEY_GOAL]

    private val drafts = SavedDraft(saved, DRAFT, Draft.serializer())

    /** Drafts are read in order, so one stored before goals were per line has its own key. */
    private val oneGoalDrafts = SavedDraft(saved, ONE_GOAL_DRAFT, OneGoalDraft.serializer())
    private val submission = Submission(saved, OPERATION, ids)

    private val draft =
        MutableStateFlow((drafts.read() ?: oneGoalDraft())?.takeIf(::wellFormed) ?: opening())
    private val progress = MutableStateFlow(Progress())
    private val retries = MutableStateFlow(0)

    /** The board last read: a refresh that fails, or a Reintentar, keeps it, marked older. */
    private val lastGood = LastGoodRead<GoalBoard, StorageFailure>()
    private val board = retries.flatMapLatest { lastGood.keep(Unit, reads.observeBoard()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)
    private val composer = SavedNoteComposer(saved, PREFIX, viewModelScope)
    private var removal: Job? = null

    val state: StateFlow<GoalBulkState> =
        combine(draft, progress, board, clock.today, composer.text) {
                draft,
                progress,
                board,
                today,
                writing
            ->
            render(draft, progress, board, today.date, writing)
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            render(draft.value, progress.value, board.value, clock.today.value.date, composer.now())
        )

    init {
        if (submission.pending) viewModelScope.launch { recover() }
    }

    fun onAction(action: GoalBulkAction) {
        val now = state.value
        val editable = now.editable
        when (action) {
            is GoalBulkAction.GoalPicked ->
                if (editable && now.goals.any { it.id == action.id }) {
                    line { it.copy(goal = action.id) }
                }

            is GoalBulkAction.Kind ->
                if (editable) line { it.copy(withdrawal = action.withdrawal) }

            is GoalBulkAction.Digit -> if (editable) key(current().amount.digit(action.digit))

            GoalBulkAction.TripleZero -> if (editable) key(current().amount.tripleZero())

            GoalBulkAction.Backspace ->
                if (editable) line { it.copy(amount = it.amount.backspace()) }

            GoalBulkAction.ClearAmount -> if (editable) line { it.copy(amount = AmountDraft.Empty) }

            is GoalBulkAction.Select -> if (editable && action.index in draft.value.lines.indices) {
                progress.update { it.copy(atLimit = false) }
                write(draft.value.copy(highlighted = action.index))
            }

            GoalBulkAction.AddLine -> if (now.canAdd) add()

            is GoalBulkAction.Remove -> if (editable) remove(action.index)

            GoalBulkAction.UndoRemove -> if (editable) undoRemove()

            GoalBulkAction.OpenDatePicker ->
                if (editable) progress.update { it.copy(datePickerOpen = true) }

            GoalBulkAction.CloseDatePicker -> progress.update { it.copy(datePickerOpen = false) }

            is GoalBulkAction.DatePicked -> {
                if (editable && GoalPolicy.acceptsDate(action.date, now.today)) {
                    line { it.copy(date = action.date) }
                }
                progress.update { it.copy(datePickerOpen = false) }
            }

            GoalBulkAction.OpenComposer -> if (editable) composer.open(current().note)

            is GoalBulkAction.ComposerChanged -> composer.write(action.text)

            // Over the limit nothing is taken: the composer stays open with the text in it.
            GoalBulkAction.ComposerDone -> composer.text.value?.let { text ->
                if (editable && SavedNoteComposer.problem(text) == null) {
                    line { it.copy(note = text) }
                    composer.close()
                }
            }

            GoalBulkAction.ComposerCancelled -> composer.close()

            GoalBulkAction.Submit -> if (now.canSubmit) submit()

            GoalBulkAction.Retry -> when {
                now.busy -> Unit
                submission.pending -> viewModelScope.launch { recover() }
                now.loadFailed || now.stale -> retries.update { it + 1 }
                else -> progress.update { it.copy(error = null) }
            }

            GoalBulkAction.Back -> when {
                now.busy || submission.pending -> Unit
                now.typed > 0 -> progress.update { it.copy(confirmDiscard = true) }
                else -> close()
            }

            GoalBulkAction.DiscardConfirmed -> close()

            GoalBulkAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }
        }
    }

    /**
     * The first draft, in the goal it was opened from, if any: what was typed in Aportar or
     * Retirar becomes line one and a second line, of the same goal, type and date, waits for
     * the next amount; otherwise one line, of the type it was opened from, if any. A hint
     * that is not valid is dropped, not shown as an error the person did not make.
     */
    private fun opening(): Draft {
        val today = clock.today.value.date
        val withdrawal = saved.get<Boolean>(KEY_PREFILL_WITHDRAWAL)
        val amount = saved.get<String>(KEY_PREFILL_AMOUNT)?.let(AmountDraft::paste)
            ?.takeIf { it.toTransactionAmount() is Outcome.Success }
        val note = saved.get<String>(KEY_PREFILL_NOTE)
            ?.takeIf { Note.of(it) is Outcome.Success }.orEmpty()
        val date = saved.get<String>(KEY_PREFILL_DATE)?.let(::parseDate)
            ?.takeIf { GoalPolicy.acceptsDate(it, today) } ?: today
        return if (amount != null) {
            Draft(
                listOf(
                    LineDraft("1", goalId, amount, withdrawal, date, note),
                    LineDraft("2", goalId, AmountDraft.Empty, withdrawal, date, "")
                ),
                highlighted = 1,
                nextKey = 3
            )
        } else {
            Draft(listOf(LineDraft("1", goalId, AmountDraft.Empty, withdrawal, date, note)), 0, 2)
        }
    }

    private fun parseDate(text: String): LocalDate? = try {
        LocalDate.parse(text)
    } catch (_: DateTimeParseException) {
        null
    }

    /** A stored draft this screen could not have written is treated as none. */
    private fun wellFormed(draft: Draft): Boolean =
        draft.lines.size in 1..BULK_RECEIPT_MAX_LINES && draft.highlighted in draft.lines.indices

    /** A draft stored before each line named its goal: all of it was for the goal it opened from. */
    private fun oneGoalDraft(): Draft? = oneGoalDrafts.read()?.let { old ->
        Draft(
            old.lines.map { LineDraft(it.key, goalId, it.amount, it.withdrawal, it.date, it.note) },
            old.highlighted,
            old.nextKey
        )
    }

    private fun current(): LineDraft = draft.value.lines[draft.value.highlighted]

    private fun write(next: Draft) {
        oneGoalDrafts.clear()
        drafts.write(next)
        draft.value = next
    }

    /** A change to the lines: what storage refused is checked again on the next save. */
    private fun change(next: Draft) {
        progress.update {
            it.copy(
                refusals = emptyMap(),
                error = it.error.takeIf { error -> error != GoalBulkError.Refused },
                removed = null
            )
        }
        write(next)
    }

    private fun line(edit: (LineDraft) -> LineDraft) {
        val now = draft.value
        val before = now.lines[now.highlighted]
        val after = edit(before)
        if (after == before) return
        if (after.amount != before.amount) progress.update { it.copy(atLimit = false) }
        change(now.copy(lines = now.lines.toMutableList().also { it[now.highlighted] = after }))
    }

    private fun key(change: AmountDraft.Change) {
        line { it.copy(amount = change.draft) }
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1, atLimit = true) }
        }
    }

    /** A new line at the end, with the goal, type and date of the line above it. */
    private fun add() {
        val now = draft.value
        val above = now.lines.last()
        val line = LineDraft(
            now.nextKey.toString(),
            above.goal,
            AmountDraft.Empty,
            above.withdrawal,
            above.date,
            ""
        )
        progress.update { it.copy(atLimit = false) }
        change(Draft(now.lines + line, now.lines.size, now.nextKey + 1))
    }

    /** The last line stays: a receipt always has one. */
    private fun remove(index: Int) {
        val now = draft.value
        if (now.lines.size <= 1 || index !in now.lines.indices) return
        val lines = now.lines.toMutableList().also { it.removeAt(index) }
        val highlighted = when {
            index < now.highlighted -> now.highlighted - 1
            else -> now.highlighted.coerceAtMost(lines.lastIndex)
        }
        change(now.copy(lines = lines, highlighted = highlighted))
        offerUndo(Removed(now.lines[index], index))
    }

    private fun offerUndo(removed: Removed) {
        progress.update { it.copy(removed = removed) }
        removal?.cancel()
        removal = viewModelScope.launch {
            delay(UNDO_WINDOW)
            progress.update { if (it.removed === removed) it.copy(removed = null) else it }
        }
    }

    private fun undoRemove() {
        val removed = progress.value.removed ?: return
        val now = draft.value
        if (now.lines.size >= BULK_RECEIPT_MAX_LINES) return
        removal?.cancel()
        val index = removed.index.coerceAtMost(now.lines.size)
        val lines = now.lines.toMutableList().also { it.add(index, removed.line) }
        change(now.copy(lines = lines, highlighted = index))
    }

    private fun close() {
        composer.close()
        progress.update { it.copy(confirmDiscard = false, closed = true) }
    }

    /** The board to work from: the last one read, also after a failed refresh. */
    private fun readable(state: GoalBoardState): GoalBoard? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    /** The goals a line can name: those not completed, in the board's order. */
    private fun active(board: GoalBoard?): List<GoalSummary> =
        board?.goals.orEmpty().filter { it.history.goal.completedOn == null }

    /** [line]'s goal, while it is active. */
    private fun goalOf(line: LineDraft, active: List<GoalSummary>): GoalSummary? =
        line.goal?.let { id -> active.firstOrNull { it.history.goal.id.value == id } }

    private fun amountOf(line: LineDraft): TransactionAmount? =
        (line.amount.toTransactionAmount() as? Outcome.Success)?.value

    private fun kindOf(line: LineDraft): GoalMovementKind? = when (line.withdrawal) {
        true -> GoalMovementKind.Withdrawal
        false -> GoalMovementKind.Contribution
        null -> null
    }

    /**
     * The lines that would be sent, with their keys, in line order: those with an amount, a
     * type and a goal among [active]; with [active] null, any goal the line names.
     */
    private fun sendable(
        draft: Draft,
        active: List<GoalSummary>?
    ): List<Pair<String, GoalMovementLine>> = draft.lines.mapNotNull { line ->
        val goal = if (active == null) {
            line.goal?.let { (GoalId.of(it) as? Outcome.Success)?.value }
        } else {
            goalOf(line, active)?.history?.goal?.id
        } ?: return@mapNotNull null
        val amount = amountOf(line) ?: return@mapNotNull null
        val kind = kindOf(line) ?: return@mapNotNull null
        line.key to GoalMovementLine(
            goal,
            kind,
            amount,
            line.date,
            (Note.of(line.note) as? Outcome.Success)?.value
        )
    }

    /** Aportes minus retiros, of the lines with an amount and a type. */
    private fun net(lines: List<GoalMovementLine>): Money? =
        lines.fold(ExactTotal.zero(Currency.COP)) {
                total,
                line
            ->
            when (line.kind) {
                GoalMovementKind.Contribution -> total + line.amount.money
                GoalMovementKind.Withdrawal -> total - line.amount.money
            }
        }.toMoney().let { (it as? Outcome.Success)?.value }

    private fun render(
        draft: Draft,
        progress: Progress,
        board: GoalBoardState,
        today: LocalDate,
        writing: String?
    ): GoalBulkState {
        val read = readable(board)
        val active = active(read)
        val lines = draft.lines.map { line ->
            val refusal = progress.refusals[line.key]
            GoalBulkLine(
                key = line.key,
                goalId = line.goal,
                goalName = goalOf(line, active)?.history?.goal?.name?.value,
                withdrawal = line.withdrawal,
                amount = amountOf(line)?.let { money.format(it.money) },
                date = line.date,
                note = line.note,
                available = (refusal as? Refusal.AboveSaved)?.let { money.format(it.available) },
                futureDate = refusal == Refusal.FutureDate,
                invalidDate = refusal == Refusal.InvalidDate
            )
        }
        val named = lines.mapNotNull { it.goalName }.distinct()
        return GoalBulkState(
            loading = board is ReadState.Loading,
            loadFailed = board is ReadState.Failed && read == null,
            stale = board is ReadState.Failed && read != null,
            goalName = named.singleOrNull(),
            goals = active.map {
                GoalBulkChoice(
                    it.history.goal.id.value,
                    it.history.goal.name.value,
                    it.history.goal.icon.value
                )
            },
            lines = lines,
            highlighted = draft.highlighted,
            total = net(sendable(draft, active).map { it.second })
                ?.let { money.format(it, signed = true) },
            today = today,
            atAmountLimit = progress.atLimit,
            limitPulse = progress.limitPulse,
            datePickerOpen = progress.datePickerOpen,
            composerOpen = writing != null && !progress.closed,
            composerDraft = writing.orEmpty(),
            composerProblem = writing?.let(SavedNoteComposer::problem),
            composerLength = SavedNoteComposer.length(writing),
            removed = progress.removed?.let { RemovedGoalLine(it.line.withdrawal) },
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val active = active(read)
        val sent = sendable(draft.value, active)
        if (sent.isEmpty()) return
        val keys = sent.map { it.first }
        val lines = sent.map { it.second }
        val named = lines.map { it.goalId }.toSet()
        val goals = active.map { it.history }.filter { it.goal.id in named }
        progress.update {
            it.copy(busy = true, error = null, refusals = emptyMap(), removed = null)
        }
        viewModelScope.launch {
            settle(
                submission.send(commands::find, STEP) { operation ->
                    commands.submit(
                        RecordGoalMovements(
                            operation,
                            read.generation,
                            goals.associate { it.goal.id to it.goal.version },
                            lines
                        )
                    )
                },
                keys,
                lines,
                goals
            )
        }
    }

    /**
     * [keys] and [lines] are the sent lines' keys and lines in the order sent, and [sentFrom]
     * the goals they name as they were read when they were sent; all empty after a restart.
     */
    private fun settle(
        settled: Settled<CommittedGoal, GoalRejection>?,
        keys: List<String>,
        lines: List<GoalMovementLine>,
        sentFrom: List<GoalHistory>
    ) {
        when (settled) {
            // Something is still in flight and will settle; or nothing was left to ask about.
            null -> if (!submission.pending) progress.update { it.copy(busy = false) }

            // A receipt found after an unknown outcome was not seen: it is never celebrated.
            is Settled.Committed -> committed(
                settled.value.goals.takeIf { !settled.found }.orEmpty(),
                lines,
                sentFrom
            )

            is Settled.Unknown -> progress.update {
                it.copy(busy = false, error = GoalBulkError.Unknown)
            }

            Settled.NotCommitted -> progress.update {
                it.copy(busy = false, error = GoalBulkError.NotSaved)
            }

            is Settled.Rejected -> rejected(settled.reason, keys)
        }
    }

    private fun rejected(reason: CommandRejection<GoalRejection>, keys: List<String>) {
        val specific = (reason as? CommandRejection.Specific)?.reason
        val refusal: Pair<Int, Refusal>? = when (specific) {
            is GoalRejection.LineAboveSaved ->
                specific.line to Refusal.AboveSaved(specific.available)

            is GoalRejection.LineFutureDate -> specific.line to Refusal.FutureDate

            is GoalRejection.LineInvalidDate -> specific.line to Refusal.InvalidDate

            else -> null
        }
        val key = refusal?.let { keys.getOrNull(it.first) }
        if (refusal != null && key != null) {
            progress.update {
                it.copy(
                    busy = false,
                    error = GoalBulkError.Refused,
                    refusals = mapOf(key to refusal.second)
                )
            }
            return
        }
        val error = when (reason) {
            is CommandRejection.StaleGeneration -> GoalBulkError.DataChanged

            is CommandRejection.VersionConflict -> GoalBulkError.Changed

            CommandRejection.OperationConflict, is CommandRejection.StorageFailed -> null

            is CommandRejection.Specific -> when (reason.reason) {
                GoalRejection.GoalMissing, GoalRejection.Completed -> GoalBulkError.Changed
                else -> null
            }
        } ?: GoalBulkError.NotSaved
        progress.update { it.copy(busy = false, error = error) }
    }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        if (!submission.pending) return
        progress.update { it.copy(busy = true, error = null) }
        settle(submission.recover(commands::find), emptyList(), emptyList(), emptyList())
    }

    /**
     * Says what was saved and closes. [goals] are what storage says the receipt did to each
     * goal's stamps, empty when it is not to be celebrated; [lines] are the sent lines and
     * [sentFrom] their goals as read when sent. After a restart neither is known, so the
     * count and net come from the draft as it stands.
     */
    private fun committed(
        goals: List<ReceiptGoal>,
        lines: List<GoalMovementLine>,
        sentFrom: List<GoalHistory>
    ) {
        val saved = lines.ifEmpty { sendable(draft.value, active = null).map { it.second } }
        corrections.goalMovementsRecorded(
            CorrectionNotice.GoalMovementsRecorded(
                count = saved.size,
                net = net(saved)?.let { money.format(it, signed = true) },
                celebrations = goals.mapNotNull { receipt ->
                    val change = receipt.change ?: return@mapNotNull null
                    val history = sentFrom.firstOrNull { it.goal.id == receipt.goalId }
                        ?: return@mapNotNull null
                    celebration(change, history, lines.filter { it.goalId == receipt.goalId })
                }
            )
        )
        drafts.clear()
        oneGoalDrafts.clear()
        composer.close()
        removal?.cancel()
        progress.update { it.copy(busy = false, error = null, removed = null, closed = true) }
    }

    /**
     * What to celebrate for [change], with what is left for the target once the receipt's
     * lines follow [history], the goal as it was read when they were sent.
     */
    private fun celebration(
        change: GoalChange,
        history: GoalHistory,
        lines: List<GoalMovementLine>
    ): GoalCelebration? {
        if (change.earned.isEmpty() && change.relit.isEmpty() && !change.reachedMeta) return null
        return GoalCelebration(
            goalId = history.goal.id.value,
            goalName = history.goal.name.value,
            earned = change.earned,
            relit = change.relit,
            reachedMeta = change.reachedMeta,
            missing = after(history, lines)?.let { money.format(GoalPolicy.missing(it)) }
        )
    }

    /** A goal as it stands with its lines of the receipt recorded after its history, in order. */
    private fun after(history: GoalHistory, lines: List<GoalMovementLine>) = run {
        val next = (history.events.maxOfOrNull(GoalEvent::sequence) ?: -1) + 1
        val today = clock.today.value.date
        val recorded = GoalPolicy.recordingOrder(lines).mapIndexed { position, index ->
            val line = lines[index]
            GoalMovement(
                CANDIDATE,
                line.kind,
                line.amount,
                line.date,
                next + position,
                today,
                line.note,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
        }
        (GoalPolicy.progress(history.copy(events = history.events + recorded)) as? Outcome.Success)
            ?.value
    }

    companion object {
        /** Route argument: the goal it was opened from, line one's goal; absent from Metas. */
        const val KEY_GOAL = "goalId"

        /** Route hints: what Aportar or Retirar had typed, checked like any input. */
        const val KEY_PREFILL_WITHDRAWAL = "withdrawal"
        const val KEY_PREFILL_AMOUNT = "amount"
        const val KEY_PREFILL_NOTE = "note"
        const val KEY_PREFILL_DATE = "date"

        /** How long a removed line can be put back. */
        val UNDO_WINDOW = 4.seconds

        /** Stands for the receipt's movements while the stamps are worked out; never sent. */
        private val CANDIDATE =
            (GoalMovementId.of("00000000-0000-4000-8000-ffffffffffff") as Outcome.Success).value

        private const val DRAFT = "goal.bulk.draft.goals"
        private const val ONE_GOAL_DRAFT = "goal.bulk.draft"
        private const val PREFIX = "goal.bulk"
        private const val OPERATION = "goal.bulk.operation"
        private const val STEP = "record_movements"
    }
}
