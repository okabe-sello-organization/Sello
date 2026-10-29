package com.software.sello

import com.software.sello.data.FinancialStorage
import com.software.sello.data.querySqlForTests
import com.software.sello.data.runSqlForTests
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals

/**
 * The database of the graph [TestCommands] runs on: the installed debug app's own unless
 * a test chose another, and in either case one every test in a run shares. Tests that
 * need a new installation, or that create records, start and end from here.
 *
 * Everything goes through that graph's own Room database, never a second connection to
 * the file: Room hears each change, as of its own writes, so what the screens show is
 * read again, and no second connection fights Room for the file's lock.
 */
object TestData {
    private val storage: FinancialStorage get() = TestCommands.koin.get()

    /** The one row a new installation already has. A reset puts it back to revision 0. */
    private const val PROFILE = "profile"

    /** What Android and Room keep in the file for themselves. */
    private val bookkeeping = setOf("android_metadata", "room_master_table")

    /**
     * Every table the app keeps data in, read from the database's own schema, so a table
     * a later version adds is cleared without anyone having to remember it.
     */
    fun tables(): List<String> =
        rows("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name")
            .filterNot { it in bookkeeping || it.startsWith("sqlite_") }

    /**
     * Back to what a new installation holds: a profile at revision 0 and nothing else,
     * and the preferences of a first run. It checks that every table is empty after it,
     * so nothing a test made can reach the next test's history.
     */
    fun reset() {
        val cleared = tables() - PROFILE
        // Foreign keys are checked when the transaction ends, so the order does not matter.
        storage.runSqlForTests(
            listOf("PRAGMA defer_foreign_keys = ON") +
                cleared.map { "DELETE FROM $it" } +
                "UPDATE $PROFILE SET revision = 0"
        )
        assertEquals("rows left after a reset", emptyMap<String, Long>(), leftIn(cleared))
        // Preferences too: an announcement one test dismissed would not show in the next.
        assertEquals(
            PreferenceWrite.Saved,
            runBlocking { TestCommands.koin.get<PreferenceReset>().resetAll() }
        )
    }

    private fun leftIn(tables: List<String>): Map<String, Long> =
        tables.associateWith(::count).filterValues { it > 0 }

    /** Changes rows the app did not write, to damage or repair what it will read. */
    fun execute(sql: String) = storage.runSqlForTests(listOf(sql))

    fun count(table: String): Long = long("SELECT COUNT(*) FROM $table")

    /** Rows of every table but the profile and what is kept only for an undo. */
    fun financialRows(): Long =
        (tables() - PROFILE).filterNot { it.startsWith("deleted_") }.sumOf(::count)

    fun long(sql: String): Long = storage.querySqlForTests(sql) {
        it.moveToFirst()
        it.getLong(0)
    }

    /** Each row of [sql] with its columns joined by "|". */
    fun rows(sql: String): List<String> = storage.querySqlForTests(sql) { cursor ->
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
