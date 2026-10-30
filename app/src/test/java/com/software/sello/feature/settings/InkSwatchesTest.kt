package com.software.sello.feature.settings

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
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.inEveryScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The ink swatches are radio buttons and report the chosen ink. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InkSwatchesTest {
    @get:Rule
    val rule = createComposeRule()

    private val isRadio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

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
}
