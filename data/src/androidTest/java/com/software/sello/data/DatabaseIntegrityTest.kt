package com.software.sello.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.data.mapper.decodeAll
import com.software.sello.data.mapper.toDomain
import com.software.sello.data.mapper.toEntity
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseIntegrityTest {
    @get:Rule
    val files = DatabaseFiles()

    private val bogota = ZoneId.of("America/Bogota")
    private val october = YearMonth.of(2026, 10)
    private val allDates = "0000-01-01" to "9999-12-32"

    private fun refused(write: suspend () -> Unit) {
        assertThrows(SQLiteConstraintException::class.java) { runBlocking { write() } }
    }

    private fun receipt(operation: Int, digest: String) = OperationReceiptEntity(
        operationId = uuid(operation),
        kind = "test.write",
        inputDigest = digest,
        generation = 1,
        revision = 1,
        subjectId = uuid(100),
        committedAt = recordedAt.toEpochMilli()
    )

    @Test
    fun aSecondCategoryWithTheSameNameIgnoringCaseAndAccentsIsRefused() = runBlocking {
        val database = files.open()
        database.categoryDao().insert(category(1, " Café ").toEntity())

        refused { database.categoryDao().insert(category(2, "cafe").toEntity()) }

        assertEquals(1, database.count("category"))
        assertEquals("Café", database.categoryDao().byId(uuid(1))!!.name)
    }

    @Test
    fun anArchivedCategoryStillHoldsItsName() = runBlocking {
        val database = files.open()
        database.categoryDao().insert(category(1, "Café", archived = true).toEntity())

        refused { database.categoryDao().insert(category(2, "CAFE").toEntity()) }

        assertEquals(1, database.count("category"))
    }

    @Test
    fun anExpenseCannotPointAtACategoryThatDoesNotExist() = runBlocking {
        val database = files.open()
        database.categoryDao().insert(category(1, "Mercado").toEntity())

        refused {
            database.expenseDao().insert(expense(10, 2, "2026-10-03", 1, 10_000).toEntity())
        }

        assertEquals(0, database.count("expense"))
    }

    @Test
    fun aLimitCannotPointAtACategoryThatDoesNotExist() = runBlocking {
        val database = files.open()
        val missing = CategoryId.of(uuid(9)).valueOrFail()

        refused {
            database.limitDao().insert(MonthLimit(missing, october, finite(100_000)).toEntity())
        }
        refused {
            database.limitDao()
                .insert(
                    DefaultLimit(
                        missing,
                        october,
                        AutomaticBudget.Limit(BudgetLimit.Unlimited)
                    ).toEntity()
                )
        }

        assertEquals(0, database.count("month_limit"))
        assertEquals(0, database.count("default_limit"))
    }

    @Test
    fun aCategoryWithHistoryCannotBeDeletedAndNothingCascades() = runBlocking {
        val database = files.open()
        val mercado = category(1, "Mercado")
        database.categoryDao().insert(mercado.toEntity())
        database.expenseDao().insert(expense(10, 1, "2026-10-03", 1, 10_000).toEntity())
        database.limitDao().insert(MonthLimit(mercado.id, october, finite(100_000)).toEntity())

        assertThrows(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL("DELETE FROM category")
        }

        assertEquals(1, database.count("category"))
        assertEquals(1, database.count("expense"))
        assertEquals(1, database.count("month_limit"))
    }

    @Test
    fun aCategoryHasOneLimitPerMonthAndOneDefaultPerEffectiveMonth() = runBlocking {
        val database = files.open()
        val mercado = category(1, "Mercado")
        database.categoryDao().insert(mercado.toEntity())
        database.limitDao().insert(MonthLimit(mercado.id, october, finite(100_000)).toEntity())
        database.limitDao().insert(
            DefaultLimit(mercado.id, october, AutomaticBudget.Limit(finite(100_000))).toEntity()
        )

        refused {
            database.limitDao().insert(MonthLimit(mercado.id, october, finite(120_000)).toEntity())
        }
        refused {
            database.limitDao()
                .insert(
                    DefaultLimit(
                        mercado.id,
                        october,
                        AutomaticBudget.Limit(BudgetLimit.Unlimited)
                    ).toEntity()
                )
        }

        val month = database.limitDao().forMonth("2026-10", "", 10).decodeAll { it.toDomain() }
        val defaults = database.limitDao().defaults(uuid(1), 10).decodeAll { it.toDomain() }
        assertEquals(
            Outcome.Success(listOf(MonthLimit(mercado.id, october, finite(100_000)))),
            month
        )
        assertEquals(
            Outcome.Success(
                listOf(DefaultLimit(mercado.id, october, AutomaticBudget.Limit(finite(100_000))))
            ),
            defaults
        )
    }

    @Test
    fun aSecondReceiptForTheSameOperationIsRefusedAndTheFirstIsKept() = runBlocking {
        val database = files.open()
        database.operationReceiptDao().insert(receipt(50, "first"))

        refused { database.operationReceiptDao().insert(receipt(50, "second")) }

        assertEquals(1, database.count("operation_receipt"))
        assertEquals("first", database.operationReceiptDao().find(uuid(50))!!.inputDigest)
        assertNull(database.operationReceiptDao().find(uuid(51)))
    }

    @Test
    fun twoRecordsCannotShareAnOrderingSequence() = runBlocking {
        val database = files.open()
        database.categoryDao().insert(category(1, "Mercado").toEntity())
        database.expenseDao().insert(expense(10, 1, "2026-10-03", 7, 10_000).toEntity())
        database.incomeDao()
            .insert(income(20, "2026-10-03", 7, 50_000, IncomeSource.Salary).toEntity())

        refused {
            database.expenseDao().insert(expense(11, 1, "2026-10-04", 7, 20_000).toEntity())
        }
        refused {
            database.incomeDao()
                .insert(income(21, "2026-10-04", 7, 60_000, IncomeSource.Transfer).toEntity())
        }

        assertEquals(1, database.count("expense"))
        assertEquals(1, database.count("income"))
    }

    @Test
    fun expensesAreReadInDateThenSequenceOrderInBoundedPages() = runBlocking {
        val database = files.open()
        database.categoryDao().insert(category(1, "Mercado").toEntity())
        // Inserted out of order, with a backdated record that was added last.
        val written = listOf(
            expense(10, 1, "2026-10-02", 2, 15_000),
            expense(11, 1, "2026-10-03", 3, 5_000),
            expense(12, 1, "2026-10-01", 1, 10_000),
            expense(13, 1, "2026-11-01", 4, 99_000),
            expense(14, 1, "2026-10-02", 5, 7_000),
            expense(15, 1, "2026-09-30", 6, 1_000)
        )
        written.forEach { database.expenseDao().insert(it.toEntity()) }

        val first = database.expenseDao().page("2026-10-01", "2026-10-31", "", 0, 3)
        val last = first.last()
        val second = database.expenseDao()
            .page("2026-10-01", "2026-10-31", last.effectiveDate, last.sequence, 3)

        assertEquals(listOf(uuid(12), uuid(10), uuid(14)), first.map { it.id })
        assertEquals(listOf(uuid(11)), second.map { it.id })
    }

    @Test
    fun everyRecordReadsBackExactlyAfterTheFileIsClosedAndReopened() = runBlocking {
        val mercado = category(1, "Café Ñandú", archived = true)
        val largest = expense(10, 1, "2024-02-29", 1, 999_999_999_999, "Año nuevo 🎉")
        val smallest = expense(11, 1, "2026-10-09", 2, 1)
        val other = SourceName.of("Venta de garaje").valueOrFail()
        val incomes = listOf(
            income(20, "2026-10-01", 1, 999_999_999_999, IncomeSource.Salary),
            income(21, "2026-10-02", 2, 1, IncomeSource.Other(other))
        )
        val monthLimits = listOf(MonthLimit(mercado.id, october, finite(0)))
        val defaults = listOf(
            DefaultLimit(
                mercado.id,
                YearMonth.of(2026, 9),
                AutomaticBudget.Limit(finite(Long.MAX_VALUE))
            ),
            DefaultLimit(mercado.id, october, AutomaticBudget.Limit(BudgetLimit.Unlimited))
        )
        val first = files.open()
        val profile = RoomFinancialProfileStore(first) { recordedAt }.establish(bogota)
        first.categoryDao().insert(mercado.toEntity())
        first.expenseDao().insert(largest.toEntity())
        first.expenseDao().insert(smallest.toEntity())
        incomes.forEach { first.incomeDao().insert(it.toEntity()) }
        monthLimits.forEach { first.limitDao().insert(it.toEntity()) }
        defaults.forEach { first.limitDao().insert(it.toEntity()) }
        first.operationReceiptDao().insert(receipt(50, "digest"))
        first.close()

        val reopened = files.open()
        val (from, until) = allDates

        assertEquals(Outcome.Success(FinancialProfile(bogota, 1, 0)), profile)
        assertEquals(
            profile,
            RoomFinancialProfileStore(reopened) { recordedAt }.establish(ZoneId.of("Asia/Tokyo"))
        )
        assertEquals(
            Outcome.Success(listOf(mercado)),
            reopened.categoryDao().page("", 10).decodeAll { it.toDomain() }
        )
        assertEquals(
            Outcome.Success(listOf(largest, smallest)),
            reopened.expenseDao().page(from, until, "", 0, 10).decodeAll { it.toDomain() }
        )
        assertEquals(
            Outcome.Success(incomes),
            reopened.incomeDao().page(from, until, "", 0, 10).decodeAll { it.toDomain() }
        )
        assertEquals(
            Outcome.Success(monthLimits),
            reopened.limitDao().forMonth("2026-10", "", 10).decodeAll { it.toDomain() }
        )
        assertEquals(
            Outcome.Success(defaults),
            reopened.limitDao().defaults(uuid(1), 10).decodeAll { it.toDomain() }
        )
        assertEquals(receipt(50, "digest"), reopened.operationReceiptDao().find(uuid(50)))
    }

    @Test
    fun receiptsAndTheGenerationOutliveTheFinancialRowsTheyDescribe() = runBlocking {
        val database = files.open()
        val store = RoomFinancialProfileStore(database) { recordedAt }
        store.establish(bogota)
        database.categoryDao().insert(category(1, "Mercado").toEntity())
        database.expenseDao().insert(expense(100, 1, "2026-10-03", 1, 10_000).toEntity())
        database.operationReceiptDao().insert(receipt(50, "digest"))

        // What a replacement of the whole history removes: every financial row.
        database.openHelper.writableDatabase.apply {
            execSQL("DELETE FROM expense")
            execSQL("DELETE FROM category")
        }

        assertEquals(receipt(50, "digest"), database.operationReceiptDao().find(uuid(50)))
        assertEquals(
            Outcome.Success(FinancialProfile(bogota, 1, 0)),
            store.establish(ZoneId.of("Asia/Tokyo"))
        )
    }
}
