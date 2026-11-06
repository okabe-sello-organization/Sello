package com.software.sello.presentation.appearance

import com.software.sello.R
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class InkChoicesTest {
    @Test
    fun everyHandMadeInkAndEveryPaletteIsInThePickerOnce() {
        assertEquals(
            listOf(InkPreference.Cobalto, InkPreference.Violeta),
            InkChoices.map { it.preference }
        )
        assertEquals(SelloInk.entries, InkChoices.map { it.ink })
        assertEquals(
            listOf(R.string.settings_ink_cobalto, R.string.settings_ink_violeta),
            InkChoices.map { it.label }
        )
    }

    @Test
    fun eachInkIsDrawnInItsOwnPaletteAndTuyaInItsHue() {
        assertEquals(SelloInk.Cobalto, InkPreference.Cobalto.themeInk)
        assertEquals(SelloInk.Violeta, InkPreference.Violeta.themeInk)
        assertEquals(HueInk(22), InkPreference.Tuya(InkHue.of(22)!!).themeInk)
        assertEquals(HueInk(0), InkPreference.Tuya(InkHue.of(0)!!).themeInk)
        assertEquals(HueInk(359), InkPreference.Tuya(InkHue.of(359)!!).themeInk)
    }

    @Test
    fun tuTintaStartsOnTheHueOfTheInkInUse() {
        assertEquals(267, InkPreference.Cobalto.startingHue)
        assertEquals(286, InkPreference.Violeta.startingHue)
        assertEquals(147, InkPreference.Tuya(InkHue.of(147)!!).startingHue)
    }
}
