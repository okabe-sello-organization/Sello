package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.DeleteGoal
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalCommand
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ReplaceStar
import com.software.sello.domain.port.StarGoal
import com.software.sello.domain.port.UnstarGoal
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import com.software.sello.presentation.submission.Settled
import com.software.sello.presentation.submission.Submission
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
 * One goal, read from the board as it changes, and its actions: starring it for Recibo
 * (with five starred, through the sheet that picks the star it replaces) and, once
 * completed, deleting it after the confirm slip. The sheet and its pick are kept in
 * [SavedStateHandle], so they come back after process death. Each command's
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
    private val guidance: GuidancePreferences,
    screenReader: ScreenReader
) : ViewModel() {
    private class Ui(val busy: Boolean = false, val error: GoalDetailError? = null)

    private val render = GoalsRenderModel(money)
    private val goalId: String = saved[KEY_GOAL] ?: ""
    private val attempts = MutableStateFlow(0)
    private val ui = MutableStateFlow(Ui())

    /** The board as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<GoalBoard, StorageFailure>()
    private val submission = Submission(saved, KEY_OPERATION, ids)
    private val completer =
        GoalCompleter(saved, commands, corrections, ids, clock, screenReader, viewModelScope)

    private val _deleted = MutableStateFlow(saved.get<Boolean>(KEY_DELETED) ?: false)

    /** The goal is deleted: the screen is left. */
    val deleted: StateFlow<Boolean> = _deleted

    private val flags = combine(
        saved.getStateFlow(KEY_CONFIRM, false),
        saved.getStateFlow(KEY_ALL, false),
        saved.getStateFlow(KEY_REPLACE, false),
        saved.getStateFlow<String?>(KEY_PICKED, null),
        ::Flags
    )

    private data class Flags(
        val confirming: Boolean,
        val all: Boolean,
        val replacing: Boolean,
        val picked: String?
    )

    val state: StateFlow<GoalDetailState> = combine(
        attempts.flatMapLatest { lastGood.keep(Unit, reads.observeBoard()) },
        clock.today,
        combine(ui, completer.state, ::Pair),
        flags
    ) { board, today, (ui, completion), flags ->
        when (val state = render.detail(board, goalId, today.date)) {
            is GoalDetailState.Ready -> state.copy(
                busy = ui.busy || completion == CompletionState.Busy,
                error = ui.error ?: (completion as? CompletionState.Failed)?.error,
                confirmingDelete = flags.confirming && state.goal.completedOn != null,
                allMovements = flags.all,
                completion = completion,
                replacing = sheet(state.goal, flags)
            )

            else -> state
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        GoalDetailState.Loading
    )

    init {
        if (submission.pending) viewModelScope.launch { recover() }
    }

    fun onAction(action: GoalDetailAction) {
        when (action) {
            GoalDetailAction.Retry -> if (completer.state.value is CompletionState.Failed) {
                completer.retry()
                // Retry always reads again, also when it clears a failed completion.
                attempts.update { it + 1 }
            } else if (ui.value.error == GoalDetailError.Unknown) {
                viewModelScope.launch { recover() }
            } else {
                ui.value = Ui()
                attempts.update { it + 1 }
            }

            GoalDetailAction.ToggleStar -> toggleStar()

            is GoalDetailAction.Replace -> replace(action.id)

            GoalDetailAction.CancelReplace -> if (!ui.value.busy) {
                saved[KEY_REPLACE] = false
                saved[KEY_PICKED] = null
                // A refusal belonged to the sheet; an unknown outcome is still to be checked.
                if (ui.value.error == GoalDetailError.NotSaved) ui.value = Ui()
            }

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
    private fun free(): Boolean = !ui.value.busy && !submission.pending

    /** A filled star unstars; an empty one stars, or with five starred opens the sheet. */
    private fun toggleStar() {
        val goal = ready()?.goal ?: return
        if (!free() || goal.completedOn != null) return
        val id = (GoalId.of(goal.id) as? Outcome.Success)?.value ?: return
        when {
            goal.starred -> send(STEP_UNSTAR) { UnstarGoal(it, goal.generation, id) }

            goal.starredOthers.size >= GoalPolicy.MAX_STARRED -> openSheet()

            else -> {
                // A sheet left open from before is not wanted now there is room.
                saved[KEY_REPLACE] = false
                saved[KEY_PICKED] = null
                send(STEP_FEATURE) { StarGoal(it, goal.generation, id) }
            }
        }
    }

    /**
     * The replace sheet while it is open and still needed: the goal is active and not
     * starred, and the other starred goals fill every place. A pick no longer among them
     * is dropped, so Reintentar never sends a star that moved meanwhile.
     */
    private fun sheet(goal: GoalDetailModel, flags: Flags): ReplaceSheet? {
        val needed = flags.replacing && !goal.starred && goal.completedOn == null &&
            goal.starredOthers.size >= GoalPolicy.MAX_STARRED
        if (!needed) return null
        val picked = flags.picked?.takeIf { id -> goal.starredOthers.any { it.id == id } }
        return ReplaceSheet(goal.starredOthers, picked)
    }

    private fun openSheet() {
        ui.value = Ui()
        saved[KEY_PICKED] = null
        saved[KEY_REPLACE] = true
    }

    /** Picking a starred goal in the sheet saves at once: its star moves here, or nothing does. */
    private fun replace(picked: String) {
        val sheet = ready()?.replacing ?: return
        val goal = ready()?.goal ?: return
        if (!free() || sheet.choices.none { it.id == picked }) return
        val star = (GoalId.of(goal.id) as? Outcome.Success)?.value ?: return
        val unstar = (GoalId.of(picked) as? Outcome.Success)?.value ?: return
        saved[KEY_PICKED] = picked
        send(STEP_REPLACE) { ReplaceStar(it, goal.generation, unstar, star) }
    }

    private fun delete() {
        val goal = ready()?.goal ?: return
        if (!free() || goal.completedOn == null || saved.get<Boolean>(KEY_CONFIRM) != true) return
        val id = (GoalId.of(goal.id) as? Outcome.Success)?.value ?: return
        send(STEP_DELETE) { DeleteGoal(it, goal.generation, id, goal.version) }
    }

    private fun send(step: String, command: (OperationId) -> GoalCommand) {
        ui.value = Ui(busy = true)
        viewModelScope.launch {
            settle(step, submission.send(commands::find, step) { commands.submit(command(it)) })
        }
    }

    /** Asks how the saved operation ended, with its own identifier. */
    private suspend fun recover() {
        if (!submission.pending) return
        val step = submission.step
        ui.value = Ui(busy = true)
        settle(step, submission.recover(commands::find))
    }

    private fun settle(step: String?, settled: Settled<CommittedGoal, GoalRejection>?) {
        when (settled) {
            null -> if (!submission.pending) ui.value = Ui()

            is Settled.Committed -> {
                if (step == STEP_REPLACE) {
                    saved[KEY_REPLACE] = false
                    saved[KEY_PICKED] = null
                }
                // Changing which goals Recibo puts first closes its goal nudge for good.
                if (step in STAR_STEPS) {
                    viewModelScope.launch { guidance.markSeen(Nudge.ReciboGoal) }
                }
                if (step == STEP_DELETE) {
                    saved[KEY_CONFIRM] = false
                    saved[KEY_DELETED] = true
                    _deleted.value = true
                }
                ui.value = Ui()
            }

            is Settled.Unknown -> ui.value = Ui(error = GoalDetailError.Unknown)

            // Five were starred meanwhile and the board already shows them: pick one to replace.
            is Settled.Rejected if step == STEP_FEATURE &&
                (settled.reason as? CommandRejection.Specific)?.reason is GoalRejection.StarLimit &&
                (ready()?.goal?.starredOthers?.size ?: 0) >= GoalPolicy.MAX_STARRED ->
                openSheet()

            // Certain: nothing was written, so this attempt is over. The sheet keeps its pick.
            is Settled.Rejected, Settled.NotCommitted -> {
                saved[KEY_CONFIRM] = false
                ui.value = Ui(error = GoalDetailError.NotSaved)
            }
        }
    }

    companion object {
        /** The route argument: the goal shown. */
        const val KEY_GOAL = "goalId"

        private const val KEY_OPERATION = "goal.detail.operation"
        private const val KEY_CONFIRM = "goal.detail.confirmDelete"
        private const val KEY_ALL = "goal.detail.allMovements"
        private const val KEY_DELETED = "goal.detail.deleted"
        private const val KEY_REPLACE = "goal.detail.replacing"
        private const val KEY_PICKED = "goal.detail.replacePicked"
        private const val STEP_FEATURE = "feature"
        private const val STEP_UNSTAR = "unstar"
        private const val STEP_REPLACE = "replace"
        private const val STEP_DELETE = "delete"
        private val STAR_STEPS = setOf(STEP_FEATURE, STEP_UNSTAR, STEP_REPLACE)

        /** Long enough to outlast a rotation without reading again. */
        private const val STOP_AFTER_MILLIS = 5_000L
    }
}
