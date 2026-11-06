package com.software.sello.feature.recibo

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.navigation.AdjustBudgetFrame
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.carry.CarryFigures
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Ajustar presupuesto looks like, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustBudgetScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val cafe = AdjustRow(
        id = "cafe",
        name = "Café",
        iconKey = "local_cafe",
        spent = plain("90.000"),
        limit = plain("50.000"),
        remaining = minus("40.000"),
        overBy = plain("40.000"),
        fraction = 1f,
        over = true,
        archived = false
    )

    private val viejo = cafe.copy(
        id = "viejo",
        name = "Viejo",
        spent = plain("0"),
        limit = plain("30.000"),
        remaining = plain("30.000"),
        overBy = null,
        fraction = 0f,
        over = false,
        archived = true
    )

    private val regalos = cafe.copy(
        id = "regalos",
        name = "Regalos",
        spent = plain("10.000"),
        limit = null,
        remaining = null,
        overBy = null,
        fraction = null,
        over = false,
        unlimited = true
    )

    /** October 80.000 over; Café 90.000 of 50.000. */
    private val ready = AdjustBudgetState.Ready(
        month = october,
        summary = ReciboSummary(
            label = ReciboAnswerLabel.Remaining,
            amount = minus("80.000"),
            stamp = ReciboStamp.Over,
            adjustable = true
        ),
        limit = plain("1.000.000"),
        spent = plain("1.080.000"),
        limited = listOf(cafe),
        open = listOf(regalos),
        stale = false,
        archived = listOf(viejo)
    )

    private fun capture(
        look: Look,
        sheet: LimitSheetState = LimitSheetState(october),
        file: String = "adjust-budget-${look.label}",
        state: AdjustBudgetState = ready
    ) {
        rule.showSello(look) {
            AdjustBudgetScreen(
                state = state,
                sheet = sheet,
                onAction = {},
                onSheet = {},
                onEditCategory = {},
                contentPadding = PaddingValues(12.dp)
            )
        }
        rule.captureStill("src/test/screenshots/$file.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    /** The month could not be read again: the sheet keeps its facts, with the notice. */
    private val sheet = LimitSheetState(
        month = october,
        categoryId = "cafe",
        name = "Café",
        iconKey = "local_cafe",
        spent = plain("90.000"),
        current = plain("50.000"),
        amount = plain("150.000"),
        chips = listOf(
            LimitChip(ChipKind.Spent, plain("90.000"), 90_000),
            LimitChip(ChipKind.CoverMonth, plain("130.000"), 130_000)
        ),
        fact = LimitFact(plain("1.100.000"), plain("20.000"), FactOutcome.Left, false),
        canSave = true
    )

    private fun limitSheet(name: String, state: LimitSheetState, dark: Boolean) = capture(
        Look(dark = dark),
        state,
        "limit-sheet-$name-${if (dark) "oscuro" else "claro"}"
    )

    @Test
    fun limitSheetOlderClaro() = limitSheet("stale", sheet.copy(stale = true), dark = false)

    @Test
    fun limitSheetOlderOscuro() = limitSheet("stale", sheet.copy(stale = true), dark = true)

    @Test fun limitSheetReadyClaro() = limitSheet("ready", sheet, dark = false)

    @Test fun limitSheetReadyOscuro() = limitSheet("ready", sheet, dark = true)

    private val readFailed = LimitSheetState(october, categoryId = "cafe", loadFailed = true)

    @Test fun limitSheetFailedClaro() = limitSheet("failed", readFailed, dark = false)

    @Test fun limitSheetFailedOscuro() = limitSheet("failed", readFailed, dark = true)

    @Test fun limitSheetBusyClaro() = limitSheet("busy", sheet.copy(busy = true), dark = false)

    @Test fun limitSheetBusyOscuro() = limitSheet("busy", sheet.copy(busy = true), dark = true)

    private val unknown = sheet.copy(error = LimitSheetError.Unknown, canSave = false)

    @Test fun limitSheetUnknownClaro() = limitSheet("unknown", unknown, dark = false)

    @Test fun limitSheetUnknownOscuro() = limitSheet("unknown", unknown, dark = true)

    private val refused = sheet.copy(error = LimitSheetError.NotSaved)

    @Test fun limitSheetRefusedClaro() = limitSheet("refused", refused, dark = false)

    @Test fun limitSheetRefusedOscuro() = limitSheet("refused", refused, dark = true)

    /** Café carries this month: the sheet's title says the limit with carry. */
    private val carrying = sheet.copy(carry = plain("20.000"))

    @Test fun limitSheetCarryClaro() = limitSheet("carry", carrying, dark = false)

    @Test fun limitSheetCarryOscuro() = limitSheet("carry", carrying, dark = true)

    @Test
    fun loadingLight() = capture(
        Look(),
        file = "adjust-budget-loading-${Look().label}",
        state = AdjustBudgetState.Loading
    )

    @Test
    fun loadingDark() = capture(
        Look(dark = true),
        file = "adjust-budget-loading-${Look(dark = true).label}",
        state = AdjustBudgetState.Loading
    )

    /** Loading in the frame ReciboDestinations.kt gives it: the title names its month. */
    private fun framed(look: Look) {
        rule.showSello(look) {
            AdjustBudgetFrame(octoberShell, october, {}, {}, null) { padding ->
                AdjustBudgetScreen(
                    state = AdjustBudgetState.Loading,
                    sheet = LimitSheetState(october),
                    onAction = {},
                    onSheet = {},
                    onEditCategory = {},
                    contentPadding = padding
                )
            }
        }
        rule.captureStill("src/test/screenshots/adjust-budget-framed-${look.label}.png")
    }

    @Test fun framedLight() = framed(Look())

    @Test fun framedDark() = framed(Look(dark = true))

    @Test
    fun failedLight() = capture(
        Look(),
        file = "adjust-budget-failed-${Look().label}",
        state = AdjustBudgetState.Failed
    )

    @Test
    fun failedDark() = capture(
        Look(dark = true),
        file = "adjust-budget-failed-${Look(dark = true).label}",
        state = AdjustBudgetState.Failed
    )

    @Test
    fun staleLight() = capture(
        Look(),
        file = "adjust-budget-stale-${Look().label}",
        state = ready.copy(stale = true)
    )

    @Test
    fun staleDark() = capture(
        Look(dark = true),
        file = "adjust-budget-stale-${Look(dark = true).label}",
        state = ready.copy(stale = true)
    )

    /**
     * Café carries −20.000 from September: base 50.000, month limit 30.000, 90.000 spent;
     * the month's limit with carry 980.000 (1.000.000 − 20.000), 1.080.000 spent.
     */
    private val carried = ready.copy(
        summary = ready.summary.copy(amount = minus("100.000")),
        limit = plain("980.000"),
        limited = listOf(
            cafe.copy(
                limit = plain("30.000"),
                remaining = minus("60.000"),
                overBy = plain("60.000"),
                carried = true
            )
        ),
        carry = CarryFigures(
            base = plain("1.000.000"),
            carry = minus("20.000"),
            carryMagnitude = plain("20.000"),
            direction = CarryDirection.Deficit,
            capacity = plain("980.000"),
            spent = plain("1.080.000"),
            chainStart = YearMonth.of(2026, 9),
            source = YearMonth.of(2026, 9)
        )
    )

    @Test fun carryClaro() = capture(Look(), file = "adjust-budget-carry-claro", state = carried)

    @Test
    fun carryOscuro() =
        capture(Look(dark = true), file = "adjust-budget-carry-oscuro", state = carried)

    // The limit sheet's other refusals, each said in loss ink under the amount.

    private val refusals = mapOf(
        "conflict" to LimitSheetError.Conflict,
        "data-changed" to LimitSheetError.DataChanged,
        "gone" to LimitSheetError.CategoryGone,
        "archived" to LimitSheetError.Archived,
        "month-ended" to LimitSheetError.MonthEnded,
        "above-cap" to LimitSheetError.LimitAboveCap,
        "carry-unavailable" to LimitSheetError.CarryUnavailable
    )

    /** Above the cap and carry not worked out are refused only while the category carries. */
    private val carryOnly = setOf("above-cap", "carry-unavailable")

    private fun refusal(name: String, dark: Boolean) = limitSheet(
        name,
        (if (name in carryOnly) carrying else sheet).copy(error = refusals.getValue(name)),
        dark
    )

    @Test fun conflictClaro() = refusal("conflict", dark = false)

    @Test fun conflictOscuro() = refusal("conflict", dark = true)

    @Test fun dataChangedClaro() = refusal("data-changed", dark = false)

    @Test fun dataChangedOscuro() = refusal("data-changed", dark = true)

    @Test fun goneClaro() = refusal("gone", dark = false)

    @Test fun goneOscuro() = refusal("gone", dark = true)

    @Test fun archivedClaro() = refusal("archived", dark = false)

    @Test fun archivedOscuro() = refusal("archived", dark = true)

    @Test fun monthEndedClaro() = refusal("month-ended", dark = false)

    @Test fun monthEndedOscuro() = refusal("month-ended", dark = true)

    @Test fun aboveCapClaro() = refusal("above-cap", dark = false)

    @Test fun aboveCapOscuro() = refusal("above-cap", dark = true)

    @Test fun carryUnavailableClaro() = refusal("carry-unavailable", dark = false)

    @Test fun carryUnavailableOscuro() = refusal("carry-unavailable", dark = true)
}
