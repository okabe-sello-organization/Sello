package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SnapshotState
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.Corrections
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
        val problem: DeleteProblem? = null,
        /** An unresolved deletion is being asked about again. */
        val checking: Boolean = false,
        /** The month could not be read again: these are the rows as last read. */
        val stale: Boolean = false
    ) : CategoryExpensesState
}

/** The dock of a category's full list. */
enum class ListDock {
    /** Anotar, in this category. */
    Anotar,

    /** Anotar, disabled while the list is first read. */
    Waiting,

    /** Disabled, "Archivada": the category takes no new expense. */
    Archived
}

sealed interface CategoryExpensesAction {
    data class Show(val categoryId: String) : CategoryExpensesAction
    data class Search(val query: String) : CategoryExpensesAction
    data class Sort(val order: ExpenseOrder) : CategoryExpensesAction
    data object More : CategoryExpensesAction
    data object Retry : CategoryExpensesAction
    data class Delete(val id: String) : CategoryExpensesAction

    /** Ask again, with its own operation, how an unresolved deletion ended. */
    data object CheckDelete : CategoryExpensesAction
}

/** Server-side search/sort before paging; saved filters survive process recreation. */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryExpensesViewModel(
    private val saved: SavedStateHandle,
    session: MonthSession,
    private val snapshots: MonthlySnapshots,
    private val search: CategoryExpenseSearch,
    private val money: MoneyFormatter,
    corrections: Corrections,
    commands: ExpenseCommands,
    ids: RecordIdSource
) : ViewModel() {
    val query = saved.getStateFlow("expenseSearch", "")
    val order = saved.getStateFlow("expenseOrder", ExpenseOrder.Date.name)
    private val category = saved.getStateFlow<String?>("expenseCategory", null)
    private val wanted = MutableStateFlow(PAGE_SIZE)
    private val retry = MutableStateFlow(0)
    private val deletions = ExpenseRowDeletions(saved, KEY_DELETE, commands, ids, corrections)

    /** Why the last deletion did not happen, kept with the month and category it was asked in. */
    private data class Said(val place: String, val problem: DeleteProblem, val checking: Boolean)

    private val said = MutableStateFlow<Said?>(null)

    /** Whether the category is archived, from the last month read of it, with its id. */
    private val known = MutableStateFlow<Pair<String?, Boolean>?>(null)

    /** The month and category listed: the only place whose deletions are said. */
    private var current: String? = null
    private var generation = 0L
    private var versions: Map<String, Long> = emptyMap()
    private var keptRequest: Request? = null
    private var keptGeneration: Long? = null
    private val deleting = mutableSetOf<String>()

    /** The month as last read, so a Retry or a return never blanks rows already listed. */
    private val lastGood = LastGoodRead<MonthlySnapshot, SnapshotFailure>()
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
        showing(request)
        versions = emptyMap()
        // Reintentar observes the month again, as well as reading the rows again.
        combine(
            retry.flatMapLatest { lastGood.keep(request.month, snapshots.observe(request.month)) },
            wanted
        ) { snapshot, count -> snapshot to count }
            .flatMapLatest { (snapshot, count) -> load(request, snapshot, count) }
    }.combine(said) { state, said ->
        val own = said?.takeIf { it.place == current }
        if (state is CategoryExpensesState.Ready) {
            state.copy(problem = own?.problem, checking = own?.checking == true)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryExpensesState.Loading)

    /**
     * What the dock offers. Archived is known as soon as a month of the category is read and
     * holds while its rows are read or fail. Before that it waits only while the list is
     * still loading: a month that could not be read leaves Anotar, whose form says so itself.
     */
    val dock: StateFlow<ListDock> = combine(known, state) { known, state ->
        when (known?.second) {
            true -> ListDock.Archived

            false -> ListDock.Anotar

            null -> when (state) {
                CategoryExpensesState.Loading -> ListDock.Waiting
                else -> ListDock.Anotar
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListDock.Waiting)

    fun onAction(action: CategoryExpensesAction) {
        when (action) {
            is CategoryExpensesAction.Show -> saved["expenseCategory"] = action.categoryId

            is CategoryExpensesAction.Search ->
                saved["expenseSearch"] = action.query.cutToSearchLimit()

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

            CategoryExpensesAction.CheckDelete -> current?.let(::check)
        }
    }

    /**
     * A new search, order, month or category: what was said goes, and a deletion of this
     * month and category still unresolved, from before a restart too, is asked about again.
     */
    private fun showing(request: Request) {
        val place = request.id?.let { "${request.month}.$it" }
        current = place
        // A check of this place being asked stays said; anything else goes.
        said.value = said.value?.takeIf { it.place == place && it.problem == DeleteProblem.Unknown }
        if (known.value?.first != request.id) known.value = null
        if (place != null && deletions.unresolved(place)) check(place)
    }

    /** Says [problem] about [place]'s deletion, only while that place is the one listed. */
    private fun say(place: String, problem: DeleteProblem?, checking: Boolean = false) {
        if (current == place) said.value = problem?.let { Said(place, it, checking) }
    }

    private fun load(request: Request, snapshot: SnapshotState, count: Int) = flow {
        val id = (request.id?.let(CategoryId::of) as? Outcome.Success)?.value
        val budget = (snapshot as? ReadState.Ready)?.value
        // A month that could not be read again keeps the rows listed, said to be older.
        // No more pages are offered from them: Reintentar is the way on.
        val listed = (state.value as? CategoryExpensesState.Ready)
            ?.takeIf { keptRequest == request && snapshot is ReadState.Failed && id != null }
        if (listed != null) {
            emit(listed.copy(busy = false, pageFailed = false, more = false, stale = true))
            return@flow
        }
        if (id == null || budget == null) {
            versions = emptyMap()
            emit(
                if (snapshot is ReadState.Failed ||
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
        category?.let { known.value = request.id to it.archived }
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
        val place = current ?: return
        // One being sent or asked about says its own answer; nothing else is sent meanwhile.
        if (deletions.busy(place)) return
        // An unresolved deletion is asked about first, never replaced by a new one.
        if (deletions.unresolved(place)) {
            if (said.value?.place != place) say(place, DeleteProblem.Unknown)
            return
        }
        if (!deleting.add(raw)) return
        say(place, null)
        viewModelScope.launch {
            try {
                when (val ended = deletions.delete(place, id, version, fromGeneration)) {
                    is RowDeletion.NotDeleted -> say(place, ended.problem)

                    // The row leaves with the next read of the month, like any change.
                    RowDeletion.Deleted -> say(place, null)

                    // Nothing was sent (one of it waits on the bar), or the list of later
                    // months is asked first.
                    null, RowDeletion.Asked -> Unit
                }
            } finally {
                deleting -= raw
            }
        }
    }

    /** Asks how the unresolved deletion of [place] ended, with its own operation. */
    private fun check(place: String) {
        if (!deletions.unresolved(place)) return
        say(place, DeleteProblem.Unknown, checking = true)
        viewModelScope.launch {
            when (val ended = deletions.check(place)) {
                RowDeletion.Deleted -> say(place, null)

                is RowDeletion.NotDeleted -> say(place, ended.problem)

                // Already being asked: that answer is the one said.
                null, RowDeletion.Asked -> Unit
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val MAX_SEARCH = 60
        const val KEY_DELETE = "categoryExpenses.delete"

        /** The first [MAX_SEARCH] units, never ending on half of a two-unit character. */
        fun String.cutToSearchLimit(): String {
            if (length <= MAX_SEARCH) return this
            val endsOnAHalf = Character.isHighSurrogate(this[MAX_SEARCH - 1])
            return substring(0, if (endsOnAHalf) MAX_SEARCH - 1 else MAX_SEARCH)
        }
    }
}
