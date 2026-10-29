package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalHistoryError
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.AmountDraft
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.DeleteGoalMovement
import com.software.sello.domain.port.EditGoalMovement
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.money.MoneyFormatter
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aportar and Retirar, and the correction or deletion of a movement already recorded.
 * The goals come from the board as it changes; the draft, an open note and the operation
 * in flight live in [saved]. Before anything is sent, the movement is checked by the goal
 * rules: a retiro above what is saved, or a correction that would leave a later retiro
 * short, waits with the reason. A save whose outcome is not known is asked about with its
 * own identifier before anything else is sent. Recording never touches a budget.
 */
class GoalMovementViewModel(
    private val saved: SavedStateHandle,
    reads: GoalReads,
    private val commands: GoalCommands,
    private val clock: FinancialClock,
    private val ids: RecordIdSource,
    private val money: MoneyFormatter,
    private val corrections: ExpenseCorrections
) : ViewModel() {
    private data class Draft(
        val amount: AmountDraft,
        val goalId: String?,
        val date: LocalDate,
        val note: String,
        val withdrawal: Boolean
    )

    private data class Progress(
        val busy: Boolean = false,
        val error: GoalMovementError? = null,
        val closed: Boolean = false,
        val confirmDiscard: Boolean = false,
        val datePickerOpen: Boolean = false,
        val limitPulse: Int = 0,
        val atLimit: Boolean = false,
        /** The later retiro a deletion was refused for. */
        val deleteBlockedBy: GoalMovementId? = null
    )

    private val movementId: String? = saved[KEY_MOVEMENT]
    private val editing = movementId != null

    private val draft = MutableStateFlow(
        Draft(
            saved.get<String>(AMOUNT)?.let(AmountDraft::paste) ?: AmountDraft.Empty,
            saved[GOAL] ?: saved[KEY_GOAL],
            saved.get<String>(DATE)?.let(LocalDate::parse) ?: clock.today.value.date,
            saved[NOTE] ?: "",
            saved[KIND] ?: saved[KEY_WITHDRAWAL] ?: false
        )
    )
    private val progress = MutableStateFlow(Progress())
    private val board = reads.observeBoard()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadState.Loading)
    private val composer = combine(
        saved.getStateFlow(COMPOSER, false),
        saved.getStateFlow(COMPOSER_TEXT, "")
    ) { open, text -> text.takeIf { open } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, composerNow())

    private fun composerNow(): String? = if (saved.get<Boolean>(COMPOSER) ==
        true
    ) {
        saved.get<String>(COMPOSER_TEXT).orEmpty()
    } else {
        null
    }

    val state: StateFlow<GoalMovementState> =
        combine(draft, progress, board, clock.today, composer) {
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
            render(draft.value, progress.value, board.value, clock.today.value.date, composerNow())
        )

    init {
        viewModelScope.launch {
            board.collect { state ->
                val current = readable(state) ?: return@collect
                if (editing && saved.get<Boolean>(DRAFTED) != true) {
                    original(current)?.let { (_, movement) ->
                        saved[DRAFTED] = true
                        change(
                            Draft(
                                AmountDraft.Digits(movement.amount.money.minorUnits.toString()),
                                draft.value.goalId,
                                movement.date,
                                movement.note?.value.orEmpty(),
                                movement.kind == GoalMovementKind.Withdrawal
                            )
                        )
                    }
                }
            }
        }
        if (saved.get<String>(OPERATION) != null) viewModelScope.launch { recover() }
    }

    fun onAction(action: GoalMovementAction) {
        val now = state.value
        val editable = now.editable && !now.loading && !now.loadFailed
        when (action) {
            is GoalMovementAction.Kind ->
                if (editable && !editing) change(draft.value.copy(withdrawal = action.withdrawal))

            is GoalMovementAction.Digit -> if (editable) key(draft.value.amount.digit(action.digit))

            GoalMovementAction.TripleZero -> if (editable) key(draft.value.amount.tripleZero())

            GoalMovementAction.Backspace ->
                if (editable) change(draft.value.copy(amount = draft.value.amount.backspace()))

            GoalMovementAction.ClearAmount ->
                if (editable) change(draft.value.copy(amount = AmountDraft.Empty))

            is GoalMovementAction.Paste ->
                if (editable) change(draft.value.copy(amount = AmountDraft.paste(action.text)))

            is GoalMovementAction.GoalPicked ->
                if (editable && !editing && now.goals.any { it.id == action.id }) {
                    change(draft.value.copy(goalId = action.id))
                }

            GoalMovementAction.OpenDatePicker ->
                if (editable) progress.update { it.copy(datePickerOpen = true) }

            GoalMovementAction.CloseDatePicker -> progress.update {
                it.copy(datePickerOpen = false)
            }

            is GoalMovementAction.DatePicked -> {
                if (editable && GoalPolicy.acceptsDate(action.date, now.today)) {
                    change(draft.value.copy(date = action.date))
                }
                progress.update { it.copy(datePickerOpen = false) }
            }

            GoalMovementAction.OpenComposer -> if (editable) {
                saved[COMPOSER_TEXT] = draft.value.note
                saved[COMPOSER] = true
            }

            is GoalMovementAction.ComposerChanged ->
                if (saved.get<Boolean>(COMPOSER) == true) saved[COMPOSER_TEXT] = action.text

            // Over the limit nothing is taken: the composer stays open with the text in it.
            GoalMovementAction.ComposerDone -> composer.value?.let { text ->
                if (editable && noteProblem(text) == null) {
                    change(draft.value.copy(note = text))
                    closeComposer()
                }
            }

            GoalMovementAction.ComposerCancelled -> closeComposer()

            GoalMovementAction.Submit -> if (now.canSubmit) submit()

            GoalMovementAction.Retry -> if (!now.busy) {
                if (saved.get<String>(OPERATION) != null) {
                    viewModelScope.launch { recover() }
                } else {
                    progress.update { it.copy(error = null) }
                }
            }

            GoalMovementAction.Delete -> if (now.canDelete) delete()

            GoalMovementAction.Back -> when {
                now.busy || saved.get<String>(OPERATION) != null -> Unit
                typed(now) -> progress.update { it.copy(confirmDiscard = true) }
                else -> progress.update { it.copy(closed = true) }
            }

            GoalMovementAction.DiscardConfirmed ->
                progress.update { it.copy(confirmDiscard = false, closed = true) }

            GoalMovementAction.KeepEditing -> progress.update { it.copy(confirmDiscard = false) }
        }
    }

    private fun change(next: Draft) {
        if (next.amount != draft.value.amount) progress.update { it.copy(atLimit = false) }
        saved[AMOUNT] = when (val amount = next.amount) {
            AmountDraft.Empty -> null
            is AmountDraft.Digits -> amount.digits
            is AmountDraft.Rejected -> amount.original
        }
        saved[GOAL] = next.goalId
        saved[DATE] = next.date.toString()
        saved[NOTE] = next.note
        saved[KIND] = next.withdrawal
        progress.update { it.copy(deleteBlockedBy = null) }
        draft.value = next
    }

    private fun key(change: AmountDraft.Change) {
        change(draft.value.copy(amount = change.draft))
        if (change.limitReached) {
            progress.update { it.copy(limitPulse = it.limitPulse + 1, atLimit = true) }
        }
    }

    private fun closeComposer() {
        saved[COMPOSER] = false
        saved[COMPOSER_TEXT] = null
    }

    private fun noteProblem(text: String): GoalNoteProblem? =
        when ((Note.of(text) as? Outcome.Failure)?.error) {
            null, TextError.Required -> null
            is TextError.TooLong -> GoalNoteProblem.TooLong
            TextError.ControlCharacter -> GoalNoteProblem.NotAllowed
        }

    private fun typed(state: GoalMovementState): Boolean = if (editing) {
        state.changed
    } else {
        draft.value.amount != AmountDraft.Empty || draft.value.note.isNotBlank()
    }

    /** The board to work from: the last one read, also after a failed refresh. */
    private fun readable(state: GoalBoardState): GoalBoard? = when (state) {
        ReadState.Loading -> null
        is ReadState.Ready -> state.value
        is ReadState.Failed -> state.lastGood
    }

    private fun summary(board: GoalBoard?, id: String?): GoalSummary? =
        board?.goals?.firstOrNull { it.history.goal.id.value == id }

    /** The goal and the movement being corrected, as read. */
    private fun original(board: GoalBoard): Pair<GoalSummary, GoalMovement>? {
        val goal = summary(board, draft.value.goalId) ?: return null
        val movement = goal.history.events.filterIsInstance<GoalMovement>()
            .firstOrNull { it.id.value == movementId } ?: return null
        return goal to movement
    }

    /** The movement as it would be recorded, to check it against the goal's history. */
    private fun candidate(
        draft: Draft,
        amount: TransactionAmount,
        original: GoalMovement?,
        today: LocalDate
    ): GoalMovement {
        val note = (Note.of(draft.note) as? Outcome.Success)?.value
        return original?.copy(amount = amount, date = draft.date, note = note) ?: GoalMovement(
            CANDIDATE,
            if (draft.withdrawal) GoalMovementKind.Withdrawal else GoalMovementKind.Contribution,
            amount,
            draft.date,
            Long.MAX_VALUE,
            today,
            note,
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )
    }

    private fun withdrawalOf(goal: GoalSummary?, id: GoalMovementId): BlockingWithdrawal? =
        goal?.history?.events?.filterIsInstance<GoalMovement>()?.firstOrNull { it.id == id }
            ?.let { BlockingWithdrawal(money.format(it.amount.money), it.date) }

    private fun render(
        draft: Draft,
        progress: Progress,
        board: GoalBoardState,
        today: LocalDate,
        writing: String?
    ): GoalMovementState {
        val read = readable(board)
        val active = read?.goals.orEmpty().filter { it.history.goal.completedOn == null }
        val offered = if (editing) {
            listOfNotNull(summary(read, draft.goalId))
        } else {
            GoalPolicy.ordered(active, { it.history.goal }, { it.progress })
        }
        val goal = summary(read, draft.goalId)?.takeIf { editing || it in active }
        val original = read?.let(::original)?.second
        val amount = (draft.amount.toTransactionAmount() as? Outcome.Success)?.value
        val check = if (goal != null && amount != null) {
            (
                GoalPolicy.withMovement(goal.history, candidate(draft, amount, original, today))
                    as? Outcome.Failure
                )?.error as? GoalHistoryError.WithdrawalAboveSaved
        } else {
            null
        }
        val ours =
            check != null && (check.withdrawal == CANDIDATE || check.withdrawal == original?.id)
        val note = (Note.of(draft.note) as? Outcome.Success)?.value
        return GoalMovementState(
            loading =
                board is ReadState.Loading || (
                    editing && read != null && original == null &&
                        saved.get<Boolean>(DRAFTED) != true
                    ),
            loadFailed = board is ReadState.Failed && read == null,
            editing = editing,
            withdrawal = draft.withdrawal,
            amount = amount?.let { money.format(it.money) },
            amountText = (draft.amount as? AmountDraft.Rejected)?.original,
            limitPulse = progress.limitPulse,
            atLimit = progress.atLimit,
            goals = offered.map {
                GoalChoice(
                    id = it.history.goal.id.value,
                    name = it.history.goal.name.value,
                    iconKey = it.history.goal.icon.value,
                    percentage = it.progress.percentage,
                    saved = money.format(it.progress.saved)
                )
            },
            goalId = goal?.history?.goal?.id?.value,
            overSaved = check?.takeIf { ours }?.let { money.format(it.available) },
            blockedBy = check?.takeIf { !ours }?.let { withdrawalOf(goal, it.withdrawal) }
                ?: progress.deleteBlockedBy?.let { withdrawalOf(goal, it) },
            date = draft.date,
            today = today,
            datePickerOpen = progress.datePickerOpen,
            note = draft.note,
            composerOpen = writing != null && !progress.closed,
            composerDraft = writing.orEmpty(),
            composerProblem = writing?.let(::noteProblem),
            composerLength = writing?.trim()?.let { it.codePointCount(0, it.length) } ?: 0,
            changed = original != null && (
                amount != original.amount || draft.date != original.date || note != original.note
                ),
            busy = progress.busy,
            error = progress.error,
            confirmDiscard = progress.confirmDiscard,
            closed = progress.closed
        )
    }

    private fun operation(step: String): OperationId {
        val operation = (OperationId.of(ids.next()) as Outcome.Success).value
        saved[OPERATION] = operation.value
        saved[STEP] = step
        return operation
    }

    private fun submit() {
        val read = readable(board.value) ?: return
        val current = draft.value
        val goal = summary(read, current.goalId) ?: return
        val amount = (current.amount.toTransactionAmount() as? Outcome.Success)?.value ?: return
        val note = (Note.of(current.note) as? Outcome.Success)?.value
        val id = goal.history.goal.id
        val version = goal.history.goal.version
        val original = original(read)?.second
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            if (original != null) {
                val operation = operation(STEP_EDIT)
                settle(
                    STEP_EDIT,
                    commands.submit(
                        EditGoalMovement(
                            operation,
                            read.generation,
                            id,
                            version,
                            original.id,
                            original.version,
                            amount,
                            current.date,
                            note
                        )
                    )
                )
            } else {
                val operation =
                    operation(if (current.withdrawal) STEP_WITHDRAW else STEP_CONTRIBUTE)
                settle(
                    saved[STEP] ?: STEP_CONTRIBUTE,
                    commands.submit(
                        RecordGoalMovement(
                            operation,
                            read.generation,
                            id,
                            version,
                            if (current.withdrawal) {
                                GoalMovementKind.Withdrawal
                            } else {
                                GoalMovementKind.Contribution
                            },
                            amount,
                            current.date,
                            note
                        )
                    )
                )
            }
        }
    }

    private fun delete() {
        val read = readable(board.value) ?: return
        val (goal, movement) = original(read) ?: return
        progress.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val operation = operation(STEP_DELETE)
            settle(
                STEP_DELETE,
                corrections.deleteGoalMovement(
                    DeleteGoalMovement(
                        operation,
                        read.generation,
                        goal.history.goal.id,
                        goal.history.goal.version,
                        movement.id,
                        movement.version
                    )
                )
            )
        }
    }

    private suspend fun settle(step: String, outcome: GoalCommandOutcome) {
        when (outcome) {
            // A deletion was already offered to undo by [corrections].
            is CommandOutcome.Committed -> committed(
                step,
                outcome.value.receipt,
                announced = true,
                change = outcome.value.change
            )

            is CommandOutcome.OutcomeUnknown -> recover()

            is CommandOutcome.Rejected -> {
                // Certain: nothing was written, so this attempt is over.
                saved[OPERATION] = null
                val reason = outcome.reason
                val specific = (reason as? CommandRejection.Specific)?.reason
                if (specific is GoalRejection.AboveSaved) {
                    progress.update {
                        it.copy(
                            busy = false,
                            deleteBlockedBy = specific.withdrawal.takeIf { step == STEP_DELETE },
                            error = GoalMovementError.Changed.takeIf { step != STEP_DELETE }
                        )
                    }
                    return
                }
                val error = when (reason) {
                    is CommandRejection.StaleGeneration -> GoalMovementError.DataChanged

                    is CommandRejection.VersionConflict -> GoalMovementError.Changed

                    CommandRejection.OperationConflict, is CommandRejection.StorageFailed -> null

                    is CommandRejection.Specific -> when (reason.reason) {
                        is GoalRejection.FutureDate, GoalRejection.InvalidDate ->
                            GoalMovementError.FutureDate

                        GoalRejection.GoalMissing, GoalRejection.MovementMissing ->
                            GoalMovementError.Gone

                        GoalRejection.Completed -> GoalMovementError.Completed

                        else -> null
                    }
                } ?: if (step == STEP_DELETE) {
                    GoalMovementError.NotDeleted
                } else {
                    GoalMovementError.NotSaved
                }
                progress.update { it.copy(busy = false, error = error) }
            }
        }
    }

    /** Asks how the saved operation ended, with its own identifier. Only a receipt counts. */
    private suspend fun recover() {
        val operation =
            (saved.get<String>(OPERATION)?.let(OperationId::of) as? Outcome.Success)?.value
                ?: return
        val step = saved[STEP] ?: STEP_CONTRIBUTE
        progress.update { it.copy(busy = true, error = null) }
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> progress.update {
                it.copy(busy = false, error = GoalMovementError.Unknown)
            }

            is Outcome.Success -> {
                val committed = found.value
                if (committed != null) {
                    committed(step, committed.receipt, announced = false)
                } else {
                    saved[OPERATION] = null
                    progress.update {
                        it.copy(
                            busy = false,
                            error = if (step == STEP_DELETE) {
                                GoalMovementError.NotDeleted
                            } else {
                                GoalMovementError.NotSaved
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * [change] is what storage says the save did to the stamps; null for a replay or a
     * save found after an unknown outcome, which are never celebrated.
     */
    private fun committed(
        step: String,
        receipt: OperationReceipt,
        announced: Boolean,
        change: GoalChange? = null
    ) {
        saved[OPERATION] = null
        val amount = (draft.value.amount.toTransactionAmount() as? Outcome.Success)?.value
        when (step) {
            STEP_DELETE -> if (!announced) corrections.goalMovementDeleted(receipt)

            STEP_EDIT -> corrections.goalMovementUpdated()

            else -> corrections.goalMovementRecorded(
                CorrectionNotice.GoalMovementRecorded(
                    withdrawal = step == STEP_WITHDRAW,
                    amount = amount?.let { money.format(it.money) },
                    celebration = change?.let { celebration(it, amount) }
                )
            )
        }
        progress.update { it.copy(busy = false, error = null, closed = true) }
    }

    /** What to celebrate for [change], with what is left for the target after the aporte. */
    private fun celebration(change: GoalChange, amount: TransactionAmount?): GoalCelebration? {
        if (change.earned.isEmpty() && change.relit.isEmpty() && !change.reachedMeta) return null
        val goal = summary(readable(board.value), draft.value.goalId) ?: return null
        val after = amount?.let {
            val movement = candidate(draft.value, it, null, clock.today.value.date)
            (GoalPolicy.withMovement(goal.history, movement) as? Outcome.Success)?.value
        }
        return GoalCelebration(
            goalId = goal.history.goal.id.value,
            goalName = goal.history.goal.name.value,
            earned = change.earned,
            relit = change.relit,
            reachedMeta = change.reachedMeta,
            missing = after?.let { money.format(GoalPolicy.missing(it)) }
        )
    }

    companion object {
        /** Route arguments: the goal chosen, Retirar instead of Aportar, the movement to correct. */
        const val KEY_GOAL = "goalId"
        const val KEY_WITHDRAWAL = "withdrawal"
        const val KEY_MOVEMENT = "movementId"

        /** Stands for a new movement while it is checked; never sent. */
        private val CANDIDATE =
            (GoalMovementId.of("00000000-0000-4000-8000-ffffffffffff") as Outcome.Success).value

        private const val AMOUNT = "goal.movement.amount"
        private const val GOAL = "goal.movement.goal"
        private const val DATE = "goal.movement.date"
        private const val NOTE = "goal.movement.note"
        private const val KIND = "goal.movement.withdrawal"
        private const val DRAFTED = "goal.movement.drafted"
        private const val COMPOSER = "goal.movement.composer"
        private const val COMPOSER_TEXT = "goal.movement.composer.text"
        private const val OPERATION = "goal.movement.operation"
        private const val STEP = "goal.movement.step"
        private const val STEP_CONTRIBUTE = "contribute"
        private const val STEP_WITHDRAW = "withdraw"
        private const val STEP_EDIT = "edit"
        private const val STEP_DELETE = "delete"
    }
}
