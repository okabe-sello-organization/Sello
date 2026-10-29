package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MoneySemanticsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun cop(sign: MoneySign, digits: String, spoken: String) =
        MoneyTextValue(sign, "", digits, spoken)

    @Test
    fun amountsAnnounceTheFullFigureWithCurrencyAndDrawATrueMinus() {
        rule.inEveryScheme(
            content = {
                Column {
                    MoneyText(
                        cop(MoneySign.None, "937.200", "937.200 pesos"),
                        Modifier.testTag("hero"),
                        MoneyStyle.Hero
                    )
                    MoneyText(
                        cop(MoneySign.Minus, "10.000", "menos 10.000 pesos"),
                        Modifier.testTag("loss"),
                        MoneyStyle.Total
                    )
                    MoneyText(
                        cop(MoneySign.Plus, "850.000", "más 850.000 pesos"),
                        Modifier.testTag("line")
                    )
                    MoneyText(
                        MoneyTextValue(MoneySign.None, "USD", "2.340,00", "2.340,00 USD"),
                        Modifier.testTag("usd"),
                        MoneyStyle.Title
                    )
                }
            }
        ) {
            rule.onNodeWithTag("hero").assertContentDescriptionEquals("937.200 pesos")
            rule.onNodeWithTag("hero").assertTextEquals("937.200")
            rule.onNodeWithTag("loss").assertContentDescriptionEquals("menos 10.000 pesos")
            rule.onNodeWithTag("loss").assertTextEquals("\u221210.000")
            rule.onNodeWithTag("line").assertContentDescriptionEquals("más 850.000 pesos")
            rule.onNodeWithTag("line").assertTextEquals("+850.000")
            rule.onNodeWithTag("usd").assertContentDescriptionEquals("2.340,00 USD")
            rule.onNodeWithTag("usd").assertTextEquals("USD\u00A02.340,00")
        }
    }

    @Test
    fun theLargestAmountWrapsInsideANarrowSlipAtDoubleFontSize() {
        val max = cop(
            MoneySign.Minus,
            "9.223.372.036.854.775.808",
            "menos 9.223.372.036.854.775.808 pesos"
        )
        rule.inEveryScheme(
            fontScale = 2f,
            width = 320.dp,
            content = {
                Slip {
                    MoneyText(
                        cop(MoneySign.None, "1", "1 peso"),
                        Modifier.testTag("short"),
                        MoneyStyle.Hero
                    )
                    MoneyText(max, Modifier.testTag("hero"), MoneyStyle.Hero)
                    MoneyText(max, Modifier.testTag("line"))
                }
            }
        ) {
            // The slip leaves 320 - 2 x 16 for content.
            val oneLine = rule.onNodeWithTag("short").getUnclippedBoundsInRoot().height
            for (tag in listOf("hero", "line")) {
                val bounds = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue("$it $tag is ${bounds.width} wide", bounds.width <= 288.dp)
                rule.onNodeWithTag(tag).assertContentDescriptionEquals(max.spoken)
                rule.onNodeWithTag(tag).assertTextEquals(
                    if (tag == "hero") {
                        "\u22129.223.372.036.854.775.808"
                    } else {
                        "\u22129.223.372.036.854.775.808"
                    }
                )
            }
            val hero = rule.onNodeWithTag("hero").getUnclippedBoundsInRoot().height
            assertTrue(
                "$it hero should take several lines: $hero vs $oneLine",
                hero > oneLine * 1.5f
            )
        }
    }
}
