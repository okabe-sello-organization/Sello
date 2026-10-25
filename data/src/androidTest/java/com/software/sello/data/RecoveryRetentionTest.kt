package com.software.sello.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.RestoreOutcome
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The whole data lifecycle on real files, one step feeding the next: a version 1
 * database a person could have made (archived category, budget history, past-month
 * limits, ordered expenses with notes, two kinds of income, a receipt) is opened and
 * upgraded by this build, saved as a backup, restored into another installation,
 * erased there, and restored again. Every expected row is written out by hand from the
 * fixture below, never read back from the code under test.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryRetentionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, SelloDatabase::class.java)

    private val old = StorageNames("retention-old.db", "retention-old")
    private val new = StorageNames("retention-new.db", "retention-new")
    private val opened = mutableListOf<FinancialStorage>()
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }
    private val at = Instant.parse("2026-10-22T14:30:00Z")
    private var nextOperation = 9_000

    @After
    fun removeFiles() {
        opened.forEach { it.close() }
        for (names in listOf(old, new)) {
            context.deleteDatabase(names.database)
            File(context.filesDir, names.restoreFolder).deleteRecursively()
            File(context.filesDir, names.resetFolder).deleteRecursively()
        }
    }

    /** The person's preferences, read for the backup; what a restore writes is recorded. */
    private class Preferences(
        private val appearance: AppearancePreference,
        private val ordering: CategoryOrderingPreference
    ) : AppearancePreferences,
        CategoryOrderingPreferences {
        val written = mutableListOf<Any>()

        private fun write(value: Any): PreferenceWrite {
            written += value
            return PreferenceWrite.Saved
        }

        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> =
            flowOf(PreferenceRead.Ready(appearance))

        override suspend fun setTheme(theme: ThemePreference) = write(theme)

        override suspend fun setInk(ink: InkPreference) = write(ink)

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(PreferenceRead.Ready(ordering))

        override suspend fun setCategoryOrder(order: CategoryOrder) = write(order)

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            write(direction)
    }

    private val chosen = Preferences(
        AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
        CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending)
    )

    /** A version 1 file written from the exported schema alone, as an old build left it. */
    private fun versionOneHistory() {
        val created = 1_760_000_000_000L
        val c1 = uuid(1)
        val c2 = uuid(2)
        val c3 = uuid(3)
        helper.createDatabase(old.database, 1).use { db ->
            listOf(
                "INSERT INTO profile VALUES (1, 'America/Lima', 3, 12, $created)",
                "INSERT INTO category VALUES ('$c1', 'Mercado', 'mercado', 'shopping_cart', " +
                    "0, 2, $created, $created)",
                "INSERT INTO category VALUES ('$c2', 'Café', 'cafe', 'restaurant', 1, 3, " +
                    "$created, $created)",
                "INSERT INTO category VALUES ('$c3', 'Transporte', 'transporte', 'home', 0, 1, " +
                    "$created, $created)",
                "INSERT INTO default_limit VALUES ('$c1', '2026-08', 'finite', 300000, 'COP')",
                "INSERT INTO default_limit VALUES ('$c1', '2026-10', 'finite', 350000, 'COP')",
                "INSERT INTO default_limit VALUES ('$c2', '2026-08', 'finite', 80000, 'COP')",
                "INSERT INTO default_limit VALUES ('$c3', '2026-09', 'unlimited', NULL, 'COP')",
                "INSERT INTO month_limit VALUES ('$c1', '2026-09', 'finite', 320000, 'COP')",
                "INSERT INTO month_limit VALUES ('$c3', '2026-10', 'unlimited', NULL, 'COP')",
                "INSERT INTO expense VALUES ('${uuid(
                    11
                )}', '$c1', '2026-08-05', 1, 48700, 'COP', " +
                    "'Plaza', 1, $created, $created)",
                "INSERT INTO expense VALUES ('${uuid(12)}', '$c2', '2026-08-06', 2, 6500, 'COP', " +
                    "NULL, 1, $created, $created)",
                "INSERT INTO expense VALUES ('${uuid(
                    13
                )}', '$c1', '2026-09-02', 3, 120000, 'COP', " +
                    "'Quincena de mercado', 2, $created, $created)",
                "INSERT INTO expense VALUES ('${uuid(14)}', '$c3', '2026-10-01', 4, 9200, 'COP', " +
                    "NULL, 1, $created, $created)",
                "INSERT INTO expense VALUES ('${uuid(
                    15
                )}', '$c1', '2026-10-01', 5, 15300, 'COP', " +
                    "'Pan', 1, $created, $created)",
                "INSERT INTO income VALUES ('${uuid(21)}', '2026-08-01', 1, 4200000, 'COP', " +
                    "'salary', NULL, NULL, 1, $created, $created)",
                "INSERT INTO income VALUES ('${uuid(22)}', '2026-09-15', 2, 350000, 'COP', " +
                    "'other', 'Venta', 'Bicicleta', 1, $created, $created)",
                "INSERT INTO operation_receipt VALUES ('${uuid(31)}', 'expense.create', " +
                    "'${"ab".repeat(32)}', 3, 12, '${uuid(15)}', $created)"
            ).forEach(db::execSQL)
        }
    }

    /** Every financial row, as the person would recognise it, in a stable order. */
    private val financialHistory = listOf(
        listOf(
            "${uuid(1)}|Mercado|shopping_cart|0|2",
            "${uuid(2)}|Café|restaurant|1|3",
            "${uuid(3)}|Transporte|home|0|1"
        ),
        listOf(
            "${uuid(1)}|2026-08|finite|300000",
            "${uuid(1)}|2026-10|finite|350000",
            "${uuid(2)}|2026-08|finite|80000",
            "${uuid(3)}|2026-09|unlimited|null"
        ),
        listOf("${uuid(1)}|2026-09|finite|320000", "${uuid(3)}|2026-10|unlimited|null"),
        listOf(
            "${uuid(11)}|${uuid(1)}|2026-08-05|1|48700|Plaza|1",
            "${uuid(12)}|${uuid(2)}|2026-08-06|2|6500|null|1",
            "${uuid(13)}|${uuid(1)}|2026-09-02|3|120000|Quincena de mercado|2",
            "${uuid(14)}|${uuid(3)}|2026-10-01|4|9200|null|1",
            "${uuid(15)}|${uuid(1)}|2026-10-01|5|15300|Pan|1"
        ),
        listOf(
            "${uuid(21)}|2026-08-01|1|4200000|salary|null|null|1",
            "${uuid(22)}|2026-09-15|2|350000|other|Venta|Bicicleta|1"
        )
    )

    /**
     * The same history after a restore. A backup carries no record versions (they only
     * detect conflicting edits, see the backup format), so every record starts at 1.
     */
    private val restoredHistory = listOf(
        listOf(
            "${uuid(1)}|Mercado|shopping_cart|0|1",
            "${uuid(2)}|Café|restaurant|1|1",
            "${uuid(3)}|Transporte|home|0|1"
        ),
        financialHistory[1],
        financialHistory[2],
        listOf(
            "${uuid(11)}|${uuid(1)}|2026-08-05|1|48700|Plaza|1",
            "${uuid(12)}|${uuid(2)}|2026-08-06|2|6500|null|1",
            "${uuid(13)}|${uuid(1)}|2026-09-02|3|120000|Quincena de mercado|1",
            "${uuid(14)}|${uuid(3)}|2026-10-01|4|9200|null|1",
            "${uuid(15)}|${uuid(1)}|2026-10-01|5|15300|Pan|1"
        ),
        financialHistory[4]
    )

    private fun rows(names: StorageNames, sql: String): List<String> = SQLiteDatabase.openDatabase(
        context.getDatabasePath(names.database).path,
        null,
        SQLiteDatabase.OPEN_READONLY
    ).use { db ->
        db.rawQuery(sql, null).use { cursor ->
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
    }

    private fun history(names: StorageNames) = listOf(
        rows(names, "SELECT id, name, icon, archived, version FROM category ORDER BY id"),
        rows(
            names,
            "SELECT category_id, effective_month, kind, limit_minor FROM default_limit " +
                "ORDER BY category_id, effective_month"
        ),
        rows(names, "SELECT category_id, month, kind, limit_minor FROM month_limit ORDER BY month"),
        rows(
            names,
            "SELECT id, category_id, effective_date, sequence, amount_minor, note, version " +
                "FROM expense ORDER BY effective_date, sequence"
        ),
        rows(
            names,
            "SELECT id, effective_date, sequence, amount_minor, source_key, source_name, note, " +
                "version FROM income ORDER BY effective_date, sequence"
        )
    )

    private fun profile(names: StorageNames) =
        rows(names, "SELECT financial_zone, generation FROM profile").single()

    private fun open(names: StorageNames): FinancialStorage =
        openFinancialStorage(context, Dispatchers.IO, { at }, names).also { opened += it }

    private fun operation() = OperationId.of(uuid(nextOperation++)).valueOrFail()

    @Test
    fun aVersionOneHistorySurvivesUpgradeBackupRestoreResetAndRestoreAgain() = runBlocking {
        versionOneHistory()

        // 1. This build opens the old file: migrated, not replaced.
        val upgraded = open(old)
        assertEquals(
            ZoneId.of("America/Lima"),
            upgraded.profiles.establish(ZoneId.of("Asia/Tokyo")).valueOrFail().zone
        )
        assertEquals(financialHistory, history(old))
        assertEquals("America/Lima|3", profile(old))

        // 2. Its backup holds exactly that history and the person's preferences.
        val prepared = upgraded.backupExports(chosen, chosen, { at }, dispatchers).prepare()
            .valueOrFail()
        assertEquals(
            BackupSummary(categories = 3, expenses = 5, incomes = 2, 0, 0),
            prepared.summary
        )
        val contents = BackupCodec().read(prepared.bytes).valueOrFail().contents
        assertEquals(ZoneId.of("America/Lima"), contents.financial.zone)
        assertEquals(
            listOf("Mercado" to false, "Café" to true, "Transporte" to false),
            contents.financial.categories.map { it.name.value to it.archived }
        )
        assertEquals(
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
            contents.preferences?.appearance
        )

        // 3. Restored into another installation made in Bogotá: the same history and zone,
        //    every record at version 1.
        val other = open(new)
        other.profiles.establish(ZoneId.of("America/Bogota")).valueOrFail()
        val applied = Preferences(AppearancePreference(), CategoryOrderingPreference())
        val restores = other.restores(applied, applied, {
            uuid(nextOperation++)
        }, { at }, dispatchers)
        val restored = restores.replace(restores.stage(prepared.bytes).valueOrFail())
        assertEquals(
            RestoreOutcome.Restored(BackupSummary(3, 5, 2, 0, 0), PendingPreferences(null, null)),
            restored
        )
        assertEquals(restoredHistory, history(new))
        assertEquals("America/Lima|2", profile(new))
        assertEquals(
            listOf<Any>(
                ThemePreference.Dark,
                InkPreference.Violeta,
                CategoryOrder.Alphabetical,
                CategoryOrderDirection.Ascending
            ),
            applied.written
        )

        // 4. Erased there: nothing financial left, a new generation, the device's zone.
        val resets = other.resets({ PreferenceWrite.Saved }, { ZoneId.of("America/Bogota") }, {
            at
        }, dispatchers)
        val erased = resets.reset(resets.preview().valueOrFail(), operation())
        assertEquals(ResetOutcome.Erased(preferencesPending = false), erased)
        assertTrue(history(new).all { it.isEmpty() })
        assertEquals("America/Bogota|3", profile(new))

        // 5. The same file restores again across that boundary, exactly as before.
        val again = restores.replace(restores.stage(prepared.bytes).valueOrFail())
        assertTrue("$again", again is RestoreOutcome.Restored)
        assertEquals(restoredHistory, history(new))
        assertEquals("America/Lima|4", profile(new))
        // The old installation was only read: still the upgraded history.
        assertEquals(financialHistory, history(old))
    }
}
