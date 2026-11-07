package com.software.sello.feature.movements

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.MovementFilter
import com.software.sello.domain.port.MovementReadFailure
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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

@OptIn(ExperimentalCoroutinesApi::class)
class DayByDayViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val session = MonthSession(HandFinancialClock("2026-10-06"), HandMonotonicClock())
    private val snapshots = HandDayByDaySnapshots()
    private val movements = HandMonthMovements()
    private val food = dayCategory(1, "Alimentación")
    private val bus = dayCategory(2, "Transporte", "directions_bus")
    private val coffee = dayCategory(3, "Café", "local_cafe", archived = true)
    private val home = dayCategory(4, "Hogar", "home")

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model(saved: SavedStateHandle = SavedStateHandle()) = DayByDayViewModel(
        saved,
        session,
        snapshots,
        movements,
        HandMovementSearch(),
        handSourceNames,
        testMoney()
    )

    private fun TestScope.watch(model: DayByDayViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }

    private val DayByDayViewModel.ready get() = state.value as DayByDayState.Ready

    /** The mock's first days: today, Monday, an income-only Saturday. */
    private fun mockMonth() {
        movements.rows = listOf(
            spent(13, coffee, "2026-10-06", 8_000),
            spent(12, bus, "2026-10-06", 2_950, "Bus"),
            spent(11, food, "2026-10-06", 18_000, "Almuerzo"),
            spent(10, food, "2026-10-05", 96_400, "Mercado D1"),
            spent(9, home, "2026-10-05", 1_100_000, "Arriendo", fixed = true),
            received(8, "2026-10-03", 2_500_000, "Salario")
        )
        // Worked by hand: 18.000 + 2.950 + 8.000 = 28.950; 1.100.000 + 96.400 = 1.196.400.
        movements.days = mapOf(
            LocalDate.of(2026, 10, 6) to Money.cop(28_950),
            LocalDate.of(2026, 10, 5) to Money.cop(1_196_400)
        )
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
    }

    @Test
    fun theMonthIsListedByDayNewestFirstWithEachDaysSpending() = runTest {
        val model = model()
        watch(model)
        assertEquals(DayByDayState.Loading, model.state.value)
        mockMonth()

        val ready = model.ready
        assertEquals(
            listOf(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 3)),
            ready.days.map { it.date }
        )
        assertEquals("28.950 pesos", ready.days[0].spent?.spoken)
        assertEquals("1.196.400 pesos", ready.days[1].spent?.spoken)
        // A day with only a 2.500.000 income has no sum: incomes are never netted in.
        assertNull(ready.days[2].spent)
        assertEquals(LocalDate.of(2026, 10, 6), ready.today)
        assertEquals(6L, ready.totalCount)
        assertFalse(ready.more)
    }

    @Test
    fun rowsSayTheirNoteCategoryFixedAndAnIncomesPlus() = runTest {
        val model = model()
        watch(model)
        mockMonth()

        val rows = model.ready.days.flatMap { it.rows }
        val coffeeRow = rows[0] as DayRow.Spent
        assertNull(coffeeRow.note)
        assertEquals("Café", coffeeRow.categoryName)
        assertEquals("local_cafe", coffeeRow.iconKey)
        assertTrue(coffeeRow.archived)
        assertFalse(coffeeRow.fixed)
        assertTrue((rows[4] as DayRow.Spent).fixed)
        val salary = rows[5] as DayRow.Received
        assertEquals("Salario", salary.note)
        assertEquals(IncomeSource.Salary.key, salary.sourceKey)
        assertEquals("más 2.500.000 pesos", salary.amount.spoken)
        assertEquals("8.000 pesos", coffeeRow.amount.spoken)
    }

    @Test
    fun theSummaryIsResumensOwnFiguresForTheMonth() = runTest {
        val model = model()
        watch(model)
        mockMonth()

        assertEquals("1.240.300 pesos", model.ready.spent.spoken)
        assertEquals("3.150.000 pesos", model.ready.income.spoken)
        assertEquals(october, model.ready.month)
    }

    @Test
    fun theChipsFilterAndTheChipSurvivesProcessDeath() = runTest {
        val saved = SavedStateHandle()
        val model = model(saved)
        watch(model)
        mockMonth()

        model.onAction(DayByDayAction.Filter(MovementFilter.Incomes))
        assertEquals(MovementFilter.Incomes, model.ready.filter)
        assertEquals(listOf("Salario"), model.ready.days.flatMap { it.rows }.map { it.note })
        model.onAction(DayByDayAction.Filter(MovementFilter.Expenses))
        assertEquals(5, model.ready.listed)
        assertTrue(model.ready.days.flatMap { it.rows }.all { it is DayRow.Spent })

        // A new process: the saved chip is read again.
        val restored = model(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        watch(restored)
        assertEquals(MovementFilter.Expenses, restored.ready.filter)
        assertEquals(MovementFilter.Expenses, movements.asked.last().first)
    }

    @Test
    fun longMonthsLoadInPages() = runTest {
        movements.rows = (1..25).map { spent(100 + it, food, "2026-10-01", 1_000L * it) }
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)

        assertEquals(20, model.ready.listed)
        assertTrue(model.ready.more)
        assertEquals(25L, model.ready.totalCount)
        model.onAction(DayByDayAction.More)
        assertEquals(25, model.ready.listed)
        assertFalse(model.ready.more)
    }

    @Test
    fun comingBackFromAnEditorKeepsEveryPageListed() = runTest {
        val many = (1..60).map { spent(100 + it, food, "2026-10-01", 1_000L * it) }
        movements.rows = many
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        val screen = watch(model)
        model.onAction(DayByDayAction.More)
        assertEquals(40, model.ready.listed)

        // The 40th row's editor stays open longer than sharing waits, then Back.
        screen.cancel()
        advanceTimeBy(6_000)
        runCurrent()
        watch(model)
        runCurrent()

        assertEquals(40, model.ready.listed)
        assertEquals(many[39].expense.id.value, model.ready.days.last().rows.last().id)
        assertTrue(model.ready.more)
    }

    @Test
    fun anotherChipOrMonthStartsAtOnePage() = runTest {
        movements.rows = (1..60).map { spent(100 + it, food, "2026-10-01", 1_000L * it) }
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)
        model.onAction(DayByDayAction.More)
        assertEquals(40, model.ready.listed)

        model.onAction(DayByDayAction.Filter(MovementFilter.Expenses))
        assertEquals(20, model.ready.listed)

        model.onAction(DayByDayAction.More)
        assertEquals(40, model.ready.listed)
        val september = YearMonth.of(2026, 9)
        session.select(september)
        snapshots.of(september).value = ReadState.Ready(
            octoberSnapshot().let { it.copy(budget = it.budget.copy(month = september)) }
        )
        assertEquals(september, model.ready.month)
        assertEquals(20, model.ready.listed)
    }

    @Test
    fun aPageThatFailsKeepsWhatIsShownAndRetryReadsItAgain() = runTest {
        movements.rows = (1..25).map { spent(100 + it, food, "2026-10-01", 1_000L * it) }
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)
        movements.failure = MovementReadFailure.Storage(StorageFailure.Unavailable("disk"))

        model.onAction(DayByDayAction.More)
        assertEquals(DayByDayProblem.Unread, model.ready.pageFailure)
        assertEquals(20, model.ready.listed)
        movements.failure = null
        model.onAction(DayByDayAction.Retry)
        assertFalse(model.ready.pageFailed)
        assertEquals(25, model.ready.listed)
    }

    @Test
    fun aMonthThatCannotBeReadIsAFailureNeverAnEmptyMonth() = runTest {
        movements.failure = MovementReadFailure.Storage(StorageFailure.Unavailable("disk"))
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)

        assertEquals(DayByDayState.Failed(DayByDayProblem.Unread), model.state.value)
        movements.failure = null
        mockMonth()
        model.onAction(DayByDayAction.Retry)
        assertEquals(6L, model.ready.totalCount)
    }

    @Test
    fun aDayWhoseSumOverflowsIsATypedFailureNeverWrapped() = runTest {
        movements.failure = MovementReadFailure.Unrepresentable
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)

        assertEquals(DayByDayState.Failed(DayByDayProblem.TooLarge), model.state.value)
    }

    @Test
    fun aSnapshotFailureBeforeAnythingIsShownIsTheError() = runTest {
        val model = model()
        watch(model)
        snapshots.of(october).value =
            ReadState.Failed(SnapshotFailure.Storage(StorageFailure.Unavailable("disk")), null)

        assertEquals(DayByDayState.Failed(DayByDayProblem.Unread), model.state.value)
    }

    @Test
    fun aMonthThatCannotBeReadAgainKeepsTheRowsAsOlder() = runTest {
        val model = model()
        watch(model)
        mockMonth()

        snapshots.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        assertTrue(model.ready.stale)
        assertEquals(6, model.ready.listed)
        assertFalse(model.ready.more)
        model.onAction(DayByDayAction.Retry)
        assertTrue(model.ready.stale)
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        assertFalse(model.ready.stale)
    }

    @Test
    fun aCorrectionOrItsUndoIsListedWithTheNextRevision() = runTest {
        val model = model()
        watch(model)
        mockMonth()

        // Corrected elsewhere: storage moves on; until the month's snapshot follows, the
        // rows read between the two are not shown as the month.
        movements.revision = 8
        movements.rows = movements.rows.drop(1)
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot(revision = 8))
        assertEquals(5, model.ready.listed)
        assertFalse(model.ready.busy)
    }

    @Test
    fun rowsReadBetweenTwoChangesWaitForTheMonthToCatchUp() = runTest {
        movements.revision = 8
        val model = model()
        watch(model)
        mockMonth()
        movements.revision = 8

        assertEquals(DayByDayState.Loading, model.state.value)
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot(revision = 8))
        assertEquals(6, model.ready.listed)
    }

    @Test
    fun theSharedMonthIsTheOneListed() = runTest {
        val model = model()
        watch(model)
        mockMonth()
        val september = YearMonth.of(2026, 9)
        session.select(september)

        assertEquals(DayByDayState.Loading, model.state.value)
        snapshots.of(september).value = ReadState.Ready(
            octoberSnapshot().let { it.copy(budget = it.budget.copy(month = september)) }
        )
        assertEquals(september, model.ready.month)
    }

    @Test
    fun underIngresosNoDayShowsAnExpenseSum() = runTest {
        movements.rows = listOf(
            received(7, "2026-10-01", 650_000, "Proyecto web"),
            spent(8, food, "2026-10-01", 15_000, "Rappi, café")
        )
        movements.days = mapOf(LocalDate.of(2026, 10, 1) to Money.cop(15_000))
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)
        assertEquals("15.000 pesos", model.ready.days.single().spent?.spoken)

        model.onAction(DayByDayAction.Filter(MovementFilter.Incomes))

        // "Jue 1" holds only the +650.000 income: no 15.000 above it.
        val day = model.ready.days.single()
        assertEquals(listOf("Proyecto web"), day.rows.map { it.note })
        assertNull(day.spent)
        model.onAction(DayByDayAction.Filter(MovementFilter.Expenses))
        assertEquals("15.000 pesos", model.ready.days.single().spent?.spoken)
    }

    @Test
    fun aDayTooLargeAfterRowsAreListedIsSaidAsTooLargeNotAsARetryablePage() = runTest {
        movements.rows = (1..25).map { spent(100 + it, food, "2026-10-01", 1_000L * it) }
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)
        movements.failure = MovementReadFailure.Unrepresentable

        model.onAction(DayByDayAction.More)

        assertEquals(DayByDayProblem.TooLarge, model.ready.pageFailure)
        assertEquals(20, model.ready.listed)
        assertFalse(model.ready.more)
    }

    @Test
    fun aDayMixingCurrenciesIsDamagedDataNotTooLarge() = runTest {
        movements.failure = MovementReadFailure.CurrencyMismatch
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)

        assertEquals(DayByDayState.Failed(DayByDayProblem.Damaged), model.state.value)
    }

    @Test
    fun aDayTooLargeIsReadAgainByItselfOnceTheMonthChanges() = runTest {
        movements.failure = MovementReadFailure.Unrepresentable
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot())
        val model = model()
        watch(model)
        assertEquals(DayByDayState.Failed(DayByDayProblem.TooLarge), model.state.value)

        // The person corrects the expense elsewhere: storage and the month move to revision 8.
        movements.failure = null
        mockMonth()
        movements.revision = 8
        snapshots.of(october).value = ReadState.Ready(octoberSnapshot(revision = 8))

        // No Reintentar was needed: the new revision read the month again.
        assertEquals(6, model.ready.listed)
        assertNull(model.ready.pageFailure)
    }
}
