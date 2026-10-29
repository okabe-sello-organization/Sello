package com.software.sello.feature.recibo

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.inEveryScheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Receipt lines read as label then value. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReceiptLinesTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun receiptLinesReadAsLabelThenValue() {
        rule.inEveryScheme(
            content = {
                Slip {
                    LeaderLine("Uber", Modifier.testTag("leader"), caption = "Transporte") {
                        MoneyText(MoneyTextValue(MoneySign.None, "", "18.400", "18.400 pesos"))
                    }
                    LeaderLine("Fecha", "jue 22 oct", Modifier.testTag("plain"))
                    LeaderLine("Abrir", "Detalle", Modifier.testTag("action"), onClick = {})
                    TotalLine("Total", Modifier.testTag("total")) {
                        MoneyText(
                            MoneyTextValue(MoneySign.None, "", "126.000", "126.000 pesos"),
                            style = MoneyStyle.Title
                        )
                    }
                    KeyValue("Límite", "Sin límite", Modifier.testTag("kv"), caption = "este mes")
                }
            }
        ) {
            rule.onNodeWithTag("leader").assertTextContains("Uber")
            rule.onNodeWithTag("leader").assertTextContains("Transporte")
            rule.onNodeWithTag("leader").assertContentDescriptionEquals("18.400 pesos")
            rule.onNodeWithTag("plain").assertTextContains("Fecha")
            rule.onNodeWithTag("plain").assertTextContains("jue 22 oct")
            rule.onNodeWithTag("total").assertTextContains("Total")
            rule.onNodeWithTag("total").assertContentDescriptionEquals("126.000 pesos")
            rule.onNodeWithTag("kv").assertTextContains("Límite")
            rule.onNodeWithTag("kv").assertTextContains("Sin límite")
            rule.onNodeWithTag("action").assert(isButton)
            val action = rule.onNodeWithTag("action").getUnclippedBoundsInRoot().height
            assertTrue("$it action line is $action", action >= 48.dp)
        }
    }
}
