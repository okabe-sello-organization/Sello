package com.software.sello.devtools

import com.software.sello.devtools.ScenarioStep.DeleteInvestment
import com.software.sello.devtools.ScenarioStep.DeleteMovement
import com.software.sello.devtools.ScenarioStep.EditMovement
import com.software.sello.devtools.ScenarioStep.Investment
import com.software.sello.devtools.ScenarioStep.Movement
import com.software.sello.devtools.ScenarioStep.Repeat
import com.software.sello.devtools.ScenarioStep.SetRate
import com.software.sello.devtools.ScenarioStep.UndoMovementDelete
import com.software.sello.devtools.ScenarioStep.Verify
import com.software.sello.domain.model.CarriedValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment as StoredInvestment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentFigures
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioOverview
import com.software.sello.domain.model.PortfolioTotal
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteInvestment as DeleteInvestmentCommand
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.EditInvestmentMovement
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentReads
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.RestoreInvestmentMovement
import com.software.sello.domain.port.SetUsdRate
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Investment steps send the app's own investment commands: to the investment their
 * creation step made, at the version the step was written for, on the sandbox's day; a
 * correction names the movement at the place its step recorded it in, Deshacer names the
 * deletion it undoes, and an amount or rate out of range is never sent. A check compares
 * each figure the board reads with the expected one, and says which did not match.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentScenarioRunnerTest {
    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private val start = LocalDate.of(2026, 10, 20)
    private val zone = ZoneId.of("America/Bogota")
    private val ledger = Ledger()
    private val investments = LedgerInvestments(ledger)

    /** Each investment's history: movements at places 0 to 5, identified by their place. */
    private val reads = Reads()

    private inner class Reads : InvestmentReads {
        var board: InvestmentBoard? = null

        override suspend fun board() = Outcome.Success(checkNotNull(board))

        override fun observeBoard() = error("unused")

        override suspend fun investment(id: InvestmentId): Outcome.Success<InvestmentSummary?> {
            val currency = currencyOf(id)
            val movements = (0L..5L).map {
                movement(it, if (it % 2 == 0L) Contribution else Valuation, currency)
            }
            return Outcome.Success(summary(id, "Inversión", currency, movements))
        }
    }

    /** The currency the creation that made [id] chose. */
    private fun currencyOf(id: InvestmentId): Currency = investments.sent
        .filterIsInstance<CreateInvestment>()
        .first { ledger.receipts[it.operationId.value]?.second == id.value }
        .currency

    private fun movementId(place: Long) =
        InvestmentMovementId.of("00000000-0000-4000-a000-00000000000$place").value()

    /** A stored movement's own date, not the sandbox's day. */
    private val stored = LocalDate.of(2026, 10, 1)

    private fun movement(place: Long, kind: InvestmentMovementKind, currency: Currency) =
        InvestmentMovement(
            movementId(place),
            kind,
            InvestmentAmount.ofValue(Money(1, currency)).value(),
            stored,
            place,
            null,
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )

    private fun summary(
        id: InvestmentId,
        name: String,
        currency: Currency,
        movements: List<InvestmentMovement> = emptyList(),
        figures: InvestmentFigures = figures(currency, 0, null)
    ) = InvestmentSummary(
        InvestmentHistory(
            StoredInvestment(
                id,
                InvestmentName.of(name).value(),
                null,
                IconKey.of("insights").value(),
                currency,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            movements
        ),
        figures
    )

    private fun figures(currency: Currency, contributed: Long, value: CarriedValue?) =
        InvestmentFigures(
            Money(contributed, currency),
            Money(0, currency),
            Money(0, currency),
            value,
            null,
            null,
            YearMonth.of(2026, 10),
            2
        )

    private fun scenario(vararg steps: ScenarioStep) = Scenario(
        id = "inversiones-prueba",
        version = 1,
        seed = 1,
        zone = zone,
        start = start,
        title = "Inversiones",
        summary = "",
        steps = steps.toList()
    )

    private fun store(scenario: Scenario) = InMemorySessionStore(
        SandboxSession(active = true, date = start, scenario = scenario.key)
    )

    private fun TestScope.runner(scenario: Scenario, store: InMemorySessionStore): ScenarioRunner {
        val clock = ControlledFinancialClock(FinancialDay(start, zone))
        return ScenarioRunner(
            scenario,
            7,
            SandboxTime(clock, store, backgroundScope),
            LedgerCategories(ledger),
            LedgerExpenses(ledger),
            LedgerIncomes(ledger),
            LedgerGoals(ledger),
            NoSnapshots,
            NoSnapshots,
            NoGoalReads,
            noSchedules(clock),
            ScenarioInvestments(investments, reads),
            store,
            backgroundScope
        )
    }

    @Test
    fun investmentStepsSendTheirOwnCommandsToTheInvestmentTheScenarioMade() = runTest {
        val scenario = scenario(
            Investment("fondo", "Fondo", "insights", Currency.COP, 3_000_000),
            Movement("fondo", Move.Aporte, 1_000_000, 1),
            Repeat(1),
            Movement("fondo", Move.Valor, 5_000_000, 2, on = start.minusDays(1)),
            Investment(
                "acciones",
                "Acciones",
                "trending_up",
                Currency.USD,
                100_000,
                value = 105_050,
                rate = Rate(415_025)
            ),
            Movement("acciones", Move.Retiro, 5_000, 1),
            EditMovement(3, 4_800_000, 4),
            DeleteMovement(1, 5),
            UndoMovementDelete(7),
            DeleteMovement(0, 7, firstValue = true),
            EditMovement(4, 90_000, 2, movementVersion = 3),
            SetRate(Rate(400_000, on = start.minusDays(2))),
            DeleteInvestment("acciones", 3),
            Movement("fondo", Move.Aporte, 1_000_000_000_000, 8, expect = Expect.InvalidInput),
            SetRate(Rate(99), Expect.InvalidInput)
        )
        val runner = runner(scenario, store(scenario))
        while (runner.step()) Unit

        assertTrue(runner.state.value.problem, runner.state.value.finished)
        val sent = investments.sent
        val fondo = InvestmentId.of(ledger.receipts.getValue(scenario.operation(0)).second).value()
        val acciones =
            InvestmentId.of(ledger.receipts.getValue(scenario.operation(4)).second).value()
        val create = sent[0] as CreateInvestment
        assertEquals(
            listOf<Any?>(
                "Fondo",
                "insights",
                Currency.COP,
                Money.cop(3_000_000),
                Money.cop(3_000_000),
                start,
                null,
                7L
            ),
            listOf(
                create.name.value,
                create.icon.value,
                create.currency,
                create.firstContribution.money,
                create.firstValue.money,
                create.date,
                create.newRate,
                create.generation
            )
        )
        val aporte = sent[1] as RecordInvestmentMovement
        assertEquals(
            listOf(fondo, 1L, InvestmentMovementKind.Contribution, Money.cop(1_000_000), start),
            listOf(
                aporte.investmentId,
                aporte.expectedVersion,
                aporte.kind,
                aporte.amount.money,
                aporte.date
            )
        )
        // The repeat is the same operation and input: storage answers with its receipt.
        assertEquals(aporte, sent[2])
        val valor = sent[3] as RecordInvestmentMovement
        assertEquals(
            listOf(2L, InvestmentMovementKind.Valuation, start.minusDays(1)),
            listOf(valor.expectedVersion, valor.kind, valor.date)
        )
        val usd = sent[4] as CreateInvestment
        assertEquals(
            listOf(
                Currency.USD,
                Money(100_000, Currency.USD),
                Money(105_050, Currency.USD),
                DatedUsdRate(UsdRate.ofHundredths(415_025).value(), start)
            ),
            listOf(usd.currency, usd.firstContribution.money, usd.firstValue.money, usd.newRate)
        )
        val retiro = sent[5] as RecordInvestmentMovement
        assertEquals(
            listOf(acciones, InvestmentMovementKind.Withdrawal, Money(5_000, Currency.USD)),
            listOf(retiro.investmentId, retiro.kind, retiro.amount.money)
        )
        // Step 4's value was fondo's second movement step: place 2 + 1.
        val edit = sent[6] as EditInvestmentMovement
        assertEquals(
            listOf(fondo, 4L, movementId(3), 1L, Money.cop(4_800_000), stored),
            listOf(
                edit.investmentId,
                edit.expectedVersion,
                edit.movementId,
                edit.movementVersion,
                edit.amount.money,
                edit.date
            )
        )
        val delete = sent[7] as DeleteInvestmentMovement
        assertEquals(
            listOf(fondo, 5L, movementId(2)),
            listOf(delete.investmentId, delete.expectedVersion, delete.movementId)
        )
        assertEquals(
            scenario.operation(7),
            (sent[8] as RestoreInvestmentMovement).deletionId.value
        )
        // A creation's first value is place 1, its first aporte place 0.
        assertEquals(movementId(1), (sent[9] as DeleteInvestmentMovement).movementId)
        val firstAporte = sent[10] as EditInvestmentMovement
        assertEquals(
            listOf(acciones, movementId(0), 3L, Money(90_000, Currency.USD)),
            listOf(
                firstAporte.investmentId,
                firstAporte.movementId,
                firstAporte.movementVersion,
                firstAporte.amount.money
            )
        )
        val rate = sent[11] as SetUsdRate
        assertEquals(400_000L to start.minusDays(2), rate.rate.hundredths to rate.date)
        val gone = sent[12] as DeleteInvestmentCommand
        assertEquals(acciones to 3L, gone.investmentId to gone.expectedVersion)
        // Out of range: never sent.
        assertEquals(13, sent.size)
        assertEquals(
            listOf("AboveMaximum", "OutOfBounds"),
            runner.state.value.log.takeLast(2).map { it.result.substringAfterLast(": ") }
        )
    }

    @Test
    fun aDeletionTriedAgainAfterItCommittedIsAnsweredFromItsReceipt() = runTest {
        val scenario = scenario(
            Investment("fondo", "Fondo", "insights", Currency.COP, 3_000_000),
            Movement("fondo", Move.Aporte, 1_000_000, 1),
            DeleteMovement(1, 2)
        )
        val store = store(scenario)
        val first = runner(scenario, store)
        while (first.step()) Unit
        // The deletion committed, then its checkpoint was lost with the process.
        store.session = store.session.copy(nextStep = 2)

        val second = runner(scenario, store)
        assertTrue(second.step())

        assertEquals("Ya estaba guardado", second.state.value.log.last().result)
        assertEquals(1, investments.sent.count { it is DeleteInvestmentMovement })
    }

    @Test
    fun aCheckComparesEachFigureTheBoardReadsAndNamesTheOneThatDiffers() = runTest {
        val made = scenario(
            Investment("fondo", "Fondo", "insights", Currency.COP, 5_000_000),
            Investment("cripto", "Cripto", "insights", Currency.COP, 2_000_000),
            Investment("acciones", "Acciones", "trending_up", Currency.USD, 100_000),
            DeleteInvestment("acciones", 1)
        )
        val runner = runner(made, store(made))
        while (runner.step()) Unit
        fun id(step: Int) =
            InvestmentId.of(ledger.receipts.getValue(made.operation(step)).second).value()
        val oct1 = LocalDate.of(2026, 10, 1)
        val fondo = summary(
            id(0),
            "Fondo",
            Currency.COP,
            figures = InvestmentFigures(
                Money.cop(5_000_000),
                Money.cop(2_000_000),
                Money.cop(50_000),
                CarriedValue(Money.cop(4_000_000), oct1),
                Money.cop(1_050_000),
                InvestmentReturn(GainDirection.Gain, 210),
                YearMonth.of(2026, 9),
                5
            )
        )
        val cripto =
            summary(id(1), "Cripto", Currency.COP, figures = figures(Currency.COP, 2_000_000, null))
        val rate = UsdRateEntry(
            UsdRateId.of("00000000-0000-4000-b000-000000000001").value(),
            UsdRate.ofHundredths(415_025).value(),
            start,
            0,
            Instant.EPOCH
        )
        reads.board = InvestmentBoard(
            7,
            9,
            listOf(fondo, cripto),
            UsdRateBook(rate, listOf(rate)),
            Outcome.Success(
                PortfolioOverview(
                    emptyList(),
                    PortfolioTotal(
                        Money.cop(4_000_000),
                        Money.cop(5_000_000),
                        Money.cop(2_000_000),
                        Money.cop(50_000),
                        Money.cop(1_050_000),
                        InvestmentReturn(GainDirection.Gain, 210),
                        YearMonth.of(2026, 9),
                        oct1,
                        oct1
                    ),
                    rate
                )
            )
        )
        val fondoCheck = InvestmentCheck(
            "fondo",
            5_000_000,
            Worth(4_000_000, oct1, 1_050_000, Yield(GainDirection.Gain, 210)),
            "IN00",
            withdrawn = 2_000_000,
            dividends = 50_000,
            since = YearMonth.of(2026, 9),
            movements = 5
        )
        val total = PortfolioCheck.Total(
            4_000_000,
            5_000_000,
            2_000_000,
            50_000,
            1_050_000,
            Yield(GainDirection.Gain, 210),
            YearMonth.of(2026, 9),
            oct1,
            oct1,
            RateCheck(415_025, start),
            "IN00"
        )
        val checks = Verify(
            emptyList(),
            investments = listOf(
                fondoCheck,
                InvestmentCheck("cripto", 2_000_000, null, "IN00"),
                InvestmentCheck.gone("acciones", "IN00")
            ),
            portfolio = total
        )
        val matching = runner(
            made.copy(steps = made.steps + checks),
            store(made).also {
                it.session = it.session.copy(nextStep = made.steps.size)
            }
        )

        assertTrue(matching.step())
        val lines = matching.state.value.log.last().result.lines()
        assertEquals("Comprobación: 4 de 4 cifras coinciden", lines.first())
        assertEquals(
            "✓ inversión fondo: esperado valor 4.000.000 del 2026-10-01, aportado 5.000.000, " +
                "retirado 2.000.000, dividendos 50.000, ganancia 1.050.000, gana 21,0 %, " +
                "desde 2026-09, 5 movimientos, real valor 4.000.000 del 2026-10-01, " +
                "aportado 5.000.000, retirado 2.000.000, dividendos 50.000, " +
                "ganancia 1.050.000, gana 21,0 %, desde 2026-09, 5 movimientos (IN00)",
            lines[1]
        )
        assertTrue(
            lines[2],
            lines[2].startsWith("✓ inversión cripto: esperado sin valor, aportado 2.000.000")
        )
        assertEquals("✓ inversión acciones: esperado borrada, real borrada (IN00)", lines[3])
        assertTrue(lines[4], lines[4].endsWith("dólar a 4.150,25 del 2026-10-20 (IN00)"))

        // Each figure is its own: a wrong withdrawal or rate is named, and the run stops.
        val wrong = Verify(
            emptyList(),
            investments = listOf(fondoCheck.copy(withdrawn = 1_999_999)),
            portfolio = total.copy(rate = RateCheck(415_000, start))
        )
        val mismatched = runner(
            made.copy(steps = made.steps + wrong),
            store(made).also {
                it.session = it.session.copy(nextStep = made.steps.size)
            }
        )

        assertFalse(mismatched.step())
        val seen = mismatched.state.value.log.last().result.lines()
        assertEquals("Comprobación: no coincide", seen.first())
        assertTrue(seen[1], seen[1].startsWith("✗ inversión fondo: esperado valor 4.000.000"))
        assertTrue(seen[1], "retirado 1.999.999" in seen[1] && "retirado 2.000.000" in seen[1])
        assertTrue(seen[2], seen[2].startsWith("✗ total de inversiones"))
    }

    private companion object {
        val Contribution = InvestmentMovementKind.Contribution
        val Valuation = InvestmentMovementKind.Valuation
    }
}
