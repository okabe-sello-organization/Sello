package com.software.sello.feature.category

import android.graphics.Insets
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With the keyboard up for the name, the form keeps the height it had, as Anotar does:
 * the name stays where it was, in view above the keyboard, and nothing is squeezed.
 */
@RunWith(AndroidJUnit4::class)
class CategoryEditorKeyboardTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A keyboard [heightPx] tall, reported as the system reports it: as window insets. */
    private fun keyboard(heightPx: Int) {
        rule.runOnUiThread {
            val insets = WindowInsets.Builder()
                .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, heightPx))
                .setVisible(WindowInsets.Type.ime(), heightPx > 0)
                .build()
            // Straight to the Compose view: the window's own frame would consume them.
            val content = rule.activity.findViewById<ViewGroup>(android.R.id.content)
            content.getChildAt(0).dispatchApplyWindowInsets(insets)
        }
        rule.waitForIdle()
    }

    @Test
    fun theNameStaysInViewAboveTheKeyboardWhereItWas() {
        rule.setContent {
            // One pixel per dp on a phone-sized stage, whatever the test device is.
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                SelloTheme(reducedMotion = true) {
                    Box(Modifier.requiredSize(360.dp, 640.dp).testTag(STAGE)) {
                        CategoryEditorScreen(
                            CategoryEditorState(
                                loading = false,
                                iconKeys = CategoryIcons.keys,
                                iconKey = "home"
                            ),
                            {}
                        )
                    }
                }
            }
        }
        val before = rule.onNodeWithTag(EDITOR_NAME_TAG).getUnclippedBoundsInRoot()

        keyboard(KEYBOARD)

        val stage = rule.onNodeWithTag(STAGE).getUnclippedBoundsInRoot()
        val name = rule.onNodeWithTag(EDITOR_NAME_TAG).getUnclippedBoundsInRoot()
        val keyboardTop = stage.bottom.value - KEYBOARD
        assertTrue("The name ends at ${name.bottom}", name.bottom.value <= keyboardTop)
        assertTrue("The name moved from $before to $name", name.top == before.top)
    }

    private companion object {
        const val STAGE = "stage"
        const val KEYBOARD = 250
    }
}
