package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.RoomRestores
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StageFailure
import com.software.sello.domain.port.StagedLookup
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A FinanceTracker backup restored through the released restore over a real database
 * file (ADR 0019). The current history is one Mercado expense written by the real
 * commands. The legacy file below is synthetic; every expected row and count is
 * written out by hand from it.
 */
@RunWith(AndroidJUnit4::class)
class LegacyImportRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val dispatchers = realDispatchers

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

    private val investments = HandInvestmentPreferences()

    private val preferences = Preferences()
    private var nextId = 5_000

    private fun restores() = RoomRestores(
        ledger.database,
        files.restoreFolder,
        preferences,
        preferences,
        investments,
        NoticeSeen(),
        { uuid(nextId++) },
        { Instant.parse("2026-10-25T20:00:00Z") },
        dispatchers
    )

    /**
     * Two categories kept, one renamed ("mercado" clashes with "Mercado"), one left out
     * for an empty name with its expense; one expense left out for a zero amount; a
     * salary schedule with the October income it recorded; a goal; a USD investment of USD
     * 1.000,00 with USD 15,00 of interest, and a dollar rate of 4.150,125.
     */
    private val legacy = """
        {"exportedAt": "2026-10-25 14:01",
         "preferences": {"themeMode": "LIGHT", "homeSortField": "BUDGET_LIMIT",
                         "homeSortDirection": "ASC", "defaultCurrency": "COP"},
         "categories": [
           {"id": 1, "name": "Mercado", "colorArgb": -1, "iconKey": "shopping_cart",
            "monthlyLimitCop": 300000, "updatedAt": 1787000000000},
           {"id": 2, "name": "mercado", "colorArgb": -1, "iconKey": "work",
            "monthlyLimitCop": null, "updatedAt": 1787000000000},
           {"id": 3, "name": "", "colorArgb": -1, "iconKey": "work",
            "monthlyLimitCop": 1000, "updatedAt": 1787000000000}],
         "expenses": [
           {"id": 1, "categoryId": 1, "amountCop": 20000, "description": "Pan",
            "date": "2026-09-02"},
           {"id": 2, "categoryId": 2, "amountCop": 15000, "description": "",
            "date": "2026-10-03"},
           {"id": 3, "categoryId": 3, "amountCop": 5000, "description": "Huérfano",
            "date": "2026-10-04"},
           {"id": 4, "categoryId": 1, "amountCop": 0, "description": "Cero",
            "date": "2026-10-04"}],
         "recurringExpenses": [],
         "income": [
           {"id": 1, "amountCop": 4000000, "source": "Salario", "date": "2026-10-01",
            "recurringIncomeId": 1}],
         "recurringIncome": [
           {"id": 1, "amountCop": 4000000, "source": "Salario", "recurrenceType": "MONTHLY",
            "startDate": "2026-09-15", "nextDueDate": "2026-11-01"}],
         "investments": [
           {"id": 1, "name": "Fondo", "colorArgb": -1, "iconKey": "trending_up",
            "currency": "USD", "createdDate": "2026-09-01", "updatedAt": 1787000000000}],
         "investmentEntries": [
           {"id": 1, "investmentId": 1, "entryType": "CASH_INJECTION",
            "amountMinorUnits": 100000, "date": "2026-09-01"},
           {"id": 2, "investmentId": 1, "entryType": "INTEREST", "amountMinorUnits": 1500,
            "date": "2026-10-01", "notes": "Interés"}],
         "exchangeRates": [
           {"fromCurrency": "USD", "toCurrency": "COP", "rate": 4150.125,
            "updatedDate": "2026-10-20 08:30"}],
         "goals": [
           {"id": 1, "name": "Viaje", "targetAmountCop": 3000000, "currentAmountCop": 1000000,
            "deadlineDate": "2027-01-31", "colorArgb": -1}]}
    """.trimIndent()

    private val report = LegacyImportReport(
        listOf(
            LegacyImportLine(LegacyRecord.Category, LegacyReason.Renamed, 1),
            LegacyImportLine(LegacyRecord.Category, LegacyReason.InvalidText, 1),
            LegacyImportLine(LegacyRecord.Expense, LegacyReason.AmountOutOfRange, 1),
            LegacyImportLine(LegacyRecord.Expense, LegacyReason.CategoryLeftOut, 1),
            LegacyImportLine(LegacyRecord.Investment, LegacyReason.ValueAdded, 1),
            LegacyImportLine(LegacyRecord.InvestmentEntry, LegacyReason.InterestAsValue, 1),
            LegacyImportLine(LegacyRecord.ExchangeRate, LegacyReason.Rounded, 1)
        )
    )

    private suspend fun currentHistory() {
        ledger.categories.submit(ledger.createCategory("Mercado", finite(50_000)))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000))
    }

    private fun query(sql: String): List<String> =
        ledger.database.openHelper.readableDatabase.query(sql).use { cursor ->
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

    private fun everyRow(): List<List<String>> = listOf(
        query("SELECT name, icon, archived FROM category ORDER BY name"),
        query(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name"
        ),
        query("SELECT COUNT(*) FROM month_limit"),
        query(
            "SELECT c.name, e.effective_date, e.sequence, e.amount_minor, e.note FROM expense e " +
                "JOIN category c ON c.id = e.category_id ORDER BY e.effective_date"
        ),
        query(
            "SELECT i.effective_date, i.sequence, i.amount_minor, i.source_key, " +
                "i.origin_schedule_id = s.id, i.origin_date, i.origin_slot " +
                "FROM income i LEFT JOIN schedule s ON s.id = i.origin_schedule_id"
        ),
        query(
            "SELECT kind, source_key, amount_minor, rule, rule_first, start_date, " +
                "handled_date, handled_slot FROM schedule"
        ),
        query("SELECT COUNT(*) FROM schedule_pause"),
        query("SELECT name, icon_key, target_month, completed_on FROM goal"),
        query("SELECT kind, amount_minor FROM goal_event ORDER BY sequence"),
        query("SELECT name, icon_key, currency, next_sequence FROM investment"),
        query(
            "SELECT sequence, kind, amount_minor, currency, effective_date, note " +
                "FROM investment_movement ORDER BY sequence"
        ),
        query("SELECT rate_hundredths, effective_date, sequence FROM usd_rate"),
        query("SELECT financial_zone, generation FROM profile")
    )

    private fun stagingFiles(): List<String> = files.restoreFolder.list()?.sorted().orEmpty()

    @Test
    fun aLegacyFileIsPreviewedWithItsReportAndRestoredThroughTheReleasedRestore() = runBlocking {
        currentHistory()

        val staged = restores().stage(legacy.toByteArray()).valueOrFail()
        val outcome = restores().replace(staged)

        assertEquals(report, staged.legacy)
        assertEquals(BackupSummary(1, 1, 0, 0, 0), staged.current)
        assertEquals(BackupSummary(2, 2, 1, 1, 1, 1), staged.replacement)
        assertEquals(Instant.parse("2026-10-25T19:01:00Z"), staged.backupAt)
        assertTrue(outcome is RestoreOutcome.Restored)
        assertEquals(
            listOf(
                listOf("Mercado|shopping_cart|0", "mercado_2|work|0"),
                listOf("Mercado|2026-09|finite|300000", "mercado_2|2026-09|unlimited|null"),
                listOf("0"),
                listOf("Mercado|2026-09-02|1|20000|Pan", "mercado_2|2026-10-03|2|15000|null"),
                listOf("2026-10-01|1|4000000|salary|1|2026-10-01|1"),
                listOf("income|salary|4000000|monthly|1|2026-09-15|2026-10-01|1"),
                listOf("0"),
                listOf("Viaje|savings|2027-01|null"),
                listOf("target|3000000", "contribution|1000000"),
                listOf("Fondo|trending_up|USD|3"),
                // The added first value, then the interest as the value it reached.
                listOf(
                    "0|contribution|100000|USD|2026-09-01|null",
                    "1|valuation|100000|USD|2026-09-01|null",
                    "2|valuation|101500|USD|2026-10-01|Interés"
                ),
                listOf("415013|2026-10-20|1"),
                // The current financial zone, and a new generation.
                listOf("America/Bogota|2")
            ),
            everyRow()
        )
        assertEquals(
            listOf<Any>(
                ThemePreference.Light,
                InkPreference.Cobalto,
                CategoryOrder.Limit,
                CategoryOrderDirection.Ascending
            ),
            preferences.written
        )
        assertEquals(emptyList<String>(), stagingFiles())
    }

    /** The report is kept with the staged file, so a restart still shows what was left out. */
    @Test
    fun theReportOfAStagedLegacyFileSurvivesARestart() = runBlocking {
        currentHistory()
        restores().stage(legacy.toByteArray()).valueOrFail()

        ledger.reopen()
        val resumed = (restores().staged() as? StagedLookup.Waiting)?.staged

        assertEquals(report, resumed?.legacy)
        assertEquals(BackupSummary(2, 2, 1, 1, 1, 1), resumed?.replacement)
    }

    /** Marking a confirmation in the manifest keeps the report written beside it. */
    @Test
    fun theReportOfAConfirmedLegacyFileSurvivesARestart() = runBlocking {
        currentHistory()
        val staged = restores().stage(legacy.toByteArray()).valueOrFail()
        // Refused as stale, after the confirmation was marked.
        assertEquals(
            RestoreOutcome.Stale,
            restores().replace(staged.copy(revision = staged.revision + 1))
        )

        ledger.reopen()

        assertEquals(StagedLookup.Waiting(staged, attempted = true), restores().staged())
    }

    @Test
    fun aCanceledOrRefusedLegacyFileChangesNothingAndIsNotKept() = runBlocking {
        currentHistory()
        val before = everyRow()

        restores().stage(legacy.toByteArray()).valueOrFail()
        restores().discard()
        val newer = restores().stage(("{\"version\": 3, " + legacy.drop(1)).toByteArray())
        val neither = restores().stage("{\"exportedAt\": \"2026-10-25 14:01\"}".toByteArray())

        assertEquals(Outcome.Failure(StageFailure.Rejected(RestoreRejection.NewerVersion)), newer)
        assertEquals(Outcome.Failure(StageFailure.Rejected(RestoreRejection.NotABackup)), neither)
        assertEquals(before, everyRow())
        assertEquals(emptyList<String>(), stagingFiles())
        assertTrue(preferences.written.isEmpty())
    }
}
