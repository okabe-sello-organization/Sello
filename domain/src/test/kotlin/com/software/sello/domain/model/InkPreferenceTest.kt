package com.software.sello.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The ink a person picked, as preferences and backups write it (SELLO-202). */
class InkPreferenceTest {
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
    fun eachInkReadsBackFromItsIdAndHue() {
        assertEquals(InkPreference.Cobalto, InkPreference.of("cobalto", null))
        assertEquals(InkPreference.Violeta, InkPreference.of("violeta", null))
        assertEquals(InkPreference.Tuya(InkHue.of(22)!!), InkPreference.of("tuya", 22))
        assertEquals("tuya", InkPreference.Tuya(InkHue.of(22)!!).id)
    }

    @Test
    fun anIdAndHueThatDoNotMakeAnInkAreRefused() {
        assertNull(InkPreference.of("tuya", null))
        assertNull(InkPreference.of("tuya", 360))
        assertNull(InkPreference.of("tuya", -1))
        assertNull(InkPreference.of("cobalto", 22))
        assertNull(InkPreference.of("violeta", 0))
        assertNull(InkPreference.of("sistema", null))
        assertNull(InkPreference.of("Tuya", 22))
    }
}
