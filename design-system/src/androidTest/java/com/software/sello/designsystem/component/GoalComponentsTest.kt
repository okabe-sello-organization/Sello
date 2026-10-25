package com.software.sello.designsystem.component

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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The goal parts that had no device test (gap list, SELLO-085): `GoalPill`,
 * `MovementRow`, `StampTicket`, `GoalPinPill` and `IconChoiceStrip`, in each of the four
 * colour schemes. What a screen reader hears, what each control does and that every
 * target is at least 48dp.
 */
@RunWith(AndroidJUnit4::class)
class GoalComponentsTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<String>()

    private val half = MilestoneSpec("50 %", "50 por ciento, logrado", MilestoneState.Earned)
    private val quarter = MilestoneSpec("25 %", "25 por ciento, logrado", MilestoneState.Earned)

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
    fun aMovementWithAStampIsSaidWithItsAmountAndAWithdrawalHasNoStamp() {
        rule.inEveryScheme(content = {
            Column {
                MovementRow(
                    "Aporte",
                    "vie 2 oct",
                    MoneyTextValue(MoneySign.Plus, "", "350.000", "más 350.000 pesos"),
                    milestone = half,
                    annotation = "Ganaste el sello del 50 %"
                )
                MovementRow(
                    "Retiro",
                    "lun 20 oct",
                    MoneyTextValue(MoneySign.Minus, "", "400.000", "menos 400.000 pesos"),
                    Modifier.testTag("retiro"),
                    icon = SelloIcon.ArrowBack
                )
            }
        }) { scheme ->
            rule.onNodeWithContentDescription("50 por ciento, logrado").assertExists()
            rule.onNodeWithContentDescription("más 350.000 pesos").assertExists()
            rule.onNodeWithText("Ganaste el sello del 50 %").assertExists()
            rule.onNodeWithContentDescription("menos 400.000 pesos").assertExists()
            val stamps = rule.onAllNodesWithDescriptionEnding(", logrado")
            assertEquals(scheme, 1, stamps)
        }
    }

    private fun ComposeContentTestRule.onAllNodesWithDescriptionEnding(suffix: String): Int =
        onAllNodes(
            SemanticsMatcher("description ends with $suffix") { node ->
                node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                    .any { it.endsWith(suffix) }
            }
        ).fetchSemanticsNodes().size

    @Test
    fun theTicketSaysItsStampsAndLineAndTwoStampsSitSmallerSideBySide() {
        rule.inEveryScheme(content = {
            Column {
                StampTicket(listOf(half), "Ganaste el sello del 50 %", "Viaje · faltan 1.290.000")
                StampTicket(
                    listOf(quarter, half),
                    "Ganaste los sellos del 25 % y del 50 %",
                    "Curso de inglés · faltan 900.000"
                )
            }
        }) { scheme ->
            rule.onNodeWithText("Ganaste el sello del 50 %").assertExists()
            rule.onNodeWithText("Ganaste los sellos del 25 % y del 50 %").assertExists()
            rule.onNodeWithText("Curso de inglés · faltan 900.000").assertExists()
            val single = rule.onAllNodes(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("50 por ciento, logrado")
                )
            ).fetchSemanticsNodes().map { it.boundsInRoot.width }
            val pairWidth = rule.onNodeWithContentDescription("25 por ciento, logrado")
                .getUnclippedBoundsInRoot().width
            assertEquals(scheme, 2, single.size)
            assertTrue("$scheme $single $pairWidth", single.max() > single.min())
            assertTrue(scheme, pairWidth < 54.dp)
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

    @Test
    fun theIconStripSaysEachIconMarksThePickedOneAndScrollsToTheLast() {
        val choices = listOf(
            IconChoice("flight", SelloIcon.Flight, "Viaje"),
            IconChoice("home", SelloIcon.Home, "Casa"),
            IconChoice("school", SelloIcon.School, "Educación"),
            IconChoice("pets", SelloIcon.Pets, "Mascotas"),
            IconChoice("phone", SelloIcon.PhoneIphone, "Celular"),
            IconChoice("bank", SelloIcon.AccountBalance, "Inversión"),
            IconChoice("flag", SelloIcon.Flag, "Otra")
        )
        var picked = ""
        rule.inEveryScheme(width = 200.dp, reset = { picked = "" }, content = {
            IconChoiceStrip(choices, "home", { picked = it })
        }) { scheme ->
            rule.onNodeWithContentDescription("Casa", useUnmergedTree = true)
                .assertExists()
            rule.onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
            ).assertExists()
            val last = rule.onNodeWithContentDescription("Otra").performScrollTo()
            last.performClick()

            assertEquals(scheme, "flag", picked)
            assertTarget(last, scheme)
        }
    }
}
