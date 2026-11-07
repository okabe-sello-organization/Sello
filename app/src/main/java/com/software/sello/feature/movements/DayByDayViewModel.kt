package com.software.sello.feature.movements

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.MonthMatches
import com.software.sello.domain.policy.SearchTotals
import com.software.sello.domain.port.MonthMovements
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementQuery
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.MovementSearch
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.income.IncomeSourceNames
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.read.LastGoodRead
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Día a día (SELLO-192): the shared month's expenses and incomes by day, in pages. The
 * month is observed through Resumen's snapshot, so the summary is Recibo's and Resumen's
 * own figures and any change, an edit or its undo, reads the list again. The chip
 * survives process death; the month is the session's.
 *
 * Its search (SELLO-193) looks through every month. It is read again on the same
 * observation of the month, so a change anywhere, such as a result corrected in its
 * editor, reads the results again. The query survives process death; only the query typed
 * last is read, after a short pause in typing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayByDayViewModel(
    private val saved: SavedStateHandle,
    session: MonthSession,
    private val snapshots: ResumenSnapshots,
    private val movements: MonthMovements,
    private val search: MovementSearch,
    private val sourceNames: IncomeSourceNames,
    private val money: MoneyFormatter
) : ViewModel() {
    private val filter = saved.getStateFlow(KEY_FILTER, MovementFilter.All.name)
    private val wanted = MutableStateFlow(PAGE_SIZE)
    private val wantedFound = MutableStateFlow(PAGE_SIZE)
    private val retry = MutableStateFlow(0)

    /** What is typed in the search field, as typed. */
    val query: StateFlow<String> = saved.getStateFlow(KEY_QUERY, "")

    /** The month as last read, so a Retry or a return never blanks rows already listed. */
    private val lastGood = LastGoodRead<ResumenSnapshot, SnapshotFailure>()

    /** The month and chip whose rows [state] holds, to tell its own rows from another's. */
    private var listedFor: Request? = null

    /**
     * The month and chip [wanted] counts pages for. Coming back from a row's editor after
     * sharing stopped observes the same month and chip again: its pages are kept, so the row
     * opened is still listed. Only another month or chip starts at one page.
     */
    private var pagesFor: Request? = null

    /** The search and chip whose results [found] holds. */
    private var foundFor: Found? = null

    /**
     * The search and chip [wantedFound] counts pages for. Coming back from a result's editor
     * after sharing stopped observes the same search again: its pages are kept, so the
     * result opened is still listed (R1). Only another search or chip starts at one page.
     */
    private var pagedFor: Found? = null

    private data class Request(val month: YearMonth, val filter: MovementFilter)

    private data class Found(val query: MovementQuery, val filter: MovementFilter)

    private val chip = filter.map { chip ->
        MovementFilter.entries.firstOrNull { it.name == chip } ?: MovementFilter.All
    }

    /**
     * The shared month, observed once for the day list and the search alike. Reintentar
     * observes it again, which reads both again.
     */
    private val observed = session.selectedMonth.flatMapLatest { month ->
        retry.flatMapLatest { lastGood.keep(month, snapshots.observe(month)) }
            .map { month to it }
    }.shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), replay = 1)

    private fun monthOf(month: YearMonth) = observed.filter { it.first == month }.map { it.second }

    val state: StateFlow<DayByDayState> = combine(session.selectedMonth, chip) { month, chip ->
        Request(month, chip)
    }.flatMapLatest { request ->
        if (request != pagesFor) {
            pagesFor = request
            wanted.value = PAGE_SIZE
        }
        combine(monthOf(request.month), wanted) { snapshot, count -> snapshot to count }
            .flatMapLatest { (snapshot, count) -> load(request, snapshot, count) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayByDayState.Loading)

    val found: StateFlow<SearchState> = combine(
        query.map(MovementQuery::of).distinctUntilChanged(),
        chip
    ) { query, chip -> query?.let { Found(it, chip) } }
        .flatMapLatest { request ->
            if (request == null) {
                foundFor = null
                pagedFor = null
                return@flatMapLatest flowOf<SearchState>(SearchState.Off)
            }
            if (request != pagedFor) {
                pagedFor = request
                wantedFound.value = PAGE_SIZE
            }
            flow<SearchState> {
                // A new query waits for typing to pause; a reply to an older one is never
                // read, as flatMapLatest drops it. Another chip reads at once.
                if (foundFor?.query != request.query) {
                    emit(SearchState.Loading(request.query.words, request.filter))
                    delay(TYPING_PAUSE_MS)
                }
                // Any month's observation tells a change: the revision is the whole history's.
                val month = observed.map { it.second }
                emitAll(
                    combine(month, wantedFound) { snapshot, count -> snapshot to count }
                        .flatMapLatest { (snapshot, count) -> loadFound(request, snapshot, count) }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Off)

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

            is DayByDayAction.Search -> saved[KEY_QUERY] = action.text.cutToSearchLimit()

            DayByDayAction.MoreFound -> {
                val ready = found.value as? SearchState.Ready
                if (ready != null && ready.more && !ready.busy && !ready.stale &&
                    wantedFound.value <= Int.MAX_VALUE - PAGE_SIZE
                ) {
                    wantedFound.update { it + PAGE_SIZE }
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
                    listed?.copy(busy = false, pageFailure = null, more = false, stale = true)
                        ?: DayByDayState.Failed(DayByDayProblem.Unread)
                )
                return@flow
            }

            is ReadState.Ready -> snapshot.value
        }
        emit(listed?.copy(busy = true, pageFailure = null) ?: DayByDayState.Loading)
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
                    val problem = read.error.problem()
                    emit(
                        listed?.copy(
                            busy = false,
                            pageFailure = problem,
                            // Reading on cannot help a sum too large or damaged data.
                            more = listed.more && problem == DayByDayProblem.Unread
                        )
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
                // Under Ingresos a day shows no expense sum: incomes are never netted in.
                days = groups(
                    rows,
                    days.takeIf {
                        request.filter != MovementFilter.Incomes
                    }.orEmpty()
                ),
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

    private fun loadFound(request: Found, snapshot: ResumenSnapshotState, count: Int) = flow {
        val words = request.query.words
        // Results already shown for this search and chip are kept through every failure.
        val listed = (found.value as? SearchState.Ready)?.takeIf { foundFor == request }
        val month = when (snapshot) {
            ReadState.Loading -> {
                emit(listed?.copy(busy = true) ?: SearchState.Loading(words, request.filter))
                return@flow
            }

            // Kept, said to be older; no more pages from them: Reintentar is the way on.
            is ReadState.Failed -> {
                emit(
                    listed?.copy(busy = false, pageFailure = null, more = false, stale = true)
                        ?: SearchState.Failed(words, request.filter, DayByDayProblem.Unread)
                )
                return@flow
            }

            is ReadState.Ready -> snapshot.value
        }
        val waiting = SearchState.Loading(words, request.filter)
        emit(listed?.copy(busy = true, pageFailure = null) ?: waiting)
        val names = sourceNames.fixed()
        val rows = mutableListOf<Movement>()
        var more = false
        var totals: SearchTotals? = null
        while (rows.size < count) {
            val read = search.read(
                request.query,
                request.filter,
                names,
                rows.lastOrNull(),
                minOf(count - rows.size, MovementSearch.MAX_PAGE)
            )
            when (read) {
                is Outcome.Failure -> {
                    val problem = read.error.problem()
                    emit(
                        listed?.copy(
                            busy = false,
                            pageFailure = problem,
                            // Reading on cannot help a sum too large or damaged data.
                            more = listed.more && problem == DayByDayProblem.Unread
                        ) ?: SearchState.Failed(words, request.filter, problem)
                    )
                    return@flow
                }

                is Outcome.Success -> {
                    val page = read.value
                    // Read between two changes: the next snapshot reads it again.
                    if (page.generation != month.budget.generation ||
                        page.revision != month.budget.revision
                    ) {
                        emit(listed?.copy(busy = true) ?: waiting)
                        return@flow
                    }
                    rows += page.movements
                    more = page.more
                    totals = page.totals
                    if (!more) break
                }
            }
        }
        val all = totals ?: return@flow
        // Pages are read one transaction each, at the same revision; the totals are the last
        // page's. A listed month they leave out cannot happen in one history: it is damage,
        // said as such, never a crash or a month without its figures.
        val months = foundMonths(rows, all.months.associateBy { it.month })
        if (months == null) {
            emit(
                listed?.copy(busy = false, pageFailure = DayByDayProblem.Damaged, more = false)
                    ?: SearchState.Failed(words, request.filter, DayByDayProblem.Damaged)
            )
            return@flow
        }
        foundFor = request
        emit(
            SearchState.Ready(
                words = words,
                amount = request.query.amount?.let { money.format(it.money) },
                today = month.budget.asOf,
                filter = request.filter,
                expenses = all.expenses?.let { FoundTotal(it.count, money.format(it.total)) },
                incomes = all.incomes?.let {
                    FoundTotal(it.count, money.format(it.total, signed = true))
                },
                months = months,
                more = more
            )
        )
    }

    /**
     * Consecutive results of one month under its heading, with the month's whole totals;
     * null when a listed month has none.
     */
    private fun foundMonths(
        rows: List<Movement>,
        totals: Map<YearMonth, MonthMatches>
    ): List<FoundMonth>? = rows.groupBy { YearMonth.from(it.date) }.map { (month, found) ->
        val matches = totals[month] ?: return null
        FoundMonth(
            month,
            matches.count,
            matches.spent?.let(money::format),
            found.map { FoundRow(it.date, row(it)) }
        )
    }

    private fun MovementReadFailure.problem() = when (this) {
        MovementReadFailure.Unrepresentable -> DayByDayProblem.TooLarge
        MovementReadFailure.CurrencyMismatch -> DayByDayProblem.Damaged
        is MovementReadFailure.Storage -> DayByDayProblem.Unread
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val KEY_FILTER = "dayByDay.filter"
        const val KEY_QUERY = "dayByDay.query"

        /** How long typing pauses before the query typed last is searched. */
        const val TYPING_PAUSE_MS = 250L

        /** The first [MovementQuery.MAX_LENGTH] units, never half of a two-unit character. */
        fun String.cutToSearchLimit(): String {
            if (length <= MovementQuery.MAX_LENGTH) return this
            val endsOnAHalf = Character.isHighSurrogate(this[MovementQuery.MAX_LENGTH - 1])
            return substring(
                0,
                if (endsOnAHalf) MovementQuery.MAX_LENGTH - 1 else MovementQuery.MAX_LENGTH
            )
        }
    }
}
