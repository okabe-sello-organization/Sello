package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.BackupExportFailure
import com.software.sello.data.backup.BackupRejection
import com.software.sello.data.backup.CarryChainSupport
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupPreparationFailure
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StageFailure
import java.io.File
import java.time.Instant
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Carry chains through a real backup, restore and erase on real database files (ADR 0020
 * CP26, SC42–SC45). Comida is SC01: base 100.000 from ene 2027, spent 20.000, 70.000 and
 * 250.000 in ene, feb and mar, the financial day 5 abr 2027. A backup holds the chain,
 * never the carry, so what a restored history shows is worked out again; the expected
 * figures are copied from docs/testing/signed-carryover-examples.md.
 */
@RunWith(AndroidJUnit4::class)
class CarryBackupRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    @get:Rule
    val fresh = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val other by lazy { TestLedger(fresh).also { it.start() } }
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }
    private val investments = HandInvestmentPreferences()
    private val notice = NoticeSeen()

    /** The portable preferences, which can be told to fail. */
    private class Preferences :
        AppearancePreferences,
        CategoryOrderingPreferences {
        var fail = false
        val wanted = AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)

        private fun write(): PreferenceWrite = if (fail) {
            PreferenceWrite.Failed(
                PreferenceFailure.Unavailable
            )
        } else {
            PreferenceWrite.Saved
        }

        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> =
            flowOf(PreferenceRead.Ready(wanted))

        override suspend fun setTheme(theme: ThemePreference) = write()

        override suspend fun setInk(ink: InkPreference) = write()

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(
                PreferenceRead.Ready(
                    CategoryOrderingPreference(
                        CategoryOrder.MostUsed,
                        CategoryOrderDirection.Descending
                    )
                )
            )

        override suspend fun setCategoryOrder(order: CategoryOrder) = PreferenceWrite.Saved

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            PreferenceWrite.Saved
    }

    private val preferences = Preferences()
    private val accepting = BackupCodec(carryChains = CarryChainSupport.Accepted)
    private var nextId = 5_000

    private fun month(text: String) = YearMonth.parse(text)

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun export(source: TestLedger, codec: BackupCodec = accepting) = BackupExport(
        RoomBackupSnapshots(source.database),
        preferences,
        preferences,
        investments,
        { source.audit },
        dispatchers,
        codec
    )

    /** Made on the financial day of the history, 5 abr 2027, as a person's would be. */
    private suspend fun backup(source: TestLedger = ledger): ByteArray {
        source.audit = Instant.parse("2027-04-05T15:00:00Z")
        return export(source).create().valueOrFail().bytes
    }

    private fun restoresInto(
        target: TestLedger,
        folder: DatabaseFiles,
        codec: BackupCodec = accepting
    ) = RoomRestores(
        target.database,
        folder.restoreFolder,
        preferences,
        preferences,
        investments,
        notice,
        { uuid(nextId++) },
        { target.audit },
        dispatchers,
        codec
    )

    private fun stagingFiles(folder: DatabaseFiles) =
        folder.restoreFolder.list()?.sorted().orEmpty()

    /** Every table a restore or a refusal could touch, with the chains and the profile. */
    private fun everyRow(of: TestLedger): List<List<String>> = listOf(
        of.rows("category", "id", "name", "archived", "version"),
        of.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor"),
        of.rows("month_limit", "category_id", "month"),
        of.rows("expense", "id", "category_id", "effective_date", "amount_minor"),
        of.chainRows(),
        of.rows("operation_receipt", "kind", "generation", "revision"),
        of.rows("profile", "financial_zone", "generation", "revision")
    )

    private val sc01 = listOf(
        "2027-01|K 0|C 100000|S 20000|R 80000|WithinLimit",
        "2027-02|K 80000|C 180000|S 70000|R 110000|WithinLimit",
        "2027-03|K 110000|C 210000|S 250000|R -40000|Over",
        "2027-04|K -40000|C 60000|S 0|R 60000|OnTrack"
    )

    /** SC01's first months, then the chain ended in abr with the −40.000 confirmed (SC22). */
    private suspend fun endedInApril(source: TestLedger) {
        val outcome = source.categories.submit(
            DisableCarry(source.operation(), 1, source.categoryId(100), 2, cop(-40_000))
        )
        check(outcome is CategoryCommandOutcome.Committed) { "$outcome" }
    }

    private suspend fun comida(of: TestLedger, through: String) =
        of.carry.history(of.categoryId(100), month(through)).valueOrFail()

    // SC43: the file holds the chain, not the balance.

    @Test
    fun sc43AChainRestoresOnAFreshInstallationAndReplaysSc01() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        other.goTo("2027-04-05")

        val restores = restoresInto(other, fresh)
        val staged = restores.stage(bytes).valueOrFail()
        val outcome = restores.replace(staged)

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(listOf("${uuid(100)}|2027-01|null"), other.chainRows())
        // The figures are worked out again from the three expenses and the chain.
        val april = comida(other, "2027-04")
        assertEquals(sc01, april.replay.months.map { it.text() })
        assertEquals(emptyList<String>(), april.replay.settled.map { it.text() })
        assertEquals(2L, april.generation)
        // Carry is not an expense or an income: the records are exactly the three recorded.
        assertEquals(
            listOf("20000", "70000", "250000"),
            other.rows("expense", "amount_minor").sortedBy { it.toLong() }
        )
        assertEquals(0L, other.long("SELECT COUNT(*) FROM income"))
        assertEquals(
            cop(60_000),
            other.snapshots.read(month("2027-04")).valueOrFail().categories.single().budget?.limit
        )
        assertEquals(emptyList<String>(), stagingFiles(fresh))
    }

    @Test
    fun theFileASourceWritesHoldsNoCarriedAmount() = runBlocking {
        CarryHistories(ledger).sc01()

        val text = String(backup())

        assertTrue(text.contains("\"carryChains\""))
        for (derived in listOf("carryIn", "capacity", "remaining", "-40000", "60000")) {
            assertTrue(derived, !text.contains(derived))
        }
    }

    @Test
    fun sc22AnEndedChainRestoresWithItsSettledBalanceAndAprilAsItsBase() = runBlocking {
        CarryHistories(ledger).sc01()
        endedInApril(ledger)
        val bytes = backup()
        other.goTo("2027-04-05")

        val restores = restoresInto(other, fresh)
        restores.replace(restores.stage(bytes).valueOrFail())

        assertEquals(listOf("${uuid(100)}|2027-01|2027-04"), other.chainRows())
        val april = comida(other, "2027-04")
        assertEquals(sc01.take(3), april.replay.months.map { it.text() })
        assertEquals(listOf("2027-01|2027-04|-40000"), april.replay.settled.map { it.text() })
        assertEquals(
            cop(100_000),
            other.snapshots.read(month("2027-04")).valueOrFail().categories.single().budget?.limit
        )
    }

    // SC42, SC45: older files restore as no-carry; a restore replaces the chains.

    @Test
    fun sc42AVersionFiveFileRestoresWithEveryCategoryOffOverAnEnabledOne() = runBlocking {
        CarryHistories(ledger).sc01()
        endedInApril(ledger)
        // The same history written as version 5: no chains, as Sello wrote it before.
        val version5 = String(backup()).replace("\"version\": 6,", "\"version\": 5,")
            .replace(Regex(",\\s+\"carryChains\": \\[[^]]*]"), "")
        check(!version5.contains("carryChains"))
        CarryHistories(other).sc01()
        assertEquals(listOf("${uuid(100)}|2027-01|null"), other.chainRows())

        val restores = restoresInto(other, fresh)
        val outcome = restores.replace(restores.stage(version5.toByteArray()).valueOrFail())

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(emptyList<String>(), other.chainRows())
        assertEquals(emptyList<String>(), comida(other, "2027-04").replay.months.map { it.text() })
        suspend fun snapshot(of: String) = other.snapshots.read(month(of)).valueOrFail()
        // ene C 100.000; mar R −150.000; abr C 100.000, not 60.000: no deficit is guessed.
        assertEquals(cop(100_000), snapshot("2027-01").categories.single().budget?.limit)
        assertEquals(cop(-150_000), snapshot("2027-03").categories.single().budget?.remaining)
        assertEquals(cop(100_000), snapshot("2027-04").categories.single().budget?.limit)
    }

    @Test
    fun sc45ARestoreOfAFileWithNoChainsEndsTheChainsOfTheHistoryItReplaces() = runBlocking {
        CarryHistories(ledger).sc01()
        val withoutChains = BackupCodec().encode(
            accepting.read(backup()).valueOrFail().contents.let {
                it.copy(financial = it.financial.copy(carryChains = emptyList()))
            }
        ).valueOrFail()
        CarryHistories(other).sc01()

        val restores = restoresInto(other, fresh)
        restores.replace(restores.stage(withoutChains).valueOrFail())

        assertEquals(emptyList<String>(), other.chainRows())
    }

    @Test
    fun sc45AnEraseAfterARestoreRemovesTheChains() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        other.goTo("2027-04-05")
        val restores = restoresInto(other, fresh)
        restores.replace(restores.stage(bytes).valueOrFail())
        assertEquals(1, other.chainRows().size)
        val resets = RoomResets(
            other.database,
            fresh.resetFolder,
            fresh.restoreFolder,
            emptyList(),
            PreferenceReset { PreferenceWrite.Saved },
            { other.zone },
            { other.audit },
            dispatchers
        )

        val preview = resets.preview().valueOrFail()
        resets.reset(preview, other.operation())

        assertEquals(emptyList<String>(), other.chainRows())
        assertEquals(0L, other.long("SELECT COUNT(*) FROM category"))
    }

    // The customer gate until SELLO-041.

    @Test
    fun theCustomerPathRefusesAFileWithAChainBeforeAnyChange() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        CarryHistories(other).sc01()
        val before = everyRow(other)

        // The composition's own restores, the way the app builds them, with its default codec.
        val storage = other.storage()
        val restores = storage.restores(
            preferences,
            preferences,
            investments,
            notice,
            { uuid(nextId++) },
            { other.audit },
            dispatchers
        )
        val refused = restores.stage(bytes)
        val direct = restoresInto(other, fresh, BackupCodec()).stage(bytes)

        val expected = Outcome.Failure(StageFailure.Rejected(RestoreRejection.CarryNotSupported))
        assertEquals(expected, refused)
        assertEquals(expected, direct)
        assertEquals(before, everyRow(other))
        assertEquals(emptyList<String>(), stagingFiles(fresh))
        assertNull(restores.staged())
        assertNull(restores.pending())
    }

    @Test
    fun theCustomerPathStillRestoresAFileWithNoChain() = runBlocking {
        CarryHistories(ledger).sc01()
        val withoutChains = String(backup()).replace("\"version\": 6,", "\"version\": 5,")
            .replace(Regex(",\\s+\"carryChains\": \\[[^]]*]"), "")
        other.goTo("2027-04-05")

        val restores = restoresInto(other, fresh, BackupCodec())
        val outcome = restores.replace(restores.stage(withoutChains.toByteArray()).valueOrFail())

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(3L, other.long("SELECT COUNT(*) FROM expense"))
        assertEquals(emptyList<String>(), other.chainRows())
    }

    @Test
    fun theCustomerPathMakesNoBackupOfAHistoryItCouldNotRestore() = runBlocking {
        CarryHistories(ledger).sc01()

        ledger.audit = Instant.parse("2027-04-05T15:00:00Z")

        val created = export(ledger, BackupCodec()).create()
        val prepared = export(ledger, BackupCodec()).prepare()

        assertEquals(
            Outcome.Failure(BackupExportFailure.Unrestorable(BackupRejection.CarryNotSupported)),
            created
        )
        assertEquals(Outcome.Failure(BackupPreparationFailure.Unrestorable), prepared)
    }

    // Validation before any change.

    @Test
    fun aFileWithABrokenChainChangesNothingAndIsNotKept() = runBlocking {
        CarryHistories(ledger).sc01()
        val good = String(backup())
        CarryHistories(other).sc01()
        val before = everyRow(other)
        val restores = restoresInto(other, fresh)
        val overlapping = good.replace(
            "\"end\": null",
            "\"end\": null\n      },\n      {\n        \"categoryId\": \"${uuid(100)}\"," +
                "\n        \"start\": \"2027-03\",\n        \"end\": \"2027-04\""
        )
        val baseAboveCap = good.replace("\"amount\": 100000,", "\"amount\": 1000000000000,")
        val noBase = good.replace(
            "\"kind\": \"finite\",\n        \"amount\": 100000,\n        \"currency\": \"COP\"",
            "\"kind\": \"unlimited\",\n        \"amount\": null,\n        \"currency\": null"
        )
        for (file in listOf(overlapping, baseAboveCap, noBase)) {
            check(file != good)

            val staged = restores.stage(file.toByteArray())

            assertEquals(
                Outcome.Failure(StageFailure.Rejected(RestoreRejection.InvalidContent)),
                staged
            )
            assertEquals(before, everyRow(other))
            assertEquals(emptyList<String>(), stagingFiles(fresh))
        }
        assertNull(restores.staged())
    }

    // Replacement: fencing, one commit, resumable settings.

    @Test
    fun aReplacementWithChainsFencesCarryCommandsAndUndoPreparedBefore() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        CarryHistories(other).sc01()
        // An expense of the current month, deleted: its undo is what a stale screen would hold.
        other.expenses.create(other.createExpense(100, "2027-04-02", 5_000))
        val deletion = other.operation()
        other.expenses.delete(DeleteExpense(deletion, 1, expenseIdOf(104), 1))
        endedInApril(other)
        assertEquals(1L, other.long("SELECT COUNT(*) FROM deleted_expense"))
        val restores = restoresInto(other, fresh)
        restores.replace(restores.stage(bytes).valueOrFail())

        val stale = other.categories.submit(
            EnableCarry(other.operation(), 1, other.categoryId(100), 1)
        )
        val staleDisable = other.categories.submit(
            DisableCarry(other.operation(), 1, other.categoryId(100), 1, cop(-40_000))
        )
        val undo = other.expenses.restore(RestoreExpense(other.operation(), 1, deletion))

        val fenced = CategoryCommandOutcome.Rejected(CategoryRejection.StaleGeneration(2))
        assertEquals(fenced, stale)
        assertEquals(fenced, staleDisable)
        assertEquals(
            ExpenseCommandOutcome.Rejected(ExpenseRejection.StaleGeneration(2)),
            undo
        )
        assertEquals(listOf("${uuid(100)}|2027-01|null"), other.chainRows())
        assertEquals(0L, other.long("SELECT COUNT(*) FROM deleted_expense"))
    }

    @Test
    fun askingAgainAfterACrashBeforeTheFileMovedReplaysTheSameChainReplacement() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        other.goTo("2027-04-05")
        // A preference that fails keeps the files, as a crash right after the commit would.
        preferences.fail = true
        val staged = restoresInto(other, fresh).stage(bytes).valueOrFail()
        restoresInto(other, fresh).replace(staged)
        val rows = everyRow(other)
        File(
            fresh.restoreFolder,
            "restored.json"
        ).renameTo(File(fresh.restoreFolder, "staged.json"))
        other.reopen()

        val again = restoresInto(other, fresh).replace(staged)

        assertTrue("$again", again is RestoreOutcome.Restored)
        assertEquals(rows, everyRow(other))
        assertEquals(
            1L,
            other.long("SELECT COUNT(*) FROM operation_receipt WHERE kind = 'history.restore'")
        )
        assertEquals(listOf("${uuid(100)}|2027-01|null"), other.chainRows())
        assertEquals(sc01, comida(other, "2027-04").replay.months.map { it.text() })
    }

    @Test
    fun aPreferenceFailureAfterTheReplacementLeavesTheChainsAndResumesAfterARestart() =
        runBlocking {
            CarryHistories(ledger).sc01()
            val bytes = backup()
            other.goTo("2027-04-05")
            preferences.fail = true
            val staged = restoresInto(other, fresh).stage(bytes).valueOrFail()

            val outcome = restoresInto(other, fresh).replace(staged)
            val history = everyRow(other)

            // The money is replaced, chains included, but the restore is not complete.
            assertTrue("$outcome", outcome is RestoreOutcome.Restored)
            assertEquals(
                PendingPreferences(appearance = preferences.wanted, ordering = null),
                (outcome as RestoreOutcome.Restored).preferences
            )
            assertEquals(listOf("${uuid(100)}|2027-01|null"), other.chainRows())

            other.reopen()
            val pending = restoresInto(other, fresh).pending()
            assertEquals(outcome, pending)
            preferences.fail = false
            val done = restoresInto(other, fresh).applyPending()

            assertEquals(PendingPreferences(null, null), done?.preferences)
            assertEquals(history, everyRow(other))
            assertNull(restoresInto(other, fresh).pending())
            assertEquals(emptyList<String>(), stagingFiles(fresh))
            assertEquals(sc01, comida(other, "2027-04").replay.months.map { it.text() })
        }

    @Test
    fun aFailureInsideTheReplacementRollsTheChainsBackToo() = runBlocking {
        CarryHistories(ledger).sc01()
        val bytes = backup()
        CarryHistories(other).sc01()
        endedInApril(other)
        val staged = restoresInto(other, fresh).stage(bytes).valueOrFail()
        // The revision cannot advance past its largest value, so the replacement fails
        // after its deletes and inserts have run, inside the transaction.
        other.database.openHelper.writableDatabase.execSQL(
            "UPDATE profile SET revision = ${Long.MAX_VALUE}"
        )
        val before = everyRow(other)

        val outcome = restoresInto(other, fresh).replace(staged.copy(revision = Long.MAX_VALUE))

        assertEquals(RestoreOutcome.Failed, outcome)
        assertEquals(before, everyRow(other))
        assertEquals(listOf("${uuid(100)}|2027-01|2027-04"), other.chainRows())
    }
}
