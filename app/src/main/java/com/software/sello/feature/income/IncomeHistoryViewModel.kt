package com.software.sello.feature.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.IncomePlan
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.policy.IncomePlanPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where the month's income went, from the same summary Resumen shows: what the category
 * budgets take and what is left unassigned, signed when the budgets add up to more than
 * came in ([over]). [allocatedShare] is how much of the bar is drawn solid; it is
 * geometry only, never shown as a number or turned back into money.
 */
data class IncomeSplit(
    val income: MoneyTextValue,
    val allocated: MoneyTextValue,
    val unallocated: MoneyTextValue,
    val over: Boolean,
    val allocatedShare: Float
)

data class IncomeLine(
    val id: String,
    val amount: MoneyTextValue,
    val sourceKey: String,
    val sourceName: String?,
    val date: LocalDate,
    val note: String?
)

/** One income a schedule records later this month. [key] tells it from the rest. */
data class ExpectedLine(
    val key: String,
    val sourceKey: String,
    val sourceName: String?,
    val date: LocalDate,
    val amount: MoneyTextValue
)

/**
 * Plan del mes by ADR 0012's BG3: [recorded] income, the income still [expected],
 * [planned] as both and [unassigned] as the plan less the category budgets, signed
 * when they take more ([over]). Expected money is shown here only, never in a total.
 */
data class IncomePlanView(
    val recorded: MoneyTextValue,
    val expected: MoneyTextValue,
    val planned: MoneyTextValue,
    val unassigned: MoneyTextValue,
    val over: Boolean,
    val items: List<ExpectedLine>
)

sealed interface IncomePlanState {
    /** No plan: nothing more is expected, the month is not this one, or it is still read. */
    data object None : IncomePlanState

    /** The schedules or the month's summary could not be read, or a sum does not fit. */
    data object Failed : IncomePlanState

    data class Shown(val plan: IncomePlanView) : IncomePlanState
}

sealed interface IncomeHistoryState {
    data object Loading : IncomeHistoryState

    data object Failed : IncomeHistoryState

    data class Ready(
        val month: YearMonth,
        val total: MoneyTextValue,
        val lines: List<IncomeLine>,
        val more: Boolean,
        val busy: Boolean = false,
        val pageFailed: Boolean = false
    ) : IncomeHistoryState
}

sealed interface IncomeHistoryAction {
    data object Retry : IncomeHistoryAction

    data object More : IncomeHistoryAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeHistoryViewModel(
    private val reads: IncomeReads,
    private val session: MonthSession,
    snapshots: ResumenSnapshots,
    schedules: ScheduleReads,
    private val money: MoneyFormatter,
    clock: FinancialClock
) : ViewModel() {
    val today = clock.today.map {
        it.date
    }.stateIn(viewModelScope, SharingStarted.Eagerly, clock.today.value.date)

    /** Null while it is read, or when it could not be: never shown as zero. */
    val split: StateFlow<IncomeSplit?> = session.selectedMonth
        .flatMapLatest(snapshots::observe)
        .map { read -> (read as? ResumenSnapshotState.Ready)?.snapshot?.let(::split) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun split(summary: ResumenSnapshot): IncomeSplit {
        val income = summary.income.minorUnits
        val allocated = summary.allocated.minorUnits
        val share = when {
            income <= 0 -> 0f
            allocated >= income -> 1f
            else -> (allocated.toDouble() / income.toDouble()).toFloat().coerceIn(0f, 1f)
        }
        return IncomeSplit(
            income = money.format(summary.income),
            allocated = money.format(summary.allocated),
            unallocated = money.format(summary.unallocated),
            over = summary.unallocated.minorUnits < 0,
            allocatedShare = share
        )
    }

    private val shown = MutableStateFlow<IncomeHistoryState>(IncomeHistoryState.Loading)
    val state: StateFlow<IncomeHistoryState> = shown.asStateFlow()
    private val attempts = MutableStateFlow(0)
    private var cursor: IncomeCursor? = null
    private var snapshot: IncomeMonth? = null
    private var epoch = 0

    /**
     * Plan del mes, for the month in progress only: a past month's plan has nothing
     * left to expect. The month's summary and the schedules are put together only when
     * both were read at the same revision, so an income catch-up has just recorded is
     * never counted as both recorded and expected.
     */
    val plan: StateFlow<IncomePlanState> = combine(
        session.selectedMonth,
        clock.today.map { YearMonth.from(it.date) }.distinctUntilChanged(),
        attempts
    ) { selected, current, _ -> selected.takeIf { it == current } }
        .flatMapLatest { month ->
            if (month == null) {
                flowOf(IncomePlanState.None)
            } else {
                combine(snapshots.observe(month), schedules.observe()) { summary, book ->
                    plan(month, summary, book)
                }.filterNotNull().onStart { emit(IncomePlanState.None) }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, IncomePlanState.None)

    /** Null while the two reads disagree or one is still loading: the last plan stays. */
    private fun plan(
        month: YearMonth,
        summary: ResumenSnapshotState,
        book: ScheduleBookState
    ): IncomePlanState? {
        val schedules = when (book) {
            ScheduleBookState.Loading -> return null
            is ScheduleBookState.Failed -> return IncomePlanState.Failed
            is ScheduleBookState.Ready -> book.book
        }
        val expected = IncomePlanPolicy.expected(schedules.schedules, month)
        if (expected.isEmpty()) return IncomePlanState.None
        val read = when (summary) {
            ResumenSnapshotState.Loading -> return null
            is ResumenSnapshotState.Failed -> return IncomePlanState.Failed
            is ResumenSnapshotState.Ready -> summary.snapshot
        }
        if (read.budget.generation != schedules.generation ||
            read.budget.revision != schedules.revision
        ) {
            return null
        }
        return when (val plan = IncomePlanPolicy.plan(read.income, read.allocated, expected)) {
            is Outcome.Failure -> IncomePlanState.Failed

            is Outcome.Success -> plan.value?.let { IncomePlanState.Shown(view(it)) }
                ?: IncomePlanState.None
        }
    }

    private fun view(plan: IncomePlan) = IncomePlanView(
        recorded = money.format(plan.recorded),
        expected = money.format(plan.expected),
        planned = money.format(plan.planned),
        unassigned = money.format(plan.unassigned),
        over = plan.unassigned.minorUnits < 0,
        items = plan.items.map {
            ExpectedLine(
                "${it.schedule.value}.${it.occurrence.date}.${it.occurrence.slot}",
                it.source.key,
                (it.source as? IncomeSource.Other)?.name?.value,
                it.occurrence.date,
                money.format(it.amount.money)
            )
        }
    )

    init {
        viewModelScope.launch {
            combine(session.selectedMonth, attempts) { month, _ -> month }
                .flatMapLatest(reads::observeMonth).collectLatest { state ->
                    val epoch = ++epoch
                    cursor = null
                    snapshot = null
                    shown.value = IncomeHistoryState.Loading
                    when (state) {
                        IncomeMonthState.Loading -> Unit

                        is IncomeMonthState.Failed -> shown.value = IncomeHistoryState.Failed

                        is IncomeMonthState.Ready -> {
                            snapshot = state.month
                            page(state.month, first = true, epoch)
                        }
                    }
                }
        }
    }

    fun onAction(action: IncomeHistoryAction) {
        when (action) {
            IncomeHistoryAction.Retry -> attempts.update { it + 1 }

            IncomeHistoryAction.More -> {
                val current = shown.value as? IncomeHistoryState.Ready ?: return
                val snapshot = snapshot ?: return
                if (current.busy || !current.more) return
                shown.value = current.copy(busy = true, pageFailed = false)
                val epoch = epoch
                viewModelScope.launch { page(snapshot, first = false, epoch) }
            }
        }
    }

    private suspend fun page(expected: IncomeMonth, first: Boolean, epoch: Int) {
        val page = reads.inMonth(expected.month, if (first) null else cursor, PAGE_SIZE)
        val checked = reads.month(expected.month)
        if (this.epoch != epoch || snapshot != expected ||
            session.selectedMonthNow != expected.month
        ) {
            return
        }
        if (checked is Outcome.Failure || page is Outcome.Failure) {
            val previous = shown.value as? IncomeHistoryState.Ready
            shown.value = if (first || previous == null) {
                IncomeHistoryState.Failed
            } else {
                previous.copy(busy = false, pageFailed = true)
            }
            return
        }
        val latest = (checked as Outcome.Success).value
        if (latest.generation != expected.generation || latest.revision != expected.revision) {
            attempts.update { it + 1 }
            return
        }
        val result = (page as Outcome.Success).value
        val previous = if (first) {
            emptyList()
        } else {
            (shown.value as? IncomeHistoryState.Ready)?.lines.orEmpty()
        }
        cursor = result.next
        shown.value = IncomeHistoryState.Ready(
            expected.month,
            money.format(expected.total),
            previous + result.incomes.map {
                IncomeLine(
                    it.id.value,
                    money.format(it.amount.money),
                    it.source.key,
                    (it.source as? IncomeSource.Other)?.name?.value,
                    it.date,
                    it.note?.value
                )
            },
            more = result.next != null
        )
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
