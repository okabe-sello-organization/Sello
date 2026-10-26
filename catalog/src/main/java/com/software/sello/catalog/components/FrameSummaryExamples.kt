package com.software.sello.catalog.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CategoryCell
import com.software.sello.designsystem.component.CategoryGrid
import com.software.sello.designsystem.component.ChipRows
import com.software.sello.designsystem.component.ComparisonBar
import com.software.sello.designsystem.component.ComparisonSlip
import com.software.sello.designsystem.component.EntryDock
import com.software.sello.designsystem.component.ExpectedChip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.Footnote
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.LimitRow
import com.software.sello.designsystem.component.LineEmphasis
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MonthBar
import com.software.sello.designsystem.component.PastMonthRibbon
import com.software.sello.designsystem.component.PlanSlip
import com.software.sello.designsystem.component.ReviewInvitation
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.component.SelloNavigationBar
import com.software.sello.designsystem.component.SelloNavigationRail
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipStub
import com.software.sello.designsystem.component.SpendingRankRow
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StubButton
import com.software.sello.designsystem.component.SuggestionNote
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.TitleBar
import com.software.sello.designsystem.component.TotalLine
import com.software.sello.designsystem.component.TotalRule
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

@Composable
fun EntryDockExample() {
    val strings = LocalResources.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        EntryDock("Anotar", { taps++ })
        EntryDock(strings.getString(R.string.catalog_demo_anotar_ingreso), { taps++ })
        EntryDock(strings.getString(R.string.catalog_demo_aportar_a_una_meta), {
            taps++
        }, tone = ButtonTone.Gain, icon = SelloIcon.Flag)
        EntryDock(strings.getString(R.string.catalog_demo_anotar_movimiento), {
            taps++
        }, tone = ButtonTone.Ink)
        EntryDock(strings.getString(R.string.catalog_demo_crear_una_meta), {
            taps++
        }, tone = ButtonTone.Gain, icon = SelloIcon.Flag)
        EntryDock(strings.getString(R.string.catalog_demo_elige_una_meta), {
        }, tone = ButtonTone.Gain, enabled = false)
        EntryDock(strings.getString(R.string.catalog_demo_guardando), {}, busy = true)
        ExampleCaption("Acciones: $taps")
    }
}

private val Tabs = listOf(
    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
    SelloDestination("resumen", "Resumen", SelloIcon.Insights),
    SelloDestination("metas", "Metas", SelloIcon.Flag),
    SelloDestination("inversiones", "Inversiones", SelloIcon.AccountBalance)
)

@Composable
fun NavigationBarExample() {
    val strings = LocalResources.current
    var selected by rememberSaveable { mutableStateOf("recibo") }
    ExampleStack {
        for (count in 2..4) {
            ExampleCaption("$count destinos · ${Tabs.firstOrNull { it.id == selected }?.label}")
            SelloNavigationBar(
                SelloNavigation(
                    Tabs.take(count),
                    if (Tabs.take(count).any {
                            it.id ==
                                selected
                        }
                    ) {
                        selected
                    } else {
                        "recibo"
                    },
                    { selected = it }
                ),
                Modifier.testTag("navigation:$count")
            )
        }
        ExampleCaption(
            strings.getString(
                R.string.catalog_demo_riel_existente_para_ventanas_amplias_la_integracion
            )
        )
        SelloNavigationRail(
            SelloNavigation(Tabs, selected, {
                selected = it
            }),
            modifier = Modifier.height(340.dp),
            dock = SelloDock("Anotar", "Anotar", {})
        )
    }
}

@Composable
fun MonthBarExample() {
    val strings = LocalResources.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        MonthBar("Octubre", "2026", { taps++ }, description = "Cambiar mes, octubre 2026") {
            SettingsAction()
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_metas_suma_crear_meta))
        MonthBar("Octubre", "2026", { taps++ }, description = "Cambiar mes, octubre 2026") {
            IconButton({}) { Icon(SelloIcon.Add.painter(), "Crear meta") }
            SettingsAction()
        }
        ExampleCaption("Abrir selector: $taps")
        ExampleCaption("Una pestaña con título en vez de mes, como Metas")
        TitleBar("Metas") {
            IconButton({}) { Icon(SelloIcon.Add.painter(), "Crear meta") }
            SettingsAction()
        }
    }
}

@Composable
private fun SettingsAction() {
    IconButton({}) { Icon(SelloIcon.Settings.painter(), "Configuración") }
}

@Composable
fun PastMonthRibbonExample() {
    val strings = LocalResources.current
    var past by rememberSaveable { mutableStateOf(true) }
    ExampleStack {
        // The ribbon lives on the band under the month bar, as on Recibo and Resumen.
        Column(Modifier.background(SelloTheme.colors.band).padding(bottom = 10.dp)) {
            MonthBar(
                if (past) "Septiembre" else "Octubre",
                "2026",
                {},
                description = "Cambiar mes"
            ) { SettingsAction() }
            if (past) {
                PastMonthRibbon(
                    buildAnnotatedString {
                        append("Estás viendo ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("septiembre") }
                    },
                    strings.getString(R.string.catalog_demo_volver_a_octubre),
                    { past = false },
                    Modifier.padding(horizontal = 12.dp).testTag("ribbon")
                )
            }
        }
        if (!past) ExampleCaption(strings.getString(R.string.catalog_demo_octubre_mes_actual))
        SelloButton(strings.getString(R.string.catalog_demo_ver_septiembre), {
            past = true
        }, kind = ButtonKind.Outlined)
    }
}

@Composable
fun FootnotesExample() {
    val strings = LocalResources.current
    var opened by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        Slip(Modifier.fillMaxWidth()) {
            Footnote(
                AnnotatedString(
                    strings.getString(R.string.catalog_demo_incluye_200_000_separado_para_1_pago)
                ),
                {
                    opened =
                        strings.getString(R.string.catalog_demo_pagos_previstos)
                }
            )
            Footnote(
                AnnotatedString(
                    strings.getString(R.string.catalog_demo_valores_anotados_entre_el_12_jun_y)
                ),
                {
                    opened =
                        strings.getString(R.string.catalog_demo_fechas_de_valoracion)
                }
            )
        }
        FactLine(
            AnnotatedString(
                strings.getString(R.string.catalog_demo_llevas_1_710_000_guardados_en_esta)
            )
        )
        ReviewInvitation(strings.getString(R.string.catalog_demo_ver_como_cerraste_octubre), {
            opened =
                strings.getString(R.string.catalog_demo_resumen_de_octubre)
        })
        ExampleCaption(opened)
    }
}

@Composable
fun AnswerSlipExample() {
    val strings = LocalResources.current
    var state by rememberSaveable { mutableIntStateOf(0) }
    val labels =
        listOf(
            "Actual",
            strings.getString(R.string.catalog_demo_al_limite),
            "Excedido",
            strings.getString(R.string.catalog_demo_sin_margen),
            "Revisar",
            strings.getString(R.string.catalog_demo_sin_limite),
            "Cerrado",
            strings.getString(R.string.catalog_demo_revisar_pasado)
        )
    ExampleStack {
        ChipRows(labels.size) { index ->
            SelloChip(labels[index], state == index, { state = index })
        }
        val stamp = listOf(
            strings.getString(R.string.catalog_demo_al_dia),
            strings.getString(R.string.catalog_demo_al_limite),
            "Excedido",
            strings.getString(R.string.catalog_demo_sin_margen),
            "Revisar",
            strings.getString(R.string.catalog_demo_sin_limite),
            "Cumplido",
            "Revisar"
        )[state]
        val ink = when (state) {
            0, 6 -> StampInk.Good
            2 -> StampInk.Over
            4, 7 -> StampInk.Warn
            else -> StampInk.Neutral
        }
        val value = listOf(400_000L, 0L, -80_000L, 0L, 50_000L, 920_000L, 80_000L, 280_000L)[state]
        val label = when (state) {
            5 -> R.string.catalog_demo_has_gastado_este_mes
            4, 7 -> R.string.catalog_demo_falta_para_tus_pagos
            else -> R.string.catalog_demo_restante_del_presupuesto
        }
        // v2 Recibo: Revisar's sentence under the big number, then the tear line, the
        // daily figure, the footnote while payments are reserved and Ajustar presupuesto.
        val reserved = mapOf(
            0 to R.string.catalog_demo_incluye_200_000_separado_para_1_pago,
            3 to R.string.catalog_demo_incluye_100_000_separado_para_1_pago,
            4 to R.string.catalog_demo_incluye_150_000_separado_para_1_pago,
            7 to R.string.catalog_demo_incluye_200_000_separado_para_1_pago
        )[state]
        AnswerSlip(strings.getString(label), copFixture(value), stamp, ink) {
            when (state) {
                4 -> Text(
                    strings.getString(R.string.catalog_demo_quedan_100_000_y_tus_pagos_previstos)
                )

                7 -> Text(strings.getString(R.string.catalog_demo_pasaste_tu_limite_por_80_000))
            }
            if (state != 5) {
                TearLine()
                if (state == 6) {
                    Text(strings.getString(R.string.catalog_demo_mes_cerrado_gastaste_920_000_de_1))
                } else {
                    MoneyText(copFixture(if (state == 0) 40_000 else 0), style = MoneyStyle.Card)
                    Text(strings.getString(R.string.catalog_demo_por_dia))
                    ExampleCaption(strings.getString(R.string.catalog_demo_10_dias_incluido_hoy))
                }
            }
            reserved?.let {
                Footnote(AnnotatedString(strings.getString(it)), {})
            }
            if (state == 2 || state == 4 || state == 7) {
                SelloButton(
                    strings.getString(R.string.catalog_demo_ajustar_presupuesto),
                    {},
                    kind = ButtonKind.Text
                )
            }
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Text(strings.getString(R.string.catalog_demo_tus_categorias))
            CategoryGrid(listOf("Comida", strings.getString(R.string.catalog_demo_cafe))) {
                    index,
                    name
                ->
                CategoryCell(
                    name,
                    if (index ==
                        0
                    ) {
                        SelloIcon.Restaurant
                    } else {
                        SelloIcon.LocalCafe
                    },
                    copFixture(
                        if (index ==
                            0
                        ) {
                            120_000
                        } else {
                            -10_000
                        }
                    ),
                    if (index ==
                        0
                    ) {
                        0.4f
                    } else {
                        1f
                    },
                    if (index ==
                        0
                    ) {
                        "Comida, quedan 120.000 pesos"
                    } else {
                        "Café, excedido por 10.000 pesos"
                    },
                    {
                    },
                    over =
                        index == 1
                )
            }
        }
    }
}

@Composable
fun ComparisonExample() {
    val strings = LocalResources.current
    var mode by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        ChipRows(3, rows = 1) { index ->
            SelloChip(
                listOf(
                    "Menos",
                    strings.getString(R.string.catalog_demo_mas),
                    strings.getString(R.string.catalog_demo_mes_cerrado)
                )[index],
                mode == index,
                { mode = index }
            )
        }
        ComparisonSlip(
            strings.getString(
                if (mode ==
                    2
                ) {
                    R.string.catalog_demo_como_cerro_septiembre
                } else {
                    R.string.catalog_demo_como_va_este_mes
                }
            ),
            copFixture(if (mode == 0) 50_000 else 160_000),
            strings.getString(
                when (mode) {
                    0 -> R.string.catalog_demo_menos_que_en_septiembre
                    1 -> R.string.catalog_demo_more_than_september
                    else -> R.string.catalog_demo_mas_que_en_agosto
                }
            ),
            strings.getString(
                if (mode ==
                    0
                ) {
                    R.string.catalog_demo_11_1_al_mismo_dia_del_mes
                } else {
                    R.string.catalog_demo_21_1_respecto_al_mes_anterior
                }
            ),
            strings.getString(
                if (mode ==
                    2
                ) {
                    R.string.catalog_demo_meses_completos
                } else {
                    R.string.catalog_demo_del_1_al_22_de_cada_mes
                }
            ),
            when (mode) {
                0 -> listOf(
                    ComparisonBar("Octubre", copFixture(400_000), 0.8f, true),
                    ComparisonBar("Septiembre", copFixture(450_000), 0.9f, false)
                )

                1 -> listOf(
                    ComparisonBar("Octubre", copFixture(920_000), 0.92f, true),
                    ComparisonBar("Septiembre", copFixture(760_000), 0.76f, false)
                )

                else -> listOf(
                    ComparisonBar("Septiembre", copFixture(920_000), 0.92f, true),
                    ComparisonBar("Agosto", copFixture(760_000), 0.76f, false)
                )
            }
        )
        IncomeSlipExample(mode)
    }
}

/**
 * Resumen's income slip (SELLO-123): where the income went, by spending category, and
 * the stub to the income list and Pagos e ingresos fijos.
 */
@Composable
private fun IncomeSlipExample(mode: Int) {
    val strings = LocalResources.current
    val october = mode == 0
    val brand = SelloTheme.colors.brand
    val spent = if (october) 400_000L else 920_000L
    val lines = if (october) {
        listOf(
            Triple("Hogar", SelloIcon.Home, 200_000L),
            Triple("Comida", SelloIcon.Restaurant, 80_000L),
            Triple(strings.getString(R.string.catalog_demo_cafe), SelloIcon.LocalCafe, 60_000L)
        )
    } else {
        listOf(
            Triple("Hogar", SelloIcon.Home, 500_000L),
            Triple("Comida", SelloIcon.Restaurant, 250_000L),
            Triple(strings.getString(R.string.catalog_demo_cafe), SelloIcon.LocalCafe, 100_000L)
        )
    }
    Slip(Modifier.fillMaxWidth()) {
        Text(strings.getString(R.string.catalog_income_question))
        LeaderLine(
            strings.getString(
                if (mode ==
                    2
                ) {
                    R.string.catalog_income_in_september
                } else {
                    R.string.catalog_income_in_october
                }
            )
        ) { MoneyText(copFixture(2_000_000), style = MoneyStyle.Title) }
        SplitBar(
            listOf(
                SplitSegment(spent / 2_000_000f, copFixture(spent).spoken, brand),
                SplitSegment(
                    1f - spent / 2_000_000f,
                    copFixture(2_000_000 - spent).spoken,
                    brand,
                    striped = true
                )
            ),
            Modifier.padding(vertical = 8.dp)
        )
        lines.forEach { (name, icon, pesos) ->
            LeaderLine(name, icon = icon, onClick = {}) { MoneyText(copFixture(-pesos)) }
        }
        LeaderLine(
            strings.getString(R.string.catalog_income_others),
            Modifier.padding(start = 25.dp)
        ) {
            MoneyText(copFixture(if (october) -60_000 else -70_000))
        }
        TotalLine(strings.getString(R.string.catalog_demo_ingresos_menos_gastos)) {
            MoneyText(copFixture(2_000_000 - spent), style = MoneyStyle.Title)
        }
        TearLine()
        SlipStub(
            start = {
                StubButton(
                    SelloIcon.ReceiptLong,
                    strings.getString(R.string.catalog_income_stub_incomes),
                    {},
                    fact = strings.getString(R.string.catalog_income_stub_count)
                )
            },
            end = {
                StubButton(
                    SelloIcon.Autorenew,
                    strings.getString(R.string.catalog_income_stub_fixed),
                    {},
                    fact = strings.getString(R.string.catalog_income_stub_next),
                    label = strings.getString(R.string.catalog_income_stub_fixed_label)
                )
            }
        )
    }
}

@Composable
fun RankingExample() {
    val strings = LocalResources.current
    var opened by rememberSaveable { mutableStateOf("") }
    Slip(Modifier.fillMaxWidth(), pinked = false) {
        Text(strings.getString(R.string.catalog_demo_donde_se_va))
        SpendingRankRow("Comida", SelloIcon.Restaurant, copFixture(300_000), 0.75f, {
            opened =
                "Comida"
        })
        SpendingRankRow(
            strings.getString(
                R.string.catalog_demo_cafe
            ),
            SelloIcon.LocalCafe,
            copFixture(100_000),
            0.25f,
            {
                opened = strings.getString(R.string.catalog_demo_cafe)
            }
        )
        TotalLine("Total") { MoneyText(copFixture(400_000)) }
        ExampleCaption(opened)
    }
}

@Composable
fun SuggestionExample() {
    val strings = LocalResources.current
    var dismissed by rememberSaveable { mutableStateOf(false) }
    var action by rememberSaveable { mutableStateOf(false) }
    ExampleStack {
        if (!dismissed) {
            SuggestionNote(
                strings.getString(
                    R.string.catalog_demo_una_sugerencia
                ),
                strings.getString(
                    R.string.catalog_demo_cafe_supero_su_limite_dos_meses_seguidos
                ),
                strings.getString(
                    R.string.catalog_demo_podrias_ajustar_su_limite_para_que_refleje
                ),
                strings.getString(
                    R.string.catalog_demo_ajustar_el_presupuesto_de_cafe
                ),
                strings.getString(R.string.catalog_demo_descartar_sugerencia),
                {
                    action =
                        true
                },
                { dismissed = true }
            )
        }
        if (action) {
            ExampleCaption(
                strings.getString(R.string.catalog_demo_accion_sintetica_ajustar_cafe)
            )
        }
        SelloButton(strings.getString(R.string.catalog_demo_mostrar_sugerencia), {
            dismissed = false
            action = false
        }, kind = ButtonKind.Text)
        ExampleCaption(
            strings.getString(
                R.string.catalog_demo_la_elegibilidad_y_el_descarte_mensual_pertenecen
            )
        )
    }
}

/**
 * Plan del mes as the income screen draws it: receipt lines in their three weights, a
 * single-rule sum and one dashed chip per expected income. Then a plan the budgets
 * exceed.
 */
@Composable
fun PlanSlipExample() {
    val strings = LocalResources.current
    ExampleStack {
        ExamplePlan(
            recorded = 2_000_000,
            expected = 1_000_000,
            unassigned = 200_000,
            chips = listOf(
                Triple(
                    R.string.catalog_demo_salario_vie_30_oct,
                    R.string.catalog_demo_salario_esperado_el_vie_30_oct,
                    1_000_000L
                )
            )
        )
        ExampleCaption(strings.getString(R.string.catalog_demo_con_mas_presupuestado_que_el_plan))
        ExamplePlan(
            recorded = 500_000,
            expected = 1_650_000,
            unassigned = -650_000,
            chips = listOf(
                Triple(
                    R.string.catalog_demo_salario_vie_30_oct,
                    R.string.catalog_demo_salario_esperado_el_vie_30_oct,
                    1_000_000L
                ),
                Triple(
                    R.string.catalog_demo_arriendo_sab_31_oct,
                    R.string.catalog_demo_arriendo_esperado_el_sab_31_oct,
                    650_000L
                )
            )
        )
    }
}

@Composable
private fun ExamplePlan(
    recorded: Long,
    expected: Long,
    unassigned: Long,
    chips: List<Triple<Int, Int, Long>>
) {
    val strings = LocalResources.current
    val colors = SelloTheme.colors
    PlanSlip(
        strings.getString(R.string.catalog_demo_plan_del_mes),
        strings.getString(R.string.catalog_demo_incluye_lo_que_esperas_recibir)
    ) {
        LeaderLine(strings.getString(R.string.catalog_demo_anotado)) {
            MoneyText(copFixture(recorded))
        }
        LeaderLine(
            strings.getString(R.string.catalog_demo_esperado),
            emphasis = LineEmphasis.Soft
        ) { MoneyText(copFixture(expected), fontWeight = FontWeight.W700) }
        TotalLine(strings.getString(R.string.catalog_demo_planeado), rule = TotalRule.Single) {
            MoneyText(copFixture(recorded + expected))
        }
        LeaderLine(
            strings.getString(R.string.catalog_demo_sin_asignar_en_el_plan),
            emphasis = LineEmphasis.Strong
        ) {
            MoneyText(
                copFixture(unassigned),
                color = if (unassigned < 0) colors.semantic.loss else colors.ink,
                fontWeight = FontWeight.W900
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        ) {
            chips.forEach { (text, spoken, pesos) ->
                ExpectedChip(
                    strings.getString(text),
                    copFixture(pesos),
                    strings.getString(spoken)
                )
            }
        }
        Text(
            strings.getString(R.string.catalog_demo_lo_esperado_se_anota_solo_en_su_fecha),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
    }
}

/**
 * Rows of Ajustar presupuesto: one category over its limit, one within it and one
 * without a limit. Each row is one button read as one sentence.
 */
@Composable
fun LimitRowExample() {
    val strings = LocalResources.current
    var opened by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            val cafe = strings.getString(R.string.catalog_demo_cafe)
            val salud = strings.getString(R.string.catalog_demo_salud)
            val regalos = strings.getString(R.string.catalog_demo_regalos)
            LimitRow(
                name = cafe,
                icon = SelloIcon.LocalCafe,
                caption = strings.getString(R.string.catalog_demo_gastaste_90_000_de_50_000),
                amount = copFixture(-40_000),
                fraction = 1f,
                over = true,
                spoken = cafe,
                onClick = { opened = cafe },
                modifier = Modifier.testTag("limit-row:over")
            )
            LimitRow(
                name = salud,
                icon = SelloIcon.MedicalServices,
                caption = strings.getString(R.string.catalog_demo_gastaste_60_000_de_80_000),
                amount = copFixture(20_000),
                fraction = 0.75f,
                over = false,
                spoken = salud,
                onClick = { opened = salud }
            )
            LimitRow(
                name = regalos,
                icon = SelloIcon.ReceiptLong,
                caption = strings.getString(R.string.catalog_demo_gastado),
                amount = copFixture(10_000),
                fraction = null,
                over = false,
                spoken = regalos,
                onClick = { opened = regalos }
            )
        }
        if (opened.isNotEmpty()) ExampleCaption(opened)
    }
}
