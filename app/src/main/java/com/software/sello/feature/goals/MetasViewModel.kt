package com.software.sello.feature.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.platform.ScreenReader
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
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
 * never the month chosen on Recibo: goals are not tied to a month. Whether the help sheet
 * is open lives in the saved state, so it comes back after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetasViewModel(
    private val saved: SavedStateHandle,
    reads: GoalReads,
    commands: GoalCommands,
    clock: FinancialClock,
    money: MoneyFormatter,
    ids: RecordIdSource,
    corrections: Corrections,
    screenReader: ScreenReader
) : ViewModel() {
    private val render = GoalsRenderModel(money)
    private val attempts = MutableStateFlow(0)
    private val completer =
        GoalCompleter(saved, commands, corrections, ids, clock, screenReader, viewModelScope)

    /** The board as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<GoalBoard, StorageFailure>()
    private val board = attempts.flatMapLatest { lastGood.keep(Unit, reads.observeBoard()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            ReadState.Loading
        )

    val state: StateFlow<MetasState> =
        combine(
            board,
            clock.today,
            completer.state,
            completer.failures,
            saved.getStateFlow(KEY_HELP, false)
        ) { board, today, completion, failures, help ->
            when (val metas = render.metas(board, today.date)) {
                is MetasState.Ready -> metas.copy(
                    completion = completion,
                    completionFailures = failures,
                    helpOpen = help
                )

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
                // Retry always reads again, also when it clears a failed completion.
                attempts.update { it + 1 }
            } else {
                attempts.update { it + 1 }
            }

            // Handled by the root, which owns navigation.
            is MetasAction.Open -> Unit

            is MetasAction.MarkComplete -> {
                // Older goals are marked too, at the version they show: storage refuses a
                // stale one.
                val read = goodIn(board.value) ?: return
                val card = (state.value as? MetasState.Ready)?.goals?.firstOrNull {
                    it.id ==
                        action.id
                }
                if (card?.reached == true) {
                    render.completionTarget(read, action.id)?.let(completer::complete)
                }
            }

            MetasAction.CelebrationDone -> completer.done()

            MetasAction.ShowHelp -> saved[KEY_HELP] = true

            // The root opens the Guía; coming back finds Metas, not the sheet.
            MetasAction.CloseHelp, MetasAction.OpenGuide -> saved[KEY_HELP] = false
        }
    }

    /** The goals as last read, also after a failed refresh; none before any read. */
    private fun goodIn(board: GoalBoardState): GoalBoard? = when (board) {
        ReadState.Loading -> null
        is ReadState.Ready -> board.value
        is ReadState.Failed -> board.lastGood
    }

    private companion object {
        const val KEY_HELP = "metas.help"

        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
