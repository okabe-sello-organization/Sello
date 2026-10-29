package com.software.sello.feature.expense

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import com.software.sello.domain.port.ExpenseReads
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomePage
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.UndoOffer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

fun uuid(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

fun categoryId(number: Int) = (CategoryId.of(uuid(number)) as Outcome.Success).value

fun expenseId(number: Int) = (ExpenseId.of(uuid(number)) as Outcome.Success).value

fun pesos(amount: Long) = (TransactionAmount.of(Money.cop(amount)) as Outcome.Success).value

/** A category as a month sees it: [limit] in pesos, or null for no limit. */
fun categoryIn(
    number: Int,
    name: String,
    limit: Long?,
    archived: Boolean = false,
    icon: String = "home"
) = CategoryInMonth(
    Category(
        categoryId(number),
        (CategoryName.of(name) as Outcome.Success).value,
        (IconKey.of(icon) as Outcome.Success).value,
        archived = archived,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    ),
    MonthBudgetState.Limited(
        limit?.let { (BudgetLimit.finite(Money.cop(it)) as Outcome.Success).value }
            ?: BudgetLimit.Unlimited,
        explicit = false
    )
)

fun storedExpense(number: Int, category: Int, date: String, amount: Long, note: String? = null) =
    Expense(
        expenseId(number),
        categoryId(category),
        LocalDate.parse(date),
        number.toLong(),
        pesos(amount),
        (Note.of(note) as Outcome.Success).value,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

/** A real snapshot, worked out by the real policy from the rows given. */
fun monthOf(
    today: String,
    categories: List<CategoryInMonth>,
    expenses: List<Expense> = emptyList(),
    month: YearMonth = YearMonth.from(LocalDate.parse(today)),
    generation: Long = 1
): MonthlySnapshot = (
    MonthlyBudgetPolicy.snapshot(
        month,
        FinancialDay(LocalDate.parse(today), ZoneId.of("America/Bogota")),
        generation,
        revision = 1,
        categories = categories,
        expenses = expenses
    ) as Outcome.Success
    ).value

/** A snapshot source a test drives; it records which months were observed. */
class HandSnapshots : MonthlySnapshots {
    val observed = mutableListOf<YearMonth>()
    private val states = mutableMapOf<YearMonth, MutableStateFlow<SnapshotState>>()

    fun of(month: YearMonth) = states.getOrPut(month) { MutableStateFlow(ReadState.Loading) }

    fun ready(snapshot: MonthlySnapshot) {
        of(snapshot.month).value = ReadState.Ready(snapshot)
    }

    /** One read: the month as it was last published, or a failure when it never was. */
    override suspend fun read(month: YearMonth): Outcome<MonthlySnapshot, SnapshotFailure> =
        when (val state = of(month).value) {
            is ReadState.Ready -> Outcome.Success(state.value)
            else -> Outcome.Failure(SnapshotFailure.Storage(StorageFailure.Unavailable("unread")))
        }

    override fun observe(month: YearMonth): Flow<SnapshotState> =
        of(month).onStart { observed += month }
}

/**
 * Commands a test scripts: what each one answers, and what it was sent. Unless told
 * otherwise every command commits.
 */
class HandExpenseCommands : ExpenseCommands {
    val created = mutableListOf<CreateExpense>()
    val edited = mutableListOf<EditExpense>()
    val deleted = mutableListOf<DeleteExpense>()
    val restored = mutableListOf<RestoreExpense>()
    val looked = mutableListOf<OperationId>()
    var answer: (CreateExpense) -> ExpenseCommandOutcome =
        { CommandOutcome.Committed(committed(it.operationId), replayed = false) }
    var editAnswer: (EditExpense) -> ExpenseCommandOutcome = {
        CommandOutcome.Committed(
            committed(it.operationId, it.expenseId, OperationKind.EditExpense),
            replayed = false
        )
    }
    var deleteAnswer: (DeleteExpense) -> ExpenseCommandOutcome = {
        CommandOutcome.Committed(
            committed(it.operationId, it.expenseId, OperationKind.DeleteExpense),
            replayed = false
        )
    }
    var restoreAnswer: (RestoreExpense) -> ExpenseCommandOutcome = {
        CommandOutcome.Committed(
            committed(it.operationId, expenseId(900), OperationKind.RestoreExpense),
            replayed = false
        )
    }
    var found: (
        OperationId
    ) -> Outcome<CommittedExpense?, StorageFailure> = { Outcome.Success(null) }

    /** What [undoOffer] answers: the adapter's own knowledge of the deletion on offer. */
    var offer: () -> UndoOffer? = { null }

    /** When set, every command waits here: the save is in flight. */
    var hold: CompletableDeferred<Unit>? = null

    fun committed(operation: OperationId, expense: Int = 900) =
        committed(operation, expenseId(expense), OperationKind.CreateExpense)

    fun committed(operation: OperationId, expense: ExpenseId, kind: OperationKind) =
        CommittedExpense(OperationReceipt(operation, kind, 1, 1, Instant.EPOCH), expense)

    override suspend fun create(command: CreateExpense): ExpenseCommandOutcome {
        created += command
        hold?.await()
        return answer(command)
    }

    override suspend fun find(
        operationId: OperationId
    ): Outcome<CommittedExpense?, StorageFailure> {
        looked += operationId
        return found(operationId)
    }

    override suspend fun edit(command: EditExpense): ExpenseCommandOutcome {
        edited += command
        hold?.await()
        return editAnswer(command)
    }

    override suspend fun delete(command: DeleteExpense): ExpenseCommandOutcome {
        deleted += command
        hold?.await()
        return deleteAnswer(command)
    }

    override suspend fun restore(command: RestoreExpense): ExpenseCommandOutcome {
        restored += command
        hold?.await()
        return restoreAnswer(command)
    }

    override fun undoOffer(): UndoOffer? = offer()
}

/** What "storage" holds, set by the test. */
class HandExpenseReads : ExpenseReads {
    val stored = mutableMapOf<ExpenseId, Expense>()
    var lastUsed: CategoryId? = null
    var failure: StorageFailure? = null
    var reads = 0

    override suspend fun byId(id: ExpenseId): Outcome<Expense?, StorageFailure> {
        reads++
        failure?.let { return Outcome.Failure(it) }
        return Outcome.Success(stored[id])
    }

    override suspend fun lastUsedCategory(): Outcome<CategoryId?, StorageFailure> =
        failure?.let { Outcome.Failure(it) } ?: Outcome.Success(lastUsed)

    /** Every page asked for, as (cursor, limit). */
    val pages = mutableListOf<Pair<ExpenseCursor?, Int>>()

    /** Lets a test keep a page read waiting. */
    var hold: CompletableDeferred<Unit>? = null

    override suspend fun inCategory(
        categoryId: CategoryId,
        month: YearMonth,
        after: ExpenseCursor?,
        limit: Int
    ): Outcome<ExpensePage, StorageFailure> {
        pages += after to limit
        hold?.await()
        failure?.let { return Outcome.Failure(it) }
        val newestFirst = stored.values
            .filter { it.categoryId == categoryId && YearMonth.from(it.date) == month }
            .sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.sequence })
            .filter {
                after == null || it.date < after.date ||
                    (it.date == after.date && it.sequence < after.sequence)
            }
        val page = newestFirst.take(limit)
        val next = page.lastOrNull()?.takeIf { newestFirst.size > limit }
            ?.let { ExpenseCursor(it.date, it.sequence) }
        return Outcome.Success(ExpensePage(page, next))
    }
}

fun incomeId(number: Int) = (IncomeId.of(uuid(number)) as Outcome.Success).value

fun storedIncome(
    number: Int,
    date: String,
    amount: Long,
    source: IncomeSource = IncomeSource.Salary,
    note: String? = null
) = Income(
    incomeId(number),
    LocalDate.parse(date),
    number.toLong(),
    pesos(amount),
    source,
    (Note.of(note) as Outcome.Success).value,
    version = 1,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH
)

/** Income commands a test scripts. Unless told otherwise every command commits. */
class HandIncomeCommands : IncomeCommands {
    val created = mutableListOf<CreateIncome>()
    val edited = mutableListOf<EditIncome>()
    val deleted = mutableListOf<DeleteIncome>()
    val restored = mutableListOf<RestoreIncome>()
    val looked = mutableListOf<OperationId>()
    var createAnswer: (CreateIncome) -> IncomeCommandOutcome =
        { CommandOutcome.Committed(committed(it.operationId, incomeId(900)), false) }
    var editAnswer: (EditIncome) -> IncomeCommandOutcome =
        { CommandOutcome.Committed(committed(it.operationId, it.incomeId), false) }
    var deleteAnswer: (DeleteIncome) -> IncomeCommandOutcome =
        { CommandOutcome.Committed(committed(it.operationId, it.incomeId), false) }
    var restoreAnswer: (RestoreIncome) -> IncomeCommandOutcome =
        { CommandOutcome.Committed(committed(it.operationId, incomeId(900)), false) }
    var found: (
        OperationId
    ) -> Outcome<CommittedIncome?, StorageFailure> = { Outcome.Success(null) }
    var offer: () -> IncomeUndoOffer? = { null }
    var hold: CompletableDeferred<Unit>? = null

    fun committed(operation: OperationId, income: IncomeId = incomeId(900)) = CommittedIncome(
        OperationReceipt(operation, OperationKind.CreateIncome, 1, 1, Instant.EPOCH),
        income
    )

    override suspend fun create(command: CreateIncome): IncomeCommandOutcome {
        created += command
        hold?.await()
        return createAnswer(command)
    }

    override suspend fun edit(command: EditIncome): IncomeCommandOutcome {
        edited += command
        hold?.await()
        return editAnswer(command)
    }

    override suspend fun delete(command: DeleteIncome): IncomeCommandOutcome {
        deleted += command
        hold?.await()
        return deleteAnswer(command)
    }

    override suspend fun restore(command: RestoreIncome): IncomeCommandOutcome {
        restored += command
        hold?.await()
        return restoreAnswer(command)
    }

    override fun undoOffer(): IncomeUndoOffer? = offer()

    override suspend fun find(operationId: OperationId): Outcome<CommittedIncome?, StorageFailure> {
        looked += operationId
        return found(operationId)
    }
}

/** What "storage" holds of income, set by the test. A month's total is published by hand. */
class HandIncomeReads : IncomeReads {
    val stored = mutableMapOf<IncomeId, Income>()
    var failure: StorageFailure? = null
    val pages = mutableListOf<Pair<IncomeCursor?, Int>>()
    val observed = mutableListOf<YearMonth>()
    private val months = mutableMapOf<YearMonth, MutableStateFlow<IncomeMonthState>>()

    fun of(month: YearMonth) = months.getOrPut(month) { MutableStateFlow(ReadState.Loading) }

    /** Publishes [month] as storage would after a change: the exact total of what is stored. */
    fun publish(month: YearMonth, generation: Long = 1, revision: Long = 1) {
        val incomes = stored.values.filter { YearMonth.from(it.date) == month }
        of(month).value = ReadState.Ready(
            IncomeMonth(
                month,
                generation,
                revision,
                Money.cop(incomes.sumOf { it.amount.money.minorUnits }),
                incomes.size
            )
        )
    }

    override suspend fun byId(id: IncomeId): Outcome<Income?, StorageFailure> =
        failure?.let { Outcome.Failure(it) } ?: Outcome.Success(stored[id])

    override suspend fun inMonth(
        month: YearMonth,
        after: IncomeCursor?,
        limit: Int
    ): Outcome<IncomePage, StorageFailure> {
        pages += after to limit
        failure?.let { return Outcome.Failure(it) }
        val newestFirst = stored.values.filter { YearMonth.from(it.date) == month }
            .sortedWith(compareByDescending<Income> { it.date }.thenByDescending { it.sequence })
            .filter {
                after == null || it.date < after.date ||
                    (it.date == after.date && it.sequence < after.sequence)
            }
        val page = newestFirst.take(limit)
        val next = page.lastOrNull()?.takeIf { newestFirst.size > limit }
            ?.let { IncomeCursor(it.date, it.sequence) }
        return Outcome.Success(IncomePage(page, next))
    }

    override suspend fun month(month: YearMonth): Outcome<IncomeMonth, StorageFailure> =
        when (val state = of(month).value) {
            is ReadState.Ready -> Outcome.Success(state.value)
            else -> Outcome.Failure(StorageFailure.Unavailable("unread"))
        }

    override fun observeMonth(month: YearMonth): Flow<IncomeMonthState> =
        of(month).onStart { observed += month }
}
