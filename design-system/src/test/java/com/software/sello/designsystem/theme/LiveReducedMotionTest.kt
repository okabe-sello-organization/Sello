package com.software.sello.designsystem.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The theme follows the system's "remove animations" setting while it is on screen,
 * and stops listening when it leaves, shown here with a setting changed by hand. The
 * device's real setting is in the `RealReducedMotionSettingTest` on the device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveReducedMotionTest {
    @get:Rule
    val rule = createComposeRule()

    /** A setting a test changes by hand, counting who is listening. */
    private class HandScale(var off: Boolean) : AnimatorScale {
        val listeners = mutableListOf<() -> Unit>()
        var watches = 0

        override fun isOff() = off

        override fun watch(onChange: () -> Unit): AutoCloseable {
            watches++
            listeners += onChange
            return AutoCloseable { listeners -= onChange }
        }

        fun set(value: Boolean) {
            off = value
            listeners.toList().forEach { it() }
        }
    }

    @Test
    fun aChangeWhileOnScreenIsFollowedInBothDirections() {
        val scale = HandScale(off = false)
        var reduced: Boolean? = null
        rule.setContent { reduced = reducedMotion(scale) }
        rule.waitForIdle()
        val atFirst = reduced

        rule.runOnUiThread { scale.set(true) }
        rule.waitForIdle()
        val turnedOff = reduced
        rule.runOnUiThread { scale.set(false) }
        rule.waitForIdle()

        assertEquals(listOf(false, true, false), listOf(atFirst, turnedOff, reduced))
    }

    @Test
    fun drawingAgainNeverListensTwiceAndLeavingTheScreenStopsListening() {
        val scale = HandScale(off = false)
        var tick by mutableIntStateOf(0)
        var shown by mutableStateOf(true)
        rule.setContent {
            if (shown) {
                tick
                reducedMotion(scale)
            }
        }
        repeat(5) {
            tick++
            rule.waitForIdle()
        }
        val whileShown = scale.watches to scale.listeners.size

        shown = false
        rule.waitForIdle()

        assertEquals(1 to 1, whileShown)
        assertEquals(0, scale.listeners.size)
    }

    @Test
    fun aChangeMadeJustBeforeListeningStartedIsNotMissed() {
        // The setting flips after it is first read and before anyone is listening.
        val scale = object : AnimatorScale {
            var reads = 0

            override fun isOff() = reads++ > 0

            override fun watch(onChange: () -> Unit) = AutoCloseable {}
        }
        var reduced: Boolean? = null

        rule.setContent { reduced = reducedMotion(scale) }
        rule.waitForIdle()

        assertEquals(true, reduced)
    }
}
