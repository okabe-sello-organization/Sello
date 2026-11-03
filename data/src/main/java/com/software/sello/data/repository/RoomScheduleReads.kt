package com.software.sello.data.repository

import android.database.SQLException
import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.mapper.profileFrom
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleMonthBook
import com.software.sello.domain.port.ScheduleMonthBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.ScheduleRecord
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow

/**
 * Reads every schedule with its pauses in one transaction at one revision. A schedule
 * that cannot be read fails the read: it is never left out, and a failed read is never
 * "no schedules".
 */
class RoomScheduleReads(private val database: SelloDatabase) : ScheduleReads {
    override suspend fun book(): Outcome<ScheduleBook, StorageFailure> = reading {
        database.withTransaction {
            val profile = when (val stored = profileFrom(database.profileDao().rows())) {
                is Outcome.Success -> stored.value
                is Outcome.Failure -> return@withTransaction stored
            }
            when (val schedules = database.storedSchedules()) {
                is Outcome.Success ->
                    Outcome.Success(
                        ScheduleBook(profile.generation, profile.revision, schedules.value)
                    )

                is Outcome.Failure -> schedules
            }
        }
    }

    override fun observe(): Flow<ScheduleBookState> = database.observeOnRevision { book() }

    /**
     * The book and, in the same transaction, every expense and income with an origin that
     * is dated in [month] as stored now. One entry that cannot be read fails the read.
     */
    override suspend fun month(month: YearMonth): Outcome<ScheduleMonthBook, StorageFailure> =
        reading {
            database.withTransaction {
                val book = when (val read = book()) {
                    is Outcome.Success -> read.value
                    is Outcome.Failure -> return@withTransaction read
                }
                val from = EffectiveDates.storedText(month.atDay(1))
                val until = EffectiveDates.storedText(month.atEndOfMonth())
                val records = mutableListOf<ScheduleRecord>()
                for (row in database.expenseDao().recordedFor(from, until)) {
                    when (val expense = row.toDomain()) {
                        is Outcome.Success -> records += ScheduleRecord.Payment(expense.value)
                        is Outcome.Failure -> return@withTransaction expense
                    }
                }
                for (row in database.incomeDao().recordedFor(from, until)) {
                    when (val income = row.toDomain()) {
                        is Outcome.Success -> records += ScheduleRecord.Earning(income.value)
                        is Outcome.Failure -> return@withTransaction income
                    }
                }
                Outcome.Success(ScheduleMonthBook(book, month, records))
            }
        }

    override fun observeMonth(month: YearMonth): Flow<ScheduleMonthBookState> =
        database.observeOnRevision { month(month) }
}
