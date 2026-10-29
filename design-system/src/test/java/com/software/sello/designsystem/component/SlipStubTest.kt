package com.software.sello.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
import org.robolectric.annotation.GraphicsMode

/** The tear-off stub under Resumen's income slip (SELLO-123). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SlipStubTest {
    @get:Rule
    val rule = createComposeRule()

    private var opened = ""

    @Composable
    private fun Stub() {
        SlipStub(
            start = {
                StubButton(
                    SelloIcon.ReceiptLong,
                    "Ingresos",
                    { opened = "ingresos" },
                    Modifier.testTag("incomes"),
                    fact = "3 en octubre"
                )
            },
            end = {
                StubButton(
                    SelloIcon.Autorenew,
                    "Fijos",
                    { opened = "fijos" },
                    Modifier.testTag("fixed"),
                    label = "Pagos e ingresos fijos"
                )
            }
        )
    }

    @Test
    fun eachHalfIsOneButtonSaidAsItsLabelThenItsFact() {
        rule.inEveryScheme(content = { Stub() }) {
            val incomes = rule.onNodeWithTag("incomes").fetchSemanticsNode().config
            assertEquals(
                listOf("Ingresos, 3 en octubre"),
                incomes.getOrNull(SemanticsProperties.ContentDescription)
            )
            assertEquals(Role.Button, incomes.getOrNull(SemanticsProperties.Role))
            // Without a fact, only the label: never an empty or invented one.
            assertEquals(
                listOf("Pagos e ingresos fijos"),
                rule.onNodeWithTag("fixed").fetchSemanticsNode().config
                    .getOrNull(SemanticsProperties.ContentDescription)
            )
            rule.onNodeWithTag("incomes").assertHasClickAction()
        }
        rule.onNodeWithTag("fixed").performClick()
        assertEquals("fijos", opened)
        rule.onNodeWithTag("incomes").performClick()
        assertEquals("ingresos", opened)
    }

    @Test
    fun bothHalvesShareTheWidthAndStayTallEnoughToTouchAtLargeText() {
        rule.inEveryScheme(fontScale = 2f, content = { Stub() }) {
            val incomes = rule.onNodeWithTag("incomes").getUnclippedBoundsInRoot()
            val fixed = rule.onNodeWithTag("fixed").getUnclippedBoundsInRoot()
            assertEquals(incomes.width.value, fixed.width.value, 1f)
            assertTrue(incomes.right <= fixed.left)
            assertTrue(incomes.height >= 48.dp && fixed.height >= 48.dp)
        }
    }

    @Test
    fun eachHalfCentresItsIconAndWordsInItsSpace() {
        rule.inEveryScheme(content = { Stub() }) {
            listOf("incomes", "fixed").forEach { tag ->
                val half = rule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
                val content = rule.onNode(
                    hasTestTag(STUB_CONTENT_TAG) and hasAnyAncestor(hasTestTag(tag)),
                    useUnmergedTree = true
                ).getUnclippedBoundsInRoot()
                val left = content.left - half.left
                val right = half.right - content.right
                assertTrue("$tag: $left on the left, $right on the right", left > 8.dp)
                assertEquals(left.value, right.value, 1f)
            }
        }
    }
}
