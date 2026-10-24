package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.RecordIdSource
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
 * Metas from the goal board as it changes. The band's month is the financial day's,
 * never the month chosen on Recibo: goals are not tied to a month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetasViewModel(
    saved: SavedStateHandle,
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

    val state: StateFlow<MetasState> =
        combine(board, clock.today, completer.state) { board, today, completion ->
            when (val metas = render.metas(board, today.date)) {
                is MetasState.Ready -> metas.copy(completion = completion)
                else -> metas
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            MetasState.Loading
        )

    fun onAction(action: MetasAction) {
        when (action) {
            // A new attempt starts a new observation.
            MetasAction.Retry -> if (completer.state.value is CompletionState.Failed) {
                completer.retry()
            } else {
                attempts.update { it + 1 }
            }

            // Handled by the root, which owns navigation.
            is MetasAction.Open -> Unit

            is MetasAction.MarkComplete -> {
                val read = (board.value as? GoalBoardState.Ready)?.board ?: return
                val card = (state.value as? MetasState.Ready)?.goals?.firstOrNull {
                    it.id ==
                        action.id
                }
                if (card?.reached == true) {
                    render.completionTarget(read, action.id)?.let(completer::complete)
                }
            }

            MetasAction.CelebrationDone -> completer.done()
        }
    }

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
