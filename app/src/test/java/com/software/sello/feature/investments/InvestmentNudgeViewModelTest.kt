package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.HandGuidance
import com.software.sello.testing.testMoney
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Investment detail's nudge: once, the first time a page opens (SELLO-213). */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentNudgeViewModelTest {
    private val clock = HandFinancialClock("2026-11-08")
    private val id = "00000000-0000-4000-8000-000000000001"
    private val investment = handInvestment(
        id,
        "Fondo",
        null,
        "home",
        listOf(Triple(Contribution, 1_000_000L, "2026-11-01"))
    )
    private val reads = HandInvestmentReads(ReadState.Ready(investmentsBoard(listOf(investment))))
    private val guidance = HandGuidance()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun TestScope.opened(kept: HandGuidance = guidance): InvestmentDetailViewModel {
        val model = InvestmentDetailViewModel(
            SavedStateHandle(mapOf(InvestmentDetailViewModel.KEY_INVESTMENT to id)),
            reads,
            clock,
            testMoney(),
            kept
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        return model
    }

    private fun InvestmentDetailViewModel.nudge(): Boolean =
        (state.value as? InvestmentDetailState.Ready)?.nudge ?: false

    @Test
    fun theFirstPageOpenedShowsItAndKeepsTheDay() = runTest {
        assertTrue(opened().nudge())
        assertEquals(listOf("shown Investments 2026-11-08"), guidance.writes)
    }

    @Test
    fun itShowsAllThatDayOnceAndPastMidnightCountsAsSeen() = runTest {
        val model = opened()
        val again = opened().nudge()

        clock.goTo("2026-11-09")

        assertTrue(again)
        assertFalse(model.nudge())
        assertFalse(opened().nudge())
        assertEquals(listOf("shown Investments 2026-11-08"), guidance.writes)
    }

    @Test
    fun itsCrossClosesItForGood() = runTest {
        val model = opened()

        model.onAction(InvestmentDetailAction.CloseNudge)

        assertFalse(model.nudge())
        assertTrue(Nudge.Investments in guidance.stored.memory.seen)
        assertFalse(opened().nudge())
    }

    @Test
    fun aCloseThatCannotBeKeptStillHidesItHere() = runTest {
        val model = opened()
        guidance.failWrites = true

        model.onAction(InvestmentDetailAction.CloseNudge)

        assertFalse(model.nudge())
        assertFalse(Nudge.Investments in guidance.stored.memory.seen)
    }

    @Test
    fun anotherNudgeShownTodayLeavesItForAnotherDay() = runTest {
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 8))))
        val today = opened().nudge()

        clock.goTo("2026-11-09")

        assertFalse(today)
        assertTrue(opened().nudge())
    }

    @Test
    fun peopleAlreadyUsingSelloNeverSeeIt() = runTest {
        val existing = HandGuidance(Guidance(existingUseChecked = false))
        existing.rememberExistingUse(wasUsingSello = true)

        assertFalse(opened(existing).nudge())
    }

    @Test
    fun itWaitsUntilExistingUseIsChecked() = runTest {
        val unchecked = HandGuidance(Guidance(existingUseChecked = false))
        val model = opened(unchecked)
        val waiting = model.nudge()

        unchecked.rememberExistingUse(wasUsingSello = false)

        assertFalse(waiting)
        assertTrue(model.nudge())
    }

    @Test
    fun aFailedGuidanceReadShowsNoNudge() = runTest {
        guidance.failReads()

        assertFalse(opened().nudge())
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun overOlderFiguresItDoesNotShowAndTheDayIsNotSpent() = runTest {
        val good = reads.board.value
        val unchecked = HandGuidance(Guidance(existingUseChecked = false))
        val model = opened(unchecked)
        reads.board.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)

        unchecked.rememberExistingUse(wasUsingSello = false)
        val older = model.state.value as InvestmentDetailState.Ready
        reads.board.value = good

        assertTrue(older.stale)
        assertFalse(older.nudge)
        assertTrue(model.nudge())
        assertEquals(listOf("existing", "shown Investments 2026-11-08"), unchecked.writes)
    }
}
