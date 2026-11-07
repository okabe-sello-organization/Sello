package com.software.sello.designsystem.theme

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.software.sello.designsystem.R
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Cobalto's brand hue as a whole OKLCH degree (its exact hue is 266.6°). A palette built
 * from it is Cobalto: the generator turns each role by the hue's distance from this one.
 */
internal const val COBALTO_HUE = 267

/** Violeta's brand hue as a whole OKLCH degree (its exact hue is 285.9°). */
internal const val VIOLETA_HUE = 286

/**
 * The palette a hue of 0 to 359 OKLCH degrees gives (Tuya, Sistema): Cobalto's, turned.
 * Each role keeps the luminance of Cobalto's colour for it, so every contrast ratio stays
 * Cobalto's, and turns its hue by as much as [hue] is from [COBALTO_HUE]; its chroma drops
 * only where sRGB can't hold it. Neutral roles (white) stay, and so do the meanings: gain,
 * loss, warn, the goal band and the fixed investment inks. Its family is Cobalto, the ink
 * it turns, so at [COBALTO_HUE] it equals Cobalto's palette.
 */
fun hueColors(hue: Int, isDark: Boolean): SelloColors {
    val turn = checkHue(hue) - COBALTO_HUE
    val light = with(CobaltoLight) {
        lightPalette(
            family = family,
            brand = brand.turned(turn),
            onBrand = onBrand.turned(turn),
            brandSoft = brandSoft.turned(turn),
            onBrandSoft = onBrandSoft.turned(turn),
            desk = desk.turned(turn),
            paper = paper.turned(turn),
            paperAlt = paperAlt.turned(turn),
            paperDim = paperDim.turned(turn),
            ink = ink.turned(turn),
            inkSoft = inkSoft.turned(turn),
            outline = outline.turned(turn),
            rule = rule.turned(turn),
            pastBar = pastBar.turned(turn),
            onPastBar = onPastBar.turned(turn),
            pastBarSoft = pastBarSoft.turned(turn),
            onPastBarSoft = onPastBarSoft.turned(turn)
        )
    }
    if (!isDark) return light
    return with(CobaltoDark) {
        darkPalette(
            family = family,
            brand = brand.turned(turn),
            onBrand = onBrand.turned(turn),
            brandSoft = brandSoft.turned(turn),
            onBrandSoft = onBrandSoft.turned(turn),
            desk = desk.turned(turn),
            paper = paper.turned(turn),
            paperAlt = paperAlt.turned(turn),
            paperDim = paperDim.turned(turn),
            ink = ink.turned(turn),
            inkSoft = inkSoft.turned(turn),
            outline = outline.turned(turn),
            rule = rule.turned(turn),
            band = band.turned(turn),
            onBand = onBand.turned(turn),
            goalBand = goalBand,
            investmentBand = investmentBand.turned(turn),
            pastBar = pastBar.turned(turn),
            onPastBar = onPastBar.turned(turn),
            pastBarSoft = pastBarSoft.turned(turn),
            onPastBarSoft = onPastBarSoft.turned(turn),
            lightInk = light.ink
        )
    }
}

/**
 * The light band [hueColors] gives [hue], alone: what a swatch or a hue slider shows, without
 * building the whole palette for each.
 */
internal fun hueSwatch(hue: Int): Color = CobaltoLight.band.turned(checkHue(hue) - COBALTO_HUE)

/**
 * The OKLCH hue of [argb] (Sistema reads the phone's accent colour with it) as a whole degree
 * from 0 to 359, rounded to the nearest: 359.5° and above is 0°, the same hue round the
 * circle. Null for a colour with no hue to turn to, a grey such as a monochrome phone gives.
 */
fun hueOf(argb: Int): Int? {
    val lab = linearToOklab(Color(argb).linear())
    if (hypot(lab[1], lab[2]) < NEUTRAL_CHROMA) return null
    return Math.toDegrees(atan2(lab[2], lab[1])).roundToInt().mod(FULL_TURN)
}

private const val FULL_TURN = 360

/** A hue that also carries one of the fixed meanings: red marks overspending, green goals. */
enum class HueMeaning { Red, Green }

/** Whether [hue] is red (0 to 49°) or green (122 to 195°), or neither. */
fun hueMeaning(hue: Int): HueMeaning? = when (checkHue(hue)) {
    in 0 until 50 -> HueMeaning.Red
    in 122 until 196 -> HueMeaning.Green
    else -> null
}

/** The Spanish name of [hue], for labels and TalkBack. */
@StringRes
fun hueName(hue: Int): Int {
    val degree = checkHue(hue)
    return HueNames.first { (end, _) -> degree < end }.second
}

/** Each name with the first degree past it, in order round the circle. */
private val HueNames = listOf(
    12 to R.string.sello_hue_rosa,
    45 to R.string.sello_hue_rojo,
    70 to R.string.sello_hue_naranja,
    92 to R.string.sello_hue_ambar,
    112 to R.string.sello_hue_amarillo,
    135 to R.string.sello_hue_lima,
    168 to R.string.sello_hue_verde,
    200 to R.string.sello_hue_turquesa,
    225 to R.string.sello_hue_petroleo,
    250 to R.string.sello_hue_azul,
    282 to R.string.sello_hue_anil,
    305 to R.string.sello_hue_violeta,
    330 to R.string.sello_hue_morado,
    352 to R.string.sello_hue_fucsia,
    360 to R.string.sello_hue_rosa
)

private fun checkHue(hue: Int): Int {
    require(hue in 0..359) { "A hue is a whole degree from 0 to 359, not $hue" }
    return hue
}

/** Below this OKLCH chroma a colour has no hue to turn (white). */
private const val NEUTRAL_CHROMA = 0.004

/** This colour with its OKLCH hue turned by [degrees], at the same luminance. */
private fun Color.turned(degrees: Int): Color {
    val rgb = linear()
    val lab = linearToOklab(rgb)
    val chroma = hypot(lab[1], lab[2])
    if (chroma < NEUTRAL_CHROMA) return this
    val hue = Math.toDegrees(atan2(lab[2], lab[1]))
    val turned = ((hue + degrees) % 360 + 360) % 360
    return linearToColor(solve(luminance(rgb), chroma, turned))
}

/**
 * The colour at hue [h] with luminance [y] and the most chroma up to [chroma] that sRGB
 * holds.
 */
private fun solve(y: Double, chroma: Double, h: Double): DoubleArray {
    fun at(c: Double) = fromLch(lightnessFor(y, c, h), c, h)
    val full = at(chroma)
    if (inGamut(full)) return full
    var lo = 0.0
    var hi = chroma
    repeat(30) {
        val mid = (lo + hi) / 2
        if (inGamut(at(mid))) lo = mid else hi = mid
    }
    return at(lo)
}

/** The OKLab lightness whose colour at chroma [c] and hue [h] has luminance [y]. */
private fun lightnessFor(y: Double, c: Double, h: Double): Double {
    var lo = 0.0
    var hi = 1.0
    repeat(40) {
        val mid = (lo + hi) / 2
        if (luminance(fromLch(mid, c, h)) < y) lo = mid else hi = mid
    }
    return (lo + hi) / 2
}

private fun inGamut(rgb: DoubleArray) = rgb.all { it >= -1e-6 && it <= 1 + 1e-6 }

private fun luminance(rgb: DoubleArray) = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]

private fun Color.linear(): DoubleArray {
    val argb = toArgb()
    return intArrayOf(argb shr 16, argb shr 8, argb)
        .map { toLinear((it and 0xFF) / 255.0) }
        .toDoubleArray()
}

private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun toGamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

private fun linearToColor(rgb: DoubleArray): Color {
    val (r, g, b) = rgb.map { (toGamma(it.coerceIn(0.0, 1.0)) * 255).roundToInt() }
    return Color(red = r, green = g, blue = b)
}

private fun fromLch(l: Double, c: Double, h: Double): DoubleArray {
    val radians = h * PI / 180
    return oklabToLinear(l, c * cos(radians), c * sin(radians))
}

// OKLab (Björn Ottosson, 2020), from and to linear sRGB.
private fun linearToOklab(rgb: DoubleArray): DoubleArray {
    val (r, g, b) = rgb
    val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    return doubleArrayOf(
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
    )
}

private fun oklabToLinear(lightness: Double, a: Double, b: Double): DoubleArray {
    val l = (lightness + 0.3963377774 * a + 0.2158037573 * b).pow(3)
    val m = (lightness - 0.1055613458 * a - 0.0638541728 * b).pow(3)
    val s = (lightness - 0.0894841775 * a - 1.2914855480 * b).pow(3)
    return doubleArrayOf(
        4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
        -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
        -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    )
}
