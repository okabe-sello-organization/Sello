package com.software.sello.domain.port

import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** An entry catch-up recorded for a schedule: it carries its origin (rule 4). */
sealed interface ScheduleRecord {
    data class Payment(val expense: Expense) : ScheduleRecord

    data class Earning(val income: Income) : ScheduleRecord
}

/** Never shown as nothing recorded. */
typealias ScheduleRecordsState = ReadState<List<ScheduleRecord>, StorageFailure>

/**
 * What catch-up recorded since the notice was last dismissed (recurrence-examples rule
 * 14): every expense and income with an origin created after a moment, by date, then
 * creation order. One deleted since is simply not there.
 */
interface ScheduleRecordReads {
    /** Every such entry created after [since]; all of them while [since] is null. */
    suspend fun recordedAfter(since: Instant?): Outcome<List<ScheduleRecord>, StorageFailure>

    /** [recordedAfter], read again after every committed change: Loading first. */
    fun observeRecordedAfter(since: Instant?): Flow<ScheduleRecordsState>
}
