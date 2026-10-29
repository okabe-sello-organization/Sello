package com.software.sello.designsystem.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import org.junit.Assert.assertEquals
import org.junit.Test

class SelloMotionTest {
    @Test
    fun tweensUseTheReferenceDurationsAndEasings() {
        assertEquals(tween<Float>(120, easing = LinearOutSlowInEasing), SelloMotion.quick<Float>())
        assertEquals(
            tween<Float>(240, easing = SelloMotion.StandardEasing),
            SelloMotion.standard<Float>()
        )
        assertEquals(
            tween<Float>(360, easing = SelloMotion.EmphasizedEasing),
            SelloMotion.emphasized<Float>()
        )
        assertEquals(tween<Float>(150, easing = LinearEasing), SelloMotion.feed<Float>())
    }

    @Test
    fun springsUseTheReferenceDampingAndStiffness() {
        assertEquals(spring<Float>(1f, 120f), SelloMotion.fill<Float>())
        assertEquals(spring<Float>(0.8f, 380f), SelloMotion.settle<Float>())
        assertEquals(spring<Float>(0.55f, 700f), SelloMotion.stamp<Float>())
    }
}
