package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.YearSnapshot
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.YearInput
import com.software.sello.domain.policy.YearPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.YearSnapshotState
import com.software.sello.domain.port.YearSnapshots
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Tu año, read in one transaction so every figure belongs to one revision: each month shown
 * as Resumen reads a month, the year's incomes, the year before's expenses through the day
 * compared, and the earliest date anything was recorded.
 */
class RoomYearSnapshots(
    private val database: SelloDatabase,
    private val clock: FinancialClock,
    private val computation: CoroutineContext
) : YearSnapshots {
    private class Rows(
        val generation: Long,
        val revision: Long,
        val months: List<ResumenMonthRows>,
        val incomes: List<Income>,
        val previousExpenses: List<Expense>,
        val firstRecorded: LocalDate?
    )

    private suspend fun readAsOf(
        year: Int,
        today: FinancialDay
    ): Outcome<YearSnapshot, SnapshotFailure> {
        if (year > today.date.year) {
            return Outcome.Failure(SnapshotFailure.FutureMonth(YearMonth.from(today.date)))
        }
        val rows = when (val read = reading { database.withTransaction { rows(year, today) } }) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Storage(read.error))
        }
        return withContext(computation) {
            val months = when (val read = rows.months.inputs(today.date)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return@withContext read
            }
            YearPolicy.snapshot(
                year,
                today,
                rows.generation,
                rows.revision,
                YearInput(months, rows.incomes, rows.previousExpenses, rows.firstRecorded)
            )
        }
    }

    private suspend fun rows(year: Int, today: FinancialDay): Outcome<Rows, StorageFailure> {
        val chains = when (val read = database.everyCarryChain()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        val months = mutableListOf<ResumenMonthRows>()
        val incomes = mutableListOf<Income>()
        var generation = 0L
        var revision = 0L
        for (period in YearPolicy.monthsShown(year, today)) {
            val budget = when (val read = database.monthBudget(period)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            generation = budget.generation
            revision = budget.revision
            months += when (val read = database.resumenMonthRows(budget, chains)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            incomes += when (val read = database.monthIncomes(period)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
        }
        val previousThrough = YearPolicy.previousThrough(year, today)
        val previousExpenses = mutableListOf<Expense>()
        for (month in 1..previousThrough.monthValue) {
            previousExpenses += when (
                val read = database.monthExpenses(YearMonth.of(previousThrough.year, month))
            ) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
        }
        val earliest = listOfNotNull(
            database.expenseDao().earliestDate()?.let { "expense" to it },
            database.incomeDao().earliestDate()?.let { "income" to it }
        )
        val dates = earliest.map { (record, text) ->
            when (val date = EffectiveDates.parseDate(text)) {
                is Outcome.Success -> date.value

                is Outcome.Failure ->
                    return Outcome.Failure(StorageFailure.Integrity(record, null, "effective_date"))
            }
        }
        return Outcome.Success(
            Rows(generation, revision, months, incomes, previousExpenses, dates.minOrNull())
        )
    }

    override fun observe(year: Int): Flow<YearSnapshotState> = observeOn(
        combine(clock.today, database.profileDao().changes()) { today, _ -> today },
        SnapshotFailure::Storage
    ) { today -> readAsOf(year, today) }
}
