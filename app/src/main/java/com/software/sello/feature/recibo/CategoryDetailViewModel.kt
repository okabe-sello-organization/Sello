package com.software.sello.feature.recibo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryInsightsSnapshots
import com.software.sello.domain.port.CategoryInsightsState
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.carry.carryProblem
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.corrections.DeleteResult
import com.software.sello.presentation.money.MoneyFormatter
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    private val insightsSnapshots: CategoryInsightsSnapshots
) : ViewModel() {
    private val render = ReciboRenderModel(money)
    private val category = MutableStateFlow<String?>(null)
    private val attempt = MutableStateFlow(0)

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
    private val deleteProblem = MutableStateFlow<DeleteProblem?>(null)

    val state: StateFlow<CategoryDetailState> =
        combine(session.selectedMonth, category, attempt) { month, id, _ -> Shown(month, id) }
            .flatMapLatest { shown ->
                // Another month or category starts from its first page.
                if (kept?.shown != shown) pagesWanted.value = 1
                val id = shown.categoryId?.let { CategoryId.of(it) as? Outcome.Success }?.value
                when {
                    shown.categoryId == null -> flowOf(CategoryDetailState.Closed)

                    id == null -> flowOf(CategoryDetailState.Gone)

                    else -> snapshots.observe(shown.month)
                        .combine(insightsSnapshots.observe(id, shown.month)) { snapshot, insights ->
                            snapshot to
                                insights
                        }
                        .flatMapLatest { (snapshot, insights) ->
                            detail(snapshot, shown, id, insights)
                        }
                }
            }
            .combine(deleteProblem) { state, problem ->
                if (state is CategoryDetailState.Ready) {
                    state.copy(
                        deleteProblem = problem
                    )
                } else {
                    state
                }
            }
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
                val idle = list is ExpenseListState.Rows && !list.loadingMore && !list.refreshing
                if (list is ExpenseListState.Rows && list.more && idle) {
                    pagesWanted.update { it + 1 }
                }
            }

            is CategoryDetailAction.Delete -> delete(action.expenseId)
        }
    }

    private fun delete(expenseId: String) {
        val from = listed
        val version = from.versions[expenseId] ?: return
        val id = (ExpenseId.of(expenseId) as? Outcome.Success)?.value ?: return
        if (!deleting.add(expenseId)) return
        deleteProblem.value = null
        viewModelScope.launch {
            try {
                deleteProblem.value = when (corrections.delete(id, version, from.generation)) {
                    // The row leaves with the next read of the month, like any change.
                    DeleteResult.Deleted -> null

                    DeleteResult.Gone -> DeleteProblem.Gone

                    DeleteResult.Changed -> DeleteProblem.Changed

                    DeleteResult.DataChanged -> DeleteProblem.DataChanged

                    DeleteResult.Failed -> DeleteProblem.Failed

                    // The list of later months is shown above every screen (CP20).
                    DeleteResult.AwaitsConfirmation -> null
                }
            } finally {
                deleting -= expenseId
            }
        }
    }

    private fun detail(
        state: SnapshotState,
        shown: Shown,
        id: CategoryId,
        insights: CategoryInsightsState
    ): Flow<CategoryDetailState> {
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
        val ready = CategoryDetailState.Ready(
            category = line,
            month = snapshot.month,
            today = snapshot.asOf,
            stale = stale,
            canRecord = !line.archived && snapshot.month == YearMonth.from(snapshot.asOf),
            carryProblem = problem,
            insights = when (insights) {
                ReadState.Loading -> DetailInsightsState.Loading

                is ReadState.Failed -> DetailInsightsState.Failed

                is ReadState.Ready -> {
                    val other = insights.value.budget
                    if (insights.value.categoryId == id &&
                        other.generation == snapshot.generation &&
                        other.revision == snapshot.revision &&
                        other.asOf == snapshot.asOf && other.zone == snapshot.zone &&
                        other.month == snapshot.month
                    ) {
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
            emit(ready)
            list(id, snapshot).collect { list ->
                // Only a list that was read is worth showing again while refreshing.
                if (list !is ExpenseListState.Failed) kept = Kept(shown, snapshot.generation, list)
                emit(ready.copy(expenses = list))
            }
        }
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
    }
}
