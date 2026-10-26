package com.software.sello.devtools

import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.SetUsdRate
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Investment and rate writes in the debug build go through the same fence, faults and
 * log as every other financial write, the inspector finds their receipts, and the
 * sandbox's read failure reaches the investment board.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentSandboxPortsTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** Every other kind's receipts: none, so a lookup there finds nothing. */
    private val ledger = Ledger()

    /** The investment receipts. */
    private val own = Ledger()
    private val fence = WriteFence()
    private val faults = DebugFaults()
    private val log = OperationLog()
    private var saved = 0

    private fun rate(n: Int) = SetUsdRate(
        OperationId.of("00000000-0000-4000-8000-00000000000$n").value(),
        1,
        UsdRate.ofHundredths(415_025).value(),
        LocalDate.of(2026, 10, 20)
    )

    private fun inspector(investments: LedgerInvestments) = OperationInspector(
        LedgerExpenses(ledger),
        LedgerIncomes(ledger),
        LedgerCategories(ledger),
        LedgerGoals(ledger),
        investments,
        LedgerSchedules(ledger)
    )

    @Test
    fun aNewInvestmentChangeIsReportedAndAReplayOrARejectionIsNot() = runTest {
        val commands = FencedInvestmentCommands(LedgerInvestments(own), fence) { saved++ }

        val first = commands.submit(rate(1))
        val replay = commands.submit(rate(1))
        own.answer = Ledger.Next.Refuse
        commands.submit(rate(2))

        assertFalse((first as InvestmentCommandOutcome.Committed).replayed)
        assertTrue((replay as InvestmentCommandOutcome.Committed).replayed)
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

    @Test
    fun aLostInvestmentAnswerCommitsOnceIsLoggedAndItsReceiptIsFound() = runTest {
        val investments = LedgerInvestments(own)
        val commands = FaultyInvestmentCommands(investments, faults, log)
        faults.arm(Fault.LostAnswer)
        val command = rate(4)

        val told = commands.submit(command)

        assertEquals(InvestmentCommandOutcome.OutcomeUnknown(command.operationId), told)
        assertEquals(1, own.writes.size)
        val logged = log.state.value.single()
        assertEquals(
            Triple("Inversión: SetUsdRate", "Desconocido", Fault.LostAnswer),
            Triple(logged.kind, logged.told, logged.fault)
        )
        // Found through the investment receipts, not another kind's.
        assertEquals(StoredOutcome.Saved(1), inspector(investments).stored(logged))
    }

    @Test
    fun anInvestmentAnswerIsLoggedAsItsCallerWasTold() = runTest {
        val investments = LedgerInvestments(own)
        val commands = FaultyInvestmentCommands(investments, faults, log)

        commands.submit(rate(5))
        commands.submit(rate(5))
        investments.refuseNext = InvestmentRejection.RateRequired
        commands.submit(rate(6))

        assertEquals(
            listOf("Guardado", "Ya estaba guardado", "Rechazado: RateRequired"),
            log.state.value.map { it.told }
        )
        assertEquals(
            listOf(StoredOutcome.Saved(1), StoredOutcome.Saved(1), StoredOutcome.Nothing),
            log.state.value.map { inspector(investments).stored(it) }
        )
    }

    @Test
    fun aFailureBeforeStorageWritesNoInvestmentChange() = runTest {
        faults.arm(Fault.FailBeforeStorage)

        val told = FaultyInvestmentCommands(LedgerInvestments(own), faults, log).submit(rate(7))

        assertTrue(told is InvestmentCommandOutcome.OutcomeUnknown)
        assertTrue(own.writes.isEmpty())
    }

    private val good = InvestmentBoard(
        1,
        4,
        emptyList(),
        UsdRateBook(null, emptyList()),
        Outcome.Success(PortfolioOverview(emptyList(), null, null))
    )

    private class Upstream(val state: MutableStateFlow<InvestmentBoardState>) : InvestmentReads {
        override suspend fun board() = error("unused")

        override fun observeBoard() = state

        override suspend fun investment(id: InvestmentId) = error("unused")
    }

    @Test
    fun anInvestmentReadFailureKeepsTheLastGoodBoardAndARetryReadsAgain() = runTest {
        val reads = FaultyInvestmentReads(
            Upstream(MutableStateFlow(InvestmentBoardState.Ready(good))),
            faults
        )
        val seen = mutableListOf<InvestmentBoardState>()
        val reading = launch { reads.observeBoard().toList(seen) }
        runCurrent()

        faults.arm(Fault.ReadFailure)
        runCurrent()
        reading.cancel()

        val failed = seen.last() as InvestmentBoardState.Failed
        assertEquals(SimulatedStorageFailure, failed.failure)
        assertSame(good, failed.lastGood)
        assertEquals(InvestmentBoardState.Ready(good), reads.observeBoard().first())
    }
}
