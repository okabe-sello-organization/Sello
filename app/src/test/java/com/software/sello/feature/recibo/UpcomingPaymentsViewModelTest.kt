package com.software.sello.feature.recibo

import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ReservedPayment
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Pagos previstos on jue 22 oct 2026 (RE30: 120.000 on the 25th and 80.000 on the 30th). */
@OptIn(ExperimentalCoroutinesApi::class)
class UpcomingPaymentsViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val snapshots = HandSnapshots()
    private val clock = HandFinancialClock("2026-10-22")
    private val money = testMoney()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun payment(number: Int, date: String, amount: Long, note: String?) = ReservedPayment(
        (ScheduleId.of(uuid(number)) as Outcome.Success).value,
        categoryId(1),
        note?.let { (Note.of(it) as Outcome.Success).value },
        Occurrence(LocalDate.parse(date), 1),
        pesos(amount)
    )

    private fun publish(vararg reserved: ReservedPayment) = snapshots.ready(
        (
            MonthlyBudgetPolicy.snapshot(
                october,
                FinancialDay(LocalDate.parse("2026-10-22"), ZoneId.of("America/Bogota")),
                1,
                1,
                listOf(categoryIn(1, "Ocio", 1_000_000)),
                listOf(storedExpense(50, 1, "2026-10-05", 400_000)),
                reserved.toList()
            ) as Outcome.Success
            ).value
    )

    @Test
    fun theReservedPaymentsAreListedByDateNamedByNoteElseCategory() {
        val model = UpcomingPaymentsViewModel(snapshots, clock, money)
        publish(
            payment(700, "2026-10-25", 120_000, "Suscripciones"),
            payment(701, "2026-10-30", 80_000, null)
        )

        val ready = model.state.value as UpcomingState.Ready
        assertEquals("200.000 pesos", ready.total.spoken)
        assertEquals(
            listOf(
                Triple("Suscripciones", LocalDate.parse("2026-10-25"), "120.000 pesos"),
                Triple("Ocio", LocalDate.parse("2026-10-30"), "80.000 pesos")
            ),
            ready.payments.map { Triple(it.name, it.date, it.amount.spoken) }
        )
        assertEquals(uuid(700), ready.payments.first().scheduleId)
    }

    @Test
    fun nothingReservedIsEmptyAndAFailedReadIsFailedNeverEmpty() {
        val model = UpcomingPaymentsViewModel(snapshots, clock, money)
        publish()
        val empty = model.state.value
        snapshots.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)

        assertEquals(UpcomingState.Empty(october), empty)
        assertEquals(UpcomingState.Failed, model.state.value)
    }
}
