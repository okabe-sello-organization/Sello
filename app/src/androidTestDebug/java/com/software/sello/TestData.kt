package com.software.sello

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry

/**
 * The installed debug app's own database, which every test in a run shares. Tests
 * that need a new installation, or that create records, start and end from here.
 */
object TestData {
    private var databaseName = "sello.db"

    /** Reads and writes the database called [name], a test's own, until [useInstalledApp]. */
    fun use(name: String) {
        databaseName = name
    }

    fun useInstalledApp() {
        databaseName = "sello.db"
    }

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val tables = listOf(
        // Movements point at investments; rates point at nothing.
        "investment_movement",
        "investment",
        "usd_rate",
        "featured_goal",
        "goal_event",
        "goal",
        "expense",
        "income",
        "month_limit",
        "default_limit",
        // Schedules point at categories, and pauses at schedules.
        "schedule_pause",
        "schedule",
        // Carry chains point at categories.
        "carry_chain",
        "category",
        "operation_receipt"
    )

    private fun <T> database(flags: Int, use: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(context.getDatabasePath(databaseName).path, null, flags)
            .use(use)

    /** Back to what a new installation holds: a profile at revision 0 and nothing else. */
    fun reset() = database(SQLiteDatabase.OPEN_READWRITE) { database ->
        database.beginTransaction()
        try {
            tables.forEach { database.execSQL("DELETE FROM $it") }
            // Not a financial record: only what an undo would put back.
            database.execSQL("DELETE FROM deleted_expense")
            database.execSQL("DELETE FROM deleted_income")
            database.execSQL("DELETE FROM deleted_goal_event")
            database.execSQL("DELETE FROM deleted_investment_movement")
            database.execSQL("UPDATE profile SET revision = 0")
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** Changes rows behind the app's back, to damage or repair what it will read. */
    fun execute(sql: String) = database(SQLiteDatabase.OPEN_READWRITE) { it.execSQL(sql) }

    fun count(table: String): Long = long("SELECT COUNT(*) FROM $table")

    fun financialRows(): Long = tables.sumOf(::count)

    fun long(sql: String): Long = database(SQLiteDatabase.OPEN_READONLY) { database ->
        database.rawQuery(sql, null).use {
            it.moveToFirst()
            it.getLong(0)
        }
    }

    /** Each row of [sql] with its columns joined by "|". */
    fun rows(sql: String): List<String> = database(SQLiteDatabase.OPEN_READONLY) { database ->
        database.rawQuery(sql, null).use { cursor ->
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
}
