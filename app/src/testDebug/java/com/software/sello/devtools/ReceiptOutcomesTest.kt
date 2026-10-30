package com.software.sello.devtools

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateExpenses
import com.software.sello.domain.port.ExpenseBatchRejection
import com.software.sello.feature.bulk.HandExpenseBatchCommands
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptOutcomesTest {
    private val operation =
        (OperationId.of("00000000-0000-4000-8000-000000000001") as Outcome.Success).value
    private val confirmation = CorrectionConfirmation(41, "batch-1")
    private val receipt = CreateExpenses(operation, 7, LocalDate.parse("2026-09-30"), emptyList())

    private val inner = HandExpenseBatchCommands()
    private val outcomes = ReceiptOutcomes()
    private var saved = 0
    private val commands = FencedExpenseBatchCommands(inner, WriteFence(), { saved++ }, outcomes)

    @Test
    fun aReceiptThatFirstAsksToConfirmLaterMonthsIsStillReportedUnknownOnceSaved() = runTest {
        inner.answer = {
            CommandOutcome.Rejected(
                CommandRejection.Specific(
                    ExpenseBatchRejection.CorrectionUnconfirmed(
                        confirmation,
                        CarryCorrection(emptyList(), emptyList(), emptyList(), null, emptyMap())
                    )
                )
            )
        }
        outcomes.arm()

        assertTrue(commands.create(receipt) is CommandOutcome.Rejected)
        assertTrue(outcomes.armed.value)

        inner.answer = { CommandOutcome.Committed(inner.committed(it), replayed = false) }
        val confirmed = commands.create(receipt.copy(confirmation = confirmation))

        assertEquals(CommandOutcome.OutcomeUnknown(operation), confirmed)
        assertFalse(outcomes.armed.value)
        assertEquals(1, saved)
        // The first question fails, as if storage stopped answering; the next one finds it.
        val stored = inner.committed(operation, 0)
        inner.found = { Outcome.Success(stored) }
        assertTrue(commands.find(operation) is Outcome.Failure)
        assertEquals(Outcome.Success(stored), commands.find(operation))
    }
}
