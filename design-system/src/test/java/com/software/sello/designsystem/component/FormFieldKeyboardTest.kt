package com.software.sello.designsystem.component

import android.content.Context
import android.os.Looper
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import java.time.Duration
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * A field that is left puts the system keyboard away itself (#292), but only once no other
 * field takes input: moving to another field keeps the keyboard. That the keyboard goes
 * when no field is left is proven on a device, where Compose's own hide can arrive late;
 * here it always arrives in time.
 */
@RunWith(AndroidJUnit4::class)
class FormFieldKeyboardTest {
    @get:Rule
    val rule = createComposeRule()

    private var shown by mutableStateOf(true)

    private val keyboard
        get() = shadowOf(
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(InputMethodManager::class.java)
        )

    private fun show(vararg labels: String) = rule.setContent {
        SelloTheme {
            Column {
                if (shown) FormField(label = labels.first(), value = "", onValueChange = {})
                labels.drop(1).forEach { FormField(label = it, value = "", onValueChange = {}) }
            }
        }
    }

    /** Lets the frames pass that a field waits before it may put the keyboard away. */
    private fun settle() {
        rule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        rule.waitForIdle()
    }

    private fun typeIn(label: String) {
        rule.onNodeWithContentDescription(label).performClick()
        settle()
        assertTrue(keyboard.isSoftInputVisible)
    }

    @Test
    fun movingToAnotherFieldKeepsTheKeyboard() {
        show("Nombre", "Nota")
        typeIn("Nombre")

        rule.onNodeWithContentDescription("Nota").performClick()
        settle()

        assertTrue(keyboard.isSoftInputVisible)
    }

    @Test
    fun aFieldThatGoesWhileAnotherTakesOverKeepsTheKeyboard() {
        show("Nombre", "Nota")
        typeIn("Nombre")

        rule.onNodeWithContentDescription("Nota").performClick()
        shown = false
        settle()

        assertTrue(keyboard.isSoftInputVisible)
    }
}
