package com.software.sello.data.repository

import android.database.sqlite.SQLiteException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.policy.ReservationPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.MonthBudget
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.StoredSchedule
import java.time.YearMonth
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Reads a month's rows in one transaction and hands them to [MonthlyBudgetPolicy].
 * Nothing is calculated here or in SQL: the database returns rows and the policy
 * does every sum exactly, on [computation] and never on the caller's thread.
 */
class RoomMonthlySnapshots(
    private val database: SelloDatabase,
    private val clock: FinancialClock,
    private val computation: CoroutineContext
) : MonthlySnapshots {
    private class Rows(
        val budget: MonthBudget,
        val expenses: List<Expense>,
        val schedules: List<StoredSchedule>
    )

    override suspend fun read(month: YearMonth): Outcome<MonthlySnapshot, SnapshotFailure> =
        readAsOf(month, clock.today.value)

    private suspend fun readAsOf(
        month: YearMonth,
        today: FinancialDay
    ): Outcome<MonthlySnapshot, SnapshotFailure> {
        val rows = when (val read = reading { database.withTransaction { rows(month) } }) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return Outcome.Failure(SnapshotFailure.Storage(read.error))
        }
        // Off the caller's thread: a view model calls this from the main thread and the
        // work grows with the month's expenses. The rows are already out of the database,
        // so the figures are of one revision whatever is saved meanwhile.
        return withContext(computation) {
            MonthlyBudgetPolicy.snapshot(
                month = month,
                today = today,
                generation = rows.budget.generation,
                revision = rows.budget.revision,
                categories = rows.budget.categories.map { CategoryInMonth(it.category, it.state) },
                expenses = rows.expenses,
                // Read with the rest, so what is reserved belongs to the same revision.
                reserved = ReservationPolicy.reserved(rows.schedules, month)
            )
        }
    }

    private suspend fun rows(month: YearMonth): Outcome<Rows, StorageFailure> {
        val budget = when (val read = database.monthBudget(month)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        val expenses = when (val read = database.monthExpenses(month)) {
            is Outcome.Success -> read.value
            is Outcome.Failure -> return read
        }
        return when (val schedules = database.storedSchedules()) {
            is Outcome.Success -> Outcome.Success(Rows(budget, expenses, schedules.value))
            is Outcome.Failure -> schedules
        }
    }

    /**
     * Every command advances the revision in the profile row, so watching that row is
     * watching every committed change. Bursts are conflated: a slow reader gets the
     * latest state, not a queue of stale ones.
     */
    override fun observe(month: YearMonth): Flow<SnapshotState> = flow {
        emit(SnapshotState.Loading)
        var lastGood: MonthlySnapshot? = null
        try {
            combine(clock.today, database.profileDao().changes()) { today, _ -> today }
                .conflate()
                .collect { today ->
                    when (val read = readAsOf(month, today)) {
                        is Outcome.Success -> {
                            lastGood = read.value
                            emit(SnapshotState.Ready(read.value))
                        }

                        is Outcome.Failure -> emit(SnapshotState.Failed(read.error, lastGood))
                    }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SQLiteException) {
            emit(SnapshotState.Failed(unavailable(failure), lastGood))
        } catch (failure: IllegalStateException) {
            emit(SnapshotState.Failed(unavailable(failure), lastGood))
        }
    }

    private fun unavailable(failure: Exception) =
        SnapshotFailure.Storage(StorageFailure.Unavailable(failure.javaClass.simpleName))
}
