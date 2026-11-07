package com.software.sello.presentation.appearance

import com.software.sello.R
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
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
    fun tuTintaStartsOnTuyasHueOrElseTheHueOfTheInkInUse() {
        assertEquals(267, AppearancePreference(ink = InkPreference.Cobalto).startingHue)
        assertEquals(286, AppearancePreference(ink = InkPreference.Violeta).startingHue)
        val green = InkHue.of(147)!!
        assertEquals(147, AppearancePreference(ink = InkPreference.Tuya(green)).startingHue)
        // Kept while another ink is in use.
        assertEquals(
            147,
            AppearancePreference(ink = InkPreference.Violeta, tuyaHue = green).startingHue
        )
    }
}
