package com.software.sello.catalog.components

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ChoiceSlip
import com.software.sello.designsystem.component.DayGrid
import com.software.sello.designsystem.component.EntryLine
import com.software.sello.designsystem.component.IntervalStepper
import com.software.sello.designsystem.component.RepeatNext
import com.software.sello.designsystem.component.RepeatSheet
import com.software.sello.designsystem.component.RepeatSheetActions
import com.software.sello.designsystem.component.ScheduleRow
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

private enum class RepeatKind(@StringRes val label: Int, val most: Int) {
    Month(R.string.catalog_demo_cada_mes, 0),
    Twice(R.string.catalog_demo_dos_veces_al_mes, 0),
    Weeks(R.string.catalog_demo_cada_semana_o_mas, 52),
    Days(R.string.catalog_demo_cada_cierto_numero_de_dias, 365)
}

/**
 * Se repite as the entry opens it: the four kinds, the day grid or the stepper, the end,
 * when the next one is recorded, and Quitar and Listo. The sheet covers the example as
 * it covers the entry; back closes it. Which days go together is this stand-in's rule.
 */
@Composable
fun RepeatSheetExample(startOpen: Boolean = false) {
    val strings = LocalResources.current
    var open by rememberSaveable { mutableStateOf(startOpen) }
    var repeats by rememberSaveable { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(RepeatKind.Month) }
    // The chosen days, oldest first, as text so they survive recreation.
    var days by rememberSaveable { mutableStateOf("5") }
    var every by rememberSaveable { mutableIntStateOf(1) }
    var ends by rememberSaveable { mutableStateOf(false) }
    val chosen = days.split(',').filter { it.isNotEmpty() }.map { it.toInt() }
    BackHandler(enabled = open) { open = false }
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EntryLine(
                if (repeats) {
                    strings.getString(kind.label)
                } else {
                    strings.getString(R.string.catalog_demo_se_repite_no)
                },
                SelloIcon.Autorenew,
                { open = true },
                Modifier.testTag("repeat:open"),
                action = strings.getString(R.string.catalog_demo_cambiar),
                placeholder = !repeats
            )
        }
        if (open) {
            RepeatSheet(strings.getString(R.string.catalog_demo_se_repite), { open = false }) {
                ChipFlow {
                    RepeatKind.entries.forEach {
                        SelloChip(strings.getString(it.label), it == kind, {
                            kind = it
                            days = if (it == RepeatKind.Twice) "15,30" else "5"
                            every = 1
                        })
                    }
                }
                when (kind) {
                    RepeatKind.Month, RepeatKind.Twice -> {
                        val twice = kind == RepeatKind.Twice
                        FormLabel(
                            if (twice) {
                                R.string.catalog_demo_los_dias_elige_dos
                            } else {
                                R.string.catalog_demo_el_dia
                            }
                        )
                        DayGrid(
                            selected = chosen.toSet(),
                            onToggle = { day ->
                                days = when {
                                    !twice -> "$day"
                                    day in chosen -> (chosen - day).joinToString(",")
                                    else -> (chosen.takeLast(1) + day).joinToString(",")
                                }
                            },
                            dayDescription = { strings.getString(R.string.catalog_demo_dia_n, it) }
                        )
                        if (chosen.any { it > 28 }) {
                            ExampleCaption(
                                strings.getString(R.string.catalog_demo_en_los_meses_mas_cortos)
                            )
                        }
                    }

                    RepeatKind.Weeks, RepeatKind.Days -> {
                        FormLabel(R.string.catalog_demo_cada)
                        val weeks = kind == RepeatKind.Weeks
                        val unit = when {
                            weeks && every == 1 -> R.string.catalog_demo_semana_desde
                            weeks -> R.string.catalog_demo_semanas_desde
                            every == 1 -> R.string.catalog_demo_dia_desde
                            else -> R.string.catalog_demo_dias_desde
                        }
                        IntervalStepper(
                            value = every,
                            unit = strings.getString(unit),
                            onDecrease = { every-- },
                            onIncrease = { every++ },
                            decreaseLabel = strings.getString(R.string.catalog_demo_menos),
                            increaseLabel = strings.getString(R.string.catalog_demo_mas),
                            canDecrease = every > 1,
                            canIncrease = every < kind.most
                        )
                    }
                }
                FormLabel(R.string.catalog_demo_termina)
                ChipFlow {
                    SelloChip(strings.getString(R.string.catalog_demo_nunca), !ends, {
                        ends = false
                    })
                    SelloChip(
                        strings.getString(R.string.catalog_demo_en_una_fecha),
                        ends,
                        { ends = true },
                        icon = SelloIcon.EventBusy
                    )
                }
                RepeatNext(strings.getString(R.string.catalog_demo_proximo_lun_5_nov))
                RepeatSheetActions(
                    dismissLabel = strings.getString(R.string.catalog_demo_quitar),
                    onDismiss = {
                        repeats = false
                        open = false
                    },
                    confirmLabel = strings.getString(R.string.catalog_demo_listo),
                    onConfirm = {
                        repeats = true
                        open = false
                    },
                    confirmEnabled = kind != RepeatKind.Twice || chosen.size == 2
                )
            }
        }
    }
}

@Composable
private fun FormLabel(@StringRes text: Int) {
    Text(
        LocalResources.current.getString(text),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
        color = SelloTheme.colors.inkSoft,
        modifier = Modifier.padding(start = 2.dp, top = 6.dp, bottom = 6.dp)
    )
}

private class ScheduleFixture(
    @StringRes val name: Int,
    @StringRes val caption: Int,
    @StringRes val spoken: Int,
    val pesos: Long,
    val icon: SelloIcon,
    val soon: Boolean = false,
    val paused: Boolean = false,
    val ended: Boolean = false
)

private val Payments = listOf(
    ScheduleFixture(
        R.string.catalog_demo_arriendo,
        R.string.catalog_demo_hogar_el_5,
        R.string.catalog_demo_arriendo_spoken,
        950_000,
        SelloIcon.Home
    ),
    ScheduleFixture(
        R.string.catalog_demo_gimnasio,
        R.string.catalog_demo_gimnasio_el_15,
        R.string.catalog_demo_gimnasio_spoken,
        99_900,
        SelloIcon.FitnessCenter
    ),
    ScheduleFixture(
        R.string.catalog_demo_servicios_publicos,
        R.string.catalog_demo_hogar_el_28,
        R.string.catalog_demo_servicios_spoken,
        200_000,
        SelloIcon.Home,
        soon = true
    ),
    ScheduleFixture(
        R.string.catalog_demo_bus,
        R.string.catalog_demo_transporte_sabados,
        R.string.catalog_demo_bus_spoken,
        20_000,
        SelloIcon.DirectionsBus,
        paused = true
    )
)

private val Ended = listOf(
    ScheduleFixture(
        R.string.catalog_demo_curso_de_ingles,
        R.string.catalog_demo_educacion_termino,
        R.string.catalog_demo_curso_spoken,
        189_000,
        SelloIcon.School,
        ended = true
    )
)

private val Incomes = listOf(
    ScheduleFixture(
        R.string.catalog_demo_freelance,
        R.string.catalog_demo_el_15_de_cada_mes,
        R.string.catalog_demo_freelance_spoken,
        250_000,
        SelloIcon.EditNote
    ),
    ScheduleFixture(
        R.string.catalog_demo_salario,
        R.string.catalog_demo_el_30_proximo,
        R.string.catalog_demo_salario_spoken,
        1_000_000,
        SelloIcon.AccountBalance,
        soon = true
    )
)

/**
 * The list of Pagos e ingresos fijos: active payments, one still to come in soft ink,
 * one paused and stamped, one ended under Terminados, then incomes in gain ink.
 */
@Composable
fun ScheduleRowExample() {
    val strings = LocalResources.current
    var opened by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        Slip(Modifier.fillMaxWidth()) {
            SlipTitle(R.string.catalog_demo_tus_pagos_fijos)
            ScheduleRows(Payments, income = false) { opened = it }
            FormLabel(R.string.catalog_demo_terminados)
            ScheduleRows(Ended, income = false) { opened = it }
        }
        Slip(Modifier.fillMaxWidth()) {
            SlipTitle(R.string.catalog_demo_tus_ingresos_fijos)
            ScheduleRows(Incomes, income = true) { opened = it }
        }
        if (opened.isNotEmpty()) {
            ExampleCaption(strings.getString(R.string.catalog_demo_abriste, opened))
        }
    }
}

@Composable
private fun SlipTitle(@StringRes text: Int) {
    Text(
        LocalResources.current.getString(text),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 4.dp).semantics { heading() }
    )
}

@Composable
private fun ScheduleRows(rows: List<ScheduleFixture>, income: Boolean, onOpen: (String) -> Unit) {
    val strings = LocalResources.current
    val paused = strings.getString(R.string.catalog_demo_en_pausa)
    rows.forEachIndexed { index, row ->
        val name = strings.getString(row.name)
        ScheduleRow(
            name = name,
            caption = strings.getString(row.caption),
            amount = copFixture(row.pesos, signed = income),
            icon = row.icon,
            onClick = { onOpen(name) },
            spoken = strings.getString(row.spoken),
            soon = row.soon,
            income = income,
            pausedLabel = paused.takeIf { row.paused },
            dimmed = row.paused || row.ended,
            last = index == rows.lastIndex
        )
    }
}

private enum class ChoiceStage { Closed, Asking, Busy }

/**
 * Saving an entry a schedule recorded: two answers and a way back. The busy moment stands
 * in for the real save.
 */
@Composable
fun ChoiceSlipExample() {
    val strings = LocalResources.current
    var stage by rememberSaveable { mutableStateOf(ChoiceStage.Closed) }
    var answer by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        SelloButton(
            strings.getString(R.string.catalog_demo_guardar_cambios),
            { stage = ChoiceStage.Asking },
            Modifier.testTag("choice:open"),
            kind = ButtonKind.Outlined
        )
        Text(
            answer.ifEmpty { strings.getString(R.string.catalog_demo_sin_respuesta) },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("choice:outcome")
        )
    }
    if (stage != ChoiceStage.Closed) {
        LaunchedEffect(stage) {
            if (stage == ChoiceStage.Busy) {
                delay(CHOICE_BUSY_MILLIS)
                stage = ChoiceStage.Closed
            }
        }
        val first = strings.getString(R.string.catalog_demo_solo_este)
        val second = strings.getString(R.string.catalog_demo_este_y_los_siguientes)
        val back = strings.getString(R.string.catalog_demo_volver_a_editar)
        ExampleChoice(
            onFirst = {
                answer = first
                stage = ChoiceStage.Busy
            },
            onSecond = {
                answer = second
                stage = ChoiceStage.Busy
            },
            onBack = {
                answer = back
                stage = ChoiceStage.Closed
            },
            busy = stage == ChoiceStage.Busy
        )
    }
}

@Composable
internal fun ExampleChoice(
    onFirst: () -> Unit = {},
    onSecond: () -> Unit = {},
    onBack: () -> Unit = {},
    busy: Boolean = false
) {
    val strings = LocalResources.current
    val name = strings.getString(R.string.catalog_demo_arriendo)
    val text = strings.getString(R.string.catalog_demo_este_gasto_lo_anoto, name)
    val start = text.indexOf(name)
    ChoiceSlip(
        title = strings.getString(R.string.catalog_demo_cambiar_tambien_los_siguientes),
        message = buildAnnotatedString {
            append(text)
            addStyle(SpanStyle(fontWeight = FontWeight.W700), start, start + name.length)
        },
        firstLabel = strings.getString(R.string.catalog_demo_solo_este),
        onFirst = onFirst,
        secondLabel = strings.getString(R.string.catalog_demo_este_y_los_siguientes),
        onSecond = onSecond,
        backLabel = strings.getString(R.string.catalog_demo_volver_a_editar),
        onBack = onBack,
        busy = busy
    )
}

private const val CHOICE_BUSY_MILLIS = 1_200L
