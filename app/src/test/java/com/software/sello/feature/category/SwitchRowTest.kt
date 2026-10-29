package com.software.sello.feature.category

import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.inEveryScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The category editor's switch row toggles once per press and keeps a full touch target. */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SwitchRowTest {
    @get:Rule
    val rule = createComposeRule()

    private val isSwitch = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    /** The area that accepts a touch, which may be larger than what is drawn. */
    private fun tall(tag: String, label: String) {
        val node = rule.onNodeWithTag(tag).fetchSemanticsNode()
        val height = with(node.layoutInfo.density) { node.touchBoundsInRoot.height.toDp() }
        assertTrue("$label: $tag accepts touches over $height", height >= minTarget)
    }

    /** 48dp, less the fraction of a pixel that rounding to whole pixels can cost. */
    private val minTarget = 47.9.dp

    @Test
    fun theWholeSwitchRowTogglesOncePerPress() {
        var checked by mutableStateOf(false)
        var changes = 0
        rule.inEveryScheme(
            reset = {
                checked = false
                changes = 0
            },
            content = {
                SwitchRow("Avisarme", checked, {
                    checked = it
                    changes++
                }, Modifier.testTag("row"))
            }
        ) {
            rule.onNodeWithTag("row").assert(isSwitch).assertIsOff().performClick()
            rule.onNodeWithTag("row").assertIsOn()
            assertEquals(it, 1, changes)
            tall("row", it)
        }
    }
}
