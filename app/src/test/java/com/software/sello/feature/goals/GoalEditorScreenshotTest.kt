package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.GoalPlace
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What Nueva meta looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "+xhdpi")
class GoalEditorScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    /** A plan with its four places: the form with everything filled, ready to save. */
    private val planned = GoalEditorState(
        loading = false,
        name = "Viaje a la costa",
        nameLength = 16,
        iconKey = "flight",
        amount = plain("6.000.000"),
        targetMonth = YearMonth.of(2027, 12),
        pace = PlanPace(plain("461.538"), 13),
        places = listOf(
            YearMonth.of(2027, 3),
            YearMonth.of(2027, 6),
            YearMonth.of(2027, 9),
            YearMonth.of(2027, 12)
        ).zip(GoalPlace.entries).map { (month, place) ->
            PlannedPlace(PlaceModel(place, PlaceLook.Empty, null), PlanCaption.Month(month))
        }
    )

    private fun capture(look: Look) {
        rule.showSello(look) {
            GoalEditorScreen(planned) {}
        }
        rule.onRoot().captureRoboImage("src/test/screenshots/goal-editor-${look.label}.png")
    }

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDarkAtTwiceTheFontSize() = capture(Look(dark = true, fontScale = 2f))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))
}
