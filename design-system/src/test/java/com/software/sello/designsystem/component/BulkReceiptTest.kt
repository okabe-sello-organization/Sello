package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The bulk receipt highlights, removes and reads its lines as the approved design says. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class BulkReceiptTest {
    @get:Rule
    val rule = createComposeRule()

    private val done = mutableListOf<String>()
    private var highlighted by mutableIntStateOf(2)

    private fun receipt(
        lines: List<BulkLine> = threeExpenses,
        enabled: Boolean = true,
        addEnabled: Boolean = true,
        reducedMotion: Boolean = true,
        highlightedFoot: (@Composable () -> Unit)? = null
    ) {
        rule.setContent {
            SelloTheme(reducedMotion = reducedMotion) {
                BulkReceipt(
                    lines = lines,
                    highlighted = highlighted,
                    caption = BulkCaption.Count("Anotar varios", "${lines.size} renglones"),
                    total = expenseTotal,
                    onSelect = {
                        done += "select $it"
                        highlighted = it
                    },
                    onRemove = { done += "remove $it" },
                    onAdd = { done += "add" },
                    modifier = Modifier.height(400.dp),
                    addEnabled = addEnabled,
                    enabled = enabled,
                    highlightedFoot = highlightedFoot
                )
            }
        }
    }

    private fun line(index: Int) = rule.onAllNodesWithTag(BULK_LINE_TAG)[index]

    @Test
    fun tappingALineHighlightsIt() {
        receipt()

        line(0).performClick()

        assertEquals(listOf("select 0"), done)
        assertEquals(true, line(0).fetchSemanticsNode().config[SemanticsProperties.Selected])
        assertEquals(false, line(2).fetchSemanticsNode().config[SemanticsProperties.Selected])
    }

    @Test
    fun swipingALineLeftPastFortyPercentRemovesIt() {
        receipt()

        line(1).performTouchInput { swipeLeft(startX = right - 4f, endX = right - width * 0.5f) }
        rule.waitForIdle()

        assertEquals(listOf("remove 1"), done)
    }

    @Test
    fun aSwipeShortOfFortyPercentRemovesNothing() {
        receipt()

        line(1).performTouchInput { swipeLeft(startX = right - 4f, endX = right - width * 0.3f) }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun theLongPressMenuRemovesTheLineOnce() {
        receipt()

        line(1).performTouchInput { longClick() }
        rule.onNodeWithText("Quitar renglón").assertIsDisplayed().performClick()

        assertEquals(listOf("remove 1"), done)
        rule.onAllNodes(hasTestTag(BULK_LINE_MENU_TAG)).assertCountEquals(0)
    }

    @Test
    fun theLastLineCannotBeRemovedInAnyWay() {
        highlighted = 0
        receipt(lines = threeExpenses.take(1))

        line(0).performTouchInput { swipeLeft(startX = right - 4f, endX = left + 4f) }
        line(0).performTouchInput { longClick() }
        rule.waitForIdle()

        rule.onAllNodes(hasTestTag(BULK_LINE_MENU_TAG)).assertCountEquals(0)
        assertFalse("remove 0" in done)
        val actions = line(0).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertTrue(actions.isNullOrEmpty())
    }

    @Test
    fun aScreenReaderReadsEachLineAsOneItemAndOffersRemovingIt() {
        receipt(lines = listOf(threeExpenses[0], missingCategory, threeExpenses[2]))

        val first = line(0).fetchSemanticsNode().config
        assertEquals(
            listOf("Renglón 1 de 3. Transporte, Uber, 18.400 pesos"),
            first[SemanticsProperties.ContentDescription]
        )
        val missing = line(1).fetchSemanticsNode()
        assertEquals(
            listOf("Renglón 2 de 3. Farmacia Pasteur, 75.100 pesos, Elige una categoría"),
            missing.config[SemanticsProperties.ContentDescription]
        )
        // One item: nothing inside the line is read on its own.
        assertTrue(missing.children.isEmpty())
        val remove = missing.config[SemanticsActions.CustomActions].single()
        assertEquals("Quitar renglón", remove.label)
        rule.runOnUiThread { remove.action() }

        assertEquals(listOf("remove 1"), done)
    }

    @Test
    fun aLineWithoutAnAmountIsReadAsHavingNone() {
        highlighted = 0
        receipt(lines = listOf(threeExpenses[0].copy(amount = null, detail = null)))

        assertEquals(
            listOf("Renglón 1 de 1. Transporte, Sin monto"),
            line(0).fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
        )
    }

    @Test
    fun pastFiveLinesTheLinesScrollAndTheTotalStays() {
        val many = (1..12).map { categoryLine("$it", "Comida", "4.500") }
        highlighted = 0
        receipt(lines = many)

        rule.onNodeWithText("Total").assertIsDisplayed()
        val list = rule.onNodeWithTag(BULK_RECEIPT_LINES_TAG)
        assertTrue(list.fetchSemanticsNode().config.contains(SemanticsActions.ScrollToIndex))
        list.performScrollToIndex(11)

        rule.onNodeWithText("Total").assertIsDisplayed()
        rule.onNodeWithText("Otro renglón").assertIsDisplayed()
    }

    @Test
    fun aLineHighlightedOutOfSightIsScrolledIntoView() {
        val many = (1..12).map { categoryLine("$it", "Línea $it", "4.500") }
        highlighted = 0
        receipt(lines = many)

        rule.runOnUiThread { highlighted = 11 }
        rule.waitForIdle()

        // Lines out of sight are not composed, so the line is found by what it reads.
        rule.onNode(hasContentDescription("Línea 12", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theReceiptNeverTakesMoreThanThirtyLines() {
        val thirtyOne = (1..31).map { categoryLine("$it", "Comida", "4.500") }

        assertThrows(IllegalArgumentException::class.java) {
            highlighted = 0
            receipt(lines = thirtyOne)
        }
    }

    @Test
    fun otroRenglonAddsALine() {
        receipt()
        rule.onNodeWithText("Otro renglón").performClick()
        assertEquals(listOf("add"), done)
    }

    @Test
    fun whileLockedNothingResponds() {
        receipt(enabled = false)

        line(0).performClick()
        line(1).performTouchInput { swipeLeft(startX = right - 4f, endX = left + 4f) }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), done)
        rule.onNodeWithText("Otro renglón").assertIsNotEnabled()
    }

    @Test
    fun whileLockedEachLineReadsAsDisabledAndOffersNoRemoval() {
        receipt(enabled = false)

        line(1).assertIsNotEnabled()
        val actions = line(1).fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
        assertTrue(actions.isNullOrEmpty())
    }

    @Test
    fun whileLockedNoLineIsHighlightedNorCarriesItsFoot() {
        receipt(enabled = false, highlightedFoot = { FieldPill("vie 16 oct", SelloIcon.Event, {}) })

        (0..2).forEach { index ->
            val config = line(index).fetchSemanticsNode().config
            assertEquals(false, config[SemanticsProperties.Selected])
        }
        rule.onAllNodes(hasText("vie 16 oct")).assertCountEquals(0)
    }

    @Test
    fun theHighlightedLinesDatePillIsReachedAndTappedOnItsOwn() {
        var pillTaps = 0
        receipt(highlightedFoot = { FieldPill("vie 16 oct", SelloIcon.Event, { pillTaps++ }) })

        val pill = line(2).fetchSemanticsNode().children.single()
        assertEquals(listOf("vie 16 oct"), pill.config[SemanticsProperties.Text].map { it.text })
        rule.onNode(hasText("vie 16 oct") and hasClickAction()).performClick()

        assertEquals(1, pillTaps)
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun aSwipePastFortyPercentRemovesTheLineBeforeItSpringsBack() {
        receipt(reducedMotion = false)
        rule.mainClock.autoAdvance = false

        line(1).performTouchInput { swipeLeft(startX = right - 4f, endX = right - width * 0.5f) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        assertEquals(listOf("remove 1"), done)
    }

    @Test
    fun otroRenglonWaitsWhenTheCallerSaysSo() {
        receipt(addEnabled = false)

        rule.onNodeWithText("Otro renglón").assertIsNotEnabled()
    }
}
