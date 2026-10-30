package com.software.sello.feature.income

import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.IncomeCursor
import com.software.sello.domain.port.IncomeMonthState
import com.software.sello.domain.port.IncomePage
import com.software.sello.domain.port.IncomeReads
import com.software.sello.domain.port.IncomeSuggestionPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ResumenSnapshotState
import com.software.sello.domain.port.ResumenSnapshots
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.ScheduleReads
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.resumen.resumenSnapshot
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncomeHistoryViewModelTest {
    private val reads = HandIncomeReads()
    private val clock = HandFinancialClock("2026-10-22")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val october = YearMonth.of(2026, 10)
    private val money = testMoney()

    /** Resumen's summary of each month, as the history reads it. */
    private val summaries = mutableMapOf<YearMonth, MutableStateFlow<ResumenSnapshotState>>()

    private fun summary(month: YearMonth) =
        summaries.getOrPut(month) { MutableStateFlow(ReadState.Loading) }

    private val snapshots = object : ResumenSnapshots {
        override suspend fun read(month: YearMonth) = error("The history observes")

        override fun observe(month: YearMonth) = summary(month)
    }

    /** The schedules, as the history observes them. */
    private val book = MutableStateFlow<ScheduleBookState>(ReadState.Loading)

    private val schedules = object : ScheduleReads {
        override suspend fun book() = error("The history observes")

        override fun observe() = book
    }

    /** The same reads, but a page can be held back to look at the screen while it is read. */
    private val gated = object : IncomeReads by reads {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun inMonth(
            month: YearMonth,
            after: IncomeCursor?,
            limit: Int
        ): Outcome<IncomePage, StorageFailure> {
            gate?.await()
            return reads.inMonth(month, after, limit)
        }
    }

    /** The dismissed month as stored; [answer] is what a write reports. */
    private class HandSuggestions : IncomeSuggestionPreferences {
        val stored = MutableStateFlow<PreferenceRead<YearMonth?>>(PreferenceRead.Ready(null))
        val written = mutableListOf<YearMonth>()
        var answer: PreferenceWrite = PreferenceWrite.Saved

        /** Until it is false the read never answers, as a slow preference file does. */
        var slow = false

        override fun observeDismissedIncomeSuggestion(): Flow<PreferenceRead<YearMonth?>> =
            if (slow) flow { awaitCancellation() } else stored

        override suspend fun dismissIncomeSuggestion(month: YearMonth): PreferenceWrite {
            written += month
            if (answer == PreferenceWrite.Saved) stored.value = PreferenceRead.Ready(month)
            return answer
        }
    }

    private val suggestions = HandSuggestions()

    private fun history() =
        IncomeHistoryViewModel(gated, session, snapshots, schedules, suggestions, money, clock)

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    @Test
    fun pagesAreBoundedAndTheTotalIsNotCalculatedFromTheFirstPage() {
        repeat(51) { index ->
            reads.stored[incomeId(index + 1)] = storedIncome(index + 1, "2026-10-01", 1_000)
        }
        val model = history()
        reads.publish(october)
        val first = model.state.value as IncomeHistoryState.Ready
        assertEquals(50, first.lines.size)
        assertEquals("51.000 pesos", first.total.spoken)
        assertTrue(first.more)
        model.onAction(IncomeHistoryAction.More)
        val all = model.state.value as IncomeHistoryState.Ready
        assertEquals(51, all.lines.size)
        assertEquals(51, all.lines.map { it.id }.distinct().size)
        assertEquals(listOf(50, 50), reads.pages.map { it.second })
    }

    @Test
    fun aChangeKeepsTheListAndThePagesAlreadyLoadedAndOnlyTheFirstLoadShowsLoading() {
        repeat(51) { index ->
            reads.stored[incomeId(index + 1)] = storedIncome(index + 1, "2026-10-01", 1_000)
        }
        val model = history()
        reads.publish(october)
        model.onAction(IncomeHistoryAction.More)
        assertEquals(51, (model.state.value as IncomeHistoryState.Ready).lines.size)

        // A new income lands while the list is read again.
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        reads.stored[incomeId(52)] = storedIncome(52, "2026-10-02", 2_000)
        reads.publish(october, revision = 2)
        val reading = model.state.value
        gate.complete(Unit)

        assertTrue("The list flashed $reading", reading is IncomeHistoryState.Ready)
        assertEquals(51, (reading as IncomeHistoryState.Ready).lines.size)
        val updated = model.state.value as IncomeHistoryState.Ready
        assertEquals(52, updated.lines.size)
        assertEquals(incomeId(52).value, updated.lines.first().id)
        assertEquals("53.000 pesos", updated.total.spoken)
        assertFalse(updated.more)
        assertFalse(updated.busy)
    }

    @Test
    fun deletingAnIncomeUpdatesTheLoadedPagesInPlace() {
        repeat(51) { index ->
            reads.stored[incomeId(index + 1)] = storedIncome(index + 1, "2026-10-01", 1_000)
        }
        val model = history()
        reads.publish(october)
        model.onAction(IncomeHistoryAction.More)

        reads.stored.remove(incomeId(51))
        reads.publish(october, revision = 2)

        val updated = model.state.value as IncomeHistoryState.Ready
        assertEquals(50, updated.lines.size)
        assertEquals("50.000 pesos", updated.total.spoken)
        assertFalse(updated.more)
    }

    @Test
    fun aChangeWhileAPageIsBeingReadDoesNotLeaveTheListBusy() {
        repeat(51) { index ->
            reads.stored[incomeId(index + 1)] = storedIncome(index + 1, "2026-10-01", 1_000)
        }
        val model = history()
        reads.publish(october)
        val gate = CompletableDeferred<Unit>()
        gated.gate = gate
        model.onAction(IncomeHistoryAction.More)

        reads.stored[incomeId(52)] = storedIncome(52, "2026-10-02", 2_000)
        reads.publish(october, revision = 2)
        gate.complete(Unit)

        val updated = model.state.value as IncomeHistoryState.Ready
        assertEquals(50, updated.lines.size)
        assertTrue(updated.more)
        assertFalse(updated.busy)
        model.onAction(IncomeHistoryAction.More)
        assertEquals(52, (model.state.value as IncomeHistoryState.Ready).lines.size)
    }

    @Test
    fun failuresAreNotAnEmptyMonthAndChangingMonthDoesNotKeepOldRows() {
        reads.stored[incomeId(1)] = storedIncome(1, "2026-10-01", 150_000)
        val model = history()
        reads.publish(october)
        assertEquals(1, (model.state.value as IncomeHistoryState.Ready).lines.size)
        reads.of(october).value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
        assertEquals(IncomeHistoryState.Failed, model.state.value)
        val september = YearMonth.of(2026, 9)
        session.select(september)
        assertEquals(IncomeHistoryState.Loading, model.state.value)
        reads.publish(september)
        assertEquals(emptyList<IncomeLine>(), (model.state.value as IncomeHistoryState.Ready).lines)
    }

    // --- Where it went: the same summary Resumen shows ---

    @Test
    fun whatCameInIsSplitIntoCategoryBudgetsAndUnassignedFromTheMonthsSummary() {
        val model = history()
        // 150.000 came in; the limits add up to 100.000.
        summary(october).value = ReadState.Ready(resumenSnapshot(income = 150_000))

        val split = model.split.value!!
        assertEquals("100.000 pesos", split.allocated.spoken)
        assertEquals("50.000 pesos", split.unallocated.spoken)
        assertEquals(false, split.over)
        assertEquals(2f / 3f, split.allocatedShare, 0.0001f)
    }

    @Test
    fun moreBudgetedThanCameInIsSignedSaysBothFactsAndDrawsNoNegativeSegment() {
        val model = history()
        // 60.000 came in against 100.000 of limits: 40.000 short.
        summary(october).value = ReadState.Ready(resumenSnapshot(income = 60_000))

        val split = model.split.value!!
        assertEquals("menos 40.000 pesos", split.unallocated.spoken)
        assertEquals(true, split.over)
        assertEquals("60.000 pesos", split.income.spoken)
        assertEquals(1f, split.allocatedShare)
    }

    @Test
    fun withNothingInNothingIsSharedAndAFailedSummaryShowsNoFigures() {
        val model = history()
        summary(october).value = ReadState.Ready(resumenSnapshot(income = 0))
        val nothing = model.split.value!!.allocatedShare

        summary(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)

        assertEquals(0f, nothing)
        // Never zero for a read that failed: there is simply no split to show.
        assertNull(model.split.value)
    }

    @Test
    fun theSplitFollowsTheSelectedMonth() {
        val model = history()
        summary(october).value = ReadState.Ready(resumenSnapshot(income = 150_000))
        val september = YearMonth.of(2026, 9)

        session.select(september)
        val loading = model.split.value
        summary(september).value = ReadState.Ready(resumenSnapshot(income = 300_000))

        assertNull(loading)
        assertEquals("200.000 pesos", model.split.value!!.unallocated.spoken)
    }

    // --- Plan del mes: ADR 0012's BG-E08 and BG-E09, recurrence examples RE35 and RE36 ---

    private val created = Instant.parse("2026-10-01T08:00:00Z")

    private fun <T> Outcome<T, *>.value() = (this as Outcome.Success).value

    /** A salary of 1.000.000 on the 30th, from [start]; [handled] is its checkpoint. */
    private fun salary(handled: String? = null, start: String = "2026-10-01") = StoredSchedule(
        Schedule(
            ScheduleId.of("00000000-0000-4000-8000-000000000001").value(),
            ScheduledEntry.Income(
                IncomeSource.Salary,
                TransactionAmount.of(Money.cop(1_000_000)).value(),
                null
            ),
            Recurrence.of(
                RepeatRule.Monthly.of(30).value(),
                LocalDate.parse(start),
                null,
                emptyList()
            ).value()
        ),
        sequence = 1,
        handled = handled?.let { Occurrence(LocalDate.parse(it), 1) },
        version = 1,
        createdAt = created,
        updatedAt = created
    )

    private fun book(revision: Long, vararg stored: StoredSchedule) =
        ReadState.Ready(ScheduleBook(1, revision, stored.toList()))

    /** The month's summary at [revision]: [income] came in, budgets take [allocated]. */
    private fun summary(revision: Long, income: Long, allocated: Long = 2_800_000) =
        ReadState.Ready(
            resumenSnapshot(income = income).let {
                it.copy(
                    budget = it.budget.copy(revision = revision),
                    allocated = Money.cop(allocated),
                    unallocated = Money.cop(income - allocated)
                )
            }
        )

    @Test
    fun bgE08theExpectedSalaryIsPlannedApartFromWhatCameIn() {
        val model = history()
        summary(october).value = summary(revision = 7, income = 2_000_000)
        book.value = book(7, salary())

        val plan = (model.plan.value as IncomePlanState.Shown).plan
        assertEquals("2.000.000 pesos", plan.recorded.spoken)
        assertEquals("1.000.000 pesos", plan.expected.spoken)
        assertEquals("3.000.000 pesos", plan.planned.spoken)
        assertEquals("200.000 pesos", plan.unassigned.spoken)
        assertEquals(false, plan.over)
        assertEquals(
            listOf(LocalDate.parse("2026-10-30") to "1.000.000 pesos"),
            plan.items.map { it.date to it.amount.spoken }
        )
        // What came in is untouched: still 800.000 short of the budgets.
        assertEquals("menos 800.000 pesos", model.split.value!!.unallocated.spoken)
    }

    @Test
    fun bgE09onceRecordedTheSalaryIsNeverAlsoExpected() {
        val model = history()
        summary(october).value = summary(revision = 7, income = 2_000_000)
        book.value = book(7, salary())

        // Catch-up records the salary at revision 8. The summary is read first.
        summary(october).value = summary(revision = 8, income = 3_000_000)
        val between = (model.plan.value as IncomePlanState.Shown).plan
        book.value = book(8, salary(handled = "2026-10-30"))

        // The plan read at 7 stays until both are at 8: never 3.000.000 and 1.000.000.
        assertEquals("2.000.000 pesos", between.recorded.spoken)
        assertEquals("1.000.000 pesos", between.expected.spoken)
        assertEquals(IncomePlanState.None, model.plan.value)
    }

    @Test
    fun aPlanWaitsForTheSummaryAtTheSchedulesRevision() {
        val model = history()
        book.value = book(8, salary())
        summary(october).value = summary(revision = 7, income = 2_000_000)
        val behind = model.plan.value

        summary(october).value = summary(revision = 8, income = 2_000_000)

        assertEquals(IncomePlanState.None, behind)
        assertTrue(model.plan.value is IncomePlanState.Shown)
    }

    @Test
    fun moreBudgetedThanThePlanIsSigned() {
        val model = history()
        summary(october).value = summary(revision = 7, income = 500_000)
        book.value = book(7, salary())

        val plan = (model.plan.value as IncomePlanState.Shown).plan
        // 500.000 + 1.000.000 − 2.800.000.
        assertEquals("menos 1.300.000 pesos", plan.unassigned.spoken)
        assertEquals(true, plan.over)
    }

    @Test
    fun aPastMonthHasNoPlan() {
        val model = history()
        val september = YearMonth.of(2026, 9)
        session.select(september)
        summary(september).value = summary(revision = 7, income = 2_000_000)
        // From September, and catch-up has not yet recorded 30 September.
        book.value = book(7, salary(start = "2026-09-01"))

        assertEquals(IncomePlanState.None, model.plan.value)
    }

    @Test
    fun withNothingExpectedThereIsNoPlan() {
        val model = history()
        summary(october).value = summary(revision = 7, income = 2_000_000)
        book.value = book(7)

        assertEquals(IncomePlanState.None, model.plan.value)
    }

    @Test
    fun aFailedReadIsAFailedPlanNeverNoneAndRetryReadsAgain() {
        val model = history()
        summary(october).value = summary(revision = 7, income = 2_000_000)
        book.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
        val schedulesFailed = model.plan.value

        book.value = book(7, salary())
        summary(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        val summaryFailed = model.plan.value

        summary(october).value = summary(revision = 7, income = 2_000_000)
        model.onAction(IncomeHistoryAction.Retry)

        assertEquals(IncomePlanState.Failed, schedulesFailed)
        assertEquals(IncomePlanState.Failed, summaryFailed)
        assertTrue(model.plan.value is IncomePlanState.Shown)
    }

    // --- Asignar a una categoría: the suggestion for unassigned income (SELLO-083) ---

    /** The summary of [income] with the budgets taking 100.000, as the fixture has it. */
    private fun snapshot(income: Long) = ReadState.Ready(resumenSnapshot(income = income))

    @Test
    fun positiveUnassignedIncomeInTheMonthInProgressOffersACategoryWithThatLimit() {
        val model = history()
        // 150.000 came in; the budgets take 100.000: 50.000 has no budget.
        summary(october).value = snapshot(150_000)

        val offer = model.assign.value!!
        assertEquals("50.000 pesos", offer.unassigned.spoken)
        assertEquals("50000", offer.limit)
    }

    @Test
    fun nothingIsOfferedWhileTheSummaryIsStillRead() {
        val model = history()

        assertNull(model.assign.value)
    }

    @Test
    fun nothingIsUnassignedNothingIsOffered() {
        val model = history()
        // 100.000 came in and the budgets take 100.000.
        summary(october).value = snapshot(100_000)

        assertNull(model.assign.value)
    }

    @Test
    fun budgetsAboveTheIncomeKeepTheirSignAndOfferNothing() {
        val model = history()
        // 60.000 came in against 100.000 of budgets: 40.000 short.
        summary(october).value = snapshot(60_000)

        assertNull(model.assign.value)
        assertEquals("menos 40.000 pesos", model.split.value!!.unallocated.spoken)
    }

    @Test
    fun aClosedMonthOffersNothingEvenWithUnassignedIncome() {
        val model = history()
        val september = YearMonth.of(2026, 9)
        summary(october).value = snapshot(150_000)
        summary(september).value = snapshot(300_000)

        session.select(september)

        // 200.000 of September's income is unassigned, but the month is over.
        assertEquals("200.000 pesos", model.split.value!!.unallocated.spoken)
        assertNull(model.assign.value)
        session.select(october)
        assertEquals("50.000 pesos", model.assign.value!!.unassigned.spoken)
    }

    @Test
    fun aFailedReadOffersNothingEvenWithTheLastGoodFiguresKept() {
        val model = history()
        summary(october).value = snapshot(150_000)

        summary(october).value =
            ReadState.Failed(SnapshotFailure.Unrepresentable, resumenSnapshot(income = 150_000))

        assertNull(model.assign.value)
    }

    @Test
    fun aLimitTheFormCanHoldIsPrefilledWholeToTheLastDigit() {
        val model = history()
        // The budgets take 100.000, so this leaves 999.999.999.999 (twelve digits) unassigned.
        summary(october).value = snapshot(1_000_000_099_999)

        val offer = model.assign.value!!
        assertEquals("999.999.999.999", offer.unassigned.digits)
        assertEquals("999999999999", offer.limit)
    }

    @Test
    fun anUnassignedAmountThatIsNotAValidLimitIsOfferedWithAnEmptyLimitAndNeverClipped() {
        val model = history()
        // 1.000.000.000.000 unassigned: thirteen digits, more than a limit can be.
        summary(october).value = snapshot(1_000_000_100_000)

        val offer = model.assign.value!!
        assertEquals("1.000.000.000.000", offer.unassigned.digits)
        assertNull(offer.limit)
    }

    @Test
    fun theOfferFollowsTheSummaryWhenABudgetIsAssigned() {
        val model = history()
        summary(october).value = snapshot(150_000)
        val before = model.assign.value

        // A category of 50.000 is created: the budgets now take 150.000.
        summary(october).value = ReadState.Ready(
            resumenSnapshot(income = 150_000).let {
                it.copy(allocated = Money.cop(150_000), unallocated = Money.cop(0))
            }
        )

        assertEquals("50000", before!!.limit)
        assertNull(model.assign.value)
    }

    // --- Dismissing the suggestion (SELLO-083, owner decision 2026-10-29) ---

    @Test
    fun dismissingHidesTheSuggestionForTheMonthAndKeepsItAcrossARestart() {
        val model = history()
        summary(october).value = snapshot(150_000)
        assertEquals("50.000 pesos", model.assign.value!!.unassigned.spoken)

        model.onAction(IncomeHistoryAction.DismissAssign)

        assertNull(model.assign.value)
        assertEquals(listOf(october), suggestions.written)
        // A new screen after a restart reads the same stored month.
        val restarted = history()
        assertNull(restarted.assign.value)
        // Dismissing is advice only: the split still says what is unassigned.
        assertEquals("50.000 pesos", restarted.split.value!!.unallocated.spoken)
    }

    @Test
    fun aDismissedMonthIsOfferedAgainNextMonthWhileIncomeIsStillUnassigned() {
        suggestions.stored.value = PreferenceRead.Ready(october)
        val model = history()
        summary(october).value = snapshot(150_000)
        val november = YearMonth.of(2026, 11)
        summary(november).value = snapshot(120_000)
        assertNull(model.assign.value)

        clock.goTo("2026-11-03")

        assertEquals("20.000 pesos", model.assign.value!!.unassigned.spoken)
        assertEquals("20000", model.assign.value!!.limit)
    }

    @Test
    fun aFailedWriteStillHidesTheSuggestionForTheSession() {
        suggestions.answer = PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        val model = history()
        summary(october).value = snapshot(150_000)

        model.onAction(IncomeHistoryAction.DismissAssign)

        assertNull(model.assign.value)
        assertEquals(listOf(october), suggestions.written)
        // Nothing was kept: a screen opened later offers it again.
        assertEquals("50.000 pesos", history().assign.value!!.unassigned.spoken)
    }

    @Test
    fun aResetBringsTheDismissedSuggestionBack() {
        val model = history()
        summary(october).value = snapshot(150_000)
        model.onAction(IncomeHistoryAction.DismissAssign)
        assertNull(model.assign.value)

        // A reset clears every preference: nothing is dismissed any more.
        suggestions.stored.value = PreferenceRead.Ready(null)

        assertEquals("50.000 pesos", model.assign.value!!.unassigned.spoken)
    }

    @Test
    fun aSlowPreferenceReadDoesNotDelayTheSuggestionAndAFailedOneHidesNothing() {
        suggestions.slow = true
        val slow = history()
        summary(october).value = snapshot(150_000)
        assertEquals("50.000 pesos", slow.assign.value!!.unassigned.spoken)

        suggestions.slow = false
        suggestions.stored.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, lastGood = null)
        assertEquals("50.000 pesos", history().assign.value!!.unassigned.spoken)
    }

    @Test
    fun dismissingWhileNothingIsOfferedWritesNothing() {
        val model = history()
        summary(october).value = snapshot(100_000)

        model.onAction(IncomeHistoryAction.DismissAssign)

        assertEquals(emptyList<YearMonth>(), suggestions.written)
    }

    @Test
    fun reintentarBringsTheSuggestionBackAfterAFailedRead() {
        var healthy = false
        val flaky = object : ResumenSnapshots {
            override suspend fun read(month: YearMonth) = error("The history observes")

            override fun observe(month: YearMonth): Flow<ResumenSnapshotState> = flow {
                emit(
                    if (healthy) {
                        snapshot(150_000)
                    } else {
                        ReadState.Failed(SnapshotFailure.Unrepresentable, null)
                    }
                )
                awaitCancellation()
            }
        }
        val model = IncomeHistoryViewModel(
            gated,
            session,
            flaky,
            schedules,
            suggestions,
            money,
            clock
        )
        assertNull(model.assign.value)

        healthy = true
        model.onAction(IncomeHistoryAction.Retry)

        assertEquals("50.000 pesos", model.assign.value!!.unassigned.spoken)
    }
}
