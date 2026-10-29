package com.software.sello.platform

import com.software.sello.testing.TestDispatcherProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClockOwnershipTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val oneMinuteBeforeNovember = Instant.parse("2026-11-01T04:59:00Z")
    private val signals = RecordingTimeSignals()

    private fun TestScope.dispatchers() = TestDispatcherProvider(testScheduler)

    private fun TestScope.clockIn(owner: CoroutineScope): SystemFinancialClock {
        val zone = ProcessFinancialZone().apply { initialize(bogota) }
        val clock =
            SystemFinancialClock(
                zone,
                VirtualAuditClock(testScheduler, oneMinuteBeforeNovember),
                signals,
                owner
            )
        runCurrent()
        return clock
    }

    @Test
    fun clockSubscribesToSignalsOnceInItsOwningScope() = runTest {
        val owner = ApplicationScope(dispatchers())

        clockIn(owner)

        assertEquals(1, signals.subscriptions)
        assertEquals(0, signals.cancellations)
        owner.close()
    }

    @Test
    fun closingTheOwnerUnsubscribesAndStopsTheMidnightTimer() = runTest {
        val owner = ApplicationScope(dispatchers())
        val clock = clockIn(owner)

        owner.close()
        runCurrent()
        advanceTimeBy(48 * 60 * 60_000L)
        runCurrent()

        assertEquals(1, signals.cancellations)
        assertEquals(LocalDate.of(2026, 10, 31), clock.today.value.date)
        assertEquals(0, testScheduler.currentTime - 48 * 60 * 60_000L)
    }

    @Test
    fun signalsAfterCloseAreIgnored() = runTest {
        val owner = ApplicationScope(dispatchers())
        val clock = clockIn(owner)
        owner.close()
        runCurrent()

        advanceTimeBy(60_000)
        signals.signal()
        runCurrent()

        assertEquals(LocalDate.of(2026, 10, 31), clock.today.value.date)
    }

    @Test
    fun closingPropagatesCancellationToOwnedWork() = runTest {
        val owner = ApplicationScope(dispatchers())
        var observed: Throwable? = null
        val job = owner.launch {
            try {
                awaitCancellation()
            } catch (cancellation: CancellationException) {
                observed = cancellation
                throw cancellation
            }
        }
        runCurrent()

        owner.close()
        runCurrent()

        assertTrue(observed is CancellationException)
        assertTrue(job.isCancelled)
        assertFalse(owner.isActive)
    }

    @Test
    fun oneFailingChildDoesNotCancelItsSiblings() = runTest {
        val failures = mutableListOf<Throwable>()
        val owner = ApplicationScope(dispatchers())
        val handler = kotlinx.coroutines.CoroutineExceptionHandler { _, failure ->
            failures +=
                failure
        }
        val sibling: Job = owner.launch { awaitCancellation() }
        owner.launch(handler) { error("boom") }
        runCurrent()

        assertEquals(listOf("boom"), failures.map { it.message })
        assertTrue(sibling.isActive)
        assertTrue(owner.isActive)
        owner.close()
    }

    @Test
    fun ownedWorkRunsOnTheInjectedDispatcher() = runTest {
        val owner = ApplicationScope(dispatchers())
        var ran = false

        owner.launch { ran = true }
        assertFalse(ran)
        runCurrent()

        assertTrue(ran)
        owner.cancel()
    }
}
