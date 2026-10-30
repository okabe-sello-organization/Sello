package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Aportar varios looks like on the CI phone (412×914 dp), in Claro and Oscuro, kept as
 * committed images (ADR 0022): the approved mock's goal states.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class GoalBulkScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, state: GoalBulkState, dark: Boolean) {
        val look = Look(dark = dark)
        rule.showSello(look) { GoalBulkScreen(state) {} }
        rule.captureStill(
            "src/test/screenshots/goal-bulk-$name-${if (dark) "oscuro" else "claro"}.png"
        )
    }

    @Test
    fun openingClaro() = capture("opening", GoalBulkSamples.opening, dark = false)

    @Test
    fun openingOscuro() = capture("opening", GoalBulkSamples.opening, dark = true)

    @Test
    fun threeLinesClaro() = capture("three-lines", GoalBulkSamples.three, dark = false)

    @Test
    fun threeLinesOscuro() = capture("three-lines", GoalBulkSamples.three, dark = true)

    @Test
    fun missingTypeClaro() = capture("missing-type", GoalBulkSamples.missing, dark = false)

    @Test
    fun missingTypeOscuro() = capture("missing-type", GoalBulkSamples.missing, dark = true)

    @Test
    fun refusedClaro() = capture("refused", GoalBulkSamples.refused, dark = false)

    @Test
    fun refusedOscuro() = capture("refused", GoalBulkSamples.refused, dark = true)

    @Test
    fun savingClaro() = capture("saving", GoalBulkSamples.saving, dark = false)

    @Test
    fun savingOscuro() = capture("saving", GoalBulkSamples.saving, dark = true)

    @Test
    fun unknownClaro() = capture("unknown", GoalBulkSamples.unknown, dark = false)

    @Test
    fun unknownOscuro() = capture("unknown", GoalBulkSamples.unknown, dark = true)

    @Test
    fun thirtyLinesClaro() = capture("thirty-lines", GoalBulkSamples.thirty, dark = false)

    @Test
    fun thirtyLinesOscuro() = capture("thirty-lines", GoalBulkSamples.thirty, dark = true)
}
