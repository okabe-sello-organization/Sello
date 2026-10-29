package com.software.sello.devtools

import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.RecordedOccurrence
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommandOutcome
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schedule writes in the debug build go through the same fence as every other financial
 * write, and catch-up, which has no operation of its own, is fenced and marks the
 * sandbox changed when it records.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleSandboxPortsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val ledger = Ledger()
    private val fence = WriteFence()
    private var saved = 0
    private val today = LocalDate.of(2026, 10, 25)

    private fun pause(n: Int) = PauseSchedule(
        OperationId.of("00000000-0000-4000-8000-00000000000$n").value(),
        1,
        ScheduleId.of("00000000-0000-4000-9000-000000000001").value(),
        2
    )

    @Test
    fun aNewScheduleChangeIsReportedAndAReplayOrARejectionIsNot() = runTest {
        val commands = FencedScheduleCommands(LedgerSchedules(ledger), fence) { saved++ }

        val first = commands.submit(pause(1))
        val replay = commands.submit(pause(1))
        ledger.answer = Ledger.Next.Refuse
        commands.submit(pause(2))

        assertFalse((first as CommandOutcome.Committed).replayed)
        assertTrue((replay as CommandOutcome.Committed).replayed)
        assertEquals(1, saved)
    }

    @Test
    fun aScheduleCommandAfterTheFenceClosedNeverReachesStorage() = runTest {
        val schedules = LedgerSchedules(ledger)
        val commands = FencedScheduleCommands(schedules, fence) { saved++ }
        fence.close()

        val late = launch { commands.submit(pause(3)) }
        runCurrent()

        assertTrue(schedules.sent.isEmpty())
        assertFalse(late.isCompleted)
        late.cancel()
    }

    private class Batches(val answers: ArrayDeque<CatchUpBatch>) : ScheduleCatchUpStore {
        var asked = 0

        override suspend fun recordDue(today: LocalDate, limit: Int): CatchUpBatch {
            asked++
            return answers.removeFirst()
        }
    }

    @Test
    fun catchUpIsFencedAndOnlyARecordedBatchMarksTheSandboxChanged() = runTest {
        val recorded = CatchUpBatch.Recorded(listOf(occurrence()))
        val store = Batches(
            ArrayDeque(
                listOf(
                    recorded,
                    CatchUpBatch.Recorded(emptyList()),
                    CatchUpBatch.Failed(StorageFailure.Unavailable("x")),
                    CatchUpBatch.Unknown
                )
            )
        )
        val fenced = FencedScheduleCatchUp(store, fence) { saved++ }

        assertEquals(recorded, fenced.recordDue(today, 50))
        repeat(3) { fenced.recordDue(today, 50) }
        assertEquals(1, saved)

        fence.close()
        val late = launch { fenced.recordDue(today, 50) }
        runCurrent()
        assertEquals(4, store.asked)
        assertFalse(late.isCompleted)
        late.cancel()
    }

    private fun occurrence() = RecordedOccurrence.Payment(
        EntryOrigin(
            ScheduleId.of("00000000-0000-4000-9000-000000000001").value(),
            Occurrence(today, 0)
        ),
        ExpenseId.of("00000000-0000-4000-8000-000000000099").value()
    )
}
