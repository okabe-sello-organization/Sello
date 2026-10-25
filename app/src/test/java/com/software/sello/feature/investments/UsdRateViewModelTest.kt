package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SetUsdRate
import com.software.sello.navigation.HandFinancialClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Dólar and Cambiar la tasa (investment rule 11, IN30–IN32). */
@OptIn(ExperimentalCoroutinesApi::class)
class UsdRateViewModelTest {
    private val clock = HandFinancialClock("2026-10-25")
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val reads = HandInvestmentReads()
    private val commands = HandInvestmentCommands()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun entry(saved: SavedStateHandle = SavedStateHandle()) =
        UsdRateEntryViewModel(saved, reads, commands, clock, ids, testMoney())

    private fun UsdRateEntryViewModel.type(keys: String) = keys.forEach { key ->
        onAction(
            if (key == ',') UsdRateEntryAction.Comma else UsdRateEntryAction.Digit(key.digitToInt())
        )
    }

    private fun committed() = InvestmentCommandOutcome.Committed(
        CommittedInvestment(
            OperationReceipt(
                (OperationId.of("00000000-0000-4000-a000-000000000001") as Outcome.Success).value,
                OperationKind.SetUsdRate,
                1,
                2,
                Instant.EPOCH
            ),
            null as InvestmentId?
        ),
        replayed = false
    )

    @Test
    fun dolarShowsTheCurrentRateThenTheEarlierOnesNewestFirst() = runTest {
        reads.board.value = InvestmentBoardState.Ready(
            ratesBoard(
                rateEntry(396_050, "2026-09-15", 1),
                rateEntry(415_025, "2026-10-20", 3),
                rateEntry(401_000, "2026-10-02", 2)
            )
        )
        val model = UsdRateViewModel(reads, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()

        val ready = model.state.value as UsdRateState.Ready
        assertEquals(RateModel("4.150,25", LocalDate.of(2026, 10, 20)), ready.current)
        assertEquals(
            listOf(
                RateModel("4.010,00", LocalDate.of(2026, 10, 2)),
                RateModel("3.960,50", LocalDate.of(2026, 9, 15))
            ),
            ready.history
        )
    }

    @Test
    fun withNoRateDolarSaysSoAndAFailedReadIsNeverNoRate() = runTest {
        val model = UsdRateViewModel(reads, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        assertNull((model.state.value as UsdRateState.Ready).current)

        reads.board.value = InvestmentBoardState.Failed(StorageFailure.Unavailable("x"), null)
        runCurrent()
        assertEquals(UsdRateState.Failed, model.state.value)
    }

    @Test
    fun aTypedRateIsSentWithItsDateAndTheScreenCloses() = runTest {
        reads.board.value = InvestmentBoardState.Ready(ratesBoard(rateEntry(415_025, "2026-10-20")))
        commands.answers += committed()
        val model = entry()
        model.type("4180,7")
        runCurrent()

        assertEquals("4.180,7", model.state.value.rate)
        assertEquals(RateModel("4.150,25", LocalDate.of(2026, 10, 20)), model.state.value.current)
        model.onAction(UsdRateEntryAction.DatePicked(LocalDate.of(2026, 10, 24)))
        model.onAction(UsdRateEntryAction.Submit)
        runCurrent()

        val sent = commands.sent.single() as SetUsdRate
        assertEquals(418_070L, sent.rate.hundredths)
        assertEquals(LocalDate.of(2026, 10, 24), sent.date)
        assertTrue(model.state.value.closed)
    }

    @Test
    fun ratesOutsideTheBoundsWaitAndAThirdDecimalIsRefused() = runTest {
        val low = entry()
        low.type("0,99")
        runCurrent()
        assertEquals(RateProblem.BelowMinimum, low.state.value.problem)
        assertFalse(low.state.value.canSubmit)

        val high = entry()
        high.type("100000,01")
        runCurrent()
        assertEquals(RateProblem.AboveMaximum, high.state.value.problem)
        assertFalse(high.state.value.canSubmit)

        val precise = entry()
        precise.type("4150,255")
        runCurrent()
        assertEquals("4.150,25", precise.state.value.rate)
        assertTrue(precise.state.value.atLimit)

        val bounds = entry()
        bounds.type("100000")
        runCurrent()
        assertTrue(bounds.state.value.canSubmit)
    }

    @Test
    fun aDateAfterTodayIsNotTaken() = runTest {
        val model = entry()
        model.onAction(UsdRateEntryAction.DatePicked(LocalDate.of(2026, 10, 26)))
        runCurrent()
        assertEquals(LocalDate.of(2026, 10, 25), model.state.value.date)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithItsOwnIdentifierBeforeAnythingElse() = runTest {
        commands.answers += InvestmentCommandOutcome.OutcomeUnknown(
            (OperationId.of("00000000-0000-4000-a000-000000000001") as Outcome.Success).value
        )
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val model = entry()
        model.type("4000")
        model.onAction(UsdRateEntryAction.Submit)
        runCurrent()

        assertEquals(RateEntryError.Unknown, model.state.value.error)
        assertEquals(
            listOf("00000000-0000-4000-a000-000000000001"),
            commands.asked.map {
                it.value
            }
        )
        // Back waits while the outcome is not known; asking again finds it saved.
        model.onAction(UsdRateEntryAction.Back)
        assertFalse(model.state.value.closed)
        commands.found = Outcome.Success(committed().change)
        model.onAction(UsdRateEntryAction.Retry)
        runCurrent()
        assertTrue(model.state.value.closed)
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun aRestoredScreenKeepsTheDraftAndAsksAboutTheSaveInFlight() = runTest {
        val saved = SavedStateHandle()
        commands.answers += InvestmentCommandOutcome.OutcomeUnknown(
            (OperationId.of("00000000-0000-4000-a000-000000000001") as Outcome.Success).value
        )
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val first = entry(saved)
        first.type("4150,2")
        first.onAction(UsdRateEntryAction.Submit)
        runCurrent()

        commands.found = Outcome.Success(null)
        val restored = entry(saved)
        runCurrent()
        assertEquals("4.150,2", restored.state.value.rate)
        assertEquals(RateEntryError.NotSaved, restored.state.value.error)
        assertEquals(2, commands.asked.size)
        assertEquals(1, commands.sent.size)
    }

    @Test
    fun aCertainRefusalSaysWhyAndKeepsTheDraft() = runTest {
        commands.answers +=
            InvestmentCommandOutcome.Rejected(InvestmentRejection.StaleGeneration(2))
        val model = entry()
        model.type("4000")
        model.onAction(UsdRateEntryAction.Submit)
        runCurrent()
        assertEquals(RateEntryError.DataChanged, model.state.value.error)
        assertEquals("4.000", model.state.value.rate)
        assertFalse(model.state.value.closed)
    }
}
