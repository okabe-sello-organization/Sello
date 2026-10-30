package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CategoryInMonth
import com.software.sello.domain.policy.MonthlyBudgetPolicy
import com.software.sello.domain.policy.ReservationPolicy
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandExpenseReads
import com.software.sello.feature.expense.categoryId
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.feature.recurrence.scheduleId
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * Category detail with the category's fixed payments (SELLO-135,
 * docs/design/v2/categoria-fijos.html). Jue 22 oct 2026; Hogar (1) has a 1.500.000 limit.
 * Arriendo (950.000, the 5th) is recorded; Internet (90.000, the 23rd) and Servicios
 * públicos (200.000, the 28th) are still to come: 290.000 reserved. Aseo (60.000, every 2
 * weeks) is paused since the 12th. Café (2) has its own payment. Figures by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailFixedTest {
    private val october = YearMonth.of(2026, 10)
    private val today = LocalDate.parse("2026-10-22")
    private val session = MonthSession(HandFinancialClock("2026-10-22"), HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val schedules = HandScheduleReads()
    private val home = categoryIn(1, "Hogar", 1_500_000)
    private val coffee = categoryIn(2, "Café", null)

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun stored(
        number: Int,
        category: Int,
        note: String,
        rule: RepeatRule,
        amount: Long,
        handled: String?,
        pauses: List<SchedulePause> = emptyList()
    ) = StoredSchedule(
        Schedule(
            scheduleId(number),
            ScheduledEntry.Payment(
                categoryId(category),
                (TransactionAmount.of(Money.cop(amount)) as Outcome.Success).value,
                (Note.of(note) as Outcome.Success).value
            ),
            (
                Recurrence.of(rule, LocalDate.parse("2026-09-01"), null, pauses) as
                    Outcome.Success
                ).value
        ),
        sequence = number.toLong(),
        handled = handled?.let { Occurrence(LocalDate.parse(it), 1) },
        version = 1,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun monthly(day: Int) = (RepeatRule.Monthly.of(day) as Outcome.Success).value
    private val fortnightly = (RepeatRule.EveryWeeks.of(2) as Outcome.Success).value

    private val book = listOf(
        stored(1, 1, "Arriendo", monthly(5), 950_000, "2026-10-05"),
        stored(2, 1, "Internet", monthly(23), 90_000, "2026-09-23"),
        stored(3, 1, "Servicios públicos", monthly(28), 200_000, "2026-09-28"),
        stored(
            4,
            1,
            "Aseo",
            fortnightly,
            60_000,
            "2026-09-29",
            listOf(SchedulePause(LocalDate.parse("2026-10-12"), null))
        ),
        stored(5, 2, "Suscripción", monthly(25), 20_000, "2026-09-25")
    )

    private fun snapshotOf(
        spent: Long,
        stored: List<StoredSchedule>,
        generation: Long = 1,
        revision: Long = 1
    ) = (
        MonthlyBudgetPolicy.snapshot(
            october,
            FinancialDay(today, ZoneId.of("America/Bogota")),
            generation,
            revision,
            listOf(home, coffee),
            listOf(storedExpense(100, 1, "2026-10-05", spent)),
            ReservationPolicy.reserved(stored, october)
        ) as Outcome.Success
        ).value

    private fun publish(
        spent: Long,
        stored: List<StoredSchedule> = book,
        month: YearMonth = october,
        categories: List<CategoryInMonth> = listOf(home, coffee)
    ) {
        val expenses: List<Expense> =
            listOf(storedExpense(100, 1, month.atDay(5).toString(), spent))
        snapshots.ready(
            (
                MonthlyBudgetPolicy.snapshot(
                    month,
                    FinancialDay(today, ZoneId.of("America/Bogota")),
                    1,
                    1,
                    categories,
                    expenses,
                    ReservationPolicy.reserved(stored, month)
                ) as Outcome.Success
                ).value
        )
    }

    private fun TestScope.started(): CategoryDetailViewModel {
        val corrections = Corrections(
            HandExpenseCommands(),
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            { testScheduler.currentTime.milliseconds },
            { uuid(900) },
            CoroutineScope(Dispatchers.Main),
            HandCategoryReads(),
            testMoney()
        )
        val viewModel = CategoryDetailViewModel(
            session,
            snapshots,
            HandExpenseReads(),
            testMoney(),
            corrections,
            HandCategoryInsights(),
            schedules
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        return viewModel
    }

    private val CategoryDetailViewModel.ready get() = state.value as CategoryDetailState.Ready

    private fun CategoryDetailViewModel.show(category: Int) =
        onAction(CategoryDetailAction.Show(uuid(category)))

    /** Before: Te quedan 550.000. Now 1.500.000 − 950.000 − 290.000 = 260.000. */
    @Test
    fun theHeroIsWhatTheCategorysPaymentsStillToComeLeave() = runTest {
        val viewModel = started()
        publish(950_000)
        viewModel.show(1)

        val category = viewModel.ready.category
        assertEquals(
            CategoryReserved(
                BudgetFigure(ReciboHeadline.Remaining, plain("260.000"), ReciboStamp.OnTrack),
                ReciboReserved(plain("290.000"), 2),
                shortfall = null
            ),
            category.reserved
        )
        // Recibo's cell for the category keeps what was recorded against it.
        assertEquals(plain("550.000"), category.cellAmount)
    }

    @Test
    fun paymentsTakingExactlyWhatIsLeftAreSinMargen() = runTest {
        val viewModel = started()
        publish(1_210_000)
        viewModel.show(1)

        assertEquals(
            BudgetFigure(ReciboHeadline.Remaining, plain("0"), ReciboStamp.NoMargin),
            viewModel.ready.category.reserved?.figure
        )
    }

    /** 1.500.000 − 1.300.000 = 200.000 left; 290.000 to come: 90.000 short. */
    @Test
    fun paymentsNeedingMoreThanIsLeftAreRevisarWithRecibosTwoFacts() = runTest {
        val viewModel = started()
        publish(1_300_000)
        viewModel.show(1)

        val reserved = viewModel.ready.category.reserved!!
        assertEquals(
            BudgetFigure(ReciboHeadline.ShortForPayments, plain("90.000"), ReciboStamp.Review),
            reserved.figure
        )
        assertEquals(ReciboShortfall(plain("200.000"), false, plain("290.000")), reserved.shortfall)
    }

    @Test
    fun withNothingStillToComeTheHeroIsWhatIsLeft() = runTest {
        val viewModel = started()
        publish(950_000, stored = emptyList())
        viewModel.show(1)

        assertNull(viewModel.ready.category.reserved)
    }

    @Test
    fun theSlipListsWhatIsStillToComeThenTheCategorysOtherPayments() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        viewModel.show(1)

        assertEquals(
            DetailFixedState.Ready(
                upcoming = listOf(
                    DetailUpcomingPayment(
                        uuid(2),
                        "Internet",
                        LocalDate.parse("2026-10-23"),
                        plain("90.000")
                    ),
                    DetailUpcomingPayment(
                        uuid(3),
                        "Servicios públicos",
                        LocalDate.parse("2026-10-28"),
                        plain("200.000")
                    )
                ),
                later = listOf(
                    DetailSchedule(
                        uuid(1),
                        "Arriendo",
                        monthly(5),
                        LocalDate.parse("2026-09-01"),
                        next = LocalDate.parse("2026-11-05"),
                        pausedFrom = null,
                        amount = plain("950.000")
                    ),
                    DetailSchedule(
                        uuid(4),
                        "Aseo",
                        fortnightly,
                        LocalDate.parse("2026-09-01"),
                        next = null,
                        pausedFrom = LocalDate.parse("2026-10-12"),
                        amount = plain("60.000")
                    )
                )
            ),
            viewModel.ready.fixed
        )
    }

    @Test
    fun withNothingLeftToComeTheOtherPaymentsAreStillListed() = runTest {
        val viewModel = started()
        val recorded = book.take(1) + listOf(
            stored(2, 1, "Internet", monthly(20), 90_000, "2026-10-20")
        )
        publish(950_000, stored = recorded)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, recorded))
        viewModel.show(1)

        val fixed = viewModel.ready.fixed as DetailFixedState.Ready
        assertEquals(emptyList<DetailUpcomingPayment>(), fixed.upcoming)
        assertEquals(listOf(uuid(1), uuid(2)), fixed.later.map { it.id })
    }

    @Test
    fun aCategoryWithNoFixedPaymentsHasNoSlip() = runTest {
        val viewModel = started()
        publish(950_000, stored = book.drop(4))
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book.drop(4)))
        viewModel.show(1)

        assertEquals(DetailFixedState.Hidden, viewModel.ready.fixed)
    }

    @Test
    fun aPastMonthHasNoSlip() = runTest {
        val viewModel = started()
        val september = YearMonth.of(2026, 9)
        publish(1_450_000, month = september)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        session.select(september)
        viewModel.show(1)

        assertEquals(DetailFixedState.Hidden, viewModel.ready.fixed)
        assertNull(viewModel.ready.category.reserved)
    }

    @Test
    fun whileTheSchedulesAreReadTheSlipIsLoading() = runTest {
        val viewModel = started()
        publish(950_000)
        viewModel.show(1)

        assertEquals(DetailFixedState.Loading, viewModel.ready.fixed)
    }

    @Test
    fun aFailedReadKeepsTheSchedulesReadBefore() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        viewModel.show(1)
        val before = viewModel.ready.fixed

        schedules.state.value = ReadState.Failed(
            StorageFailure.Unavailable("hand"),
            ScheduleBook(1, 1, book)
        )

        assertEquals((before as DetailFixedState.Ready).copy(stale = true), viewModel.ready.fixed)
    }

    /** A retry starts a new read; when it fails too, what was listed stays, marked older. */
    @Test
    fun aRetryWhoseReadFailsAgainKeepsTheListReadBefore() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        viewModel.show(1)
        val before = viewModel.ready.fixed as DetailFixedState.Ready

        viewModel.onAction(CategoryDetailAction.Retry)
        schedules.state.value = ReadState.Failed(StorageFailure.Unavailable("hand"), null)

        assertEquals(before.copy(stale = true), viewModel.ready.fixed)
    }

    /** Internet was its only fixed payment and was deleted: it never comes back. */
    @Test
    fun aPaymentDeletedSinceIsNeverShownAgainFromWhatWasKept() = runTest {
        val viewModel = started()
        val internet = listOf(book[1])
        publish(950_000, stored = internet)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, internet))
        viewModel.show(1)
        assertEquals(1, (viewModel.ready.fixed as DetailFixedState.Ready).upcoming.size)

        // Deleted at revision 2: both reads see it gone.
        snapshots.ready(snapshotOf(950_000, emptyList(), revision = 2))
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 2, emptyList()))
        assertEquals(DetailFixedState.Hidden, viewModel.ready.fixed)

        // A later write: the hero arrives first, then the schedules read fails.
        snapshots.ready(snapshotOf(950_000, emptyList(), revision = 3))
        assertEquals(DetailFixedState.Hidden, viewModel.ready.fixed)
        schedules.state.value = ReadState.Failed(StorageFailure.Unavailable("hand"), null)
        assertEquals(DetailFixedState.Hidden, viewModel.ready.fixed)
    }

    /** After a restore, another history: the slip of the one before is not kept. */
    @Test
    fun aSlipOfAnotherHistoryIsNotKept() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        viewModel.show(1)

        snapshots.ready(snapshotOf(950_000, book, generation = 2))
        schedules.state.value = ReadState.Failed(StorageFailure.Unavailable("hand"), null)

        assertEquals(DetailFixedState.Failed, viewModel.ready.fixed)
    }

    /** Schedules read at another revision than the hero wait: the two always agree. */
    @Test
    fun schedulesOfAnotherRevisionThanTheHeroAreNotListed() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 2, book))
        viewModel.show(1)

        assertEquals(DetailFixedState.Loading, viewModel.ready.fixed)
    }

    @Test
    fun aCategoryWithoutALimitListsItsPaymentsAndReservesNothing() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Ready(ScheduleBook(1, 1, book))
        viewModel.show(2)

        assertNull(viewModel.ready.category.reserved)
        assertEquals(
            listOf(
                DetailUpcomingPayment(
                    uuid(5),
                    "Suscripción",
                    LocalDate.parse("2026-10-25"),
                    plain("20.000")
                )
            ),
            (viewModel.ready.fixed as DetailFixedState.Ready).upcoming
        )
    }

    @Test
    fun aFailedFirstReadIsSaidAndNeverShownAsNone() = runTest {
        val viewModel = started()
        publish(950_000)
        schedules.state.value = ReadState.Failed(StorageFailure.Unavailable("hand"), null)
        viewModel.show(1)

        assertEquals(DetailFixedState.Failed, viewModel.ready.fixed)
    }
}
