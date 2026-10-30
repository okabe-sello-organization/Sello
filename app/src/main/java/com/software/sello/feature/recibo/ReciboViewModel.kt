package com.software.sello.feature.recibo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.ClosedMonthInvitePolicy
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.ClosedMonthInvitePreferences
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shows the selected month from the shared snapshot, through [ReciboRenderModel]:
 * no amount is added, compared or defaulted here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboViewModel(
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    preferences: CategoryOrderingPreferences,
    money: MoneyFormatter,
    private val invitations: ClosedMonthInvitePreferences,
    clock: FinancialClock,
    private val categories: CategoryReads
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

    /** What was handled in this session, so the line goes even when keeping it fails. */
    private val handledHere = MutableStateFlow<YearMonth?>(null)

    /**
     * The latest month handled, kept or only in this session. A failed read hides nothing:
     * it is only advice.
     */
    private val handled = combine(
        invitations.observeHandled().map { read ->
            when (read) {
                is PreferenceRead.Ready -> read.value
                is PreferenceRead.Failed -> read.lastGood
            }
        },
        handledHere
    ) { kept, here -> listOfNotNull(kept, here).maxOrNull() }

    /**
     * The month just closed, on the financial day, while it had expenses and was not yet
     * handled. Its read is only made when the invitation could show, and a failed read
     * hides the line: it is advice, never a figure.
     */
    private val invitation = combine(
        session.selectedMonth,
        clock.today,
        handled
    ) { shown, today, done ->
        ClosedMonthInvitePolicy.monthToReview(today.date, shown)?.takeIf {
            done == null || it > done
        }
    }
        .distinctUntilChanged()
        .flatMapLatest { month ->
            if (month == null) {
                flowOf(null)
            } else {
                snapshots.observe(month).map { read ->
                    val hadExpenses = read is ReadState.Ready && read.value.spent.minorUnits > 0
                    ReciboInvitation(month).takeIf { hadExpenses }
                }
            }
        }
        // Recibo's first state never waits on a preference read: no line until it is known.
        .onStart { emit(null) }

    val state: StateFlow<ReciboState> =
        combine(session.selectedMonth, attempt) { month, _ -> month }
            .flatMapLatest { month ->
                snapshots.observe(month)
                    .combine(ordering) { snapshot, preference ->
                        render.recibo(snapshot, preference)
                    }
                    .mapLatest { named(it, month) }
            }
            .combine(invitation) { state, invitation ->
                if (state is ReciboState.Month) state.copy(invitation = invitation) else state
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                ReciboState.Loading
            )

    /**
     * A carry failure with no earlier read to take the name from: the category is named
     * from the month's categories, so the repair can say which one. Without them it is
     * said without a name, never left out.
     */
    private suspend fun named(state: ReciboState, month: YearMonth): ReciboState {
        val failed = state as? ReciboState.CarryFailed ?: return state
        if (failed.problem.name != null) return state
        val name = (categories.monthBudget(month) as? Outcome.Success)?.value?.categories
            ?.firstOrNull {
                it.category.id.value == failed.problem.categoryId
            }?.category?.name?.value
        return failed.copy(problem = failed.problem.copy(name = name))
    }

    fun onAction(action: ReciboAction) {
        when (action) {
            // A new attempt starts a new observation of the same month.
            ReciboAction.Retry -> attempt.update { it + 1 }

            // Remembered either way; opening the month is the root's.
            is ReciboAction.OpenInvitation -> handle(action.month)

            is ReciboAction.DismissInvitation -> handle(action.month)

            // Handled by the root, which owns navigation and the selection.
            ReciboAction.CreateCategory,
            is ReciboAction.OpenCategory,
            is ReciboAction.EditCategory,
            ReciboAction.OpenReserved,
            ReciboAction.AdjustBudget,
            is ReciboAction.SetMonthLimit -> Unit
        }
    }

    /** Gone for [month] at once, and across restarts when it can be kept. */
    private fun handle(month: YearMonth) {
        handledHere.update { maxOf(it ?: month, month) }
        viewModelScope.launch { invitations.markHandled(month) }
    }

    private companion object {
        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
