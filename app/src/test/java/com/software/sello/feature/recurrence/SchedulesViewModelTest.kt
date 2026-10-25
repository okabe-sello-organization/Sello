package com.software.sello.feature.recurrence

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleBookState
import com.software.sello.domain.port.SnapshotState
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.pesos
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.Instant
import java.time.LocalDate
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

/** Pagos e ingresos fijos on jue 22 oct 2026, from the locked page's figures. */
@OptIn(ExperimentalCoroutinesApi::class)
class SchedulesViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val clock = HandFinancialClock("2026-10-22")
    private val reads = HandScheduleReads()
    private val snapshots = HandSnapshots()
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun reset() = Dispatchers.resetMain()

    private fun d(text: String) = LocalDate.parse(text)

    private fun <T> Outcome<T, *>.value() = (this as Outcome.Success).value

    private fun stored(
        number: Int,
        entry: ScheduledEntry,
        rule: RepeatRule,
        start: String,
        handled: String? = null,
        end: String? = null,
        pauses: List<SchedulePause> = emptyList()
    ) = StoredSchedule(
        Schedule(
            scheduleId(number),
            entry,
            Recurrence.of(rule, d(start), end?.let(::d), pauses).value()
        ),
        sequence = number.toLong(),
        handled = handled?.let { Occurrence(d(it), 1) },
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun pay(category: Int, amount: Long, note: String? = null) = ScheduledEntry.Payment(
        categoryId(category),
        pesos(amount),
        note?.let { (com.software.sello.domain.model.Note.of(it) as Outcome.Success).value }
    )

    private fun monthly(day: Int) = RepeatRule.Monthly.of(day).value()

    private val rent =
        stored(1, pay(1, 950_000, "Arriendo"), monthly(5), "2026-10-01", "2026-10-05")
    private val bills = stored(2, pay(1, 200_000), monthly(28), "2026-10-01")
    private val bus = stored(
        3,
        pay(2, 20_000),
        RepeatRule.EveryWeeks.of(1).value(),
        "2026-09-26",
        handled = "2026-10-10",
        pauses = listOf(SchedulePause(d("2026-10-12"), null))
    )
    private val course =
        stored(4, pay(3, 189_000), monthly(16), "2026-06-01", "2026-09-16", end = "2026-09-30")
    private val salary = stored(
        5,
        ScheduledEntry.Income(IncomeSource.Salary, pesos(1_000_000), null),
        monthly(30),
        "2026-10-01"
    )

    private fun publish(vararg schedules: StoredSchedule) {
        reads.state.value = ScheduleBookState.Ready(ScheduleBook(1, 1, schedules.toList()))
    }

    private fun categories() = snapshots.ready(
        monthOf(
            "2026-10-22",
            listOf(
                categoryIn(1, "Hogar", 400_000, icon = "home"),
                categoryIn(2, "Transporte", 100_000, icon = "directions_bus"),
                categoryIn(3, "Educación", null, archived = true, icon = "school")
            )
        )
    )

    private fun list(saved: SavedStateHandle = SavedStateHandle()) =
        SchedulesViewModel(saved, reads, snapshots, clock, money)

    private val SchedulesViewModel.ready get() = state.value as SchedulesState.Ready

    @Test
    fun paymentsAreListedByDayWithTheMonthAsAPlanAndEndedOnesApart() {
        val model = list()
        categories()
        publish(rent, bills, bus, course, salary)

        val ready = model.ready
        assertEquals(ScheduleTab.Payments, ready.tab)
        assertEquals(listOf("3", "1", "2"), ready.rows.map { it.id.takeLast(1) })
        assertEquals(listOf("Transporte", "Hogar", "Hogar"), ready.rows.map { it.categoryName })
        assertEquals(listOf(null, "Arriendo", null), ready.rows.map { it.note })
        assertEquals(listOf(d("2026-10-12"), null, null), ready.rows.map { it.pausedFrom })
        assertEquals(listOf(null, d("2026-11-05"), d("2026-10-28")), ready.rows.map { it.next })
        assertEquals(listOf(false, false, true), ready.rows.map { it.soon })
        // Ended on 30 Sep; its category is archived and still names it.
        assertEquals(
            listOf("Educación" to d("2026-09-30")),
            ready.ended.map {
                it.categoryName to
                    it.endedOn
            }
        )
        // Through: 950.000 + 20.000 (3 oct) + 20.000 (10 oct); to come: 200.000 on the 28th.
        val plan = ready.plan!!
        assertEquals(
            listOf("990.000 pesos", "200.000 pesos", "1.190.000 pesos"),
            listOf(plan.done.spoken, plan.toCome.spoken, plan.total.spoken)
        )
        assertEquals(listOf(3, 1, 2), listOf(plan.doneCount, plan.toComeCount, plan.active))
    }

    @Test
    fun incomesAreTheirOwnTabAndTheTabSurvivesARestart() {
        val saved = SavedStateHandle()
        val model = list(saved)
        categories()
        publish(rent, salary)
        model.onAction(SchedulesAction.TabPicked(ScheduleTab.Incomes))

        val restored = list(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }))

        assertEquals(listOf("salary"), model.ready.rows.map { it.sourceKey })
        assertEquals("1.000.000 pesos", model.ready.plan!!.toCome.spoken)
        assertEquals(ScheduleTab.Incomes, restored.ready.tab)
    }

    @Test
    fun openedForIncomesItStartsThere() {
        val model = list(SavedStateHandle(mapOf(SchedulesViewModel.KEY_INCOME to true)))
        categories()
        publish()

        assertEquals(ScheduleTab.Incomes, model.ready.tab)
        assertTrue(model.ready.empty)
    }

    @Test
    fun aFailedReadIsFailedNeverAnEmptyListAndRetryReadsAgain() {
        val model = list()
        categories()
        reads.state.value = ScheduleBookState.Failed(StorageFailure.Unavailable("x"), null)
        val schedulesFailed = model.state.value
        publish(rent)
        snapshots.of(october).value = SnapshotState.Failed(SnapshotFailure.Unrepresentable, null)
        val categoriesFailed = model.state.value
        categories()
        model.onAction(SchedulesAction.Retry)

        assertEquals(SchedulesState.Failed, schedulesFailed)
        assertEquals(SchedulesState.Failed, categoriesFailed)
        assertEquals(1, model.ready.rows.size)
    }
}
