package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ajustar presupuesto on jue 22 oct 2026. Limits 830.000 (Café 50.000, Comida 200.000,
 * Hogar 400.000, Ropa 70.000, Salud 80.000 and the archived Viejo 30.000); 860.000
 * spent, so the month is 30.000 over. Regalos and Libros have no limit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdjustBudgetViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2026-10-22")
    private val session = MonthSession(clock, HandMonotonicClock())
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })

    private val month = monthOf(
        "2026-10-22",
        listOf(
            categoryIn(1, "Café", 50_000),
            categoryIn(2, "Comida", 200_000),
            categoryIn(3, "Hogar", 400_000),
            categoryIn(4, "Ropa", 70_000),
            categoryIn(5, "Salud", 80_000),
            categoryIn(6, "Regalos", null),
            categoryIn(7, "Libros", null),
            categoryIn(8, "Viejo", 30_000, archived = true)
        ),
        listOf(
            storedExpense(50, 1, "2026-10-05", 90_000),
            storedExpense(51, 2, "2026-10-06", 230_000),
            storedExpense(52, 3, "2026-10-07", 400_000),
            storedExpense(53, 4, "2026-10-08", 70_000),
            storedExpense(54, 5, "2026-10-09", 60_000),
            storedExpense(55, 6, "2026-10-10", 10_000)
        )
    )

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun model() = AdjustBudgetViewModel(SavedStateHandle(), session, snapshots, money)

    @Test
    fun readyShowsTheSummaryTheLinesAndBothGroups() {
        val model = model()
        snapshots.ready(month)

        val ready = model.state.value as AdjustBudgetState.Ready
        assertEquals(october, ready.month)
        assertEquals(ReciboStamp.Over, ready.summary.stamp)
        assertEquals("830.000 pesos", ready.limit?.spoken)
        assertEquals("860.000 pesos", ready.spent.spoken)
        assertEquals(
            listOf(uuid(1), uuid(2), uuid(3), uuid(4), uuid(5), uuid(8)),
            ready.limited.map { it.id }
        )
        assertEquals(listOf(uuid(6)), ready.open.map { it.id })
        val cafe = ready.limited.first()
        assertEquals("menos 40.000 pesos", cafe.remaining?.spoken)
        assertEquals("40.000 pesos", cafe.overBy?.spoken)
        assertEquals("90.000 pesos", cafe.spent.spoken)
        assertEquals("50.000 pesos", cafe.limit?.spoken)
        assertTrue(cafe.over)
        assertEquals(null, ready.open.single().limit)
    }

    @Test
    fun anArchivedCategoryIsListedAndMarked() {
        val model = model()
        snapshots.ready(month)

        val ready = model.state.value as AdjustBudgetState.Ready
        assertEquals(listOf(uuid(8)), ready.limited.filter { it.archived }.map { it.id })
    }

    @Test
    fun aFailedReadWithLastGoodIsStaleAndWithoutIsFailed() {
        val model = model()
        snapshots.of(october).value =
            SnapshotState.Failed(SnapshotFailure.Unrepresentable, null)
        val failed = model.state.value
        snapshots.of(october).value =
            SnapshotState.Failed(SnapshotFailure.Unrepresentable, month)

        assertEquals(AdjustBudgetState.Failed, failed)
        assertTrue((model.state.value as AdjustBudgetState.Ready).stale)
    }

    @Test
    fun theMonthIsTheOneTheScreenOpenedOn() {
        val model = model()
        snapshots.ready(month)
        clock.goTo("2026-11-01")

        assertEquals(listOf(october), snapshots.observed)
        assertEquals(october, (model.state.value as AdjustBudgetState.Ready).month)
    }

    @Test
    fun retryObservesAgain() {
        val model = model()
        snapshots.ready(month)

        model.onAction(AdjustBudgetAction.Retry)

        assertEquals(listOf(october, october), snapshots.observed)
    }
}
