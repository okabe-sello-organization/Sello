package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.testMoney
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Inversiones follows the order saved in Configuración as it changes. */
@OptIn(ExperimentalCoroutinesApi::class)
class InversionesViewModelTest {
    private val clock = HandFinancialClock("2026-10-25")
    private val preferences = HandInvestmentPreferences()
    private val investments = listOf(
        handInvestment(
            "00000000-0000-4000-8000-000000000001",
            "Zeta",
            null,
            "home",
            listOf(
                Triple(Contribution, 5_000_000L, "2026-10-01"),
                Triple(Valuation, 5_000_000L, "2026-10-01")
            )
        ),
        handInvestment(
            "00000000-0000-4000-8000-000000000002",
            "Alfa",
            null,
            "home",
            listOf(
                Triple(Contribution, 1_000_000L, "2026-10-01"),
                Triple(Valuation, 1_000_000L, "2026-10-01")
            )
        )
    )

    // The overview worked out as the board works it out.
    private val reads = HandInvestmentReads(
        ReadState.Ready(
            investmentsBoard(investments).copy(
                overview = InvestmentPolicy.overview(
                    investments.map { it.history to it.figures },
                    null
                )
            )
        )
    )

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun InversionesViewModel.names() =
        (state.value as InversionesState.Ready).rows.map { it.name }

    @Test
    fun theListFollowsTheOrderChosenInConfiguracion() = runTest {
        val model = InversionesViewModel(SavedStateHandle(), reads, preferences, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        // Mayor valor, the default.
        assertEquals(listOf("Zeta", "Alfa"), model.names())

        preferences.setInvestmentOrder(InvestmentOrder.Alphabetical)
        runCurrent()

        assertEquals(listOf("Alfa", "Zeta"), model.names())
    }

    private val failure = ReadState.Failed(StorageFailure.Unavailable("SQLiteException"), null)

    /** The same board, every observation of it new, as the real one is. */
    private fun fresh() = HandInvestmentReads(reads.board.value, newObservations = true)

    @Test
    fun retryAndComingBackKeepInversionesFiguresAndAFailureAgainLeavesThemOlder() = runTest {
        val fresh = fresh()
        val good = fresh.board.value
        val model = InversionesViewModel(SavedStateHandle(), fresh, preferences, clock, testMoney())
        val first = backgroundScope.launch { model.state.collect {} }
        runCurrent()
        fresh.board.value = failure
        runCurrent()
        assertEquals(true, (model.state.value as InversionesState.Ready).stale)
        val seen = mutableListOf<InversionesState>()
        val second = backgroundScope.launch { model.state.collect { seen += it } }
        runCurrent()

        model.onAction(InversionesAction.Retry)
        runCurrent()
        assertTrue(seen.all { it is InversionesState.Ready && it.stale })

        fresh.board.value = good
        runCurrent()
        first.cancel()
        second.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        seen.clear()
        backgroundScope.launch { model.state.collect { seen += it } }
        runCurrent()

        assertEquals(3, fresh.started)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it is InversionesState.Ready && !it.stale })
        assertEquals(listOf("Zeta", "Alfa"), model.names())
    }

    @Test
    fun retryAndComingBackKeepTheInvestmentPageAndAFailureAgainLeavesItOlder() = runTest {
        val fresh = fresh()
        val good = fresh.board.value
        val zeta = investments[0].history.investment.id.value
        val model = InvestmentDetailViewModel(
            SavedStateHandle(mapOf(InvestmentDetailViewModel.KEY_INVESTMENT to zeta)),
            fresh,
            clock,
            testMoney()
        )
        val first = backgroundScope.launch { model.state.collect {} }
        runCurrent()
        fresh.board.value = failure
        runCurrent()
        assertEquals(true, (model.state.value as InvestmentDetailState.Ready).stale)
        val seen = mutableListOf<InvestmentDetailState>()
        val second = backgroundScope.launch { model.state.collect { seen += it } }
        runCurrent()

        model.onAction(InvestmentDetailAction.Retry)
        runCurrent()
        assertTrue(seen.all { it is InvestmentDetailState.Ready && it.stale })

        fresh.board.value = good
        runCurrent()
        first.cancel()
        second.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        seen.clear()
        backgroundScope.launch { model.state.collect { seen += it } }
        runCurrent()

        assertEquals(3, fresh.started)
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it is InvestmentDetailState.Ready && !it.stale })
    }

    @Test
    fun eachInversionesHelpSheetOpensClosesAndComesBackAfterARestart() = runTest {
        val saved = SavedStateHandle()
        val model = InversionesViewModel(saved, reads, preferences, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        fun help(of: InversionesViewModel) = (of.state.value as InversionesState.Ready).help
        assertEquals(null, help(model))

        for (sheet in listOf(InvestmentHelp.Value, InvestmentHelp.Gain, InvestmentHelp.Rate)) {
            model.onAction(InversionesAction.ShowHelp(sheet))
            runCurrent()
            assertEquals(sheet, help(model))
        }

        // A restart keeps the saved state, and with it the open sheet.
        val restored = InversionesViewModel(
            SavedStateHandle(saved.keys().associateWith { saved[it] }),
            reads,
            preferences,
            clock,
            testMoney()
        )
        backgroundScope.launch { restored.state.collect {} }
        runCurrent()
        assertEquals(InvestmentHelp.Rate, help(restored))

        model.onAction(InversionesAction.CloseHelp)
        runCurrent()
        assertEquals(null, help(model))

        // Ver más en la Guía closes it: coming back finds Inversiones.
        model.onAction(InversionesAction.ShowHelp(InvestmentHelp.Value))
        model.onAction(InversionesAction.OpenGuide)
        runCurrent()
        assertEquals(null, help(model))
    }

    @Test
    fun eachInvestmentPageHelpSheetOpensClosesAndComesBackAfterARestart() = runTest {
        val zeta = investments[0].history.investment.id.value
        val saved = SavedStateHandle(mapOf(InvestmentDetailViewModel.KEY_INVESTMENT to zeta))
        val model = InvestmentDetailViewModel(saved, reads, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        fun help(of: InvestmentDetailViewModel) =
            (of.state.value as InvestmentDetailState.Ready).help
        assertEquals(null, help(model))

        model.onAction(InvestmentDetailAction.ShowHelp(InvestmentHelp.Gain))
        runCurrent()
        assertEquals(InvestmentHelp.Gain, help(model))
        model.onAction(InvestmentDetailAction.ShowHelp(InvestmentHelp.Chart))
        runCurrent()
        assertEquals(InvestmentHelp.Chart, help(model))

        val restored = InvestmentDetailViewModel(
            SavedStateHandle(saved.keys().associateWith { saved[it] }),
            reads,
            clock,
            testMoney()
        )
        backgroundScope.launch { restored.state.collect {} }
        runCurrent()
        assertEquals(InvestmentHelp.Chart, help(restored))

        model.onAction(InvestmentDetailAction.CloseHelp)
        runCurrent()
        assertEquals(null, help(model))

        model.onAction(InvestmentDetailAction.ShowHelp(InvestmentHelp.Gain))
        model.onAction(InvestmentDetailAction.OpenGuide)
        runCurrent()
        assertEquals(null, help(model))
    }
}
