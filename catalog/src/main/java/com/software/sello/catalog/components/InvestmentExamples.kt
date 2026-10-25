package com.software.sello.catalog.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.InvestmentRow
import com.software.sello.designsystem.component.PerformanceLine
import com.software.sello.designsystem.component.RecordedMark
import com.software.sello.designsystem.component.RecordedValuesChart
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.investmentShade
import com.software.sello.designsystem.icon.SelloIcon

/**
 * The investment parts SELLO-109 to SELLO-111 extended (docs/design/v2/gaps/inversiones.md):
 * Pierde with the value in ink, an even return, six investments with repeating shades and
 * a gap, rows that open nothing yet, the values chart's months and legend, the comma pad
 * and the ink dock button. Synthetic figures from the investment examples.
 */
@Composable
fun InvestmentStatesExample() {
    val strings = LocalResources.current
    var result by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        AnswerSlip(
            label = strings.getString(R.string.catalog_demo_tus_inversiones_valen),
            amount = copFixture(8_292_000),
            stamp = strings.getString(R.string.catalog_demo_pierde),
            ink = StampInk.Over,
            lossAmount = false
        ) {
            PerformanceLine(
                strings.getString(R.string.catalog_demo_0_3),
                strings.getString(R.string.catalog_demo_30_000_de_perdida),
                loss = true
            )
            PerformanceLine(
                strings.getString(R.string.catalog_demo_0_0),
                strings.getString(R.string.catalog_demo_sin_ganancia_ni_perdida),
                loss = false,
                even = true
            )
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_pierde_value_in_ink))
        val six = listOf(
            "Parqueadero" to 19_000_000L,
            "CDT Banco" to 10_746_000L,
            "Fondo de acciones" to 5_242_000L,
            "Ecopetrol" to 3_050_000L,
            "Bonos" to 2_400_000L,
            "Local" to 1_900_000L
        )
        val total = six.sumOf { it.second }.toFloat()
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            SplitBar(
                six.mapIndexed { rank, (name, pesos) ->
                    SplitSegment(pesos / total, name, investmentShade(rank))
                },
                gap = 2.dp
            )
            six.forEachIndexed { rank, (name, pesos) ->
                InvestmentRow(
                    name = name,
                    detail = strings.getString(R.string.catalog_demo_finca_raiz_valor_del_12_jun),
                    icon = SelloIcon.Insights,
                    shade = investmentShade(rank),
                    value = copFixture(pesos),
                    missingLabel = strings.getString(R.string.catalog_demo_sin_valor),
                    secondary = strings.getString(R.string.catalog_demo_7_5),
                    onOpen = null,
                    divider = rank < six.lastIndex
                )
            }
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_six_shades))
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            RecordedValuesChart(
                values = listOf(
                    RecordedMark(
                        "may",
                        0f,
                        0.5f,
                        strings.getString(R.string.catalog_demo_3_may_2025_3_000_000)
                    ),
                    RecordedMark(
                        "oct",
                        1f,
                        0.87f,
                        strings.getString(R.string.catalog_demo_14_oct_2026_5_242_000)
                    )
                ),
                contributions = listOf(
                    RecordedMark("a", 0f, 0.5f, ""),
                    RecordedMark("b", 0.3f, 1f, ""),
                    RecordedMark("c", 0.75f, 0.92f, "")
                ),
                summary = strings.getString(R.string.catalog_demo_valores_de_may_a_oct),
                selectedId = result.takeIf { it == "may" || it == "oct" },
                onSelect = { result = it },
                axis = strings.getString(R.string.catalog_demo_may_25) to
                    strings.getString(R.string.catalog_demo_oct_26),
                legend = strings.getString(R.string.catalog_demo_valor_anotado) to
                    strings.getString(R.string.catalog_demo_lo_que_pusiste)
            )
        }
        AmountKeypad(
            onDigit = { result = it.toString() },
            onTripleZero = {},
            onBackspace = {},
            onClearAll = {},
            onComma = { result = "," }
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_comma_pad))
        SelloButton(
            strings.getString(R.string.catalog_demo_anotar_movimiento),
            { result = "dock" },
            Modifier.fillMaxWidth(),
            icon = SelloIcon.Add,
            tone = ButtonTone.Ink
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_pressed, result))
    }
}
