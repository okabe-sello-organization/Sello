package com.software.sello

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.goals.CompletedGoalRow
import com.software.sello.feature.goals.GoalCard
import com.software.sello.feature.investments.InvestmentRow
import com.software.sello.feature.investments.PerformanceLine
import com.software.sello.feature.recibo.CategoryCell
import com.software.sello.feature.recibo.CategoryGrid
import com.software.sello.navigation.MonthBar
import com.software.sello.navigation.TitleBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** What the components that belong to one feature look like, kept as committed images (ADR 0022): the groups the design system's own captures held before the components moved. Each is in Cobalto light at the normal font size, as before; the host gate compares it with its baseline in `src/test/screenshots/`. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-h1200dp-mdpi")
class FeatureComponentsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun pesos(digits: String, minus: Boolean = false) = MoneyTextValue(
        if (minus) MoneySign.Minus else MoneySign.None,
        "",
        digits,
        "$digits pesos"
    )

    /** [content] on the desk, 320 dp wide and as tall as it needs, captured as [name]. */
    private fun capture(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                Box(
                    Modifier.requiredWidth(
                        320.dp
                    ).background(SelloTheme.colors.desk).testTag("stage")
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        content()
                    }
                }
            }
        }
        rule.onNodeWithTag(
            "stage"
        ).drawnBitmap().captureBaseline("src/test/screenshots/components-$name.png")
    }

    @Test
    fun categories() = capture("categories") {
        CategoryGrid(listOf("left", "over", "free")) { _, id ->
            when (id) {
                "left" -> CategoryCell(
                    "Alimentación",
                    SelloIcon.Restaurant,
                    pesos("287.600"),
                    0.68f,
                    "Alimentación. Quedan 287.600 pesos de 900.000. 68 por ciento usado.",
                    {}
                )

                "over" -> CategoryCell(
                    "Entretenimiento",
                    SelloIcon.Theaters,
                    pesos("120.000", minus = true),
                    1.8f,
                    "Entretenimiento. Pasado por 120.000 pesos de 150.000.",
                    {},
                    over = true
                )

                else -> CategoryCell(
                    "Transporte",
                    SelloIcon.DirectionsBus,
                    pesos("86.000"),
                    null,
                    "Transporte. Gastado 86.000 pesos. Sin límite.",
                    {},
                    amountCaption = "gastado"
                )
            }
        }
    }

    @Test
    fun goalsAndInvestments() = capture("goals-and-investments") {
        val places = listOf("25 %", "50 %", "75 %", "Meta").mapIndexed { index, label ->
            MilestoneSpec(
                label,
                "$label, por alcanzar",
                if (index == 0) MilestoneState.Earned else MilestoneState.Empty
            )
        }
        GoalCard(
            "Curso de inglés", SelloIcon.School, "15 %", pesos("300.000"), "de 2.000.000",
            "jun 2027", 0.15f, places, {}
        )
        CompletedGoalRow("Vacunas de Luna", "400.000, 30 sep", SelloIcon.Pets, "Lograda", {})
        InvestmentRow(
            "Parqueadero",
            "Finca raíz · valor del 12 jun",
            SelloIcon.Home,
            SelloTheme.colors.brand,
            pesos("19.000.000"),
            "Sin valor",
            "+7,5 %",
            {}
        )
        PerformanceLine("+5,6 %", "+2.066.000 de ganancia", false)
        PerformanceLine("-3,1 %", "-120.000 de pérdida", true)
    }

    @Test
    fun barsAndKeypad() = capture("bars-and-keypad") {
        TitleBar("Inversiones")
        MonthBar("Octubre", "2026", {})
        AmountKeypad({}, {}, {}, {})
    }
}
