package com.software.sello.feature.recibo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
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
 * Shows the selected month from the shared snapshot, through [ReciboRenderModel]:
 * no amount is added, compared or defaulted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboViewModel(
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    preferences: CategoryOrderingPreferences,
    money: MoneyFormatter
) : ViewModel() {
    private val render = ReciboRenderModel(money)
    private val attempt = MutableStateFlow(0)

    private val ordering = preferences.observeCategoryOrdering()
        .map { result ->
            when (result) {
                is PreferenceRead.Ready -> result.value
                is PreferenceRead.Failed -> result.lastGood ?: CategoryOrderingPreference()
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
            CategoryOrderingPreference()
        )

    val state: StateFlow<ReciboState> =
        combine(session.selectedMonth, attempt) { month, _ -> month }
            .flatMapLatest(snapshots::observe)
            .combine(ordering) { snapshot, preference -> render.recibo(snapshot, preference) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                ReciboState.Loading
            )

    fun onAction(action: ReciboAction) {
        when (action) {
            // A new attempt starts a new observation of the same month.
            ReciboAction.Retry -> attempt.update { it + 1 }

            // Handled by the root, which owns navigation and the selection.
            ReciboAction.CreateCategory,
            is ReciboAction.OpenCategory,
            is ReciboAction.EditCategory,
            ReciboAction.OpenReserved -> Unit
        }
    }

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
