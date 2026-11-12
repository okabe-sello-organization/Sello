package com.software.sello.feature.settings

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where Tu tinta's colour bar and colour square stand: the bar's [hue], in degrees from 0 up
 * to but not including 360, and the square's [saturation] (left to right) and [value]
 * (bottom to top), each from 0 to 1. Kept apart from the colour, so a grey or black keeps
 * the bar's hue and moving out of it again finds the colour where it was.
 */
data class TuyaPick(val hue: Float, val saturation: Float, val value: Float) {
    /** Whether this is a place the bar and the square can stand; NaN never is. */
    val isValid: Boolean
        get() = hue >= 0f && hue < FULL_TURN && saturation in 0f..1f && value in 0f..1f

    /** The 24-bit sRGB colour (`0xRRGGBB`) at this place. */
    fun rgb(): Int {
        fun channel(n: Int): Int {
            val k = (n + hue / SIXTH) % SIX
            val part = value - value * saturation * maxOf(0f, minOf(k, FOUR - k, 1f))
            return (part * MAX_CHANNEL).roundToInt().coerceIn(0, MAX_CHANNEL)
        }
        return (channel(RED_N) shl 16) or (channel(GREEN_N) shl 8) or channel(BLUE_N)
    }

    companion object {
        /**
         * Where [rgb] stands. A colour with no hue of its own (a grey, black or white) keeps
         * [hue], the bar's hue before it.
         */
        fun of(rgb: Int, hue: Float): TuyaPick {
            val r = (rgb shr 16 and MAX_CHANNEL) / MAX_CHANNEL.toFloat()
            val g = (rgb shr 8 and MAX_CHANNEL) / MAX_CHANNEL.toFloat()
            val b = (rgb and MAX_CHANNEL) / MAX_CHANNEL.toFloat()
            val max = maxOf(r, g, b)
            val span = max - minOf(r, g, b)
            val own = when {
                span == 0f -> hue
                max == r -> SIXTH * (((g - b) / span) % SIX)
                max == g -> SIXTH * ((b - r) / span + 2)
                else -> SIXTH * ((r - g) / span + FOUR)
            }
            val turned = if (own < 0f) own + FULL_TURN else own
            return TuyaPick(
                hue = if (abs(turned - FULL_TURN) < HUE_EPSILON) 0f else turned,
                saturation = if (max == 0f) 0f else span / max,
                value = max
            )
        }
    }
}

/**
 * The colour a typed code names: `#RRGGBB` or `RRGGBB`, in any case, as 24-bit sRGB; null for
 * anything else, which changes nothing.
 */
fun colourOfCode(text: String): Int? {
    val digits = text.removePrefix("#")
    if (digits.length != CODE_DIGITS || !digits.all { it.isHexDigit() }) return null
    return digits.toInt(HEX)
}

/** [rgb]'s code as Tu tinta shows it: "#C72842". */
fun codeOf(rgb: Int): String = "#%06X".format(rgb)

/** The longest text the code field keeps: a `#` and six digits. */
const val CODE_LENGTH = 7

private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private const val CODE_DIGITS = 6
private const val HEX = 16
private const val FULL_TURN = 360f
private const val SIXTH = 60f
private const val SIX = 6f
private const val FOUR = 4f
private const val MAX_CHANNEL = 255
private const val RED_N = 5
private const val GREEN_N = 3
private const val BLUE_N = 1
private const val HUE_EPSILON = 1e-4f
