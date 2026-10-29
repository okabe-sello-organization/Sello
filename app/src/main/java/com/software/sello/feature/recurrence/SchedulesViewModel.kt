package com.software.sello.feature.recurrence

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.policy.ScheduleStatus
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.SnapshotState
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ScheduleTab { Payments, Incomes }

/**
 * Where a schedule sits in the list on the financial day, in this order: something still
 * [Due] this month, this month already [Recorded], nothing this month ([Later]), [Paused]
 * or [Ended].
 */
enum class ScheduleGroup { Due, Recorded, Later, Paused, Ended }

/**
 * One schedule's row. A payment is named by its note, else its category ([categoryName]
 * with [iconKey]); an income by its note, else its source ([sourceKey], [sourceName]).
 * [soon] says one is still to come this month; [pausedFrom] and [endedOn] dim it. [group]
 * places it in the list; [recordedOn] is when this month's last occurrence went through.
 */
data class ScheduleRow(
    val id: String,
    val note: String?,
    val categoryName: String?,
    val iconKey: String?,
    val sourceKey: String?,
    val sourceName: String?,
    val rule: RepeatRule,
    val start: LocalDate,
    val next: LocalDate?,
    val amount: MoneyTextValue,
    val soon: Boolean,
    val pausedFrom: LocalDate?,
    val endedOn: LocalDate?,
    val group: ScheduleGroup = ScheduleGroup.Due,
    val recordedOn: LocalDate? = null
)

/** The month as a plan: what went through, what is still to come, both. Never recorded money. */
data class ScheduleMonthPlan(
    val month: YearMonth,
    val done: MoneyTextValue,
    val doneCount: Int,
    val toCome: MoneyTextValue,
    val toComeCount: Int,
    val total: MoneyTextValue,
    val active: Int,
    /** The minor units behind [done] and [total], only to draw the bar's share. */
    val doneUnits: Long = 0,
    val totalUnits: Long = 0
)

sealed interface SchedulesState {
    data object Loading : SchedulesState

    /** The schedules or the categories could not be read: never shown as none. */
    data object Failed : SchedulesState

    /**
     * The [tab]'s schedules: [rows] by day, then [ended]. [plan] is null only when a sum
     * does not fit an exact amount, and then the plan is not shown.
     */
    data class Ready(
        val tab: ScheduleTab,
        val plan: ScheduleMonthPlan?,
        val rows: List<ScheduleRow>,
        val ended: List<ScheduleRow>
    ) : SchedulesState {
        val empty: Boolean get() = rows.isEmpty() && ended.isEmpty()
    }
}

sealed interface SchedulesAction {
    data class TabPicked(val tab: ScheduleTab) : SchedulesAction

    data object Retry : SchedulesAction
}

/**
 * Pagos e ingresos fijos (docs/design/v2/fijos.html): every schedule of one kind, with
 * the current month as a plan. Payments are named from the month's categories, archived
 * ones included, read from the same snapshot Recibo uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModel(
    private val saved: SavedStateHandle,
    schedules: ScheduleReads,
    snapshots: MonthlySnapshots,
    clock: FinancialClock,
    private val money: MoneyFormatter
) : ViewModel() {
    private val attempts = MutableStateFlow(0)

    val today: StateFlow<LocalDate> = clock.today.map { it.date }
        .stateIn(viewModelScope, SharingStarted.Eagerly, clock.today.value.date)
    private val tab = saved.getStateFlow(KEY_TAB, initialTab().name)

    private fun initialTab(): ScheduleTab =
        if (saved.get<Boolean>(KEY_INCOME) == true) ScheduleTab.Incomes else ScheduleTab.Payments

    val state: StateFlow<SchedulesState> = combine(
        clock.today.map { it.date }.distinctUntilChanged(),
        attempts
    ) { today, _ -> today }
        .flatMapLatest { today ->
            combine(
                schedules.observe(),
                snapshots.observe(YearMonth.from(today)),
                tab
            ) { book, month, tab -> render(today, book, month, ScheduleTab.valueOf(tab)) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SchedulesState.Loading)

    fun onAction(action: SchedulesAction) {
        when (action) {
            is SchedulesAction.TabPicked -> saved[KEY_TAB] = action.tab.name
            SchedulesAction.Retry -> attempts.update { it + 1 }
        }
    }

    private fun render(
        today: LocalDate,
        book: ScheduleBookState,
        month: SnapshotState,
        tab: ScheduleTab
    ): SchedulesState {
        val stored = when (book) {
            ReadState.Loading -> return SchedulesState.Loading
            is ReadState.Failed -> return SchedulesState.Failed
            is ReadState.Ready -> book.value.schedules
        }
        val snapshot = when (month) {
            SnapshotState.Loading -> return SchedulesState.Loading
            is SnapshotState.Failed -> return SchedulesState.Failed
            is SnapshotState.Ready -> month.snapshot
        }
        val categories = snapshot.categories.associateBy { it.category.id }
        val current = YearMonth.from(today)
        val lines = ScheduleListPolicy.lines(
            stored.filter {
                (it.schedule.entry is ScheduledEntry.Income) ==
                    (tab == ScheduleTab.Incomes)
            },
            today,
            current
        )
        val plan = (ScheduleListPolicy.month(lines, current) as? Outcome.Success)?.value
        val rows = lines.map { line ->
            val schedule = line.stored.schedule
            val entry = schedule.entry
            val category = (entry as? ScheduledEntry.Payment)?.let { categories[it.categoryId] }
            val source = (entry as? ScheduledEntry.Income)?.source
            line.status to ScheduleRow(
                id = schedule.id.value,
                note = entry.note?.value,
                categoryName = category?.category?.name?.value,
                iconKey = category?.category?.icon?.value,
                sourceKey = source?.key,
                sourceName = (source as? IncomeSource.Other)?.name?.value,
                rule = schedule.recurrence.rule,
                start = schedule.recurrence.start,
                next = line.next,
                amount = money.format(entry.amount.money),
                soon = line.toComeThisMonth,
                pausedFrom = line.pausedFrom,
                endedOn = schedule.recurrence.end.takeIf { line.status == ScheduleStatus.Ended },
                group = when {
                    line.status == ScheduleStatus.Ended -> ScheduleGroup.Ended
                    line.status == ScheduleStatus.Paused -> ScheduleGroup.Paused
                    line.toComeThisMonth -> ScheduleGroup.Due
                    line.recordedThisMonth != null -> ScheduleGroup.Recorded
                    else -> ScheduleGroup.Later
                },
                recordedOn = line.recordedThisMonth
            )
        }
        return SchedulesState.Ready(
            tab = tab,
            plan = plan?.let {
                ScheduleMonthPlan(
                    current,
                    money.format(it.done),
                    it.doneCount,
                    money.format(it.toCome),
                    it.toComeCount,
                    money.format(it.total),
                    it.active,
                    it.done.minorUnits,
                    it.total.minorUnits
                )
            },
            rows = rows.filter { it.first != ScheduleStatus.Ended }.map { it.second },
            ended = rows.filter { it.first == ScheduleStatus.Ended }.map { it.second }
        )
    }

    companion object {
        /** Route argument: open on Ingresos. */
        const val KEY_INCOME = "income"
        private const val KEY_TAB = "schedules.tab"
    }
}
