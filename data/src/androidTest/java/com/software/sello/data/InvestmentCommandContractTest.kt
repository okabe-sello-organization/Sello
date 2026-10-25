package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.CarriedValue
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PortfolioError
import com.software.sello.domain.port.CommittedInvestment
import com.software.sello.domain.port.DatedUsdRate
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DeleteInvestment
import com.software.sello.domain.port.DeleteInvestmentMovement
import com.software.sello.domain.port.EditInvestment
import com.software.sello.domain.port.EditInvestmentMovement
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentCommandOutcome.Committed
import com.software.sello.domain.port.InvestmentCommandOutcome.Rejected
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.RecordInvestmentMovement
import com.software.sello.domain.port.RestoreInvestmentMovement
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Investments against a real database file, through the real commands and reads. Every
 * expected figure is the hand-worked number of an approved example in
 * docs/testing/investment-examples.md, named by its ID. Records are numbered from 100
 * (an investment, then its first aporte and first value, then a rate entered with it)
 * and operations from 1000; today is 25 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class InvestmentCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 25)
    private val first = investmentId(100)

    @Before
    fun startToday() {
        ledger.goTo("2026-10-25")
    }

    private fun movements() = ledger.rows(
        "investment_movement",
        "sequence",
        "id",
        "kind",
        "amount_minor",
        "currency",
        "effective_date",
        "version"
    )

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private fun committed(outcome: InvestmentCommandOutcome): CommittedInvestment =
        (outcome as? Committed)?.change ?: throw AssertionError("not committed: $outcome")

    private suspend fun figures(id: InvestmentId = first) = ledger.summary(id).figures

    /**
     * IN08: aporte 4.000.000 on 1 sep (value the same, 100–102), value 5.000.000 on 1 oct
     * (103), aporte 1.000.000 on 10 oct (104).
     */
    private suspend fun in08() {
        ledger.created(ledger.createInvestment("Acciones", pesos(4_000_000), "2026-09-01"))
        ledger.recorded(first, Valuation, pesosValue(5_000_000), "2026-10-01") // 103
        ledger.recorded(first, Contribution, pesos(1_000_000), "2026-10-10") // 104
    }

    /** IN09: IN08, then a retiro of 2.000.000 on 12 oct (record 105). */
    private suspend fun in09() {
        in08()
        ledger.recorded(first, Withdrawal, pesos(2_000_000), "2026-10-12")
    }

    private suspend fun delete(number: Int, operation: OperationId = ledger.operation()) =
        ledger.invest(
            DeleteInvestmentMovement(
                operation,
                1,
                first,
                ledger.investmentVersion(first),
                movementId(number),
                1
            )
        )

    // ---- Creating ----

    @Test
    fun aNewCopInvestmentIsItsRowItsFirstAporteAndItsValueWithOneRevisionAndReceipt() =
        runBlocking {
            val operation = ledger.operation()

            val saved = ledger.created(
                ledger.createInvestment(
                    "Fondo",
                    pesos(3_000_000),
                    "2025-05-03",
                    description = "Fiducuenta",
                    operation = operation
                )
            )

            assertEquals(
                OperationKind.CreateInvestment to first,
                saved.receipt.kind to saved.investmentId
            )
            assertEquals(
                listOf("${uuid(100)}|Fondo|Fiducuenta|COP|2|1"),
                ledger.rows(
                    "investment",
                    "id",
                    "name",
                    "description",
                    "currency",
                    "next_sequence",
                    "version"
                )
            )
            // Lo mismo que aporté: the value is the aporte, the later position, the same date.
            assertEquals(
                listOf(
                    "0|${uuid(101)}|contribution|3000000|COP|2025-05-03|1",
                    "1|${uuid(102)}|valuation|3000000|COP|2025-05-03|1"
                ),
                movements()
            )
            assertEquals(1L, ledger.revision())
            assertEquals(listOf("1|investment.create|${uuid(100)}"), receipts())
            assertEquals(Outcome.Success(saved), ledger.investments.find(operation))
            val figures = figures()
            assertEquals(CarriedValue(Money.cop(3_000_000), day("2025-05-03")), figures.value)
            assertEquals(Money.cop(0), figures.gain)
            assertEquals(InvestmentReturn(GainDirection.Even, 0), figures.`return`)
        }

    @Test
    fun in03OtroValorIsTheFirstValueOnTheSameDay() = runBlocking {
        ledger.created(
            ledger.createInvestment(
                "CDT Banco",
                pesos(10_000_000),
                "2026-09-30",
                value = pesosValue(10_746_000)
            )
        )

        val figures = figures()

        // IN03: G +746.000; 7,46 % → +7,5 %.
        assertEquals(Money.cop(746_000), figures.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 75), figures.`return`)
        assertEquals(CarriedValue(Money.cop(10_746_000), day("2026-09-30")), figures.value)
    }

    @Test
    fun in27AUsdInvestmentWithItsFirstRateIsSavedTogetherAndStaysInUsd() = runBlocking {
        val saved = ledger.created(
            ledger.createInvestment(
                "Acciones EE. UU.",
                cents(100_000),
                "2026-10-20",
                value = cents(105_050),
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        )

        assertEquals(first, saved.investmentId)
        assertEquals(
            listOf("${uuid(103)}|415025|2026-10-20|0"),
            ledger.rows("usd_rate", "id", "rate_hundredths", "effective_date", "sequence")
        )
        assertEquals(1L, ledger.revision())
        val figures = figures()
        // IN27: G +USD 50,50; 5,05 % → +5,1 %.
        assertEquals(Money(5_050, Currency.USD), figures.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 51), figures.`return`)
        assertEquals(rate(415_025), ledger.board().rates.current?.rate)
    }

    @Test
    fun in33AUsdInvestmentWithNoRateYetAndNoneGivenIsRefused() = runBlocking {
        val outcome = ledger.invest(
            ledger.createInvestment("Acciones", cents(100_000), "2026-10-20")
        )

        assertEquals(Rejected(InvestmentRejection.RateRequired), outcome)
        assertEquals(emptyList<String>(), ledger.rows("investment", "id"))
        assertEquals(0L, ledger.revision())

        // Once a rate exists, a USD investment needs none of its own.
        assertTrue(ledger.setRate(415_025, "2026-10-20") is Committed)
        assertTrue(
            ledger.invest(
                ledger.createInvestment("Acciones", cents(100_000), "2026-10-20")
            ) is Committed
        )
    }

    @Test
    fun theSameCreationTwiceIsOneInvestmentAndADifferentOneUnderItsNameIsAConflict() = runBlocking {
        val operation = ledger.operation()
        val firstTime = ledger.invest(
            ledger.createInvestment("Fondo", pesos(3_000_000), "2026-10-01", operation = operation)
        )

        val again = ledger.invest(
            ledger.createInvestment("Fondo", pesos(3_000_000), "2026-10-01", operation = operation)
        )
        val other = ledger.invest(
            ledger.createInvestment("Fondo", pesos(3_000_001), "2026-10-01", operation = operation)
        )
        val stale = ledger.invest(
            ledger.createInvestment("Fondo", pesos(1), "2026-10-01", generation = 2)
        )

        assertEquals(Committed(committed(firstTime), replayed = true), again)
        assertEquals(Rejected(InvestmentRejection.OperationConflict), other)
        assertEquals(Rejected(InvestmentRejection.StaleGeneration(current = 1)), stale)
        assertEquals(1, ledger.rows("investment", "id").size)
        assertEquals(1L, ledger.revision())
    }

    @Test
    fun amountsInAnotherCurrencyAreRefused() = runBlocking {
        val mixed = ledger.invest(
            ledger.createInvestment(
                "Fondo",
                pesos(1_000_000),
                "2026-10-01",
                value = cents(100),
                currency = Currency.COP
            )
        )
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01"))

        val usdIntoCop = ledger.movement(first, Contribution, cents(100), "2026-10-02")

        assertEquals(Rejected(InvestmentRejection.CurrencyMismatch), mixed)
        assertEquals(Rejected(InvestmentRejection.CurrencyMismatch), usdIntoCop)
        assertEquals(2, movements().size)
    }

    // ---- Dates ----

    @Test
    fun in24AMovementDatedTomorrowIsRefused() = runBlocking {
        val future = ledger.invest(ledger.createInvestment("Fondo", pesos(1), "2026-10-26"))
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01"))

        val movement = ledger.movement(first, Contribution, pesos(1), "2026-10-26")
        val rate = ledger.setRate(400_000, "2026-10-26")

        assertEquals(Rejected(InvestmentRejection.FutureDate(today)), future)
        assertEquals(Rejected(InvestmentRejection.FutureDate(today)), movement)
        assertEquals(Rejected(InvestmentRejection.FutureDate(today)), rate)
        assertEquals(emptyList<String>(), ledger.rows("usd_rate", "id"))
    }

    @Test
    fun in25NothingButAnAporteMayComeBeforeTheFirstAporte() = runBlocking {
        ledger.created(ledger.createInvestment("Fondo", pesos(3_000_000), "2025-05-03"))

        val value = ledger.movement(first, Valuation, pesosValue(3_000_000), "2025-05-01")
        assertEquals(
            Rejected(InvestmentRejection.BeforeFirstContribution(day("2025-05-03"))),
            value
        )

        ledger.recorded(first, Contribution, pesos(100_000), "2025-05-01")
        assertEquals(YearMonth.of(2025, 5), figures().since)
        ledger.recorded(first, Contribution, pesos(100_000), "2025-04-01")
        assertEquals(YearMonth.of(2025, 4), figures().since)
    }

    // ---- Values and the withdrawal limit ----

    @Test
    fun in09AWithdrawalIsNotALoss() = runBlocking {
        in09()

        val figures = figures()

        // IN09: V 4.000.000 (valor del 1 oct); W 2.000.000; G +1.000.000; +20,0 %.
        assertEquals(CarriedValue(Money.cop(4_000_000), day("2026-10-01")), figures.value)
        assertEquals(Money.cop(5_000_000), figures.contributed)
        assertEquals(Money.cop(2_000_000), figures.withdrawn)
        assertEquals(Money.cop(1_000_000), figures.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 200), figures.`return`)
    }

    @Test
    fun in10AWithdrawalAboveTheValueIsRefusedAndTheWholeValueIsAccepted() = runBlocking {
        in09()
        val before = movements()
        val revision = ledger.revision()

        val above = ledger.movement(first, Withdrawal, pesos(4_000_001), "2026-10-20")

        // The refused retiro took record 106; nothing was written.
        assertEquals(
            Rejected(InvestmentRejection.AboveValue(movementId(106), Money.cop(4_000_000))),
            above
        )
        assertEquals(before, movements())
        assertEquals(revision, ledger.revision())

        ledger.recorded(first, Withdrawal, pesos(4_000_000), "2026-10-20")
        assertEquals(Money.cop(0), figures().value?.amount)
    }

    @Test
    fun in18AWithdrawalWithNoValueWaitsForOne() = runBlocking {
        ledger.created(ledger.createInvestment("Cripto", pesos(2_000_000), "2026-10-01"))
        delete(102)

        val retiro = ledger.movement(first, Withdrawal, pesos(1), "2026-10-02")

        // IN17: Sin valor; IN18: the retiro waits.
        assertNull(figures().value)
        assertNull(figures().gain)
        assertEquals(Rejected(InvestmentRejection.NoValue(movementId(103))), retiro)
    }

    @Test
    fun in13AValueAndAnAporteOnTheSameDayReplayInTheOrderTheyWereRecorded() = runBlocking {
        ledger.created(ledger.createInvestment("Fondo", pesos(3_000_000), "2026-10-01"))

        ledger.recorded(first, Valuation, pesosValue(4_000_000), "2026-10-20")
        ledger.recorded(first, Contribution, pesos(500_000), "2026-10-20")

        // IN13: V 4.500.000.
        assertEquals(Money.cop(4_500_000), figures().value?.amount)
    }

    // ---- Corrections ----

    @Test
    fun in20ACorrectionThatWouldTakeMoreThanTheValueIsRefused() = runBlocking {
        in09()
        val before = movements()

        val outcome = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first,
                ledger.investmentVersion(
                    first
                ),
                movementId(105), 1, pesos(7_000_000), day("2026-10-12"), null
            )
        )

        assertEquals(
            Rejected(InvestmentRejection.AboveValue(movementId(105), Money.cop(6_000_000))),
            outcome
        )
        assertEquals(before, movements())
    }

    @Test
    fun aCorrectionInPlaceKeepsItsPositionAndRecomputesTheFigures() = runBlocking {
        in09()

        val outcome = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first,
                ledger.investmentVersion(
                    first
                ),
                movementId(
                    105
                ),
                1, pesos(1_000_000), day("2026-10-12"), Note.of("Arriendo").valueOrFail()
            )
        )

        assertTrue("$outcome", outcome is Committed)
        assertEquals("4|${uuid(105)}|withdrawal|1000000|COP|2026-10-12|2", movements().last())
        // IN08's value 6.000.000 less the corrected retiro of 1.000.000.
        assertEquals(Money.cop(5_000_000), figures().value?.amount)
        assertEquals(Money.cop(1_000_000), figures().gain)
    }

    @Test
    fun theSameCorrectionAsWhatIsRecordedIsUnchangedAndAnOldVersionIsAConflict() = runBlocking {
        in09()
        val version = ledger.investmentVersion(first)

        val same = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first, version,
                movementId(
                    105
                ),
                1, pesos(2_000_000), day("2026-10-12"), null
            )
        )
        val oldInvestment = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first, version - 1,
                movementId(
                    105
                ),
                1, pesos(1), day("2026-10-12"), null
            )
        )
        val oldMovement = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first, version,
                movementId(
                    105
                ),
                2, pesos(1), day("2026-10-12"), null
            )
        )
        val missing = ledger.invest(
            EditInvestmentMovement(
                ledger.operation(), 1, first, version,
                movementId(
                    999
                ),
                1, pesos(1), day("2026-10-12"), null
            )
        )

        assertEquals(Rejected(InvestmentRejection.Unchanged), same)
        assertEquals(
            Rejected(InvestmentRejection.VersionConflict(current = version)),
            oldInvestment
        )
        assertEquals(Rejected(InvestmentRejection.VersionConflict(current = 1)), oldMovement)
        assertEquals(Rejected(InvestmentRejection.MovementMissing), missing)
    }

    @Test
    fun editingNameDescriptionAndIconChangesNoFigureAndTheSameValuesAreUnchanged() = runBlocking {
        in09()
        val before = figures()
        fun edit(version: Long) = EditInvestment(
            ledger.operation(),
            1,
            first,
            version,
            InvestmentName.of("Acciones Bancolombia").valueOrFail(),
            InvestmentDescription.of("Trii").valueOrFail(),
            IconKey.of("show_chart").valueOrFail()
        )

        val renamed = ledger.invest(edit(ledger.investmentVersion(first)))
        val same = ledger.invest(edit(ledger.investmentVersion(first)))

        assertTrue("$renamed", renamed is Committed)
        assertEquals(Rejected(InvestmentRejection.Unchanged), same)
        assertEquals(before, figures())
        val investment = ledger.summary(first).history.investment
        assertEquals(
            "Acciones Bancolombia" to "Trii",
            investment.name.value to investment.description?.value
        )
        assertEquals(Currency.COP, investment.currency)
    }

    @Test
    fun in19DeletingTheAporteALaterRetiroNeedsIsRefusedNamingThatRetiro() = runBlocking {
        in08()
        ledger.recorded(first, Withdrawal, pesos(6_000_000), "2026-10-12") // 105
        val before = movements()

        val outcome = delete(104)

        assertEquals(
            Rejected(InvestmentRejection.AboveValue(movementId(105), Money.cop(5_000_000))),
            outcome
        )
        assertEquals(before, movements())
        assertEquals(0, ledger.rows("deleted_investment_movement", "deletion_id").size)
    }

    @Test
    fun in22TheOnlyAporteCannotBeDeleted() = runBlocking {
        ledger.created(ledger.createInvestment("Fondo", pesos(3_000_000), "2026-10-01"))

        val outcome = delete(101)

        assertEquals(Rejected(InvestmentRejection.LastContribution), outcome)
        assertEquals(2, movements().size)
    }

    @Test
    fun in21ADeletedRetiroComesBackWithinSixSecondsAndNotAfter() = runBlocking {
        in09()
        val withIt = movements()
        ledger.elapsed = 7.hours
        val deletion = ledger.operation()

        assertTrue(delete(105, deletion) is Committed)

        // IN21: deleted, V 6.000.000, W 0, G +1.000.000.
        val deleted = figures()
        assertEquals(Money.cop(6_000_000), deleted.value?.amount)
        assertEquals(Money.cop(0), deleted.withdrawn)
        assertEquals(Money.cop(1_000_000), deleted.gain)
        assertEquals(deletion, ledger.investments.movementUndoOffer()?.operationId)
        assertEquals(first, ledger.investments.movementUndoOffer()?.investmentId)
        assertEquals(1, ledger.rows("deleted_investment_movement", "deletion_id").size)

        ledger.elapsed = 7.hours + 5999.milliseconds
        val restored = ledger.invest(RestoreInvestmentMovement(ledger.operation(), 1, deletion))

        // Deshacer brings back IN09: the same row, the same figures.
        assertTrue("$restored", restored is Committed)
        assertEquals(withIt, movements())
        assertEquals(Money.cop(4_000_000), figures().value?.amount)
        assertEquals(Money.cop(2_000_000), figures().withdrawn)
        assertEquals(0, ledger.rows("deleted_investment_movement", "deletion_id").size)
        assertNull(ledger.investments.movementUndoOffer())

        val again = ledger.operation()
        delete(105, again)
        ledger.elapsed += 6.seconds
        assertEquals(
            Rejected(InvestmentRejection.UndoExpired),
            ledger.invest(RestoreInvestmentMovement(ledger.operation(), 1, again))
        )
        assertEquals(Money.cop(0), figures().withdrawn)
    }

    @Test
    fun deletingAMovementMakesAnEarlierGoalOrExpenseDeletionFinal() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 7_000)) // 101
        ledger.expenses.delete(
            DeleteExpense(
                ledger.operation(),
                1,
                ExpenseId.of(uuid(101)).valueOrFail(),
                1
            )
        )
        assertTrue(ledger.expenses.undoOffer() != null)
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01")) // 102–104
        ledger.recorded(investmentId(102), Dividend, pesos(1_000), "2026-10-02") // 105

        val outcome = ledger.invest(
            DeleteInvestmentMovement(
                ledger.operation(),
                1,
                investmentId(102),
                ledger.investmentVersion(investmentId(102)),
                movementId(105),
                1
            )
        )

        assertTrue("$outcome", outcome is Committed)
        assertNull(ledger.expenses.undoOffer())
        assertEquals(0, ledger.rows("deleted_expense", "deletion_id").size)
        assertTrue(ledger.investments.movementUndoOffer() != null)
    }

    // ---- Deleting an investment ----

    @Test
    fun in23DeletingAnInvestmentRemovesItsNineMovementsAndNothingElse() = runBlocking {
        // IN05 Ecopetrol: 2 aportes, 2 dividends, the first value and 4 more values.
        ledger.created(ledger.createInvestment("Ecopetrol", pesos(1_500_000), "2026-02-01"))
        ledger.recorded(first, Contribution, pesos(1_500_000), "2026-02-15")
        ledger.recorded(first, Dividend, pesos(89_000), "2026-04-01")
        ledger.recorded(first, Dividend, pesos(89_000), "2026-07-01")
        for ((date, value) in listOf(
            "2026-03-01" to 3_100_000L,
            "2026-05-01" to 2_900_000L,
            "2026-08-01" to 3_000_000L,
            "2026-10-09" to 3_050_000L
        )) {
            ledger.recorded(first, Valuation, pesosValue(value), date)
        }
        val ecopetrol = figures()
        // IN05: D 178.000; G +228.000; +7,6 %.
        assertEquals(Money.cop(178_000), ecopetrol.dividends)
        assertEquals(Money.cop(228_000), ecopetrol.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 76), ecopetrol.`return`)
        assertEquals(9, ecopetrol.movements)
        ledger.audit = ledger.audit.plusSeconds(1)
        ledger.created(ledger.createInvestment("CDT Banco", pesos(10_000_000), "2026-09-30")) // 110
        assertTrue(ledger.setRate(415_025, "2026-10-20") is Committed)
        val cdt = investmentId(110)
        val others = ledger.rows("investment_movement", "id", "investment_id")
            .filter { it.endsWith(uuid(110)) }

        val outcome = ledger.invest(
            DeleteInvestment(ledger.operation(), 1, first, ledger.investmentVersion(first))
        )

        assertTrue("$outcome", outcome is Committed)
        assertEquals(listOf(uuid(110)), ledger.rows("investment", "id"))
        assertEquals(others, ledger.rows("investment_movement", "id", "investment_id"))
        assertEquals(1, ledger.rows("usd_rate", "id").size)
        assertNull(ledger.investmentReads.investment(first).valueOrFail())
        assertEquals(listOf(cdt), ledger.board().investments.map { it.history.investment.id })
        assertEquals("investment.delete|${uuid(100)}", receipts().last().substringAfter('|'))
    }

    @Test
    fun deletingAnInvestmentReadAtAnOldVersionIsRefused() = runBlocking {
        in08()

        val outcome = ledger.invest(DeleteInvestment(ledger.operation(), 1, first, 1))

        assertEquals(Rejected(InvestmentRejection.VersionConflict(current = 3)), outcome)
        assertEquals(4, movements().size)
        assertEquals(
            Rejected(InvestmentRejection.InvestmentMissing),
            ledger.invest(DeleteInvestment(ledger.operation(), 1, investmentId(999), 1))
        )
    }

    // ---- The rate ----

    @Test
    fun in30OnTheSameDateTheLastRateEnteredIsCurrent() = runBlocking {
        assertTrue(ledger.setRate(415_025, "2026-10-20") is Committed)
        ledger.created(
            ledger.createInvestment(
                "Acciones",
                cents(100_000),
                "2026-10-20",
                value = cents(105_050)
            )
        )

        ledger.setRate(400_000, "2026-10-24")
        ledger.setRate(401_000, "2026-10-24")

        val board = ledger.board()
        assertEquals(
            rate(401_000) to day("2026-10-24"),
            board.rates.current?.let {
                it.rate to
                    it.date
            }
        )
        val total = (board.overview as Outcome.Success).value.total!!
        // IN30: V 4.212.505 (4.212.505,00), C 4.010.000.
        assertEquals(Money.cop(4_212_505), total.value)
        assertEquals(Money.cop(4_010_000), total.contributed)
    }

    @Test
    fun in31ARateDatedEarlierJoinsTheHistoryWithoutBecomingCurrent() = runBlocking {
        val set = ledger.setRate(400_000, "2026-10-24")
        ledger.setRate(390_000, "2026-10-22")

        val rates = ledger.board().rates

        assertEquals(rate(400_000), rates.current?.rate)
        // The history: the latest date first.
        assertEquals(
            listOf(400_000L to "2026-10-24", 390_000L to "2026-10-22"),
            rates.history.map { it.rate.hundredths to it.date.toString() }
        )
        assertEquals(listOf(0L, 1L), ledger.rows("usd_rate", "sequence").map(String::toLong))
        val receipt = committed(set)
        assertEquals(OperationKind.SetUsdRate, receipt.receipt.kind)
        assertNull(receipt.investmentId)
        assertEquals(Outcome.Success(receipt), ledger.investments.find(receipt.receipt.operationId))
        assertEquals("investment.set_usd_rate|${uuid(100)}", receipts().first().substringAfter('|'))
    }

    @Test
    fun aUsdValueWithNoRateIsNeverConvertedAtAGuess() = runBlocking {
        ledger.created(
            ledger.createInvestment(
                "Acciones",
                cents(100_000),
                "2026-10-20",
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        )
        ledger.database.openHelper.writableDatabase.execSQL("DELETE FROM usd_rate")

        assertEquals(Outcome.Failure(PortfolioError.MissingRate), ledger.board().overview)
    }

    // ---- The overview ----

    @Test
    fun in28TheFourCopInvestmentsAndTheUsdOneAddUpInPesosAtTheCurrentRate() = runBlocking {
        // IN02 Parqueadero (100–104): aportes 18.000.000, rents 350.000, value 19.000.000 (12 jun).
        ledger.created(ledger.createInvestment("Parqueadero", pesos(18_000_000), "2026-01-10"))
        ledger.recorded(investmentId(100), Dividend, pesos(350_000), "2026-03-01")
        ledger.recorded(investmentId(100), Valuation, pesosValue(19_000_000), "2026-06-12")
        // IN03 CDT Banco (105–107): aporte 10.000.000, value 10.746.000 (30 sep), Otro valor.
        ledger.audit = ledger.audit.plusSeconds(1)
        ledger.created(
            ledger.createInvestment(
                "CDT Banco",
                pesos(10_000_000),
                "2026-09-30",
                value = pesosValue(10_746_000)
            )
        )
        // IN04 Fondo (108–113).
        ledger.audit = ledger.audit.plusSeconds(1)
        ledger.created(ledger.createInvestment("Fondo", pesos(3_000_000), "2025-05-03"))
        val fondo = investmentId(108)
        ledger.recorded(fondo, Contribution, pesos(3_000_000), "2025-11-03")
        ledger.recorded(fondo, Withdrawal, pesos(500_000), "2026-08-03")
        ledger.recorded(fondo, Valuation, pesosValue(5_242_000), "2026-10-14")
        // IN05 Ecopetrol (114–119).
        ledger.audit = ledger.audit.plusSeconds(1)
        ledger.created(ledger.createInvestment("Ecopetrol", pesos(3_000_000), "2026-02-01"))
        val ecopetrol = investmentId(114)
        ledger.recorded(ecopetrol, Dividend, pesos(89_000), "2026-04-01")
        ledger.recorded(ecopetrol, Dividend, pesos(89_000), "2026-07-01")
        ledger.recorded(ecopetrol, Valuation, pesosValue(3_050_000), "2026-10-09")
        // IN27 Acciones EE. UU. with the rate 4.150,25 of 20 oct.
        ledger.audit = ledger.audit.plusSeconds(1)
        ledger.created(
            ledger.createInvestment(
                "Acciones EE. UU.",
                cents(100_000),
                "2026-10-20",
                value = cents(105_050),
                newRate = DatedUsdRate(rate(415_025), day("2026-10-20"))
            )
        )

        val board = ledger.board()

        // IN04: the Fondo alone, G −258.000, −4,3 %.
        assertEquals(Money.cop(-258_000), board.investments[2].figures.gain)
        assertEquals(
            InvestmentReturn(GainDirection.Loss, 43),
            board.investments[2].figures.`return`
        )
        val overview = (board.overview as Outcome.Success).value
        val total = overview.total!!
        // IN28: V 42.397.838, C 41.150.250, W 500.000, D 528.000; G +2.275.588; 5,53 % → +5,5 %.
        assertEquals(Money.cop(42_397_838), total.value)
        assertEquals(Money.cop(41_150_250), total.contributed)
        assertEquals(Money.cop(500_000), total.withdrawn)
        assertEquals(Money.cop(528_000), total.dividends)
        assertEquals(Money.cop(2_275_588), total.gain)
        assertEquals(InvestmentReturn(GainDirection.Gain, 55), total.`return`)
        assertEquals(rate(415_025) to day("2026-10-20"), overview.rate?.let { it.rate to it.date })
        assertEquals(day("2026-06-12") to day("2026-10-20"), total.valuedFrom to total.valuedTo)
        // Largest value in pesos first; the USD one at 4.359.838.
        assertEquals(
            listOf("Parqueadero", "CDT Banco", "Fondo", "Acciones EE. UU.", "Ecopetrol"),
            overview.items.map { it.history.investment.name.value }
        )
        assertEquals(Money.cop(4_359_838), overview.items[3].valueInPesos)
        assertEquals(board.revision, ledger.revision())
    }

    // ---- Recovery ----

    @Test
    fun aCallerCancelledWhileAnAporteIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        ledger.created(ledger.createInvestment("Fondo", pesos(1_000_000), "2026-10-01"))
        val command =
            RecordInvestmentMovement(
                ledger.operation(),
                1,
                first,
                ledger.investmentVersion(first),
                Contribution,
                pesos(750_000),
                day("2026-10-02"),
                null
            )
        lateinit var caller: Job
        var returned: InvestmentCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.investments.submit(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(Money.cop(1_750_000), figures().contributed)
        val found = ledger.investments.find(command.operationId).valueOrFail()
        assertEquals(Committed(found!!, replayed = true), ledger.investments.submit(command))
        assertEquals(Money.cop(1_750_000), figures().contributed)
    }
}
