package com.software.sello.feature.recurrence

import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.domain.usecase.CatchUpSchedules
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * When catch-up runs (docs/testing/recurrence-examples.md, rule 6 and RE14): on start,
 * when the financial day changes and after a committed change, one run at a time. The
 * store answers at once unless a test holds it; real batches are tested on Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatchUpRunnerTest {
    private val zone = ZoneId.of("America/Bogota")
    private val day = MutableStateFlow(FinancialDay(LocalDate.of(2026, 10, 27), zone))
    private val clock = object : FinancialClock {
        override val today = day
    }
    private val book = MutableStateFlow<ScheduleBookState>(ReadState.Loading)
    private val reads = object : ScheduleReads {
        override suspend fun book(): Outcome<ScheduleBook, StorageFailure> = error("not read here")

        override fun observe(): Flow<ScheduleBookState> = book
    }
    private val asked = mutableListOf<LocalDate>()
    private var hold: CompletableDeferred<Unit>? = null
    private val store = ScheduleCatchUpStore { today, _ ->
        asked += today
        hold?.await()
        CatchUpBatch.Recorded(emptyList())
    }

    private fun ready(revision: Long) = ReadState.Ready(ScheduleBook(1, revision, emptyList()))

    @Test
    fun itRunsOnStartOnceTheScheduleBookIsRead() = runTest {
        val runner = CatchUpRunner(CatchUpSchedules(store, clock), clock, reads, backgroundScope)
        runCurrent()
        assertEquals(emptyList<LocalDate>(), asked)

        book.value = ready(4)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 10, 27)), asked)
        assertEquals(CatchUpState.Finished(CatchUpResult(emptyList(), null)), runner.state.value)
    }

    /** RE14: open from 27 to 28 October, the day change alone runs it again. */
    @Test
    fun re14aNewFinancialDayRunsItWithoutReopening() = runTest {
        CatchUpRunner(CatchUpSchedules(store, clock), clock, reads, backgroundScope)
        book.value = ready(4)
        runCurrent()

        day.value = FinancialDay(LocalDate.of(2026, 10, 28), zone)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)), asked)
    }

    @Test
    fun aCommittedChangeSuchAsASavedScheduleRunsItAgain() = runTest {
        CatchUpRunner(CatchUpSchedules(store, clock), clock, reads, backgroundScope)
        book.value = ready(4)
        runCurrent()

        book.value = ready(5)
        runCurrent()

        assertEquals(2, asked.size)
    }

    @Test
    fun changesDuringARunMakeOneMoreRunNotOneEach() = runTest {
        val running = CompletableDeferred<Unit>()
        hold = running
        CatchUpRunner(CatchUpSchedules(store, clock), clock, reads, backgroundScope)
        book.value = ready(4)
        runCurrent()

        book.value = ready(5)
        runCurrent()
        book.value = ready(6)
        runCurrent()
        day.value = FinancialDay(LocalDate.of(2026, 10, 28), zone)
        runCurrent()
        assertEquals(1, asked.size)
        hold = null
        running.complete(Unit)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)), asked)
    }

    @Test
    fun anUnreadableScheduleBookStartsNothing() = runTest {
        CatchUpRunner(CatchUpSchedules(store, clock), clock, reads, backgroundScope)

        book.value = ReadState.Failed(StorageFailure.Unavailable("SQLiteException"), null)
        runCurrent()

        assertEquals(emptyList<LocalDate>(), asked)
    }
}
