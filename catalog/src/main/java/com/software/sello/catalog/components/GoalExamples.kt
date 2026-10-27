package com.software.sello.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.AmountKeypad
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ChartReference
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.CompletedGoalRow
import com.software.sello.designsystem.component.EntryAmountSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.GoalCard
import com.software.sello.designsystem.component.GoalPill
import com.software.sello.designsystem.component.GoalPinPill
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconPickerCircle
import com.software.sello.designsystem.component.InvestmentRow
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MilestoneRow
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MilestoneState
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MovementRow
import com.software.sello.designsystem.component.PerformanceLine
import com.software.sello.designsystem.component.ReachedRow
import com.software.sello.designsystem.component.RecordedMark
import com.software.sello.designsystem.component.RecordedValuesChart
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TitleBar
import com.software.sello.designsystem.component.investmentShade
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme

internal val GoalPlaces = listOf(
    MilestoneSpec("25 %", "25 por ciento, logrado", MilestoneState.Earned),
    MilestoneSpec("50 %", "50 por ciento, logrado", MilestoneState.Earned),
    MilestoneSpec("75 %", "75 por ciento, por alcanzar", MilestoneState.Empty),
    MilestoneSpec("Meta", "Meta, por alcanzar", MilestoneState.Empty)
)

@Composable
fun MilestonesExample() {
    val strings = LocalResources.current
    var replay by rememberSaveable { mutableIntStateOf(0) }
    var relit by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        ExampleCaption(
            strings.getString(
                R.string.catalog_demo_vacio_logrado_conservado_100_pendiente_de_marcar
            )
        )
        MilestoneRow(
            listOf(
                GoalPlaces[0],
                GoalPlaces[1].copy(
                    state = MilestoneState.Kept,
                    description = strings.getString(
                        R.string.catalog_demo_50_por_ciento_sello_conservado_tras_un
                    )
                ),
                GoalPlaces[2],
                GoalPlaces[3].copy(
                    state = MilestoneState.Ready,
                    description = strings.getString(
                        R.string.catalog_demo_100_por_ciento_pendiente_de_marcar_como
                    )
                )
            )
        )
        FactLine(
            AnnotatedString(
                strings.getString(R.string.catalog_demo_el_sello_del_50_sigue_siendo_tuyo)
            )
        )
        ExampleCaption(
            strings.getString(R.string.catalog_demo_proyecciones_solo_al_crear_o_editar_con)
        )
        Text(strings.getString(R.string.catalog_demo_si_aportas_769_231_cada_mes))
        MilestoneRow(
            GoalPlaces.mapIndexed { index, place ->
                place.copy(
                    caption = listOf(
                        "logrado",
                        "logrado",
                        strings.getString(R.string.catalog_demo_jun_28),
                        strings.getString(R.string.catalog_demo_dic_28)
                    )[index]
                )
            }
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_sellos_centrados))
        MilestoneRow(GoalPlaces, evenly = true)
        key(replay) { MilestoneRow(GoalPlaces, landingIndex = if (replay > 0) 1 else null) }
        SelloButton(strings.getString(R.string.catalog_demo_repetir_aterrizaje), {
            replay++
        }, kind = ButtonKind.Outlined)
        ExampleCaption(strings.getString(R.string.catalog_demo_vuelve_a_encenderse))
        key(relit) {
            MilestoneRow(
                GoalPlaces,
                relighting = if (relit >
                    0
                ) {
                    setOf(1)
                } else {
                    emptySet()
                }
            )
        }
        SelloButton(strings.getString(R.string.catalog_demo_repetir_encendido), {
            relit++
        }, kind = ButtonKind.Outlined)
        ExampleCaption(
            strings.getString(R.string.catalog_demo_con_movimiento_reducido_se_muestra_el_mismo)
        )
    }
}

@Composable
fun GoalPillExample() {
    val strings = LocalResources.current
    var result by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        GoalPill(
            strings.getString(
                R.string.catalog_demo_viaje_a_la_costa
            ),
            strings.getString(
                R.string.catalog_demo_57
            ),
            0.57f,
            SelloIcon.Flight,
            strings.getString(
                R.string.catalog_demo_aportar_a_viaje_a_la_costa
            ),
            {
                result =
                    strings.getString(R.string.catalog_demo_abrir_meta)
            },
            { result = "Aportar" },
            openDescription = strings.getString(R.string.catalog_demo_viaje_57_ver_meta)
        )
        GoalPill(
            strings.getString(R.string.catalog_demo_fondo_de_emergencia),
            strings.getString(R.string.catalog_demo_0),
            0f,
            SelloIcon.AccountBalance,
            strings.getString(R.string.catalog_demo_aportar_a_fondo),
            { result = strings.getString(R.string.catalog_demo_abrir_meta) },
            { result = "Aportar" },
            openDescription = strings.getString(R.string.catalog_demo_fondo_0_ver_meta)
        )
        GoalPill(
            strings.getString(
                R.string.catalog_demo_una_meta_de_nombre_largo_para_probar
            ),
            strings.getString(R.string.catalog_demo_100),
            1f,
            SelloIcon.Home,
            "Aportar",
            {
                result =
                    strings.getString(R.string.catalog_demo_abrir_meta)
            },
            { result = "Aportar" }
        )
        ReachedRow(
            strings.getString(
                R.string.catalog_demo_celular_nuevo
            ),
            strings.getString(
                R.string.catalog_demo_llego_al_100
            ),
            strings.getString(R.string.catalog_demo_marcar_como_lograda),
            {
                result =
                    strings.getString(R.string.catalog_demo_completar_meta)
            }
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_estrecho_boton_debajo))
        ReachedRow(
            strings.getString(R.string.catalog_demo_celular_nuevo),
            strings.getString(R.string.catalog_demo_llego_al_100),
            strings.getString(R.string.catalog_demo_marcar_como_lograda),
            { result = strings.getString(R.string.catalog_demo_completar_meta) },
            Modifier.width(240.dp)
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_marcando))
        ReachedRow(
            strings.getString(R.string.catalog_demo_celular_nuevo),
            strings.getString(R.string.catalog_demo_llego_al_100),
            strings.getString(R.string.catalog_demo_marcar_como_lograda),
            {},
            busy = true
        )
        ExampleCaption(result)
    }
}

@Composable
fun GoalCardsExample() {
    val strings = LocalResources.current
    var result by rememberSaveable { mutableStateOf("") }
    var landed by rememberSaveable { mutableIntStateOf(0) }
    // The reference's goals, highest completion first.
    ExampleStack {
        GoalCard(
            strings.getString(R.string.catalog_demo_viaje_a_la_costa),
            SelloIcon.Flight,
            strings.getString(R.string.catalog_demo_57),
            copFixture(1_710_000),
            strings.getString(R.string.catalog_demo_de_3_000_000),
            "dic 2026",
            0.57f,
            GoalPlaces,
            { result = strings.getString(R.string.catalog_demo_abrir_viaje_a_la_costa) }
        )
        GoalCard(
            "Celular nuevo",
            SelloIcon.PhoneIphone,
            "37 %",
            copFixture(450_000),
            "de 1.200.000",
            strings.getString(R.string.catalog_demo_sin_fecha),
            0.37f,
            placesUpTo(1),
            { result = "Abrir Celular nuevo" },
            flagged = false
        )
        GoalCard(
            "Curso de inglés",
            SelloIcon.School,
            "15 %",
            copFixture(300_000),
            "de 2.000.000",
            "jun 2027",
            0.15f,
            placesUpTo(0),
            { result = "Abrir Curso de inglés" }
        )
        key(landed) {
            CompletedGoalRow(
                strings.getString(R.string.catalog_demo_vacunas_de_luna),
                strings.getString(R.string.catalog_demo_400_000_30_sep),
                SelloIcon.Pets,
                "Lograda",
                {
                    result =
                        strings.getString(R.string.catalog_demo_abrir_meta_lograda_solo_lectura)
                },
                land = landed > 0
            )
        }
        SelloButton(strings.getString(R.string.catalog_demo_repetir_lograda), {
            landed++
        }, kind = ButtonKind.Outlined)
        ExampleCaption(result)
    }
}

/** The four places with the first [earned] marked as reached. */
private fun placesUpTo(earned: Int) = GoalPlaces.mapIndexed { index, place ->
    if (index < earned) {
        place.copy(state = MilestoneState.Earned, description = "${place.label}, logrado")
    } else {
        place.copy(state = MilestoneState.Empty, description = "${place.label}, por alcanzar")
    }
}

@Composable
fun MovementsExample() {
    val strings = LocalResources.current
    var withdrawal by rememberSaveable { mutableStateOf(false) }
    ExampleStack {
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            MovementRow(
                "Aporte",
                strings.getString(R.string.catalog_demo_vie_2_oct),
                copFixture(350_000, signed = true),
                milestone = GoalPlaces[1],
                annotation = strings.getString(R.string.catalog_demo_ganaste_el_sello_del_50)
            )
            MovementRow(
                "Retiro",
                strings.getString(R.string.catalog_demo_lun_20_oct_arreglo_del_carro),
                copFixture(-400_000),
                icon = SelloIcon.ArrowBack
            )
        }
        SelloChip("Retirar", withdrawal, { withdrawal = !withdrawal })
        EntryAmountSlip(
            if (withdrawal) {
                strings.getString(
                    R.string.catalog_demo_retiro_en_pesos
                )
            } else {
                strings.getString(R.string.catalog_demo_aporte_en_pesos)
            },
            copFixture(if (withdrawal) -500_000 else 500_000, signed = true),
            icon = SelloIcon.Flight,
            tone = if (withdrawal) ButtonTone.Ink else ButtonTone.Gain
        )
        FactLine(
            AnnotatedString(
                strings.getString(R.string.catalog_demo_llevas_1_710_000_guardados_en_esta)
            )
        )
        EntryAmountSlip(
            strings.getString(R.string.catalog_demo_retiro_en_pesos),
            copFixture(-2_000_000),
            error = strings.getString(R.string.catalog_demo_mas_de_lo_guardado_1_710_000)
        )
        SelloButton(strings.getString(R.string.catalog_demo_registrar_retiro), {
        }, enabled = false, tone = ButtonTone.Ink)
    }
}

@Composable
fun InvestmentsExample() {
    val strings = LocalResources.current
    var result by rememberSaveable { mutableStateOf("") }
    val colors = SelloTheme.colors
    ExampleStack {
        Slip(Modifier.fillMaxWidth()) {
            PerformanceLine(
                strings.getString(R.string.catalog_demo_5_6),
                strings.getString(R.string.catalog_demo_2_066_000_de_ganancia),
                false
            )
            PerformanceLine(
                strings.getString(R.string.catalog_demo_4_3),
                strings.getString(R.string.catalog_demo_258_000_de_perdida),
                true
            )
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            SplitBar(
                listOf(
                    SplitSegment(
                        0.7f,
                        strings.getString(R.string.catalog_demo_parqueadero_19_000_000_pesos),
                        investmentShade(0)
                    ),
                    SplitSegment(
                        0.3f,
                        strings.getString(R.string.catalog_demo_fondo_8_000_000_pesos),
                        investmentShade(1)
                    )
                )
            )
            InvestmentRow(
                "Parqueadero",
                strings.getString(R.string.catalog_demo_finca_raiz_valor_del_12_jun),
                SelloIcon.Home,
                investmentShade(0),
                copFixture(
                    19_000_000
                ),
                strings.getString(R.string.catalog_demo_sin_valor),
                strings.getString(R.string.catalog_demo_7_5),
                {
                    result =
                        "Parqueadero"
                }
            )
            InvestmentRow(
                "Cripto",
                strings.getString(
                    R.string.catalog_demo_criptomoneda_sin_valor_anotado
                ),
                SelloIcon.Insights,
                colors.inkSoft,
                null,
                strings.getString(
                    R.string.catalog_demo_sin_valor
                ),
                strings.getString(R.string.catalog_demo_aportaste_2_000_000),
                {
                    result =
                        "Cripto"
                },
                divider = false
            )
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_sin_valor_se_omite_de_la_barra))
        ExampleCaption(result)
    }
}

@Composable
fun ReciboPinExample() {
    val strings = LocalResources.current
    var pinned by rememberSaveable { mutableStateOf(false) }
    ExampleStack {
        Column(Modifier.fillMaxWidth().background(SelloTheme.colors.band).padding(12.dp)) {
            GoalPinPill(
                if (pinned) {
                    strings.getString(
                        R.string.catalog_demo_en_tu_recibo
                    )
                } else {
                    strings.getString(R.string.catalog_demo_mostrar_en_recibo)
                },
                pinned,
                {
                    pinned =
                        !pinned
                }
            )
        }
    }
}

@Composable
fun SplitChartsExample() {
    val strings = LocalResources.current
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var recorded by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = SelloTheme.colors
    val months = listOf(
        ChartPoint(
            "ago",
            "ago",
            750_000,
            strings.getString(R.string.catalog_demo_agosto_750_000_pesos)
        ),
        ChartPoint(
            "sep",
            "sep",
            920_000,
            strings.getString(R.string.catalog_demo_septiembre_920_000_pesos),
            valueLabel = strings.getString(R.string.catalog_demo_920_mil)
        ),
        ChartPoint(
            "oct",
            "oct*",
            400_000,
            strings.getString(R.string.catalog_demo_octubre_400_000_pesos_mes_en_curso),
            ChartPeriod.Current,
            strings.getString(R.string.catalog_demo_400_mil)
        )
    )
    ExampleStack {
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Text(strings.getString(R.string.catalog_demo_entro_en_octubre))
            SplitBar(
                listOf(
                    SplitSegment(
                        0.75f,
                        strings.getString(R.string.catalog_demo_en_presupuestos_1_500_000_pesos),
                        colors.brand
                    ),
                    SplitSegment(
                        0.25f,
                        strings.getString(R.string.catalog_demo_sin_asignar_500_000_pesos),
                        colors.brand,
                        striped = true
                    )
                )
            )
            LeaderLine(strings.getString(R.string.catalog_demo_en_presupuestos_de_categorias)) {
                MoneyText(copFixture(1_500_000))
            }
            LeaderLine(strings.getString(R.string.catalog_demo_sin_asignar)) {
                MoneyText(copFixture(500_000))
            }
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Text(strings.getString(R.string.catalog_demo_mes_a_mes_un_mes_normal_835))
            ColumnsChart(
                months,
                strings.getString(
                    R.string.catalog_demo_gastos_mensuales_octubre_esta_en_curso
                ),
                selectedId = picked,
                onSelect = {
                    picked =
                        it
                },
                reference = ChartReference(
                    1_000_000,
                    strings.getString(R.string.catalog_demo_limite_1_m)
                ),
                stripedCurrent = true
            )
            picked?.let { id ->
                Text(months.first { it.id == id }.spoken, Modifier.testTag("chart:selected"))
            }
            ExampleCaption(strings.getString(R.string.catalog_demo_octubre_va_hasta_el_22_no_se))
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Text(strings.getString(R.string.catalog_demo_que_dias_gastas_mas_los_sabados))
            ExampleCaption(
                strings.getString(R.string.catalog_demo_42_700_en_promedio_ultimos_4_meses)
            )
            ColumnsChart(
                listOf(
                    ChartPoint(
                        "vie",
                        "vie",
                        20_000,
                        strings.getString(R.string.catalog_demo_viernes_20_000_pesos_en_promedio)
                    ),
                    ChartPoint(
                        "sab",
                        strings.getString(R.string.catalog_demo_sab),
                        42_700,
                        strings.getString(R.string.catalog_demo_sabado_42_700_pesos_en_promedio),
                        ChartPeriod.Current,
                        strings.getString(R.string.catalog_demo_42_7_mil)
                    )
                ),
                strings.getString(R.string.catalog_demo_gasto_medio_por_dia_de_la_semana)
            )
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Text(strings.getString(R.string.catalog_demo_valores_anotados))
            RecordedValuesChart(
                listOf(
                    RecordedMark(
                        "jun",
                        0f,
                        0.45f,
                        strings.getString(R.string.catalog_demo_12_jun_4_000_000_pesos)
                    ),
                    RecordedMark(
                        "sep",
                        0.65f,
                        0.72f,
                        strings.getString(R.string.catalog_demo_9_sep_5_242_000_pesos)
                    ),
                    RecordedMark(
                        "oct",
                        1f,
                        0.68f,
                        strings.getString(R.string.catalog_demo_14_oct_5_000_000_pesos)
                    )
                ),
                listOf(
                    RecordedMark("a", 0f, 0.4f, ""),
                    RecordedMark("b", 0.4f, 0.6f, ""),
                    RecordedMark("c", 1f, 0.6f, "")
                ),
                strings.getString(
                    R.string.catalog_demo_valores_registrados_y_aportes_menos_retiros_en
                ),
                recorded,
                {
                    recorded =
                        it
                }
            )
            recorded?.let { Text("Valor seleccionado: $it") }
        }
    }
}

/**
 * Nueva meta's parts: the icon circle, empty and chosen, then Cuánto necesitas edged as
 * the figure the Sello pad types into, and the pad, which stays under the form.
 */
@Composable
fun GoalFormExample() {
    val strings = LocalResources.current
    var digits by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconPickerCircle(null, "Elegir ícono", "Elegir ícono", {}, tone = ButtonTone.Gain)
            IconPickerCircle(
                SelloIcon.Flight,
                "Cambiar",
                "Ícono: Viaje. Cambiar",
                {},
                tone = ButtonTone.Gain
            )
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_sin_icono))
        EntryAmountSlip(
            strings.getString(R.string.catalog_demo_cuanto_necesitas),
            digits.toLongOrNull()?.let(::copFixture),
            active = true
        )
        AmountKeypad(
            onDigit = { if (digits.length < 12) digits = (digits + it).trimStart('0') },
            onTripleZero = { if (digits.isNotEmpty() && digits.length <= 9) digits += "000" },
            onBackspace = { digits = digits.dropLast(1) },
            onClearAll = { digits = "" },
            compact = true
        )
    }
}

@Composable
fun GoalsBandExample() {
    val strings = LocalResources.current
    ExampleStack {
        // Metas' header: + Nueva meta beside the title, then the total as the hero card.
        TitleBar("Metas", chip = { compact ->
            BandChip(strings.getString(R.string.catalog_demo_nueva_meta), {}, compact = compact)
        })
        AnswerSlip(
            strings.getString(R.string.catalog_demo_guardado_en_metas),
            copFixture(2_760_000),
            stamp = null,
            ink = StampInk.Good
        ) {
            Text(
                strings.getString(R.string.catalog_demo_mas_2_010_000_en_octubre),
                color = SelloTheme.colors.semantic.gain
            )
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_sin_aportes_este_mes))
        AnswerSlip(
            strings.getString(R.string.catalog_demo_guardado_en_metas),
            copFixture(2_760_000),
            stamp = null,
            ink = StampInk.Good
        )
    }
}
