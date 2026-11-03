package com.software.sello.feature.investments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** Dólar from the investment board's rates as they change. */
@OptIn(ExperimentalCoroutinesApi::class)
class UsdRateViewModel(reads: InvestmentReads, clock: FinancialClock, money: MoneyFormatter) :
    ViewModel() {
    private val render = InvestmentsRenderModel(money)
    private val attempts = MutableStateFlow(0)

    /** The board as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<InvestmentBoard, StorageFailure>()
    private val board = attempts.flatMapLatest { lastGood.keep(Unit, reads.observeBoard()) }

    val state: StateFlow<UsdRateState> =
        combine(board, clock.today) { board, today ->
            when (board) {
                ReadState.Loading -> UsdRateState.Loading

                is ReadState.Ready -> ready(board.value.rates, today.date, false)

                is ReadState.Failed -> board.lastGood?.let {
                    ready(it.rates, today.date, true)
                } ?: UsdRateState.Failed
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            UsdRateState.Loading
        )

    private fun ready(book: UsdRateBook, today: LocalDate, stale: Boolean): UsdRateState.Ready {
        val newest = book.history.sortedWith(
            compareByDescending<UsdRateEntry> { it.date }
                .thenByDescending { it.sequence }
        )
        return UsdRateState.Ready(
            current = book.current?.let { RateModel(render.rate(it.rate), it.date) },
            history = newest.filterNot { it == book.current }
                .map { RateModel(render.rate(it.rate), it.date) },
            today = today,
            stale = stale
        )
    }

    fun onAction(action: UsdRateAction) {
        when (action) {
            UsdRateAction.Retry -> attempts.update { it + 1 }

            // Handled by the root, which owns navigation.
            UsdRateAction.Change -> Unit
        }
    }

    private companion object {
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
