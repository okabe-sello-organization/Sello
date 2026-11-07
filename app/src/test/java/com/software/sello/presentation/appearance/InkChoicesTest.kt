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
        for (systemHue in listOf(null, 140)) {
            assertEquals(SelloInk.Cobalto, InkPreference.Cobalto.themeInk(systemHue))
            assertEquals(SelloInk.Violeta, InkPreference.Violeta.themeInk(systemHue))
            assertEquals(HueInk(22), InkPreference.Tuya(InkHue.of(22)!!).themeInk(systemHue))
            assertEquals(HueInk(0), InkPreference.Tuya(InkHue.of(0)!!).themeInk(systemHue))
            assertEquals(HueInk(359), InkPreference.Tuya(InkHue.of(359)!!).themeInk(systemHue))
        }
    }

    @Test
    fun sistemaIsThePhonesHueAndCobaltoWhereThePhoneHasNone() {
        assertEquals(HueInk(140), InkPreference.Sistema.themeInk(140))
        assertEquals(HueInk(0), InkPreference.Sistema.themeInk(0))
        assertEquals(HueInk(359), InkPreference.Sistema.themeInk(359))
        assertEquals(SelloInk.Cobalto, InkPreference.Sistema.themeInk(null))

        assertEquals(InkPreference.Sistema, InkPreference.Sistema.shown(140))
        assertEquals(InkPreference.Cobalto, InkPreference.Sistema.shown(null))
        for (ink in listOf(InkPreference.Violeta, InkPreference.Tuya(InkHue.of(22)!!))) {
            assertEquals(ink, ink.shown(null))
            assertEquals(ink, ink.shown(140))
        }
    }

    @Test
    fun tuTintaStartsOnTuyasHueOrElseTheHueOfTheInkInUse() {
        assertEquals(267, AppearancePreference(ink = InkPreference.Cobalto).startingHue(140))
        assertEquals(286, AppearancePreference(ink = InkPreference.Violeta).startingHue(140))
        val green = InkHue.of(147)!!
        assertEquals(147, AppearancePreference(ink = InkPreference.Tuya(green)).startingHue(140))
        // Kept while another ink is in use.
        assertEquals(
            147,
            AppearancePreference(ink = InkPreference.Violeta, tuyaHue = green).startingHue(140)
        )
        assertEquals(
            147,
            AppearancePreference(ink = InkPreference.Sistema, tuyaHue = green).startingHue(140)
        )
        // Sistema with no Tuya yet: the phone's hue, or Cobalto's where the phone has none.
        assertEquals(140, AppearancePreference(ink = InkPreference.Sistema).startingHue(140))
        assertEquals(267, AppearancePreference(ink = InkPreference.Sistema).startingHue(null))
    }
}
