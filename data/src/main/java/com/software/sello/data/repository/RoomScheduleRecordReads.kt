package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.ScheduleRecordReads
import com.software.sello.domain.port.ScheduleRecordsState
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * What schedules recorded, from the entries themselves: one transaction reads the
 * expenses and incomes with an origin created after a moment, so a deleted one is gone
 * and nothing is kept apart from them. One that cannot be read fails the read.
 */
class RoomScheduleRecordReads(private val database: SelloDatabase) : ScheduleRecordReads {
    override suspend fun recordedAfter(
        since: Instant?
    ): Outcome<List<ScheduleRecord>, StorageFailure> = reading {
        database.withTransaction {
            val after = since?.toEpochMilli() ?: Long.MIN_VALUE
            val records = mutableListOf<Placed>()
            for (row in database.expenseDao().recordedAfter(after)) {
                when (val expense = row.toDomain()) {
                    is Outcome.Success -> records += Placed(
                        row.effectiveDate,
                        row.sequence,
                        ScheduleRecord.Payment(expense.value)
                    )

                    is Outcome.Failure -> return@withTransaction expense
                }
            }
            for (row in database.incomeDao().recordedAfter(after)) {
                when (val income = row.toDomain()) {
                    is Outcome.Success -> records += Placed(
                        row.effectiveDate,
                        row.sequence,
                        ScheduleRecord.Earning(income.value)
                    )

                    is Outcome.Failure -> return@withTransaction income
                }
            }
            Outcome.Success(
                records.sortedWith(compareBy({ it.date }, { it.sequence })).map { it.record }
            )
        }
    }

    // Every command, catch-up included, moves the profile's revision.
    override fun observeRecordedAfter(since: Instant?): Flow<ScheduleRecordsState> =
        database.observeOnRevision { recordedAfter(since) }
}

/** A record with where it falls: its date, then the order it was put down. */
private class Placed(val date: String, val sequence: Long, val record: ScheduleRecord)
