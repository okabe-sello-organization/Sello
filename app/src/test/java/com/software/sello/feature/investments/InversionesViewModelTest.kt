package com.software.sello.feature.investments

import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.testMoney
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
        val model = InversionesViewModel(reads, preferences, clock, testMoney())
        backgroundScope.launch { model.state.collect {} }
        runCurrent()
        // Mayor valor, the default.
        assertEquals(listOf("Zeta", "Alfa"), model.names())

        preferences.setInvestmentOrder(InvestmentOrder.Alphabetical)
        runCurrent()

        assertEquals(listOf("Alfa", "Zeta"), model.names())
    }
}
