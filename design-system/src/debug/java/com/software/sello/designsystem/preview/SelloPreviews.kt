package com.software.sello.designsystem.preview

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CarryBreakdown
import com.software.sello.designsystem.component.CarryLine
import com.software.sello.designsystem.component.ChangeRow
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StampSize
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Light and dark: the two looks a component has to survive. The theme follows the
 * preview's night mode. Debug only; nothing here ships.
 */
@Preview(name = "Claro", locale = "es", widthDp = 360, showBackground = true)
@Preview(name = "Oscuro", locale = "es", widthDp = 360, uiMode = UI_MODE_NIGHT_YES)
annotation class SelloPreviews

/** The desk the components sit on, in the theme the preview asks for. */
@Composable
private fun Stage(content: @Composable () -> Unit) {
    SelloTheme(reducedMotion = true) {
        Column(
            Modifier.background(SelloTheme.colors.desk).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) { content() }
    }
}

private fun pesos(digits: String, minus: Boolean = false) = MoneyTextValue(
    if (minus) MoneySign.Minus else MoneySign.None,
    "",
    digits,
    "$digits pesos"
)

@SelloPreviews
@Composable
private fun ButtonsAndChipsPreview() = Stage {
    SelloButton("Guardar", {})
    SelloButton("Guardando", {}, loading = true)
    SelloButton("Escribe un monto", {}, enabled = false)
    SelloButton("Aportar a una meta", {}, tone = ButtonTone.Gain, icon = SelloIcon.Flag)
    SelloButton("Borrar todo", {}, tone = ButtonTone.Loss)
    SelloButton("Archivar", {}, kind = ButtonKind.Text)
    ChipFlow {
        SelloChip("Café", selected = true, onClick = {})
        SelloChip("Transporte", selected = false, onClick = {})
    }
    FieldPill("Hoy, jue 22", SelloIcon.Event, {})
}

@SelloPreviews
@Composable
private fun MoneyAndStampsPreview() = Stage {
    MoneyText(pesos("937.200"), style = MoneyStyle.Hero)
    MoneyText(pesos("48.700"), style = MoneyStyle.Total)
    MoneyText(pesos("18.400", minus = true), style = MoneyStyle.Card)
    Stamp("Al día", StampInk.Good)
    Stamp("Pasado", StampInk.Over, size = StampSize.Large)
    Stamp("Revisar", StampInk.Warn)
}

@SelloPreviews
@Composable
private fun PaperAndLinesPreview() = Stage {
    AnswerSlip("Te quedan", pesos("287.600"), "Al día", StampInk.Good)
    Slip {
        LeaderLine("Uber", "18.400", caption = "Transporte")
        LeaderLine("Fecha", "jue 22 oct")
        TearLine()
        TotalLine("Total") { MoneyText(pesos("126.000"), style = MoneyStyle.Title) }
    }
}

@SelloPreviews
@Composable
private fun FeedbackPreview() = Stage {
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

@SelloPreviews
@Composable
private fun ChartsPreview() = Stage {
    ColumnsChart(
        listOf(
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
        ),
        "Gasto por mes. El más alto es agosto."
    )
}

@SelloPreviews
@Composable
private fun EntryControlsPreview() = Stage {
    FormField("Nombre", "Mercado", {})
    SegmentedSwitch(listOf("Sistema", "Claro", "Oscuro"), 0, {})
    Text("Teclado de montos", color = SelloTheme.colors.inkSoft)
    AmountKeypad({}, {}, {}, {})
}

@SelloPreviews
@Composable
private fun CarryPreview() = Stage {
    CarryBreakdown(
        listOf(
            CarryLine("Límite base", pesos("100.000")),
            CarryLine(
                "Arrastre",
                pesos("20.000", minus = true),
                "Te pasaste 20.000 en meses anteriores"
            ),
            CarryLine("Límite con arrastre", pesos("80.000"), emphasis = LineEmphasis.Strong),
            CarryLine("Gastado", pesos("30.000"))
        ),
        footnote = "Viene del restante de ene 2027 · arrastras desde ene 2027"
    )
    ChangeRow(
        "feb 2027",
        "180.000 → 200.000 (+20.000)",
        "feb 2027: límite con arrastre de 180.000 a 200.000, más 20.000.",
        caption = "Restante 110.000 → 130.000"
    )
}
