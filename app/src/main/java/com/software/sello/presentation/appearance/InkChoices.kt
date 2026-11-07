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
 * The hand-made inks in the picker, in its order, before Tuya. Each hand-made preference
 * and each palette appears once.
 */
val InkChoices = listOf(
    InkChoice(InkPreference.Cobalto, SelloInk.Cobalto, R.string.settings_ink_cobalto),
    InkChoice(InkPreference.Violeta, SelloInk.Violeta, R.string.settings_ink_violeta)
)

/** What the app is drawn in for this preference: a hand-made palette, or Tuya's hue. */
val InkPreference.themeInk: ThemeInk
    get() = when (this) {
        InkPreference.Cobalto -> SelloInk.Cobalto
        InkPreference.Violeta -> SelloInk.Violeta
        is InkPreference.Tuya -> HueInk(hue.degrees)
    }

/**
 * Where Tu tinta's slider starts: Tuya's hue, in use or kept, or else the hue of the
 * hand-made ink in use.
 */
val AppearancePreference.startingHue: Int
    get() = tuyaHue?.degrees ?: when (val drawn = ink.themeInk) {
        is SelloInk -> drawn.hue
        is HueInk -> drawn.hue
    }

/** The ink's name as a person reads it: "Cobalto", or Tuya with its colour, "Tuya (Rojo)". */
@Composable
fun InkPreference.label(): String = when (this) {
    InkPreference.Cobalto -> stringResource(R.string.settings_ink_cobalto)

    InkPreference.Violeta -> stringResource(R.string.settings_ink_violeta)

    is InkPreference.Tuya ->
        stringResource(R.string.settings_ink_tuya_named, stringResource(hueName(hue.degrees)))
}
