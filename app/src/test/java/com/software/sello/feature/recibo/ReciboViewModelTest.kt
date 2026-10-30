package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryMonth
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.policy.MonthBudgetState
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReciboViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val august = YearMonth.of(2026, 8)
    private val clock = HandFinancialClock("2026-10-09")
    private val session = MonthSession(clock, HandMonotonicClock())

    /** Snapshots Recibo observes; it never reads one once. */
    private class ObserveOnlySnapshots : HandSnapshots() {
        override suspend fun read(month: YearMonth): Outcome<MonthlySnapshot, SnapshotFailure> =
            error("Recibo observes; it does not read once")
    }

    private val snapshots = ObserveOnlySnapshots()
    private val orderings = HandOrderings()

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun TestScope.started(): Pair<ReciboViewModel, Job> {
        val viewModel = ReciboViewModel(
            session,
            snapshots,
            orderings,
            testMoney(),
            HandInvitations(),
            clock
        )
        // Something has to be watching for the state to be kept up to date.
        val watching =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.state.collect {}
            }
        return viewModel to watching
    }

    private fun snapshot(month: YearMonth, spent: Long, categories: Int) = MonthlySnapshot(
        month = month,
        asOf = LocalDate.of(2026, 10, 9),
        zone = ZoneId.of("America/Bogota"),
        generation = 1,
        revision = 7,
        categories = List(categories) { index ->
            CategoryMonth(
                Category(
                    (CategoryId.of("00000000-0000-4000-8000-00000000000$index") as Outcome.Success)
                        .value,
                    (CategoryName.of("Categoría $index") as Outcome.Success).value,
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
        },
        spent = Money.cop(spent),
        overall = OverallBudget.NoLimit,
        futureDatedExpenses = 0
    )

    @Test
    fun itShowsLoadingUntilTheFirstReadArrives() = runTest {
        val (viewModel) = started()

        assertEquals(ReciboState.Loading, viewModel.state.value)
        assertEquals(listOf(october), snapshots.observed)
    }

    @Test
    fun aMonthWithNoCategoriesIsTheFirstRunNotZeroSpending() = runTest {
        val (viewModel) = started()

        snapshots.of(october).value = ReadState.Ready(snapshot(october, 0, categories = 0))

        assertEquals(ReciboState.FirstRun, viewModel.state.value)
    }

    @Test
    fun aMonthIsShownThroughTheSharedRenderModelWithItsExactTotal() = runTest {
        val (viewModel) = started()
        val read = ReadState.Ready(snapshot(october, 3_000_001_199_997, categories = 2))

        snapshots.of(october).value = read

        val state = viewModel.state.value as ReciboState.Month
        assertEquals(ReciboRenderModel(testMoney()).recibo(read), state)
        assertEquals(
            MoneyTextValue(MoneySign.None, "", "3.000.001.199.997", "3.000.001.199.997 pesos"),
            state.summary.amount
        )
        assertEquals(
            listOf("00000000-0000-4000-8000-000000000000", "00000000-0000-4000-8000-000000000001"),
            state.categories.map { it.id }
        )
    }

    @Test
    fun aFailedReadKeepsOlderFiguresOnlyAsOlderAndIsFailedWithoutThem() = runTest {
        val (viewModel) = started()
        val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException"))

        snapshots.of(october).value =
            ReadState.Failed(failure, lastGood = snapshot(october, 5_000, categories = 1))
        val withOlder = viewModel.state.value
        snapshots.of(october).value = ReadState.Failed(failure, lastGood = null)

        assertEquals(true, (withOlder as ReciboState.Month).stale)
        assertEquals(ReciboState.Failed, viewModel.state.value)
    }

    @Test
    fun retryObservesTheSameMonthAgain() = runTest {
        val (viewModel) = started()
        val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException"))
        snapshots.of(october).value = ReadState.Failed(failure, lastGood = null)

        viewModel.onAction(ReciboAction.Retry)
        snapshots.of(october).value = ReadState.Ready(snapshot(october, 0, categories = 0))

        assertEquals(listOf(october, october), snapshots.observed)
        assertEquals(ReciboState.FirstRun, viewModel.state.value)
    }

    @Test
    fun selectingAnotherMonthShowsThatMonth() = runTest {
        val (viewModel) = started()
        snapshots.of(october).value = ReadState.Ready(snapshot(october, 9_000, categories = 1))
        snapshots.of(august).value = ReadState.Ready(snapshot(august, 0, categories = 0))

        session.select(august)

        assertEquals(listOf(october, august), snapshots.observed)
        assertEquals(ReciboState.FirstRun, viewModel.state.value)
    }

    @Test
    fun theMonthChangeMovesRecibotoTheNewMonth() = runTest {
        val (viewModel) = started()
        val november = YearMonth.of(2026, 11)
        snapshots.of(october).value = ReadState.Ready(snapshot(october, 9_000, categories = 1))
        snapshots.of(november).value = ReadState.Ready(snapshot(november, 1, categories = 1))

        clock.goTo("2026-11-01")

        // Day 1: the invitation reads October once more, beside the month now shown.
        assertEquals(listOf(october, november, october), snapshots.observed)
        val state = viewModel.state.value as ReciboState.Month
        assertEquals(MoneyTextValue(MoneySign.None, "", "1", "1 pesos"), state.summary.amount)
        assertEquals(ReciboInvitation(october), state.invitation)
    }

    @Test
    fun changingThePersistedOrderReordersTheSameSelectedMonth() = runTest {
        val (viewModel) = started()
        val base = snapshot(october, 9_000, categories = 2)
        val ordered = base.copy(
            categories = listOf(
                base.categories[0].copy(expenseCount = 1),
                base.categories[1].copy(expenseCount = 2)
            )
        )
        snapshots.of(october).value = ReadState.Ready(ordered)

        assertEquals(
            listOf("00000000-0000-4000-8000-000000000001", "00000000-0000-4000-8000-000000000000"),
            (viewModel.state.value as ReciboState.Month).categories.map { it.id }
        )

        orderings.state.value = PreferenceRead.Ready(
            CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending)
        )

        assertEquals(
            listOf("00000000-0000-4000-8000-000000000000", "00000000-0000-4000-8000-000000000001"),
            (viewModel.state.value as ReciboState.Month).categories.map { it.id }
        )
    }
}

internal class HandOrderings : CategoryOrderingPreferences {
    val state = MutableStateFlow<PreferenceRead<CategoryOrderingPreference>>(
        PreferenceRead.Ready(CategoryOrderingPreference())
    )

    override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> = state

    override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite =
        PreferenceWrite.Saved

    override suspend fun setCategoryOrderDirection(
        direction: CategoryOrderDirection
    ): PreferenceWrite = PreferenceWrite.Saved
}
