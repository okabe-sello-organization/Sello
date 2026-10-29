package com.software.sello.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.check
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CheckedBackup
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RestoreOutcome
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Restoring from the start-up failure screen, on real files: a database that will not
 * open is set aside, the restore an earlier process left staged for it is cleared, and
 * the new store is filled from a backup. The backup is G01 in Lima; expected rows are
 * written out by hand.
 */
@RunWith(AndroidJUnit4::class)
class RestoreAfterSetAsideTest {
    @get:Rule
    val files = DatabaseFiles()

    private val names get() = StorageNames(files.name, "unused-preferences")
    private val kept: File get() = files.context.getDatabasePath(names.setAsideDatabase)
    private val bogota = ZoneId.of("America/Bogota")
    private val opened = mutableListOf<FinancialStorage>()
    private val dispatchers = realDispatchers

    /** Preferences that record what was applied. */
    private class Preferences :
        AppearancePreferences,
        CategoryOrderingPreferences {
        val written = mutableListOf<Any>()

        private fun write(value: Any): PreferenceWrite {
            written += value
            return PreferenceWrite.Saved
        }

        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = emptyFlow()

        override suspend fun setTheme(theme: ThemePreference) = write(theme)

        override suspend fun setInk(ink: InkPreference) = write(ink)

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            emptyFlow()

        override suspend fun setCategoryOrder(order: CategoryOrder) = write(order)

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            write(direction)
    }

    @After
    fun closeAndRemoveKeptCopy() {
        opened.forEach { it.close() }
        files.context.deleteDatabase(kept.name)
    }

    private fun open(): FinancialStorage =
        openFinancialStorage(files.context, Dispatchers.IO, { recordedAt }, names)
            .also { opened += it }

    private val g01 = BackupCodec().encode(
        BackupContents(
            exportedAt = Instant.parse("2026-10-22T14:30:00Z"),
            financial = FinancialSnapshot(
                zone = ZoneId.of("America/Lima"),
                revision = 9,
                categories = listOf(category(1, "Comida")),
                defaultLimits = emptyList(),
                monthLimits = emptyList(),
                expenses = listOf(
                    expense(101, 1, "2026-10-01", 1, 10_000),
                    expense(102, 1, "2026-10-02", 2, 15_000),
                    expense(103, 1, "2026-10-03", 3, 5_000)
                ),
                incomes = listOf(income(201, "2026-10-01", 1, 200_000, IncomeSource.Salary)),
                goals = emptyList(),
                featuredGoal = null,
                schedules = emptyList(),
                investments = emptyList(),
                usdRates = emptyList()
            ),
            preferences = PortablePreferences(
                AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
                CategoryOrderingPreference(
                    CategoryOrder.Alphabetical,
                    CategoryOrderDirection.Ascending
                )
            )
        )
    ).valueOrFail()

    private fun rows(sql: String): List<String> =
        SQLiteDatabase.openDatabase(files.file.path, null, SQLiteDatabase.OPEN_READONLY).use {
            it.rawQuery(sql, null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add((0 until cursor.columnCount).joinToString("|") { cursor.getString(it) })
                    }
                }
            }
        }

    @Test
    fun aBackupIsCheckedWithoutAnyStorage() {
        assertEquals(
            Outcome.Success(
                CheckedBackup(
                    Instant.parse("2026-10-22T14:30:00Z"),
                    ZoneId.of("America/Lima"),
                    BackupSummary(categories = 1, expenses = 3, incomes = 1, 0, 0)
                )
            ),
            BackupCodec().check(g01)
        )
        assertFalse(files.file.exists())
    }

    @Test
    fun theUnreadableFileIsKeptAndTheNewStoreHoldsExactlyTheBackup() = runBlocking {
        val damaged = ByteArray(8192) { (it * 31 + 7).toByte() }
        files.file.parentFile!!.mkdirs()
        files.file.writeBytes(damaged)
        // A restore an earlier process staged for the file that will not open.
        files.restoreFolder.mkdirs()
        File(files.restoreFolder, "restored.json").writeText("{}")
        val failed = open()
        assertTrue(failed.profiles.establish(bogota) is Outcome.Failure)
        failed.close()

        assertEquals(Outcome.Success(Unit), setAsideFinancialStorage(files.context, names))
        assertFalse(files.restoreFolder.exists())
        val storage = open()
        storage.profiles.establish(bogota).valueOrFail()
        val preferences = Preferences()
        val restores = storage.restores(
            preferences,
            preferences,
            HandInvestmentPreferences(),
            NoticeSeen(),
            { uuid(5_000) },
            { recordedAt },
            dispatchers
        )
        val staged = restores.stage(g01).valueOrFail()
        val outcome = restores.replace(staged)

        assertEquals(BackupSummary(0, 0, 0, 0, 0), staged.current)
        assertEquals(
            RestoreOutcome.Restored(BackupSummary(1, 3, 1, 0, 0), PendingPreferences(null, null)),
            outcome
        )
        storage.close()
        assertEquals(listOf("${uuid(1)}|Comida"), rows("SELECT id, name FROM category"))
        assertEquals(
            listOf(
                "${uuid(101)}|2026-10-01|10000",
                "${uuid(102)}|2026-10-02|15000",
                "${uuid(103)}|2026-10-03|5000"
            ),
            rows("SELECT id, effective_date, amount_minor FROM expense ORDER BY sequence")
        )
        assertEquals(listOf("200000"), rows("SELECT amount_minor FROM income"))
        // The backup's zone and the generation after the new store's first.
        assertEquals(
            listOf("America/Lima|2"),
            rows("SELECT financial_zone, generation FROM profile")
        )
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertArrayEquals(damaged, kept.readBytes())
        assertEquals(emptyList<String>(), files.restoreFolder.list()?.toList().orEmpty())
    }
}
