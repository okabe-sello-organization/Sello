package com.software.sello.feature.income

import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommittedIncome
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DeleteIncome
import com.software.sello.domain.port.EditIncome
import com.software.sello.domain.port.IncomeCommandOutcome
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonth
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomePage
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.IncomeUndoOffer
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RestoreIncome
import com.software.sello.feature.expense.uuid
import com.software.sello.testing.pesos
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

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
