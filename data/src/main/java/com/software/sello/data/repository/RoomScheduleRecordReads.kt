package com.software.sello.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.ScheduleRecordReads
import com.software.sello.domain.port.ScheduleRecordsState
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * What schedules recorded, from the entries themselves: one transaction reads the
 * expenses and incomes with an origin created after a moment, so a deleted one is gone
 * and nothing is kept apart from them. One that cannot be read fails the read.
 */
class RoomScheduleRecordReads(private val database: SelloDatabase) : ScheduleRecordReads {
    override suspend fun recordedAfter(since: Instant?): Outcome<List<ScheduleRecord>, StorageFailure> =
        reading {
            database.withTransaction {
                val after = since?.toEpochMilli() ?: Long.MIN_VALUE
                val records = mutableListOf<Pair<ScheduleRecord, Pair<String, Long>>>()
                for (row in database.expenseDao().recordedAfter(after)) {
                    when (val expense = row.toDomain()) {
                        is Outcome.Success ->
                            records += ScheduleRecord.Payment(expense.value) to (row.effectiveDate to row.sequence)
                        is Outcome.Failure -> return@withTransaction expense
                    }
                }
                for (row in database.incomeDao().recordedAfter(after)) {
                    when (val income = row.toDomain()) {
                        is Outcome.Success ->
                            records += ScheduleRecord.Earning(income.value) to (row.effectiveDate to row.sequence)
                        is Outcome.Failure -> return@withTransaction income
                    }
                }
                Outcome.Success(
                    records.sortedWith(compareBy({ it.second.first }, { it.second.second }))
                        .map { it.first }
                )
            }
        }

    override fun observeRecordedAfter(since: Instant?): Flow<ScheduleRecordsState> = flow {
        emit(ScheduleRecordsState.Loading)
        try {
            // Every command, catch-up included, moves the profile's revision.
            database.profileDao().changes().conflate().collect {
                emit(
                    when (val read = recordedAfter(since)) {
                        is Outcome.Success -> ScheduleRecordsState.Ready(read.value)
                        is Outcome.Failure -> ScheduleRecordsState.Failed(read.error)
                    }
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: SQLException) {
            emit(ScheduleRecordsState.Failed(StorageFailure.Unavailable(failure.javaClass.simpleName)))
        } catch (failure: IllegalStateException) {
            emit(ScheduleRecordsState.Failed(StorageFailure.Unavailable(failure.javaClass.simpleName)))
        }
    }
}
