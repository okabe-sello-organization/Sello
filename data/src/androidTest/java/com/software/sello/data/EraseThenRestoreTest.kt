package com.software.sello.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.RoomRestores
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.InvestmentPreferences
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.Resets
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.Restores
import com.software.sello.domain.port.SmallExpensePreferences
import java.io.File
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An erase whose last steps did not finish, then a restore, on one real database file,
 * through the storage the app's composition builds: the erase and the restore share it,
 * as they share one process. The history before is Mercado (100) with one expense; the
 * backup is Comida (1) with two expenses, in Lima, with dark violet alphabetical order
 * and dollar investments by return. Expected values are written out by hand.
 */
@RunWith(AndroidJUnit4::class)
class EraseThenRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    /**
     * One preference store for every group, as the app's; its reset can be told to fail.
     * [lastBackup] stands for what a backup does not carry, such as the last backup's name.
     */
    private class Store :
        AppearancePreferences,
        CategoryOrderingPreferences,
        InvestmentPreferences,
        SmallExpensePreferences,
        PreferenceReset {
        var appearance = AppearancePreference()
        var ordering = CategoryOrderingPreference()
        var investments = InvestmentPreference()
        var line = SmallExpenseLine.DEFAULT
        var lastBackup: String? = null
        var failReset = false
        var failAppearance = false

        private fun saved(fail: Boolean = false, write: () -> Unit): PreferenceWrite = if (fail) {
            PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        } else {
            write()
            PreferenceWrite.Saved
        }

        override suspend fun resetAll() = saved(failReset) {
            appearance = AppearancePreference()
            ordering = CategoryOrderingPreference()
            investments = InvestmentPreference()
            line = SmallExpenseLine.DEFAULT
            lastBackup = null
        }

        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> =
            flowOf(PreferenceRead.Ready(appearance))

        override suspend fun setTheme(theme: ThemePreference) = saved(failAppearance) {
            appearance = appearance.copy(theme = theme)
        }

        override suspend fun setInk(ink: InkPreference) = saved(failAppearance) {
            val hue = (ink as? InkPreference.Tuya)?.hue ?: appearance.tuyaHue
            appearance = appearance.copy(ink = ink, tuyaHue = hue)
        }

        override suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?) =
            saved(failAppearance) { appearance = appearance.copy(ink = ink, tuyaHue = tuyaHue) }

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(PreferenceRead.Ready(ordering))

        override suspend fun setCategoryOrder(order: CategoryOrder) = saved {
            ordering = ordering.copy(order = order)
        }

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            saved { ordering = ordering.copy(direction = direction) }

        override fun observeInvestments(): Flow<PreferenceRead<InvestmentPreference>> =
            flowOf(PreferenceRead.Ready(investments))

        override suspend fun setNewInvestmentCurrency(currency: Currency) = saved {
            investments = investments.copy(newCurrency = currency)
        }

        override suspend fun setInvestmentOrder(order: InvestmentOrder) = saved {
            investments = investments.copy(order = order)
        }

        override fun observeSmallExpenseLine(): Flow<PreferenceRead<SmallExpenseLine>> =
            flowOf(PreferenceRead.Ready(line))

        override suspend fun setSmallExpenseLine(line: SmallExpenseLine) = saved {
            this.line = line
        }
    }

    private val store = Store().apply {
        appearance = AppearancePreference(ThemePreference.Light, InkPreference.Cobalto)
        ordering = CategoryOrderingPreference(CategoryOrder.Limit, CategoryOrderDirection.Ascending)
        line = SmallExpenseLine.of(50_000)!!
        lastBackup = "sello-mercado.json"
    }
    private val lima = ZoneId.of("America/Lima")
    private var nextId = 7_000
    private var whenStamped: () -> Unit = {}

    private val restored = PortablePreferences(
        AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
        CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending),
        InvestmentPreference(Currency.USD, InvestmentOrder.Return),
        SmallExpenseLine.of(8_000)!!
    )

    /** One process: the storage the composition builds, with its restores and resets. */
    private inner class Process {
        private val storage = ledger.storage()
        val restores: Restores = storage.restores(
            store,
            store,
            store,
            store,
            HandGuidancePreferences(),
            NoticeSeen(),
            { uuid(nextId++) },
            {
                whenStamped()
                ledger.audit
            },
            realDispatchers,
            store
        )
        val resets: Resets = storage.resets(store, { lima }, { ledger.audit }, realDispatchers)
    }

    /** As the app starts: the erase's steps are finished, then the restore's preferences. */
    private suspend fun startAgain(): Process {
        ledger.reopen()
        return Process().also {
            it.resets.finishPending()
            it.restores.applyPending()
        }
    }

    private fun backup(preferences: PortablePreferences? = restored) = BackupCodec().encode(
        BackupContents(
            exportedAt = Instant.parse("2026-10-20T14:30:00Z"),
            financialMonth = YearMonth.of(2026, 10),
            financial = FinancialSnapshot(
                zone = lima,
                revision = 4,
                categories = listOf(category(1, "Comida")),
                defaultLimits = emptyList(),
                monthLimits = emptyList(),
                expenses = listOf(
                    expense(101, 1, "2026-10-01", 1, 10_000),
                    expense(102, 1, "2026-10-02", 2, 15_000)
                ),
                incomes = emptyList(),
                goals = emptyList(),
                starredGoals = emptySet(),
                schedules = emptyList(),
                investments = emptyList(),
                usdRates = emptyList(),
                carryChains = emptyList()
            ),
            preferences = preferences
        )
    ).valueOrFail()

    private suspend fun mercado() {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000))
    }

    private suspend fun erase(process: Process): ResetOutcome {
        val preview = process.resets.preview().valueOrFail()
        return process.resets.reset(preview, ledger.operation())
    }

    private suspend fun restore(process: Process, bytes: ByteArray = backup()): RestoreOutcome =
        process.restores.replace(process.restores.stage(bytes).valueOrFail())

    private val restoredSummary = BackupSummary(categories = 1, expenses = 2, incomes = 0, 0, 0)

    /** The backup's own, and nothing of the erased history's. */
    private fun assertRestoredPreferences() {
        assertEquals(restored.appearance, store.appearance)
        assertEquals(restored.ordering, store.ordering)
        assertEquals(restored.investments, store.investments)
        assertEquals(restored.smallExpenseLine, store.line)
        assertNull(store.lastBackup)
    }

    private val keptCopy: File by lazy {
        files.context.getDatabasePath(StorageNames(files.name, "unused").setAsideDatabase)
    }

    private fun reset(name: String) = File(files.resetFolder, name)

    private fun restoreFiles(): List<String> = files.restoreFolder.list()?.sorted().orEmpty()

    private fun history() = listOf(
        ledger.rows("category", "id", "name"),
        ledger.rows("expense", "id", "amount_minor"),
        ledger.rows("profile", "financial_zone", "generation")
    )

    private val restoredHistory = listOf(
        listOf("${uuid(1)}|Comida"),
        listOf("${uuid(101)}|10000", "${uuid(102)}|15000"),
        listOf("America/Lima|3")
    )

    @Test
    fun anEraseWhosePreferencesFailedNeverUndoesTheRestoreMadeAfterIt() = runBlocking {
        mercado()
        val process = Process()
        store.failReset = true
        assertEquals(ResetOutcome.Erased(preferencesPending = true), erase(process))
        assertTrue(reset("pending.properties").exists())
        val erased = history()

        // While the erase's preferences cannot be reset, no restore replaces the history.
        val staged = process.restores.stage(backup()).valueOrFail()
        assertEquals(RestoreOutcome.Failed, process.restores.replace(staged))
        assertEquals(erased, history())
        assertEquals("sello-mercado.json", store.lastBackup)

        store.failReset = false
        val outcome = process.restores.replace(staged)

        assertEquals(
            RestoreOutcome.Restored(restoredSummary, PendingPreferences(null, null)),
            outcome
        )
        assertRestoredPreferences()
        // Every later start, with a store that would reset: nothing goes back.
        repeat(2) {
            val next = startAgain()
            assertFalse(next.resets.pending())
            assertNull(next.restores.pending())
        }
        assertRestoredPreferences()
        assertEquals(restoredHistory, history())
        assertFalse(reset("pending.properties").exists())
        // A new erase is accepted, and it does reset them.
        assertEquals(ResetOutcome.Erased(preferencesPending = false), erase(Process()))
        assertEquals(AppearancePreference(), store.appearance)
        assertEquals(
            listOf("America/Lima|4"),
            ledger.rows("profile", "financial_zone", "generation")
        )
    }

    @Test
    fun aRestoresPendingFileOutlivesAnEraseThatCouldNotFinishBeforeIt() = runBlocking {
        mercado()
        val process = Process()
        store.failReset = true
        erase(process)
        store.failReset = false
        store.failAppearance = true

        val outcome = restore(process)

        assertEquals(
            RestoreOutcome.Restored(restoredSummary, PendingPreferences(restored.appearance, null)),
            outcome
        )
        val next = startAgain()
        // The erase's turn at start-up left the file the appearance still comes from.
        assertEquals(listOf("applied", "restored.json", "staged.properties"), restoreFiles())
        assertEquals(outcome, next.restores.pending())

        store.failAppearance = false
        val done = startAgain().restores.pending()

        assertNull(done)
        assertRestoredPreferences()
        assertEquals(emptyList<String>(), restoreFiles())
        assertEquals(restoredHistory, history())
    }

    @Test
    fun aRestoreCutShortAfterItsCommitIsFinishedAfterAnEraseThatCouldNotFinish() = runBlocking {
        mercado()
        val process = Process()
        store.failReset = true
        erase(process)
        store.failReset = false
        val staged = process.restores.stage(backup()).valueOrFail()
        val replacing = async(Dispatchers.Default) { process.restores.replace(staged) }
        // The receipt is stamped inside the transaction: the process ends right there,
        // before the file moves and before any preference is applied.
        whenStamped = { replacing.cancel() }
        val cancelled = runCatching { replacing.await() }.exceptionOrNull()
        whenStamped = {}
        assertTrue("$cancelled", cancelled is CancellationException)
        assertEquals(listOf("staged.json", "staged.properties"), restoreFiles())

        startAgain()

        assertRestoredPreferences()
        assertEquals(emptyList<String>(), restoreFiles())
        assertEquals(restoredHistory, history())
        assertFalse(reset("pending.properties").exists())
    }

    @Test
    fun aCompactionKeptBusyStaysOwedAndIsDoneLaterWithoutBlockingAnything() = runBlocking {
        mercado()
        // Another connection reading from the write-ahead log keeps the checkpoint
        // from truncating it.
        val reader = SQLiteDatabase.openDatabase(
            files.file.path,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        reader.execSQL(";BEGIN")
        reader.rawQuery("SELECT COUNT(*) FROM expense", null).use { it.moveToFirst() }
        val process = Process()

        val outcome = erase(process)

        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
        assertTrue(reset("compact.pending").exists())
        assertFalse(reset("pending.properties").exists())
        assertTrue(process.resets.pending())
        assertEquals(AppearancePreference(), store.appearance)

        // A restore and a new erase are not held up by it.
        assertTrue(restore(process) is RestoreOutcome.Restored)
        assertRestoredPreferences()
        reader.execSQL(";ROLLBACK")
        reader.close()
        val next = startAgain()

        assertFalse(reset("compact.pending").exists())
        assertFalse(next.resets.pending())
        assertRestoredPreferences()
        assertEquals(restoredHistory, history())
        assertEquals(ResetOutcome.Erased(preferencesPending = false), erase(next))
    }

    @Test
    fun aBackupWithoutPreferencesStillLeavesNoneOfTheErasedHistorysBehind() = runBlocking {
        mercado()
        val process = Process()
        store.failReset = true
        erase(process)
        store.failReset = false

        val outcome = restore(process, backup(preferences = null))

        assertEquals(
            RestoreOutcome.Restored(restoredSummary, PendingPreferences(null, null)),
            outcome
        )
        repeat(2) { startAgain() }
        assertEquals(AppearancePreference(), store.appearance)
        assertEquals(CategoryOrderingPreference(), store.ordering)
        assertEquals(InvestmentPreference(), store.investments)
        assertEquals(SmallExpenseLine.DEFAULT, store.line)
        assertNull(store.lastBackup)
        assertFalse(reset("pending.properties").exists())
        assertEquals(restoredHistory, history())
    }

    @Test
    fun theCopyKeptAsideGoesEvenAfterARestoreReplacedTheErasedHistory() = runBlocking {
        mercado()
        // The copy cannot be deleted while the erase finishes: something sits in its place.
        keptCopy.mkdirs()
        File(keptCopy, "in-the-way").writeText("x")
        val process = Process()
        erase(process)
        assertTrue(reset("pending.properties").exists())
        // Then it is a copy that can go, and a restore replaces the erased history.
        keptCopy.deleteRecursively()
        keptCopy.writeBytes(ByteArray(64) { 7 })
        restore(process)

        val next = startAgain()

        assertFalse(keptCopy.exists())
        assertFalse(reset("pending.properties").exists())
        assertFalse(next.resets.pending())
        assertRestoredPreferences()
    }

    @Test
    fun anOlderRestoresPendingPreferencesAreNeverAppliedAfterAnErase() = runBlocking {
        mercado()
        val process = Process()
        store.failAppearance = true
        restore(process)
        assertEquals(listOf("applied", "restored.json", "staged.properties"), restoreFiles())
        // The erase cannot empty the restore folder, so the older file is still there.
        assertTrue(files.restoreFolder.setWritable(false, false))
        assertEquals(ResetOutcome.Erased(preferencesPending = false), erase(process))
        assertTrue(files.restoreFolder.setWritable(true, false))
        assertEquals(listOf("applied", "restored.json", "staged.properties"), restoreFiles())
        store.failAppearance = false

        val applied = Process().restores.applyPending()

        assertNull(applied)
        assertEquals(AppearancePreference(), store.appearance)
        assertEquals(CategoryOrderingPreference(), store.ordering)
        assertEquals(emptyList<String>(), restoreFiles())
        assertEquals(
            listOf("America/Lima|3"),
            ledger.rows("profile", "financial_zone", "generation")
        )
    }

    /** As an older build restored: without putting the erase's preferences back first. */
    @Test
    fun aReplacedEraseOnlyMarksItsPreferencesDoneAndNeverWipesTheRestoredOnes() = runBlocking {
        mercado()
        store.failReset = true
        erase(Process())
        store.failReset = false
        val older = RoomRestores(
            ledger.database,
            files.restoreFolder,
            store,
            store,
            store,
            store,
            HandGuidancePreferences(),
            NoticeSeen(),
            { uuid(nextId++) },
            { ledger.audit },
            realDispatchers,
            Mutex(),
            { true }
        )
        older.replace(older.stage(backup()).valueOrFail())

        val next = startAgain()

        assertEquals(restored.appearance, store.appearance)
        assertEquals(restored.ordering, store.ordering)
        assertEquals(restored.investments, store.investments)
        assertFalse(reset("pending.properties").exists())
        assertFalse(next.resets.pending())
    }

    @Test
    fun anEraseIsCompactedEvenWhenWhatItOwesCannotBeWrittenDown() = runBlocking {
        mercado()
        ledger.categories.submit(ledger.createCategory("Farmacia Pasteur", finite(20_000)))
        // Only `compact.pending` cannot be written: its temporary copy cannot be made.
        File(files.resetFolder, "compact.pending.tmp").mkdirs()

        erase(Process())

        assertFalse(reset("compact.pending").exists())
        val folder = files.file.parentFile!!
        val bytes = folder.listFiles { file -> file.name.startsWith(files.name) }!!
            .joinToString("") { it.readBytes().toString(Charsets.ISO_8859_1) }
        for (erased in listOf("Mercado", "Farmacia Pasteur")) {
            assertFalse("$erased survives in the files", erased in bytes)
        }
        // What it owes is still kept in its own file, to look at again.
        assertTrue(reset("pending.properties").exists())
    }

    @Test
    fun somethingThatIsNotAFileInTheMarkersPlaceNeverHoldsARestoreUp() = runBlocking {
        mercado()
        val marker = reset("pending.properties").apply { mkdirs() }
        File(marker, "in-the-way").writeText("x")

        assertTrue(restore(Process()) is RestoreOutcome.Restored)
        assertFalse(marker.exists())
        assertEquals(listOf("${uuid(1)}|Comida"), ledger.rows("category", "id", "name"))
    }

    @Test
    fun aMarkerWithAMalformedEscapeIsReadAsDamagedNeverACrash() = runBlocking {
        mercado()
        files.resetFolder.mkdirs()
        reset("pending.properties").writeText("operation=\\uZZZZ\n")

        assertTrue(restore(Process()) is RestoreOutcome.Restored)
        assertEquals(ResetOutcome.Erased(preferencesPending = false), erase(Process()))
    }
}
