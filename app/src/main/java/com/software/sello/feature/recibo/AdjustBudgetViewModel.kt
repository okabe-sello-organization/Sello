package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.policy.LimitAdjustmentPolicy
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.carryFigures
import com.software.sello.presentation.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The month "Ajustar presupuesto" was opened on, from the shared snapshot. The month is
 * fixed when the screen opens, so a month change while it is open never swaps what the
 * person is adjusting. Nothing is added or compared here: order and figures come from
 * [LimitAdjustmentPolicy] and the snapshot.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustBudgetViewModel(
    saved: SavedStateHandle,
    session: MonthSession,
    snapshots: MonthlySnapshots,
    private val money: MoneyFormatter
) : ViewModel() {
    private val render = ReciboRenderModel(money)
    private val attempt = MutableStateFlow(0)
    private val month: YearMonth =
        saved.get<String>(KEY_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
            ?: session.currentMonthNow

    init {
        saved[KEY_MONTH] = month.toString()
    }

    val state: StateFlow<AdjustBudgetState> = attempt
        .flatMapLatest { snapshots.observe(month) }
        .map(::render)
        .stateIn(viewModelScope, SharingStarted.Eagerly, AdjustBudgetState.Loading)

    fun onAction(action: AdjustBudgetAction) {
        when (action) {
            AdjustBudgetAction.Retry -> attempt.update { it + 1 }

            // Handled by the root, which owns the sheet and navigation.
            is AdjustBudgetAction.Open, AdjustBudgetAction.OpenReserved -> Unit
        }
    }

    private fun render(state: SnapshotState): AdjustBudgetState = when (state) {
        ReadState.Loading -> AdjustBudgetState.Loading

        is ReadState.Ready -> ready(state.value, stale = false)

        is ReadState.Failed ->
            state.lastGood?.let { ready(it, stale = true) } ?: AdjustBudgetState.Failed
    }

    private fun ready(snapshot: MonthlySnapshot, stale: Boolean): AdjustBudgetState.Ready {
        val order = LimitAdjustmentPolicy.order(snapshot.categories)
        return AdjustBudgetState.Ready(
            month = snapshot.month,
            summary = render.summary(snapshot),
            limit = (snapshot.overall as? OverallBudget.Limited)?.budget?.limit?.let(money::format),
            spent = money.format(snapshot.spent),
            limited = order.limited.filterNot { it.category.archived }.map(::row),
            open = order.open.filterNot { it.category.archived }.map(::row),
            stale = stale,
            // Archived ones last and apart, in the same order as above.
            archived = (order.limited + order.open).filter { it.category.archived }.map(::row),
            carry = (snapshot.overall as? OverallBudget.Limited)?.budget
                ?.let { money.carryFigures(it, snapshot.month, withSpent = false) }
        )
    }

    private fun row(line: CategoryMonth): AdjustRow {
        val budget = line.budget
        return AdjustRow(
            id = line.category.id.value,
            name = line.category.name.value,
            iconKey = line.category.icon.value,
            spent = money.format(line.spent),
            limit = budget?.let { money.format(it.limit) },
            remaining = budget?.let { money.format(it.remaining) },
            overBy = budget?.remaining?.takeIf { it.minorUnits < 0 }?.let(money::magnitude),
            fraction = budget?.let(render::fill),
            over = budget != null && budget.remaining.minorUnits < 0,
            archived = line.category.archived,
            unlimited = (line.state as? MonthBudgetState.Limited)?.limit == BudgetLimit.Unlimited,
            carried = budget?.carry != null
        )
    }

    companion object {
        const val KEY_MONTH = "month"
    }
}
