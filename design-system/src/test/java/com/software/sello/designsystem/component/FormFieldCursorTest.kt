package com.software.sello.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Text already in a field, such as a name being edited, is typed after, not before. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FormFieldCursorTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")
    private val field get() = rule.onNodeWithContentDescription("Nombre")

    private fun selection() =
        field.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun show(initial: String) {
        value = initial
        rule.setContent {
            SelloTheme {
                FormField(label = "Nombre", value = value, onValueChange = { value = it })
            }
        }
    }

    @Test
    fun aFieldOpenedWithTextHasTheCursorAfterIt() {
        show("Alimentación")

        assertEquals(TextRange(12), selection())
    }

    @Test
    fun textGivenLaterAlsoPutsTheCursorAfterIt() {
        show("")

        value = "Mercado"
        rule.waitForIdle()

        assertEquals(TextRange(7), selection())
    }

    @Test
    fun typingStillAddsWhereTheCursorIs() {
        show("Café")

        field.performTextInput(" y pan")

        assertEquals("Café y pan", value)
        assertEquals(TextRange(10), selection())
    }
}
