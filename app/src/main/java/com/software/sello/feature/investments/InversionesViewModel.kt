package com.software.sello.feature.investments

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

/** Inversiones from the investment board as it changes. It is not tied to a month. */
@OptIn(ExperimentalCoroutinesApi::class)
class InversionesViewModel(
    reads: InvestmentReads,
    clock: FinancialClock,
    money: MoneyFormatter
) : ViewModel() {
    private val render = InvestmentsRenderModel(money)
    private val attempts = MutableStateFlow(0)
    private val board = attempts.flatMapLatest { reads.observeBoard() }

    val state: StateFlow<InversionesState> =
        combine(board, clock.today) { board, today -> render.overview(board, today.date) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                InversionesState.Loading
            )

    fun onAction(action: InversionesAction) {
        when (action) {
            // A new attempt starts a new observation.
            InversionesAction.Retry -> attempts.update { it + 1 }

            // Handled by the root, which owns navigation.
            is InversionesAction.Open -> Unit
        }
    }

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
