package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bulk receipt of the approved Anotar varios page (SELLO-126), in Claro and Oscuro, at the
 * 412 dp width the design is drawn for. Synthetic figures; every total is worked by hand.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class BulkReceiptScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(
        name: String,
        dark: Boolean,
        prepare: () -> Unit = {},
        content: @Composable () -> Unit
    ) {
        rule.setContent {
            SelloTheme(darkTheme = dark, reducedMotion = true) {
                Box(
                    Modifier.requiredWidth(412.dp).background(SelloTheme.colors.desk)
                        .testTag("stage")
                ) {
                    Box(Modifier.padding(12.dp)) { content() }
                }
            }
        }
        rule.waitForIdle()
        prepare()
        val theme = if (dark) "oscuro" else "claro"
        rule.onNodeWithTag("stage").drawnBitmap()
            .captureBaseline("src/test/screenshots/bulk-receipt-$name-$theme.png")
    }

    @Composable
    private fun Receipt(
        lines: List<BulkLine>,
        highlighted: Int,
        caption: BulkCaption,
        total: BulkTotal = expenseTotal,
        addEnabled: Boolean = true,
        enabled: Boolean = true,
        dated: Boolean = true,
        highlightedFoot: (@Composable () -> Unit)? = null
    ) {
        BulkReceipt(
            lines = lines,
            highlighted = highlighted,
            caption = caption,
            total = total,
            onSelect = {},
            onRemove = {},
            onAdd = {},
            modifier = Modifier.height(SlipHeight),
            addEnabled = addEnabled,
            enabled = enabled,
            headerEnd = { if (dated) FieldPill("Hoy, jue 22", SelloIcon.Event, {}) },
            highlightedFoot = highlightedFoot
        )
    }

    private fun threeLines(dark: Boolean) = capture("three-lines", dark) {
        // 18.400 + 75.100 + 32.500 = 126.000
        Receipt(threeExpenses, 2, BulkCaption.Count("Anotar varios", "3 renglones"))
    }

    private fun missingCategory(dark: Boolean) = capture("missing-category", dark) {
        Receipt(
            listOf(threeExpenses[0], missingCategory, threeExpenses[2]),
            1,
            BulkCaption.Problem("Falta 1 categoría")
        )
    }

    private fun removing(dark: Boolean) = capture(
        "removing",
        dark,
        prepare = {
            // Held halfway, past the 40 % that removes it on letting go.
            rule.onAllNodesWithTag(BULK_LINE_TAG)[1].performTouchInput {
                down(centerRight - Offset(4f, 0f))
                moveBy(Offset(-width * 0.25f, 0f))
                moveBy(Offset(-width * 0.25f, 0f))
            }
            rule.waitForIdle()
        }
    ) {
        Receipt(threeExpenses, 2, BulkCaption.Count("Anotar varios", "3 renglones"))
    }

    private fun limit(dark: Boolean) = capture("thirty-lines", dark) {
        // 15 × 4.500 + 15 × 4.500 = 135.000
        val lines = (1..30).map { index ->
            if (index % 2 == 0) {
                categoryLine("$index", "Comida", "4.500")
            } else {
                BulkLine(
                    "$index",
                    SelloIcon.LocalCafe,
                    BulkMarkTone.Category,
                    "Café",
                    pesos("4.500"),
                    detail = "Tinto"
                )
            }
        }
        Receipt(
            lines,
            29,
            BulkCaption.Problem("Máximo 30 por recibo", icon = false),
            total = BulkTotal("Total", pesos("135.000")),
            addEnabled = false
        )
    }

    private fun goal(dark: Boolean) = capture("goal", dark) {
        // +100.000 + 80.000 − 50.000 = +130.000
        Receipt(
            listOf(
                BulkLine(
                    "1",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Aporte",
                    pesos("100.000").copy(sign = MoneySign.Plus),
                    detail = "lun 12 oct",
                    amountTone = BulkAmountTone.Gain
                ),
                BulkLine(
                    "2",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Aporte",
                    pesos("80.000").copy(sign = MoneySign.Plus),
                    detail = "mié 14 oct · Regalo de mamá",
                    amountTone = BulkAmountTone.Gain
                ),
                BulkLine(
                    "3",
                    SelloIcon.ArrowBack,
                    BulkMarkTone.Neutral,
                    "Retiro",
                    pesos("50.000", minus = true),
                    detail = "Arreglo del carro"
                )
            ),
            2,
            BulkCaption.Count("3 renglones"),
            total = BulkTotal(
                "Total",
                pesos("130.000").copy(sign = MoneySign.Plus),
                BulkAmountTone.Gain
            ),
            dated = false,
            highlightedFoot = { FieldPill("vie 16 oct", SelloIcon.Event, {}) }
        )
    }

    private fun investment(dark: Boolean) = capture("investment", dark) {
        // Aportado counts only the aporte: 500.000.
        Receipt(
            listOf(
                BulkLine(
                    "1",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Aporte",
                    pesos("500.000").copy(sign = MoneySign.Plus),
                    detail = "sáb 3 oct"
                ),
                BulkLine(
                    "2",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Dividendo o renta",
                    pesos("89.000").copy(sign = MoneySign.Plus),
                    detail = "mar 29 sep"
                ),
                BulkLine(
                    "3",
                    SelloIcon.Insights,
                    BulkMarkTone.Value,
                    "Valor de hoy",
                    pesos("3.600.000")
                )
            ),
            2,
            BulkCaption.Count("3 renglones"),
            total = BulkTotal("Aportado", pesos("500.000")),
            dated = false,
            highlightedFoot = { FieldPill("hoy, jue 22", SelloIcon.Event, {}) }
        )
    }

    private fun saving(dark: Boolean) = capture("saving", dark) {
        // Locked while saving: no highlight, no caret, Otro renglón waits.
        Receipt(
            threeExpenses,
            2,
            BulkCaption.Count("Anotar varios", "3 renglones"),
            enabled = false
        )
    }

    private fun usd(dark: Boolean) = capture("usd", dark) {
        // Aportado counts only the aportes: 250,50 + 300,00 = 550,50.
        Receipt(
            listOf(
                BulkLine(
                    "1",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Aporte",
                    dollars("250,50"),
                    detail = "lun 12 oct"
                ),
                BulkLine(
                    "2",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Aporte",
                    dollars("300,00"),
                    detail = "lun 5 oct"
                ),
                BulkLine(
                    "3",
                    SelloIcon.Add,
                    BulkMarkTone.Gain,
                    "Dividendo o renta",
                    dollars("12,40"),
                    detail = "Dividendo de octubre"
                )
            ),
            2,
            BulkCaption.Count("3 renglones"),
            total = BulkTotal(
                "Aportado",
                MoneyTextValue(MoneySign.None, "USD", "550,50", "550,50 dólares")
            ),
            dated = false,
            highlightedFoot = { FieldPill("vie 16 oct", SelloIcon.Event, {}) }
        )
    }

    private fun dollars(digits: String) =
        MoneyTextValue(MoneySign.Plus, "USD", digits, "más $digits dólares")

    private fun missingType(dark: Boolean) = capture("missing-type", dark) {
        Receipt(
            listOf(
                BulkLine(
                    "1",
                    null,
                    BulkMarkTone.Gain,
                    "Sin tipo",
                    pesos("40.000"),
                    titleSoft = true,
                    reason = "Elige un tipo"
                ),
                BulkLine("2", null, BulkMarkTone.Gain, "Sin tipo", null, titleSoft = true)
            ),
            1,
            BulkCaption.Problem("Falta 1 tipo"),
            total = BulkTotal("Aportado", pesos("0")),
            dated = false
        )
    }

    @Test fun threeLinesClaro() = threeLines(dark = false)

    @Test fun threeLinesOscuro() = threeLines(dark = true)

    @Test fun missingCategoryClaro() = missingCategory(dark = false)

    @Test fun missingCategoryOscuro() = missingCategory(dark = true)

    @Test fun removingClaro() = removing(dark = false)

    @Test fun removingOscuro() = removing(dark = true)

    @Test fun thirtyLinesClaro() = limit(dark = false)

    @Test fun thirtyLinesOscuro() = limit(dark = true)

    @Test fun goalClaro() = goal(dark = false)

    @Test fun goalOscuro() = goal(dark = true)

    @Test fun investmentClaro() = investment(dark = false)

    @Test fun investmentOscuro() = investment(dark = true)

    @Test fun savingClaro() = saving(dark = false)

    @Test fun savingOscuro() = saving(dark = true)

    @Test fun usdClaro() = usd(dark = false)

    @Test fun usdOscuro() = usd(dark = true)

    @Test fun missingTypeClaro() = missingType(dark = false)

    @Test fun missingTypeOscuro() = missingType(dark = true)

    private companion object {
        /** About the free height the receipt gets on a 412 × 914 dp phone. */
        val SlipHeight = 400.dp
    }
}
