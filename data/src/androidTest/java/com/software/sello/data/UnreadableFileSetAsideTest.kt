package com.software.sello.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.local.setAside
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Outcome
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Starting again after a real file would not open: the file is kept and the next open is empty. */
@RunWith(AndroidJUnit4::class)
class UnreadableFileSetAsideTest {
    @get:Rule
    val files = DatabaseFiles()

    private val bogota = ZoneId.of("America/Bogota")
    private val kept: File get() = files.context.getDatabasePath("kept-${files.name}")

    @After
    fun removeKeptCopy() {
        files.context.deleteDatabase(kept.name)
    }

    private fun establish() = files.open().let { database ->
        runBlocking { RoomFinancialProfileStore(database) { recordedAt }.establish(bogota) }
            .also { database.close() }
    }

    @Test
    fun aDamagedFileIsKeptByteForByteAndTheNextOpenIsANewEmptyHistory() {
        val damaged = ByteArray(8192) { (it * 31 + 7).toByte() }
        files.file.parentFile!!.mkdirs()
        files.file.writeBytes(damaged)
        assertTrue(establish() is Outcome.Failure)

        val moved = setAside(files.file, kept)

        assertEquals(Outcome.Success(Unit), moved)
        assertArrayEquals(damaged, kept.readBytes())
        assertEquals(
            Outcome.Success(FinancialProfile(zone = bogota, generation = 1, revision = 0)),
            establish()
        )
        val tables = listOf("category", "default_limit", "month_limit", "expense", "income")
        SQLiteDatabase.openDatabase(files.file.path, null, SQLiteDatabase.OPEN_READONLY).use {
            for (table in tables) {
                it.rawQuery("SELECT COUNT(*) FROM $table", null).use { rows ->
                    rows.moveToFirst()
                    assertEquals(table, 0L, rows.getLong(0))
                }
            }
        }
        assertArrayEquals(damaged, kept.readBytes())
    }

    @Test
    fun aFileFromANewerVersionIsStillWholeAndReadableWhereItWasKept() {
        files.file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(files.file, null).use { newer ->
            newer.execSQL("CREATE TABLE future_ledger (amount INTEGER NOT NULL)")
            newer.execSQL("INSERT INTO future_ledger VALUES (10000)")
            newer.version = SelloDatabase.VERSION + 1
        }
        assertTrue(establish() is Outcome.Failure)

        val moved = setAside(files.file, kept)

        assertEquals(Outcome.Success(Unit), moved)
        assertTrue(establish() is Outcome.Success)
        SQLiteDatabase.openDatabase(kept.path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(SelloDatabase.VERSION + 1, it.version)
            it.rawQuery("SELECT amount FROM future_ledger", null).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(10_000L, row.getLong(0))
            }
            val profileTable = "SELECT COUNT(*) FROM sqlite_master WHERE name = 'profile'"
            assertEquals("never migrated", 0L, DatabaseUtils.longForQuery(it, profileTable, null))
        }
    }
}
