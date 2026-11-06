package com.software.sello.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class SelloTokensTest {
    @Test
    fun alphaTokensByRole() {
        val alpha = SelloAlpha()
        assertEquals(0.38f, alpha.disabled)
        assertEquals(0.5f, alpha.muted)
        assertEquals(0.55f, alpha.scrim)
        assertEquals(0.72f, alpha.secondary)
        assertEquals(0.16f, alpha.bandTint)
        assertEquals(0.15f, alpha.faintFill)
    }

    @Test
    fun newCornerTokens() {
        val shapes = SelloShapes()
        assertEquals(RoundedCornerShape(12.dp), shapes.inset)
        assertEquals(RoundedCornerShape(14.dp), shapes.panel)
    }
}
