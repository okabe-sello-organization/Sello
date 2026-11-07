package com.software.sello.presentation.appearance

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.ThemeInk
import com.software.sello.designsystem.theme.hueName
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkPreference

/** One hand-made ink a person can pick: its stored preference, its palettes and its name. */
class InkChoice(val preference: InkPreference, val ink: SelloInk, @StringRes val label: Int)

/**
 * The hand-made inks in the picker, in its order, before Sistema and Tuya. Each hand-made
 * preference and each palette appears once.
 */
val InkChoices = listOf(
    InkChoice(InkPreference.Cobalto, SelloInk.Cobalto, R.string.settings_ink_cobalto),
    InkChoice(InkPreference.Violeta, SelloInk.Violeta, R.string.settings_ink_violeta)
)

/**
 * The ink Sello is drawn in and Apariencia shows chosen: the one picked, but Sistema on a
 * phone with no colour of its own ([systemHue] null, as on Android 11) is Cobalto. The choice
 * itself is kept, so the phone's colour is taken again once there is one.
 */
fun InkPreference.shown(systemHue: Int?): InkPreference =
    if (this == InkPreference.Sistema && systemHue == null) InkPreference.Cobalto else this

/**
 * What the app is drawn in for this preference: a hand-made palette, or the palette of
 * Tuya's hue or of the phone's, [systemHue].
 */
fun InkPreference.themeInk(systemHue: Int?): ThemeInk = when (this) {
    InkPreference.Cobalto -> SelloInk.Cobalto

    InkPreference.Violeta -> SelloInk.Violeta

    // As [shown]: Cobalto on a phone with no colour of its own.
    InkPreference.Sistema -> systemHue?.let(::HueInk) ?: SelloInk.Cobalto

    is InkPreference.Tuya -> HueInk(hue.degrees)
}

/**
 * Where Tu tinta's slider starts: Tuya's hue, in use or kept, or else the hue of the ink in
 * use, the phone's [systemHue] for Sistema.
 */
fun AppearancePreference.startingHue(systemHue: Int?): Int =
    tuyaHue?.degrees ?: when (val drawn = ink.themeInk(systemHue)) {
        is SelloInk -> drawn.hue
        is HueInk -> drawn.hue
    }

/**
 * The ink's name as a person reads it: "Cobalto", "Sistema", or Tuya with its colour,
 * "Tuya (Rojo)".
 */
@Composable
fun InkPreference.label(): String = when (this) {
    InkPreference.Cobalto -> stringResource(R.string.settings_ink_cobalto)

    InkPreference.Violeta -> stringResource(R.string.settings_ink_violeta)

    InkPreference.Sistema -> stringResource(R.string.settings_ink_sistema)

    is InkPreference.Tuya ->
        stringResource(R.string.settings_ink_tuya_named, stringResource(hueName(hue.degrees)))
}
