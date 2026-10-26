package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentReads
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
 * One investment from the board as it changes. Whether every movement is shown and the
 * chart's chosen value live in [saved], so they come back after a restart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentDetailViewModel(
    private val saved: SavedStateHandle,
    reads: InvestmentReads,
    clock: FinancialClock,
    money: MoneyFormatter
) : ViewModel() {
    private val render = InvestmentsRenderModel(money)
    private val id: String =
        checkNotNull(saved[KEY_INVESTMENT]) { "An investment's detail needs its id" }
    private val attempts = MutableStateFlow(0)

    val state: StateFlow<InvestmentDetailState> = combine(
        attempts.flatMapLatest { reads.observeBoard() },
        clock.today,
        saved.getStateFlow(KEY_ALL, false),
        saved.getStateFlow<String?>(KEY_SELECTED, null)
    ) { board, today, all, selected ->
        when (val detail = render.detail(board, id, today.date)) {
            is InvestmentDetailState.Ready -> detail.copy(
                allMovements = all,
                selectedValue = selected
            )

            else -> detail
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
        InvestmentDetailState.Loading
    )

    fun onAction(action: InvestmentDetailAction) {
        when (action) {
            InvestmentDetailAction.Retry -> attempts.update { it + 1 }

            InvestmentDetailAction.ShowAllMovements -> saved[KEY_ALL] = true

            is InvestmentDetailAction.SelectValue -> saved[KEY_SELECTED] = action.id

            // Handled by the root, which owns navigation.
            InvestmentDetailAction.Edit,
            InvestmentDetailAction.RecordValue,
            is InvestmentDetailAction.OpenMovement -> Unit
        }
    }

    companion object {
        /** Route argument: the investment to show. */
        const val KEY_INVESTMENT = "investmentId"

        private const val KEY_ALL = "investment.detail.all"
        private const val KEY_SELECTED = "investment.detail.selected"
        private const val STOP_AFTER_MILLIS = 5_000L
    }
}
