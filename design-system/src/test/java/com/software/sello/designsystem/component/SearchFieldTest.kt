package com.software.sello.designsystem.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A search input: what is typed, its placeholder, its clear button and search key. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchFieldTest {
    @get:Rule
    val rule = createComposeRule()

    private var value by mutableStateOf("")
    private val field get() = rule.onNodeWithTag("search")

    private fun show(initial: String) {
        value = initial
        rule.setContent {
            SelloTheme {
                SearchField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "Buscar en todos tus movimientos",
                    clearLabel = "Borrar la búsqueda",
                    modifier = Modifier.testTag("search")
                )
            }
        }
    }

    @Test
    fun anEmptyFieldShowsItsPlaceholderAndNoClearButton() {
        show("")

        rule.onNodeWithText("Buscar en todos tus movimientos").assertExists()
        rule.onNodeWithContentDescription("Borrar la búsqueda").assertDoesNotExist()
        // Screen readers call the field by its placeholder.
        assertEquals(
            listOf("Buscar en todos tus movimientos"),
            field.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    @Test
    fun whatIsTypedIsPassedOnExactly() {
        show("")

        field.performTextInput("  Rappí ")

        assertEquals("  Rappí ", value)
        field.assertTextEquals("  Rappí ", includeEditableText = true)
        rule.onNodeWithText("Buscar en todos tus movimientos").assertDoesNotExist()
    }

    @Test
    fun theClearButtonEmptiesTheField() {
        show("rappi")

        rule.onNodeWithContentDescription("Borrar la búsqueda").performClick()

        assertEquals("", value)
        rule.onNodeWithText("Buscar en todos tus movimientos").assertExists()
    }

    @Test
    fun theSearchKeyLeavesTheField() {
        show("rappi")
        field.performClick()

        assertEquals(
            ImeAction.Search,
            field.fetchSemanticsNode().config[SemanticsProperties.ImeAction]
        )
        field.performImeAction()

        assertFalse(field.fetchSemanticsNode().config[SemanticsProperties.Focused])
    }
}
