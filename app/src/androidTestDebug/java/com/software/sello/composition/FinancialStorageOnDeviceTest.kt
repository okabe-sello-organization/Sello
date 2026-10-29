package com.software.sello.composition

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.TestData
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.OverallBudget
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryReads
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialProfileStore
import com.software.sello.domain.port.MonthlySnapshots
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.SnapshotState
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** The installed app's own database file, as its startup left it. */
@RunWith(AndroidJUnit4::class)
class FinancialStorageOnDeviceTest {
    @Before
    fun newInstallation() = TestData.reset()

    private val koin get() = GlobalContext.get()
    private val file
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getDatabasePath("sello.db")

    private fun <T> stored(read: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(read)

    private fun SQLiteDatabase.count(table: String): Long =
        rawQuery("SELECT COUNT(*) FROM $table", null).use {
            it.moveToFirst()
            it.getLong(0)
        }

    @Test
    fun startupCreatedTheRealDatabaseWithExactlyOneProfile() {
        assertTrue(file.exists())
        val zone = stored { database ->
            assertEquals(7, database.version)
            assertEquals(1, database.count("profile"))
            database.rawQuery("SELECT financial_zone FROM profile WHERE id = 1", null).use {
                it.moveToFirst()
                it.getString(0)
            }
        }

        assertEquals(koin.get<FinancialClock>().today.value.zone, ZoneId.of(zone))
    }

    @Test
    fun theStoredZoneWinsOverADifferentDeviceZoneOnceTheAppHasStarted() = runBlocking {
        val elsewhere = ZoneId.of("Pacific/Kiritimati")
        val running = koin.get<FinancialClock>().today.value.zone
        assertNotEquals(elsewhere, running)

        val profile = koin.get<FinancialProfileStore>().establish(elsewhere)

        assertEquals(running, (profile as Outcome.Success).value.zone)
        assertEquals(1, stored { it.count("profile") })
    }

    @Test
    fun theExpenseWorkflowIsWiredToTheRealDatabaseAndAnswersWithoutWriting() = runBlocking {
        val never = OperationId.of("00000000-0000-4000-8000-000000000000") as Outcome.Success

        val found = koin.get<ExpenseCommands>().find(never.value)

        assertEquals(Outcome.Success(null), found)
        assertEquals(36, koin.get<RecordIdSource>().next().length)
    }

    @Test
    fun aNewInstallationHasNoCategoriesAndTheCategoryWorkflowIsWired() = runBlocking {
        val never = OperationId.of("00000000-0000-4000-8000-000000000000") as Outcome.Success
        val month = YearMonth.from(koin.get<FinancialClock>().today.value.date)

        val budget = koin.get<CategoryReads>().monthBudget(month) as Outcome.Success

        // No sample categories: the first one is the person's own.
        assertEquals(emptyList<Any>(), budget.value.categories)
        assertEquals(month, budget.value.month)
        assertEquals(Outcome.Success(null), koin.get<CategoryCommands>().find(never.value))
    }

    @Test
    fun theMonthlySnapshotOfANewInstallationIsEmptyAndReadInTheStoredZone() = runBlocking {
        val today = koin.get<FinancialClock>().today.value
        val snapshots = koin.get<MonthlySnapshots>()

        val read = snapshots.read(YearMonth.from(today.date)) as Outcome.Success
        val observed = snapshots.observe(YearMonth.from(today.date))
            .first { it !is SnapshotState.Loading }

        assertEquals(OverallBudget.NoLimit, read.value.overall)
        assertEquals(emptyList<Any>(), read.value.categories)
        assertEquals(today.date to today.zone, read.value.asOf to read.value.zone)
        assertEquals(SnapshotState.Ready(read.value), observed)
    }

    @Test
    fun openingStorageDiscardsWhatAnEarlierProcessKeptToUndoADeletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // What a process that ended inside the six seconds leaves behind.
        TestData.execute(
            "INSERT INTO deleted_expense VALUES ('00000000-0000-4000-8000-000000000001', 1, 0, " +
                "'00000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000003', " +
                "'2026-10-03', 1, 48700, 'COP', NULL, 1, 0, 0, NULL, NULL, NULL)"
        )

        TestData.execute(
            "INSERT INTO deleted_income VALUES ('00000000-0000-4000-8000-000000000004', 1, 0, " +
                "'00000000-0000-4000-8000-000000000005', '2026-10-03', 1, 150000, 'COP', " +
                "'salary', NULL, NULL, 1, 0, 0, NULL, NULL, NULL)"
        )

        val opened = openStorage(context, ZoneId.of("Pacific/Kiritimati"), koin.get(), koin.get())

        assertTrue("$opened", opened is Outcome.Success)
        (opened as Outcome.Success).value.storage.close()
        assertEquals(0L, stored { it.count("deleted_expense") })
        assertEquals(0L, stored { it.count("deleted_income") })
        assertEquals(0L, stored { it.count("expense") + it.count("income") })
    }

    @Test
    fun noFinancialRecordIsSeeded() {
        val tables = listOf(
            "category",
            "default_limit",
            "month_limit",
            "expense",
            "income",
            "operation_receipt",
            "deleted_expense",
            "deleted_income"
        )

        val counts = stored { database -> tables.associateWith { database.count(it) } }

        assertEquals(tables.associateWith { 0L }, counts)
    }
}
