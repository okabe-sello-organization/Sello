package com.software.sello.feature.settings

import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.presentation.appearance.choice

/** Pure mapping at the composition boundary: neutral preferences never leak into tokens. */
data class AppearanceTheme(val ink: SelloInk, val dark: Boolean)

fun AppearancePreference.asSelloTheme(systemDark: Boolean): AppearanceTheme = AppearanceTheme(
    ink = ink.choice.ink,
    dark = when (theme) {
        ThemePreference.System -> systemDark
        ThemePreference.Light -> false
        ThemePreference.Dark -> true
    }
)
