package com.software.sello.presentation.appearance

import com.software.sello.R
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.InkPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class InkChoicesTest {
    @Test
    fun everyStoredInkAndEveryPaletteIsInThePickerOnce() {
        assertEquals(InkPreference.entries, InkChoices.map { it.preference })
        assertEquals(SelloInk.entries, InkChoices.map { it.ink })
    }

    @Test
    fun eachInkKeepsItsPaletteAndName() {
        assertEquals(SelloInk.Cobalto, InkPreference.Cobalto.choice.ink)
        assertEquals(SelloInk.Violeta, InkPreference.Violeta.choice.ink)
        assertEquals(R.string.settings_ink_cobalto, InkPreference.Cobalto.choice.label)
        assertEquals(R.string.settings_ink_violeta, InkPreference.Violeta.choice.label)
    }
}
