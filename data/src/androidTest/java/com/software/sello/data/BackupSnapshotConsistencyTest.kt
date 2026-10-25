package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupCounts
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CreateIncome
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.SetMonthLimit
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Backups of a real database file written by the real commands. The financial day is
 * 9 October 2026 in Bogotá; the history is G01 plus an archived category.
 */
@RunWith(AndroidJUnit4::class)
class BackupSnapshotConsistencyTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val september = YearMonth.of(2026, 9)
    private val october = YearMonth.of(2026, 10)

    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }

    private class Preferences(
        private val appearance: PreferenceRead<AppearancePreference>,
        private val ordering: PreferenceRead<CategoryOrderingPreference>
    ) : AppearancePreferences,
        CategoryOrderingPreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> =
            flowOf(appearance)

        override suspend fun setTheme(theme: ThemePreference): PreferenceWrite = error("read only")

        override suspend fun setInk(ink: InkPreference): PreferenceWrite = error("read only")

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(ordering)

        override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite =
            error("read only")

        override suspend fun setCategoryOrderDirection(
            direction: CategoryOrderDirection
        ): PreferenceWrite = error("read only")
    }

    private val darkViolet = Preferences(
        PreferenceRead.Ready(AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)),
        PreferenceRead.Ready(CategoryOrderingPreference())
    )

    private fun export(
        preferences: Preferences = darkViolet,
        now: Instant = Instant.parse("2026-10-22T19:30:00Z")
    ) = BackupExport(
        RoomBackupSnapshots(ledger.database),
        preferences,
        preferences,
        { now },
        dispatchers
    )

    private suspend fun spend(date: String, pesos: Long) {
        ledger.expenses.create(ledger.createExpense(100, date, pesos))
    }

    /** G01 in Mercado (100), and Café (101) limited in September then archived. */
    private suspend fun writeG01() {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        ledger.categories.submit(ledger.createCategory("Café", BudgetLimit.Unlimited))
        ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(101),
                1,
                september,
                finite(40_000)
            )
        )
        ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ledger.categoryId(101), 2))
        spend("2026-10-01", 10_000)
        spend("2026-10-02", 15_000)
        spend("2026-10-03", 5_000)
        ledger.incomes.create(
            CreateIncome(
                operationId = ledger.operation(),
                generation = 1,
                amount = TransactionAmount.of(Money.cop(200_000)).valueOrFail(),
                source = IncomeSource.Salary,
                date = LocalDate.parse("2026-10-01"),
                note = Note.of(null).valueOrFail()
            )
        )
    }

    private suspend fun snapshot(): FinancialSnapshot =
        RoomBackupSnapshots(ledger.database).read().valueOrFail()

    @Test
    fun aSnapshotHoldsExactlyTheHistoryTheCommandsWrote() = runBlocking {
        writeG01()

        val snapshot = snapshot()

        // Two categories, a month limit, an archive, three expenses and an income.
        assertEquals(8L, snapshot.revision)
        assertEquals(ZoneId.of("America/Bogota"), snapshot.zone)
        assertEquals(
            BackupCounts(
                categories = 2,
                archivedCategories = 1,
                defaultLimits = 3,
                monthLimits = 1,
                expenses = 3,
                incomes = 1,
                goals = 0,
                goalEvents = 0,
                schedules = 0
            ),
            snapshot.counts
        )
        assertEquals(
            listOf(10_000L, 15_000L, 5_000L),
            snapshot.expenses.map { it.amount.money.minorUnits }
        )
        assertEquals(listOf(200_000L), snapshot.incomes.map { it.amount.money.minorUnits })
        assertEquals(
            setOf(
                DefaultLimit(
                    ledger.categoryId(100),
                    october,
                    AutomaticBudget.Limit(finite(100_000))
                ),
                DefaultLimit(
                    ledger.categoryId(101),
                    october,
                    AutomaticBudget.Limit(BudgetLimit.Unlimited)
                ),
                DefaultLimit(ledger.categoryId(101), YearMonth.of(2026, 11), AutomaticBudget.Paused)
            ),
            snapshot.defaultLimits.toSet()
        )
        assertEquals(
            listOf(MonthLimit(ledger.categoryId(101), september, finite(40_000))),
            snapshot.monthLimits
        )
    }

    @Test
    fun aBackupReadsBackAsTheSameHistoryAndChangesNothing() = runBlocking {
        writeG01()
        val revision = ledger.revision()
        val before = ledger.rows("expense", "id", "amount_minor", "version")

        val file = export().create().valueOrFail()
        val plan = BackupCodec().read(file.bytes).valueOrFail()
        val stored = snapshot()

        assertEquals("sello-respaldo-2026-10-22-1430.json", file.name)
        assertTrue(file.preferencesIncluded)
        assertEquals(stored.counts, file.counts)
        // A file carries no runtime versions; everything else is exactly what is stored.
        assertEquals(
            stored.copy(
                categories = stored.categories.map { it.copy(version = 1) },
                expenses = stored.expenses.map { it.copy(version = 1) },
                incomes = stored.incomes.map { it.copy(version = 1) }
            ),
            plan.contents.financial
        )
        assertEquals(ThemePreference.Dark, plan.contents.preferences?.appearance?.theme)
        assertEquals(revision, ledger.revision())
        assertEquals(before, ledger.rows("expense", "id", "amount_minor", "version"))
    }

    @Test
    fun aClockFinerThanMillisecondsStillMakesABackupStampedToTheMillisecond() = runBlocking {
        // Newer Android clocks give microseconds; the format keeps milliseconds.
        writeG01()

        val file = export(
            now = Instant.parse("2026-10-22T19:30:00.123456789Z")
        ).create().valueOrFail()

        assertTrue(
            file.bytes.decodeToString().contains("\"exportedAt\": \"2026-10-22T19:30:00.123Z\"")
        )
        assertEquals(3, BackupCodec().read(file.bytes).valueOrFail().counts.expenses)
    }

    @Test
    fun unreadablePreferencesStillLeaveAFullFinancialBackup() = runBlocking {
        writeG01()
        val broken = Preferences(
            PreferenceRead.Failed(PreferenceFailure.Unavailable, null),
            PreferenceRead.Ready(CategoryOrderingPreference())
        )

        val file = export(broken).create().valueOrFail()
        val plan = BackupCodec().read(file.bytes).valueOrFail()

        assertFalse(file.preferencesIncluded)
        assertNull(plan.contents.preferences)
        assertEquals(3, plan.counts.expenses)
    }

    @Test
    fun theSameStoredDataAlwaysMakesTheSameFile() = runBlocking {
        writeG01()

        assertArrayEquals(
            export().create().valueOrFail().bytes,
            export().create().valueOrFail().bytes
        )
    }

    @Test
    fun aSnapshotTakenWhileExpensesAreSavedBelongsToOneRevision() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(100_000)))
        val base = ledger.revision()
        val rounds = 40
        val seen = mutableListOf<FinancialSnapshot>()

        coroutineScope {
            val writer = launch(Dispatchers.Default) {
                repeat(rounds) { spend("2026-10-0${it % 9 + 1}", 1_000L + it) }
            }
            launch(Dispatchers.Default) {
                while (writer.isActive) seen += snapshot()
                seen += snapshot()
            }
        }

        // Each expense advances the revision by exactly one, so a snapshot of one
        // revision holds exactly that many expenses, and each of the right amount.
        for (snapshot in seen) {
            val added = (snapshot.revision - base).toInt()
            assertEquals("revision ${snapshot.revision}", added, snapshot.expenses.size)
            assertEquals(
                "revision ${snapshot.revision}",
                (0 until added).map { 1_000L + it }.toSet(),
                snapshot.expenses.map { it.amount.money.minorUnits }.toSet()
            )
        }
        assertEquals(rounds, seen.last().expenses.size)
    }
}
