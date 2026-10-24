package com.software.sello.domain.usecase

import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.valueOrFail
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How catch-up goes through its batches (docs/testing/recurrence-examples.md, rules 4
 * and 6). The store here answers from a script; real batches are tested on Room.
 */
class CatchUpSchedulesTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val clock = object : FinancialClock {
        override val today =
            MutableStateFlow(
                FinancialDay(this@CatchUpSchedulesTest.today, ZoneId.of("America/Bogota"))
            )
    }
    private val cafe = ScheduleId.of("00000000-0000-4000-8000-000000000200").valueOrFail()

    /** The [day]th occurrence of a daily café after 31 July, as an expense. */
    private fun recorded(day: Int) = RecordedOccurrence.Payment(
        EntryOrigin(cafe, Occurrence(LocalDate.of(2026, 7, 31).plusDays(day.toLong()), 1)),
        ExpenseId.of("00000000-0000-4000-8000-%012d".format(1000 + day)).valueOrFail()
    )

    private class Scripted(private val batches: List<CatchUpBatch>) : ScheduleCatchUpStore {
        val asked = mutableListOf<Pair<LocalDate, Int>>()

        override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch {
            asked += today to limit
            return batches.getOrElse(asked.size - 1) { CatchUpBatch.Recorded(emptyList()) }
        }
    }

    /** RE11: 85 occurrences are two batches, 50 and 35; a short batch means nothing is left. */
    @Test
    fun re11everyMissedOccurrenceIsRecordedInBatchesOfFifty() = runBlocking {
        val first = (1..50).map(::recorded)
        val second = (51..85).map(::recorded)
        val store = Scripted(listOf(CatchUpBatch.Recorded(first), CatchUpBatch.Recorded(second)))
        val heard = mutableListOf<List<RecordedOccurrence>>()

        val result = CatchUpSchedules(store, clock).run { heard += it }

        assertEquals(CatchUpResult(first + second, null), result)
        assertEquals(listOf(today to 50, today to 50), store.asked)
        assertEquals(listOf(first, second), heard)
    }

    @Test
    fun exactlyFiftyAsksOnceMoreAndNothingDueRecordsNothing() = runBlocking {
        val fifty = (1..50).map(::recorded)
        val store = Scripted(listOf(CatchUpBatch.Recorded(fifty)))
        val heard = mutableListOf<List<RecordedOccurrence>>()

        val result = CatchUpSchedules(store, clock).run { heard += it }

        assertEquals(CatchUpResult(fifty, null), result)
        assertEquals(2, store.asked.size)
        assertEquals(listOf(fifty), heard)
        assertEquals(
            CatchUpResult(emptyList(), null),
            CatchUpSchedules(Scripted(emptyList()), clock).run()
        )
    }

    /** RE12: what a batch committed stays when a later one fails. */
    @Test
    fun aFailedBatchKeepsWhatEarlierBatchesRecorded() = runBlocking {
        val first = (1..50).map(::recorded)
        val failure = StorageFailure.Unavailable("SQLiteFullException")
        val store = Scripted(listOf(CatchUpBatch.Recorded(first), CatchUpBatch.Failed(failure)))

        val result = CatchUpSchedules(store, clock).run()

        assertEquals(CatchUpResult(first, CatchUpStop.Failed(failure)), result)
        assertEquals(2, store.asked.size)
    }

    /** RE13: after an unknown commit nothing more is tried; the next run starts from storage. */
    @Test
    fun anUnknownCommitStopsTheRun() = runBlocking {
        val store =
            Scripted(listOf(CatchUpBatch.Unknown, CatchUpBatch.Recorded(listOf(recorded(1)))))

        val result = CatchUpSchedules(store, clock).run()

        assertEquals(CatchUpResult(emptyList(), CatchUpStop.Unknown), result)
        assertEquals(1, store.asked.size)
    }

    @Test
    fun todayIsReadOnceForTheWholeRun() = runBlocking {
        val store = object : ScheduleCatchUpStore {
            val days = mutableListOf<LocalDate>()

            override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch {
                days += today
                // Midnight passes while the first batch is written.
                clock.today.value = FinancialDay(today.plusDays(1), clock.today.value.zone)
                return CatchUpBatch.Recorded(
                    if (days.size ==
                        1
                    ) {
                        (1..50).map(::recorded)
                    } else {
                        emptyList()
                    }
                )
            }
        }

        CatchUpSchedules(store, clock).run()

        assertEquals(listOf(today, today), store.days)
    }
}
