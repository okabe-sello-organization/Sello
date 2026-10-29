package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.DeleteResult
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.money.MoneyFormatter
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
import kotlinx.coroutines.launch

sealed interface CategoryExpensesState {
    data object Loading : CategoryExpensesState
    data object Failed : CategoryExpensesState
    data class Ready(
        val categoryName: String,
        val month: YearMonth,
        val today: LocalDate,
        val rows: List<ExpenseRow>,
        val more: Boolean,
        val totalCount: Long,
        val busy: Boolean = false,
        val pageFailed: Boolean = false,
        val problem: DeleteProblem? = null
    ) : CategoryExpensesState
}

sealed interface CategoryExpensesAction {
    data class Show(val categoryId: String) : CategoryExpensesAction
    data class Search(val query: String) : CategoryExpensesAction
    data class Sort(val order: ExpenseOrder) : CategoryExpensesAction
    data object More : CategoryExpensesAction
    data object Retry : CategoryExpensesAction
    data class Delete(val id: String) : CategoryExpensesAction
}

/** Server-side search/sort before paging; saved filters survive process recreation. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryExpensesViewModel(
    private val saved: SavedStateHandle,
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    private val search: CategoryExpenseSearch,
    private val money: MoneyFormatter,
    private val corrections: ExpenseCorrections
) : ViewModel() {
    val query = saved.getStateFlow("expenseSearch", "")
    val order = saved.getStateFlow("expenseOrder", ExpenseOrder.Date.name)
    private val category = saved.getStateFlow<String?>("expenseCategory", null)
    private val wanted = MutableStateFlow(PAGE_SIZE)
    private val retry = MutableStateFlow(0)
    private val problem = MutableStateFlow<DeleteProblem?>(null)
    private var generation = 0L
    private var versions: Map<String, Long> = emptyMap()
    private var keptRequest: Request? = null
    private var keptGeneration: Long? = null
    private val deleting = mutableSetOf<String>()
    private data class Request(
        val id: String?,
        val month: YearMonth,
        val query: String,
        val order: ExpenseOrder
    )

    private val request =
        combine(category, session.selectedMonth, query, order) { id, month, text, sort ->
            Request(
                id,
                month,
                text,
                ExpenseOrder.entries.firstOrNull { it.name == sort } ?: ExpenseOrder.Date
            )
        }

    val state: StateFlow<CategoryExpensesState> = request.flatMapLatest { request ->
        wanted.value = PAGE_SIZE
        problem.value = null
        versions = emptyMap()
        combine(snapshots.observe(request.month), wanted, retry) { snapshot, count, _ ->
            snapshot to
                count
        }
            .flatMapLatest { (snapshot, count) -> load(request, snapshot, count) }
    }.combine(problem) { state, problem ->
        if (state is CategoryExpensesState.Ready) state.copy(problem = problem) else state
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryExpensesState.Loading)

    fun onAction(action: CategoryExpensesAction) {
        when (action) {
            is CategoryExpensesAction.Show -> saved["expenseCategory"] = action.categoryId

            is CategoryExpensesAction.Search ->
                saved["expenseSearch"] = action.query.take(MAX_SEARCH)

            is CategoryExpensesAction.Sort -> saved["expenseOrder"] = action.order.name

            CategoryExpensesAction.Retry -> retry.update { it + 1 }

            CategoryExpensesAction.More -> {
                val ready = state.value as? CategoryExpensesState.Ready
                if (ready != null && ready.more && !ready.busy &&
                    wanted.value <= Int.MAX_VALUE - PAGE_SIZE
                ) {
                    wanted.update { it + PAGE_SIZE }
                }
            }

            is CategoryExpensesAction.Delete -> delete(action.id)
        }
    }

    private fun load(request: Request, snapshot: SnapshotState, count: Int) = flow {
        val id = (request.id?.let(CategoryId::of) as? Outcome.Success)?.value
        val budget = (snapshot as? SnapshotState.Ready)?.snapshot
        if (id == null || budget == null) {
            versions = emptyMap()
            emit(
                if (snapshot is SnapshotState.Failed ||
                    id == null
                ) {
                    CategoryExpensesState.Failed
                } else {
                    CategoryExpensesState.Loading
                }
            )
            return@flow
        }
        val category = budget.categories.firstOrNull { it.category.id == id }?.category
        if (category == null) {
            versions = emptyMap()
            emit(CategoryExpensesState.Failed)
            return@flow
        }
        val old = (state.value as? CategoryExpensesState.Ready)
            ?.takeIf { keptRequest == request && keptGeneration == budget.generation }
        emit(old?.copy(busy = true, pageFailed = false) ?: CategoryExpensesState.Loading)
        val rows = mutableListOf<Expense>()
        var more = false
        var totalCount = 0L
        while (rows.size < count) {
            when (
                val read = search.read(
                    id,
                    request.month,
                    request.order,
                    request.query,
                    rows.size,
                    minOf(count - rows.size, ExpenseReads.MAX_PAGE)
                )
            ) {
                is Outcome.Failure -> {
                    emit(
                        old?.takeIf {
                            it.month == request.month &&
                                it.categoryName == category.name.value
                        }
                            ?.copy(pageFailed = true, busy = false) ?: CategoryExpensesState.Failed
                    )
                    return@flow
                }

                is Outcome.Success -> {
                    if (read.value.generation != budget.generation ||
                        read.value.revision != budget.revision
                    ) {
                        versions = emptyMap()
                        emit(CategoryExpensesState.Loading)
                        return@flow
                    }
                    rows += read.value.expenses
                    more = read.value.more
                    totalCount = read.value.totalCount
                    if (!more) break
                }
            }
        }
        generation = budget.generation
        versions = rows.associate { it.id.value to it.version }
        keptRequest = request
        keptGeneration = budget.generation
        emit(
            CategoryExpensesState.Ready(
                category.name.value,
                request.month,
                budget.asOf,
                rows.map {
                    ExpenseRow(
                        it.id.value,
                        it.date,
                        it.note?.value,
                        money.format(it.amount.money),
                        it.date > budget.asOf
                    )
                },
                more,
                totalCount
            )
        )
    }

    private fun delete(raw: String) {
        val id = (ExpenseId.of(raw) as? Outcome.Success)?.value ?: return
        val version = versions[raw] ?: return
        val fromGeneration = generation
        if (!deleting.add(raw)) return
        viewModelScope.launch {
            try {
                problem.value = when (corrections.delete(id, version, fromGeneration)) {
                    DeleteResult.Deleted -> null
                    DeleteResult.Gone -> DeleteProblem.Gone
                    DeleteResult.Changed -> DeleteProblem.Changed
                    DeleteResult.DataChanged -> DeleteProblem.DataChanged
                    DeleteResult.Failed -> DeleteProblem.Failed
                }
            } finally {
                deleting -= raw
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_SEARCH = 60
    }
}
