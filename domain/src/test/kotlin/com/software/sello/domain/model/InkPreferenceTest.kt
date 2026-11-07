package com.software.sello.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The ink a person picked, as preferences and backups write it (SELLO-202). */
class InkPreferenceTest {
    private fun hue(degrees: Long) = InkHue.of(degrees)!!

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
    fun eachInkReadsBackFromItsIdAndTuyasHue() {
        assertEquals(InkPreference.Cobalto, InkPreference.of("cobalto", null))
        assertEquals(InkPreference.Violeta, InkPreference.of("violeta", null))
        assertEquals(InkPreference.Sistema, InkPreference.of("sistema", null))
        assertEquals("sistema", InkPreference.Sistema.id)
        assertEquals(InkPreference.Tuya(hue(22)), InkPreference.of("tuya", hue(22)))
        assertEquals("tuya", InkPreference.Tuya(hue(22)).id)
    }

    @Test
    fun anotherInkLeavesTuyasKeptHueToTuya() {
        assertEquals(InkPreference.Cobalto, InkPreference.of("cobalto", hue(22)))
        assertEquals(InkPreference.Violeta, InkPreference.of("violeta", hue(0)))
        assertEquals(InkPreference.Sistema, InkPreference.of("sistema", hue(140)))
        assertEquals(
            hue(140),
            AppearancePreference(ink = InkPreference.Sistema, tuyaHue = hue(140)).tuyaHue
        )
    }

    @Test
    fun anIdThatMakesNoInkIsRefused() {
        assertNull(InkPreference.of("tuya", null))
        assertNull(InkPreference.of("Tuya", hue(22)))
        assertNull(InkPreference.of("Sistema", null))
        assertNull(InkPreference.of("system", null))
        assertNull(InkPreference.of("", null))
    }

    @Test
    fun tuyaInUseIsItsOwnKeptHueAndAnotherInkKeepsIt() {
        val red = InkPreference.Tuya(hue(22))
        assertEquals(hue(22), AppearancePreference(ink = red).tuyaHue)
        assertNull(AppearancePreference().tuyaHue)
        assertEquals(
            hue(22),
            AppearancePreference(ink = InkPreference.Violeta, tuyaHue = hue(22)).tuyaHue
        )
        assertThrows(IllegalArgumentException::class.java) {
            AppearancePreference(ink = red, tuyaHue = hue(40))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AppearancePreference(ink = red, tuyaHue = null)
        }
    }
}
