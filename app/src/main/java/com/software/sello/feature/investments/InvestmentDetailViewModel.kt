package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.NudgePolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One investment from the board as it changes. Whether every movement is shown and the
 * chart's chosen value and the open help sheet live in [saved], so they come back after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentDetailViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    clock: FinancialClock,
    money: MoneyFormatter,
    private val guidance: GuidancePreferences
) : ViewModel() {
    private val render = InvestmentsRenderModel(money)
    private val id: String =
        checkNotNull(saved[KEY_INVESTMENT]) { "An investment's detail needs its id" }
    private val attempts = MutableStateFlow(0)

    /** The board as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<InvestmentBoard, StorageFailure>()

    /** Whether the nudge was closed in this session, so it goes even when keeping that fails. */
    private val closedHere = MutableStateFlow(false)

    /**
     * The day the investments nudge shows on (SELLO-213), or null: the first time a page
     * opens, by the shared rule. Only a guidance read that worked counts.
     */
    private val nudge = combine(guidance.observeGuidance(), clock.today, closedHere) {
            read,
            today,
            closed
        ->
        if (read !is PreferenceRead.Ready || closed) return@combine null
        NudgePolicy.pick(read.value, today.date, listOf(Nudge.Investments))
            ?.let {
                Showing(
                    ShownNudge(it, today.date),
                    NudgePolicy.keptFor(read.value.memory, it, today.date)
                )
            }
    }
        // The page never waits on this read: no nudge until it is known.
        .onStart { emit(null) }
        .distinctUntilChanged()

    /** The showing last asked to be kept, so one showing is written once. */
    private var keeping: ShownNudge? = null

    val state: StateFlow<InvestmentDetailState> = combine(
        attempts.flatMapLatest { lastGood.keep(Unit, reads.observeBoard()) },
        clock.today,
        saved.getStateFlow(KEY_ALL, false),
        saved.getStateFlow<String?>(KEY_SELECTED, null),
        saved.getStateFlow<String?>(KEY_HELP, null)
    ) { board, today, all, selected, help ->
        when (val detail = render.detail(board, id, today.date)) {
            is InvestmentDetailState.Ready -> detail.copy(
                allMovements = all,
                selectedValue = selected,
                help = investmentHelp(help)
            )

            else -> detail
        }
    }
        // Only current figures carry the nudge: never while loading or failed, nor over older ones.
        .combine(nudge) { detail, nudge ->
            val shown = nudge.takeIf { detail is InvestmentDetailState.Ready && !detail.stale }
            keepShown(shown)
            val ready = detail as? InvestmentDetailState.Ready
            ready?.copy(nudge = shown != null) ?: detail
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            InvestmentDetailState.Loading
        )

    fun onAction(action: InvestmentDetailAction) {
        when (action) {
            InvestmentDetailAction.Retry -> attempts.update { it + 1 }

            InvestmentDetailAction.CloseNudge -> {
                closedHere.value = true
                viewModelScope.launch { guidance.markSeen(Nudge.Investments) }
            }

            InvestmentDetailAction.ShowAllMovements -> saved[KEY_ALL] = true

            is InvestmentDetailAction.SelectValue -> saved[KEY_SELECTED] = action.id

            is InvestmentDetailAction.ShowHelp -> saved[KEY_HELP] = action.help.name

            // The root opens the Guía; coming back finds the page, not the sheet.
            InvestmentDetailAction.CloseHelp, InvestmentDetailAction.OpenGuide ->
                saved[KEY_HELP] = null

            // Handled by the root, which owns navigation.
            InvestmentDetailAction.Edit,
            InvestmentDetailAction.RecordValue,
            is InvestmentDetailAction.OpenMovement -> Unit
        }
    }

    /**
     * Keeps the day the nudge showed on, unless it is kept already; the same write keeps an
     * expired nudge as seen. A failed write is not retried until something changes.
     */
    private fun keepShown(shown: Showing?) {
        val showing = shown?.takeUnless { it.kept }?.nudge ?: return
        if (keeping == showing) return
        keeping = showing
        viewModelScope.launch { guidance.markShown(showing.nudge, showing.day) }
    }

    /** The nudge showing and its day, and whether that day is kept already. */
    private data class Showing(val nudge: ShownNudge, val kept: Boolean)

    companion object {
        /** Route argument: the investment to show. */
        const val KEY_INVESTMENT = "investmentId"

        private const val KEY_ALL = "investment.detail.all"
        private const val KEY_SELECTED = "investment.detail.selected"
        private const val KEY_HELP = "investment.detail.help"
        private const val STOP_AFTER_MILLIS = 5_000L
    }
}
