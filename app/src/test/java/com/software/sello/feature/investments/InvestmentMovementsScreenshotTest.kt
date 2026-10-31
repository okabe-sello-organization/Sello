package com.software.sello.feature.investments

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.BULK_RECEIPT_LINES_TAG
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentMovementKind.Contribution
import com.software.sello.domain.model.InvestmentMovementKind.Dividend
import com.software.sello.domain.model.InvestmentMovementKind.Valuation
import com.software.sello.domain.model.InvestmentMovementKind.Withdrawal
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.navigation.CorrectionNoticeBar
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Varios movimientos as the approved Anotar varios page draws it (SELLO-126), in Claro and
 * Oscuro at the CI phone's 412x914 dp, kept as committed images (ADR 0022). Synthetic
 * figures, every total worked by hand; today is Thursday 22 October 2026.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class InvestmentMovementsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    private fun line(
        key: String,
        kind: InvestmentMovementKind?,
        amount: MoneyTextValue?,
        date: LocalDate,
        note: String = "",
        valid: Boolean = kind != null,
        refusal: MovementLineRefusal? = null
    ) = MovementLineModel(key, kind, amount, valid, date, note, refusal)

    private fun receipt(
        lines: List<MovementLineModel>,
        highlighted: Int,
        contributed: MoneyTextValue,
        name: String = "Ecopetrol",
        usd: Boolean = false,
        busy: Boolean = false,
        error: InvestmentMovementsError? = null,
        stale: Boolean = false
    ) = InvestmentMovementsState(
        loading = false,
        name = name,
        usd = usd,
        lines = lines,
        highlighted = highlighted,
        contributed = contributed,
        today = today,
        busy = busy,
        error = error,
        stale = stale
    )

    /** An aporte of 500.000 on 3 oct, a dividend of 89.000 on 29 sep, today's value 3.600.000. */
    private val three = listOf(
        line("1", Contribution, pesos("500.000"), LocalDate.of(2026, 10, 3)),
        line("2", Dividend, pesos("89.000"), LocalDate.of(2026, 9, 29)),
        line("3", Valuation, pesos("3.600.000"), today)
    )

    private fun dollars(digits: String) =
        MoneyTextValue(MoneySign.None, "USD", digits, "$digits dólares")

    private val states = mapOf(
        "opening" to receipt(listOf(line("0", null, null, today)), 0, pesos("0")),
        // Aportado counts only the aporte: 500.000.
        "three" to receipt(three, 2, pesos("500.000")),
        "missing-type" to receipt(
            listOf(three[0], line("2", null, pesos("89.000"), LocalDate.of(2026, 9, 29)), three[2]),
            1,
            pesos("500.000")
        ),
        // 500.000 − 4.000.000 = −3.500.000; the retiro is above today's value of 3.600.000.
        "refused" to receipt(
            listOf(
                three[0],
                line(
                    "2",
                    Withdrawal,
                    pesos("4.000.000"),
                    today,
                    refusal = MovementLineRefusal.OverValue(pesos("3.600.000"))
                ),
                three[2]
            ),
            1,
            pesos("3.500.000", minus = true),
            error = InvestmentMovementsError.NotSaved
        ),
        // The last read failed: the receipt works from the investment as read before.
        "stale" to receipt(three, 2, pesos("500.000"), stale = true),
        "saving" to receipt(three, 2, pesos("500.000"), busy = true),
        "unknown" to receipt(three, 2, pesos("500.000"), error = InvestmentMovementsError.Unknown),
        // 250,50 + 300,00 = 550,50 aportado; the dividend of 12,40 stays out.
        "usd" to receipt(
            listOf(
                line("1", Contribution, dollars("250,50"), LocalDate.of(2026, 10, 12)),
                line("2", Contribution, dollars("300,00"), LocalDate.of(2026, 10, 5)),
                line(
                    "3",
                    Dividend,
                    dollars("12,40"),
                    LocalDate.of(2026, 10, 16),
                    "Dividendo de octubre"
                )
            ),
            2,
            dollars("550,50"),
            name = "Acciones EE. UU.",
            usd = true
        )
    )

    private fun capture(name: String, dark: Boolean) {
        val state = states.getValue(name)
        rule.showSello(Look(dark = dark)) { InvestmentMovementsScreen(state) {} }
        val theme = if (dark) "oscuro" else "claro"
        rule.captureStill("src/test/screenshots/investment-movements-$name-$theme.png")
    }

    private fun detail(dark: Boolean) {
        val state = InvestmentDetailState.Ready(
            id = "e",
            name = "Ecopetrol",
            description = null,
            iconKey = "trending_up",
            dollars = false,
            value = pesos("3.600.000"),
            valuedOn = today,
            gain = pesos("600.000"),
            // 3.600.000 against 3.500.000 put in: +600.000 with the dividends, 17,1 %.
            `return` = InvestmentReturn(GainDirection.Gain, 171),
            contributed = pesos("3.500.000"),
            withdrawn = pesos("0"),
            dividends = pesos("178.000"),
            since = YearMonth.of(2026, 3),
            chart = null,
            movements = listOf(
                InvestmentMovementModel("1", Valuation, today, null, pesos("3.600.000")),
                InvestmentMovementModel(
                    "2",
                    Contribution,
                    LocalDate.of(2026, 10, 3),
                    null,
                    pesos("500.000")
                ),
                InvestmentMovementModel(
                    "3",
                    Valuation,
                    LocalDate.of(2026, 10, 9),
                    null,
                    pesos("3.050.000")
                ),
                InvestmentMovementModel(
                    "4",
                    Dividend,
                    LocalDate.of(2026, 9, 29),
                    null,
                    pesos("89.000")
                ),
                InvestmentMovementModel(
                    "5",
                    Contribution,
                    LocalDate.of(2026, 3, 2),
                    null,
                    pesos("3.000.000")
                )
            ),
            today = today
        )
        // As the app frames it (InvestmentDestination), with the receipt's confirmation.
        val saved = CorrectionNotice.InvestmentMovementsRecorded(3, "Ecopetrol")
        rule.showSello(Look(dark = dark)) {
            ShellScreen(
                state = ShellState(
                    currentMonth = YearMonth.of(2026, 10),
                    selectedMonth = YearMonth.of(2026, 10),
                    monthPickerOpen = false,
                    pickerYear = 2026,
                    pendingEntry = null
                ),
                names = monthNames(),
                onAction = {},
                notice = { CorrectionNoticeBar(saved) {} },
                onBack = {},
                titleOnly = "Ecopetrol",
                entryDock = false,
                dock = SelloDock(
                    "Anotar movimiento",
                    "Anotar movimiento",
                    {},
                    tone = ButtonTone.Ink,
                    icon = SelloIcon.Add
                )
            ) { padding ->
                InvestmentDetailScreen(state, {}, dateLabels(), padding)
            }
        }
        val theme = if (dark) "oscuro" else "claro"
        rule.captureStill("src/test/screenshots/investment-detail-varios-$theme.png")
    }

    /** The receipt's lines scroll inside it once more lines than fit are typed. */
    private fun scrolls(count: Int): Boolean {
        val lines = (1..count).map {
            line("$it", Contribution, pesos("100.000"), LocalDate.of(2026, 10, it))
        }
        // count aportes of 100.000.
        rule.showSello { InvestmentMovementsScreen(receipt(lines, 0, pesos("${count}00.000"))) {} }
        val range = rule.onNodeWithTag(BULK_RECEIPT_LINES_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange]
        return range.maxValue() > 0f
    }

    @Test fun openingClaro() = capture("opening", dark = false)

    @Test fun openingOscuro() = capture("opening", dark = true)

    @Test fun threeClaro() = capture("three", dark = false)

    @Test fun threeOscuro() = capture("three", dark = true)

    @Test fun missingTypeClaro() = capture("missing-type", dark = false)

    @Test fun missingTypeOscuro() = capture("missing-type", dark = true)

    @Test fun refusedClaro() = capture("refused", dark = false)

    @Test fun refusedOscuro() = capture("refused", dark = true)

    @Test fun savingClaro() = capture("saving", dark = false)

    @Test fun savingOscuro() = capture("saving", dark = true)

    @Test fun unknownClaro() = capture("unknown", dark = false)

    @Test fun unknownOscuro() = capture("unknown", dark = true)

    @Test fun staleClaro() = capture("stale", dark = false)

    @Test fun staleOscuro() = capture("stale", dark = true)

    @Test fun usdClaro() = capture("usd", dark = false)

    @Test fun usdOscuro() = capture("usd", dark = true)

    @Test fun detailClaro() = detail(dark = false)

    @Test fun detailOscuro() = detail(dark = true)

    @Test
    fun sixLinesScrollInsideTheReceiptAtThePhoneSize() {
        assertTrue(scrolls(6))
    }
}
