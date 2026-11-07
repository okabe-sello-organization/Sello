package com.software.sello.feature.resumen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.YearSnapshots
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.read.LastGoodRead
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Tu año. The year shown, the selected month and whether every category is listed are kept
 * in [saved], so they come back after process death. It opens on the route's `year`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YearViewModel(
    private val saved: SavedStateHandle,
    reads: YearSnapshots,
    money: MoneyFormatter,
    months: MonthNames,
    clock: FinancialClock
) : ViewModel() {
    private val render = YearRenderModel(money, months)
    private val attempts = MutableStateFlow(0)

    /** The year as last read, so a Retry or a return never blanks figures already shown. */
    private val lastGood = LastGoodRead<YearSnapshot, SnapshotFailure>()

    private val year: StateFlow<Int> = saved.getStateFlow(
        YEAR,
        saved.get<Int>(ROUTE_YEAR) ?: clock.today.value.date.year
    )

    private val read = combine(year, attempts) { year, _ -> year }
        .flatMapLatest { year ->
            lastGood.keep(year, reads.observe(year)).map { render.render(year, it) }
        }

    val state: StateFlow<YearState> = combine(
        read,
        saved.getStateFlow<String?>(SELECTED, null),
        saved.getStateFlow<Int?>(ALL, null)
    ) { state, selected, all ->
        if (state is YearState.Ready) {
            state.copy(
                selectedMonth = selected?.takeIf { id ->
                    state.months.any { it.chart.id == id && it.amount != null }
                },
                allCategories = all == state.year
            )
        } else {
            state
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        YearState.Loading(year.value)
    )

    fun onAction(action: YearAction) {
        when (action) {
            YearAction.Previous -> move(-1)

            YearAction.Next -> move(1)

            YearAction.Retry -> attempts.update { it + 1 }

            is YearAction.SelectMonth -> select(action.id)

            YearAction.ShowAllCategories -> {
                val current = state.value as? YearState.Ready ?: return
                saved[ALL] = current.year
            }
        }
    }

    /** Only within the years the figures allow: the first with records to the current one. */
    private fun move(by: Int) {
        val current = state.value as? YearState.Ready ?: return
        val allowed = if (by < 0) current.canPrevious else current.canNext
        if (!allowed) return
        saved[SELECTED] = null
        saved[YEAR] = current.year + by
    }

    /** A month that has begun; selecting it again lets it go. */
    private fun select(id: String) {
        val current = state.value as? YearState.Ready ?: return
        if (current.months.none { it.chart.id == id && it.amount != null }) return
        saved[SELECTED] = if (current.selectedMonth == id) null else id
    }

    private companion object {
        /** The year the route opened on, set by navigation. */
        const val ROUTE_YEAR = "year"
        const val YEAR = "tuano.year"
        const val SELECTED = "tuano.month"
        const val ALL = "tuano.allCategories"
    }
}
