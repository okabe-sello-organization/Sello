package com.software.sello.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.local.Migration1To2
import com.software.sello.data.local.Migration2To3
import com.software.sello.data.local.Migration3To4
import com.software.sello.data.local.Migration4To5
import com.software.sello.data.local.Migration5To6
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Outcome
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The migration harness. It builds a database from a committed
 * `data/schemas/.../N.json` alone, then upgrades or opens that file with this build.
 * Room refuses the file if the exported schema and the entities disagree. Each later
 * version adds its migration and its upgrade cases here.
 */
@RunWith(AndroidJUnit4::class)
class ExportedSchemaOnDeviceTest {
    private val name = "schema-harness.db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, SelloDatabase::class.java)

    @After
    fun removeFile() {
        instrumentation.targetContext.deleteDatabase(name)
    }

    private val versionOneTables =
        listOf(
            "profile",
            "category",
            "default_limit",
            "month_limit",
            "expense",
            "income",
            "operation_receipt"
        )

    private fun SupportSQLiteDatabase.everyRow(): Map<String, List<String>> =
        versionOneTables.associateWith { table ->
            query("SELECT * FROM $table").use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            (0 until cursor.columnCount).joinToString("|") {
                                cursor.getString(it)
                                    ?: "null"
                            }
                        )
                    }
                }
            }
        }

    /**
     * A version 1 database holding one of every kind of row a person could have by
     * then. Returns what it held, read before the file was closed.
     */
    private fun versionOneWithHistory(): Map<String, List<String>> =
        helper.createDatabase(name, 1).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
            exported.execSQL(
                "INSERT INTO category VALUES ('${uuid(
                    1
                )}', 'Mercado', 'mercado', 'shopping_cart', 0, 3, 10, 20)"
            )
            exported.execSQL(
                "INSERT INTO default_limit VALUES ('${uuid(
                    1
                )}', '2026-09', 'finite', 300000, 'COP')"
            )
            exported.execSQL(
                "INSERT INTO month_limit VALUES ('${uuid(1)}', '2026-10', 'unlimited', NULL, 'COP')"
            )
            exported.execSQL(
                "INSERT INTO expense VALUES ('${uuid(2)}', '${uuid(1)}', '2026-10-03', 5, 48700, " +
                    "'COP', 'Pan', 2, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(9)}', 'expense.create', " +
                    "'${"ab".repeat(32)}', 4, 17, '${uuid(2)}', 50)"
            )
            exported.everyRow()
        }

    @Test
    fun upgradingFromVersionOneKeepsEveryRowAndAddsOnlyTheEmptyTableForDeletedExpenses() {
        val before = versionOneWithHistory()

        // Room compares the result with the exported version 2 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 2, true, Migration1To2).use { upgraded ->
            val kept = upgraded.query("SELECT COUNT(*) FROM deleted_expense").use { count ->
                count.moveToFirst()
                count.getInt(0)
            }

            assertEquals(6, before.values.sumOf { rows -> rows.size })
            assertEquals(before, upgraded.everyRow())
            assertEquals(0, kept)
        }
    }

    @Test
    fun upgradingFromVersionTwoKeepsEveryRowAndAddsOnlyTheEmptyTableForDeletedIncome() {
        val tables = versionOneTables + "deleted_expense"
        fun SupportSQLiteDatabase.rowsOf() = tables.associateWith { table ->
            query("SELECT * FROM $table").use { cursor ->
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
        val before = helper.createDatabase(name, 2).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
            exported.execSQL(
                "INSERT INTO income VALUES ('${uuid(3)}', '2026-10-01', 2, 5200000, 'COP', " +
                    "'other', 'Bono', 'Extra', 1, 30, 40)"
            )
            // What a process that ended inside the six seconds left behind.
            exported.execSQL(
                "INSERT INTO deleted_expense VALUES ('${uuid(
                    9
                )}', 4, 50, '${uuid(2)}', '${uuid(1)}', " +
                    "'2026-10-03', 5, 48700, 'COP', 'Pan', 2, 30, 40)"
            )
            exported.rowsOf()
        }

        // Room compares the result with the exported version 3 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 3, true, Migration2To3).use { upgraded ->
            val kept = upgraded.query("SELECT COUNT(*) FROM deleted_income").use { count ->
                count.moveToFirst()
                count.getInt(0)
            }

            assertEquals(3, before.values.sumOf { rows -> rows.size })
            assertEquals(before, upgraded.rowsOf())
            assertEquals(0, kept)
        }
    }

    @Test
    fun upgradingFromVersionThreeKeepsEveryRowAndAddsOnlyTheEmptyGoalTables() {
        val tables = versionOneTables + "deleted_expense" + "deleted_income"
        fun SupportSQLiteDatabase.rowsOf() = tables.associateWith { table ->
            query("SELECT * FROM $table").use { cursor ->
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
        val before = helper.createDatabase(name, 3).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
            exported.execSQL(
                "INSERT INTO category VALUES ('${uuid(1)}', 'Mercado', 'mercado', " +
                    "'shopping_cart', 0, 3, 10, 20)"
            )
            exported.execSQL(
                "INSERT INTO expense VALUES ('${uuid(2)}', '${uuid(1)}', '2026-10-03', 5, " +
                    "48700, 'COP', 'Pan', 2, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO income VALUES ('${uuid(3)}', '2026-10-01', 2, 5200000, 'COP', " +
                    "'other', 'Bono', 'Extra', 1, 30, 40)"
            )
            // What a process that ended inside the six seconds left behind.
            exported.execSQL(
                "INSERT INTO deleted_income VALUES ('${uuid(9)}', 4, 50, '${uuid(4)}', " +
                    "'2026-10-02', 3, 10000, 'COP', 'salary', NULL, NULL, 1, 30, 40)"
            )
            exported.rowsOf()
        }

        // Room compares the result with the exported version 4 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 4, true, Migration3To4).use { upgraded ->
            val goalRows = listOf("goal", "goal_event", "deleted_goal_event", "featured_goal")
                .sumOf { table ->
                    upgraded.query("SELECT COUNT(*) FROM $table").use { count ->
                        count.moveToFirst()
                        count.getInt(0)
                    }
                }

            assertEquals(5, before.values.sumOf { rows -> rows.size })
            assertEquals(before, upgraded.rowsOf())
            assertEquals(0, goalRows)
        }
    }

    @Test
    fun upgradingFromVersionFourKeepsEveryRowAndAddsOnlyEmptySchedulesAndOrigins() {
        val withOrigin = listOf("expense", "income", "deleted_expense", "deleted_income")
        val tables = versionOneTables + "deleted_expense" + "deleted_income" + "goal" +
            "goal_event" + "deleted_goal_event" + "featured_goal"
        fun SupportSQLiteDatabase.rowsOf(columns: (String) -> String) =
            tables.associateWith { table ->
                query("SELECT ${columns(table)} FROM $table").use { cursor ->
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
        val before = helper.createDatabase(name, 4).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
            exported.execSQL(
                "INSERT INTO category VALUES ('${uuid(1)}', 'Hogar', 'hogar', 'home', 0, 3, 10, 20)"
            )
            exported.execSQL(
                "INSERT INTO expense VALUES ('${uuid(2)}', '${uuid(1)}', '2026-10-05', 5, " +
                    "950000, 'COP', 'Arriendo', 2, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO income VALUES ('${uuid(3)}', '2026-10-01', 2, 1600000, 'COP', " +
                    "'salary', NULL, NULL, 1, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO deleted_expense VALUES ('${uuid(9)}', 4, 50, '${uuid(4)}', " +
                    "'${uuid(1)}', '2026-10-02', 3, 10000, 'COP', NULL, 1, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO goal VALUES ('${uuid(
                    5
                )}', 'Viaje', 'flight', NULL, NULL, 2, 1, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO goal_event VALUES ('${uuid(6)}', '${uuid(5)}', 0, 'target', " +
                    "3000000, 'COP', NULL, '2026-10-02', NULL, 1, 30, 40)"
            )
            exported.execSQL("INSERT INTO featured_goal VALUES (1, '${uuid(5)}')")
            exported.rowsOf { "*" }
        }

        // Room compares the result with the exported version 5 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 5, true, Migration4To5).use { upgraded ->
            val kept = upgraded.rowsOf { table ->
                // The rows as they were, column for column, without the new columns.
                upgraded.query("PRAGMA table_info($table)").use { info ->
                    buildList {
                        while (info.moveToNext()) {
                            val column = info.getString(info.getColumnIndexOrThrow("name"))
                            if (!column.startsWith("origin_")) add("`$column`")
                        }
                    }
                }.joinToString(", ")
            }
            val origins = withOrigin.associateWith { table ->
                upgraded.query(
                    "SELECT COUNT(*) FROM $table WHERE origin_schedule_id IS NOT NULL OR " +
                        "origin_date IS NOT NULL OR origin_slot IS NOT NULL"
                ).use { count ->
                    count.moveToFirst()
                    count.getInt(0)
                }
            }
            val scheduleRows = listOf("schedule", "schedule_pause").sumOf { table ->
                upgraded.query("SELECT COUNT(*) FROM $table").use { count ->
                    count.moveToFirst()
                    count.getInt(0)
                }
            }

            assertEquals(8, before.values.sumOf { rows -> rows.size })
            assertEquals(before, kept)
            assertEquals(withOrigin.associateWith { 0 }, origins)
            assertEquals(0, scheduleRows)
        }
    }

    @Test
    fun upgradingFromVersionFiveKeepsEveryRowAndAddsOnlyEmptyInvestmentTables() {
        val tables = versionOneTables + "deleted_expense" + "deleted_income" + "goal" +
            "goal_event" + "deleted_goal_event" + "featured_goal" + "schedule" + "schedule_pause"
        val investmentTables =
            listOf("investment", "investment_movement", "deleted_investment_movement", "usd_rate")
        fun SupportSQLiteDatabase.rows() = tables.associateWith { table ->
            query("SELECT * FROM $table").use { cursor ->
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
        val before = helper.createDatabase(name, 5).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
            exported.execSQL(
                "INSERT INTO category VALUES ('${uuid(1)}', 'Hogar', 'hogar', 'home', 0, 3, 10, 20)"
            )
            exported.execSQL(
                "INSERT INTO default_limit VALUES ('${uuid(1)}', '2026-09', 'finite', " +
                    "300000, 'COP')"
            )
            exported.execSQL(
                "INSERT INTO month_limit VALUES ('${uuid(1)}', '2026-10', 'unlimited', NULL, 'COP')"
            )
            exported.execSQL(
                "INSERT INTO expense VALUES ('${uuid(2)}', '${uuid(1)}', '2026-10-05', 5, " +
                    "950000, 'COP', 'Arriendo', 2, 30, 40, '${uuid(7)}', '2026-10-05', 1)"
            )
            exported.execSQL(
                "INSERT INTO income VALUES ('${uuid(3)}', '2026-10-01', 2, 1600000, 'COP', " +
                    "'salary', NULL, NULL, 1, 30, 40, NULL, NULL, NULL)"
            )
            exported.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(9)}', 'expense.create', " +
                    "'${"ab".repeat(32)}', 4, 17, '${uuid(2)}', 50)"
            )
            exported.execSQL(
                "INSERT INTO deleted_income VALUES ('${uuid(10)}', 4, 50, '${uuid(4)}', " +
                    "'2026-10-02', 3, 10000, 'COP', 'salary', NULL, NULL, 1, 30, 40, " +
                    "NULL, NULL, NULL)"
            )
            exported.execSQL(
                "INSERT INTO goal VALUES ('${uuid(5)}', 'Viaje', 'flight', NULL, NULL, " +
                    "2, 1, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO goal_event VALUES ('${uuid(6)}', '${uuid(5)}', 0, 'target', " +
                    "3000000, 'COP', NULL, '2026-10-02', NULL, 1, 30, 40)"
            )
            exported.execSQL("INSERT INTO featured_goal VALUES (1, '${uuid(5)}')")
            exported.execSQL(
                "INSERT INTO schedule VALUES ('${uuid(7)}', 'payment', '${uuid(1)}', NULL, " +
                    "NULL, 950000, 'COP', NULL, 'monthly', 5, NULL, '2026-10-05', NULL, 0, " +
                    "'2026-10-05', 1, 1, 30, 40)"
            )
            exported.execSQL(
                "INSERT INTO schedule_pause VALUES ('${uuid(7)}', '2026-10-07', NULL)"
            )
            exported.rows()
        }

        // Room compares the result with the exported version 6 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 6, true, Migration5To6).use { upgraded ->
            val investmentRows = investmentTables.associateWith { table ->
                upgraded.query("SELECT COUNT(*) FROM $table").use { count ->
                    count.moveToFirst()
                    count.getInt(0)
                }
            }

            assertEquals(13, before.values.sumOf { rows -> rows.size })
            assertEquals(before, upgraded.rows())
            assertEquals(investmentTables.associateWith { 0 }, investmentRows)
        }
    }

    @Test
    fun aVersionOneDatabaseOpenedByThisBuildIsUpgradedThroughEveryVersionAndReadsTheSameData() =
        runBlocking {
            versionOneWithHistory()

            val database = openSelloDatabase(instrumentation.targetContext, Dispatchers.IO, name)
            val profile =
                RoomFinancialProfileStore(database) {
                    recordedAt
                }.establish(ZoneId.of("Asia/Tokyo"))
            val expenses = database.expenseDao().page("0000-01-01", "9999-12-32", "", 0, 10)
            val version = database.openHelper.readableDatabase.version
            database.close()

            assertEquals(
                Outcome.Success(FinancialProfile(ZoneId.of("America/Bogota"), 4, 17)),
                profile
            )
            assertEquals(listOf(48_700L), expenses.map { it.amountMinor })
            assertEquals(7, version)
        }

    @Test
    fun aDatabaseBuiltFromTheExportedCurrentSchemaOpensAndKeepsItsRows() = runBlocking {
        helper.createDatabase(name, 7).use { exported ->
            exported.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 4, 17, 0)"
            )
        }

        val database = openSelloDatabase(instrumentation.targetContext, Dispatchers.IO, name)
        val profile =
            RoomFinancialProfileStore(database) { recordedAt }.establish(ZoneId.of("Asia/Tokyo"))
        database.close()

        assertEquals(
            Outcome.Success(FinancialProfile(ZoneId.of("America/Bogota"), 4, 17)),
            profile
        )
    }
}
