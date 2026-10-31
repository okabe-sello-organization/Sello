package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.showSello
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Aportar a una meta looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalMovementScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 23)

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val aporte = GoalMovementState(
        loading = false,
        amount = plain("350.000"),
        goals = listOf(
            GoalChoice("a", "Viaje a la costa", "flight", 57, plain("1.710.000")),
            GoalChoice("b", "Celular nuevo", "phone_iphone", 37, plain("450.000"))
        ),
        goalId = "a",
        date = today,
        today = today
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            GoalMovementScreen(aporte, severalOffered = true) {}
        }
        rule.captureStill("src/test/screenshots/goal-movement-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
