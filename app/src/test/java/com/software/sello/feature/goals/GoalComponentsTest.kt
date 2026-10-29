package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.inEveryScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The goal pill and the Recibo pin say their state and answer a touch. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<String>()

    /** The area a finger can press, which may reach past what is drawn. */
    private fun assertTarget(control: SemanticsNodeInteraction, scheme: String) {
        val node = control.fetchSemanticsNode()
        val (width, height) = with(node.layoutInfo.density) {
            node.touchBoundsInRoot.width.toDp() to node.touchBoundsInRoot.height.toDp()
        }
        assertTrue("$scheme $width × $height", width >= 48.dp && height >= 48.dp)
    }

    @Test
    fun theGoalPillSaysItsExactProgressOpensTheGoalAndItsPlusIsAportar() {
        rule.inEveryScheme(reset = { actions.clear() }, content = {
            GoalPill(
                "Viaje a la costa",
                "57 %",
                0.57f,
                SelloIcon.Flight,
                "Aportar a Viaje a la costa",
                { actions += "abrir" },
                { actions += "aportar" },
                openDescription = "Viaje a la costa, 57 por ciento. Ver meta"
            )
        }) { scheme ->
            val open = rule.onNodeWithContentDescription(
                "Viaje a la costa, 57 por ciento. Ver meta"
            )
            open.assertHasClickAction().assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(0.57f, 0f..1f)
                )
            ).performClick()
            val plus = rule.onNodeWithContentDescription("Aportar a Viaje a la costa")
            plus.performClick()

            assertEquals(scheme, listOf("abrir", "aportar"), actions)
            assertTarget(open, scheme)
            assertTarget(plus, scheme)
        }
    }

    @Test
    fun theReciboPinIsARadioThatSaysWhetherTheGoalIsChosen() {
        var chosen = false
        rule.inEveryScheme(reset = { chosen = false }, content = {
            Column {
                GoalPinPill("Mostrar en Recibo", false, { chosen = true }, Modifier.testTag("off"))
                GoalPinPill("En tu Recibo", true, {}, Modifier.testTag("on"))
            }
        }) { scheme ->
            rule.onNodeWithTag("on").assertIsSelected()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            val off = rule.onNodeWithTag("off").assertIsNotSelected()
            off.performClick()

            assertTrue(scheme, chosen)
            assertTarget(off, scheme)
        }
    }
}
