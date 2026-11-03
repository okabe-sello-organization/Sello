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
 *
 * Bounded (SELLO-165), even when nothing was ever seen: the earliest created, at most
 * [PER_KIND] of each kind, and then every entry of the moment where it stops. Listo marks
 * seen up to the latest moment shown, so whatever is left out comes after it, next time.
 */
class RoomScheduleRecordReads(private val database: SelloDatabase) : ScheduleRecordReads {
    override suspend fun recordedAfter(
        since: Instant?
    ): Outcome<List<ScheduleRecord>, StorageFailure> = reading {
        database.withTransaction {
            val after = since?.toEpochMilli() ?: Long.MIN_VALUE
            val expenses = database.expenseDao().recordedAfter(after, PER_KIND)
            val incomes = database.incomeDao().recordedAfter(after, PER_KIND)
            // A kind that filled its share may have more after its last row: stop at the
            // earliest such moment, with all of it.
            val stop = listOfNotNull(
                expenses.takeIf { it.size == PER_KIND }?.last()?.createdAt,
                incomes.takeIf { it.size == PER_KIND }?.last()?.createdAt
            ).minOrNull()
            val records = mutableListOf<Placed>()
            val expenseRows = if (stop == null) {
                expenses
            } else {
                expenses.filter { it.createdAt < stop } + database.expenseDao().recordedAt(stop)
            }
            for (row in expenseRows) {
                when (val expense = row.toDomain()) {
                    is Outcome.Success -> records += Placed(
                        row.effectiveDate,
                        row.sequence,
                        ScheduleRecord.Payment(expense.value)
                    )

                    is Outcome.Failure -> return@withTransaction expense
                }
            }
            val incomeRows = if (stop == null) {
                incomes
            } else {
                incomes.filter { it.createdAt < stop } + database.incomeDao().recordedAt(stop)
            }
            for (row in incomeRows) {
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

    companion object {
        /** At most this many of each kind before the moment where a read stops. */
        const val PER_KIND = 250
    }
}

/** A record with where it falls: its date, then the order it was put down. */
private class Placed(val date: String, val sequence: Long, val record: ScheduleRecord)
