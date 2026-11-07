package com.software.sello.feature.movements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.MonthMovements
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Día a día (SELLO-192): the shared month's expenses and incomes by day, in pages. The
 * month is observed through Resumen's snapshot, so the summary is Recibo's and Resumen's
 * own figures and any change, an edit or its undo, reads the list again. The chip
 * survives process death; the month is the session's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayByDayViewModel(
    private val saved: SavedStateHandle,
    session: MonthSession,
    private val snapshots: ResumenSnapshots,
    private val movements: MonthMovements,
    private val money: MoneyFormatter
) : ViewModel() {
    private val filter = saved.getStateFlow(KEY_FILTER, MovementFilter.All.name)
    private val wanted = MutableStateFlow(PAGE_SIZE)
    private val retry = MutableStateFlow(0)

    /** The month as last read, so a Retry or a return never blanks rows already listed. */
    private val lastGood = LastGoodRead<ResumenSnapshot, SnapshotFailure>()

    /** The month and chip whose rows [state] holds, to tell its own rows from another's. */
    private var listedFor: Request? = null

    private data class Request(val month: YearMonth, val filter: MovementFilter)

    val state: StateFlow<DayByDayState> = combine(session.selectedMonth, filter) { month, chip ->
        Request(month, MovementFilter.entries.firstOrNull { it.name == chip } ?: MovementFilter.All)
    }.flatMapLatest { request ->
        wanted.value = PAGE_SIZE
        // Reintentar observes the month again, as well as reading the rows again.
        combine(
            retry.flatMapLatest { lastGood.keep(request.month, snapshots.observe(request.month)) },
            wanted
        ) { snapshot, count -> snapshot to count }
            .flatMapLatest { (snapshot, count) -> load(request, snapshot, count) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayByDayState.Loading)

    fun onAction(action: DayByDayAction) {
        when (action) {
            is DayByDayAction.Filter -> saved[KEY_FILTER] = action.filter.name

            DayByDayAction.Retry -> retry.update { it + 1 }

            DayByDayAction.More -> {
                val ready = state.value as? DayByDayState.Ready
                if (ready != null && ready.more && !ready.busy && !ready.stale &&
                    wanted.value <= Int.MAX_VALUE - PAGE_SIZE
                ) {
                    wanted.update { it + PAGE_SIZE }
                }
            }
        }
    }

    private fun load(request: Request, snapshot: ResumenSnapshotState, count: Int) = flow {
        // Rows already shown for this month and chip are kept through every failure.
        val listed = (state.value as? DayByDayState.Ready)?.takeIf { listedFor == request }
        val month = when (snapshot) {
            ReadState.Loading -> {
                emit(listed?.copy(busy = true) ?: DayByDayState.Loading)
                return@flow
            }

            // Kept, said to be older; no more pages from them: Reintentar is the way on.
            is ReadState.Failed -> {
                emit(
                    listed?.copy(busy = false, pageFailed = false, more = false, stale = true)
                        ?: DayByDayState.Failed(DayByDayProblem.Unread)
                )
                return@flow
            }

            is ReadState.Ready -> snapshot.value
        }
        emit(listed?.copy(busy = true, pageFailed = false) ?: DayByDayState.Loading)
        val rows = mutableListOf<Movement>()
        var more = false
        var totalCount = 0L
        var days: Map<LocalDate, Money> = emptyMap()
        while (rows.size < count) {
            val read = movements.read(
                request.month,
                request.filter,
                rows.size,
                minOf(count - rows.size, MonthMovements.MAX_PAGE)
            )
            when (read) {
                is Outcome.Failure -> {
                    val problem = when (read.error) {
                        MovementReadFailure.Unrepresentable -> DayByDayProblem.TooLarge
                        is MovementReadFailure.Storage -> DayByDayProblem.Unread
                    }
                    emit(
                        listed?.copy(busy = false, pageFailed = true)
                            ?: DayByDayState.Failed(problem)
                    )
                    return@flow
                }

                is Outcome.Success -> {
                    val page = read.value
                    // Read between two changes: the next snapshot reads it again.
                    if (page.generation != month.budget.generation ||
                        page.revision != month.budget.revision
                    ) {
                        emit(listed?.copy(busy = true) ?: DayByDayState.Loading)
                        return@flow
                    }
                    rows += page.movements
                    more = page.more
                    totalCount = page.totalCount
                    days = page.days
                    if (!more) break
                }
            }
        }
        listedFor = request
        emit(
            DayByDayState.Ready(
                month = request.month,
                today = month.budget.asOf,
                filter = request.filter,
                spent = money.format(month.budget.spent),
                income = money.format(month.income),
                days = groups(rows, days),
                totalCount = totalCount,
                more = more
            )
        )
    }

    /** Consecutive rows of one day under its heading, with the day's whole expense sum. */
    private fun groups(rows: List<Movement>, days: Map<LocalDate, Money>): List<DayGroup> =
        rows.groupBy { it.date }.map { (date, movements) ->
            DayGroup(date, days[date]?.let(money::format), movements.map(::row))
        }

    private fun row(movement: Movement): DayRow = when (movement) {
        is Movement.Spent -> DayRow.Spent(
            id = movement.expense.id.value,
            note = movement.expense.note?.value,
            amount = money.format(movement.expense.amount.money),
            categoryName = movement.category.name.value,
            iconKey = movement.category.icon.value,
            fixed = movement.expense.origin != null,
            archived = movement.category.archived
        )

        is Movement.Received -> DayRow.Received(
            id = movement.income.id.value,
            note = movement.income.note?.value,
            amount = money.format(movement.income.amount.money, signed = true),
            sourceKey = movement.income.source.key,
            sourceName = (movement.income.source as? IncomeSource.Other)
                ?.name?.value
        )
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val KEY_FILTER = "dayByDay.filter"
    }
}
