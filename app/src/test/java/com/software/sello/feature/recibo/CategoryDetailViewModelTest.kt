package com.software.sello.feature.recibo

import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ExpenseCommandOutcome.Rejected
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.SnapshotState
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandExpenseReads
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.expense.HandSnapshots
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import java.time.LocalDate
import java.time.YearMonth
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryDetailViewModelTest {
    private val october = YearMonth.of(2026, 10)
    private val september = YearMonth.of(2026, 9)
    private val session = MonthSession(HandFinancialClock("2026-10-23"), HandMonotonicClock())
    private val snapshots = HandSnapshots()
    private val reads = HandExpenseReads()
    private val insights = HandCategoryInsights()
    private val commands = HandExpenseCommands()
    private var nextOperation = 700
    private val labels = object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"

        override fun negative(amount: String) = "menos $amount"

        override fun positive(amount: String) = "más $amount"
    }
    private val food = categoryIn(1, "Alimentación", 900_000)
    private val coffee = categoryIn(2, "Café", null)

    @Before
    fun mainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetDispatcher() = Dispatchers.resetMain()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun TestScope.started(): CategoryDetailViewModel {
        val corrections = Corrections(
            commands,
            HandIncomeCommands(),
            HandGoalCommands(),
            HandInvestmentCommands(),
            // The clock moves with the test's own time, so a notice shown ends when it should.
            { testScheduler.currentTime.milliseconds },
            { uuid(nextOperation++) },
            CoroutineScope(Dispatchers.Main)
        )
        val viewModel =
            CategoryDetailViewModel(
                session,
                snapshots,
                reads,
                MoneyFormatter(labels),
                corrections,
                insights
            )
        // Something has to be watching for the state to be kept up to date.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect {}
        }
        return viewModel
    }

    /** Stores [expenses] and publishes the month they make, as a committed change would. */
    private fun commit(expenses: List<Expense>, today: String = "2026-10-23") {
        reads.stored.clear()
        expenses.forEach { reads.stored[it.id] = it }
        snapshots.ready(monthOf(today, listOf(food, coffee), expenses, october))
    }

    /** [count] expenses of 1.000 in Alimentación, numbered from 100, one per day from the 1st. */
    private fun many(count: Int) = List(count) {
        storedExpense(100 + it, 1, "2026-10-%02d".format(1 + it % 23), 1_000)
    }

    private val CategoryDetailViewModel.ready get() = state.value as CategoryDetailState.Ready
    private val CategoryDetailViewModel.rows get() = ready.expenses as ExpenseListState.Rows

    private fun show(viewModel: CategoryDetailViewModel, category: Int) =
        viewModel.onAction(CategoryDetailAction.Show(uuid(category)))

    @Test
    fun insightsWaitForTheSameRevisionGenerationAndFinancialDayAsTheHero() = runTest {
        val vm = started()
        commit(many(3))
        show(vm, 1)
        val budget = (
            snapshots.read(
                october
            ) as com.software.sello.domain.model.Outcome.Success
            ).value
        val raw = com.software.sello.domain.model.CategoryInsights(
            budget, food.category.id, null, null, 23,
            com.software.sello.domain.model.Money.cop(
                1_000
            ),
            null, null, null, emptyList(), null, emptyList()
        )
        insights.state.value =
            com.software.sello.domain.port.CategoryInsightsState.Ready(
                raw.copy(budget = budget.copy(revision = 2))
            )
        assertEquals(DetailInsightsState.Loading, vm.ready.insights)
        insights.state.value =
            com.software.sello.domain.port.CategoryInsightsState.Ready(
                raw.copy(budget = budget.copy(generation = 2))
            )
        assertEquals(DetailInsightsState.Loading, vm.ready.insights)
        insights.state.value =
            com.software.sello.domain.port.CategoryInsightsState.Ready(
                raw.copy(budget = budget.copy(asOf = budget.asOf.minusDays(1)))
            )
        assertEquals(DetailInsightsState.Loading, vm.ready.insights)
        insights.state.value = com.software.sello.domain.port.CategoryInsightsState.Ready(raw)
        assertEquals(
            plain("1.000"),
            (vm.ready.insights as DetailInsightsState.Ready).figures.averageExpense
        )
        insights.state.value =
            com.software.sello.domain.port.CategoryInsightsState.Failed(
                SnapshotFailure.Unrepresentable,
                raw
            )
        assertEquals(DetailInsightsState.Failed, vm.ready.insights)
        assertEquals(plain("3.000"), vm.ready.category.spent)
    }

    @Test
    fun nothingIsShownOrReadUntilACategoryIsOpened() = runTest {
        val viewModel = started()
        commit(many(3))

        assertEquals(CategoryDetailState.Closed, viewModel.state.value)
        assertEquals(emptyList<YearMonth>(), snapshots.observed)
        assertEquals(0, reads.pages.size)

        show(viewModel, 1)
        viewModel.onAction(CategoryDetailAction.Show(null))

        assertEquals(CategoryDetailState.Closed, viewModel.state.value)
    }

    @Test
    fun itShowsTheCategorysFiguresFromTheSnapshotAndItsExpensesNewestFirst() = runTest {
        val viewModel = started()
        commit(
            listOf(
                storedExpense(100, 1, "2026-10-03", 186_500, "Mercado quincenal"),
                storedExpense(101, 1, "2026-10-21", 20_600),
                storedExpense(102, 2, "2026-10-21", 8_800),
                storedExpense(103, 1, "2026-10-21", 33_500, "Almuerzo")
            )
        )

        show(viewModel, 1)

        // 186.500 + 20.600 + 33.500 = 240.600 of 900.000 leaves 659.400.
        val ready = viewModel.ready
        assertEquals(
            BudgetFigure(ReciboHeadline.Remaining, plain("659.400"), ReciboStamp.OnTrack),
            ready.category.figure
        )
        assertEquals(plain("240.600"), ready.category.spent)
        assertEquals(plain("900.000"), ready.category.limit)
        assertEquals(3, ready.category.expenseCount)
        assertEquals(october, ready.month)
        assertEquals(LocalDate.of(2026, 10, 23), ready.today)
        assertEquals(false, ready.stale)
        assertEquals(true, ready.canRecord)
        assertEquals(
            ExpenseListState.Rows(
                listOf(
                    ExpenseRow(
                        uuid(103),
                        LocalDate.of(2026, 10, 21),
                        "Almuerzo",
                        plain("33.500"),
                        false
                    ),
                    ExpenseRow(uuid(101), LocalDate.of(2026, 10, 21), null, plain("20.600"), false),
                    ExpenseRow(
                        uuid(100),
                        LocalDate.of(2026, 10, 3),
                        "Mercado quincenal",
                        plain("186.500"),
                        false
                    )
                ),
                more = false
            ),
            ready.expenses
        )
        assertEquals(listOf<Pair<ExpenseCursor?, Int>>(null to 20), reads.pages)
    }

    @Test
    fun aLongListComesAPageAtATimeAndSaysThereIsMoreWhileTheFiguresCountEverything() = runTest {
        val viewModel = started()
        commit(many(45))
        show(viewModel, 1)
        val first = viewModel.rows

        viewModel.onAction(CategoryDetailAction.ShowMore)
        val second = viewModel.rows
        viewModel.onAction(CategoryDetailAction.ShowMore)
        val third = viewModel.rows
        viewModel.onAction(CategoryDetailAction.ShowMore)

        assertEquals(20 to true, first.rows.size to first.more)
        assertEquals(40 to true, second.rows.size to second.more)
        assertEquals(45 to false, third.rows.size to third.more)
        assertEquals(45, third.rows.map { it.id }.toSet().size)
        // Whatever is listed, the figures are of all 45: 45.000 spent.
        assertEquals(plain("45.000"), viewModel.ready.category.spent)
        assertEquals(45, viewModel.ready.category.expenseCount)
        // Nothing was asked once there was no more.
        assertEquals(3, reads.pages.size)
        // Day 23 has one expense and each day before it two, so the 20th listed is the
        // later of the two on the 13th: number 135. The second page starts after it.
        assertEquals(ExpenseCursor(LocalDate.of(2026, 10, 13), 135), reads.pages[1].first)
    }

    @Test
    fun askingForMoreTwiceWhileOnePageIsBeingReadAsksOnce() = runTest {
        val viewModel = started()
        commit(many(70))
        show(viewModel, 1)
        val hold = CompletableDeferred<Unit>()
        reads.hold = hold

        viewModel.onAction(CategoryDetailAction.ShowMore)
        val reading = viewModel.rows
        viewModel.onAction(CategoryDetailAction.ShowMore)
        viewModel.onAction(CategoryDetailAction.ShowMore)
        hold.complete(Unit)

        assertEquals(
            Triple(20, true, true),
            Triple(reading.rows.size, reading.more, reading.loadingMore)
        )
        assertEquals(2, reads.pages.size)
        assertEquals(40 to false, viewModel.rows.rows.size to viewModel.rows.loadingMore)
    }

    @Test
    fun noExpensesIsEmptyAndAListThatCouldNotBeReadIsFailedNeverEmpty() = runTest {
        val viewModel = started()
        commit(listOf(storedExpense(100, 2, "2026-10-21", 8_800)))
        show(viewModel, 1)
        val empty = viewModel.ready.expenses

        reads.failure = StorageFailure.Unavailable("SQLiteException")
        viewModel.onAction(CategoryDetailAction.Retry)
        val failed = viewModel.ready
        reads.failure = null
        viewModel.onAction(CategoryDetailAction.Retry)

        assertEquals(ExpenseListState.Empty, empty)
        assertEquals(ExpenseListState.Failed, failed.expenses)
        // The figures were read; only the list was not.
        assertEquals(plain("0"), failed.category.spent)
        assertEquals(ExpenseListState.Empty, viewModel.ready.expenses)
        assertEquals(listOf(october, october, october), snapshots.observed)
    }

    @Test
    fun aLaterPageThatFailsKeepsWhatWasListedAndAsksForThatSamePageAgain() = runTest {
        val viewModel = started()
        commit(many(45))
        show(viewModel, 1)
        reads.failure = StorageFailure.Unavailable("SQLiteException")

        viewModel.onAction(CategoryDetailAction.ShowMore)
        val failed = viewModel.rows
        reads.failure = null
        viewModel.onAction(CategoryDetailAction.ShowMore)

        assertEquals(
            listOf(20, true, false, true),
            listOf(failed.rows.size, failed.more, failed.loadingMore, failed.moreFailed)
        )
        assertEquals(40 to false, viewModel.rows.rows.size to viewModel.rows.moreFailed)
        assertEquals(reads.pages[1], reads.pages[2])
        assertEquals(3, reads.pages.size)
    }

    @Test
    fun aSavedExpenseAppearsWithNewFiguresAndTheSameNumberOfPagesListed() = runTest {
        val viewModel = started()
        val before = many(45)
        commit(before)
        show(viewModel, 1)
        viewModel.onAction(CategoryDetailAction.ShowMore)
        val hold = CompletableDeferred<Unit>()
        reads.hold = hold

        commit(before + storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo"))
        val refreshing = viewModel.ready
        hold.complete(Unit)

        // The new figures arrive with the list already on screen, not with a blank one.
        assertEquals(plain("93.700"), refreshing.category.spent)
        assertEquals(40, (refreshing.expenses as ExpenseListState.Rows).rows.size)
        assertEquals(
            ExpenseRow(uuid(900), LocalDate.of(2026, 10, 23), "Nuevo", plain("48.700"), false),
            viewModel.rows.rows.first()
        )
        assertEquals(40 to true, viewModel.rows.rows.size to viewModel.rows.more)
        assertEquals(46, viewModel.ready.category.expenseCount)
    }

    @Test
    fun anotherMonthOrCategoryStartsAgainFromItsFirstPage() = runTest {
        val viewModel = started()
        commit(many(45) + List(25) { storedExpense(500 + it, 2, "2026-10-05", 2_000) })
        show(viewModel, 1)
        viewModel.onAction(CategoryDetailAction.ShowMore)

        show(viewModel, 2)
        val other = viewModel.ready
        snapshots.ready(
            monthOf(
                "2026-10-23",
                listOf(food, coffee),
                listOf(storedExpense(700, 2, "2026-09-30", 3_000)),
                september
            )
        )
        reads.stored[storedExpense(700, 2, "2026-09-30", 3_000).id] =
            storedExpense(700, 2, "2026-09-30", 3_000)
        session.select(september)

        assertEquals("Café", other.category.name)
        assertEquals(
            20 to true,
            (other.expenses as ExpenseListState.Rows).let {
                it.rows.size to
                    it.more
            }
        )
        assertEquals(plain("50.000"), other.category.spent)
        assertEquals(september, viewModel.ready.month)
        assertEquals(listOf(uuid(700)), viewModel.rows.rows.map { it.id })
        // A finished month takes no new expense from here.
        assertEquals(false, viewModel.ready.canRecord)
    }

    @Test
    fun aFailedRefreshShowsOlderFiguresAsOlderAndWithNoneItIsFailed() = runTest {
        val viewModel = started()
        val failure = SnapshotFailure.Storage(StorageFailure.Unavailable("SQLiteException"))
        show(viewModel, 1)
        val loading = viewModel.state.value

        snapshots.of(october).value = SnapshotState.Failed(failure, lastGood = null)
        val failed = viewModel.state.value
        viewModel.onAction(CategoryDetailAction.Retry)
        commit(many(2))
        val read = viewModel.ready
        snapshots.of(october).value =
            SnapshotState.Failed(failure, lastGood = monthOf("2026-10-23", listOf(food), many(2)))

        assertEquals(CategoryDetailState.Loading, loading)
        assertEquals(CategoryDetailState.Failed, failed)
        assertEquals(false, read.stale)
        assertEquals(true, viewModel.ready.stale)
        assertEquals(plain("2.000"), viewModel.ready.category.spent)
        assertEquals(2, viewModel.rows.rows.size)
    }

    @Test
    fun aCategoryWithNothingInTheMonthOrAnIdentifierThatIsNotOneIsGone() = runTest {
        val viewModel = started()
        commit(many(1))

        show(viewModel, 9)
        val absent = viewModel.state.value
        viewModel.onAction(CategoryDetailAction.Show("' OR 1=1 --"))

        assertEquals(CategoryDetailState.Gone, absent)
        assertEquals(CategoryDetailState.Gone, viewModel.state.value)
        assertEquals(0, reads.pages.size)
    }

    @Test
    fun anArchivedCategoryTakesNoNewExpenseAndALaterDatedOneIsMarkedAsNotCounted() = runTest {
        val viewModel = started()
        val archived = categoryIn(3, "Vieja", 10_000, archived = true)
        val rows = listOf(
            storedExpense(100, 3, "2026-10-20", 4_000),
            // Possible only if the day went backwards after it was saved.
            storedExpense(101, 3, "2026-10-28", 1_000)
        )
        rows.forEach { reads.stored[it.id] = it }
        snapshots.ready(monthOf("2026-10-23", listOf(archived), rows))

        show(viewModel, 3)

        assertEquals(false, viewModel.ready.canRecord)
        assertEquals(plain("4.000"), viewModel.ready.category.spent)
        assertEquals(listOf(true, false), viewModel.rows.rows.map { it.notCountedYet })
    }

    /** Ten pages of Alimentación listed out of 230, then one more expense saved. */
    private fun TestScope.deepListThenASave(
        hold: CompletableDeferred<Unit>?
    ): CategoryDetailViewModel {
        val viewModel = started()
        commit(many(230))
        show(viewModel, 1)
        repeat(9) { viewModel.onAction(CategoryDetailAction.ShowMore) }
        reads.pages.clear()
        reads.hold = hold
        commit(many(230) + storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo"))
        return viewModel
    }

    @Test
    fun whileADeepListIsReadAgainItsOldRowsStayAllThereAndAreMarkedAsBeingRefreshed() = runTest {
        val hold = CompletableDeferred<Unit>()
        val viewModel = deepListThenASave(hold)

        val during = viewModel.ready
        val listed = during.expenses as ExpenseListState.Rows

        // The header is already of the new revision; the rows under it are the old ones.
        assertEquals(231, during.category.expenseCount)
        assertEquals(200, listed.rows.size)
        assertEquals(false, listed.rows.any { it.id == uuid(900) })
        assertEquals(true, listed.refreshing)
        assertEquals(false, listed.loadingMore)
    }

    @Test
    fun aDeepListIsNeverShorterWhileItIsReadAgainAndEndsWholeAndCurrent() = runTest {
        val viewModel = started()
        val sizes = mutableListOf<Int>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.state.collect { state ->
                val rows = (state as? CategoryDetailState.Ready)?.expenses as? ExpenseListState.Rows
                if (rows != null) sizes += rows.rows.size
            }
        }
        commit(many(230))
        show(viewModel, 1)
        repeat(9) { viewModel.onAction(CategoryDetailAction.ShowMore) }
        sizes.clear()
        reads.pages.clear()

        // Every read waits for the test, so each state in between is really published.
        var hold = CompletableDeferred<Unit>()
        reads.hold = hold
        commit(many(230) + storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo"))
        repeat(12) {
            val previous = hold
            hold = CompletableDeferred()
            reads.hold = hold
            previous.complete(Unit)
        }
        reads.hold = null
        hold.complete(Unit)

        assertEquals("$sizes", true, sizes.all { it == 200 })
        val rows = viewModel.rows
        assertEquals(
            listOf(200, true, false, false),
            listOf(rows.rows.size, rows.more, rows.refreshing, rows.loadingMore)
        )
        assertEquals(uuid(900), rows.rows.first().id)
        assertEquals(200, rows.rows.map { it.id }.toSet().size)
    }

    @Test
    fun readingADeepListAgainAsksForItInAsFewReadsAsStorageAllows() = runTest {
        deepListThenASave(hold = null)

        // 200 rows were listed and one read may ask for at most 200.
        assertEquals(listOf<Pair<ExpenseCursor?, Int>>(null to 200), reads.pages)
    }

    @Test
    fun aListLongerThanOneReadMayHoldIsReadAgainInTwo() = runTest {
        val viewModel = started()
        commit(many(260))
        show(viewModel, 1)
        repeat(11) { viewModel.onAction(CategoryDetailAction.ShowMore) }
        reads.pages.clear()

        commit(many(260) + storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo"))

        // 240 rows listed: 200, then the 40 after them.
        assertEquals(listOf(200, 40), reads.pages.map { it.second })
        assertEquals(null, reads.pages[0].first)
        assertEquals(240 to true, viewModel.rows.rows.size to viewModel.rows.more)
        assertEquals(240, viewModel.rows.rows.map { it.id }.toSet().size)
    }

    @Test
    fun askingForMoreWhileTheListIsReadAgainAsksNothing() = runTest {
        val hold = CompletableDeferred<Unit>()
        val viewModel = deepListThenASave(hold)

        viewModel.onAction(CategoryDetailAction.ShowMore)
        viewModel.onAction(CategoryDetailAction.ShowMore)
        hold.complete(Unit)

        assertEquals(200 to true, viewModel.rows.rows.size to viewModel.rows.more)
        assertEquals(1, reads.pages.size)
    }

    @Test
    fun aDeepListThatCannotBeReadAgainIsFailedNotOldRowsAndRetryReadsAllOfItAgain() = runTest {
        val viewModel = deepListThenASave(hold = null)
        reads.pages.clear()
        reads.failure = StorageFailure.Unavailable("SQLiteException")

        commit(
            many(230) + storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo") +
                storedExpense(901, 1, "2026-10-23", 1_300, "Otro")
        )
        val failed = viewModel.ready
        reads.failure = null
        viewModel.onAction(CategoryDetailAction.Retry)

        assertEquals(ExpenseListState.Failed, failed.expenses)
        assertEquals(232, failed.category.expenseCount)
        assertEquals(200 to false, viewModel.rows.rows.size to viewModel.rows.refreshing)
        assertEquals(uuid(901), viewModel.rows.rows.first().id)
        assertEquals(listOf(200, 200), reads.pages.map { it.second })
    }

    @Test
    fun anotherCategoryOpenedWhileAListIsReadAgainNeverShowsThatListsRows() = runTest {
        val hold = CompletableDeferred<Unit>()
        val viewModel = started()
        commit(many(45) + List(3) { storedExpense(500 + it, 2, "2026-10-05", 2_000) })
        show(viewModel, 1)
        viewModel.onAction(CategoryDetailAction.ShowMore)
        reads.hold = hold
        commit(many(46) + List(3) { storedExpense(500 + it, 2, "2026-10-05", 2_000) })

        show(viewModel, 2)
        val opening = viewModel.ready
        hold.complete(Unit)

        assertEquals("Café", opening.category.name)
        assertEquals(ExpenseListState.Loading, opening.expenses)
        assertEquals(listOf(uuid(502), uuid(501), uuid(500)), viewModel.rows.rows.map { it.id })
        assertEquals(false, viewModel.rows.refreshing)
    }

    @Test
    fun aSecondSaveWhileTheFirstIsStillBeingListedEndsWithBothAndNothingTwice() = runTest {
        val hold = CompletableDeferred<Unit>()
        val viewModel = deepListThenASave(hold)
        val first = storedExpense(900, 1, "2026-10-23", 48_700, "Nuevo")
        val second = storedExpense(901, 1, "2026-10-23", 1_300, "Otro")

        commit(many(230) + first + second)
        hold.complete(Unit)

        assertEquals(232, viewModel.ready.category.expenseCount)
        assertEquals(listOf(uuid(901), uuid(900)), viewModel.rows.rows.take(2).map { it.id })
        assertEquals(200 to false, viewModel.rows.rows.size to viewModel.rows.refreshing)
        assertEquals(200, viewModel.rows.rows.map { it.id }.toSet().size)
    }

    @Test
    fun afterTheHistoryIsReplacedNoRowOfTheOldOneIsShownWhileTheNewIsRead() = runTest {
        val viewModel = started()
        commit(many(45))
        show(viewModel, 1)
        val hold = CompletableDeferred<Unit>()
        reads.hold = hold

        // What a restore does: the same month and category, another history.
        val restored = listOf(storedExpense(700, 1, "2026-10-02", 9_000, "Restaurado"))
        reads.stored.clear()
        restored.forEach { reads.stored[it.id] = it }
        snapshots.ready(
            monthOf("2026-10-23", listOf(food, coffee), restored, october, generation = 2)
        )
        val during = viewModel.ready
        hold.complete(Unit)

        assertEquals(1, during.category.expenseCount)
        assertEquals(ExpenseListState.Loading, during.expenses)
        assertEquals(listOf(uuid(700)), viewModel.rows.rows.map { it.id })
    }

    // --- Deleting from the list ---

    @Test
    fun deletingAListedExpenseDeletesItAtTheVersionAndHistoryItWasListedFrom() = runTest {
        val viewModel = started()
        val expenses = listOf(
            storedExpense(100, 1, "2026-10-20", 33_500).copy(version = 4),
            storedExpense(101, 1, "2026-10-21", 20_600)
        )
        reads.stored.putAll(expenses.associateBy { it.id })
        snapshots.ready(
            monthOf("2026-10-23", listOf(food, coffee), expenses, october, generation = 6)
        )
        show(viewModel, 1)

        viewModel.onAction(CategoryDetailAction.Delete(uuid(100)))

        val sent = commands.deleted.single()
        assertEquals(
            listOf(uuid(100), 4L, 6L),
            listOf(sent.expenseId.value, sent.expectedVersion, sent.generation)
        )
        // Nothing is taken off the screen by hand: the row goes when the month is read again.
        assertEquals(listOf(uuid(101), uuid(100)), viewModel.rows.rows.map { it.id })
        assertEquals(null, viewModel.ready.deleteProblem)
    }

    @Test
    fun anExpenseThatIsNotListedOrIsAlreadyBeingDeletedAsksNothingMore() = runTest {
        val viewModel = started()
        commit(many(2))
        show(viewModel, 1)
        commands.hold = CompletableDeferred()

        viewModel.onAction(CategoryDetailAction.Delete(uuid(999)))
        viewModel.onAction(CategoryDetailAction.Delete("not an identifier"))
        viewModel.onAction(CategoryDetailAction.Delete(uuid(100)))
        viewModel.onAction(CategoryDetailAction.Delete(uuid(100)))

        assertEquals(listOf(uuid(100)), commands.deleted.map { it.expenseId.value })
    }

    @Test
    fun aDeletionThatDidNotHappenSaysWhyUntilTheNextOneIsAsked() = runTest {
        val viewModel = started()
        commit(many(2))
        show(viewModel, 1)
        val refusals = listOf(
            ExpenseRejection.ExpenseMissing to DeleteProblem.Gone,
            ExpenseRejection.VersionConflict(current = 2) to DeleteProblem.Changed,
            ExpenseRejection.StaleGeneration(current = 2) to DeleteProblem.DataChanged,
            ExpenseRejection.StorageFailed(StorageFailure.Unavailable("x")) to DeleteProblem.Failed
        )

        val said = refusals.map { (reason, _) ->
            commands.deleteAnswer = { Rejected(reason) }
            viewModel.onAction(CategoryDetailAction.Delete(uuid(100)))
            viewModel.ready.deleteProblem
        }
        commands.hold = CompletableDeferred()
        viewModel.onAction(CategoryDetailAction.Delete(uuid(101)))

        assertEquals(refusals.map { it.second }, said)
        assertEquals(null, viewModel.ready.deleteProblem)
        assertEquals(2, viewModel.rows.rows.size)
    }
}
