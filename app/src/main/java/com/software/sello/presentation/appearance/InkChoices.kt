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
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.hue

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
 * The ink Apariencia shows chosen and restore names: the one picked, but Sistema on a phone
 * that has no accent at all ([PhoneColor.None], Android 11), where it is not offered, is
 * Cobalto. The choice itself is kept, so a phone that has one takes it again.
 */
fun InkPreference.shown(phone: PhoneColor): InkPreference =
    if (this == InkPreference.Sistema && phone == PhoneColor.None) InkPreference.Cobalto else this

/**
 * What the app is drawn in for this preference: a hand-made palette, or the palette of
 * Tuya's hue or of the [phone]'s. Sistema with no hue to take, on Android 11 or with a grey
 * accent, is drawn in Cobalto.
 */
fun InkPreference.themeInk(phone: PhoneColor): ThemeInk = when (this) {
    InkPreference.Cobalto -> SelloInk.Cobalto
    InkPreference.Violeta -> SelloInk.Violeta
    InkPreference.Sistema -> phone.hue?.let(::HueInk) ?: SelloInk.Cobalto
    is InkPreference.Tuya -> HueInk(hue.degrees)
}

/**
 * Where Tu tinta's slider starts: Tuya's hue, in use or kept, or else the hue of the ink the
 * app is drawn in, the [phone]'s for Sistema.
 */
fun AppearancePreference.startingHue(phone: PhoneColor): Int =
    tuyaHue?.degrees ?: when (val drawn = ink.themeInk(phone)) {
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
