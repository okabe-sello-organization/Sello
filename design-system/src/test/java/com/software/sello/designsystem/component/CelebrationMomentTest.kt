package com.software.sello.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** A big moment is decoration: inputs that do not match degrade it, never crash it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CelebrationMomentTest {
    @get:Rule
    val rule = createComposeRule()

    private val stamps = listOf("A", "B", "C").map {
        CelebrationStamp(MilestoneSpec(it, "Sello $it", MilestoneState.Earned))
    }

    private fun show(landAtMillis: List<Int>, reduced: Boolean) {
        rule.setContent {
            SelloTheme(reducedMotion = reduced) {
                val colors = SelloTheme.colors
                CelebrationMoment(
                    title = "¡Listo!",
                    stamps = stamps,
                    landAtMillis = landAtMillis,
                    flood = colors.band,
                    onFlood = colors.onBand,
                    // Fewer colours than five, and none at all, are both taken in turn.
                    chadColors = if (reduced) emptyList() else listOf(colors.onBand),
                    actionTone = ButtonTone.OnBand,
                    primary = CelebrationAction("Ver", {}),
                    secondary = CelebrationAction("Cerrar", {}),
                    onDismiss = {}
                ) {}
            }
        }
    }

    @Test
    fun aStampWithoutItsOwnTimeLandsWithTheLastOne() {
        rule.mainClock.autoAdvance = false
        show(landAtMillis = listOf(300), reduced = false)

        rule.mainClock.advanceTimeBy(3_500)

        stamps.forEach { rule.onNodeWithContentDescription(it.spec.description).assertExists() }
        rule.onNodeWithText("Ver").assertExists()
    }

    @Test
    fun noTimesAndNoColoursStillOpenOnTheFinalScreen() {
        show(landAtMillis = emptyList(), reduced = true)

        stamps.forEach { rule.onNodeWithContentDescription(it.spec.description).assertExists() }
        rule.onNodeWithText("¡Listo!").assertExists()
    }
}
