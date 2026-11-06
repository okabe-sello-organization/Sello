package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The compact pill a row holds as its own action. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelloButtonTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun aCompactButtonTakesA48dpTouchArea() {
        rule.setContent {
            SelloTheme {
                SelloButton(
                    text = "Completar",
                    onClick = {},
                    compact = true,
                    modifier = Modifier.testTag("pill")
                )
            }
        }
        val pill = rule.onNodeWithTag("pill").fetchSemanticsNode()
        val touch = with(pill.layoutInfo.density) { pill.touchBoundsInRoot.height.toDp() }
        assertEquals(48f, touch.value, 0.5f)
    }

    @Test
    fun aBusyCompactButtonWithNoLabelHasItsSpinnerInTheMiddle() {
        rule.setContent {
            SelloTheme {
                SelloButton(
                    text = "",
                    onClick = {},
                    loading = true,
                    contentPadding = PaddingValues(0.dp),
                    compact = true,
                    modifier = Modifier.width(96.dp).testTag("pill")
                )
            }
        }
        val pill = rule.onNodeWithTag("pill").getUnclippedBoundsInRoot()
        val spinner = rule.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true
        ).getUnclippedBoundsInRoot()
        val pillMiddle = (pill.left + pill.right).value / 2
        val spinnerMiddle = (spinner.left + spinner.right).value / 2
        assertEquals(pillMiddle, spinnerMiddle, 0.5f)
    }
}
