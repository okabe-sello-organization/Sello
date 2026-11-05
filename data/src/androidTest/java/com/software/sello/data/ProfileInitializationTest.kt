package com.software.sello.data

import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.SelloDatabase
import com.software.sello.data.repository.RoomFinancialProfileStore
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileInitializationTest {
    @get:Rule
    val files = DatabaseFiles()

    private val bogota = ZoneId.of("America/Bogota")
    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun firstUseStoresTheDeviceZoneWithGenerationOneAndRevisionZero() = runBlocking {
        val database = files.open()
        val at = Instant.parse("2026-10-09T23:59:59.999Z")

        val profile = RoomFinancialProfileStore(database) { at }.establish(bogota)

        assertEquals(Outcome.Success(FinancialProfile(bogota, 1, 0)), profile)
        database.openHelper.readableDatabase.query("SELECT * FROM profile").use { row ->
            assertEquals(1, row.count)
            row.moveToFirst()
            assertEquals(1L, row.getLong(row.getColumnIndexOrThrow("id")))
            assertEquals(
                "America/Bogota",
                row.getString(row.getColumnIndexOrThrow("financial_zone"))
            )
            assertEquals(at.toEpochMilli(), row.getLong(row.getColumnIndexOrThrow("created_at")))
        }
    }

    @Test
    fun aLaterDeviceZoneNeverReplacesTheStoredZoneEvenAfterReopening() = runBlocking {
        val first = files.open()
        RoomFinancialProfileStore(first) { recordedAt }.establish(bogota)

        val sameProcess = RoomFinancialProfileStore(first) { recordedAt }.establish(madrid)
        first.close()
        val reopened = files.open()
        val nextStart = RoomFinancialProfileStore(reopened) { recordedAt }.establish(madrid)

        assertEquals(Outcome.Success(FinancialProfile(bogota, 1, 0)), sameProcess)
        assertEquals(Outcome.Success(FinancialProfile(bogota, 1, 0)), nextStart)
        assertEquals(1, reopened.count("profile"))
    }

    @Test
    fun concurrentFirstUseOnAnUnopenedFileCreatesExactlyOneProfile() = runBlocking {
        val zones = listOf("America/Bogota", "Europe/Madrid", "Asia/Tokyo", "Pacific/Auckland")
            .map(ZoneId::of)
        val database = files.open()
        val store = RoomFinancialProfileStore(database) { recordedAt }
        val start = CompletableDeferred<Unit>()

        val profiles = List(32) { index ->
            async(Dispatchers.Default) {
                start.await()
                store.establish(zones[index % zones.size])
            }
        }.also { start.complete(Unit) }.awaitAll()

        val winner = profiles.first().valueOrFail()
        assertEquals(List(32) { Outcome.Success(winner) }, profiles)
        assertTrue(winner.zone in zones)
        assertEquals(FinancialProfile(winner.zone, 1, 0), winner)
        assertEquals(1, database.count("profile"))
    }

    @Test
    fun aFileFromANewerSchemaIsRefusedAndLeftExactlyAsItWas() = runBlocking {
        files.file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(files.file, null).use { newer ->
            newer.execSQL("CREATE TABLE future_ledger (amount INTEGER NOT NULL)")
            newer.execSQL("INSERT INTO future_ledger VALUES (10000)")
            newer.version = SelloDatabase.VERSION + 1
        }

        val profile = RoomFinancialProfileStore(files.open()) { recordedAt }.establish(bogota)

        assertTrue("$profile", (profile as Outcome.Failure).error is StorageFailure.Unavailable)
        SQLiteDatabase.openDatabase(files.file.path, null, SQLiteDatabase.OPEN_READONLY).use {
            assertEquals(SelloDatabase.VERSION + 1, it.version)
            it.rawQuery("SELECT amount FROM future_ledger", null).use { row ->
                assertTrue(row.moveToFirst())
                assertEquals(10_000L, row.getLong(0))
            }
            val profileTable = "SELECT COUNT(*) FROM sqlite_master WHERE name = 'profile'"
            assertEquals("never migrated", 0L, DatabaseUtils.longForQuery(it, profileTable, null))
        }
    }

    @Test
    fun anUnreadableFileIsAFailureAndIsNotDeletedOrRecreated() = runBlocking {
        val damaged = ByteArray(8192) { (it * 31 + 7).toByte() }
        files.file.parentFile!!.mkdirs()
        files.file.writeBytes(damaged)

        val profile = RoomFinancialProfileStore(files.open()) { recordedAt }.establish(bogota)

        assertTrue("$profile", (profile as Outcome.Failure).error is StorageFailure.Unavailable)
        assertTrue(files.file.exists())
        assertArrayEquals(damaged, files.file.readBytes())
    }
}
