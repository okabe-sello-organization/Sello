package com.software.sello.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * What a text field shows and where its cursor is, while the caller owns the text.
 *
 * The caller's state can answer a change later than the next keystroke, so the field keeps
 * its own value and compares with its own previous text, never with the caller's. A value
 * from the caller replaces the field only when it is new and was not one of the texts the
 * field reported: those are echoes of typing and leave the cursor and the word in progress
 * alone. A new value goes in with the cursor after it.
 */
@Stable
internal class EditedText(initial: String) {
    var field by mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
        private set

    private var seen = initial
    private val reported = ArrayDeque<String>()

    /** Takes the caller's current [value]; call it on every composition. */
    fun sync(value: String) {
        if (value == seen) return
        seen = value
        val echo = reported.indexOf(value)
        if (echo >= 0) {
            repeat(echo + 1) { reported.removeFirst() }
            return
        }
        reported.clear()
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }

    /** Takes what the keyboard produced; returns the new text to report, or null if it is the same. */
    fun edit(edited: TextFieldValue): String? {
        val changed = edited.text != field.text
        field = edited
        if (!changed) return null
        reported.addLast(edited.text)
        return edited.text
    }
}

@Composable
internal fun rememberEditedText(value: String): EditedText =
    remember { EditedText(value) }.also { it.sync(value) }
