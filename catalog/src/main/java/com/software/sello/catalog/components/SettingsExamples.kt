package com.software.sello.catalog.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChoiceSlip
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.InkSwatches
import com.software.sello.designsystem.component.OptionSlip
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SettingRow
import com.software.sello.designsystem.component.SettingTone
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk

/**
 * Configuración's rows: one that leads somewhere with its value, one with a caption, one
 * that only labels a control, the red one, and a disabled one. Taps are counted.
 */
@Composable
fun SettingRowExample() {
    val strings = LocalResources.current
    var taps by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        Slip(Modifier.fillMaxWidth()) {
            SettingRow(
                title = strings.getString(R.string.catalog_demo_pagos_fijos),
                icon = SelloIcon.Autorenew,
                value = strings.getString(R.string.catalog_demo_cinco_pagos),
                onClick = { taps++ },
                divider = false,
                modifier = Modifier.testTag("setting-row:value")
            )
            SettingRow(
                title = strings.getString(R.string.catalog_demo_orden_categorias),
                icon = SelloIcon.Sort,
                caption = strings.getString(R.string.catalog_demo_con_gastos_del_mes),
                value = strings.getString(R.string.catalog_demo_mas_usadas),
                onClick = { taps++ }
            )
            SettingRow(
                title = strings.getString(R.string.catalog_demo_tema),
                icon = SelloIcon.Contrast
            )
            SettingRow(
                title = strings.getString(R.string.catalog_demo_borrar_todo),
                icon = SelloIcon.DeleteForever,
                caption = strings.getString(R.string.catalog_demo_pide_confirmacion),
                onClick = { taps++ },
                tone = SettingTone.Loss
            )
            SettingRow(
                title = strings.getString(R.string.catalog_demo_dolar),
                icon = SelloIcon.CurrencyExchange,
                value = "4.150,25",
                onClick = { taps++ },
                enabled = false
            )
        }
        ExampleCaption(strings.getString(R.string.catalog_demo_toques, taps))
    }
}

/** The ink choice; the chosen one is ringed. Choosing does not repaint the catalog. */
@Composable
fun InkSwatchesExample() {
    val strings = LocalResources.current
    var ink by rememberSaveable { mutableStateOf(SelloInk.Cobalto) }
    ExampleStack {
        InkSwatches(
            inks = listOf(
                SelloInk.Cobalto to strings.getString(R.string.catalog_demo_cobalto),
                SelloInk.Violeta to strings.getString(R.string.catalog_demo_violeta)
            ),
            selected = ink,
            onSelect = { ink = it },
            modifier = Modifier.testTag("ink-swatches")
        )
        InkSwatches(
            inks = listOf(
                SelloInk.Cobalto to strings.getString(R.string.catalog_demo_cobalto),
                SelloInk.Violeta to strings.getString(R.string.catalog_demo_violeta)
            ),
            selected = SelloInk.Violeta,
            onSelect = {},
            enabled = false
        )
    }
}

/** Choosing the order of the categories; the choice is shown under the button. */
@Composable
fun OptionSlipExample() {
    val strings = LocalResources.current
    val options = listOf(
        R.string.catalog_demo_mas_usadas,
        R.string.catalog_demo_alfabetico,
        R.string.catalog_demo_monto_gastado,
        R.string.catalog_demo_limite,
        R.string.catalog_demo_ultimo_gasto
    ).map(strings::getString)
    var open by rememberSaveable { mutableStateOf(false) }
    var chosen by rememberSaveable { mutableIntStateOf(0) }
    ExampleStack {
        SelloButton(
            strings.getString(R.string.catalog_demo_orden_categorias),
            { open = true },
            Modifier.testTag("option:open"),
            kind = ButtonKind.Outlined
        )
        Text(
            options[chosen],
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("option:chosen")
        )
    }
    if (open) {
        OptionSlip(
            title = strings.getString(R.string.catalog_demo_orden_categorias),
            options = options,
            selectedIndex = chosen,
            onSelect = {
                chosen = it
                open = false
            },
            onDismiss = { open = false }
        )
    }
}

/** A confirmation whose body is points to read, as before saving a backup. */
@Composable
fun ConfirmPointsExample() {
    val strings = LocalResources.current
    var open by rememberSaveable { mutableStateOf(false) }
    var answer by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        SelloButton(
            strings.getString(R.string.catalog_demo_respaldar),
            { open = true },
            Modifier.testTag("confirm-points:open")
        )
        Text(
            answer.ifEmpty { strings.getString(R.string.catalog_demo_sin_respuesta) },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("confirm-points:outcome")
        )
    }
    if (open) {
        ConfirmSlip(
            title = strings.getString(R.string.catalog_demo_antes_de_guardar),
            confirmLabel = strings.getString(R.string.catalog_demo_elegir_donde),
            onConfirm = {
                answer = strings.getString(R.string.catalog_demo_elegir_donde)
                open = false
            },
            onDismiss = {
                answer = strings.getString(R.string.catalog_demo_cancelado)
                open = false
            },
            destructive = false
        ) {
            BulletList(
                listOf(
                    Bullet(
                        strings.getString(R.string.catalog_demo_punto_legible_lead),
                        strings.getString(R.string.catalog_demo_punto_legible)
                    ),
                    Bullet(
                        strings.getString(R.string.catalog_demo_punto_tuyo_lead),
                        strings.getString(R.string.catalog_demo_punto_tuyo)
                    ),
                    Bullet(null, strings.getString(R.string.catalog_demo_punto_borrar))
                )
            )
        }
    }
}

/** A choice of two actions over content, as Exportar asks: a format, points, two answers. */
@Composable
fun ChoicePointsExample() {
    val strings = LocalResources.current
    var open by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableIntStateOf(0) }
    var answer by rememberSaveable { mutableStateOf("") }
    ExampleStack {
        SelloButton(
            strings.getString(R.string.catalog_demo_exportar),
            { open = true },
            Modifier.testTag("choice-points:open"),
            kind = ButtonKind.Outlined
        )
        Text(
            answer.ifEmpty { strings.getString(R.string.catalog_demo_sin_respuesta) },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag("choice-points:outcome")
        )
    }
    if (open) {
        val formats = listOf("CSV", "JSON")
        ChoiceSlip(
            title = strings.getString(R.string.catalog_demo_exportar),
            firstLabel = strings.getString(R.string.catalog_demo_compartir),
            onFirst = {
                answer = "${strings.getString(R.string.catalog_demo_compartir)} ${formats[format]}"
                open = false
            },
            secondLabel = strings.getString(R.string.catalog_demo_guardar),
            onSecond = {
                answer = "${strings.getString(R.string.catalog_demo_guardar)} ${formats[format]}"
                open = false
            },
            backLabel = strings.getString(R.string.catalog_demo_cancelado),
            onBack = {
                answer = strings.getString(R.string.catalog_demo_cancelado)
                open = false
            }
        ) {
            SegmentedSwitch(formats, format, { format = it })
            BulletList(
                listOf(
                    Bullet(
                        strings.getString(R.string.catalog_demo_punto_legible_lead),
                        strings.getString(R.string.catalog_demo_punto_legible)
                    )
                ),
                Modifier.padding(top = 12.dp)
            )
        }
    }
}
