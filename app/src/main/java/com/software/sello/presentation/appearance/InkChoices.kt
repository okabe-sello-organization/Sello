package com.software.sello.presentation.appearance

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.theme.ColourInk
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.ThemeInk
import com.software.sello.designsystem.theme.colourName
import com.software.sello.designsystem.theme.hueName
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.TuyaColour
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
 * What the app is drawn in for this preference: a hand-made palette, the palette of Tuya's
 * colour or hue, or of the [phone]'s hue. Sistema with no hue to take, on Android 11 or with
 * a grey accent, is drawn in Cobalto.
 */
fun InkPreference.themeInk(phone: PhoneColor): ThemeInk = when (this) {
    InkPreference.Cobalto -> SelloInk.Cobalto
    InkPreference.Violeta -> SelloInk.Violeta
    InkPreference.Sistema -> phone.hue?.let(::HueInk) ?: SelloInk.Cobalto
    is InkPreference.Tuya -> colour.themeInk()
}

/**
 * Tuya's palette: an exact colour drawn by SELLO-214's generator, or a hue saved before
 * drawn as E37 draws it, never converted (ADR 0028).
 */
fun TuyaColour.themeInk(): ThemeInk = when (this) {
    is TuyaColour.Exact -> ColourInk(colour.rgb)
    is TuyaColour.Hue -> HueInk(hue.degrees)
}

/** The name of Tuya's colour or hue: "Gris", "Rojo". */
@StringRes
fun TuyaColour.name(): Int = when (this) {
    is TuyaColour.Exact -> colourName(colour.rgb)
    is TuyaColour.Hue -> hueName(hue.degrees)
}

/**
 * Where Tu tinta starts, 24-bit sRGB: Tuya's kept colour, else the colour Tuya's kept hue
 * gives, else the colour of the ink in use, the [phone]'s for Sistema.
 */
fun AppearancePreference.startingColour(phone: PhoneColor): Int =
    (tuya?.themeInk() ?: ink.themeInk(phone)).swatch.rgb()

/** The light band of the ink this preference draws, 24-bit sRGB: Tu tinta's Antes. */
fun InkPreference.colour(phone: PhoneColor): Int = themeInk(phone).swatch.rgb()

private fun Color.rgb(): Int = toArgb() and 0xFFFFFF

/**
 * The ink's name as a person reads it: "Cobalto", "Sistema", or Tuya with its colour,
 * "Tuya (Rojo)", "Tuya (Gris)".
 */
@Composable
fun InkPreference.label(): String = when (this) {
    InkPreference.Cobalto -> stringResource(R.string.settings_ink_cobalto)

    InkPreference.Violeta -> stringResource(R.string.settings_ink_violeta)

    InkPreference.Sistema -> stringResource(R.string.settings_ink_sistema)

    is InkPreference.Tuya ->
        stringResource(R.string.settings_ink_tuya_named, stringResource(colour.name()))
}
