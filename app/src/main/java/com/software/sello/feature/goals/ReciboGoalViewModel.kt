package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.navigation.MonthSession
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Recibo's goal from the goal board as it changes (rule 12). Which goal is storage's
 * answer, [GoalBoard.featured]: the person's choice, else the fallback. Shown only on
 * the current month; goals are read apart from it and never change its figures.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboGoalViewModel(
    saved: SavedStateHandle,
    session: MonthSession,
    reads: GoalReads,
    commands: GoalCommands,
    clock: FinancialClock,
    money: MoneyFormatter,
    ids: RecordIdSource,
    corrections: ExpenseCorrections,
    screenReader: ScreenReader
) : ViewModel() {
    private val render = GoalsRenderModel(money)
    private val attempts = MutableStateFlow(0)
    private val completer =
        GoalCompleter(saved, commands, corrections, ids, clock, screenReader, viewModelScope)
    private val board = attempts.flatMapLatest { reads.observeBoard() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            GoalBoardState.Loading
        )

    val state: StateFlow<ReciboGoalState> = combine(
        board,
        session.selectedMonth,
        session.currentMonth,
        completer.state
    ) { board, selected, current, completion ->
        val read = when (board) {
            GoalBoardState.Loading -> null
            is GoalBoardState.Ready -> board.board
            is GoalBoardState.Failed -> board.lastGood ?: return@combine ReciboGoalState.Failed
        }
        val pill = read?.takeIf { selected == current }?.let(::pill)
        val reached = read?.takeIf { selected == current }?.let { reached(it, pill?.id) }
        if (pill == null && reached == null && completion == CompletionState.Idle) {
            ReciboGoalState.Hidden
        } else {
            ReciboGoalState.Shown(pill, reached, completion)
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        ReciboGoalState.Hidden
    )

    fun onAction(action: ReciboGoalAction) {
        when (action) {
            // A new attempt starts a new observation.
            ReciboGoalAction.Retry -> if (completer.state.value is CompletionState.Failed) {
                completer.retry()
            } else {
                attempts.update { it + 1 }
            }

            is ReciboGoalAction.MarkComplete -> {
                val read = (board.value as? GoalBoardState.Ready)?.board ?: return
                if ((state.value as? ReciboGoalState.Shown)?.reached?.id == action.id) {
                    render.completionTarget(read, action.id)?.let(completer::complete)
                }
            }

            ReciboGoalAction.CelebrationDone -> completer.done()
        }
    }

    private fun pill(board: GoalBoard): ReciboGoalPill? {
        val shown = board.goals.firstOrNull { it.history.goal.id == board.featured } ?: return null
        val goal = shown.history.goal
        return ReciboGoalPill(
            goal.id.value,
            goal.name.value,
            goal.icon.value,
            shown.progress.percentage
        )
    }

    /** The first other active goal at 100 %, in Metas' order. */
    private fun reached(board: GoalBoard, shown: String?): ReciboReachedGoal? =
        GoalPolicy.ordered(board.goals, { it.history.goal }, { it.progress })
            .firstOrNull {
                val goal = it.history.goal
                goal.completedOn == null && goal.id.value != shown &&
                    it.progress.state(GoalPlace.Meta) == PlaceState.Ready
            }
            ?.history?.goal?.let { ReciboReachedGoal(it.id.value, it.name.value, it.icon.value) }

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
