package com.software.sello.feature.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomePlan
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.policy.IncomePlanPolicy
import com.software.sello.domain.policy.SuggestedLimit
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.IncomeSuggestionPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
    val allocatedShare: Float,
    /** A category carries this month: Sin asignar still counts base limits only (CP17). */
    val carrying: Boolean = false
)

/**
 * The offer to give unassigned income a category: [unassigned] is what the budgets leave
 * out. [limit] starts the new category's limit, in pesos, only when [unassigned] is a limit
 * the form can hold; otherwise it is null and the limit starts empty. Nothing is assigned.
 */
data class AssignSuggestion(val unassigned: MoneyTextValue, val limit: String?)

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

    /** Nothing of the month was read: a refresh that fails after a read is [Ready.stale]. */
    data object Failed : IncomeHistoryState

    /**
     * [stale] says the last refresh failed and these are the figures as last read; Más
     * still reads on from where they end.
     */
    data class Ready(
        val month: YearMonth,
        val total: MoneyTextValue,
        val lines: List<IncomeLine>,
        val more: Boolean,
        val busy: Boolean = false,
        val pageFailed: Boolean = false,
        val stale: Boolean = false
    ) : IncomeHistoryState
}

sealed interface IncomeHistoryAction {
    data object Retry : IncomeHistoryAction

    data object More : IncomeHistoryAction

    /** Hides the suggestion to assign income for the month in progress. */
    data object DismissAssign : IncomeHistoryAction
}

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeHistoryViewModel(
    private val reads: IncomeReads,
    private val session: MonthSession,
    snapshots: ResumenSnapshots,
    schedules: ScheduleReads,
    private val suggestions: IncomeSuggestionPreferences,
    private val money: MoneyFormatter,
    clock: FinancialClock
) : ViewModel() {
    val today = clock.today.map {
        it.date
    }.stateIn(viewModelScope, SharingStarted.Eagerly, clock.today.value.date)

    /** Null while it is read, or when it could not be: never shown as zero. */
    val split: StateFlow<IncomeSplit?> = session.selectedMonth
        .flatMapLatest(snapshots::observe)
        .map { read -> (read as? ReadState.Ready)?.value?.let(::split) }
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
            allocatedShare = share,
            carrying = summary.budget.categories.any { it.budget?.carry != null }
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

    /** The month in progress and what it leaves unassigned, before any dismissal. */
    private data class Unassigned(val month: YearMonth, val offer: AssignSuggestion)

    private val offered: Flow<Unassigned?> = combine(
        session.selectedMonth,
        session.currentMonth,
        attempts
    ) { selected, current, _ -> selected.takeIf { it == current } }
        .flatMapLatest { month ->
            if (month == null) {
                flowOf(null)
            } else {
                snapshots.observe(month).map { read ->
                    (read as? ReadState.Ready)?.value?.let { assign(month, it) }
                }
            }
        }

    private fun assign(month: YearMonth, summary: ResumenSnapshot): Unassigned? =
        summary.unallocated.takeIf { it.minorUnits > 0 }?.let {
            Unassigned(month, AssignSuggestion(money.format(it), SuggestedLimit.of(it)))
        }

    /**
     * The month dismissed in this session, kept until the stored dismissal shows it, so a
     * write that failed still hides the suggestion.
     */
    private val dismissedNow = MutableStateFlow<YearMonth?>(null)

    /** The stored dismissal once read; [answered] is false until the first answer arrives. */
    private data class Stored(val answered: Boolean, val month: YearMonth?)

    /**
     * What was stored. Until the read answers, nothing is known, so the note waits (only the
     * note: the rest of the screen does not). A failed read keeps the last good value.
     */
    private val dismissedStored = suggestions.observeDismissedIncomeSuggestion()
        .map { read ->
            Stored(
                answered = true,
                month = when (read) {
                    is PreferenceRead.Ready -> read.value
                    is PreferenceRead.Failed -> read.lastGood
                }
            )
        }
        .onStart { emit(Stored(answered = false, month = null)) }

    /**
     * Null unless the month in progress has recorded income left unassigned and the person
     * has not dismissed the suggestion for it: a closed month, an amount of zero or less, a
     * summary still read and one that could not be read offer nothing, nor does a dismissal
     * that is not read yet. Reintentar reads again.
     */
    val assign: StateFlow<AssignSuggestion?> = combine(
        offered,
        dismissedStored,
        dismissedNow
    ) { offer, stored, now ->
        // Once the stored dismissal shows the month, it alone decides: a reset can bring it back.
        if (now != null && stored.month == now) dismissedNow.compareAndSet(now, null)
        offer?.takeIf {
            stored.answered && it.month != stored.month && it.month != now
        }?.offer
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Null while the two reads disagree or one is still loading: the last plan stays. */
    private fun plan(
        month: YearMonth,
        summary: ResumenSnapshotState,
        book: ScheduleBookState
    ): IncomePlanState? {
        val schedules = when (book) {
            ReadState.Loading -> return null
            is ReadState.Failed -> return IncomePlanState.Failed
            is ReadState.Ready -> book.value
        }
        val expected = IncomePlanPolicy.expected(schedules.schedules, month)
        if (expected.isEmpty()) return IncomePlanState.None
        val read = when (summary) {
            ReadState.Loading -> return null
            is ReadState.Failed -> return IncomePlanState.Failed
            is ReadState.Ready -> summary.value
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
                    // Only the first load of a month shows Loading: a change after that
                    // updates the list in place and reads the loaded pages again. Until that
                    // read succeeds, the cursor and snapshot stay those of the shown list.
                    val loaded = (shown.value as? IncomeHistoryState.Ready)
                        ?.takeIf { it.month == session.selectedMonthNow }
                    if (loaded == null) {
                        cursor = null
                        snapshot = null
                        shown.value = IncomeHistoryState.Loading
                    } else {
                        shown.value = loaded.copy(busy = true, pageFailed = false)
                    }
                    when (state) {
                        ReadState.Loading -> Unit

                        is ReadState.Failed -> {
                            shown.value =
                                loaded?.copy(busy = false, pageFailed = false, stale = true)
                                    ?: IncomeHistoryState.Failed
                        }

                        is ReadState.Ready -> {
                            val restore = loaded?.lines?.size ?: 0
                            page(state.value, first = true, epoch, restore)
                        }
                    }
                }
        }
    }

    fun onAction(action: IncomeHistoryAction) {
        when (action) {
            IncomeHistoryAction.Retry -> attempts.update { it + 1 }

            IncomeHistoryAction.DismissAssign -> dismissAssign()

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

    /** Remembers it for this month only: if income is still unassigned next month, it returns. */
    private fun dismissAssign() {
        if (assign.value == null) return
        val month = session.currentMonthNow
        dismissedNow.value = month
        viewModelScope.launch { suggestions.dismissIncomeSuggestion(month) }
    }

    /**
     * Reads a page of [expected]'s month. The first page of a refresh keeps reading until
     * [restore] lines are back, so the pages loaded with Más survive a change. A refresh
     * that fails keeps the shown list, marked as older; a Más page that fails says so.
     */
    private suspend fun page(expected: IncomeMonth, first: Boolean, epoch: Int, restore: Int = 0) {
        val incomes = mutableListOf<Income>()
        var next = if (first) null else cursor
        var failed = false
        do {
            when (val page = reads.inMonth(expected.month, next, PAGE_SIZE)) {
                is Outcome.Failure -> failed = true

                is Outcome.Success -> {
                    incomes += page.value.incomes
                    next = page.value.next
                }
            }
        } while (!failed && first && next != null && incomes.size < restore)
        val checked = reads.month(expected.month)
        if (this.epoch != epoch || (!first && snapshot != expected) ||
            session.selectedMonthNow != expected.month
        ) {
            return
        }
        if (checked is Outcome.Failure || failed) {
            val previous = (shown.value as? IncomeHistoryState.Ready)
                ?.takeIf { it.month == expected.month }
            shown.value = when {
                previous == null -> IncomeHistoryState.Failed
                first -> previous.copy(busy = false, pageFailed = false, stale = true)
                else -> previous.copy(busy = false, pageFailed = true)
            }
            return
        }
        val latest = (checked as Outcome.Success).value
        if (latest.generation != expected.generation || latest.revision != expected.revision) {
            attempts.update { it + 1 }
            return
        }
        val shownNow = shown.value as? IncomeHistoryState.Ready
        val previous = if (first) emptyList() else shownNow?.lines.orEmpty()
        snapshot = expected
        cursor = next
        shown.value = IncomeHistoryState.Ready(
            expected.month,
            money.format(expected.total),
            previous + incomes.map {
                IncomeLine(
                    it.id.value,
                    money.format(it.amount.money),
                    it.source.key,
                    (it.source as? IncomeSource.Other)?.name?.value,
                    it.date,
                    it.note?.value
                )
            },
            more = next != null,
            // Only a refresh that succeeds clears the older marker, not a Más page.
            stale = !first && shownNow?.stale == true
        )
    }

    private companion object {
        const val PAGE_SIZE = 50
    }
}
