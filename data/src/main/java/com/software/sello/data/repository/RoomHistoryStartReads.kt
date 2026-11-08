package com.software.sello.data.repository

import androidx.room.withTransaction
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.HistoryStart
import com.software.sello.domain.port.HistoryStartReads
import com.software.sello.domain.port.HistoryStartState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The earliest expense date and the earliest date of any movement (expense, income, goal
 * aporte or retiro, investment movement), read together in one transaction. Deleted ones
 * live in their own tables and do not count. A stored date that cannot be read fails the
 * read: it is never taken as no history.
 */
class RoomHistoryStartReads(private val database: SelloDatabase) : HistoryStartReads {
    override fun observe(): Flow<HistoryStartState> = database.observeOnRevision { read() }

    private suspend fun read(): Outcome<HistoryStart, StorageFailure> = reading {
        database.withTransaction {
            val stored = listOf(
                "expense" to database.expenseDao().earliestDate(),
                "income" to database.incomeDao().earliestDate(),
                "goal_event" to database.goalDao().earliestMovementDate(),
                "investment_movement" to database.investmentDao().earliestMovementDate()
            )
            val dates = stored.map { (record, text) ->
                when (val date = date(record, text)) {
                    is Outcome.Success -> date.value
                    is Outcome.Failure -> return@withTransaction date
                }
            }
            Outcome.Success(HistoryStart(dates.first(), dates.filterNotNull().minOrNull()))
        }
    }

    private fun date(record: String, text: String?): Outcome<LocalDate?, StorageFailure> {
        if (text == null) return Outcome.Success(null)
        return when (val date = EffectiveDates.parseDate(text)) {
            is Outcome.Success -> date

            is Outcome.Failure ->
                Outcome.Failure(StorageFailure.Integrity(record, null, "effective_date"))
        }
    }
}
