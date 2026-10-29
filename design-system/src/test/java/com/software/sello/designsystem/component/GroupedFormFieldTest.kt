package com.software.sello.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * A text field that draws its digits in groups. What the caller is given is always
 * exactly what was typed; only what is drawn, and read aloud, has the separators.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GroupedFormFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")
    private val field get() = rule.onNodeWithContentDescription("Límite en pesos")

    private fun show(initial: String = "") {
        value = initial
        rule.setContent {
            SelloTheme {
                FormField(
                    label = "Límite en pesos",
                    value = value,
                    onValueChange = { value = it },
                    visualTransformation = DigitGrouping()
                )
            }
        }
    }

    /** What is on screen, which is also what a screen reader reads as the field's text. */
    private fun drawn(): String? = field.fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text

    @Test
    fun digitsTypedAreGivenAsTypedAndDrawnInGroups() {
        show()

        field.performTextInput("400000")

        assertEquals("400000", value)
        assertEquals("400.000", drawn())
    }

    @Test
    fun aDigitTypedInTheMiddleGoesWhereTheCursorIsBetweenDigits() {
        show("400000")

        // After the first digit: "4|00000", drawn "4|00.000".
        field.performTextInputSelection(TextRange(1), relativeToOriginalText = true)
        field.performTextInput("9")

        assertEquals("4900000", value)
        assertEquals("4.900.000", drawn())
    }

    @Test
    fun deletingBesideASeparatorRemovesTheDigitBeforeItNeverNothing() {
        show("400000")

        // Drawn "400.000": the cursor placed just after the dot is after the third digit.
        field.performTextInputSelection(TextRange(4), relativeToOriginalText = false)
        field.performKeyInput { pressKey(Key.Backspace) }

        assertEquals("40000", value)
        assertEquals("40.000", drawn())
    }

    @Test
    fun deletingEveryDigitOneByOneLeavesNothingAndNeverASeparatorAlone() {
        show("1234567")
        field.performTextInputSelection(TextRange(7), relativeToOriginalText = true)
        val seen = mutableListOf<String?>()

        repeat(7) {
            field.performKeyInput { pressKey(Key.Backspace) }
            seen += drawn()
        }

        assertEquals(listOf("123.456", "12.345", "1.234", "123", "12", "1", ""), seen)
        assertEquals("", value)
    }

    @Test
    fun textThatIsNotAnAmountIsGivenAndDrawnExactlyAsItArrived() {
        show("400000")

        for (pasted in listOf("1e3", "1.23", "12,5", "-500000", "0500000", " 500000")) {
            field.performTextReplacement(pasted)

            assertEquals(pasted, value)
            assertEquals(pasted, drawn())
        }
    }

    @Test
    fun dotsTheWriterTypedThemselvesAreKeptAsTyped() {
        show()

        field.performTextInput("400.000")

        assertEquals("400.000", value)
        assertEquals("400.000", drawn())
    }
}
