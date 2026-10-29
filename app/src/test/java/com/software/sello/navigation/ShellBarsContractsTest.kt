package com.software.sello.navigation

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.recibo.text
import com.software.sello.inEveryScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The shell's month bar, title bar and past-month ribbon keep their contracts. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShellBarsContractsTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun pastMonthReturnRemainsReachableAtDoubleFont() {
        var returns = 0
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            PastMonthRibbon(AnnotatedString("Estás viendo septiembre"), "Volver a noviembre", {
                returns++
            })
        }) {
            val button = rule.onNodeWithText("Volver a noviembre")
            button.assertIsDisplayed().performClick()
            val node = button.fetchSemanticsNode()
            assertTrue(
                with(node.layoutInfo.density) {
                    node.touchBoundsInRoot.height.toDp()
                } >= 47.9.dp
            )
        }
        assertEquals(4, returns)
    }

    @Test
    fun atFontScale13TheRibbonPutsItsReturnUnderAMessageThatKeepsTheWidth() = ribbonStacksAt(1.3f)

    @Test
    fun atTwiceTheFontSizeTheRibbonPutsItsReturnUnderAMessageThatKeepsTheWidth() =
        ribbonStacksAt(2f)

    private fun ribbonStacksAt(scale: Float) {
        rule.inEveryScheme(fontScale = scale, width = 320.dp, content = {
            PastMonthRibbon(
                AnnotatedString("Estás viendo septiembre de 2025"),
                "Volver a noviembre",
                {},
                Modifier.testTag("ribbon")
            )
        }) { scheme ->
            val ribbon = rule.onNodeWithTag("ribbon").getUnclippedBoundsInRoot()
            val message = rule.onNodeWithText("Estás viendo septiembre de 2025")
                .getUnclippedBoundsInRoot()
            val button = rule.onNodeWithText("Volver a noviembre").getUnclippedBoundsInRoot()
            // Squeezed beside the button, the message became a column of letters.
            assertTrue("$scheme: $message in $ribbon", message.width > ribbon.width / 2)
            assertTrue("$scheme: $button under $message", button.top >= message.bottom)
            assertTrue("$scheme: $button in $ribbon", button.right <= ribbon.right)
        }
    }

    @Test
    fun ribbonKeepsItsReturnButtonOnTheMessagesLine() {
        rule.inEveryScheme(content = {
            PastMonthRibbon(AnnotatedString("Estás viendo septiembre"), "Volver a octubre", {})
        }) {
            val message = rule.onNodeWithText("Estás viendo septiembre").getUnclippedBoundsInRoot()
            val button = rule.onNodeWithText("Volver a octubre").getUnclippedBoundsInRoot()
            val middle = (button.top + button.bottom) / 2
            assertTrue(middle > message.top && middle < message.bottom)
            assertTrue(button.left > message.right)
        }
    }

    @Test
    fun monthBarButtonHugsTheMonthAndLeavesRoomForActions() {
        rule.inEveryScheme(content = {
            MonthBar("Octubre", "2026", {}, Modifier.testTag("bar"))
        }) {
            val bar = rule.onNodeWithTag("bar").getUnclippedBoundsInRoot()
            val button = rule.onNodeWithContentDescription(
                "Octubre 2026"
            ).getUnclippedBoundsInRoot()
            // Two 48dp actions still fit beside the month button on the narrowest bar.
            assertTrue(button.right - button.left <= (bar.right - bar.left) - 96.dp)
        }
    }

    @Test
    fun titleBarNamesTheTabAsAHeadingAndKeepsActionsReachable() {
        var added = 0
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            TitleBar("Inversiones") {
                IconButton({ added++ }) { Icon(SelloIcon.Add.painter(), "Agregar inversión") }
            }
        }) {
            rule.onNode(isHeading()).assertTextContains("Inversiones")
            val add = rule.onNodeWithContentDescription("Agregar inversión")
            add.assertIsDisplayed().performClick()
            val node = add.fetchSemanticsNode()
            assertTrue(
                with(node.layoutInfo.density) {
                    node.touchBoundsInRoot.height.toDp()
                } >= 47.9.dp
            )
        }
        assertEquals(4, added)
    }

    @Test
    fun aTitleBarChipKeepsItsLabelWhenTheTitleFitsBesideIt() {
        var added = 0
        // 320dp, the narrowest phone width, so the stage fits on any test device.
        rule.inEveryScheme(width = 320.dp, content = {
            TitleBar("Metas", chip = { compact ->
                BandChip("Nueva meta", {
                    added++
                }, Modifier.testTag("chip"), compact = compact)
            })
        }) {
            rule.onNodeWithText("Nueva meta", useUnmergedTree = true).assertIsDisplayed()
            rule.onNode(isHeading()).assertTextContains("Metas")
            val chip = rule.onNodeWithTag("chip")
            chip.assertIsDisplayed().performClick()
            // The pill is 36dp; its touch target is the full 48dp.
            val node = chip.fetchSemanticsNode()
            assertTrue(
                with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() } >= 47.9.dp
            )
        }
        assertEquals(4, added)
    }

    @Test
    fun aTitleBarChipIsTheRoundPlusWhenItsLabelWouldPushTheTitleOffItsLine() {
        var added = 0
        val chip: @Composable (Boolean) -> Unit = { compact ->
            BandChip("Nueva inversión", { added++ }, Modifier.testTag("chip"), compact = compact)
        }
        // At double text on 320dp they do not fit side by side: the chip is the round +
        // and still says its label, the title keeps its line, and the target is 48dp.
        rule.inEveryScheme(fontScale = 2f, width = 320.dp, content = {
            TitleBar("Inversiones", chip = chip)
        }) {
            rule.onNodeWithText("Nueva inversión", useUnmergedTree = true).assertDoesNotExist()
            val plus = rule.onNodeWithTag("chip").assertContentDescriptionEquals("Nueva inversión")
            plus.assertIsDisplayed().performClick()
            val node = plus.fetchSemanticsNode()
            assertTrue(
                with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() } >= 47.9.dp
            )
            val title = rule.onNode(isHeading()).getUnclippedBoundsInRoot()
            val round = plus.getUnclippedBoundsInRoot()
            assertTrue("title $title, chip $round", title.right <= round.left + 0.5.dp)
        }
        assertEquals(4, added)
    }
}
