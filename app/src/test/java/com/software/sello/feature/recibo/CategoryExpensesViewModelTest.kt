package com.software.sello.feature.recibo

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CategoryExpenseSearch
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ExpenseSearchPage
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.categoryIn
import com.software.sello.feature.expense.monthOf
import com.software.sello.feature.expense.storedExpense
import com.software.sello.feature.expense.uuid
import com.software.sello.feature.goals.HandGoalCommands
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.HandFinancialClock
import com.software.sello.navigation.HandMonotonicClock
import com.software.sello.navigation.MonthSession
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.testing.HandSnapshots
import com.software.sello.testing.testMoney
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryExpensesViewModelTest {
    private val month = YearMonth.of(2026, 10)
    private val snapshots = HandSnapshots()
    private val commands = HandExpenseCommands()
    private val rows = List(45) { storedExpense(100 + it, 1, "2026-10-01", 1_000, "Compra $it") }
    private val requests = mutableListOf<Triple<ExpenseOrder, String, Int>>()
    private var failure = false
    private var replyRevision = 1L
    private var hold: CompletableDeferred<Unit>? = null
    private var nextOperation = 900
    private val search = object : CategoryExpenseSearch {
        override suspend fun read(
            categoryId: CategoryId,
            month: YearMonth,
            order: ExpenseOrder,
            query: String,
            offset: Int,
            limit: Int
        ): Outcome<ExpenseSearchPage, StorageFailure> {
            requests += Triple(order, query, limit)
            hold?.await()
            if (failure) return Outcome.Failure(StorageFailure.Unavailable("read failed"))
            val found = if (query.isEmpty()) {
                rows
            } else {
                rows.filter {
                    it.note!!.value.contains(query)
                }
            }
            return Outcome.Success(
                ExpenseSearchPage(
                    found.drop(offset).take(limit),
                    found.size > offset + limit,
                    1,
                    replyRevision,
                    found.size.toLong()
                )
            )
        }
    }

    @Before fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun reset() = Dispatchers.resetMain()

    private fun TestScope.started(
        saved: SavedStateHandle = SavedStateHandle()
    ): CategoryExpensesViewModel {
        snapshots.ready(monthOf("2026-10-22", listOf(categoryIn(1, "Comida", 200_000)), rows))
        val corrections =
            Corrections(
                commands,
                HandIncomeCommands(),
                HandGoalCommands(),
                HandInvestmentCommands(),
                {
                    testScheduler.currentTime.milliseconds
                },
                { uuid(nextOperation++) },
                CoroutineScope(Dispatchers.Main),
                HandCategoryReads(),
                testMoney()
            )
        val model =
            CategoryExpensesViewModel(
                saved,
                MonthSession(HandFinancialClock("2026-10-22"), HandMonotonicClock()),
                snapshots,
                search,
                testMoney(),
                corrections
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
        model.onAction(CategoryExpensesAction.Show(uuid(1)))
        runCurrent()
        return model
    }
    private val CategoryExpensesViewModel.ready get() = state.value as CategoryExpensesState.Ready

    @Test
    fun aRefreshKeepsAllLoadedRowsWhileTheirReplacementIsRead() = runTest {
        val model = started()
        model.onAction(CategoryExpensesAction.More)
        runCurrent()
        assertEquals(40, model.ready.rows.size)
        hold = CompletableDeferred()
        replyRevision = 2
        snapshots.ready(
            monthOf("2026-10-22", listOf(categoryIn(1, "Comida", 200_000)), rows).copy(revision = 2)
        )
        runCurrent()
        assertTrue(
            "A refresh must keep the browser's existing rows",
            model.state.value is CategoryExpensesState.Ready
        )
        assertTrue(model.ready.busy)
        assertEquals(40, model.ready.rows.size)
        hold!!.complete(Unit)
        runCurrent()
        assertEquals(40, model.ready.rows.size)
    }

    @Test
    fun browsingUsesBoundedPagesAndSearchReachesBeyondTheInitiallyLoadedRows() = runTest {
        val model = started()
        assertEquals(20, model.ready.rows.size)
        model.onAction(CategoryExpensesAction.Search("Compra 44"))
        runCurrent()
        assertEquals(listOf(uuid(144)), model.ready.rows.map { it.id })
        assertEquals(Triple(ExpenseOrder.Date, "Compra 44", 20), requests.last())
        model.onAction(CategoryExpensesAction.Sort(ExpenseOrder.Amount))
        runCurrent()
        assertEquals(ExpenseOrder.Amount, requests.last().first)
    }

    @Test
    fun aSecondMoreTapWhileReadingAsksForOnlyOneAdditionalPage() = runTest {
        val model = started()
        hold = CompletableDeferred()
        model.onAction(CategoryExpensesAction.More)
        runCurrent()
        assertTrue(model.ready.busy)
        model.onAction(CategoryExpensesAction.More)
        hold!!.complete(Unit)
        runCurrent()
        assertEquals(40, model.ready.rows.size)
        assertEquals(2, requests.size)
        assertEquals(40, requests.last().third)
    }

    @Test
    fun pageFailureKeepsItsRowsAndRetriesWithoutSkippingAndNewSearchFailureDoesNotReuseThem() =
        runTest {
            val model = started()
            failure = true
            model.onAction(CategoryExpensesAction.More)
            runCurrent()
            assertEquals(20, model.ready.rows.size)
            assertTrue(model.ready.pageFailed)
            failure = false
            model.onAction(CategoryExpensesAction.Retry)
            runCurrent()
            assertEquals(40, model.ready.rows.size)
            failure = true
            model.onAction(CategoryExpensesAction.Search("another query"))
            runCurrent()
            assertEquals(CategoryExpensesState.Failed, model.state.value)
        }

    @Test
    fun restoredFiltersAreApplied() = runTest {
        val saved = SavedStateHandle(
            mapOf(
                "expenseSearch" to "Compra 44",
                "expenseOrder" to ExpenseOrder.Name.name
            )
        )
        val model = started(saved)
        assertEquals(Triple(ExpenseOrder.Name, "Compra 44", 20), requests.last())
        assertEquals(1, model.ready.rows.size)
    }

    @Test
    fun anOverlongSearchIsCutToItsFirstSixtyCharacters() = runTest {
        val model = started()
        val pasted = "abcdefghij".repeat(7)

        model.onAction(CategoryExpensesAction.Search(pasted))
        runCurrent()

        assertEquals(pasted.take(60), model.query.value)
        assertEquals(60, model.query.value.length)
        assertEquals(pasted.take(60), requests.last().second)
    }

    @Test
    fun theCutNeverSplitsACharacterThatTakesTwoUnits() = runTest {
        val model = started()
        // An emoji (two UTF-16 units) sits across the limit: positions 60 and 61.
        val pasted = "a".repeat(59) + "\uD83D\uDE00" + "bbb"

        model.onAction(CategoryExpensesAction.Search(pasted))
        runCurrent()

        assertEquals("a".repeat(59), model.query.value)
        assertEquals("a".repeat(59), requests.last().second)
    }

    @Test
    fun deletionUsesTheVersionAndGenerationOfTheListedExpense() = runTest {
        val model = started()
        model.onAction(CategoryExpensesAction.Delete(uuid(100)))
        runCurrent()
        assertEquals(1, commands.deleted.size)
        assertEquals(1L, commands.deleted.single().generation)
        assertEquals(1L, commands.deleted.single().expectedVersion)
        assertEquals(uuid(100), commands.deleted.single().expenseId.value)
    }
}
