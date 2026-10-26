package com.software.sello.feature.investments

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CreateInvestment
import com.software.sello.domain.port.DeleteInvestment
import com.software.sello.domain.port.EditInvestment
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentCommandOutcome
import com.software.sello.domain.port.InvestmentRejection
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.navigation.HandFinancialClock
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Nueva inversión and Editar inversión (investment rules 1, 2, 9, 11 and 12; IN23, IN26,
 * IN33, IN34). Figures are the approved examples' (docs/testing/investment-examples.md),
 * written out by hand; today is 25 October 2026.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvestmentEditorViewModelTest {
    private val clock = HandFinancialClock("2026-10-25")
    private var nextId = 1
    private val ids = RecordIdSource { "00000000-0000-4000-a000-%012d".format(nextId++) }
    private val reads = HandInvestmentReads()
    private val commands = HandInvestmentCommands()
    private val settings = HandInvestmentPreferences()
    private val createdId = "00000000-0000-4000-9000-000000000077"
    private val ecopetrolId = "00000000-0000-4000-9000-000000000005"

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private fun editor(saved: SavedStateHandle = SavedStateHandle()) =
        InvestmentEditorViewModel(saved, reads, settings, commands, clock, ids, testMoney())

    private fun editing(saved: SavedStateHandle = SavedStateHandle()): InvestmentEditorViewModel {
        saved[InvestmentEditorViewModel.KEY_INVESTMENT] = ecopetrolId
        return editor(saved)
    }

    /** Ecopetrol (IN05, IN23): 2 aportes, 2 dividends, its first value and 4 more = 9 movements. */
    private fun ecopetrol() = handInvestment(
        ecopetrolId,
        "Ecopetrol",
        "Acciones",
        "trending_up",
        listOf(
            Triple(InvestmentMovementKind.Contribution, 1_500_000, "2025-03-03"),
            Triple(InvestmentMovementKind.Valuation, 1_500_000, "2025-03-03"),
            Triple(InvestmentMovementKind.Contribution, 1_500_000, "2025-06-03"),
            Triple(InvestmentMovementKind.Valuation, 3_010_000, "2025-09-03"),
            Triple(InvestmentMovementKind.Dividend, 89_000, "2025-12-03"),
            Triple(InvestmentMovementKind.Valuation, 2_950_000, "2026-01-03"),
            Triple(InvestmentMovementKind.Dividend, 89_000, "2026-06-03"),
            Triple(InvestmentMovementKind.Valuation, 3_020_000, "2026-08-03"),
            Triple(InvestmentMovementKind.Valuation, 3_050_000, "2026-10-09")
        ),
        version = 3
    )

    /** Presses the pad: digits, "," for the comma and "K" for the 000 key. */
    private fun InvestmentEditorViewModel.type(keys: String) {
        keys.forEach { key ->
            onAction(
                when (key) {
                    'K' -> InvestmentEditorAction.TripleZero
                    ',' -> InvestmentEditorAction.Comma
                    else -> InvestmentEditorAction.Digit(key.digitToInt())
                }
            )
        }
    }

    private fun InvestmentEditorViewModel.identity(
        name: String = "CDT Banco",
        description: String = "CDT a 360 días",
        icon: String = "account_balance"
    ) {
        onAction(InvestmentEditorAction.NameChanged(name))
        onAction(InvestmentEditorAction.DescriptionChanged(description))
        onAction(InvestmentEditorAction.IconPicked(icon))
    }

    private fun created() = commands.answers.add(
        committedInvestment(operation(1), OperationKind.CreateInvestment, createdId)
    )

    private fun operation(n: Int) = (
        OperationId
            .of("00000000-0000-4000-a000-%012d".format(n)) as Outcome.Success
        ).value

    @Test
    fun aPesosInvestmentWithLoMismoStartsWithItsContributionAsItsValue() = runTest {
        created()
        val model = editor()
        model.identity()
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        // IN03: CDT Banco, aporte 10.000.000.
        model.type("10KK")

        val state = model.state.value
        assertEquals("10.000.000", state.firstContribution?.digits)
        assertTrue(state.sameValue)
        assertTrue(state.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as CreateInvestment
        assertEquals("CDT Banco", sent.name.value)
        assertEquals("CDT a 360 días", sent.description?.value)
        assertEquals("account_balance", sent.icon.value)
        assertEquals(Currency.COP, sent.currency)
        assertEquals(10_000_000L, sent.firstContribution.money.minorUnits)
        assertEquals(10_000_000L, sent.firstValue.money.minorUnits)
        assertEquals(Currency.COP, sent.firstValue.money.currency)
        assertEquals(LocalDate.of(2026, 10, 25), sent.date)
        assertNull(sent.newRate)
        assertEquals(createdId, model.state.value.created)
    }

    @Test
    fun otroValorIsTypedAndMayBeZero() = runTest {
        created()
        val model = editor()
        model.identity(name = "Parqueadero", description = "Finca raíz", icon = "home")
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("18KK")
        model.onAction(InvestmentEditorAction.SameValuePicked(false))

        // Otro valor opens the pad on the value, and nothing typed yet waits.
        assertEquals(InvestmentField.Value, model.state.value.padField)
        assertFalse(model.state.value.canSubmit)
        model.type("0")
        assertEquals("0", model.state.value.otherValue?.digits)
        assertTrue(model.state.value.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as CreateInvestment
        assertEquals(18_000_000L, sent.firstContribution.money.minorUnits)
        assertEquals(0L, sent.firstValue.money.minorUnits)
    }

    @Test
    fun otroValorKeepsWhatWasTypedForAnInvestmentFromBefore() = runTest {
        created()
        val model = editor()
        model.identity(name = "Parqueadero", description = "Finca raíz", icon = "home")
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("18KK")
        model.onAction(InvestmentEditorAction.SameValuePicked(false))
        model.type("19KK")
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as CreateInvestment
        assertEquals(18_000_000L, sent.firstContribution.money.minorUnits)
        assertEquals(19_000_000L, sent.firstValue.money.minorUnits)
    }

    @Test
    fun aFirstContributionOfZeroWaitsAndPesosStopAtTwelveDigits() = runTest {
        val model = editor()
        model.identity()
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("0")
        assertNull(model.state.value.firstContribution)
        assertFalse(model.state.value.canSubmit)

        // IN26: 999.999.999.999 is accepted, a thirteenth digit is refused at entry.
        model.type("999999999999")
        model.type("9")
        assertEquals("999.999.999.999", model.state.value.firstContribution?.digits)
        assertEquals(InvestmentField.First, model.state.value.atLimit)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun dollarsWithNoRateAskForItFirstAndSendItInTheSameCommand() = runTest {
        created()
        val model = editor()
        model.identity(name = "Acciones EE. UU.", description = "ETF", icon = "trending_up")
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")

        // IN33: Crear inversión waits for the rate.
        assertTrue(model.state.value.rateNeeded)
        assertFalse(model.state.value.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)
        assertTrue(commands.sent.isEmpty())

        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.Rate))
        model.type("4150,25")
        model.onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.Rate))
        model.onAction(InvestmentEditorAction.DatePicked(LocalDate.of(2026, 10, 20)))
        assertEquals("4.150,25", model.state.value.rate)
        assertTrue(model.state.value.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as CreateInvestment
        assertEquals(Currency.USD, sent.currency)
        assertEquals(100_000L, sent.firstContribution.money.minorUnits)
        assertEquals(Currency.USD, sent.firstContribution.money.currency)
        assertEquals(415_025L, sent.newRate?.rate?.hundredths)
        assertEquals(LocalDate.of(2026, 10, 20), sent.newRate?.date)
        assertEquals(LocalDate.of(2026, 10, 25), sent.date)
    }

    @Test
    fun aRateOutsideItsBoundsKeepsCrearWaiting() = runTest {
        val model = editor()
        model.identity(name = "Acciones EE. UU.", description = "ETF", icon = "trending_up")
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.Rate))
        // IN32: 0,99 is refused; 1,00 is accepted.
        model.type("0,99")
        assertEquals(RateProblem.BelowMinimum, model.state.value.rateProblem)
        assertFalse(model.state.value.canSubmit)
        model.onAction(InvestmentEditorAction.Clear)
        model.type("1,00")
        assertNull(model.state.value.rateProblem)
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun dollarsWithARateNameItAndSendNoNewOne() = runTest {
        reads.board.value = InvestmentBoardState.Ready(ratesBoard(rateEntry(415_025, "2026-10-20")))
        created()
        val model = editor()
        model.identity(name = "Acciones EE. UU.", description = "ETF", icon = "trending_up")
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000,00")

        val state = model.state.value
        assertFalse(state.rateNeeded)
        assertEquals(RateModel("4.150,25", LocalDate.of(2026, 10, 20)), state.currentRate)
        assertTrue(state.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as CreateInvestment
        assertNull(sent.newRate)
        assertEquals(100_000L, sent.firstContribution.money.minorUnits)
    }

    @Test
    fun dollarsAreTypedWithTheCommaToTheExactCent() = runTest {
        val model = editor()
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))

        model.type("1000,00")
        assertEquals("USD", model.state.value.firstContribution?.symbol)
        assertEquals("1.000,00", model.state.value.firstContribution?.digits)
        assertEquals("1.000,00 dólares", model.state.value.firstContribution?.spoken)

        // IN34: at most two decimals; 000 is not a key for dollars.
        model.type("5")
        model.onAction(InvestmentEditorAction.TripleZero)
        assertEquals("1.000,00", model.state.value.firstContribution?.digits)
        assertEquals(InvestmentField.First, model.state.value.atLimit)

        // USD 9.999.999.999,99 is accepted; USD 10.000.000.000 cannot be typed.
        model.onAction(InvestmentEditorAction.Clear)
        model.type("9999999999,99")
        assertEquals("9.999.999.999,99", model.state.value.firstContribution?.digits)
        assertTrue(model.state.value.firstReady)
        model.onAction(InvestmentEditorAction.Clear)
        model.type("10000000000")
        assertEquals("1.000.000.000", model.state.value.firstContribution?.digits)
        assertEquals(InvestmentField.First, model.state.value.atLimit)

        // USD 0,01 is the smallest aporte.
        model.onAction(InvestmentEditorAction.Clear)
        model.type(",01")
        assertEquals("0,01", model.state.value.firstContribution?.digits)
        assertTrue(model.state.value.firstReady)
    }

    @Test
    fun aNameOverFortyIsRefusedAndNothingIsSent() = runTest {
        val model = editor()
        model.identity(name = "a".repeat(41))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")

        val state = model.state.value
        assertEquals("a".repeat(41), state.name)
        assertEquals(41, state.nameLength)
        assertEquals(InvestmentTextError.TooLong, state.nameError)
        assertFalse(state.canSubmit)
        model.onAction(InvestmentEditorAction.Submit)
        assertTrue(commands.sent.isEmpty())

        model.onAction(InvestmentEditorAction.NameChanged("a".repeat(40)))
        assertTrue(model.state.value.canSubmit)
    }

    @Test
    fun aDescriptionOverFortyIsRefused() = runTest {
        val model = editor()
        model.identity(description = "d".repeat(41))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")
        assertEquals(InvestmentTextError.TooLong, model.state.value.descriptionError)
        assertFalse(model.state.value.canSubmit)
    }

    @Test
    fun editingSendsNameDescriptionAndIconOnlyAtTheVersionRead() = runTest {
        reads.board.value = InvestmentBoardState.Ready(investmentsBoard(listOf(ecopetrol())))
        commands.answers +=
            committedInvestment(operation(1), OperationKind.EditInvestment, ecopetrolId)
        val model = editing()

        val read = model.state.value
        assertTrue(read.editing)
        assertFalse(read.loading)
        assertEquals("Ecopetrol", read.name)
        assertEquals("Acciones", read.description)
        assertEquals("trending_up", read.iconKey)
        assertEquals(9, read.movementCount)

        model.onAction(InvestmentEditorAction.NameChanged("Ecopetrol ADR"))
        model.onAction(InvestmentEditorAction.DescriptionChanged(""))
        model.onAction(InvestmentEditorAction.IconPicked("insights"))
        // Currency and amounts are not offered when editing.
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        assertEquals(Currency.COP, model.state.value.currency)
        assertNull(model.state.value.padField)
        model.onAction(InvestmentEditorAction.Submit)

        val sent = commands.sent.single() as EditInvestment
        assertEquals(ecopetrolId, sent.investmentId.value)
        assertEquals(3L, sent.expectedVersion)
        assertEquals("Ecopetrol ADR", sent.name.value)
        assertNull(sent.description)
        assertEquals("insights", sent.icon.value)
        assertEquals(InvestmentEditorResult.Updated, model.state.value.result)
    }

    @Test
    fun anUnchangedEditSaysSoAndSendsNothing() = runTest {
        reads.board.value = InvestmentBoardState.Ready(investmentsBoard(listOf(ecopetrol())))
        val model = editing()
        model.onAction(InvestmentEditorAction.Submit)

        assertTrue(commands.sent.isEmpty())
        assertEquals(InvestmentSaveError.Unchanged, model.state.value.saveError)

        // The command's own Unchanged says the same.
        commands.answers += InvestmentCommandOutcome.Rejected(InvestmentRejection.Unchanged)
        model.onAction(InvestmentEditorAction.NameChanged("Ecopetrol "))
        model.onAction(InvestmentEditorAction.NameChanged("Ecopetrol S.A."))
        model.onAction(InvestmentEditorAction.Submit)
        assertEquals(1, commands.sent.size)
        assertEquals(InvestmentSaveError.Unchanged, model.state.value.saveError)
        assertNull(model.state.value.result)
    }

    @Test
    fun deletingAsksFirstCountingTheMovementsThenSendsDeleteInvestment() = runTest {
        reads.board.value = InvestmentBoardState.Ready(investmentsBoard(listOf(ecopetrol())))
        commands.answers +=
            committedInvestment(operation(1), OperationKind.DeleteInvestment, ecopetrolId)
        val model = editing()

        model.onAction(InvestmentEditorAction.Delete)
        assertTrue(commands.sent.isEmpty())

        model.onAction(InvestmentEditorAction.AskToDelete)
        assertTrue(model.state.value.confirmDelete)
        assertEquals(9, model.state.value.movementCount)
        model.onAction(InvestmentEditorAction.CancelDelete)
        assertFalse(model.state.value.confirmDelete)
        assertTrue(commands.sent.isEmpty())

        model.onAction(InvestmentEditorAction.AskToDelete)
        model.onAction(InvestmentEditorAction.Delete)
        val sent = commands.sent.single() as DeleteInvestment
        assertEquals(ecopetrolId, sent.investmentId.value)
        assertEquals(3L, sent.expectedVersion)
        // The board no longer holds it: the form ends as deleted, not discarded.
        reads.board.value = InvestmentBoardState.Ready(investmentsBoard(emptyList()))
        assertEquals(InvestmentEditorResult.Deleted, model.state.value.result)
    }

    @Test
    fun anUnknownOutcomeIsAskedAboutWithTheSameIdBeforeAnythingElse() = runTest {
        val unknown = operation(1)
        commands.answers += InvestmentCommandOutcome.OutcomeUnknown(unknown)
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val model = editor()
        model.identity()
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("10KK")
        model.onAction(InvestmentEditorAction.Submit)

        assertEquals(listOf(unknown), commands.asked)
        assertEquals(InvestmentSaveError.Unknown, model.state.value.saveError)
        assertFalse(model.state.value.canSubmit)
        // Crear cannot send a second attempt while the first is unknown.
        model.onAction(InvestmentEditorAction.Submit)
        assertEquals(1, commands.sent.size)

        commands.found = Outcome.Success(
            committedInvestment(unknown, OperationKind.CreateInvestment, createdId).change
        )
        model.onAction(InvestmentEditorAction.Retry)
        assertEquals(listOf(unknown, unknown), commands.asked)
        assertEquals(1, commands.sent.size)
        assertEquals(createdId, model.state.value.created)
    }

    @Test
    fun anUnknownOutcomeFoundNotSavedLetsThePersonTryAgain() = runTest {
        commands.answers += InvestmentCommandOutcome.OutcomeUnknown(operation(1))
        commands.found = Outcome.Success(null)
        val model = editor()
        model.identity()
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("10KK")
        model.onAction(InvestmentEditorAction.Submit)

        assertEquals(InvestmentSaveError.NotSaved, model.state.value.saveError)
        assertTrue(model.state.value.canSubmit)
        assertNull(model.state.value.result)
    }

    @Test
    fun certainRejectionsSayWhy() = runTest {
        val cases = listOf(
            InvestmentRejection.RateRequired to InvestmentSaveError.RateRequired,
            InvestmentRejection.FutureDate(LocalDate.of(2026, 10, 25)) to
                InvestmentSaveError.FutureDate,
            InvestmentRejection.InvalidAmount to InvestmentSaveError.Invalid,
            InvestmentRejection.InvalidDate to InvestmentSaveError.Invalid,
            InvestmentRejection.StaleGeneration(2) to InvestmentSaveError.Changed,
            InvestmentRejection.VersionConflict(4) to InvestmentSaveError.Changed
        )
        for ((reason, error) in cases) {
            commands.answers += InvestmentCommandOutcome.Rejected(reason)
            val model = editor()
            model.identity()
            model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
            model.type("1000")
            model.onAction(InvestmentEditorAction.Submit)
            assertEquals("$reason", error, model.state.value.saveError)
            assertNull(model.state.value.result)
            assertTrue("$reason", model.state.value.canSubmit)
        }
    }

    @Test
    fun aStaleGenerationTakesTheNewOneForTheNextAttempt() = runTest {
        commands.answers +=
            InvestmentCommandOutcome.Rejected(InvestmentRejection.StaleGeneration(2))
        created()
        val model = editor()
        model.identity()
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")
        reads.board.value =
            InvestmentBoardState.Ready(investmentsBoard(emptyList(), generation = 2))
        model.onAction(InvestmentEditorAction.Submit)
        model.onAction(InvestmentEditorAction.Submit)

        assertEquals(listOf(1L, 2L), commands.sent.map { it.generation })
    }

    @Test
    fun theDraftComesBackAfterTheProcessIsKilled() = runTest {
        val saved = SavedStateHandle()
        val before = editor(saved)
        before.identity(name = "Acciones EE. UU.", description = "ETF", icon = "trending_up")
        before.onAction(InvestmentEditorAction.CurrencyPicked(Currency.USD))
        before.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        before.type("250,5")
        before.onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.First))
        before.onAction(InvestmentEditorAction.DatePicked(LocalDate.of(2026, 10, 16)))
        before.onAction(InvestmentEditorAction.SameValuePicked(false))
        before.type("260")
        before.onAction(InvestmentEditorAction.OpenPad(InvestmentField.Rate))
        before.type("4180,7")

        val after = editor(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        val state = after.state.value
        assertEquals("Acciones EE. UU.", state.name)
        assertEquals("ETF", state.description)
        assertEquals("trending_up", state.iconKey)
        assertEquals(Currency.USD, state.currency)
        assertEquals("250,5", state.firstContribution?.digits)
        assertEquals(LocalDate.of(2026, 10, 16), state.date)
        assertFalse(state.sameValue)
        assertEquals("260", state.otherValue?.digits)
        assertEquals("4.180,7", state.rate)
        assertEquals(InvestmentField.Rate, state.padField)
        assertTrue(state.rateNeeded)

        after.type("0")
        assertEquals("4.180,70", after.state.value.rate)
    }

    @Test
    fun anOperationInFlightWhenTheProcessDiedIsAskedAboutFirst() = runTest {
        val saved = SavedStateHandle()
        commands.answers += InvestmentCommandOutcome.OutcomeUnknown(operation(1))
        commands.found = Outcome.Failure(StorageFailure.Unavailable("x"))
        val before = editor(saved)
        before.identity()
        before.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        before.type("1000")
        before.onAction(InvestmentEditorAction.Submit)
        assertEquals(InvestmentSaveError.Unknown, before.state.value.saveError)

        commands.found = Outcome.Success(
            committedInvestment(operation(1), OperationKind.CreateInvestment, createdId).change
        )
        val after = editor(SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        assertEquals(listOf(operation(1), operation(1)), commands.asked)
        assertEquals(1, commands.sent.size)
        assertEquals(createdId, after.state.value.created)
    }

    @Test
    fun aNewInvestmentStartsInTheCurrencyChosenInConfiguracion() = runTest {
        created()
        settings.setNewInvestmentCurrency(Currency.USD)
        val model = editor()

        assertEquals(Currency.USD, model.state.value.currency)
        // No rate yet: dollars ask for one before Crear inversión, as when picked here.
        model.identity(name = "Acciones EE. UU.", description = "ETF", icon = "trending_up")
        model.onAction(InvestmentEditorAction.OpenPad(InvestmentField.First))
        model.type("1000")
        assertTrue(model.state.value.rateNeeded)

        // Pesos may still be picked for this one.
        model.onAction(InvestmentEditorAction.CurrencyPicked(Currency.COP))
        assertEquals(Currency.COP, model.state.value.currency)
    }

    @Test
    fun startingInTheChosenCurrencyIsNotAChangeToDiscard() = runTest {
        settings.setNewInvestmentCurrency(Currency.USD)
        val model = editor()

        model.onAction(InvestmentEditorAction.Back)

        assertFalse(model.state.value.confirmDiscard)
        assertEquals(InvestmentEditorResult.Discarded, model.state.value.result)
    }

    @Test
    fun editingKeepsTheInvestmentsOwnCurrencyWhateverIsChosen() = runTest {
        reads.board.value = InvestmentBoardState.Ready(investmentsBoard(listOf(ecopetrol())))
        settings.setNewInvestmentCurrency(Currency.USD)

        assertEquals(Currency.COP, editing().state.value.currency)
    }

    @Test
    fun closingWithATypedDraftAsksFirst() = runTest {
        val model = editor()
        model.onAction(InvestmentEditorAction.Back)
        assertEquals(InvestmentEditorResult.Discarded, model.state.value.result)

        val typed = editor()
        typed.onAction(InvestmentEditorAction.NameChanged("CDT"))
        typed.onAction(InvestmentEditorAction.Back)
        assertTrue(typed.state.value.confirmDiscard)
        assertNull(typed.state.value.result)
        typed.onAction(InvestmentEditorAction.KeepEditing)
        assertFalse(typed.state.value.confirmDiscard)
        typed.onAction(InvestmentEditorAction.Back)
        typed.onAction(InvestmentEditorAction.DiscardConfirmed)
        assertEquals(InvestmentEditorResult.Discarded, typed.state.value.result)
    }

    @Test
    fun aFailedReadIsNeverAnEmptyFormAndADateAfterTodayIsRefused() = runTest {
        reads.board.value = InvestmentBoardState.Failed(StorageFailure.Unavailable("x"), null)
        val failed = editing()
        assertTrue(failed.state.value.loadFailed)
        assertFalse(failed.state.value.canSubmit)

        reads.board.value = InvestmentBoardState.Ready(ratesBoard())
        val model = editor()
        model.onAction(InvestmentEditorAction.OpenDatePicker(InvestmentField.First))
        model.onAction(InvestmentEditorAction.DatePicked(LocalDate.of(2026, 10, 26)))
        assertEquals(LocalDate.of(2026, 10, 25), model.state.value.date)
    }
}
