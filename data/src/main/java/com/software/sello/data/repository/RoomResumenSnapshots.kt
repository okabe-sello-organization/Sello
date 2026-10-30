package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ResumenSnapshot
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.ResumenMonthInput
import com.software.sello.domain.policy.ResumenPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class RoomResumenSnapshots(
    private val database: SelloDatabase,
    private val clock: FinancialClock,
    private val computation: CoroutineContext
) : ResumenSnapshots {
    private class Rows(
        val generation: Long,
        val revision: Long,
        val months: List<ResumenMonthInput>,
        /** Per month, the carry rows of each category a chain covers then (ADR 0020 CP16). */
        val carry: List<List<CarryRows>>,
        val incomes: List<Income>
    )

    override suspend fun read(month: YearMonth): Outcome<ResumenSnapshot, SnapshotFailure> =
        readAsOf(month, clock.today.value)

    private suspend fun readAsOf(
        month: YearMonth,
        today: FinancialDay
    ): Outcome<ResumenSnapshot, SnapshotFailure> {
        val rows = when (val read = reading { database.withTransaction { rows(month) } }) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Storage(read.error))
        }
        return withContext(computation) {
            val months = ArrayList<ResumenMonthInput>(rows.months.size)
            for ((input, carry) in rows.months.zip(rows.carry)) {
                val carryover = ArrayList<CategoryCarryInput>(carry.size)
                for (category in carry) {
                    when (val read = category.snapshotInput(today.date)) {
                        is Outcome.Success -> carryover += read.value
                        is Outcome.Failure -> return@withContext read
                    }
                }
                months += input.copy(carryover = carryover)
            }
            ResumenPolicy.snapshot(
                month,
                today,
                rows.generation,
                rows.revision,
                months,
                rows.incomes
            )
        }
    }

    private suspend fun rows(month: YearMonth): Outcome<Rows, StorageFailure> {
        val selected = when (val budget = database.monthBudget(month)) {
            is Outcome.Success -> budget.value
            is Outcome.Failure -> return budget
        }
        val months = mutableListOf<ResumenMonthInput>()
        val carry = mutableListOf<List<CarryRows>>()
        // Each month's carry from the chain covering it alone, read in this transaction
        // (SELLO-038 consumer boundary, as RoomMonthlySnapshots).
        val chains = when (val read = database.everyCarryChain()) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        for (period in ResumenPolicy.monthsEndingIn(month)) {
            val budget = if (period == month) {
                selected
            } else {
                when (val read = database.monthBudget(period)) {
                    is Outcome.Success -> read.value
                    is Outcome.Failure -> return read
                }
            }
            val expenses = when (val read = database.monthExpenses(period)) {
                is Outcome.Success -> read.value
                is Outcome.Failure -> return read
            }
            months += ResumenMonthInput(
                period,
                budget.categories.map { CategoryInMonth(it.category, it.state) },
                expenses
            )
            val covered = ArrayList<CarryRows>()
            for ((category, of) in chains) {
                when (val read = database.carryInRows(category, of, period)) {
                    is Outcome.Success -> read.value?.let { covered += it }
                    is Outcome.Failure -> return read
                }
            }
            carry += covered
        }
        val incomes = when (val read = database.monthIncomes(month)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        return Outcome.Success(Rows(selected.generation, selected.revision, months, carry, incomes))
    }

    override fun observe(month: YearMonth): Flow<ResumenSnapshotState> = observeOn(
        combine(clock.today, database.profileDao().changes()) { today, _ -> today },
        SnapshotFailure::Storage
    ) { today -> readAsOf(month, today) }
}
