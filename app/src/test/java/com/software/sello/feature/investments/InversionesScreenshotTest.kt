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
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.showSello
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
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) =
            if (currency == Currency.COP) "$digits pesos" else "$digits dólares"

        override fun negative(amount: String) = "menos $amount"

        override fun positive(amount: String) = "más $amount"
    })
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
            InversionesScreen(shown, {}, dateLabels(), PaddingValues(16.dp))
        }
        rule.captureStill("src/test/screenshots/inversiones-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
