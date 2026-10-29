package com.software.sello.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.local.Migration6To7
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.data.repository.RoomCarryoverReads
import com.software.sello.data.repository.RoomCategoryCommands
import com.software.sello.data.repository.RoomExpenseCommands
import com.software.sello.data.repository.RoomMonthlySnapshots
import com.software.sello.data.repository.UndoWindow
import com.software.sello.domain.model.BudgetStanding
import com.software.sello.domain.model.LimitedBudget
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.PastVerdict
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CommittedExpense
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading a version 6 history to version 7, which adds signed carryover's chains (ADR
 * 0020). The history is SC01's (docs/testing/signed-carryover-examples.md): Comida with
 * base 100.000, spent 20.000 in ene, 70.000 in feb and 250.000 in mar 2027, written as
 * version 6 stored it, beside one row of every other table. Expected values are SC42's:
 * an upgraded history has every category off and no starting balance is guessed.
 */
@RunWith(AndroidJUnit4::class)
class CarryoverMigrationTest {
    private val name = "carryover-migration.db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, SelloDatabase::class.java)

    @After
    fun removeFile() {
        instrumentation.targetContext.deleteDatabase(name)
    }

    private val versionSixTables = listOf(
        "profile",
        "category",
        "default_limit",
        "month_limit",
        "expense",
        "income",
        "operation_receipt",
        "deleted_expense",
        "deleted_income",
        "goal",
        "goal_event",
        "deleted_goal_event",
        "featured_goal",
        "schedule",
        "schedule_pause",
        "investment",
        "investment_movement",
        "deleted_investment_movement",
        "usd_rate"
    )

    private fun SupportSQLiteDatabase.rows() = versionSixTables.associateWith { table ->
        query("SELECT * FROM $table ORDER BY 1").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        (0 until cursor.columnCount).joinToString("|") {
                            cursor.getString(it) ?: "null"
                        }
                    )
                }
            }
        }
    }

    private val comida = uuid(1)
    private val zone = ZoneId.of("America/Bogota")

    /** The command that recorded ene's 20.000, as version 6 committed it. */
    private val recordedInEne = CreateExpense(
        operationId = operationIdOf(9),
        generation = 4,
        categoryId = categoryIdOf(1),
        amount = TransactionAmount.of(Money.cop(20_000)).valueOrFail(),
        date = LocalDate.parse("2027-01-10"),
        note = Note.of(null).valueOrFail()
    )

    /** Its identity as version 6 stored it, printed by the code on `main` at 792b247. */
    private val recordedInEneDigest =
        "f9637361ad0b0c1215ac58986b48c9bab890d99d43908a047449b35a0c270ae1"

    /** SC01's history as version 6 stored it, and one row of every other table. */
    private fun versionSixWithHistory(): Map<String, List<String>> =
        helper.createDatabase(name, 6).use { db ->
            db.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 19, 0)"
            )
            db.execSQL(
                "INSERT INTO category VALUES ('$comida', 'Comida', 'comida', 'home', 0, 3, 10, 20)"
            )
            db.execSQL(
                "INSERT INTO default_limit VALUES ('$comida', '2027-01', 'finite', 100000, 'COP')"
            )
            db.execSQL(
                "INSERT INTO month_limit VALUES ('$comida', '2027-02', 'finite', 100000, 'COP')"
            )
            for ((number, date, pesos) in listOf(
                Triple(2, "2027-01-10", 20_000),
                Triple(3, "2027-02-10", 70_000),
                Triple(4, "2027-03-10", 250_000)
            )) {
                db.execSQL(
                    "INSERT INTO expense VALUES ('${uuid(number)}', '$comida', '$date', $number, " +
                        "$pesos, 'COP', NULL, 1, 30, 40, NULL, NULL, NULL)"
                )
            }
            db.execSQL(
                "INSERT INTO income VALUES ('${uuid(5)}', '2027-03-01', 1, 1600000, 'COP', " +
                    "'salary', NULL, NULL, 1, 30, 40, NULL, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(9)}', 'expense.create', " +
                    "'$recordedInEneDigest', 4, 17, '${uuid(2)}', 50)"
            )
            db.execSQL(
                "INSERT INTO deleted_expense VALUES ('${uuid(10)}', 4, 50, '${uuid(6)}', " +
                    "'$comida', '2027-03-02', 6, 5000, 'COP', NULL, 1, 30, 40, NULL, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO deleted_income VALUES ('${uuid(11)}', 4, 50, '${uuid(7)}', " +
                    "'2027-03-02', 2, 10000, 'COP', 'salary', NULL, NULL, 1, 30, 40, " +
                    "NULL, NULL, NULL)"
            )
            db.execSQL(
                "INSERT INTO goal VALUES ('${uuid(12)}', 'Viaje', 'flight', NULL, NULL, " +
                    "2, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO goal_event VALUES ('${uuid(13)}', '${uuid(12)}', 0, 'target', " +
                    "3000000, 'COP', NULL, '2027-01-02', NULL, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO deleted_goal_event VALUES ('${uuid(14)}', 4, 50, '${uuid(15)}', " +
                    "'${uuid(12)}', 1, 'contribution', 1000, 'COP', '2027-01-03', " +
                    "'2027-01-03', NULL, 1, 30, 40)"
            )
            db.execSQL("INSERT INTO featured_goal VALUES (1, '${uuid(12)}')")
            db.execSQL(
                "INSERT INTO schedule VALUES ('${uuid(16)}', 'payment', '$comida', NULL, " +
                    "NULL, 30000, 'COP', NULL, 'monthly', 5, NULL, '2027-01-05', NULL, 0, " +
                    "'2027-03-05', 1, 1, 30, 40)"
            )
            db.execSQL("INSERT INTO schedule_pause VALUES ('${uuid(16)}', '2027-03-07', NULL)")
            db.execSQL(
                "INSERT INTO investment VALUES ('${uuid(17)}', 'CDT', NULL, 'savings', 'COP', " +
                    "1, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO investment_movement VALUES ('${uuid(18)}', '${uuid(17)}', 0, " +
                    "'contribution', 500000, 'COP', '2027-01-04', NULL, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO deleted_investment_movement VALUES ('${uuid(19)}', 4, 50, " +
                    "'${uuid(20)}', '${uuid(17)}', 1, 'withdrawal', 1000, 'COP', '2027-01-05', " +
                    "NULL, 1, 30, 40)"
            )
            db.execSQL(
                "INSERT INTO usd_rate VALUES ('${uuid(21)}', 410000, '2027-01-02', 0, 30)"
            )
            db.rows()
        }

    @Test
    fun upgradingFromVersionSixKeepsEveryRowAndAddsOnlyAnEmptyChainTable() {
        val before = versionSixWithHistory()

        // Room compares the result with the exported version 7 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 7, true, Migration6To7).use { upgraded ->
            val chains = upgraded.query("SELECT COUNT(*) FROM carry_chain").use {
                it.moveToFirst()
                it.getInt(0)
            }

            assertEquals(19, before.values.count { rows -> rows.isNotEmpty() })
            assertEquals(before, upgraded.rows())
            assertEquals(0, chains)
        }
    }

    @Test
    fun anUpgradedHistoryCarriesNothingKeepsItsReceiptsAndCanStillTurnCarryOn() = runBlocking {
        versionSixWithHistory()
        val day = MutableStateFlow(FinancialDay(LocalDate.parse("2027-04-10"), zone))
        val clock = object : FinancialClock {
            override val today = day
        }
        val audit = Instant.parse("2027-04-10T15:00:00Z")

        val database = openSelloDatabase(instrumentation.targetContext, Dispatchers.IO, name)
        val version = database.openHelper.readableDatabase.version
        val snapshots = RoomMonthlySnapshots(database, clock, Dispatchers.Default)
        val march = snapshots.read(YearMonth.of(2027, 3)).valueOrFail()
        val april = snapshots.read(YearMonth.of(2027, 4)).valueOrFail()
        val history = RoomCarryoverReads(database, clock, Dispatchers.Default)
            .history(categoryIdOf(1), YearMonth.of(2027, 4)).valueOrFail()
        val expenses = RoomExpenseCommands(
            database,
            { audit },
            clock,
            { error("A replay takes no identifier") },
            UndoWindow { 5.hours }
        )
        val replayed = expenses.create(recordedInEne)
        val enabled = RoomCategoryCommands(database, { audit }, clock, { uuid(99) })
            .submit(EnableCarry(operationIdOf(30), 4, categoryIdOf(1), 3))
        val afterEnabling = snapshots.read(YearMonth.of(2027, 4)).valueOrFail()
        val chains = database.openHelper.readableDatabase
            .query("SELECT category_id, start_month, end_month FROM carry_chain").use {
                buildList {
                    while (it.moveToNext()) {
                        add(
                            "${it.getString(0)}|${it.getString(1)}|${it.getString(2)}"
                        )
                    }
                }
            }
        database.close()

        assertEquals(7, version)
        // SC42: every category is off. mar R −150.000; abr C 100.000, not 60.000.
        assertEquals(
            LimitedBudget(
                limit = Money.cop(100_000),
                spent = Money.cop(250_000),
                remaining = Money.cop(-150_000),
                standing = BudgetStanding.Past(PastVerdict.Over)
            ),
            march.categories.single().budget
        )
        assertEquals(Money.cop(100_000), april.categories.single().budget?.limit)
        assertEquals(Money.cop(100_000), april.categories.single().budget?.remaining)
        assertEquals(null, april.categories.single().budget?.carry)
        assertEquals(emptyList<Any>(), history.chains)
        assertEquals(emptyList<Any>(), history.replay.months)
        // The receipt version 6 committed still answers the same command, unchanged.
        assertEquals(
            ExpenseCommandOutcome.Committed(
                CommittedExpense(
                    OperationReceipt(
                        operationIdOf(9),
                        OperationKind.CreateExpense,
                        generation = 4,
                        revision = 17,
                        committedAt = Instant.ofEpochMilli(50)
                    ),
                    expenseIdOf(2)
                ),
                replayed = true
            ),
            replayed
        )
        // CP2: turned on in abr, the chain starts there at 0, whatever came before.
        assertTrue("$enabled", enabled is CategoryCommandOutcome.Committed)
        assertEquals(listOf("$comida|2027-04|null"), chains)
        assertEquals(Money.cop(0), afterEnabling.categories.single().budget?.carry?.carryIn)
        assertEquals(Money.cop(100_000), afterEnabling.categories.single().budget?.limit)
    }
}
