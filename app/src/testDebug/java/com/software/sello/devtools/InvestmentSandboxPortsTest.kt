package com.software.sello.devtools

import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.SetUsdRate
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
 * Investment and rate writes in the debug build go through the same fence as every other
 * financial write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentSandboxPortsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** The investment receipts. */
    private val own = Ledger()
    private val fence = WriteFence()
    private var saved = 0

    private fun rate(n: Int) = SetUsdRate(
        OperationId.of("00000000-0000-4000-8000-00000000000$n").value(),
        1,
        UsdRate.ofHundredths(415_025).value(),
        LocalDate.of(2026, 10, 20)
    )

    @Test
    fun aNewInvestmentChangeIsReportedAndAReplayOrARejectionIsNot() = runTest {
        val commands = FencedInvestmentCommands(LedgerInvestments(own), fence) { saved++ }

        val first = commands.submit(rate(1))
        val replay = commands.submit(rate(1))
        own.answer = Ledger.Next.Refuse
        commands.submit(rate(2))

        assertFalse((first as CommandOutcome.Committed).replayed)
        assertTrue((replay as CommandOutcome.Committed).replayed)
        assertEquals(1, saved)
    }

    @Test
    fun anInvestmentCommandAfterTheFenceClosedNeverReachesStorage() = runTest {
        val investments = LedgerInvestments(own)
        val commands = FencedInvestmentCommands(investments, fence) { saved++ }
        fence.close()

        val late = launch { commands.submit(rate(3)) }
        runCurrent()

        assertTrue(investments.sent.isEmpty())
        assertFalse(late.isCompleted)
        late.cancel()
    }
}
