package com.software.sello.feature.recibo

import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.ClosedMonthInvitePreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** "Ver cómo cerraste …": the first week of a month, on the financial day. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboInvitationViewModelTest {
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val clock = HandFinancialClock("2026-10-02")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val invitations = HandInvitations()

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    /** A new view model each time it is called: the same preferences, a restarted app. */
    private fun TestScope.started(): ReciboViewModel {
        val viewModel = ReciboViewModel(
            session,
            snapshots,
            HandOrderings(),
            testMoney(),
            invitations,
            clock,
            HandCategoryReads()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        return viewModel
    }

    private fun snapshot(month: YearMonth, spent: Long) = MonthlySnapshot(
        month = month,
        asOf = LocalDate.of(2026, 11, 2),
        zone = ZoneId.of("America/Bogota"),
        generation = 1,
        revision = 1,
        categories = emptyList(),
        spent = Money.cop(spent),
        overall = OverallBudget.NoLimit,
        futureDatedExpenses = 0
    )

    /** A month with one category, so that Recibo shows it rather than the first run. */
    private fun publish(month: YearMonth, spent: Long) {
        val category = CategoryMonth(
            Category(
                (CategoryId.of("00000000-0000-4000-8000-000000000001") as Outcome.Success).value,
                (CategoryName.of("Mercado") as Outcome.Success).value,
                (IconKey.of("home") as Outcome.Success).value,
                archived = false,
                version = 1,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            ),
            MonthBudgetState.Unconfigured,
            Money.cop(0),
            budget = null,
            expenseCount = 0
        )
        snapshots.ready(snapshot(month, spent).copy(categories = listOf(category)))
    }

    private fun invitationOf(viewModel: ReciboViewModel): ReciboInvitation? =
        (viewModel.state.value as? ReciboState.Month)?.invitation

    @Test
    fun onDayOneThePreviousMonthIsOffered() = runTest {
        clock.goTo("2026-10-01")
        publish(october, 100)
        publish(september, 920_000)
        val viewModel = started()

        assertEquals(ReciboInvitation(september), invitationOf(viewModel))
    }

    @Test
    fun onDaySevenItIsStillOffered() = runTest {
        clock.goTo("2026-10-07")
        publish(october, 100)
        publish(september, 920_000)

        assertEquals(ReciboInvitation(september), invitationOf(started()))
    }

    @Test
    fun onDayEightItIsGoneAndNothingIsReadForIt() = runTest {
        clock.goTo("2026-10-08")
        publish(october, 100)
        publish(september, 920_000)

        assertNull(invitationOf(started()))
        assertEquals(listOf(october), snapshots.observed)
    }

    @Test
    fun theDayRollingOverFromSevenToEightRemovesIt() = runTest {
        clock.goTo("2026-10-07")
        publish(october, 100)
        publish(september, 920_000)
        val viewModel = started()
        assertEquals(ReciboInvitation(september), invitationOf(viewModel))

        clock.goTo("2026-10-08")

        assertNull(invitationOf(viewModel))
    }

    @Test
    fun aPastMonthShownOnDayTwoHasNoInvitation() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        publish(YearMonth.of(2026, 8), 5_000)
        val viewModel = started()
        session.select(september)

        assertNull(invitationOf(viewModel))
    }

    @Test
    fun aPreviousMonthWithoutExpensesIsNotOffered() = runTest {
        publish(october, 100)
        publish(september, 0)

        assertNull(invitationOf(started()))
    }

    @Test
    fun aFailedReadOfThePreviousMonthHidesItEvenWithAnEarlierRead() = runTest {
        publish(october, 100)
        snapshots.of(september).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException")),
            lastGood = snapshot(september, 920_000)
        )

        val viewModel = started()

        assertNull(invitationOf(viewModel))
        // The month itself is still shown.
        assertEquals(true, viewModel.state.value is ReciboState.Month)
    }

    @Test
    fun theInvitationAppearsWhenThePreviousMonthFinishesLoading() = runTest {
        publish(october, 100)
        val viewModel = started()
        assertNull(invitationOf(viewModel))

        publish(september, 920_000)

        assertEquals(ReciboInvitation(september), invitationOf(viewModel))
    }

    @Test
    fun openingItRemembersTheMonthAndItStaysGoneAfterARestart() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        val viewModel = started()

        viewModel.onAction(ReciboAction.OpenInvitation(september))

        assertNull(invitationOf(viewModel))
        assertEquals(listOf(september), invitations.written)
        assertNull(invitationOf(started()))
    }

    @Test
    fun dismissingItRemembersTheMonthAndItStaysGoneAfterARestart() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        val viewModel = started()

        viewModel.onAction(ReciboAction.DismissInvitation(september))

        assertNull(invitationOf(viewModel))
        assertEquals(listOf(september), invitations.written)
        assertNull(invitationOf(started()))
    }

    @Test
    fun theNextMonthsFirstWeekInvitesAgain() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        publish(november, 100)
        val viewModel = started()
        viewModel.onAction(ReciboAction.DismissInvitation(september))
        assertNull(invitationOf(viewModel))

        clock.goTo("2026-11-03")

        assertEquals(ReciboInvitation(october), invitationOf(viewModel))
    }

    @Test
    fun aFailedWriteStillHidesItForTheSession() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        invitations.writeResult = PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        val viewModel = started()

        viewModel.onAction(ReciboAction.DismissInvitation(september))

        assertEquals(listOf(september), invitations.written)
        assertNull(invitationOf(viewModel))
    }

    @Test
    fun anOlderMonthIsNotOfferedAgainWhenTheDayMovesBack() = runTest {
        clock.goTo("2026-11-03")
        publish(october, 100)
        publish(november, 100)
        publish(september, 920_000)
        invitations.state.value = PreferenceRead.Ready(october)
        val viewModel = started()
        assertNull(invitationOf(viewModel))

        clock.goTo("2026-10-03")

        assertNull(invitationOf(viewModel))
    }

    @Test
    fun theFirstStateDoesNotWaitForThePreferenceRead() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        val silent = ReciboViewModel(
            session,
            snapshots,
            HandOrderings(),
            testMoney(),
            NeverRead(),
            clock,
            HandCategoryReads()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            silent.state.collect {}
        }

        val state = silent.state.value as ReciboState.Month
        assertNull(state.invitation)
    }

    @Test
    fun aFailedPreferenceReadKeepsWhatWasLastKnownAndOtherwiseOffersIt() = runTest {
        publish(october, 100)
        publish(september, 920_000)
        invitations.state.value = PreferenceRead.Failed(PreferenceFailure.Unavailable, null)

        assertEquals(ReciboInvitation(september), invitationOf(started()))

        invitations.state.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, lastGood = september)

        assertNull(invitationOf(started()))
    }
}

/** Preferences kept in memory that outlive any view model made from them. */
internal class HandInvitations : ClosedMonthInvitePreferences {
    val state = MutableStateFlow<PreferenceRead<YearMonth?>>(PreferenceRead.Ready(null))
    val written = mutableListOf<YearMonth>()

    /** What a write says; a saved one is kept, a failed one is not. */
    var writeResult: PreferenceWrite = PreferenceWrite.Saved

    override fun observeHandled(): Flow<PreferenceRead<YearMonth?>> = state

    override suspend fun markHandled(month: YearMonth): PreferenceWrite {
        written += month
        if (writeResult == PreferenceWrite.Saved) state.value = PreferenceRead.Ready(month)
        return writeResult
    }
}

/** A preference read that has not answered yet. */
private class NeverRead : ClosedMonthInvitePreferences {
    override fun observeHandled(): Flow<PreferenceRead<YearMonth?>> = emptyFlow()

    override suspend fun markHandled(month: YearMonth): PreferenceWrite = PreferenceWrite.Saved
}
