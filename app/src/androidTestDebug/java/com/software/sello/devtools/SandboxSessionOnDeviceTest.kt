package com.software.sello.devtools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.composition.DebugComposition
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.composition.openStorage
import com.software.sello.data.StorageNames
import com.software.sello.domain.model.Outcome
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The sandbox's data beside the installed debug app's own, on a real device. */
@RunWith(AndroidJUnit4::class)
class SandboxSessionOnDeviceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bogota = ZoneId.of("America/Bogota")

    @Before
    fun start() {
        TestData.reset()
        eraseFinancialStorage(context, SandboxNames)
    }

    @After
    fun end() {
        eraseFinancialStorage(context, SandboxNames)
        TestData.reset()
    }

    private fun profiles(database: String): Long = SQLiteDatabase.openDatabase(
        context.getDatabasePath(database).path,
        null,
        SQLiteDatabase.OPEN_READONLY
    ).use { db ->
        db.rawQuery("SELECT COUNT(*) FROM category", null).use {
            it.moveToFirst()
            it.getLong(0)
        }
    }

    @Test
    fun theSandboxOpensItsOwnFileAndTheAppsOwnDataIsUntouched() {
        TestCommands.createCategory("Comida", 100_000)
        val before = TestData.rows("SELECT * FROM category")

        val opened = runBlocking {
            openStorage(
                context,
                bogota,
                SystemDispatcherProvider(),
                SystemAuditClock(),
                SandboxNames
            )
        }
        assertTrue("$opened", opened is Outcome.Success)
        (opened as Outcome.Success).value.storage.close()

        assertTrue(context.getDatabasePath("sandbox.db").exists())
        assertEquals(0L, profiles("sandbox.db"))
        assertEquals(before, TestData.rows("SELECT * FROM category"))
    }

    @Test
    fun resettingErasesOnlyTheSandboxAndRefusesTheAppsOwnData() {
        TestCommands.createCategory("Comida", null)
        val opened = runBlocking {
            openStorage(
                context,
                bogota,
                SystemDispatcherProvider(),
                SystemAuditClock(),
                SandboxNames
            )
        } as Outcome.Success
        opened.value.storage.close()

        eraseFinancialStorage(context, SandboxNames)

        assertFalse(context.getDatabasePath("sandbox.db").exists())
        assertEquals(1L, TestData.count("category"))
        val refused = runCatching { eraseFinancialStorage(context, StorageNames.Normal) }
        assertTrue(refused.exceptionOrNull() is IllegalArgumentException)
        assertTrue(context.getDatabasePath("sello.db").exists())
    }

    @Test
    fun theDebugBuildChoosesTheSandboxOnlyWhenTheSessionSaysSo() {
        val normal = DebugComposition(MemoryStore(SandboxSession()), {}, {})
        val sandbox = DebugComposition(MemoryStore(SandboxSession(active = true)), {}, {})

        assertEquals(StorageNames.Normal, normal.names)
        assertEquals(SandboxNames, sandbox.names)
    }

    @Test
    fun theSessionIsKeptForTheNextProcess() {
        val store = SharedPreferencesSessionStore(context)
        val original = store.read()
        try {
            val session =
                SandboxSession(active = true, date = LocalDate.of(2026, 10, 31), changed = true)
            store.write(session)

            assertEquals(session, SharedPreferencesSessionStore(context).read())
        } finally {
            store.write(original)
        }
    }
}

class MemoryStore(var session: SandboxSession) : SandboxSessionStore {
    override fun read() = session

    override fun write(session: SandboxSession) {
        this.session = session
    }
}
