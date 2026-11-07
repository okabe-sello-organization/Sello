package com.software.sello.feature.settings

import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceThemeTest {
    @Test
    fun eachApprovedAppearanceMapsToTheMatchingSelloScheme() {
        assertEquals(
            AppearanceTheme(SelloInk.Cobalto, false),
            AppearancePreference(ThemePreference.Light, InkPreference.Cobalto)
                .asSelloTheme(true, null)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Cobalto, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Cobalto)
                .asSelloTheme(false, null)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, false),
            AppearancePreference(ThemePreference.Light, InkPreference.Violeta)
                .asSelloTheme(true, null)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                .asSelloTheme(false, null)
        )
    }

    @Test
    fun systemAppearanceOnlyFollowsTheSystemDarkSignal() {
        val preference = AppearancePreference(ThemePreference.System, InkPreference.Violeta)
        assertEquals(AppearanceTheme(SelloInk.Violeta, false), preference.asSelloTheme(false, null))
        assertEquals(AppearanceTheme(SelloInk.Violeta, true), preference.asSelloTheme(true, null))
    }

    @Test
    fun sistemaIsDrawnInThePhonesHueAndInCobaltoOnAPhoneWithoutOne() {
        val sistema = AppearancePreference(ThemePreference.Dark, InkPreference.Sistema)
        assertEquals(AppearanceTheme(HueInk(140), true), sistema.asSelloTheme(false, 140))
        // Android 11: the phone has no colour of its own.
        assertEquals(AppearanceTheme(SelloInk.Cobalto, true), sistema.asSelloTheme(false, null))
        // The phone's hue is Sistema's alone.
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                .asSelloTheme(false, 140)
        )
    }
}
