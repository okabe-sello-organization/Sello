package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryInsights
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.FeatureNudgePolicy
import com.software.sello.domain.policy.ScheduleListPolicy
import com.software.sello.domain.policy.ScheduleStatus
import com.software.sello.domain.policy.UndoPolicy
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.carryProblem
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.guidance.NudgeDays
import com.software.sello.presentation.guidance.ScreenNudge
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One category in the selected month. Its figures come from the same shared snapshot
 * as Recibo; its expenses are listed a page at a time, newest first. Every new
 * snapshot lists them again, so a saved expense appears without anything here adding
 * to or subtracting from what is on screen. A page is only ever a list: no figure is
 * worked out from the rows.
 *
 * Listing again keeps what was listed in view, marked as being refreshed, and replaces
 * it whole once everything that was listed has been read again: the list never shrinks
 * to its first page and grows back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModel(
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    private val expenses: ExpenseReads,
    private val money: MoneyFormatter,
    private val corrections: Corrections,
    private val insightsSnapshots: CategoryInsightsSnapshots,
    schedules: ScheduleReads,
    private val clock: MonotonicClock,
    commands: ExpenseCommands,
    ids: RecordIdSource,
    saved: SavedStateHandle,
    private val guidance: GuidancePreferences,
    financial: FinancialClock
) : ViewModel() {
    private val render = ReciboRenderModel(money)
    private val category = MutableStateFlow<String?>(null)
    private val attempt = MutableStateFlow(0)

    /**
     * The month and its detail figures as last read, so a Retry or a return never blanks
     * what is shown.
     */
    private val lastMonth = LastGoodRead<MonthlySnapshot, SnapshotFailure>()
    private val lastFigures = LastGoodRead<CategoryInsights, SnapshotFailure>()

    /** How many pages are wanted of the list being shown. */
    private val pagesWanted = MutableStateFlow(1)

    private data class Shown(val month: YearMonth, val categoryId: String?)

    /**
     * The list last shown and what it was a list of, kept so a refresh does not blank
     * it. [generation] is the history it was read from: after a restore or reset the
     * same month and category are another history, and its rows are not these.
     */
    private class Kept(val shown: Shown, val generation: Long, val list: ExpenseListState)

    private var kept: Kept? = null

    /**
     * The version of each listed expense and the history they were listed from: what a
     * deletion asked for from a row is checked against, so a row that is out of date
     * is refused, not deleted as something it no longer is.
     */
    private class Listed(val generation: Long, val versions: Map<String, Long>)

    private var listed = Listed(0, emptyMap())
    private val deleting = mutableSetOf<String>()

    /**
     * Deleted, but still listed until the month is read again: asked for no more. Each is
     * kept with the month it was deleted from, so only a read of a later one lets it go.
     */
    private val deleted = mutableMapOf<String, Pair<Long, Long>>()
    private val deletions = ExpenseRowDeletions(saved, KEY_DELETE, commands, ids, corrections)

    /**
     * Why the last deletion asked from a list did not happen, kept with that list: another
     * category or month never shows it.
     */
    private data class Said(val shown: Shown, val problem: DeleteProblem, val checking: Boolean)

    private val said = MutableStateFlow<Said?>(null)

    /** The month and category being shown, the only list whose deletions are said. */
    private var current: Shown? = null

    /** What was last shown whole: its figures and its detail figures, of one revision. */
    private var shownWhole: Shown? = null

    /** When a change began waiting for its detail figures; later changes share the wait. */
    private var waitingSince: Duration? = null

    /** Nudges closed on this screen, so they go even when keeping that fails. */
    private val closedHere = MutableStateFlow<Set<Nudge>>(emptySet())
    private val nudgeDays = NudgeDays(guidance, viewModelScope)

    /**
     * What the last month the screen showed whole left or missed, for the arrastre nudge:
     * worked out with its figures, of the same month, category and revision.
     */
    private var carryFact: CarryFact? = null

    private data class CarryFact(
        val shown: Shown,
        val generation: Long,
        val revision: Long,
        val left: Money?
    )

    /**
     * Every expense of the two months before the one shown, read once for it when it is the
     * month in progress: what the pagos fijos nudge looks for. Null while it is read, when
     * it failed or held more than one read may (never a part taken for the whole).
     */
    private val repeats: Flow<Repeats?> =
        combine(session.selectedMonth, category, attempt, financial.today) { month, id, _, today ->
            Shown(month, id).takeIf { month == YearMonth.from(today.date) }
        }
            .distinctUntilChanged()
            .mapLatest { shown ->
                shown?.categoryId?.let { Repeats(shown, twoMonthsBefore(shown.month)) }
            }
            .onStart { emit(null) }

    private class Repeats(val shown: Shown, val expenses: List<Expense>?)

    private suspend fun twoMonthsBefore(month: YearMonth): List<Expense>? {
        val read = expenses.between(
            month.minusMonths(2).atDay(1),
            month.minusMonths(1).atEndOfMonth(),
            ExpenseReads.MAX_RECENT
        )
        return (read as? Outcome.Success)?.value?.takeIf { it.next == null }?.expenses
    }

    /** What the nudge is worked out from besides the screen's own state. */
    private class NudgeInputs(
        val guidance: PreferenceRead<Guidance>,
        val today: FinancialDay,
        val repeats: Repeats?,
        val closed: Set<Nudge>
    )

    private val nudgeInputs: Flow<NudgeInputs> = combine(
        guidance.observeGuidance(),
        financial.today,
        repeats,
        closedHere,
        corrections.nudgesDone
    ) { read, today, repeats, closed, done -> NudgeInputs(read, today, repeats, closed + done) }

    val state: StateFlow<CategoryDetailState> =
        combine(session.selectedMonth, category, attempt) { month, id, _ -> Shown(month, id) }
            .flatMapLatest { shown ->
                // Another month or category starts from its first page.
                if (kept?.shown != shown) pagesWanted.value = 1
                showing(shown)
                val id = shown.categoryId?.let { CategoryId.of(it) as? Outcome.Success }?.value
                when {
                    shown.categoryId == null -> flowOf(CategoryDetailState.Closed)

                    id == null -> flowOf(CategoryDetailState.Gone)

                    else -> lastMonth.keep(shown.month, snapshots.observe(shown.month))
                        .combine(
                            lastFigures.keep(shown, insightsSnapshots.observe(id, shown.month))
                        ) { snapshot, insights ->
                            snapshot to
                                insights
                        }
                        .flatMapLatest { (snapshot, insights) ->
                            val next = { detail(snapshot, shown, id, insights) }
                            // A change keeps the screen whole until its detail figures are
                            // worked out, so they change together instead of the figures
                            // under the total vanishing and the screen jumping.
                            if (shownWhole == shown && behind(snapshot, insights, id)) {
                                val since =
                                    waitingSince ?: clock.elapsed().also { waitingSince = it }
                                val left = since + DETAIL_FIGURES_WAIT_MILLIS.milliseconds -
                                    clock.elapsed()
                                flow {
                                    delay(left)
                                    emitAll(next())
                                }
                            } else {
                                next()
                            }
                        }
                }
            }
            .combine(said) { state, said ->
                val own = said?.takeIf {
                    state is CategoryDetailState.Ready &&
                        it.shown == Shown(state.month, state.category.id)
                }
                if (state is CategoryDetailState.Ready) {
                    state.copy(
                        deleteProblem = own?.problem,
                        checkingDelete = own?.checking == true
                    )
                } else {
                    state
                }
            }
            // The schedules are read on their own: a change to one never lists the
            // expenses again. What is still to come is the snapshot's, as on Recibo.
            .combine(attempt.flatMapLatest { schedules.observe() }) { state, book ->
                if (state is CategoryDetailState.Ready) {
                    state.copy(
                        fixed = fixed(state, book)
                    ) to book
                } else {
                    state to book
                }
            }
            .combine(nudgeInputs) { (state, book), inputs -> nudged(state, book, inputs) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS),
                CategoryDetailState.Loading
            )

    fun onAction(action: CategoryDetailAction) {
        when (action) {
            is CategoryDetailAction.Show -> category.value = action.categoryId

            // A new attempt starts a new observation of the same month and category.
            CategoryDetailAction.Retry -> attempt.update { it + 1 }

            CategoryDetailAction.ShowMore -> {
                val list = (state.value as? CategoryDetailState.Ready)?.expenses
                // One page per request: a second tap while one is being read asks nothing.
                // Nor does a tap while the list is being read again: that read brings
                // back what is listed now, and a page asked for meanwhile would be lost.
                // Nor while a change waits for its detail figures: its read is still to come.
                val idle = list is ExpenseListState.Rows && !list.loadingMore &&
                    !list.refreshing && waitingSince == null
                if (list is ExpenseListState.Rows && list.more && idle) {
                    pagesWanted.update { it + 1 }
                }
            }

            is CategoryDetailAction.Delete -> delete(action.expenseId)

            // Closed for good: the one shown, whichever it is.
            CategoryDetailAction.CloseNudge ->
                (state.value as? CategoryDetailState.Ready)?.nudge?.let { shown ->
                    val nudge = shown.nudge
                    closedHere.update { it + nudge }
                    viewModelScope.launch { guidance.markSeen(nudge) }
                }

            CategoryDetailAction.CheckDelete -> current?.let(::check)
        }
    }

    /**
     * [shown] is opened: what was said about another list goes, and a deletion of this one
     * still unresolved, from before a restart too, is asked about again with its own ID.
     */
    private fun showing(shown: Shown) {
        current = shown
        if (said.value?.shown != shown) said.value = null
        if (shown.categoryId != null && deletions.unresolved(shown.place)) check(shown)
    }

    /** Says [problem] about [shown]'s deletion, only while that list is the one shown. */
    private fun say(shown: Shown, problem: DeleteProblem?, checking: Boolean = false) {
        if (current == shown) said.value = problem?.let { Said(shown, it, checking) }
    }

    private fun delete(expenseId: String) {
        val from = listed
        val version = from.versions[expenseId] ?: return
        val id = (ExpenseId.of(expenseId) as? Outcome.Success)?.value ?: return
        val ready = state.value as? CategoryDetailState.Ready ?: return
        val shown = Shown(ready.month, ready.category.id)
        // One being sent or asked about says its own answer; nothing else is sent meanwhile.
        if (deletions.busy(shown.place)) return
        // An unresolved deletion is asked about first, never replaced by a new one.
        if (deletions.unresolved(shown.place)) {
            if (said.value?.shown != shown) say(shown, DeleteProblem.Unknown)
            return
        }
        if (expenseId in deleted || !deleting.add(expenseId)) return
        val shownMonth = ready.generation to ready.revision
        say(shown, null)
        viewModelScope.launch {
            try {
                when (val ended = deletions.delete(shown.place, id, version, from.generation)) {
                    // The row leaves with the next read of the month, like any change.
                    RowDeletion.Deleted -> {
                        deleted[expenseId] = shownMonth
                        say(shown, null)
                    }

                    is RowDeletion.NotDeleted -> say(shown, ended.problem)

                    // Nothing was sent (one of it waits on the bar), or the list of later
                    // months is asked first.
                    null, RowDeletion.Asked -> Unit
                }
            } finally {
                deleting -= expenseId
            }
        }
    }

    /** Asks how the unresolved deletion of [shown] ended, with its own operation. */
    private fun check(shown: Shown) {
        if (!deletions.unresolved(shown.place)) return
        say(shown, DeleteProblem.Unknown, checking = true)
        viewModelScope.launch {
            when (val ended = deletions.check(shown.place)) {
                RowDeletion.Deleted -> say(shown, null)

                is RowDeletion.NotDeleted -> say(shown, ended.problem)

                // Already being asked: that answer is the one said.
                null, RowDeletion.Asked -> Unit
            }
        }
    }

    private val Shown.place get() = "$month.$categoryId"

    /**
     * The one nudge due on [state], at most (SELLO-212): arrastre, then pagos fijos, both
     * only in the month in progress, then deleting, once the list shows an expense. Only
     * current figures carry one, never older ones or a carry problem, and only readings
     * that worked count: while the guidance, the schedules or the months before are
     * loading or failed, the nudges that need them do not show.
     */
    private fun nudged(
        state: CategoryDetailState,
        book: ScheduleBookState,
        inputs: NudgeInputs
    ): CategoryDetailState {
        if (state !is CategoryDetailState.Ready) return state
        val shown = Shown(state.month, state.category.id)
        val fresh = !state.stale && state.carryProblem == null
        val inProgress = state.month == YearMonth.from(inputs.today.date)
        val carry = carryFact?.takeIf {
            inProgress && it.shown == shown && it.generation == state.generation &&
                it.revision == state.revision
        }?.left
        val schedules = (book as? ReadState.Ready)?.value
            ?.takeIf { it.generation == state.generation }?.schedules
        val repeated = inputs.repeats?.takeIf { it.shown == shown }?.expenses
        val id = (CategoryId.of(state.category.id) as? Outcome.Success)?.value
        val fixed = inProgress && !state.category.archived && id != null &&
            repeated != null && schedules != null &&
            FeatureNudgePolicy.fixedPaymentDue(
                repeated,
                id,
                state.month,
                schedules.map { it.schedule }
            )
        val listed = (state.expenses as? ExpenseListState.Rows)?.rows?.isNotEmpty() == true
        // A nudge picked keeps the day, so none is picked while one that comes before it may
        // still be due: its figures, the schedules or the months before are still being read.
        val reading = inProgress && (
            state.insights is DetailInsightsState.Loading || book is ReadState.Loading ||
                inputs.repeats?.shown != shown
            )
        if (reading) return state.copy(nudge = null)
        val wanted = listOfNotNull(
            Nudge.Carry.takeIf { carry != null },
            Nudge.FixedPayment.takeIf { fixed },
            Nudge.Deleting.takeIf { listed }
        ) - inputs.closed
        val picked = ScreenNudge.pick(inputs.guidance, inputs.today.date, wanted)
            ?.takeIf { fresh }
        nudgeDays.showing(picked)
        val nudge = when (picked?.nudge) {
            Nudge.Carry -> DetailNudge.Carry(
                state.month.minusMonths(1),
                surplus = checkNotNull(carry).minorUnits > 0
            )

            Nudge.FixedPayment -> DetailNudge.FixedPayment

            Nudge.Deleting -> DetailNudge.Deleting(UndoPolicy.WINDOW.inWholeSeconds)

            else -> null
        }
        return state.copy(nudge = nudge)
    }

    /** Whether [insights] are still of an earlier revision than a newly read month. */
    private fun behind(state: SnapshotState, insights: CategoryInsightsState, id: CategoryId) =
        state is ReadState.Ready && when (insights) {
            ReadState.Loading -> true
            is ReadState.Failed -> false
            is ReadState.Ready -> !insights.value.isOf(state.value, id)
        }

    private fun CategoryInsights.isOf(snapshot: MonthlySnapshot, id: CategoryId) =
        categoryId == id &&
            budget.generation == snapshot.generation &&
            budget.revision == snapshot.revision &&
            budget.asOf == snapshot.asOf && budget.zone == snapshot.zone &&
            budget.month == snapshot.month

    private fun detail(
        state: SnapshotState,
        shown: Shown,
        id: CategoryId,
        insights: CategoryInsightsState
    ): Flow<CategoryDetailState> {
        // Until shown whole again, nothing waits: a screen not showing figures has none to keep.
        shownWhole = null
        waitingSince = null
        val (snapshot, stale) = when (state) {
            ReadState.Loading -> return flowOf(CategoryDetailState.Loading)

            is ReadState.Ready -> state.value to false

            is ReadState.Failed -> (
                state.lastGood ?: return flowOf(
                    carryProblem(state.failure)?.let(CategoryDetailState::CarryFailed) ?: Failed
                )
                ) to true
        }
        val problem = (state as? ReadState.Failed)?.failure?.let { failure ->
            carryProblem(
                failure,
                snapshot.categories.associate { it.category.id to it.category.name.value }
            )
        }
        val line = render.categories(snapshot).firstOrNull { it.id == shown.categoryId }
            ?: return flowOf(CategoryDetailState.Gone)
        carryFact = CarryFact(
            shown,
            snapshot.generation,
            snapshot.revision,
            (insights as? ReadState.Ready)?.value?.takeIf { it.isOf(snapshot, id) }
                ?.let { figures ->
                    snapshot.categories.firstOrNull { it.category.id == id }?.let { month ->
                        FeatureNudgePolicy.carryDue(
                            month,
                            figures.history.firstOrNull {
                                it.month == snapshot.month.minusMonths(1)
                            }
                        )
                    }
                }
        )
        val ready = CategoryDetailState.Ready(
            category = line,
            month = snapshot.month,
            today = snapshot.asOf,
            stale = stale,
            canRecord = !line.archived && snapshot.month == YearMonth.from(snapshot.asOf),
            carryProblem = problem,
            generation = snapshot.generation,
            revision = snapshot.revision,
            upcoming = snapshot.categories.firstOrNull { it.category.id == id }?.upcoming.orEmpty()
                .map { payment ->
                    DetailUpcomingPayment(
                        scheduleId = payment.schedule.value,
                        name = payment.note?.value ?: line.name,
                        date = payment.occurrence.date,
                        amount = money.format(payment.amount.money)
                    )
                },
            insights = when (insights) {
                ReadState.Loading -> DetailInsightsState.Loading

                is ReadState.Failed -> DetailInsightsState.Failed

                is ReadState.Ready -> {
                    if (insights.value.isOf(snapshot, id)) {
                        DetailInsightsState.Ready(
                            CategoryInsightsRenderModel(money).render(insights.value)
                        )
                    } else {
                        DetailInsightsState.Loading
                    }
                }
            },
            expenses = when (
                val before = kept
                    ?.takeIf { it.shown == shown && it.generation == snapshot.generation }?.list
            ) {
                null -> ExpenseListState.Loading

                // Rows of an earlier snapshot under this one's figures: said to be so.
                is ExpenseListState.Rows ->
                    before.copy(refreshing = true, loadingMore = false, moreFailed = false)

                else -> before
            }
        )
        return flow {
            shownWhole = shown.takeIf { ready.insights is DetailInsightsState.Ready }
            emit(ready)
            list(id, snapshot).collect { list ->
                // Only a list that was read is worth showing again while refreshing.
                if (list !is ExpenseListState.Failed) kept = Kept(shown, snapshot.generation, list)
                emit(ready.copy(expenses = list))
            }
        }
    }

    /**
     * The last schedules read and the last slip worked out from them, so a failed read, or a
     * retry whose read fails again, keeps what was listed, marked as older (never "none").
     * The slip is kept with what it was of, as [Kept] is: a slip found empty is kept too,
     * so a payment deleted or ended since never comes back.
     */
    private var keptBook: ScheduleBook? = null

    private class KeptFixed(
        val categoryId: String,
        val month: YearMonth,
        val generation: Long,
        val slip: DetailFixedState
    )

    private var keptFixed: KeptFixed? = null

    /**
     * Pagos fijos: what the snapshot still reserves for the category, then its other
     * payments that repeat, not ended. Only in the month in progress, and only from
     * schedules read at the snapshot's own revision, so the list and the hero agree.
     */
    private fun fixed(state: CategoryDetailState.Ready, book: ScheduleBookState): DetailFixedState {
        if (state.month != YearMonth.from(state.today)) return DetailFixedState.Hidden
        val failed = book is ReadState.Failed
        val read = when (book) {
            ReadState.Loading -> null
            is ReadState.Ready -> book.value.also { keptBook = it }
            is ReadState.Failed -> book.lastGood ?: keptBook
        }?.takeIf { it.generation == state.generation }
        val before = keptFixed?.takeIf {
            it.categoryId == state.category.id && it.month == state.month &&
                it.generation == state.generation
        }?.slip
        if (read == null || read.revision != state.revision) {
            return when {
                before is DetailFixedState.Ready -> if (failed) {
                    before.copy(
                        stale = true
                    )
                } else {
                    before
                }

                // Failed with no list to keep: said, never shown as none.
                failed -> DetailFixedState.Failed

                // Nothing reserved: no skeleton for a category that may have no schedules.
                state.upcoming.isEmpty() -> DetailFixedState.Hidden

                else -> DetailFixedState.Loading
            }
        }
        val coming = state.upcoming.map { it.scheduleId }.toSet()
        val later = ScheduleListPolicy.lines(
            read.schedules.filter {
                (it.schedule.entry as? ScheduledEntry.Payment)?.categoryId?.value ==
                    state.category.id
            },
            state.today,
            state.month
        )
            .filter { it.status != ScheduleStatus.Ended && it.stored.schedule.id.value !in coming }
            .sortedBy { it.next ?: LocalDate.MAX }
            .map { line ->
                val schedule = line.stored.schedule
                DetailSchedule(
                    id = schedule.id.value,
                    name = schedule.entry.note?.value ?: state.category.name,
                    rule = schedule.recurrence.rule,
                    start = schedule.recurrence.start,
                    next = line.next.takeIf { line.status == ScheduleStatus.Active },
                    pausedFrom = line.pausedFrom.takeIf { line.status == ScheduleStatus.Paused },
                    amount = money.format(schedule.entry.amount.money)
                )
            }
        val shown = if (state.upcoming.isEmpty() && later.isEmpty()) {
            DetailFixedState.Hidden
        } else {
            DetailFixedState.Ready(state.upcoming, later, stale = failed)
        }
        keptFixed = KeptFixed(state.category.id, state.month, state.generation, shown)
        return shown
    }

    /**
     * Everything that was listed before, read in one step, then one more page each time
     * another is asked for. Nothing is published while that first step is being read.
     */
    private fun list(id: CategoryId, snapshot: MonthlySnapshot): Flow<ExpenseListState> = flow {
        val rows = ArrayList<ExpenseRow>()
        val versions = HashMap<String, Long>()
        var pages = 0
        var next: ExpenseCursor? = null
        pagesWanted.collect { wanted ->
            while (pages < wanted && (pages == 0 || next != null)) {
                val step = if (pages == 0) wanted else 1
                if (pages > 0) emit(ExpenseListState.Rows(rows.toList(), true, loadingMore = true))
                val read = read(id, snapshot.month, next, step * PAGE_SIZE)
                if (read == null) {
                    if (pages == 0) {
                        // Retry reads again all that was listed, so the count is kept.
                        emit(ExpenseListState.Failed)
                    } else {
                        emit(ExpenseListState.Rows(rows.toList(), true, moreFailed = true))
                        // Asking again asks for this same page, not for one after it.
                        pagesWanted.value = pages
                    }
                    return@collect
                }
                rows += read.expenses.map { row(it, snapshot) }
                versions += read.expenses.associate { it.id.value to it.version }
                listed = Listed(snapshot.generation, versions.toMap())
                val month = snapshot.generation to snapshot.revision
                deleted.values.removeAll { it != month }
                next = read.next
                pages += step
                emit(
                    if (rows.isEmpty()) {
                        ExpenseListState.Empty
                    } else {
                        ExpenseListState.Rows(rows.toList(), more = next != null)
                    }
                )
            }
        }
    }

    /**
     * The next [count] expenses after [after], in as few reads as storage allows, or
     * null when one of them failed. [ExpensePage.next] is where the page after begins.
     */
    private suspend fun read(
        id: CategoryId,
        month: YearMonth,
        after: ExpenseCursor?,
        count: Int
    ): ExpensePage? {
        val read = ArrayList<Expense>(count)
        var cursor = after
        while (read.size < count) {
            val limit = minOf(count - read.size, ExpenseReads.MAX_PAGE)
            when (val page = expenses.inCategory(id, month, cursor, limit)) {
                is Outcome.Failure -> return null

                is Outcome.Success -> {
                    read += page.value.expenses
                    cursor = page.value.next
                    if (cursor == null) break
                }
            }
        }
        return ExpensePage(read, cursor)
    }

    private fun row(expense: Expense, snapshot: MonthlySnapshot) = ExpenseRow(
        id = expense.id.value,
        date = expense.date,
        note = expense.note?.value,
        amount = money.format(expense.amount.money),
        notCountedYet = expense.date > snapshot.asOf
    )

    private companion object {
        val Failed = CategoryDetailState.Failed

        /** Long enough to outlast a rotation without reading again. */
        const val STOP_AFTER_MILLIS = 5_000L
        const val PAGE_SIZE = 20
        const val KEY_DELETE = "categoryDetail.delete"
    }
}

/**
 * How long a change waits for its own detail figures before it is shown without them:
 * long enough for them to arrive together, short enough that a new total is never held.
 */
internal const val DETAIL_FIGURES_WAIT_MILLIS = 500L
