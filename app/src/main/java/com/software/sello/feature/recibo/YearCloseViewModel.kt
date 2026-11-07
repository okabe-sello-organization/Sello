package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.YearSnapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * The year's big moment. Which year is open is kept in [saved], so it survives process
 * death; the year and the goals are read only while it is open, never on Recibo's start.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YearCloseViewModel(
    private val saved: SavedStateHandle,
    years: YearSnapshots,
    goals: GoalReads
) : ViewModel() {
    val state: StateFlow<YearCloseState> = saved.getStateFlow<Int?>(OPEN, null)
        .flatMapLatest { year ->
            if (year == null) {
                flowOf(YearCloseState.Closed)
            } else {
                combine(years.observe(year), goals.observeBoard()) { yearRead, goalRead ->
                    when {
                        // A count from a read that failed is never shown, not even an older one.
                        yearRead is ReadState.Failed || goalRead is ReadState.Failed ->
                            YearCloseState.Failed(year)

                        yearRead is ReadState.Ready && goalRead is ReadState.Ready ->
                            yearClose(yearRead.value, goalRead.value)

                        else -> YearCloseState.Loading(year)
                    }
                }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            saved.get<Int>(OPEN)?.let { YearCloseState.Loading(it) } ?: YearCloseState.Closed
        )

    fun onAction(action: YearCloseAction) {
        when (action) {
            is YearCloseAction.Open -> saved[OPEN] = action.year
            YearCloseAction.Done -> saved[OPEN] = null
        }
    }

    private companion object {
        const val OPEN = "yearClose.year"
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
