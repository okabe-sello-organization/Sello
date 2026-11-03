package com.software.sello.feature.recurrence

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.EntryOrigin
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
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
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
    private val money = testMoney()

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
        reads.state.value = ReadState.Ready(ScheduleBook(1, 1, schedules.toList()))
    }

    private var entries = 100

    /** The expense schedule [number] recorded for its [occurrence], stored now at [amount]. */
    private fun paid(number: Int, occurrence: String, amount: Long, date: String = occurrence) =
        ScheduleRecord.Payment(
            storedExpense(entries++, 1, date, amount).copy(
                origin = EntryOrigin(scheduleId(number), Occurrence(d(occurrence), 1))
            )
        )

    /** What the schedules recorded, as the entries are stored now. */
    private fun recorded(vararg records: ScheduleRecord) {
        reads.records.value = records.toList()
    }

    /** The rent's 5 Oct entry, the bus's 3 and 10 Oct ones and the course's 16 Sep one. */
    private fun recordedSoFar() = recorded(
        paid(1, "2026-10-05", 950_000),
        paid(3, "2026-10-03", 20_000),
        paid(3, "2026-10-10", 20_000),
        paid(4, "2026-09-16", 189_000)
    )

    private fun SchedulesViewModel.plan() = ready.plan!!.let {
        listOf(it.done.spoken, it.doneCount, it.toCome.spoken, it.toComeCount, it.total.spoken)
    }

    private fun SchedulesViewModel.row(number: Int) =
        (ready.rows + ready.ended).single { it.id == scheduleId(number).value }

    private fun categories(today: String = "2026-10-22") = snapshots.ready(
        monthOf(
            today,
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
        recordedSoFar()

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
    fun eachScheduleIsGroupedByWhatItsMonthHolds() {
        // Starts in November: nothing this month.
        val later = stored(6, pay(1, 50_000), monthly(3), "2026-11-01")
        val model = list()
        categories()
        publish(rent, bills, bus, course, later)
        recordedSoFar()

        val all = model.ready.rows + model.ready.ended
        assertEquals(
            mapOf(
                "1" to ScheduleGroup.Recorded,
                "2" to ScheduleGroup.Due,
                "3" to ScheduleGroup.Paused,
                "4" to ScheduleGroup.Ended,
                "6" to ScheduleGroup.Later
            ),
            all.associate { it.id.takeLast(1) to it.group }
        )
        // The rent went through on the 5th; the bus on the 10th, before its pause.
        assertEquals(
            mapOf("1" to d("2026-10-05"), "3" to d("2026-10-10")),
            all.filter { it.recordedOn != null }.associate { it.id.takeLast(1) to it.recordedOn }
        )
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
        reads.state.value = ReadState.Failed(StorageFailure.Unavailable("x"), null)
        val schedulesFailed = model.state.value
        publish(rent)
        snapshots.of(october).value = ReadState.Failed(SnapshotFailure.Unrepresentable, null)
        val categoriesFailed = model.state.value
        categories()
        model.onAction(SchedulesAction.Retry)

        assertEquals(SchedulesState.Failed, schedulesFailed)
        assertEquals(SchedulesState.Failed, categoriesFailed)
        assertEquals(1, model.ready.rows.size)
    }

    /**
     * SELLO-148, RE17: rent on the 5th at 950.000, raised to 1.000.000 on 10 Oct. What went
     * through is the 5 Oct entry as stored: 950.000, then 900.000 once edited; deleted, the
     * rent has nothing recorded in October and leaves "Ya se anotaron".
     */
    @Test
    fun whatWentThroughIsTheEntryAsStoredNow() {
        val raised =
            stored(1, pay(1, 1_000_000, "Arriendo"), monthly(5), "2026-10-01", "2026-10-10")
        val model = list()
        categories()
        publish(raised)
        recorded(paid(1, "2026-10-05", 950_000))

        assertEquals(listOf("950.000 pesos", 1, "0 pesos", 0, "950.000 pesos"), model.plan())
        assertEquals(ScheduleGroup.Recorded, model.row(1).group)
        assertEquals(d("2026-10-05"), model.row(1).recordedOn)

        recorded(paid(1, "2026-10-05", 900_000))
        assertEquals(listOf("900.000 pesos", 1, "0 pesos", 0, "900.000 pesos"), model.plan())

        recorded()
        assertEquals(listOf("0 pesos", 0, "0 pesos", 0, "0 pesos"), model.plan())
        assertEquals(ScheduleGroup.Later, model.row(1).group)
        assertEquals(null, model.row(1).recordedOn)
    }

    /**
     * SELLO-148, RE18: on 10 Oct the rent moves from the 5th to the 25th. 950.000 went
     * through on 5 Oct and 950.000 is still to come on the 25th.
     */
    @Test
    fun aMovedDayKeepsWhatWentThroughOnTheOldDay() {
        val moved = stored(1, pay(1, 950_000, "Arriendo"), monthly(25), "2026-10-01", "2026-10-10")
        val model = list()
        categories()
        publish(moved)
        recorded(paid(1, "2026-10-05", 950_000))

        assertEquals(
            listOf("950.000 pesos", 1, "950.000 pesos", 1, "1.900.000 pesos"),
            model.plan()
        )
        assertEquals(ScheduleGroup.Due, model.row(1).group)
        assertEquals(d("2026-10-05"), model.row(1).recordedOn)
    }

    /**
     * SELLO-148, ruling on #329 (R1): the rent ended 30 Sep and the daily café, 8.000, ended
     * 10 Oct; on 20 Oct both run again and the checkpoint jumps to 20 Oct. The rent never
     * recorded 5 Oct: no "anotado 5 oct", nothing through. The café counts 1-10 Oct only,
     * never 11-20 Oct, and 21-31 Oct are still to come.
     */
    @Test
    fun aScheduleRunAgainCountsOnlyWhatItRecorded() {
        val resumed =
            stored(1, pay(1, 950_000, "Arriendo"), monthly(5), "2026-06-01", "2026-10-20")
        val cafe = stored(
            7,
            pay(1, 8_000, "Café"),
            RepeatRule.EveryDays.of(1).value(),
            "2026-10-01",
            "2026-10-20"
        )
        val model = list()
        categories()
        publish(resumed, cafe)
        recorded(
            paid(1, "2026-09-05", 950_000),
            *(1..10).map { paid(7, "2026-10-%02d".format(it), 8_000) }.toTypedArray()
        )

        // Through: 10 x 8.000 = 80.000. To come: 11 x 8.000 = 88.000. Both: 168.000.
        assertEquals(listOf("80.000 pesos", 10, "88.000 pesos", 11, "168.000 pesos"), model.plan())
        assertEquals(ScheduleGroup.Later, model.row(1).group)
        assertEquals(null, model.row(1).recordedOn)
        assertEquals(d("2026-11-05"), model.row(1).next)
        assertEquals(d("2026-10-10"), model.row(7).recordedOn)
    }

    /**
     * SELLO-148, ruling on #343 (R1): on 10 Nov an entry counts where it is stored now. The
     * 5 Nov one moved to 31 Oct leaves November with nothing through; the 5 Oct one moved
     * to 7 Nov gives November 1.900.000 from two, "anotado 7 nov".
     */
    @Test
    fun anEntryCountsInTheMonthOfItsStoredDate() {
        clock.goTo("2026-11-10")
        val model = list()
        categories("2026-11-10")
        publish(stored(1, pay(1, 950_000, "Arriendo"), monthly(5), "2026-10-01", "2026-11-05"))

        recorded(paid(1, "2026-10-05", 950_000), paid(1, "2026-11-05", 950_000, "2026-10-31"))
        assertEquals(listOf("0 pesos", 0, "0 pesos", 0, "0 pesos"), model.plan())
        assertEquals(ScheduleGroup.Later, model.row(1).group)
        assertEquals(null, model.row(1).recordedOn)

        recorded(paid(1, "2026-10-05", 950_000, "2026-11-07"), paid(1, "2026-11-05", 950_000))
        assertEquals(
            listOf("1.900.000 pesos", 2, "0 pesos", 0, "1.900.000 pesos"),
            model.plan()
        )
        assertEquals(ScheduleGroup.Recorded, model.row(1).group)
        assertEquals(d("2026-11-07"), model.row(1).recordedOn)
    }
}
