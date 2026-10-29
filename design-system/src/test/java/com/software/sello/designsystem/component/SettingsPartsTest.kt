package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Configuración's parts: the row, the ink swatches and the option slip. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsPartsTest {
    @get:Rule
    val rule = createComposeRule()

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun aRowThatLeadsSomewhereIsOneButtonReadWithItsCaptionAndValue() {
        var taps = 0
        rule.inEveryScheme(reset = { taps = 0 }, content = {
            SettingRow(
                title = "Orden de las categorías",
                icon = SelloIcon.Sort,
                caption = "Con los gastos del mes que ves",
                value = "Más usadas",
                onClick = { taps++ }
            )
        }) { scheme ->
            val row = rule.onNode(
                hasText("Orden de las categorías") and hasText("Más usadas") and
                    hasText("Con los gastos del mes que ves")
            )
            row.assert(isButton)
            assertTrue(scheme, row.getUnclippedBoundsInRoot().height >= 48.dp)
            row.performClick()
            assertEquals(scheme, 1, taps)
        }
    }

    @Test
    fun aRowWithoutAnActionIsNotAButtonAndADisabledOneDoesNotAnswer() {
        var taps = 0
        rule.inEveryScheme(reset = { taps = 0 }, content = {
            Column {
                SettingRow(title = "Tema", icon = SelloIcon.Contrast)
                SettingRow(
                    title = "Dólar",
                    icon = SelloIcon.CurrencyExchange,
                    value = "4.150,25",
                    onClick = { taps++ },
                    enabled = false
                )
            }
        }) { scheme ->
            rule.onNodeWithText("Tema").assert(!isButton)
            rule.onNode(hasText("Dólar") and hasText("4.150,25")).performClick()
            assertEquals(scheme, 0, taps)
        }
    }

    @Test
    fun eachInkIsARadioButtonAndChoosingOneReportsIt() {
        var chosen by mutableStateOf(SelloInk.Cobalto)
        rule.inEveryScheme(reset = { chosen = SelloInk.Cobalto }, content = {
            InkSwatches(
                inks = listOf(SelloInk.Cobalto to "Cobalto", SelloInk.Violeta to "Violeta"),
                selected = chosen,
                onSelect = { chosen = it }
            )
        }) { scheme ->
            assertEquals(scheme, 2, rule.onAllNodes(isSelectable()).fetchSemanticsNodes().size)
            rule.onNodeWithText("Cobalto").assert(isRadio).assertIsSelected()
            rule.onNodeWithText("Violeta").assertIsNotSelected().performClick()
            assertEquals(scheme, SelloInk.Violeta, chosen)
            rule.onNodeWithText("Violeta").assertIsSelected()
            assertTrue(
                scheme,
                rule.onNodeWithText("Violeta").getUnclippedBoundsInRoot().height >= 48.dp
            )
        }
    }

    @Test
    fun theOptionSlipMarksTheCurrentChoiceAndReportsANewOne() {
        var picked = -1
        var dismissed = 0
        rule.setContent {
            SelloTheme {
                OptionSlip(
                    title = "Dirección",
                    options = listOf("Mayor a menor", "Menor a mayor"),
                    selectedIndex = 0,
                    onSelect = { picked = it },
                    onDismiss = { dismissed++ }
                )
            }
        }
        rule.onNodeWithText("Mayor a menor").assert(isRadio).assertIsSelected()
        rule.onNodeWithText("Menor a mayor").assertIsNotSelected().performClick()
        assertEquals(1, picked)
        rule.onNodeWithText("Cancelar").performClick()
        assertEquals(1, dismissed)
    }
}
