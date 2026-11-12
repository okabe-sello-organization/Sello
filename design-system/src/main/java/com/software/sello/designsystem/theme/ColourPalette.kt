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
 * The palette any exact colour gives (Tuya, ADR 0028): [rgb] is 24-bit sRGB, `0xRRGGBB`.
 * Light: the brand and the band are the colour exactly, with white on them, however hard
 * that reads; the person judges it. Dark: the band is the colour a little deeper with white
 * on it, and the brand a lighter version with dark text. Every other role takes the
 * colour's OKLCH hue at the OKLab lightness of Cobalto's colour for it, with less chroma
 * for a muted colour, so a grey gives grey roles. The meanings never follow: gain, loss,
 * warn, the goal band and the fixed investment inks. Its family is Cobalto, the ink it
 * tints, as [hueColors]'s.
 */
fun colourColors(rgb: Int, isDark: Boolean): SelloColors {
    val picked = Oklch.of(checkColour(rgb))
    // A colour whose chroma is below a vivid one's tints the roles by as much less.
    val ratio = minOf(1.0, picked.chroma / VIVID_CHROMA)
    fun Color.tinted() = tint(this, picked.hue, ratio)
    val exact = Color(0xFF000000.toInt() or rgb)
    val light = with(CobaltoLight) {
        lightPalette(
            family = family,
            brand = exact,
            onBrand = White,
            brandSoft = brandSoft.tinted(),
            onBrandSoft = onBrandSoft.tinted(),
            desk = desk.tinted(),
            paper = paper.tinted(),
            paperAlt = paperAlt.tinted(),
            paperDim = paperDim.tinted(),
            ink = ink.tinted(),
            inkSoft = inkSoft.tinted(),
            outline = outline.tinted(),
            rule = rule.tinted(),
            pastBar = pastBar.tinted(),
            onPastBar = onPastBar.tinted(),
            pastBarSoft = pastBarSoft.tinted(),
            onPastBarSoft = onPastBarSoft.tinted()
        )
    }
    if (!isDark) return light
    return with(CobaltoDark) {
        darkPalette(
            family = family,
            brand = fromLch(
                maxOf(picked.lightness, DARK_BRAND_MIN_LIGHTNESS),
                minOf(picked.chroma, DARK_BRAND_MAX_CHROMA),
                picked.hue
            ),
            onBrand = onBrand.tinted(),
            brandSoft = brandSoft.tinted(),
            onBrandSoft = onBrandSoft.tinted(),
            desk = desk.tinted(),
            paper = paper.tinted(),
            paperAlt = paperAlt.tinted(),
            paperDim = paperDim.tinted(),
            ink = ink.tinted(),
            inkSoft = inkSoft.tinted(),
            outline = outline.tinted(),
            rule = rule.tinted(),
            band = fromLch(picked.lightness * DARK_BAND_DEPTH, picked.chroma, picked.hue),
            onBand = White,
            goalBand = goalBand,
            investmentBand = investmentBand.tinted(),
            pastBar = pastBar.tinted(),
            onPastBar = onPastBar.tinted(),
            pastBarSoft = pastBarSoft.tinted(),
            onPastBarSoft = onPastBarSoft.tinted(),
            lightInk = light.ink
        )
    }
}

/**
 * Whether [rgb] is close to the excess red ([HueMeaning.Red]) or the goal green
 * ([HueMeaning.Green]), or neither: close in light to loss, or to gain or the goal band, or
 * with its dark brand close to dark loss or gain. Close to both, the nearer in light wins.
 * A grey is never close.
 */
fun colourMeaning(rgb: Int): HueMeaning? {
    val picked = Oklch.of(checkColour(rgb))
    if (picked.chroma < MEANING_MIN_CHROMA) return null
    val colour = Color(0xFF000000.toInt() or rgb)
    val darkBrand = colourColors(rgb, isDark = true).brand
    val toRed = distance(colour, LightSemantic.loss)
    val toGoal = distance(colour, CobaltoDark.goalBand)
    val toGreen = minOf(distance(colour, LightSemantic.gain), toGoal)
    val red = toRed < NEAR_IN_LIGHT || distance(darkBrand, DarkSemantic.loss) < NEAR_IN_DARK
    val green = toGreen < NEAR_IN_LIGHT || distance(darkBrand, DarkSemantic.gain) < NEAR_IN_DARK
    return when {
        red && green -> if (toRed < toGreen) HueMeaning.Red else HueMeaning.Green
        red -> HueMeaning.Red
        green -> HueMeaning.Green
        else -> null
    }
}

/**
 * The Spanish name of [rgb], for labels and TalkBack: [hueName] of its OKLCH hue, or Negro,
 * Gris or Blanco for a grey.
 */
@StringRes
fun colourName(rgb: Int): Int {
    val picked = Oklch.of(checkColour(rgb))
    return when {
        picked.chroma >= GREY_CHROMA -> hueName(picked.hue.roundToInt().mod(FULL_TURN))
        picked.lightness < BLACK_LIGHTNESS -> R.string.sello_colour_negro
        picked.lightness > WHITE_LIGHTNESS -> R.string.sello_colour_blanco
        else -> R.string.sello_colour_gris
    }
}

private fun checkColour(rgb: Int): Color {
    require(rgb in 0..0xFFFFFF) { "A colour is 24-bit sRGB, 0x000000 to 0xFFFFFF, not $rgb" }
    return Color(0xFF000000.toInt() or rgb)
}

private val White = Color(0xFFFFFFFF)

private const val FULL_TURN = 360

/** The chroma from which a colour tints the roles fully; below it, in proportion. */
private const val VIVID_CHROMA = 0.12

/** The dark brand is at least this light and at most this vivid, so dark text reads on it. */
private const val DARK_BRAND_MIN_LIGHTNESS = 0.76
private const val DARK_BRAND_MAX_CHROMA = 0.16

/** The dark band's lightness, as a share of the colour's. */
private const val DARK_BAND_DEPTH = 0.88

/** OKLab distances under which a colour is close to a meaning, in light and in dark. */
private const val NEAR_IN_LIGHT = 0.12
private const val NEAR_IN_DARK = 0.065

/** Below this chroma a colour is too grey to be close to a meaning. */
private const val MEANING_MIN_CHROMA = 0.05

/** Below this chroma a colour is named a grey, by its lightness. */
private const val GREY_CHROMA = 0.03
private const val BLACK_LIGHTNESS = 0.25
private const val WHITE_LIGHTNESS = 0.95

/** Below this chroma a role has no hue to take (white), and stays. */
private const val NEUTRAL_CHROMA = 0.004

/** [role] at its own OKLab lightness, turned to [hue] with its chroma times [ratio]. */
private fun tint(role: Color, hue: Double, ratio: Double): Color {
    val own = Oklch.of(role)
    if (own.chroma < NEUTRAL_CHROMA) return role
    return fromLch(own.lightness, own.chroma * ratio, hue)
}

private class Oklch(val lightness: Double, val chroma: Double, val hue: Double) {
    companion object {
        fun of(colour: Color): Oklch {
            val (l, a, b) = linearToOklab(colour.linear())
            val hue = Math.toDegrees(atan2(b, a))
            return Oklch(l, hypot(a, b), if (hue < 0) hue + FULL_TURN else hue)
        }
    }
}

private fun distance(x: Color, y: Color): Double {
    val a = linearToOklab(x.linear())
    val b = linearToOklab(y.linear())
    return hypot(hypot(a[0] - b[0], a[1] - b[1]), a[2] - b[2])
}

/** The colour at OKLCH [l], [c], [h], with its chroma reduced until sRGB holds it. */
private fun fromLch(l: Double, c: Double, h: Double): Color {
    val radians = h * PI / 180
    fun at(chroma: Double) = oklabToLinear(l, chroma * cos(radians), chroma * sin(radians))
    var rgb = at(c)
    if (!inGamut(rgb)) {
        var lo = 0.0
        var hi = c
        repeat(GAMUT_STEPS) {
            val mid = (lo + hi) / 2
            if (inGamut(at(mid))) lo = mid else hi = mid
        }
        rgb = at(lo)
    }
    val (r, g, b) = rgb.map { (toGamma(it.coerceIn(0.0, 1.0)) * 255).roundToInt() }
    return Color(red = r, green = g, blue = b)
}

private const val GAMUT_STEPS = 25

private fun inGamut(rgb: DoubleArray) = rgb.all { it >= -1e-4 && it <= 1 + 1e-4 }

private fun Color.linear(): DoubleArray {
    val argb = toArgb()
    return intArrayOf(argb shr 16, argb shr 8, argb)
        .map { toLinear((it and 0xFF) / 255.0) }
        .toDoubleArray()
}

private fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

private fun toGamma(c: Double) = if (c <= 0.0031308) 12.92 * c else 1.055 * c.pow(1 / 2.4) - 0.055

// OKLab (Björn Ottosson, 2020), from and to linear sRGB, as HuePalette.kt has it; E37's
// generator keeps its own copy unchanged.
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
