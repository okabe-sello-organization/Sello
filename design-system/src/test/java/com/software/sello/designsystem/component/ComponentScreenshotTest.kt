package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the components look like, kept as committed images (ADR 0022): one per group of
 * components, in Cobalto light at the normal font size. The host gate compares each
 * capture with its baseline in `src/test/screenshots/`; a visual change re-records them
 * in the same pull request, and the image diff is how the change is reviewed. The
 * screens' own captures, in the other inks and at twice the font size, are the app's.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A screen taller than every group, so each is drawn whole.
@Config(qualifiers = "w320dp-h1200dp-mdpi")
class ComponentScreenshotTest {
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
    fun buttonsAndChips() = capture("buttons-and-chips") {
        SelloButton("Guardar", {})
        SelloButton("Guardando", {}, loading = true)
        SelloButton("Escribe un monto", {}, enabled = false)
        SelloButton("Aportar a una meta", {}, tone = ButtonTone.Gain, icon = SelloIcon.Flag)
        SelloButton("Archivar", {}, kind = ButtonKind.Text)
        ChipFlow {
            SelloChip("Café", selected = true, onClick = {})
            SelloChip("Transporte", selected = false, onClick = {})
            SelloChip("Archivada", selected = false, onClick = {}, enabled = false)
            AddChip("Nueva", {})
        }
        FieldPill("Hoy, jue 22", SelloIcon.Event, {})
    }

    @Test
    fun moneyAndStamps() = capture("money-and-stamps") {
        MoneyText(pesos("937.200"), style = MoneyStyle.Hero)
        MoneyText(pesos("48.700"), style = MoneyStyle.Total)
        MoneyText(pesos("18.400", minus = true), style = MoneyStyle.Card)
        MoneyText(pesos("2.000.000"))
        Stamp("Al día", StampInk.Good)
        Stamp("Pasado", StampInk.Over, size = StampSize.Large)
        Stamp("Revisar", StampInk.Warn)
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
    fun feedback() = capture("feedback") {
        EmptySlip("Dale forma a tu mes", "Crea una categoría.", SelloIcon.ReceiptLong)
        ErrorSlip(
            "Tus registros siguen guardados.",
            {},
            title = "No pudimos leer tu mes",
            icon = SelloIcon.Error
        )
        SelloNotice("Gasto borrado", actionLabel = "Deshacer", onAction = {})
        SlipSkeleton()
    }

    @Test
    fun charts() = capture("charts") {
        val points = listOf(
            ChartPoint("jul", "jul", 640_000, "julio, 640.000 pesos"),
            ChartPoint("ago", "ago", 910_000, "agosto, 910.000 pesos"),
            ChartPoint("sep", "sep", 780_000, "septiembre, 780.000 pesos"),
            ChartPoint(
                "oct",
                "oct",
                450_000,
                "octubre, 450.000 pesos",
                ChartPeriod.Current,
                "450.000"
            )
        )
        ColumnsChart(points, "Gasto por mes. El más alto es agosto.")
    }

    @Test
    fun barsAndKeypad() = capture("bars-and-keypad") {
        TitleBar("Inversiones")
        MonthBar("Octubre", "2026", {})
        AmountKeypad({}, {}, {}, {})
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun frame() {
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                Box(Modifier.fillMaxSize().testTag("stage")) {
                    SelloScaffold(
                        title = "Octubre 2026",
                        navigation = SelloNavigation(
                            listOf(
                                SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                                SelloDestination("resumen", "Resumen", SelloIcon.Insights)
                            ),
                            "recibo"
                        ) {},
                        dock = SelloDock("Anotar un gasto", "Anotar", {}),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding)) {
                            EmptySlip("Aún no hay gastos", "Anota el primero.")
                        }
                    }
                }
            }
        }
        rule.onNodeWithTag(
            "stage"
        ).drawnBitmap().captureBaseline("src/test/screenshots/components-frame.png")
    }
}
