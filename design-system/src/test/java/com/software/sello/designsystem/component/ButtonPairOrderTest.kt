package com.software.sello.designsystem.component

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode

/**
 * A [ButtonPair] is read and walked in the order it is seen: side by side Cancelar then the
 * action, one above the other the action (on top) then Cancelar. TalkBack and the keyboard
 * must not follow the order the two were written in.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ButtonPairOrderTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var view: View
    private lateinit var focus: FocusManager

    /**
     * [cancel] and [action] in a pair [width] dp wide, as with a keyboard: "No" and "Sí" fit
     * side by side in 300 dp; Cancelar and Desarchivarla stack in 160 dp.
     */
    private fun showPair(cancel: String, action: String, width: Int) {
        rule.setContent {
            view = LocalView.current
            focus = LocalFocusManager.current
            val input = LocalInputModeManager.current
            // Buttons take focus from Tab only outside touch mode.
            SideEffect { input.requestInputMode(InputMode.Keyboard) }
            SelloTheme {
                ButtonPair(
                    firstWeight = 1f,
                    secondWeight = 2f,
                    modifier = Modifier.width(width.dp),
                    first = { Button(cancel) },
                    second = { Button(action) }
                )
            }
        }
    }

    @Composable
    private fun Button(text: String) = SelloButton(text, {}, kind = ButtonKind.Outlined)

    private fun tabTo(text: String) {
        rule.runOnIdle { focus.moveFocus(FocusDirection.Next) }
        rule.onNodeWithText(text).assertIsFocused()
    }

    @Test
    fun stackedTheKeyboardReachesTheActionFirst() {
        showPair(cancel = "Cancelar", action = "Desarchivarla", width = 160)

        tabTo("Desarchivarla")
        tabTo("Cancelar")
    }

    @Test
    fun sideBySideTheKeyboardReachesTheDismissFirst() {
        showPair(cancel = "No", action = "Sí", width = 300)

        tabTo("No")
        tabTo("Sí")
    }

    /** The node TalkBack reads after the one with [text], by Compose's traversal order. */
    private fun readAfter(text: String): Int {
        val id = rule.onNodeWithText(text).fetchSemanticsNode().id
        val provider = view.accessibilityNodeProvider!!
        val info = provider.createAccessibilityNodeInfo(id)!!
        provider.addExtraDataToAccessibilityNodeInfo(id, info, TRAVERSAL_BEFORE, null)
        return info.extras.getInt(TRAVERSAL_BEFORE, -1)
    }

    private fun idOf(text: String) = rule.onNodeWithText(text).fetchSemanticsNode().id

    private fun withTalkBack(show: () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(AccessibilityManager::class.java)
        shadowOf(manager).apply {
            setEnabled(true)
            setTouchExplorationEnabled(true)
            setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
        }
        show()
        rule.waitForIdle()
    }

    @Test
    fun stackedTalkBackReadsTheActionThenCancelar() {
        withTalkBack { showPair(cancel = "Cancelar", action = "Desarchivarla", width = 160) }

        assertEquals(idOf("Cancelar"), readAfter("Desarchivarla"))
        assertEquals(-1, readAfter("Cancelar"))
    }

    @Test
    fun sideBySideTalkBackReadsTheDismissThenTheAction() {
        withTalkBack { showPair(cancel = "No", action = "Sí", width = 300) }

        assertEquals(idOf("Sí"), readAfter("No"))
    }

    private companion object {
        /** Compose's test-only extra for the node read next (traversalBefore). */
        const val TRAVERSAL_BEFORE =
            "android.view.accessibility.extra.EXTRA_DATA_TEST_TRAVERSALBEFORE_VAL"
    }
}
