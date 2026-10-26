package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Plan del mes's parts: the dashed slip, its three line weights and the expected chip. */
class PlanPartsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pesos(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val salary = "Salario esperado el vie 30 oct: 1.000.000 pesos"

    @Test
    fun theTagIsAHeadingSaidInOrdinaryWordsAndEveryLineIsReadAsLabelThenValue() {
        rule.inEveryScheme(content = {
            PlanSlip("Plan del mes", "Incluye lo que esperas recibir", Modifier.testTag("plan")) {
                LeaderLine("Anotado", Modifier.testTag("recorded")) {
                    MoneyText(pesos("2.000.000"))
                }
                LeaderLine("Esperado", Modifier.testTag("expected"), emphasis = LineEmphasis.Soft) {
                    MoneyText(pesos("1.000.000"))
                }
                TotalLine("Planeado", Modifier.testTag("planned"), rule = TotalRule.Single) {
                    MoneyText(pesos("3.000.000"))
                }
                LeaderLine(
                    "Sin asignar en el plan",
                    Modifier.testTag("unassigned"),
                    emphasis = LineEmphasis.Strong
                ) { MoneyText(pesos("200.000")) }
            }
        }) { scheme ->
            // Drawn in capitals, but said as written: not spelt out letter by letter.
            val tag = rule.onNode(isHeading()).fetchSemanticsNode()
            assertEquals(
                scheme,
                listOf("Plan del mes"),
                tag.config.getOrNull(SemanticsProperties.ContentDescription)
            )
            rule.onNodeWithText("Incluye lo que esperas recibir").assertIsDisplayed()
            mapOf(
                "recorded" to listOf("Anotado", "2.000.000 pesos"),
                "expected" to listOf("Esperado", "1.000.000 pesos"),
                "planned" to listOf("Planeado", "3.000.000 pesos"),
                "unassigned" to listOf("Sin asignar en el plan", "200.000 pesos")
            ).forEach { (line, said) ->
                val node = rule.onNodeWithTag(line).fetchSemanticsNode()
                val merged = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
                    .map { it.text } +
                    node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
                assertTrue("$scheme $line: $merged", said.all { part -> merged.any { part in it } })
            }
        }
    }

    @Test
    fun anExpectedChipIsOneSentenceThatCannotBeTapped() {
        rule.inEveryScheme(content = {
            ExpectedChip(
                "Salario · vie 30 oct ·",
                pesos("1.000.000"),
                salary,
                Modifier.testTag("chip")
            )
        }) { scheme ->
            val node = rule.onNodeWithContentDescription(salary).assertIsDisplayed()
                .fetchSemanticsNode()
            assertNull(scheme, node.config.getOrNull(SemanticsActions.OnClick))
            // Its parts are not read again one by one.
            assertFalse(scheme, node.children.isNotEmpty())
            assertTrue(
                scheme,
                rule.onNodeWithTag("chip").getUnclippedBoundsInRoot().height >= 32.dp
            )
        }
    }

    @Test
    fun atTwiceTheFontSizeTheChipStaysWithinTheScreenAndIsStillOneSentence() {
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            Column {
                ExpectedChip(
                    "Arriendo del apartamento · sáb 31 oct ·",
                    pesos("999.999.999.999"),
                    "Arriendo del apartamento esperado el sáb 31 oct: 999.999.999.999 pesos",
                    Modifier.testTag("chip")
                )
            }
        }) { scheme ->
            val chip = rule.onNodeWithTag("chip").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("$scheme ${chip.right}", chip.right <= 320.dp)
            // Wrapped onto more than one line rather than cut at the edge.
            assertTrue("$scheme ${chip.height}", chip.height > 48.dp)
        }
    }

    @Test
    fun aPlanSlipsLinesTakeTheThemesInkInEveryScheme() {
        var ink: Color? = null
        var expected: Color? = null
        rule.inEveryScheme(reset = { ink = null }, content = {
            expected = SelloTheme.colors.ink
            PlanSlip(tag = "Plan del mes", caption = "octubre") {
                ink = LocalContentColor.current
            }
        }) { scheme ->
            rule.waitForIdle()
            // Not the default black: the slip has no paper, so it must say its ink itself.
            assertEquals(scheme, expected, ink)
        }
    }
}
