package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import java.io.File
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Two storage profiles side by side, as the normal app and its sandbox are: setting one
 * aside or erasing it removes only that profile's private files. The other profile's
 * database, staged restore, pending erase and kept copy, and a document that is not
 * Sello's, are untouched. All contents are synthetic.
 */
@RunWith(AndroidJUnit4::class)
class ProfileFileBoundaryTest {
    private val context get() = androidx.test.platform.app.InstrumentationRegistry
        .getInstrumentation().targetContext
    private val id = UUID.randomUUID().toString().take(8)
    private val mine = StorageNames("mine-$id.db", "mine-$id")
    private val other = StorageNames("other-$id.db", "other-$id")
    private val document = File(context.filesDir, "respaldo-$id.json")
    private val opened = mutableListOf<FinancialStorage>()
    private val dispatchers = realDispatchers

    @After
    fun removeEverything() {
        opened.forEach { it.close() }
        for (names in listOf(mine, other)) {
            context.deleteDatabase(names.database)
            context.deleteDatabase(names.setAsideDatabase)
            File(context.filesDir, names.restoreFolder).deleteRecursively()
            File(context.filesDir, names.resetFolder).deleteRecursively()
        }
        document.delete()
    }

    /** Every private file of [names] that exists, with its contents. */
    private fun filesOf(names: StorageNames): Map<String, String> = buildList {
        add(context.getDatabasePath(names.database))
        add(context.getDatabasePath(names.setAsideDatabase))
        addAll(File(context.filesDir, names.restoreFolder).walk().filter(File::isFile))
        addAll(File(context.filesDir, names.resetFolder).walk().filter(File::isFile))
    }.filter(File::exists).associate { it.name to it.readBytes().contentHashCode().toString() }

    /** A profile with data, a staged restore, a kept unreadable copy and a pending erase. */
    private fun populate(names: StorageNames) = runBlocking {
        val storage = openFinancialStorage(context, Dispatchers.IO, { recordedAt }, names)
            .also { opened += it }
        storage.profiles.establish(ZoneId.of("America/Bogota")).valueOrFail()
        File(context.filesDir, names.restoreFolder).apply { mkdirs() }
            .resolve("staged.json").writeText("{\"sintetico\": true}")
        File(context.filesDir, names.resetFolder).apply { mkdirs() }
            .resolve("pending.properties").writeText("operation=${uuid(1)}\n")
        context.getDatabasePath(names.setAsideDatabase).writeBytes(ByteArray(32) { 5 })
        storage
    }

    @Test
    fun settingOneProfileAsideLeavesTheOtherProfilesFilesAndDocumentsAlone() {
        populate(mine).close()
        populate(other)
        document.writeText("{\"version\": 1}")
        val before = filesOf(other)

        assertEquals(Outcome.Success(Unit), setAsideFinancialStorage(context, mine))

        assertFalse(File(context.filesDir, mine.restoreFolder).exists())
        assertEquals(before, filesOf(other))
        assertEquals("{\"version\": 1}", document.readText())
    }

    @Test
    fun erasingOneProfileRemovesOnlyItsOwnPrivateFiles() = runBlocking {
        val storage = populate(mine)
        populate(other)
        document.writeText("{\"version\": 1}")
        val before = filesOf(other)
        // The pending erase written above has no receipt; it is forgotten, not finished.
        File(context.filesDir, mine.resetFolder).deleteRecursively()
        val resets = storage.resets({ PreferenceWrite.Saved }, { ZoneId.of("America/Lima") }, {
            recordedAt
        }, dispatchers)
        val preview = resets.preview().valueOrFail()

        val outcome = resets.reset(preview, OperationId.of(uuid(2)).valueOrFail())

        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
        assertFalse(File(context.filesDir, mine.restoreFolder).exists())
        assertFalse(context.getDatabasePath(mine.setAsideDatabase).exists())
        assertTrue(File(context.filesDir, mine.resetFolder).list().isNullOrEmpty())
        assertEquals(before, filesOf(other))
        assertEquals("{\"version\": 1}", document.readText())
    }
}
