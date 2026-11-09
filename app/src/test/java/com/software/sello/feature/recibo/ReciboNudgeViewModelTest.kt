package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.backup.HandBackupRecords
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.HandGuidance
import com.software.sello.testing.HandHistoryStart
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.HandYearSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Recibo's Guía hint and backup reminder (SELLO-211) and its goal card nudge (SELLO-213):
 * one at a time and one a day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReciboNudgeViewModelTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val utc = ZoneId.of("UTC")

    /** The financial day, moved by hand across midnight or into another zone. */
    private val clock = object : FinancialClock {
        override val today = MutableStateFlow(FinancialDay(LocalDate.of(2026, 11, 8), bogota))
    }

    private fun goTo(date: String, zone: ZoneId = bogota) {
        clock.today.value = FinancialDay(LocalDate.parse(date), zone)
    }

    private val november = YearMonth.of(2026, 11)
    private val december = YearMonth.of(2026, 12)
    private val session = MonthSession(clock, HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val guidance = HandGuidance()
    private val backups = HandBackupRecords()
    private val history = HandHistoryStart()

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun TestScope.started(kept: HandGuidance = guidance): ReciboViewModel {
        val viewModel = ReciboViewModel(
            session,
            snapshots,
            HandOrderings(),
            testMoney(),
            HandInvitations(),
            clock,
            HandCategoryReads(),
            HandYearSnapshots(),
            kept,
            backups,
            history,
            SavedStateHandle()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        return viewModel
    }

    /** A month with one category, so Recibo shows its figures rather than the first run. */
    private fun publish(month: YearMonth = november, categories: Int = 1) {
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
        snapshots.ready(
            MonthlySnapshot(
                month = month,
                asOf = LocalDate.of(2026, 11, 8),
                zone = bogota,
                generation = 1,
                revision = 1,
                categories = List(categories) { category },
                spent = Money.cop(0),
                overall = OverallBudget.NoLimit,
                futureDatedExpenses = 0
            )
        )
    }

    private fun backupAt(instant: String) = BackupRecord(
        LastBackup(Instant.parse(instant), "sello.json", 1_024, BackupSummary(1, 1, 0, 0, 0, 0))
    )

    private fun nudgeOf(viewModel: ReciboViewModel): ReciboNudge? =
        (viewModel.state.value as? ReciboState.Month)?.nudge

    // The Guía hint.

    @Test
    fun theGuideHintShowsOnceAnExpenseIsSavedAndKeepsTheDay() = runTest {
        publish()
        val viewModel = started()
        assertNull(nudgeOf(viewModel))

        history.starts(firstExpense = "2026-11-08", firstMovement = "2026-11-08")

        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
        assertEquals(listOf("shown GuideHint 2026-11-08"), guidance.writes)
        assertEquals(
            ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 8)),
            guidance.stored.memory.lastShown
        )
    }

    @Test
    fun anIncomeAloneIsNoReasonForTheGuideHint() = runTest {
        publish()
        history.starts(firstExpense = null, firstMovement = "2026-11-01")

        assertNull(nudgeOf(started()))
    }

    @Test
    fun itsCrossClosesItForGood() = runTest {
        publish()
        history.starts("2026-11-01", "2026-11-01")
        val viewModel = started()

        viewModel.onAction(ReciboAction.CloseGuideHint)
        goTo("2026-11-09")

        assertNull(nudgeOf(viewModel))
        assertEquals(setOf(Nudge.GuideHint), guidance.stored.memory.seen)
        assertNull(nudgeOf(started()))
    }

    @Test
    fun openingTheGuideClosesItForGood() = runTest {
        publish()
        history.starts("2026-11-01", "2026-11-01")
        val viewModel = started()

        viewModel.onAction(ReciboAction.OpenGuide)

        assertNull(nudgeOf(viewModel))
        assertEquals(setOf(Nudge.GuideHint), guidance.stored.memory.seen)
    }

    @Test
    fun aHelpSheetsGuideLinkLeavesTheGuideHintAsItIs() = runTest {
        publish()
        history.starts("2026-11-01", "2026-11-01")
        val viewModel = started()

        viewModel.onAction(ReciboAction.ShowHelp(ReciboHelp.Summary))
        viewModel.onAction(ReciboAction.OpenHelpGuide)

        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
        assertEquals(emptySet<Nudge>(), guidance.stored.memory.seen)
    }

    @Test
    fun aCloseThatCannotBeKeptStillHidesItHereAndLetsItComeBackLater() = runTest {
        publish()
        history.starts("2026-11-01", "2026-11-01")
        val viewModel = started()
        guidance.failWrites = true

        viewModel.onAction(ReciboAction.CloseGuideHint)

        assertNull(nudgeOf(viewModel))
        assertEquals(emptySet<Nudge>(), guidance.stored.memory.seen)
        guidance.failWrites = false
        assertEquals(ReciboNudge.GuideHint, nudgeOf(started()))
    }

    @Test
    fun peopleAlreadyUsingSelloStillSeeTheGuideHint() = runTest {
        guidance.set(GuidanceMemory(seen = Nudge.entries.filter { it.forNewUsers }.toSet()))
        publish()
        history.starts("2025-03-01", "2025-02-15")
        backups.reads.value = PreferenceRead.Ready(
            backupAt("2026-11-01T12:00:00Z")
        )

        assertEquals(ReciboNudge.GuideHint, nudgeOf(started()))
    }

    // Where and when nothing shows.

    @Test
    fun noNudgeWhileLoadingInTheFirstRunOrWhenTheMonthFailed() = runTest {
        history.starts("2026-01-05", "2026-01-05")
        val viewModel = started()
        val loading = viewModel.state.value
        snapshots.of(november).value = ReadState.Ready(
            MonthlySnapshot(
                november, LocalDate.of(2026, 11, 8), bogota, 1, 1, emptyList(),
                Money.cop(0), OverallBudget.NoLimit, 0
            )
        )

        assertEquals(ReciboState.Loading, loading)
        assertEquals(ReciboState.FirstRun(), viewModel.state.value)
        // Nothing showed, so no day was kept for a nudge.
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun noNudgeWhenTheMonthFailedWithNothingEarlier() = runTest {
        history.starts("2026-01-05", "2026-01-05")
        snapshots.of(november).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException")),
            lastGood = null
        )

        assertEquals(ReciboState.Failed, started().state.value)
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun aFailedGuidanceReadShowsNoNudgeEvenWithItsLastValue() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        guidance.failReads()

        assertNull(nudgeOf(started()))
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun aFailedReadOfTheLastBackupShowsNoNudgeRatherThanAWrongNumber() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        backups.failReads(lastGood = backupAt("2026-09-01T12:00:00Z"))

        assertNull(nudgeOf(started()))
    }

    @Test
    fun aFailedHistoryReadShowsNoNudge() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        history.fail()

        assertNull(nudgeOf(started()))
    }

    @Test
    fun aNudgeShownElsewhereTodayLeavesRecibosForTomorrow() = runTest {
        guidance.set(GuidanceMemory(lastShown = ShownNudge(Nudge.Carry, LocalDate.of(2026, 11, 8))))
        publish()
        history.starts("2026-11-05", "2026-11-05")
        val viewModel = started()
        val today = nudgeOf(viewModel)

        // Past midnight, the new day is free.
        publish(december)
        goTo("2026-12-01")

        assertNull(today)
        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
    }

    @Test
    fun overOlderFiguresNoNudgeShowsAndTheDayIsNotSpent() = runTest {
        publish()
        history.starts("2026-11-01", "2026-11-01")
        val good = (snapshots.of(november).value as ReadState.Ready).value
        snapshots.of(november).value = ReadState.Failed(
            SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException")),
            lastGood = good
        )
        val viewModel = started()
        val stale = viewModel.state.value as ReciboState.Month

        snapshots.of(november).value = ReadState.Ready(good)

        assertEquals(true, stale.stale)
        assertNull(stale.nudge)
        // Kept only once it really showed, over current figures.
        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
        assertEquals(listOf("shown GuideHint 2026-11-08"), guidance.writes)
    }

    @Test
    fun theHintShowsForOneDayOnceAndPastMidnightCountsAsSeen() = runTest {
        publish()
        history.starts("2026-11-05", "2026-11-05")
        val viewModel = started()
        val today = nudgeOf(viewModel)

        goTo("2026-11-09")

        assertEquals(ReciboNudge.GuideHint, today)
        assertNull(nudgeOf(viewModel))
        // Nothing else showed, so nothing was written: the next showing keeps it as seen.
        assertEquals(listOf("shown GuideHint 2026-11-08"), guidance.writes)
        assertNull(nudgeOf(started()))
    }

    @Test
    fun theNextNudgesShowingKeepsTheExpiredHintAsSeenInTheSameWrite() = runTest {
        guidance.set(
            GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 7)))
        )
        publish()
        // No backup since 9 October: the reminder is due on the 8th.
        history.starts("2026-10-09", "2026-10-09")

        val viewModel = started()

        assertEquals(ReciboNudge.BackupReminder(null), nudgeOf(viewModel))
        // One write keeps the reminder's day and the hint as seen.
        assertEquals(listOf("shown BackupReminder 2026-11-08"), guidance.writes)
        assertEquals(setOf(Nudge.GuideHint), guidance.stored.memory.seen)
    }

    @Test
    fun aShowingThatFailsWritesNeitherHalfSoTheExpiredHintIsNeverLost() = runTest {
        guidance.set(
            GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 7)))
        )
        publish()
        history.starts("2026-10-09", "2026-10-09")
        guidance.failWrites = true

        val viewModel = started()

        assertEquals(ReciboNudge.BackupReminder(null), nudgeOf(viewModel))
        assertEquals(listOf("shown BackupReminder 2026-11-08"), guidance.writes)
        assertEquals(emptySet<Nudge>(), guidance.stored.memory.seen)
        assertEquals(
            ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 7)),
            guidance.stored.memory.lastShown
        )
    }

    @Test
    fun aShowingThatCannotBeKeptStillShowsAndIsAskedOnce() = runTest {
        publish()
        history.starts("2026-11-05", "2026-11-05")
        guidance.failWrites = true
        val viewModel = started()

        snapshots.of(november).value = snapshots.of(november).value

        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
        assertEquals(listOf("shown GuideHint 2026-11-08"), guidance.writes)
        assertNull(guidance.stored.memory.lastShown)
    }

    @Test
    fun aZoneChangeBackNeverWritesTheShowingOverWithAnEarlierDay() = runTest {
        guidance.set(
            GuidanceMemory(lastShown = ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 9)))
        )
        publish()
        history.starts("2026-11-05", "2026-11-05")

        val viewModel = started()

        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
        assertEquals(emptyList<String>(), guidance.writes)
        assertEquals(
            ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 11, 9)),
            guidance.stored.memory.lastShown
        )
    }

    // The backup reminder.

    @Test
    fun thirtyDaysAfterTheLastBackupItSaysHowManyDays() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        // 9 October at 10:00 in Bogotá.
        backups.reads.value = PreferenceRead.Ready(
            backupAt("2026-10-09T15:00:00Z")
        )
        goTo("2026-11-07")
        val viewModel = started()
        val dayBefore = nudgeOf(viewModel)

        goTo("2026-11-08")

        // On the 29th day only the hint was due.
        assertEquals(ReciboNudge.GuideHint, dayBefore)
        assertEquals(ReciboNudge.BackupReminder(daysSinceBackup = 30), nudgeOf(viewModel))
        assertEquals("shown BackupReminder 2026-11-08", guidance.writes.last())
        assertEquals(LocalDate.of(2026, 11, 8), guidance.stored.memory.backupReminderShown)
    }

    @Test
    fun itTakesTheDayBeforeTheGuideHint() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        backups.reads.value = PreferenceRead.Ready(
            backupAt("2026-10-09T15:00:00Z")
        )

        assertEquals(ReciboNudge.BackupReminder(daysSinceBackup = 30), nudgeOf(started()))
    }

    @Test
    fun withNoBackupEverItCountsFromTheFirstMovement() = runTest {
        publish()
        history.starts(firstExpense = null, firstMovement = "2026-10-10")
        val viewModel = started()
        val early = nudgeOf(viewModel)

        goTo("2026-11-09")

        assertNull(early)
        assertEquals(ReciboNudge.BackupReminder(daysSinceBackup = null), nudgeOf(viewModel))
    }

    @Test
    fun noMovementNoReminder() = runTest {
        publish()
        goTo("2027-06-01")

        assertNull(nudgeOf(started()))
    }

    @Test
    fun itStaysThatDayThenWaitsThirtyDaysAndTheHintMayTakeTheNextDay() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        val viewModel = started()
        val shown = nudgeOf(viewModel)

        // Later that day, a new view model: still there.
        val again = nudgeOf(started())
        goTo("2026-11-09")
        val nextDay = nudgeOf(viewModel)
        guidance.set(guidance.stored.memory.copy(seen = setOf(Nudge.GuideHint)))
        goTo("2026-12-07")
        val stillWaiting = nudgeOf(viewModel)
        publish(december)
        goTo("2026-12-08")

        assertEquals(ReciboNudge.BackupReminder(null), shown)
        assertEquals(ReciboNudge.BackupReminder(null), again)
        assertEquals(ReciboNudge.GuideHint, nextDay)
        assertNull(stillWaiting)
        assertEquals(ReciboNudge.BackupReminder(null), nudgeOf(viewModel))
    }

    @Test
    fun aBackupMadeClearsItAtOnce() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        val viewModel = started()
        val before = nudgeOf(viewModel)

        backups.backupCompleted(backupAt("2026-11-08T20:00:00Z").last!!)

        assertEquals(ReciboNudge.BackupReminder(null), before)
        // The day is the reminder's, so the hint waits for tomorrow.
        assertNull(nudgeOf(viewModel))
    }

    @Test
    fun irARespaldoOnlyNavigatesAndKeepsTheReminder() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        val viewModel = started()
        val writes = guidance.writes.toList()

        viewModel.onAction(ReciboAction.OpenBackup)

        assertEquals(ReciboNudge.BackupReminder(null), nudgeOf(viewModel))
        assertEquals(writes, guidance.writes)
        assertEquals(BackupRecord(), backups.stored.value)
    }

    @Test
    fun noVolverAMostrarHidesItForGood() = runTest {
        publish()
        history.starts(firstExpense = null, firstMovement = "2026-01-05")
        val viewModel = started()

        viewModel.onAction(ReciboAction.TurnOffBackupReminder)
        val now = nudgeOf(viewModel)
        publish(YearMonth.of(2027, 3))
        goTo("2027-03-01")

        assertNull(now)
        assertEquals(true, guidance.stored.memory.backupReminderOff)
        assertNull(nudgeOf(viewModel))
        assertNull(nudgeOf(started()))
    }

    @Test
    fun aZoneChangeMovesTheBackupToAnotherDay() = runTest {
        publish()
        history.starts(firstExpense = null, firstMovement = "2026-01-05")
        // 9 October, 23:30 in Bogotá; already 10 October in UTC.
        backups.reads.value = PreferenceRead.Ready(
            backupAt("2026-10-10T04:30:00Z")
        )
        goTo("2026-11-08", utc)
        val viewModel = started()
        val inUtc = nudgeOf(viewModel)

        goTo("2026-11-08", bogota)

        assertNull(inUtc)
        assertEquals(ReciboNudge.BackupReminder(daysSinceBackup = 30), nudgeOf(viewModel))
    }

    @Test
    fun aZoneChangeBackToTheDayBeforeKeepsThatDayForItsNudge() = runTest {
        publish()
        history.starts("2026-01-05", "2026-01-05")
        guidance.set(GuidanceMemory(seen = setOf(Nudge.BackupReminder)))
        // The hint showed on 9 November in Tokyo; flying back, Bogotá is still on the 8th.
        guidance.set(
            guidance.stored.memory.copy(
                lastShown = ShownNudge(Nudge.Carry, LocalDate.of(2026, 11, 9))
            )
        )

        assertNull(nudgeOf(started()))
    }

    @Test
    fun newUserGuidanceUncheckedStillAllowsTheHint() = runTest {
        val fresh = HandGuidance(Guidance(existingUseChecked = false))
        publish()
        history.starts("2026-11-08", "2026-11-08")
        val viewModel = ReciboViewModel(
            session, snapshots, HandOrderings(), testMoney(), HandInvitations(), clock,
            HandCategoryReads(), HandYearSnapshots(), fresh, backups, history, SavedStateHandle()
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }

        assertEquals(ReciboNudge.GuideHint, nudgeOf(viewModel))
    }

    // The goal card's nudge (SELLO-213).

    /** The Guía hint and the backup reminder are done with, so only the goal nudge is left. */
    private fun onlyTheGoalNudgeLeft() {
        guidance.set(GuidanceMemory(seen = setOf(Nudge.GuideHint, Nudge.BackupReminder)))
    }

    /** The goal card as the root reports it: two or more goals taking turns, or not. */
    private fun ReciboViewModel.card(takesTurns: Boolean) =
        onAction(ReciboAction.GoalCard(takesTurns))

    @Test
    fun aCardTakingTurnsBringsTheGoalNudgeAndKeepsTheDay() = runTest {
        publish()
        onlyTheGoalNudgeLeft()
        val viewModel = started()

        viewModel.card(takesTurns = true)

        assertEquals(ReciboNudge.Goal, nudgeOf(viewModel))
        assertEquals(listOf("shown ReciboGoal 2026-11-08"), guidance.writes)
    }

    @Test
    fun whileTheCardIsHiddenOrShowsOneGoalTheNudgeNeitherShowsNorSpendsTheDay() = runTest {
        publish()
        onlyTheGoalNudgeLeft()
        val viewModel = started()
        val hidden = nudgeOf(viewModel)

        viewModel.card(takesTurns = false)

        assertNull(hidden)
        assertNull(nudgeOf(viewModel))
        assertEquals(emptyList<String>(), guidance.writes)
    }

    @Test
    fun whenTheCardHidesAgainTheNudgeGoesWithIt() = runTest {
        publish()
        onlyTheGoalNudgeLeft()
        val viewModel = started()
        viewModel.card(takesTurns = true)
        val shown = nudgeOf(viewModel)

        viewModel.card(takesTurns = false)

        assertEquals(ReciboNudge.Goal, shown)
        assertNull(nudgeOf(viewModel))
    }

    @Test
    fun theBackupReminderTakesTheDayBeforeTheGoalNudge() = runTest {
        publish()
        guidance.set(GuidanceMemory(seen = setOf(Nudge.GuideHint)))
        history.starts(firstExpense = null, firstMovement = "2026-01-05")
        val viewModel = started()

        viewModel.card(takesTurns = true)

        assertEquals(ReciboNudge.BackupReminder(daysSinceBackup = null), nudgeOf(viewModel))
        assertEquals(listOf("shown BackupReminder 2026-11-08"), guidance.writes)
    }

    @Test
    fun theGuideHintTakesTheDayAndTheGoalNudgeTheNextSoRecibosShowsOneAtATime() = runTest {
        publish()
        guidance.set(GuidanceMemory(seen = setOf(Nudge.BackupReminder)))
        history.starts("2026-11-08", "2026-11-08")
        val viewModel = started()
        viewModel.card(takesTurns = true)
        val first = nudgeOf(viewModel)

        goTo("2026-11-09")

        assertEquals(ReciboNudge.GuideHint, first)
        assertEquals(ReciboNudge.Goal, nudgeOf(viewModel))
        assertEquals(setOf(Nudge.BackupReminder, Nudge.GuideHint), guidance.stored.memory.seen)
    }

    @Test
    fun theGoalNudgesCrossClosesItForGood() = runTest {
        publish()
        onlyTheGoalNudgeLeft()
        val viewModel = started()
        viewModel.card(takesTurns = true)

        viewModel.onAction(ReciboAction.CloseGoalNudge)

        assertNull(nudgeOf(viewModel))
        assertEquals(true, Nudge.ReciboGoal in guidance.stored.memory.seen)
        val again = started()
        again.card(takesTurns = true)
        assertNull(nudgeOf(again))
    }

    @Test
    fun peopleAlreadyUsingSelloNeverSeeTheGoalNudge() = runTest {
        val existing = HandGuidance(Guidance(existingUseChecked = false))
        existing.set(GuidanceMemory(seen = setOf(Nudge.GuideHint, Nudge.BackupReminder)))
        existing.rememberExistingUse(wasUsingSello = true)
        publish()
        val viewModel = started(existing)

        viewModel.card(takesTurns = true)

        assertNull(nudgeOf(viewModel))
    }

    @Test
    fun theGoalNudgeWaitsUntilExistingUseIsChecked() = runTest {
        val unchecked = HandGuidance(Guidance(existingUseChecked = false))
        unchecked.set(GuidanceMemory(seen = setOf(Nudge.GuideHint, Nudge.BackupReminder)))
        publish()
        val viewModel = started(unchecked)
        viewModel.card(takesTurns = true)
        val waiting = nudgeOf(viewModel)

        unchecked.rememberExistingUse(wasUsingSello = false)

        assertNull(waiting)
        assertEquals(ReciboNudge.Goal, nudgeOf(viewModel))
    }
}
