package com.software.sello.feature.settings

import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.platform.PhoneColor
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceThemeTest {
    @Test
    fun eachApprovedAppearanceMapsToTheMatchingSelloScheme() {
        assertEquals(
            AppearanceTheme(SelloInk.Cobalto, false),
            AppearancePreference(ThemePreference.Light, InkPreference.Cobalto)
                .asSelloTheme(true, PhoneColor.None)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Cobalto, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Cobalto)
                .asSelloTheme(false, PhoneColor.None)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, false),
            AppearancePreference(ThemePreference.Light, InkPreference.Violeta)
                .asSelloTheme(true, PhoneColor.None)
        )
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                .asSelloTheme(false, PhoneColor.None)
        )
    }

    @Test
    fun systemAppearanceOnlyFollowsTheSystemDarkSignal() {
        val preference = AppearancePreference(ThemePreference.System, InkPreference.Violeta)
        val none = PhoneColor.None
        assertEquals(AppearanceTheme(SelloInk.Violeta, false), preference.asSelloTheme(false, none))
        assertEquals(AppearanceTheme(SelloInk.Violeta, true), preference.asSelloTheme(true, none))
    }

    @Test
    fun sistemaIsDrawnInThePhonesHueAndInCobaltoOnAPhoneWithoutOne() {
        val sistema = AppearancePreference(ThemePreference.Dark, InkPreference.Sistema)
        assertEquals(
            AppearanceTheme(HueInk(140), true),
            sistema.asSelloTheme(false, PhoneColor.Hue(140))
        )
        // Android 11, and a grey accent: no hue to take.
        for (phone in listOf(PhoneColor.None, PhoneColor.Grey)) {
            assertEquals(
                AppearanceTheme(SelloInk.Cobalto, true),
                sistema.asSelloTheme(false, phone)
            )
        }
        // The phone's hue is Sistema's alone.
        assertEquals(
            AppearanceTheme(SelloInk.Violeta, true),
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                .asSelloTheme(false, PhoneColor.Hue(140))
        )
    }
}
