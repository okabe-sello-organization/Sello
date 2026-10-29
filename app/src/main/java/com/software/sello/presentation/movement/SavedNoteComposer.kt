package com.software.sello.presentation.movement

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TextError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Why the text being written for a movement's note cannot be kept. */
enum class MovementNoteProblem { TooLong, NotAllowed }

/**
 * The note composer of a goal or investment movement form, kept in [saved] under
 * `[prefix].composer` so that it and the text in it outlive process death. [text] is
 * what is being written while it is open, and null while it is closed.
 */
class SavedNoteComposer(
    private val saved: SavedStateHandle,
    prefix: String,
    scope: CoroutineScope
) {
    private val openKey = "$prefix.composer"
    private val textKey = "$prefix.composer.text"

    val text: StateFlow<String?> = combine(
        saved.getStateFlow(openKey, false),
        saved.getStateFlow(textKey, "")
    ) { open, text -> text.takeIf { open } }
        .stateIn(scope, SharingStarted.Eagerly, now())

    /** [text] as it is stored right now, before the flow has caught up. */
    fun now(): String? = if (saved.get<Boolean>(openKey) == true) {
        saved.get<String>(textKey).orEmpty()
    } else {
        null
    }

    /** Opens on the note as it is. */
    fun open(note: String) {
        saved[textKey] = note
        saved[openKey] = true
    }

    /** Keeps what is typed, only while the composer is open. */
    fun write(text: String) {
        if (saved.get<Boolean>(openKey) == true) saved[textKey] = text
    }

    fun close() {
        saved[openKey] = false
        saved[textKey] = null
    }

    companion object {
        /** Why [text] cannot be kept as a note; a blank one simply means none. */
        fun problem(text: String): MovementNoteProblem? =
            when ((Note.of(text) as? Outcome.Failure)?.error) {
                null, TextError.Required -> null
                is TextError.TooLong -> MovementNoteProblem.TooLong
                TextError.ControlCharacter -> MovementNoteProblem.NotAllowed
            }

        /** How long [text] counts as a note, in code points; 0 when nothing is written. */
        fun length(text: String?): Int = text?.trim()?.let { it.codePointCount(0, it.length) } ?: 0
    }
}
