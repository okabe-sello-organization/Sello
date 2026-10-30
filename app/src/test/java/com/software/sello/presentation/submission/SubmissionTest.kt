package com.software.sello.presentation.submission

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.RecordIdSource
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubmissionTest {
    private val first = "00000000-0000-4000-8000-000000000001"
    private val second = "00000000-0000-4000-8000-000000000002"
    private val failure = StorageFailure.Unavailable("SQLiteException")

    private class Ids(vararg values: String) : RecordIdSource {
        private val left = ArrayDeque(values.toList())
        override fun next(): String = left.removeFirst()
    }

    /** A store that remembers what each operation sent and answers lookups from it. */
    private class Store {
        val sent = mutableListOf<OperationId>()
        val asked = mutableListOf<OperationId>()
        var committed = mutableMapOf<OperationId, String>()
        var lookupFails = false

        val find: Lookup<String> = { operation ->
            asked += operation
            if (lookupFails) {
                Outcome.Failure(StorageFailure.Unavailable("SQLiteException"))
            } else {
                Outcome.Success(committed[operation])
            }
        }
    }

    private fun submission(saved: SavedStateHandle = SavedStateHandle(), vararg ids: String) =
        Submission(saved, "form.operation", Ids(*ids.ifEmpty { arrayOf(first, second) }))

    private fun id(value: String) = (OperationId.of(value) as Outcome.Success).value

    @Test
    fun `the operation is saved before the command leaves and cleared once it commits`() = runTest {
        val saved = SavedStateHandle()
        val submission = submission(saved)
        val store = Store()

        val settled = submission.send(store.find, "create") { operation ->
            store.sent += operation
            assertEquals(first, saved.get<String>("form.operation"))
            assertEquals("create", submission.step)
            CommandOutcome.Committed("gasto", replayed = false)
        }

        assertEquals(Settled.Committed("gasto", found = false), settled)
        assertEquals(listOf(id(first)), store.sent)
        assertFalse(submission.pending)
        assertNull(submission.step)
        assertTrue(store.asked.isEmpty())
    }

    @Test
    fun `a second submit while the first is in flight sends nothing`() = runTest {
        val submission = submission()
        val store = Store()
        val answer = CompletableDeferred<CommandOutcome<String, String>>()
        val command: suspend (OperationId) -> CommandOutcome<String, String> = {
            store.sent += it
            answer.await()
        }

        val sending = async { submission.send(store.find, "create", command) }
        runCurrent()
        val again = submission.send(store.find, "create", command)
        val asked = submission.recover(store.find)
        answer.complete(CommandOutcome.Committed("gasto", replayed = false))

        assertNull(again)
        assertNull(asked)
        assertEquals(Settled.Committed("gasto", found = false), sending.await())
        assertEquals(listOf(id(first)), store.sent)
        assertTrue(store.asked.isEmpty())
    }

    @Test
    fun `a refusal is certain, so the attempt is over`() = runTest {
        val submission = submission()
        val store = Store()

        val settled = submission.send(store.find, "create") {
            CommandOutcome.Rejected(CommandRejection.Specific("FutureDate"))
        }

        assertEquals(Settled.Rejected(CommandRejection.Specific("FutureDate")), settled)
        assertFalse(submission.pending)
        assertTrue(store.asked.isEmpty())
    }

    @Test
    fun `an unknown outcome is asked about with the same ID and found committed`() = runTest {
        val submission = submission()
        val store = Store()

        val settled = submission.send(store.find, "create") { operation ->
            store.sent += operation
            store.committed[operation] = "gasto"
            CommandOutcome.OutcomeUnknown(operation)
        }

        assertEquals(Settled.Committed("gasto", found = true), settled)
        assertEquals(listOf(id(first)), store.sent)
        assertEquals(listOf(id(first)), store.asked)
        assertFalse(submission.pending)
    }

    @Test
    fun `an unknown outcome found not committed is over without sending again`() = runTest {
        val submission = submission()
        val store = Store()

        val settled = submission.send(store.find, "create") { operation ->
            store.sent += operation
            CommandOutcome.OutcomeUnknown(operation)
        }

        assertEquals(Settled.NotCommitted, settled)
        assertEquals(listOf(id(first)), store.sent)
        assertEquals(listOf(id(first)), store.asked)
        assertFalse(submission.pending)
    }

    @Test
    fun `a lookup that fails keeps the operation and the next ask uses the same ID`() = runTest {
        val submission = submission()
        val store = Store().apply { lookupFails = true }

        val settled = submission.send(store.find, "delete") { operation ->
            store.sent += operation
            store.committed[operation] = "borrado"
            CommandOutcome.OutcomeUnknown(operation)
        }
        assertEquals(Settled.Unknown(failure), settled)
        assertTrue(submission.pending)
        assertEquals("delete", submission.step)

        store.lookupFails = false
        val retried = submission.recover(store.find)

        assertEquals(Settled.Committed("borrado", found = true), retried)
        assertEquals(listOf(id(first)), store.sent)
        assertEquals(listOf(id(first), id(first)), store.asked)
        assertFalse(submission.pending)
    }

    @Test
    fun `after a restart the saved operation is looked up, never sent again`() = runTest {
        val saved = SavedStateHandle()
        val store = Store()
        val before = submission(saved)
        val gone = launch {
            before.send(store.find, "create") { operation ->
                store.sent += operation
                CompletableDeferred<CommandOutcome<String, String>>().await()
            }
        }
        runCurrent()
        gone.cancel()
        store.committed[id(first)] = "gasto"

        // Process death: only what the handle saved survives.
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val after = submission(restored, second)
        assertTrue(after.pending)
        assertEquals("create", after.step)

        val settled = after.recover(store.find)

        assertEquals(Settled.Committed("gasto", found = true), settled)
        assertEquals(listOf(id(first)), store.sent)
        assertEquals(listOf(id(first)), store.asked)
        assertFalse(after.pending)
    }

    @Test
    fun `nothing saved means nothing to ask about`() = runTest {
        val store = Store()

        assertNull(submission().recover(store.find))
        assertTrue(store.asked.isEmpty())
    }

    @Test
    fun `cancellation is rethrown and leaves the operation to be asked about`() = runTest {
        val submission = submission()
        val store = Store()

        try {
            submission.send<String, String>(store.find, "create") {
                throw CancellationException("left the screen")
            }
            fail("cancellation was swallowed")
        } catch (expected: CancellationException) {
            assertEquals("left the screen", expected.message)
        }

        assertTrue(submission.pending)
        assertTrue(store.asked.isEmpty())
        // No longer in flight: the next ask goes out, with the same ID.
        assertEquals(Settled.NotCommitted, submission.recover(store.find))
        assertEquals(listOf(id(first)), store.asked)
    }

    @Test
    fun `a cancelled lookup is rethrown and the operation stays saved`() = runTest {
        val saved = SavedStateHandle(mapOf("form.operation" to first))
        val submission = submission(saved)

        try {
            submission.recover<String> { throw CancellationException("left the screen") }
            fail("cancellation was swallowed")
        } catch (_: CancellationException) {
        }

        assertTrue(submission.pending)
        assertEquals(first, saved.get<String>("form.operation"))
    }

    @Test
    fun `a send while an operation is unresolved sends nothing and keeps its ID`() = runTest {
        val saved = SavedStateHandle(
            mapOf(
                "form.operation" to first,
                "form.operation.step" to "create"
            )
        )
        val submission = submission(saved, second)
        val store = Store()

        val settled = submission.send(store.find, "create") { operation ->
            store.sent += operation
            CommandOutcome.Committed("gasto", replayed = false)
        }

        assertNull(settled)
        assertTrue(store.sent.isEmpty())
        assertTrue(store.asked.isEmpty())
        assertEquals(first, saved.get<String>("form.operation"))
        assertEquals("create", submission.step)
    }

    @Test
    fun `an unreadable saved ID is cleared without asking storage`() = runTest {
        val saved = SavedStateHandle(mapOf("form.operation" to "not-an-id"))
        val submission = submission(saved)
        val store = Store()

        assertNull(submission.recover(store.find))

        assertTrue(store.asked.isEmpty())
        assertFalse(submission.pending)
        assertNull(saved.get<String>("form.operation"))
    }
}
