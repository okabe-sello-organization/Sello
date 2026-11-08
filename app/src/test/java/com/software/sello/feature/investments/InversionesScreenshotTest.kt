package com.software.sello.feature.investments

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateBook
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.port.InvestmentBoard
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.InvestmentSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Inversiones looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InversionesScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 25)
    private val money = testMoney()
    private val at = Instant.parse("2026-10-25T12:00:00Z")
    private var ids = 0

    private fun id() = "00000000-0000-4000-8000-%012d".format(++ids)

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun investment(
        name: String,
        description: String?,
        movements: List<Triple<InvestmentMovementKind, Long, String>>,
        currency: Currency = Currency.COP
    ): InvestmentSummary {
        val investment = Investment(
            InvestmentId.of(id()).value(),
            InvestmentName.of(name).value(),
            InvestmentDescription.of(description).value(),
            IconKey.of("home").value(),
            currency,
            1,
            at,
            at
        )
        val recorded = movements.mapIndexed { index, (kind, units, date) ->
            val amount = Money(units, currency)
            InvestmentMovement(
                InvestmentMovementId.of(id()).value(),
                kind,
                if (kind == InvestmentMovementKind.Valuation) {
                    InvestmentAmount.ofValue(amount).value()
                } else {
                    InvestmentAmount.of(amount).value()
                },
                LocalDate.parse(date),
                index + 1L,
                null,
                1,
                at,
                at
            )
        }
        val history = InvestmentHistory(investment, recorded)
        return InvestmentSummary(history, InvestmentPolicy.figures(history).value())
    }

    private val aporte = InvestmentMovementKind.Contribution
    private val retiro = InvestmentMovementKind.Withdrawal
    private val dividendo = InvestmentMovementKind.Dividend
    private val valor = InvestmentMovementKind.Valuation

    private val rate = UsdRateEntry(
        UsdRateId.of("00000000-0000-4000-8000-999999999999").value(),
        UsdRate.of(BigDecimal("4150.25")).value(),
        LocalDate.of(2026, 10, 20),
        1,
        at
    )

    /** Pesos with a gain, a fund with a withdrawal, dollars with the rate named, one without a value. */
    private val board = listOf(
        investment(
            "Parqueadero",
            "Finca raíz",
            listOf(
                Triple(aporte, 18_000_000, "2025-03-10"),
                Triple(valor, 18_000_000, "2025-03-10"),
                Triple(dividendo, 350_000, "2026-06-01"),
                Triple(valor, 19_000_000, "2026-06-12")
            )
        ),
        investment(
            "Fondo de acciones",
            "Fondo colectivo",
            listOf(
                Triple(aporte, 6_000_000, "2025-05-03"),
                Triple(valor, 6_000_000, "2025-05-03"),
                Triple(retiro, 500_000, "2026-08-03"),
                Triple(valor, 5_242_000, "2026-10-14")
            )
        ),
        investment(
            "Acciones EE. UU.",
            "ETF",
            listOf(Triple(aporte, 100_000, "2026-10-16"), Triple(valor, 105_050, "2026-10-16")),
            Currency.USD
        ),
        investment("Cripto", "Criptomoneda", listOf(Triple(aporte, 2_000_000, "2026-08-01")))
    ).let { investments ->
        InvestmentBoard(
            1,
            1,
            investments,
            UsdRateBook(rate, listOf(rate)),
            InvestmentPolicy.overview(investments.map { it.history to it.figures }, rate)
        )
    }
    private val shown =
        InvestmentsRenderModel(money).overview(ReadState.Ready(board), today)

    private fun capture(look: Look) {
        rule.showSello(look) {
            InversionesScreen(
                shown,
                {},
                dateLabels(),
                PaddingValues(16.dp)
            )
        }
        rule.captureStill("src/test/screenshots/inversiones-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    private fun shot(name: String, state: InversionesState, look: Look) {
        rule.showSello(look) {
            if (state == InversionesState.Loading) {
                // As InvestmentDestinations.kt frames it before the list is read: no chip, no dock.
                ShellScreen(
                    octoberShell,
                    monthNames(),
                    {},
                    navigation = appTabs(ShellTab.Inversiones),
                    titleOnly = "Inversiones",
                    entryDock = false
                ) { padding ->
                    InversionesScreen(state, {}, dateLabels(), padding)
                }
            } else {
                InversionesScreen(state, {}, dateLabels(), PaddingValues(16.dp))
            }
        }
        rule.captureStill("src/test/screenshots/inversiones-$name-${look.label}.png")
    }

    @Test fun loadingLight() = shot("loading", InversionesState.Loading, Look())

    @Test fun loadingDark() = shot("loading", InversionesState.Loading, Look(dark = true))

    @Test fun failedLight() = shot("failed", InversionesState.Failed, Look())

    @Test fun failedDark() = shot("failed", InversionesState.Failed, Look(dark = true))

    private val ready = shown as InversionesState.Ready

    private val empty = ready.copy(total = null, rows = emptyList(), rate = null)

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    @Test fun staleLight() = shot("stale", ready.copy(stale = true), Look())

    @Test fun staleDark() = shot("stale", ready.copy(stale = true), Look(dark = true))

    /** A dollar investment and no rate: no total can be given. */
    private val noTotal = ready.copy(total = null, rate = null, missingRate = true)

    @Test fun noTotalLight() = shot("no-total", noTotal, Look())

    @Test fun noTotalDark() = shot("no-total", noTotal, Look(dark = true))
}
