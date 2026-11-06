package com.software.sello.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelloTypographyTest {
    private val roles: Map<String, TextStyle> = with(SelloTypography) {
        mapOf(
            "displayLarge" to displayLarge,
            "displayMedium" to displayMedium,
            "displaySmall" to displaySmall,
            "headlineLarge" to headlineLarge,
            "headlineMedium" to headlineMedium,
            "headlineSmall" to headlineSmall,
            "titleLarge" to titleLarge,
            "titleMedium" to titleMedium,
            "titleSmall" to titleSmall,
            "bodyLarge" to bodyLarge,
            "bodyMedium" to bodyMedium,
            "bodySmall" to bodySmall,
            "labelLarge" to labelLarge,
            "labelMedium" to labelMedium,
            "labelSmall" to labelSmall
        )
    }

    @Test
    fun referenceRolesHaveTheirSizeLineWeightAndTracking() {
        // size, line height, weight, tracking in em — typed from the reference table.
        val expected = mapOf(
            "displayLarge" to listOf(60.0, 61.0, 900.0, -0.04),
            "displayMedium" to listOf(44.0, 46.0, 900.0, -0.04),
            "displaySmall" to listOf(32.0, 34.0, 900.0, -0.035),
            "headlineSmall" to listOf(23.0, 26.0, 800.0, -0.02),
            "titleLarge" to listOf(19.0, 22.0, 800.0, -0.01),
            "titleMedium" to listOf(16.0, 20.0, 800.0, 0.0),
            "bodyLarge" to listOf(14.0, 19.0, 400.0, 0.0),
            "bodyMedium" to listOf(13.5, 18.0, 400.0, 0.0),
            "labelLarge" to listOf(17.0, 20.0, 800.0, 0.0),
            "labelMedium" to listOf(12.5, 16.0, 400.0, 0.0),
            "labelSmall" to listOf(12.0, 14.0, 600.0, 0.0)
        )
        for ((role, v) in expected) {
            val style = roles.getValue(role)
            assertEquals(role, v[0].sp, style.fontSize)
            assertEquals(role, v[1].sp, style.lineHeight)
            assertEquals(role, FontWeight(v[2].toInt()), style.fontWeight)
            assertEquals(role, v[3].em, style.letterSpacing)
        }
    }

    @Test
    fun everyMaterialRoleIsSchibstedAndAtLeast12sp() {
        assertEquals(15, roles.size)
        for ((role, style) in roles) {
            assertEquals(role, SchibstedGrotesk, style.fontFamily)
            assertTrue("$role is ${style.fontSize}", style.fontSize.value >= 12f)
        }
    }

    @Test
    fun figureRolesUseTabularFiguresAndSentenceRolesDoNot() {
        for ((role, style) in roles) {
            val holdsFigures = !role.startsWith("body") && !role.startsWith("label")
            assertEquals(role, if (holdsFigures) "tnum" else null, style.fontFeatureSettings)
        }
    }

    @Test
    fun stencilTypeIsLimitedToStampStyles() {
        val type = SelloType()
        val stamps = listOf(type.stampSmall, type.stampLarge, type.stampHero)
        assertEquals(listOf(15.sp, 34.sp, 52.sp), stamps.map { it.fontSize })
        stamps.forEach {
            assertEquals(SairaStencilOne, it.fontFamily)
            assertEquals(0.05.em, it.letterSpacing)
        }
        roles.forEach { (role, style) -> assertTrue(role, style.fontFamily != SairaStencilOne) }
    }

    @Test
    fun repeatedCombinationsAreTheirRoleWithOnlyTheNamedChange() {
        val type = SelloType()
        // name to size, line height, weight, tracking in em, worked out from the roles above.
        val expected = mapOf(
            "barTitle" to (type.barTitle to listOf(24.0, 26.0, 800.0, -0.02)),
            "dialogTitle" to (type.dialogTitle to listOf(22.0, 26.0, 800.0, -0.02)),
            "keypadKey" to (type.keypadKey to listOf(22.0, 26.0, 800.0, -0.01)),
            "buttonLabel" to (type.buttonLabel to listOf(15.0, 20.0, 800.0, 0.0)),
            "rowTitle" to (type.rowTitle to listOf(14.0, 19.0, 800.0, 0.0)),
            "lineTitle" to (type.lineTitle to listOf(13.5, 18.0, 800.0, 0.0)),
            "groupLabel" to (type.groupLabel to listOf(12.5, 16.0, 800.0, 0.0)),
            "chipLabel" to (type.chipLabel to listOf(13.5, 18.0, 600.0, 0.0)),
            "figure" to (type.figure to listOf(16.0, 20.0, 900.0, 0.0)),
            "figureLarge" to (type.figureLarge to listOf(23.0, 26.0, 900.0, -0.02))
        )
        for ((name, pair) in expected) {
            val (style, v) = pair
            assertEquals(name, SchibstedGrotesk, style.fontFamily)
            assertEquals(name, v[0].sp, style.fontSize)
            assertEquals(name, v[1].sp, style.lineHeight)
            assertEquals(name, FontWeight(v[2].toInt()), style.fontWeight)
            assertEquals(name, v[3].em, style.letterSpacing)
        }
        // Figures stay tabular; sentence text does not.
        val tabular =
            listOf(type.barTitle, type.dialogTitle, type.keypadKey, type.figure, type.figureLarge)
        tabular.forEach { assertEquals("tnum", it.fontFeatureSettings) }
        val sentence =
            listOf(type.buttonLabel, type.rowTitle, type.lineTitle, type.groupLabel, type.chipLabel)
        sentence.forEach { assertEquals(null, it.fontFeatureSettings) }
    }

    @Test
    fun weightsByRole() {
        val weight = SelloType().weight
        assertEquals(
            listOf(400, 600, 700, 800, 900),
            listOf(weight.regular, weight.soft, weight.strong, weight.emphasis, weight.figure)
                .map { it.weight }
        )
    }
}
