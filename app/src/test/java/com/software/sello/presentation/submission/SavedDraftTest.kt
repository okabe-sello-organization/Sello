package com.software.sello.presentation.submission

import androidx.lifecycle.SavedStateHandle
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedDraftTest {
    private enum class Kind { Contribution, Withdrawal }

    @Serializable
    private data class Original(val amount: String?, val note: String)

    @Serializable
    private data class Draft(
        val amount: String?,
        val date: String,
        val note: String,
        val kind: Kind,
        val count: Int,
        val generation: Long?,
        val following: Boolean?,
        val tags: List<String>,
        val original: Original?
    )

    private val typed = Draft(
        amount = "45000",
        date = "2026-10-29",
        note = "Mercado, \"semana\" + 1",
        kind = Kind.Withdrawal,
        count = 3,
        generation = 7L,
        following = null,
        tags = listOf("", "+", "uno"),
        original = Original(amount = null, note = "")
    )

    private fun restarted(saved: SavedStateHandle) =
        SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })

    @Test
    fun `a draft survives process death under one key`() {
        val saved = SavedStateHandle()
        SavedDraft(saved, "form.draft", Draft.serializer()).write(typed)

        val restored = restarted(saved)

        assertEquals(setOf("form.draft"), restored.keys())
        assertEquals(typed, SavedDraft(restored, "form.draft", Draft.serializer()).read())
    }

    @Test
    fun `empty and absent values come back as they were`() {
        val saved = SavedStateHandle()
        val blank = typed.copy(
            amount = null,
            note = "",
            generation = null,
            following = false,
            tags = emptyList(),
            original = null
        )
        SavedDraft(saved, "form.draft", Draft.serializer()).write(blank)

        assertEquals(blank, SavedDraft(restarted(saved), "form.draft", Draft.serializer()).read())
    }

    @Test
    fun `no saved draft, or one that cannot be read, is none`() {
        val draft = SavedDraft(SavedStateHandle(), "form.draft", Draft.serializer())
        assertNull(draft.read())

        val damaged = SavedStateHandle(mapOf("form.draft" to arrayListOf<String?>("+", "1")))
        assertNull(SavedDraft(damaged, "form.draft", Draft.serializer()).read())
    }

    @Test
    fun `clearing forgets the draft`() {
        val saved = SavedStateHandle()
        val draft = SavedDraft(saved, "form.draft", Draft.serializer())
        draft.write(typed)

        draft.clear()

        assertNull(draft.read())
        assertEquals(emptySet<String>(), saved.keys())
    }
}
