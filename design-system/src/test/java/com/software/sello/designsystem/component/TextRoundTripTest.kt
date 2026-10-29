package com.software.sello.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The state behind a field answers a change later than the next keystroke, as a ViewModel
 * does on a slow device. What was typed, and where the cursor is, must not wait for it, and
 * each change is reported once.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextRoundTripTest {
    @get:Rule
    val rule = createComposeRule()

    /** The caller's state. Changes reported to it only land when [land] is called. */
    private var value by mutableStateOf("")
    private val reported = mutableListOf<String>()

    private fun report(text: String) {
        reported += text
    }

    private fun land(index: Int) {
        value = reported[index]
        rule.waitForIdle()
    }

    private fun formField(): SemanticsNodeInteraction {
        rule.setContent {
            SelloTheme { FormField("Nombre", value, ::report) }
        }
        return rule.onNodeWithContentDescription("Nombre")
    }

    private fun noteComposer(): SemanticsNodeInteraction {
        rule.setContent {
            SelloTheme { Note() }
        }
        return rule.onNode(hasSetTextAction())
    }

    @Composable
    private fun Note() {
        NoteComposer(
            AnnotatedString("Gasto en Comida"), value, ::report, "${value.length}/60",
            "Opcional", null, "Listo", "Cancelar", {}, {}
        )
    }

    private fun SemanticsNodeInteraction.shown() =
        fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    private fun SemanticsNodeInteraction.cursor() =
        fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun SemanticsNodeInteraction.typeInTheMiddle() {
        performTextInputSelection(TextRange(3))
        performTextInput("X")
    }

    // ---- FormField ----

    @Test
    fun aFieldKeepsWhatWasTypedInTheMiddleWhileTheStateCatchesUp() {
        value = "abcdef"
        val field = formField()

        field.typeInTheMiddle()

        assertEquals(listOf("abcXdef"), reported)
        assertEquals("abcXdef", field.shown())
        assertEquals(TextRange(4), field.cursor())
    }

    @Test
    fun aFieldDoesNotDropAKeystrokeThatArrivesBeforeTheState() {
        value = "abcdef"
        val field = formField()

        field.typeInTheMiddle()
        field.performTextInput("Y")

        assertEquals(listOf("abcXdef", "abcXYdef"), reported)
        assertEquals("abcXYdef", field.shown())
        assertEquals(TextRange(5), field.cursor())
    }

    @Test
    fun aFieldKeepsItsCursorWhenTheStateCatchesUpInSteps() {
        value = "abcdef"
        val field = formField()
        field.typeInTheMiddle()
        field.performTextInput("Y")

        land(0)
        assertEquals("abcXYdef", field.shown())
        land(1)

        assertEquals("abcXYdef", field.shown())
        assertEquals(TextRange(5), field.cursor())
        assertEquals(listOf("abcXdef", "abcXYdef"), reported)
    }

    @Test
    fun movingTheCursorWhileTheStateIsBehindReportsNothing() {
        value = "abcdef"
        val field = formField()
        field.typeInTheMiddle()

        field.performTextInputSelection(TextRange(1))

        assertEquals(listOf("abcXdef"), reported)
        assertEquals(TextRange(1), field.cursor())
    }

    @Test
    fun aFieldTakesAValueThatChangesOutsideItAndPutsTheCursorAfterIt() {
        value = "abcdef"
        val field = formField()
        field.typeInTheMiddle()
        land(0)

        value = "Mercado"
        rule.waitForIdle()

        assertEquals("Mercado", field.shown())
        assertEquals(TextRange(7), field.cursor())
    }

    // ---- NoteComposer ----

    @Test
    fun aNoteKeepsWhatWasTypedInTheMiddleWhileTheStateCatchesUp() {
        value = "abcdef"
        val field = noteComposer()

        field.typeInTheMiddle()

        assertEquals(listOf("abcXdef"), reported)
        assertEquals("abcXdef", field.shown())
        assertEquals(TextRange(4), field.cursor())
    }

    @Test
    fun aNoteDoesNotDropAKeystrokeThatArrivesBeforeTheState() {
        value = "abcdef"
        val field = noteComposer()

        field.typeInTheMiddle()
        field.performTextInput("Y")

        assertEquals(listOf("abcXdef", "abcXYdef"), reported)
        assertEquals("abcXYdef", field.shown())
        assertEquals(TextRange(5), field.cursor())
    }

    @Test
    fun aNoteKeepsItsCursorWhenTheStateCatchesUpInSteps() {
        value = "abcdef"
        val field = noteComposer()
        field.typeInTheMiddle()
        field.performTextInput("Y")

        land(0)
        assertEquals("abcXYdef", field.shown())
        land(1)

        assertEquals("abcXYdef", field.shown())
        assertEquals(TextRange(5), field.cursor())
        assertEquals(listOf("abcXdef", "abcXYdef"), reported)
    }

    @Test
    fun movingTheCursorInANoteWhileTheStateIsBehindReportsNothing() {
        value = "abcdef"
        val field = noteComposer()
        field.typeInTheMiddle()

        field.performTextInputSelection(TextRange(1))

        assertEquals(listOf("abcXdef"), reported)
        assertEquals(TextRange(1), field.cursor())
    }

    @Test
    fun aNoteTakesAValueThatChangesOutsideItAndPutsTheCursorAfterIt() {
        value = "abcdef"
        val field = noteComposer()
        field.typeInTheMiddle()
        land(0)

        value = "Mercado"
        rule.waitForIdle()

        assertEquals("Mercado", field.shown())
        assertEquals(TextRange(7), field.cursor())
    }
}
