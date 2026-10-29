package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.FeatureGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One goal, read from the board as it changes, and its two actions: choosing it for
 * Recibo and, once completed, deleting it after the confirm slip. Each command's
 * identifier is saved before it leaves; an outcome that is not known is asked about with
 * that same identifier, after a restart too, before anything else is sent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GoalDetailViewModel(
    private val saved: SavedStateHandle,
    reads: GoalReads,
    private val commands: GoalCommands,
    clock: FinancialClock,
    money: MoneyFormatter,
    private val ids: RecordIdSource,
    corrections: Corrections,
    screenReader: ScreenReader
) : ViewModel() {
    private class Ui(val busy: Boolean = false, val error: GoalDetailError? = null)

    private val render = GoalsRenderModel(money)
    private val goalId: String = saved[KEY_GOAL] ?: ""
    private val attempts = MutableStateFlow(0)
    private val ui = MutableStateFlow(Ui())
    private val completer =
        GoalCompleter(saved, commands, corrections, ids, clock, screenReader, viewModelScope)

    private val _deleted = MutableStateFlow(saved.get<Boolean>(KEY_DELETED) ?: false)

    /** The goal is deleted: the screen is left. */
    val deleted: StateFlow<Boolean> = _deleted

    val state: StateFlow<GoalDetailState> = combine(
        attempts.flatMapLatest { reads.observeBoard() },
        clock.today,
        combine(ui, completer.state, ::Pair),
        saved.getStateFlow(KEY_CONFIRM, false),
        saved.getStateFlow(KEY_ALL, false)
    ) { board, today, (ui, completion), confirming, all ->
        when (val state = render.detail(board, goalId, today.date)) {
            is GoalDetailState.Ready -> state.copy(
                busy = ui.busy || completion == CompletionState.Busy,
                error = ui.error ?: (completion as? CompletionState.Failed)?.error,
                confirmingDelete = confirming && state.goal.completedOn != null,
                allMovements = all,
                completion = completion
            )

            else -> state
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        GoalDetailState.Loading
    )

    init {
        if (saved.get<String>(KEY_OPERATION) != null) viewModelScope.launch { recover() }
    }

    fun onAction(action: GoalDetailAction) {
        when (action) {
            GoalDetailAction.Retry -> if (completer.state.value is CompletionState.Failed) {
                completer.retry()
            } else if (ui.value.error == GoalDetailError.Unknown) {
                viewModelScope.launch { recover() }
            } else {
                ui.value = Ui()
                attempts.update { it + 1 }
            }

            GoalDetailAction.Feature -> feature()

            GoalDetailAction.AskToDelete -> if (ready()?.goal?.completedOn != null) {
                saved[KEY_CONFIRM] = true
            }

            GoalDetailAction.CancelDelete -> if (!ui.value.busy) saved[KEY_CONFIRM] = false

            GoalDetailAction.Delete -> delete()

            GoalDetailAction.ShowAllMovements -> saved[KEY_ALL] = true

            GoalDetailAction.MarkComplete -> markComplete()

            GoalDetailAction.CelebrationDone -> completer.done()
        }
    }

    /** Marcar como lograda: offered once the goal reached its target (rule 8). */
    private fun markComplete() {
        val goal = ready()?.goal ?: return
        if (!free() || goal.completedOn != null || goal.next?.reached != true) return
        completer.complete(goal.completionTarget())
    }

    private fun ready(): GoalDetailState.Ready? = state.value as? GoalDetailState.Ready

    /** Whether a command may be sent: nothing in flight and no outcome left unknown. */
    private fun free(): Boolean = !ui.value.busy && saved.get<String>(KEY_OPERATION) == null

    private fun feature() {
        val goal = ready()?.goal ?: return
        if (!free() || goal.featured || goal.completedOn != null) return
        val id = (GoalId.of(goal.id) as? Outcome.Success)?.value ?: return
        send(STEP_FEATURE) { FeatureGoal(it, goal.generation, id) }
    }

    private fun delete() {
        val goal = ready()?.goal ?: return
        if (!free() || goal.completedOn == null || saved.get<Boolean>(KEY_CONFIRM) != true) return
        val id = (GoalId.of(goal.id) as? Outcome.Success)?.value ?: return
        send(STEP_DELETE) { DeleteGoal(it, goal.generation, id, goal.version) }
    }

    private fun send(step: String, command: (OperationId) -> GoalCommand) {
        val operation = (OperationId.of(ids.next()) as? Outcome.Success)?.value ?: return
        // Saved before it leaves, so this attempt is recognised whatever happens next.
        saved[KEY_OPERATION] = operation.value
        saved[KEY_STEP] = step
        ui.value = Ui(busy = true)
        viewModelScope.launch {
            when (val outcome = commands.submit(command(operation))) {
                is CommandOutcome.Committed -> committed(step)
                is CommandOutcome.Rejected -> notSaved()
                is CommandOutcome.OutcomeUnknown -> recover()
            }
        }
    }

    /** Asks how the saved operation ended, with its own identifier. */
    private suspend fun recover() {
        val operation = (saved.get<String>(KEY_OPERATION)?.let(OperationId::of) as? Outcome.Success)
            ?.value ?: return run { saved[KEY_OPERATION] = null }
        val step = saved.get<String>(KEY_STEP)
        ui.value = Ui(busy = true)
        when (val found = commands.find(operation)) {
            is Outcome.Failure -> ui.value = Ui(error = GoalDetailError.Unknown)
            is Outcome.Success -> found.value?.let { committed(step) } ?: notSaved()
        }
    }

    private fun committed(step: String?) {
        saved[KEY_OPERATION] = null
        saved[KEY_STEP] = null
        if (step == STEP_DELETE) {
            saved[KEY_CONFIRM] = false
            saved[KEY_DELETED] = true
            _deleted.value = true
        }
        ui.value = Ui()
    }

    /** Certain: nothing was written, so this attempt is over. */
    private fun notSaved() {
        saved[KEY_OPERATION] = null
        saved[KEY_STEP] = null
        saved[KEY_CONFIRM] = false
        ui.value = Ui(error = GoalDetailError.NotSaved)
    }

    companion object {
        /** The route argument: the goal shown. */
        const val KEY_GOAL = "goalId"

        private const val KEY_OPERATION = "goal.detail.operation"
        private const val KEY_STEP = "goal.detail.step"
        private const val KEY_CONFIRM = "goal.detail.confirmDelete"
        private const val KEY_ALL = "goal.detail.allMovements"
        private const val KEY_DELETED = "goal.detail.deleted"
        private const val STEP_FEATURE = "feature"
        private const val STEP_DELETE = "delete"

        /** Long enough to outlast a rotation without reading again. */
        private const val STOP_AFTER_MILLIS = 5_000L
    }
}
