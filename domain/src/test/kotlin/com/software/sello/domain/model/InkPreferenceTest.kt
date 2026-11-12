package com.software.sello.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The ink a person picked, as preferences and backups write it (SELLO-202, SELLO-215). */
class InkPreferenceTest {
    private fun hue(degrees: Long) = TuyaColour.Hue(InkHue.of(degrees)!!)
    private fun exact(rgb: Long) = TuyaColour.Exact(InkColour.of(rgb)!!)

    @Test
    fun aHueIsAWholeDegreeFrom0To359AndNothingElse() {
        assertEquals(0, InkHue.of(0)?.degrees)
        assertEquals(359, InkHue.of(359)?.degrees)
        // Refused, never wrapped (360 is not 0) or clipped (-1 is not 0).
        assertNull(InkHue.of(360))
        assertNull(InkHue.of(-1))
        assertNull(InkHue.of(Long.MAX_VALUE))
        assertNull(InkHue.of(4_294_967_296L))
    }

    @Test
    fun aColourIs24BitSrgbAndNothingElse() {
        assertEquals(0x000000, InkColour.of(0x000000)?.rgb)
        assertEquals(0xFFFFFF, InkColour.of(0xFFFFFF)?.rgb)
        assertEquals(0x2B4FD6, InkColour.of(0x2B4FD6)?.rgb)
        // Refused, never masked to 24 bits (0x1000000 is not black) or clipped.
        assertNull(InkColour.of(0x1000000))
        assertNull(InkColour.of(-1))
        // An opaque ARGB white is not a colour of its 24 bits.
        assertNull(InkColour.of(0xFFFFFFFFL))
        assertNull(InkColour.of(Long.MAX_VALUE))
        assertNull(InkColour.of(Long.MIN_VALUE))
    }

    @Test
    fun eachInkReadsBackFromItsIdAndTuyasColourOrHue() {
        assertEquals(InkPreference.Cobalto, InkPreference.of("cobalto", null))
        assertEquals(InkPreference.Violeta, InkPreference.of("violeta", null))
        assertEquals(InkPreference.Sistema, InkPreference.of("sistema", null))
        assertEquals("sistema", InkPreference.Sistema.id)
        assertEquals(InkPreference.Tuya(hue(22)), InkPreference.of("tuya", hue(22)))
        assertEquals(
            InkPreference.Tuya(exact(0xF2C200)),
            InkPreference.of("tuya", exact(0xF2C200))
        )
        assertEquals("tuya", InkPreference.Tuya(hue(22)).id)
        assertEquals("tuya", InkPreference.Tuya(exact(0x808080)).id)
    }

    @Test
    fun aColourAndAHueAreNeverTheSameTuya() {
        // Nothing stored is converted: hue 0 and a red stay what they were saved as.
        assertNotEquals(InkPreference.Tuya(hue(0)), InkPreference.Tuya(exact(0xFF0000)))
    }

    @Test
    fun anotherInkLeavesTuyasKeptColourOrHueToTuya() {
        assertEquals(InkPreference.Cobalto, InkPreference.of("cobalto", hue(22)))
        assertEquals(InkPreference.Violeta, InkPreference.of("violeta", hue(0)))
        assertEquals(InkPreference.Sistema, InkPreference.of("sistema", exact(0x0A6B4A)))
        assertEquals(
            exact(0x0A6B4A),
            AppearancePreference(ink = InkPreference.Sistema, tuya = exact(0x0A6B4A)).tuya
        )
        assertEquals(
            hue(140),
            AppearancePreference(ink = InkPreference.Sistema, tuya = hue(140)).tuya
        )
    }

    @Test
    fun anIdThatMakesNoInkIsRefused() {
        assertNull(InkPreference.of("tuya", null))
        assertNull(InkPreference.of("Tuya", hue(22)))
        assertNull(InkPreference.of("Tuya", exact(0x2B4FD6)))
        assertNull(InkPreference.of("Sistema", null))
        assertNull(InkPreference.of("system", null))
        assertNull(InkPreference.of("", null))
    }

    @Test
    fun tuyaInUseIsItsOwnKeptColourAndAnotherInkKeepsIt() {
        val red = InkPreference.Tuya(hue(22))
        val yellow = InkPreference.Tuya(exact(0xF2C200))
        assertEquals(hue(22), AppearancePreference(ink = red).tuya)
        assertEquals(exact(0xF2C200), AppearancePreference(ink = yellow).tuya)
        assertNull(AppearancePreference().tuya)
        assertEquals(
            hue(22),
            AppearancePreference(ink = InkPreference.Violeta, tuya = hue(22)).tuya
        )
        for (other in listOf(hue(40), exact(0xF2C200), null)) {
            assertThrows("$other", IllegalArgumentException::class.java) {
                AppearancePreference(ink = red, tuya = other)
            }
        }
        for (other in listOf(exact(0xF2C201), hue(22), null)) {
            assertThrows("$other", IllegalArgumentException::class.java) {
                AppearancePreference(ink = yellow, tuya = other)
            }
        }
    }
}
