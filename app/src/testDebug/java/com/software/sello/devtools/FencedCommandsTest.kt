package com.software.sello.devtools

import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CreateCategory
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FencedCommandsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val operation = OperationId.of("00000000-0000-4000-8000-000000000001").value()
    private val command = CreateCategory(
        operation,
        1,
        CategoryName.of("Comida").value(),
        IconKey.of("home").value(),
        BudgetLimit.Unlimited
    )

    private fun committed(replayed: Boolean) = CommandOutcome.Committed(
        CommittedCategoryChange(
            OperationReceipt(operation, OperationKind.CreateCategory, 1, 1, Instant.EPOCH),
            CategoryId.of("00000000-0000-4000-8000-000000000002").value()
        ),
        replayed
    )

    private class Answering(var answer: CategoryCommandOutcome) : CategoryCommands {
        val gate = CompletableDeferred<Unit>().apply { complete(Unit) }
        var submitted = 0

        override suspend fun submit(command: CategoryCommand): CategoryCommandOutcome {
            submitted++
            gate.await()
            return answer
        }

        override suspend fun find(operationId: OperationId) = Outcome.Success(null)
    }

    private val fence = WriteFence()
    private var saved = 0

    @Test
    fun aNewChangeIsReportedAndItsOutcomeIsPassedOnUnchanged() = runTest {
        val outcome = committed(replayed = false)
        val commands = FencedCategoryCommands(Answering(outcome), fence) { saved++ }

        assertSame(outcome, commands.submit(command))
        assertEquals(1, saved)
    }

    @Test
    fun aReplayARejectionAndAnUnknownOutcomeAreNotNewChanges() = runTest {
        val inner = Answering(committed(replayed = true))
        val commands = FencedCategoryCommands(inner, fence) { saved++ }

        commands.submit(command)
        inner.answer = CommandOutcome.Rejected(Specific(CategoryRejection.NameTaken))
        commands.submit(command)
        inner.answer = CommandOutcome.OutcomeUnknown(operation)
        commands.submit(command)

        assertEquals(0, saved)
    }

    @Test
    fun aCommandAfterTheFenceClosedNeverReachesStorage() = runTest {
        val inner = Answering(committed(replayed = false))
        val commands = FencedCategoryCommands(inner, fence) { saved++ }
        fence.close()

        val late = launch { commands.submit(command) }
        runCurrent()

        assertEquals(0, inner.submitted)
        assertFalse(late.isCompleted)
        late.cancel()
    }
}
