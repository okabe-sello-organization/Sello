package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCursor
import com.software.sello.domain.port.ExpensePage
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reading back what the expense command stored, on a real database file. */
@RunWith(AndroidJUnit4::class)
class ExpenseReadsTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    private fun id(number: Int) = ExpenseId.of(uuid(number)).valueOrFail()

    @Test
    fun aSavedExpenseReadsBackExactlyAsItsReceiptSays() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        val saved = ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 999_999_999_999))
        val receipt = (saved as CommandOutcome.Committed).value

        val read = ledger.expenseReads.byId(receipt.expenseId).valueOrFail()!!

        assertEquals(receipt.expenseId, read.id)
        assertEquals(ledger.categoryId(100), read.categoryId)
        assertEquals("2026-10-03", read.date.toString())
        assertEquals(999_999_999_999, read.amount.money.minorUnits)
        assertEquals(ledger.auditMillis, read.createdAt)
        assertEquals(receipt.receipt.committedAt, read.createdAt)
        assertEquals(Outcome.Success(null), ledger.expenseReads.byId(id(999)))
    }

    @Test
    fun theLastUsedCategoryIsTheOneOfTheExpenseAddedLastNotTheLatestDate() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.categories.submit(ledger.createCategory("Transporte"))
        val none = ledger.expenseReads.lastUsedCategory()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-08", 5_000))
        // Added later, though dated earlier.
        ledger.expenses.create(ledger.createExpense(101, "2026-10-01", 7_000))

        val last = ledger.expenseReads.lastUsedCategory()
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(101), 1))

        assertEquals(Outcome.Success(null), none)
        assertEquals(Outcome.Success(ledger.categoryId(101)), last)
        // Still the answer after archiving; whether it can take an entry is the caller's check.
        assertEquals(
            Outcome.Success(ledger.categoryId(101)),
            ledger.expenseReads.lastUsedCategory()
        )
    }

    private fun page(category: Int, month: String, after: ExpenseCursor?, limit: Int) =
        runBlocking {
            ledger.expenseReads
                .inCategory(ledger.categoryId(category), YearMonth.parse(month), after, limit)
                .valueOrFail()
        }

    private fun ExpensePage.shown() = expenses.map { "${it.date} ${it.amount.money.minorUnits}" }

    /** Mercado (100) and Transporte (101) with expenses around the edges of October. */
    private fun twoCategoriesAcrossMonths() = runBlocking {
        // Late enough that none of these dates is in the future.
        ledger.goTo("2026-11-30")
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.categories.submit(ledger.createCategory("Transporte"))
        listOf(
            Triple(100, "2026-09-30", 1_000L),
            Triple(100, "2026-10-01", 2_000L),
            Triple(100, "2026-10-31", 3_000L),
            Triple(101, "2026-10-15", 4_000L),
            Triple(100, "2026-11-01", 5_000L),
            // Added last but dated in the middle, twice on the same day.
            Triple(100, "2026-10-15", 6_000L),
            Triple(100, "2026-10-15", 7_000L)
        ).forEach { (category, date, pesos) ->
            ledger.expenses.create(ledger.createExpense(category, date, pesos))
        }
    }

    @Test
    fun aCategoryMonthListsOnlyItsOwnExpensesNewestDayFirstThenLastAddedFirst() {
        twoCategoriesAcrossMonths()

        val october = page(100, "2026-10", after = null, limit = 50)

        assertEquals(
            listOf("2026-10-31 3000", "2026-10-15 7000", "2026-10-15 6000", "2026-10-01 2000"),
            october.shown()
        )
        assertNull(october.next)
        assertEquals(listOf("2026-10-15 4000"), page(101, "2026-10", null, 50).shown())
        assertEquals(listOf("2026-09-30 1000"), page(100, "2026-09", null, 50).shown())
        assertEquals(listOf("2026-11-01 5000"), page(100, "2026-11", null, 50).shown())
        assertEquals(emptyList<String>(), page(101, "2026-09", null, 50).shown())
    }

    @Test
    fun pagesFollowEachOtherWithoutRepeatingOrSkippingEvenInsideOneDay() {
        twoCategoriesAcrossMonths()

        val first = page(100, "2026-10", after = null, limit = 2)
        val second = page(100, "2026-10", after = first.next, limit = 2)

        assertEquals(listOf("2026-10-31 3000", "2026-10-15 7000"), first.shown())
        assertEquals(ExpenseCursor(LocalDate.parse("2026-10-15"), 7), first.next)
        assertEquals(listOf("2026-10-15 6000", "2026-10-01 2000"), second.shown())
        // Exactly full, and still known to be the last.
        assertNull(second.next)
        val oneByOne = generateSequence(page(100, "2026-10", null, 1)) { previous ->
            previous.next?.let { page(100, "2026-10", it, 1) }
        }.flatMap { it.shown() }.toList()
        assertEquals(first.shown() + second.shown(), oneByOne)
    }

    @Test
    fun aPageWithADamagedExpenseIsAFailureNotAShorterList() = runBlocking {
        twoCategoriesAcrossMonths()
        ledger.database.openHelper.writableDatabase
            .execSQL("UPDATE expense SET amount_minor = 0 WHERE sequence = 2")

        val read = ledger.expenseReads
            .inCategory(ledger.categoryId(100), YearMonth.of(2026, 10), null, 50)

        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("expense", uuid(103), "amount_minor")),
            read
        )
        // The damaged row is on the second page, so the first still reads.
        assertEquals(2, page(100, "2026-10", null, 2).expenses.size)
    }

    @Test
    fun aPageSizeOutsideTheAllowedRangeIsRefused() {
        listOf(0, 201).forEach { limit ->
            assertThrows(IllegalArgumentException::class.java) {
                page(100, "2026-10", null, limit)
            }
        }
    }

    @Test
    fun aDamagedExpenseIsAFailureNotAMissingOne() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 5_000))
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE expense SET amount_minor = 0")

        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("expense", uuid(101), "amount_minor")),
            ledger.expenseReads.byId(id(101))
        )
    }
}
