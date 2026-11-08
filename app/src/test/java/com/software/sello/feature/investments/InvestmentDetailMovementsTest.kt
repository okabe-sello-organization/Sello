package com.software.sello.feature.investments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * An investment's chart and movements as the approved proposal draws them (owner,
 * 2026-11-02): the picked value above the chart, the amount at the end of each row with
 * its figure under it (IN37), a note as the title, and month lines in the full list only.
 * Synthetic figures; today is Thursday 22 October 2026.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InvestmentDetailMovementsTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val id = "00000000-0000-4000-9000-000000000001"

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun movementId(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    /**
     * IN37, Ecopetrol: the March 2026 dividend carries the note "Trimestre I". In
     * [Currency.USD] the same units are cents.
     */
    private fun summary(currency: Currency = Currency.COP): InvestmentSummary {
        val movements = listOf(
            movement(1, Contribution, 2_000_000, "2025-03-02", currency = currency),
            movement(2, Valuation, 2_000_000, "2025-03-02", currency = currency),
            movement(3, Contribution, 1_000_000, "2025-07-03", currency = currency),
            movement(4, Valuation, 3_010_000, "2025-07-03", currency = currency),
            movement(5, Valuation, 2_900_000, "2025-12-01", currency = currency),
            movement(6, Dividend, 89_000, "2026-03-15", "Trimestre I", currency),
            movement(7, Valuation, 3_120_000, "2026-04-02", currency = currency),
            movement(8, Dividend, 89_000, "2026-09-15", currency = currency),
            movement(9, Valuation, 3_050_000, "2026-10-09", currency = currency)
        )
        val history = InvestmentHistory(
            Investment(
                InvestmentId.of(id).value(),
                InvestmentName.of("Ecopetrol").value(),
                null,
                IconKey.of("trending_up").value(),
                currency,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            movements
        )
        return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
    }

    private fun movement(
        n: Int,
        kind: InvestmentMovementKind,
        units: Long,
        date: String,
        note: String? = null,
        currency: Currency = Currency.COP
    ): InvestmentMovement {
        val money = Money(units, currency)
        return InvestmentMovement(
            InvestmentMovementId.of(movementId(n)).value(),
            kind,
            if (kind == Valuation) {
                InvestmentAmount.ofValue(money).value()
            } else {
                InvestmentAmount.of(money).value()
            },
            LocalDate.parse(date),
            n.toLong(),
            Note.of(note).value(),
            1,
            Instant.EPOCH,
            Instant.EPOCH
        )
    }

    private fun show(all: Boolean = false, look: Look = Look()) {
        val summary = summary()
        val board = InvestmentBoard(
            1,
            1,
            listOf(summary),
            UsdRateBook(null, emptyList()),
            InvestmentPolicy.overview(listOf(summary.history to summary.figures), null)
        )
        val detail = InvestmentsRenderModel(testMoney()).detail(ReadState.Ready(board), id, today)
            as InvestmentDetailState.Ready
        rule.showSello(look) {
            var state by remember { mutableStateOf(detail.copy(allMovements = all)) }
            InvestmentDetailScreen(
                state,
                { action ->
                    if (action is InvestmentDetailAction.SelectValue) {
                        state = state.copy(selectedValue = action.id)
                    }
                },
                dateLabels(),
                PaddingValues(16.dp)
            )
        }
    }

    private fun row(n: Int) = hasTestTag("investment.movement.${movementId(n)}")

    @Test
    fun aNoteNamesTheDividendAndItsKindJoinsTheDay() {
        show(all = true)

        rule.onNode(
            row(6) and hasText("Trimestre I") and hasText("Dividendo · dom 15 mar")
        ).assertExists()
        rule.onNode(row(8) and hasText("Dividendo") and hasText("mar 15 sep")).assertExists()
    }

    @Test
    fun eachRowSaysItsAmountAndTheFigureUnderIt() {
        show(all = true)

        // The newest value: 3.050.000 + 178.000 − 3.000.000, the slip's gain.
        rule.onNode(
            row(9) and hasContentDescription("3.050.000 pesos") and hasText("ganancia ") and
                hasContentDescription("más 228.000 pesos")
        ).assertExists()
        rule.onNode(
            row(8) and hasContentDescription("más 89.000 pesos") and hasText("dividendos ") and
                hasContentDescription("178.000 pesos")
        ).assertExists()
        rule.onNode(
            row(5) and hasText("pérdida ") and hasContentDescription("menos 100.000 pesos")
        ).assertExists()
        rule.onNode(
            row(3) and hasContentDescription("más 1.000.000 pesos") and hasText("pusiste ") and
                hasContentDescription("3.000.000 pesos")
        ).assertExists()
        rule.onNode(row(2) and hasText("sin ganancia")).assertExists()
    }

    @Test
    fun theShortListHasNoMonthLines() {
        show()

        rule.onNodeWithTag("investment.movements.month.2026-10").assertDoesNotExist()
    }

    @Test
    fun theFootOffersVerLosButNoVariosMovimientos() {
        show()

        rule.onNodeWithTag("investment.showAll").assertExists()
        rule.onNodeWithText("Varios movimientos").assertDoesNotExist()
    }

    @Test
    fun theFullListHasOneLineAMonthAndAnotherYearSaysItsYear() {
        show(all = true)

        rule.onNode(hasTestTag("investment.movements.month.2026-10") and hasText("OCTUBRE"))
            .assertExists()
        rule.onNode(
            hasTestTag("investment.movements.month.2025-12") and hasText("DICIEMBRE 2025")
        ).assertExists()
    }

    @Test
    fun theLineAboveTheChartReadsTheNewestValueThenTheOnePicked() {
        show()

        rule.onNode(
            hasTestTag("investment.chart.picked") and hasText("9 oct 2026") and
                hasContentDescription("3.050.000 pesos")
        ).assertExists()
        rule.onNodeWithContentDescription(
            "3 jul 2025: vale 3.010.000 pesos, pusiste 3.000.000 pesos"
        ).performSemanticsAction(SemanticsActions.OnClick)
        rule.onNode(
            hasTestTag("investment.chart.picked") and hasText("3 jul 2025") and
                hasContentDescription("3.010.000 pesos") and
                hasContentDescription("3.000.000 pesos")
        ).assertExists()
    }

    @Test
    fun theChartAndListClaro() = still(dark = false, all = false)

    @Test
    fun theChartAndListOscuro() = still(dark = true, all = false)

    @Test
    fun theFullListClaro() = still(dark = false, all = true)

    @Test
    fun theFullListOscuro() = still(dark = true, all = true)

    private fun still(dark: Boolean, all: Boolean) {
        show(all, Look(dark = dark))
        rule.onNodeWithTag(
            if (all) "investment.movement.${movementId(1)}" else "investment.showAll"
        ).performScrollTo()
        rule.captureStill(
            "src/test/screenshots/investment-detail-${if (all) "movements-all" else "chart"}-" +
                "${if (dark) "oscuro" else "claro"}.png"
        )
    }

    private fun shot(name: String, state: InvestmentDetailState, dark: Boolean) {
        rule.showSello(Look(dark = dark)) {
            if (state == InvestmentDetailState.Loading) {
                // As InvestmentDestinations.kt frames it before it is read: the tab's name, no dock.
                ShellScreen(
                    octoberShell,
                    monthNames(),
                    {},
                    onBack = {},
                    titleOnly = "Inversiones",
                    entryDock = false
                ) { padding ->
                    InvestmentDetailScreen(state, {}, dateLabels(), padding)
                }
            } else {
                InvestmentDetailScreen(state, {}, dateLabels(), PaddingValues(16.dp))
            }
        }
        rule.captureStill(
            "src/test/screenshots/investment-detail-$name-${if (dark) "oscuro" else "claro"}.png"
        )
    }

    private fun ready(currency: Currency = Currency.COP): InvestmentDetailState.Ready {
        val summary = summary(currency)
        val board = InvestmentBoard(
            1,
            1,
            listOf(summary),
            UsdRateBook(null, emptyList()),
            InvestmentPolicy.overview(listOf(summary.history to summary.figures), null)
        )
        return InvestmentsRenderModel(testMoney()).detail(ReadState.Ready(board), id, today)
            as InvestmentDetailState.Ready
    }

    @Test fun loadingClaro() = shot("loading", InvestmentDetailState.Loading, dark = false)

    @Test fun loadingOscuro() = shot("loading", InvestmentDetailState.Loading, dark = true)

    @Test fun failedClaro() = shot("failed", InvestmentDetailState.Failed, dark = false)

    @Test fun failedOscuro() = shot("failed", InvestmentDetailState.Failed, dark = true)

    @Test fun staleClaro() = shot("stale", ready().copy(stale = true), dark = false)

    @Test fun staleOscuro() = shot("stale", ready().copy(stale = true), dark = true)

    /** No value given yet: what it is worth is not answered. */
    private fun noValue() = ready().copy(value = null, valuedOn = null, gain = null, chart = null)

    @Test fun noValueClaro() = shot("no-value", noValue(), dark = false)

    @Test fun noValueOscuro() = shot("no-value", noValue(), dark = true)

    /**
     * Issue 383: a dollar investment's lines say USD, as its hero does. Aportes 20.000,00 +
     * 10.000,00; no retiros; dividendos 890,00 twice; ganancia 30.500,00 + 0 + 1.780,00 −
     * 30.000,00.
     */
    @Test
    fun aDollarInvestmentWritesUsdOnEveryLine() {
        rule.showSello(Look()) {
            InvestmentDetailScreen(ready(Currency.USD), {}, dateLabels(), PaddingValues(16.dp))
        }

        listOf(
            "Aportaste" to "USD\u00A030.000,00",
            "Retiraste" to "USD\u00A00,00",
            "Dividendos y rentas" to "USD\u00A01.780,00",
            "Ganancia" to "+USD\u00A02.280,00"
        ).forEach { (label, amount) ->
            rule.onNode(hasText(label) and hasText(amount)).assertExists()
        }
        rule.onNodeWithTag("investment.chart.picked")
            .assert(hasText("USD\u00A030.500,00", substring = true))
    }

    @Test
    fun aDollarInvestmentWithNoValueWritesUsdOnAportaste() {
        rule.showSello(Look()) {
            InvestmentDetailScreen(
                ready(Currency.USD).copy(value = null, valuedOn = null, gain = null, chart = null),
                {},
                dateLabels(),
                PaddingValues(16.dp)
            )
        }

        rule.onNode(hasText("Aportaste") and hasText("USD\u00A030.000,00")).assertExists()
    }

    @Test fun dollarsClaro() = shot("usd", ready(Currency.USD), dark = false)

    @Test fun dollarsOscuro() = shot("usd", ready(Currency.USD), dark = true)
}
