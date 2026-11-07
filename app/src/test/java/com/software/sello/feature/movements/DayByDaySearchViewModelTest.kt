package com.software.sello.feature.movements

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.Matches
import com.software.sello.domain.policy.MonthMatches
import com.software.sello.domain.policy.SearchTotals
import com.software.sello.domain.port.Movement
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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

/** Día a día's search over every month (SELLO-193). */
@OptIn(ExperimentalCoroutinesApi::class)
class DayByDaySearchViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val session = MonthSession(HandFinancialClock("2026-10-06"), HandMonotonicClock())
    private val snapshots = HandDayByDaySnapshots()
    private val movements = HandMonthMovements()
    private val search = HandMovementSearch()
    private val food = dayCategory(1, "Alimentación")
    private val coffee = dayCategory(3, "Café", "local_cafe")

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(saved: SavedStateHandle = SavedStateHandle()) = DayByDayViewModel(
        saved,
        session,
        snapshots,
        movements,
        search,
        handSourceNames,
        testMoney()
    )

    private fun TestScope.watch(model: DayByDayViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.found.collect {} }
    }

    private val DayByDayViewModel.ready get() = found.value as SearchState.Ready

    /** The mock's «rappi»: three months, newest first, with the totals storage reads. */
    private val rappi = listOf(
        spent(11, food, "2026-10-03", 20_600, "Rappi, almuerzo"),
        spent(12, food, "2026-10-01", 25_600, "RAPPI cena"),
        spent(13, coffee, "2026-10-01", 15_000, "Rappi, café"),
        spent(14, food, "2026-09-27", 22_100, "Rappi, almuerzo"),
        received(15, "2026-09-20", 18_000, "Reembolso Rappi", IncomeSource.Transfer),
        spent(16, food, "2026-08-12", 27_900, "Rappi, almuerzo")
    )

    // Worked by hand: 20.600 + 25.600 + 15.000 = 61.200; 22.100 in September; 27.900 in
    // August; in all 61.200 + 22.100 + 27.900 = 111.200 over five expenses.
    private val rappiTotals = SearchTotals(
        listOf(
            MonthMatches(YearMonth.of(2026, 10), 3, Money.cop(61_200)),
            MonthMatches(YearMonth.of(2026, 9), 2, Money.cop(22_100)),
            MonthMatches(YearMonth.of(2026, 8), 1, Money.cop(27_900))
        ),
        Matches(5, Money.cop(111_200)),
        Matches(1, Money.cop(18_000))
    )

    private fun october() {
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        search.found["rappi"] = rappi to rappiTotals
    }

    /** Types [text] and lets the pause after typing go by. */
    private fun TestScope.type(model: DayByDayViewModel, text: String) {
        model.onAction(DayByDayAction.Search(text))
        advanceTimeBy(PAUSE)
        runCurrent()
    }

    @Test
    fun nothingTypedListsTheMonthsDays() = runTest {
        october()
        val model = model()
        watch(model)

        assertEquals(SearchState.Off, model.found.value)
        assertTrue(search.asked.isEmpty())
    }

    @Test
    fun wordsFindMovementsOfEveryMonthGroupedByMonthWithTheirTotals() = runTest {
        october()
        val model = model()
        watch(model)

        type(model, "rappi")

        val ready = model.ready
        assertEquals("rappi", ready.words)
        assertNull(ready.amount)
        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9), YearMonth.of(2026, 8)),
            ready.months.map { it.month }
        )
        assertEquals(listOf(3L, 2L, 1L), ready.months.map { it.count })
        assertEquals(
            listOf("61.200 pesos", "22.100 pesos", "27.900 pesos"),
            ready.months.map { it.spent?.spoken }
        )
        assertEquals(listOf(3, 2, 1), ready.months.map { it.rows.size })
        assertEquals(LocalDate.of(2026, 9, 20), ready.months[1].rows[1].date)
        assertEquals("más 18.000 pesos", ready.months[1].rows[1].row.amount.spoken)
        assertEquals(5L, ready.expenses?.count)
        assertEquals("111.200 pesos", ready.expenses?.total?.spoken)
        assertEquals(1L, ready.incomes?.count)
        assertEquals("más 18.000 pesos", ready.incomes?.total?.spoken)
        assertEquals(LocalDate.of(2026, 10, 6), ready.today)
        assertFalse(ready.more)
        // Fixed sources are found by the names the screen shows.
        assertEquals(mapOf("salary" to "Salario", "freelance" to "Freelance"), search.names)
    }

    @Test
    fun aMonthWhereOnlyAnIncomeMatchedHasNoSpendingSum() = runTest {
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        search.found["quincena"] = listOf(received(20, "2026-07-15", 1_250_000, "Quincena")) to
            SearchTotals(
                listOf(MonthMatches(YearMonth.of(2026, 7), 1, null)),
                null,
                Matches(1, Money.cop(1_250_000))
            )
        val model = model()
        watch(model)

        type(model, "quincena")

        assertNull(model.ready.months.single().spent)
        assertNull(model.ready.expenses)
        assertEquals("más 1.250.000 pesos", model.ready.incomes?.total?.spoken)
    }

    @Test
    fun aValidAmountIsSaidAsTheExactAmountSearched() = runTest {
        october()
        val model = model()
        watch(model)

        type(model, "18.000")
        assertEquals("18.000 pesos", model.ready.amount?.spoken)

        type(model, "18000")
        assertEquals("18.000 pesos", model.ready.amount?.spoken)

        // Badly grouped: searched as words only.
        type(model, "1.80.00")
        assertNull(model.ready.amount)
        assertEquals("1.80.00", model.ready.words)
    }

    @Test
    fun onlyTheQueryTypedLastIsReadOnceTypingPauses() = runTest {
        october()
        val model = model()
        watch(model)

        model.onAction(DayByDayAction.Search("r"))
        model.onAction(DayByDayAction.Search("ra"))
        model.onAction(DayByDayAction.Search("rappi"))
        assertEquals(SearchState.Loading("rappi", MovementFilter.All), model.found.value)
        advanceTimeBy(PAUSE)
        runCurrent()

        assertEquals(listOf("rappi" to MovementFilter.All), search.asked)
        assertEquals("rappi", model.ready.words)
    }

    @Test
    fun aReplyToAnOlderQueryNeverReplacesANewerOne() = runTest {
        october()
        val older = CompletableDeferred<Unit>()
        val newer = CompletableDeferred<Unit>()
        search.held["rap"] = older
        search.held["rappi"] = newer
        search.found["rap"] = rappi.take(1) to rappiTotals
        val model = model()
        watch(model)

        type(model, "rap")
        type(model, "rappi")
        // Both were asked; the older one answers first, while the newer is still out.
        assertEquals(listOf("rap", "rappi"), search.asked.map { it.first })
        older.complete(Unit)
        runCurrent()
        assertEquals(SearchState.Loading("rappi", MovementFilter.All), model.found.value)

        newer.complete(Unit)
        runCurrent()
        assertEquals("rappi", model.ready.words)
        assertEquals(6, model.ready.listed)
    }

    @Test
    fun aReplyArrivingAfterTheNewerOneIsShownIsDropped() = runTest {
        october()
        val older = CompletableDeferred<Unit>()
        search.held["rap"] = older
        search.found["rap"] = rappi.take(1) to rappiTotals
        val model = model()
        watch(model)

        type(model, "rap")
        type(model, "rappi")
        assertEquals("rappi", model.ready.words)
        older.complete(Unit)
        advanceUntilIdle()

        assertEquals("rappi", model.ready.words)
        assertEquals(6, model.ready.listed)
    }

    @Test
    fun clearingTheFieldReturnsToTheMonthsDays() = runTest {
        october()
        val model = model()
        watch(model)
        type(model, "rappi")

        model.onAction(DayByDayAction.Search(""))

        assertEquals(SearchState.Off, model.found.value)
        assertEquals("", model.query.value)
        assertTrue(model.state.value is DayByDayState.Ready)
        // Spaces alone search nothing either.
        type(model, "   ")
        assertEquals(SearchState.Off, model.found.value)
    }

    @Test
    fun nothingFoundIsReadyWithNoMonthsNeverAFailure() = runTest {
        october()
        val model = model()
        watch(model)

        type(model, "plomería")

        assertEquals("plomería", model.ready.words)
        assertTrue(model.ready.months.isEmpty())
        assertNull(model.ready.expenses)
        assertNull(model.ready.incomes)
    }

    @Test
    fun aFailedReadKeepsTheQueryAndRetrySearchesItAgain() = runTest {
        october()
        search.failure = MovementReadFailure.Storage(StorageFailure.Unavailable("disk"))
        val model = model()
        watch(model)

        type(model, "rappi")

        assertEquals(
            SearchState.Failed("rappi", MovementFilter.All, DayByDayProblem.Unread),
            model.found.value
        )
        assertEquals("rappi", model.query.value)
        search.failure = null
        model.onAction(DayByDayAction.Retry)
        assertEquals(6, model.ready.listed)
    }

    @Test
    fun matchesTooLargeToAddUpAreATypedFailureNeverWrapped() = runTest {
        october()
        search.failure = MovementReadFailure.Unrepresentable
        val model = model()
        watch(model)

        type(model, "rappi")

        assertEquals(
            SearchState.Failed("rappi", MovementFilter.All, DayByDayProblem.TooLarge),
            model.found.value
        )
    }

    @Test
    fun theQuerySurvivesProcessDeath() = runTest {
        october()
        val saved = SavedStateHandle()
        model(saved).onAction(DayByDayAction.Search("rappi"))

        val restored = model(saved)
        watch(restored)
        advanceTimeBy(PAUSE)
        runCurrent()

        assertEquals("rappi", restored.query.value)
        assertEquals("rappi", restored.ready.words)
    }

    @Test
    fun aQueryLongerThanTheSearchLimitIsCut() = runTest {
        october()
        val model = model()
        watch(model)

        model.onAction(DayByDayAction.Search("a".repeat(70)))

        assertEquals("a".repeat(60), model.query.value)
    }

    @Test
    fun theChipsFilterTheResultsAtOnce() = runTest {
        october()
        val model = model()
        watch(model)
        type(model, "rappi")

        model.onAction(DayByDayAction.Filter(MovementFilter.Incomes))

        // Same query: no pause before reading it under the new chip.
        assertEquals(MovementFilter.Incomes, model.ready.filter)
        assertEquals(
            listOf<Movement>(rappi[4]),
            model.ready.months.flatMap { it.rows }.map { row ->
                rappi.first { it is Movement.Received && it.income.id.value == row.row.id }
            }
        )
        assertEquals("rappi" to MovementFilter.Incomes, search.asked.last())
    }

    @Test
    fun longResultsLoadInPagesEachAfterTheLastRowListed() = runTest {
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val many = (1..25).map { spent(100 + it, food, "2026-10-01", 18_000, "Almuerzo") }
        search.found["almuerzo"] = many to SearchTotals(
            listOf(MonthMatches(october, 25, Money.cop(450_000))),
            // 18.000 × 25 = 450.000, worked by hand.
            Matches(25, Money.cop(450_000)),
            null
        )
        val model = model()
        watch(model)

        type(model, "almuerzo")
        assertEquals(20, model.ready.listed)
        assertTrue(model.ready.more)
        assertEquals(25L, model.ready.months.single().count)

        model.onAction(DayByDayAction.MoreFound)
        assertEquals(25, model.ready.listed)
        assertFalse(model.ready.more)
        assertEquals(
            many.map { it.expense.id.value },
            model.ready.months.single().rows.map { it.row.id }
        )
    }

    @Test
    fun aPageThatFailsKeepsTheResultsShown() = runTest {
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val many = (1..25).map { spent(100 + it, food, "2026-10-01", 18_000, "Almuerzo") }
        search.found["almuerzo"] = many to SearchTotals(
            listOf(MonthMatches(october, 25, Money.cop(450_000))),
            Matches(25, Money.cop(450_000)),
            null
        )
        val model = model()
        watch(model)
        type(model, "almuerzo")
        search.failure = MovementReadFailure.Storage(StorageFailure.Unavailable("disk"))

        model.onAction(DayByDayAction.MoreFound)

        assertEquals(DayByDayProblem.Unread, model.ready.pageFailure)
        assertEquals(20, model.ready.listed)
        search.failure = null
        model.onAction(DayByDayAction.Retry)
        assertNull(model.ready.pageFailure)
        assertEquals(25, model.ready.listed)
    }

    @Test
    fun aCorrectionMadeInItsEditorReadsTheSameResultsAgain() = runTest {
        october()
        val model = model()
        watch(model)
        type(model, "rappi")

        // The person corrects a result: storage and the month move to revision 8.
        search.revision = 8
        search.found["rappi"] = rappi.drop(1) to rappiTotals
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot(revision = 8))

        assertEquals("rappi", model.ready.words)
        assertEquals(5, model.ready.listed)
        assertFalse(model.ready.busy)
    }

    @Test
    fun resultsReadBetweenTwoChangesWaitForTheMonthToCatchUp() = runTest {
        october()
        search.revision = 8
        val model = model()
        watch(model)

        type(model, "rappi")
        assertEquals(SearchState.Loading("rappi", MovementFilter.All), model.found.value)

        snapshots.of(october).value = ReadState.Ready(octoberSnapshot(revision = 8))
        assertEquals(6, model.ready.listed)
    }

    @Test
    fun resultsThatCannotBeReadAgainStayMarkedOlder() = runTest {
        october()
        val model = model()
        watch(model)
        type(model, "rappi")

        snapshots.of(october).value =
            ReadState.Failed(SnapshotFailure.Storage(StorageFailure.Unavailable("disk")), null)

        assertTrue(model.ready.stale)
        assertEquals(6, model.ready.listed)
        assertFalse(model.ready.more)
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        assertFalse(model.ready.stale)
    }

    @Test
    fun theMonthAndTheSearchShareOneObservation() = runTest {
        october()
        val model = model()
        watch(model)
        type(model, "rappi")

        assertEquals(1, snapshots.started)
        model.onAction(DayByDayAction.Retry)
        assertEquals(2, snapshots.started)
    }

    private companion object {
        /** Longer than the pause the screen waits for after typing. */
        const val PAUSE = 300L
    }
}
