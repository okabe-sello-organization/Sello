package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.DispatcherProvider
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StageFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Investments and USD rates through a real backup, erase and restore on a real database
 * file. The history is approved examples IN04 (Fondo, with a mistaken dividend deleted on
 * the way) and IN27 (Acciones EE. UU., in USD), with the rates 4.000,00 and 4.150,25
 * (docs/testing/investment-examples.md). Every expected figure is the examples' own,
 * worked out by hand; the working checkpoint is that the restored board shows the same.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentBackupRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val dispatchers = object : DispatcherProvider {
        override val main = Dispatchers.Default
        override val default = Dispatchers.Default
        override val io = Dispatchers.IO
    }
    private val investments = HandInvestmentPreferences()
    private val preferences = object : AppearancePreferences, CategoryOrderingPreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = flowOf(
            PreferenceRead.Ready(
                AppearancePreference(ThemePreference.System, InkPreference.Cobalto)
            )
        )

        override suspend fun setTheme(theme: ThemePreference) = PreferenceWrite.Saved

        override suspend fun setInk(ink: InkPreference) = PreferenceWrite.Saved

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
    private var nextId = 9_000

    private lateinit var fondo: InvestmentId
    private lateinit var acciones: InvestmentId
    private lateinit var deletion: OperationId

    /**
     * IN04 day by day, a dividend recorded by mistake and deleted (its undo copy kept), the
     * 4.000,00 rate, then IN27 entered with the 4.150,25 rate. Ends on 25 October 2026.
     */
    private suspend fun history() {
        ledger.goTo("2025-05-03")
        fondo = ledger.created(
            ledger.createInvestment(
                "Fondo",
                pesos(3_000_000),
                "2025-05-03",
                description = "Fondo de inversión colectiva"
            )
        ).investmentId!!
        ledger.goTo("2025-11-03")
        ledger.recorded(fondo, InvestmentMovementKind.Contribution, pesos(3_000_000), "2025-11-03")
        ledger.goTo("2026-08-03")
        ledger.recorded(fondo, InvestmentMovementKind.Dividend, pesos(10_000), "2026-08-03")
        val mistaken = ledger.summary(fondo).history.movements
            .single { it.kind == InvestmentMovementKind.Dividend }
        deletion = ledger.operation()
        val deleted = ledger.invest(
            DeleteInvestmentMovement(
                deletion,
                1,
                fondo,
                ledger.investmentVersion(fondo),
                mistaken.id,
                mistaken.version
            )
        )
        assertTrue("$deleted", deleted is InvestmentCommandOutcome.Committed)
        ledger.recorded(fondo, InvestmentMovementKind.Withdrawal, pesos(500_000), "2026-08-03")
        ledger.goTo("2026-10-14")
        ledger.recorded(
            fondo,
            InvestmentMovementKind.Valuation,
            pesosValue(5_242_000),
            "2026-10-14"
        )
        ledger.goTo("2026-10-15")
        assertTrue(ledger.setRate(400_000, "2026-10-15") is InvestmentCommandOutcome.Committed)
        ledger.goTo("2026-10-20")
        acciones = ledger.created(
            ledger.createInvestment(
                "Acciones EE. UU.",
                cents(100_000),
                "2026-10-20",
                value = InvestmentAmount.ofValue(Money(105_050, Currency.USD)).valueOrFail(),
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        ).investmentId!!
        ledger.goTo("2026-10-25")
    }

    private fun export() = BackupExport(
        RoomBackupSnapshots(ledger.database),
        preferences,
        preferences,
        investments,
        { ledger.audit },
        dispatchers
    )

    private suspend fun backup(): ByteArray = export().create().valueOrFail().bytes

    private fun restores() = RoomRestores(
        ledger.database,
        files.restoreFolder,
        preferences,
        preferences,
        investments,
        NoticeSeen(),
        { uuid(nextId++) },
        { ledger.audit },
        dispatchers
    )

    private fun resets() = RoomResets(
        ledger.database,
        files.resetFolder,
        files.restoreFolder,
        emptyList(),
        PreferenceReset { PreferenceWrite.Saved },
        { ledger.zone },
        { ledger.audit },
        dispatchers
    )

    private suspend fun erase() {
        val resets = resets()
        val outcome = resets.reset(resets.preview().valueOrFail(), ledger.operation())
        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
    }

    private suspend fun restore(bytes: ByteArray): RestoreOutcome {
        val restores = restores()
        return restores.replace(restores.stage(bytes).valueOrFail())
    }

    /** What a person sees of the board: no versions, generations or revisions. */
    private fun InvestmentBoard.seen() = listOf(
        investments.map { it.history.investment.copy(version = 1) to it.figures },
        investments.map { summary -> summary.history.movements.map { it.copy(version = 1) } },
        rates,
        when (val made = overview) {
            is Outcome.Success -> Triple(
                made.value.items.map {
                    Triple(it.history.investment.name, it.figures, it.valueInPesos)
                },
                made.value.total,
                made.value.rate
            )

            is Outcome.Failure -> made.error
        }
    )

    @Test
    fun aBackupErasedAndRestoredGivesBackTheSameBoard() = runBlocking {
        history()
        val before = ledger.board()
        val bytes = backup()

        erase()
        assertEquals(emptyList<Any>(), ledger.board().investments)
        val outcome = restore(bytes)

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(2, (outcome as RestoreOutcome.Restored).summary.investments)
        val after = ledger.board()
        assertEquals(before.seen(), after.seen())
        // IN04: 5.242.000 + 500.000 − 6.000.000. IN27: USD 1.050,50 − USD 1.000,00.
        assertEquals(
            listOf(Money.cop(-258_000), Money(5_050, Currency.USD)),
            after.investments.map { it.figures.gain }
        )
        // The latest date is current: 4.150,25 of 20 October, with 4.000,00 kept before it.
        assertEquals(415_025L, after.rates.current?.rate?.hundredths)
        assertEquals(day("2026-10-20"), after.rates.current?.date)
        assertEquals(listOf(415_025L, 400_000L), after.rates.history.map { it.rate.hundredths })
        // Fondo and IN27 at 4.150,25: V 5.242.000 + 4.359.838; C 6.000.000 + 4.150.250;
        // G = 9.601.838 + 500.000 − 10.150.250.
        val total = (after.overview as Outcome.Success).value.total!!
        assertEquals(Money.cop(9_601_838), total.value)
        assertEquals(Money.cop(-48_412), total.gain)
    }

    @Test
    fun aRestoreKeepsRecordingPositionsAndLeavesNoUndoCopies() = runBlocking {
        history()
        assertEquals(1L, ledger.long("SELECT COUNT(*) FROM deleted_investment_movement"))
        val bytes = backup()

        erase()
        restore(bytes)

        // Fondo's positions 0–5 with 3, the deleted dividend, missing; versions start again.
        assertEquals(
            listOf("Acciones EE. UU.|1|2", "Fondo|1|6"),
            ledger.rows("investment", "name", "version", "next_sequence")
        )
        assertEquals(
            listOf(0L, 1L, 2L, 4L, 5L),
            ledger.summary(fondo).history.movements.map { it.sequence }.sorted()
        )
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM deleted_investment_movement"))
        // The deleted dividend's copy did not travel: undoing its deletion is refused.
        val undo = ledger.invest(
            RestoreInvestmentMovement(ledger.operation(), ledger.board().generation, deletion)
        )
        assertTrue("$undo", undo is InvestmentCommandOutcome.Rejected)
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM investment_movement WHERE sequence = 3"))
        // The next movement takes the position after the last one.
        val recorded = ledger.invest(
            RecordInvestmentMovement(
                ledger.operation(),
                ledger.board().generation,
                fondo,
                ledger.investmentVersion(fondo),
                InvestmentMovementKind.Dividend,
                pesos(1),
                day("2026-10-25"),
                null
            )
        )
        assertTrue("$recorded", recorded is InvestmentCommandOutcome.Committed)
        assertEquals(
            6L,
            ledger.long("SELECT MAX(sequence) FROM investment_movement WHERE kind = 'dividend'")
        )
    }

    @Test
    fun restoringAVersion3FileLeavesNoInvestmentsAndNoRates() = runBlocking {
        history()

        val outcome = restore(VERSION_3.toByteArray())

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        val board = ledger.board()
        assertEquals(emptyList<Any>(), board.investments)
        assertNull(board.rates.current)
        assertEquals(emptyList<Any>(), board.rates.history)
        for (table in listOf("investment", "investment_movement", "usd_rate")) {
            assertEquals(table, 0L, ledger.long("SELECT COUNT(*) FROM $table"))
        }
    }

    @Test
    fun aBadVersion4FileChangesNothing() = runBlocking {
        history()
        val good = String(backup())
        val before = ledger.board()
        val revision = ledger.revision()
        val bad = listOf(
            // IN04's retiro above the 6.000.000 it was worth on 3 August 2026.
            good.replace("\"amount\": 500000", "\"amount\": 6000001"),
            // A rate with three decimals.
            good.replace("\"rateHundredths\": 415025", "\"rateHundredths\": 4150.255"),
            // An aporte of 0.
            good.replaceFirst("\"amount\": 3000000", "\"amount\": 0")
        )

        for (text in bad) {
            assertTrue(text != good)
            val staged = restores().stage(text.toByteArray())
            assertEquals(
                Outcome.Failure(StageFailure.Rejected(RestoreRejection.InvalidContent)),
                staged
            )
        }

        assertEquals(revision, ledger.revision())
        assertEquals(before, ledger.board())
    }

    @Test
    fun erasingEverythingRemovesInvestmentsTheirUndoCopiesAndTheRates() = runBlocking {
        history()
        val resets = resets()
        val preview = resets.preview().valueOrFail()
        assertEquals(2, preview.current.investments)

        val outcome = resets.reset(preview, ledger.operation())

        assertEquals(ResetOutcome.Erased(preferencesPending = false), outcome)
        for (
        table in listOf(
            "investment",
            "investment_movement",
            "deleted_investment_movement",
            "usd_rate"
        )
        ) {
            assertEquals(table, 0L, ledger.long("SELECT COUNT(*) FROM $table"))
        }
    }

    @Test
    fun aBackupHoldsExactlyTheStoredInvestmentsAndChangesNothing() = runBlocking {
        history()
        val revision = ledger.revision()
        val stored = RoomBackupSnapshots(ledger.database).read().valueOrFail()

        val file = export().create().valueOrFail()
        val plan = BackupCodec().read(file.bytes).valueOrFail()

        assertEquals(stored.counts, file.counts)
        assertEquals(
            Triple(2, 7, 2),
            Triple(file.counts.investments, file.counts.investmentMovements, file.counts.usdRates)
        )
        // A file carries no runtime versions; everything else is exactly what is stored.
        assertEquals(
            stored.investments.map { history ->
                history.copy(
                    investment = history.investment.copy(version = 1),
                    movements = history.movements.map { it.copy(version = 1) }
                )
            },
            plan.contents.financial.investments
        )
        assertEquals(stored.usdRates, plan.contents.financial.usdRates)
        assertEquals(revision, ledger.revision())
    }

    @Test
    fun aSnapshotTakenWhileMovementsAreRecordedBelongsToOneRevision() = runBlocking {
        ledger.goTo("2026-10-01")
        val cdt = ledger.created(
            ledger.createInvestment("CDT Banco", pesos(10_000_000), "2026-10-01")
        ).investmentId!!
        val base = ledger.revision()
        val rounds = 30
        val seen = mutableListOf<FinancialSnapshot>()

        coroutineScope {
            val writer = launch(Dispatchers.Default) {
                repeat(rounds) {
                    ledger.recorded(
                        cdt,
                        InvestmentMovementKind.Dividend,
                        pesos(1_000L + it),
                        "2026-10-01"
                    )
                }
            }
            launch(Dispatchers.Default) {
                while (writer.isActive) {
                    seen +=
                        RoomBackupSnapshots(ledger.database).read().valueOrFail()
                }
                seen += RoomBackupSnapshots(ledger.database).read().valueOrFail()
            }
        }

        // Each movement advances the revision by exactly one, so a snapshot of one revision
        // holds the first aporte, the first value and exactly that many dividends.
        for (snapshot in seen) {
            val added = (snapshot.revision - base).toInt()
            val dividends = snapshot.investments.single().movements
                .filter { it.kind == InvestmentMovementKind.Dividend }
            assertEquals("revision ${snapshot.revision}", added, dividends.size)
            assertEquals(
                "revision ${snapshot.revision}",
                (0 until added).map { 1_000L + it }.toSet(),
                dividends.map { it.amount.money.minorUnits }.toSet()
            )
        }
        assertEquals(rounds + 2, seen.last().investments.single().movements.size)
    }

    private companion object {
        /** A version 3 file, as an app before investments wrote it: one category, nothing else. */
        val VERSION_3 = """
            {
              "format": "sello-backup",
              "version": 3,
              "exportedAt": "2026-10-22T14:30:00Z",
              "financial": {
                "sourceRevision": 1,
                "zone": "America/Bogota",
                "categories": [
                  {
                    "id": "00000000-0000-4000-8000-000000000001",
                    "name": "Mercado",
                    "icon": "shopping_cart",
                    "archived": false,
                    "createdAt": "2026-09-01T12:00:00Z",
                    "updatedAt": "2026-09-01T12:00:00Z"
                  }
                ],
                "defaultLimits": [],
                "monthLimits": [],
                "expenses": [],
                "incomes": [],
                "goals": [],
                "featuredGoal": null,
                "schedules": []
              },
              "preferences": null
            }
        """.trimIndent()
    }
}
