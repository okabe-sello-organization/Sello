package com.software.sello.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.local.Migration8To9
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.openSelloDatabase
import com.software.sello.data.repository.RoomCategoryExpenseSearch
import com.software.sello.data.repository.RoomScheduleRecordReads
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.ExpenseOrder
import com.software.sello.domain.port.ScheduleRecord
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading a version 8 history to version 9 (SELLO-165): every row is kept as it was,
 * each expense gains the search key of its note and the new indexes serve the reads they
 * are for. The history: Hogar (...100), "\u00d1AME \u00c1REA" (...201), an expense with no
 * note (...202), the rent a schedule recorded (...203), 1,200 "\u00c1rbol" expenses so the
 * key is filled across more than one page, a salary a schedule recorded and two receipts.
 */
@RunWith(AndroidJUnit4::class)
class SearchIndexMigrationTest {
    private val name = "search-index-migration.db"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, SelloDatabase::class.java)

    @After
    fun removeFile() {
        instrumentation.targetContext.deleteDatabase(name)
    }

    private val hogar = uuid(100)
    private val rent = uuid(300)
    private val salary = uuid(301)

    /** Every version 8 table this history writes to, with its version 8 columns only. */
    private val keptColumns = mapOf(
        "profile" to "*",
        "category" to "*",
        "expense" to "id, category_id, effective_date, sequence, amount_minor, currency, " +
            "note, version, created_at, updated_at, origin_schedule_id, origin_date, origin_slot",
        "income" to "*",
        "operation_receipt" to "*"
    )

    private fun SupportSQLiteDatabase.rows(sql: String) = query(sql).use { cursor ->
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

    private fun SupportSQLiteDatabase.kept() =
        keptColumns.mapValues { (table, columns) -> rows("SELECT $columns FROM $table ORDER BY 1") }

    private fun SupportSQLiteDatabase.expense(
        number: Int,
        sequence: Long,
        note: String?,
        origin: String = "NULL, NULL, NULL"
    ) = execSQL(
        "INSERT INTO expense (id, category_id, effective_date, sequence, amount_minor, " +
            "currency, note, version, created_at, updated_at, origin_schedule_id, " +
            "origin_date, origin_slot) VALUES ('${uuid(number)}', '$hogar', '2026-10-05', " +
            "$sequence, 950000, 'COP', ?, 1, ${1_000 + sequence}, ${1_000 + sequence}, $origin)",
        arrayOf<Any?>(note)
    )

    /** The history as version 8 stored it. */
    private fun versionEight(): Map<String, List<String>> =
        helper.createDatabase(name, 8).use { db ->
            db.execSQL(
                "INSERT INTO profile (id, financial_zone, generation, revision, created_at) " +
                    "VALUES (1, 'America/Bogota', 2, 30, 0)"
            )
            db.execSQL(
                "INSERT INTO category (id, name, name_key, icon, archived, version, " +
                    "created_at, updated_at) VALUES ('$hogar', 'Hogar', 'hogar', 'home', 0, 1, 0, 0)"
            )
            db.expense(201, 1, "\u00d1AME \u00c1REA")
            db.expense(202, 2, null)
            db.expense(203, 3, "Arriendo", "'$rent', '2026-10-05', 1")
            for (index in 0 until 1_200) db.expense(1_000 + index, 10L + index, "\u00c1rbol $index")
            db.execSQL(
                "INSERT INTO income (id, effective_date, sequence, amount_minor, currency, " +
                    "source_key, source_name, note, version, created_at, updated_at, " +
                    "origin_schedule_id, origin_date, origin_slot) VALUES ('${uuid(400)}', " +
                    "'2026-10-02', 1, 3000000, 'COP', 'salary', NULL, NULL, 1, 1002, 1002, " +
                    "'$salary', '2026-10-02', 1)"
            )
            db.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(9)}', 'history.restore', " +
                    "'aa', 1, 4, NULL, 500)"
            )
            db.execSQL(
                "INSERT INTO operation_receipt VALUES ('${uuid(10)}', 'expense.create', " +
                    "'bb', 2, 30, '${uuid(203)}', 900)"
            )
            db.kept()
        }

    private fun SupportSQLiteDatabase.plan(sql: String) =
        rows("EXPLAIN QUERY PLAN $sql").joinToString("\n")

    @Test
    fun everyRowIsKeptAndEachExpenseGainsTheSearchKeyOfItsNote() {
        val before = versionEight()

        // Room compares the result with the exported version 9 schema and fails on any difference.
        helper.runMigrationsAndValidate(name, 9, true, Migration8To9).use { db ->
            assertEquals(before, db.kept())
            assertEquals(
                listOf(
                    "${uuid(201)}|name area",
                    "${uuid(202)}|",
                    "${uuid(203)}|arriendo"
                ),
                db.rows(
                    "SELECT id, search_key FROM expense WHERE id IN " +
                        "('${uuid(201)}', '${uuid(202)}', '${uuid(203)}') ORDER BY id"
                )
            )
            assertEquals(
                listOf("1200"),
                db.rows("SELECT COUNT(*) FROM expense WHERE search_key LIKE 'arbol %'")
            )
            assertEquals(
                listOf("arbol 0", "arbol 1199"),
                db.rows(
                    "SELECT search_key FROM expense WHERE id IN " +
                        "('${uuid(1_000)}', '${uuid(2_199)}') ORDER BY sequence"
                )
            )
        }
    }

    @Test
    fun theRecordedSinceAndLatestReceiptReadsUseTheirIndexes() {
        versionEight()

        helper.runMigrationsAndValidate(name, 9, true, Migration8To9).use { db ->
            for (table in listOf("expense", "income")) {
                val plan = db.plan(
                    "SELECT * FROM $table WHERE created_at > 0 " +
                        "AND origin_schedule_id IS NOT NULL ORDER BY created_at, sequence LIMIT 250"
                )
                assertTrue(plan, "index_${table}_created_at_origin_schedule_id" in plan)
            }
            val latest = db.plan(
                "SELECT MAX(committed_at) FROM operation_receipt WHERE kind = 'history.restore'"
            )
            assertTrue(latest, "index_operation_receipt_kind_committed_at" in latest)
        }
    }

    /** Opened by this build: searched without accents, and the recorded entries read. */
    @Test
    fun anUpgradedHistoryIsSearchedWithoutAccentsAndItsRecordsRead() = runBlocking {
        versionEight()

        val database = openSelloDatabase(instrumentation.targetContext, Dispatchers.IO, name)
        val found = RoomCategoryExpenseSearch(database).read(
            CategoryId.of(hogar).valueOrFail(),
            YearMonth.of(2026, 10),
            ExpenseOrder.Date,
            "name area",
            0,
            20
        ).valueOrFail()
        val recorded = RoomScheduleRecordReads(database).recordedAfter(null)
        val version = database.openHelper.readableDatabase.version
        database.close()

        assertEquals(9, version)
        assertEquals(listOf("\u00d1AME \u00c1REA"), found.expenses.map { it.note!!.value })
        assertEquals(
            listOf("income|2026-10-02", "expense|2026-10-05"),
            (recorded as Outcome.Success).value.map {
                when (it) {
                    is ScheduleRecord.Payment -> "expense|${it.expense.date}"
                    is ScheduleRecord.Earning -> "income|${it.income.date}"
                }
            }
        )
    }
}
