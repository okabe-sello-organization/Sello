package com.software.sello.feature.settings

import com.software.sello.designsystem.theme.ThemeInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.presentation.appearance.themeInk

/** Pure mapping at the composition boundary: neutral preferences never leak into tokens. */
data class AppearanceTheme(val ink: ThemeInk, val dark: Boolean)

/** [systemHue] is the phone's colour, for Sistema: null when it has none. */
fun AppearancePreference.asSelloTheme(systemDark: Boolean, systemHue: Int?): AppearanceTheme =
    AppearanceTheme(
        ink = ink.themeInk(systemHue),
        dark = when (theme) {
            ThemePreference.System -> systemDark
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
    )
