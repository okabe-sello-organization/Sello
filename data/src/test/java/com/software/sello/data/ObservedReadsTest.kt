package com.software.sello.data

import com.software.sello.data.repository.observeOn
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ReadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A read kept up to date: what it says first, after each change, and when it fails. */
class ObservedReadsTest {
    private val damaged = StorageFailure.Integrity("goal", "a", "name")

    /** A storage failure as a feature wraps it, to tell the two apart. */
    private data class Wrapped(val failure: StorageFailure)

    @Test
    fun loadingComesFirstThenEachReadAndAFailureKeepsTheLastGoodValue() = runTest {
        val reads = ArrayDeque(
            listOf<Outcome<Int, StorageFailure>>(
                Outcome.Success(10),
                Outcome.Failure(damaged),
                Outcome.Success(30),
                Outcome.Failure(damaged)
            )
        )
        val changes = Channel<Int>()
        val states = Channel<ReadState<Int, StorageFailure>>(Channel.UNLIMITED)
        val collector = launch {
            observeOn(changes.consumeAsFlow(), { it }) { reads.removeFirst() }.collect(states::send)
        }

        val seen = mutableListOf(states.receive())
        repeat(4) {
            changes.send(it)
            seen += states.receive()
        }
        collector.cancel()

        assertEquals(
            listOf(
                ReadState.Loading,
                ReadState.Ready(10),
                ReadState.Failed(damaged, 10),
                ReadState.Ready(30),
                ReadState.Failed(damaged, 30)
            ),
            seen
        )
    }

    @Test
    fun aFailureBeforeAnyGoodReadHasNothingToKeepAndIsNeverAnEmptyValue() = runTest {
        val states = observeOn(flowOf(Unit), { it }) {
            Outcome.Failure<StorageFailure>(damaged)
        }.toList()

        assertEquals(listOf(ReadState.Loading, ReadState.Failed(damaged, null)), states)
    }

    @Test
    fun eachReadIsGivenTheChangeThatAskedForIt() = runTest {
        val states = observeOn(flowOf("2026-10", "2026-11"), { it }) { month ->
            Outcome.Success("read $month")
        }.toList()

        assertEquals(
            listOf(
                ReadState.Loading,
                ReadState.Ready("read 2026-10"),
                ReadState.Ready("read 2026-11")
            ),
            states
        )
    }

    @Test
    fun aDatabaseErrorEndsTheReadsAsAFailureThatKeepsTheLastGoodValue() = runTest {
        val changes = Channel<Unit>()
        val states = Channel<ReadState<Int, Wrapped>>(Channel.UNLIMITED)
        val collector = launch {
            observeOn(changes.consumeAsFlow(), ::Wrapped) { Outcome.Success(5) }
                .collect(states::send)
        }

        val loading = states.receive()
        changes.send(Unit)
        val ready = states.receive()
        changes.close(IllegalStateException("closed"))
        val failed = states.receive()
        collector.join()

        assertEquals(
            listOf(
                ReadState.Loading,
                ReadState.Ready(5),
                ReadState.Failed(Wrapped(StorageFailure.Unavailable("IllegalStateException")), 5)
            ),
            listOf(loading, ready, failed)
        )
    }

    @Test
    fun aCancelledCollectorGetsTheCancellationNotAFailure() = runTest {
        val seen = mutableListOf<ReadState<Int, StorageFailure>>()
        var cancelled: Throwable? = null
        val collector = launch {
            try {
                observeOn<Int, Int, StorageFailure>(flowOf(1), { it }) {
                    throw CancellationException("gone")
                }.collect { seen += it }
            } catch (thrown: CancellationException) {
                cancelled = thrown
                throw thrown
            }
        }
        collector.join()

        assertEquals(listOf(ReadState.Loading), seen)
        assertTrue("$cancelled", cancelled is CancellationException)
    }
}
