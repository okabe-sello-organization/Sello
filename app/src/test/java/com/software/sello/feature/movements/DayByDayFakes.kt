package com.software.sello.feature.movements

import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.MonthMovements
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementPage
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.income.storedIncome
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.feature.resumen.resumenSnapshot
import com.software.sello.testing.asNewObservation
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart

fun dayCategory(number: Int, name: String, icon: String = "restaurant", archived: Boolean = false) =
    Category(
        categoryId(number),
        (CategoryName.of(name) as Outcome.Success).value,
        (IconKey.of(icon) as Outcome.Success).value,
        archived = archived,
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

fun spent(
    number: Int,
    category: Category,
    date: String,
    pesos: Long,
    note: String? = null,
    fixed: Boolean = false
) = Movement.Spent(
    storedExpense(number, 1, date, pesos, note).copy(
        categoryId = category.id,
        origin = if (fixed) {
            EntryOrigin(scheduleId(9), Occurrence(LocalDate.parse(date), 1))
        } else {
            null
        }
    ),
    category
)

fun received(
    number: Int,
    date: String,
    pesos: Long,
    note: String? = null,
    source: IncomeSource = IncomeSource.Salary
) = Movement.Received(storedIncome(number, date, pesos, source, note))

/** October 2026 as Resumen reads it on the 6th, at [revision]. */
fun octoberSnapshot(spent: Long = 1_240_300, income: Long = 3_150_000, revision: Long = 7) =
    resumenSnapshot(spent, income).let {
        it.copy(budget = it.budget.copy(asOf = LocalDate.of(2026, 10, 6), revision = revision))
    }

/** The month's movements a test lays out; each read slices them as storage would. */
class HandMonthMovements : MonthMovements {
    var rows: List<Movement> = emptyList()
    var days: Map<LocalDate, Money> = emptyMap()
    var generation = 1L
    var revision = 7L
    var failure: MovementReadFailure? = null
    val asked = mutableListOf<Triple<MovementFilter, Int, Int>>()

    override suspend fun read(
        month: YearMonth,
        filter: MovementFilter,
        offset: Int,
        limit: Int
    ): Outcome<MovementPage, MovementReadFailure> {
        asked += Triple(filter, offset, limit)
        failure?.let { return Outcome.Failure(it) }
        val listed = rows.filter {
            when (filter) {
                MovementFilter.All -> true
                MovementFilter.Expenses -> it is Movement.Spent
                MovementFilter.Incomes -> it is Movement.Received
            }
        }
        return Outcome.Success(
            MovementPage(
                listed.drop(offset).take(limit),
                offset + limit < listed.size,
                listed.size.toLong(),
                days,
                generation,
                revision
            )
        )
    }
}

/** Resumen's month, observed afresh each time as the real one is. */
class HandDayByDaySnapshots : ResumenSnapshots {
    private val states = mutableMapOf<YearMonth, MutableStateFlow<ResumenSnapshotState>>()
    var started = 0

    fun of(month: YearMonth) = states.getOrPut(month) {
        MutableStateFlow<ResumenSnapshotState>(ReadState.Loading)
    }

    override suspend fun read(month: YearMonth): Outcome<ResumenSnapshot, SnapshotFailure> =
        error("Día a día observes the month")

    override fun observe(month: YearMonth): Flow<ResumenSnapshotState> =
        of(month).asNewObservation().onStart { started++ }
}
