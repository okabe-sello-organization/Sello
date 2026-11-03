package com.software.sello.domain.port

import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.StorageFailure
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow

/**
 * A stored schedule. [sequence] is its creation order, which catch-up follows among
 * schedules due on the same day (docs/testing/recurrence-examples.md, rule 6).
 * [handled] is the last occurrence catch-up has dealt with, or the end of the day a change
 * was made (rule 7): it and every earlier one are never recorded, even when the entry
 * they made was deleted (rule 4).
 */
data class StoredSchedule(
    val schedule: Schedule,
    val sequence: Long,
    val handled: Occurrence?,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
)

/** Every schedule at one [revision] of [generation], in creation order. */
data class ScheduleBook(
    val generation: Long,
    val revision: Long,
    val schedules: List<StoredSchedule>
)

/** The book as [ScheduleReads.observe] sees it. A failed read keeps the last good book. */
typealias ScheduleBookState = ReadState<ScheduleBook, StorageFailure>

/**
 * The [book] with every entry a schedule recorded that is dated in [month] as stored now
 * ([records]): an edited one at its edited amount and date, a deleted one gone. Both are
 * read at the book's revision.
 */
data class ScheduleMonthBook(
    val book: ScheduleBook,
    val month: YearMonth,
    val records: List<ScheduleRecord>
)

/** [ScheduleReads.observeMonth]'s state. A failed read keeps the last good one. */
typealias ScheduleMonthBookState = ReadState<ScheduleMonthBook, StorageFailure>

interface ScheduleReads {
    /** One damaged schedule fails the read: it is never left out, and a failure is never "none". */
    suspend fun book(): Outcome<ScheduleBook, StorageFailure>

    /** [book], read again after every committed change: Loading first, then each read. */
    fun observe(): Flow<ScheduleBookState>

    /** The book and what its schedules recorded for [month], read in one transaction. */
    suspend fun month(month: YearMonth): Outcome<ScheduleMonthBook, StorageFailure>

    /** [month], read again after every committed change: Loading first, then each read. */
    fun observeMonth(month: YearMonth): Flow<ScheduleMonthBookState>
}
