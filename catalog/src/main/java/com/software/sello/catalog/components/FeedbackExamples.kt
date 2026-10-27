package com.software.sello.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.Bullet
import com.software.sello.designsystem.component.BulletList
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloNotice
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.slipGap)) { content() }
}

@Composable
private fun Result(text: String, tag: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = SelloTheme.colors.ink,
        modifier = Modifier.testTag(tag)
    )
}

private enum class ConfirmStage { Closed, Asking, Busy }

@Composable
fun ConfirmSlipExample() {
    var stage by rememberSaveable { mutableStateOf(ConfirmStage.Closed) }
    var outcome by rememberSaveable { mutableStateOf("Sin respuesta") }
    Stack {
        SelloButton(
            text = "Borrar categoría",
            onClick = { stage = ConfirmStage.Asking },
            kind = ButtonKind.Outlined,
            modifier = Modifier.testTag("confirm:open")
        )
        Result(outcome, "confirm:outcome")
    }
    if (stage != ConfirmStage.Closed) {
        // Stands in for a real command: busy for a moment, then a confirmed result.
        LaunchedEffect(stage) {
            if (stage == ConfirmStage.Busy) {
                delay(CONFIRM_BUSY_MILLIS)
                outcome = "Confirmado"
                stage = ConfirmStage.Closed
            }
        }
        ConfirmSlip(
            title = "¿Borrar Café?",
            message = buildAnnotatedString {
                append("Se borran la categoría y ")
                withStyle(SpanStyle(fontWeight = FontWeight.W700)) { append("sus 12 gastos") }
                append(". No se puede deshacer.")
            },
            confirmLabel = "Borrar",
            onConfirm = { stage = ConfirmStage.Busy },
            onDismiss = {
                outcome = "Cancelado"
                stage = ConfirmStage.Closed
            },
            busy = stage == ConfirmStage.Busy
        )
    }
}

private const val CONFIRM_BUSY_MILLIS = 1_200L

/** The notice in each thing it says. It holds no time: here it stays until something else is shown. */
@Composable
fun NoticeExample() {
    val strings = LocalResources.current
    var undone by rememberSaveable { mutableIntStateOf(0) }
    var seen by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        SelloNotice(
            message = "Gasto borrado",
            actionLabel = "Deshacer",
            onAction = { undone++ },
            spoken = "Gasto borrado. Puedes deshacerlo durante 6 segundos.",
            modifier = Modifier.testTag("notice:undo")
        )
        Result("Deshecho: $undone", "notice:undone")
        SelloNotice(message = "Gasto borrado", actionLabel = "Deshacer", onAction = {}, busy = true)
        SelloNotice(message = "Gasto actualizado")
        SelloNotice(
            message = "No se pudo deshacer porque tus datos cambiaron. El gasto quedó borrado."
        )
        // What Sello recorded on its own: the repeat icon leads, and Ver opens the list.
        // It goes by itself; here frozen with 60 % of its time left.
        SelloNotice(
            message = strings.getString(R.string.catalog_demo_sello_anoto_4_pagos_fijos),
            actionLabel = strings.getString(R.string.catalog_demo_ver),
            onAction = { seen++ },
            icon = SelloIcon.Autorenew,
            modifier = Modifier.testTag("notice:recorded"),
            remaining = 0.6f
        )
        Result(strings.getString(R.string.catalog_demo_vistos, seen), "notice:seen")
    }
}

@Composable
fun EmptySlipExample() {
    var actions by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        EmptySlip(
            title = "Aún no hay gastos",
            body = "Anota el primero y aquí verás cuánto te queda.",
            actionLabel = "Anotar un gasto",
            onAction = { actions++ }
        )
        Result("Acciones: $actions", "empty:actions")
        EmptySlip(title = "Sin movimientos este mes", body = "Nada que mostrar todavía.")
    }
}

@Composable
fun ErrorSlipExample() {
    var retries by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        ErrorSlip(
            message = "No pudimos leer tus gastos de octubre. Tus datos siguen guardados.",
            onRetry = { retries++ }
        )
        Result("Reintentos: $retries", "error:retries")
    }
}

@Composable
fun SlipSkeletonExample() {
    Stack {
        SlipSkeleton()
        SlipSkeleton(lines = 5)
    }
}

@Composable
fun ButtonExample() {
    var presses by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        ButtonKind.entries.forEach { kind ->
            SelloButton(
                text = kind.name,
                onClick = { presses++ },
                kind = kind,
                modifier = Modifier.fillMaxWidth().testTag("button:${kind.name}")
            )
        }
        ButtonTone.entries.forEach { tone ->
            SelloButton(tone.name, { presses++ }, Modifier.fillMaxWidth(), tone = tone)
            SelloButton(
                tone.name,
                { presses++ },
                Modifier.fillMaxWidth(),
                kind = ButtonKind.Outlined,
                tone = tone
            )
        }
        SelloButton("Con icono", { presses++ }, Modifier.fillMaxWidth(), icon = SelloIcon.Add)
        SelloButton(
            text = "Guardando",
            onClick = { presses++ },
            loading = true,
            modifier = Modifier.fillMaxWidth().testTag("button:loading")
        )
        SelloButton(
            text = "Escribe un monto",
            onClick = { presses++ },
            enabled = false,
            modifier = Modifier.fillMaxWidth().testTag("button:disabled")
        )
        Result("Pulsaciones: $presses", "button:presses")
    }
}

@Composable
fun BulletListExample() {
    Slip(Modifier.fillMaxWidth(), pinked = false) {
        BulletList(
            listOf(
                Bullet(
                    "Cualquiera que abra el archivo puede leerlo.",
                    "Muestra tus montos, categorías y notas."
                ),
                Bullet("Tú lo guardas y lo cuidas.", "Sello no guarda copias por ti."),
                Bullet(null, "Un punto sin encabezado en negrita.")
            )
        )
    }
}

private enum class BackupStage { Idle, Saving, Saved, Failed, Unknown }

/** The states of saving a file, with synthetic figures: what the Respaldo screen shows. */
@Composable
fun BackupStatesExample() {
    var stage by rememberSaveable { mutableStateOf(BackupStage.Idle) }
    Stack {
        ChipFlow {
            BackupStage.entries.forEach { option ->
                SelloChip(
                    label = option.name,
                    selected = stage == option,
                    onClick = { stage = option },
                    modifier = Modifier.testTag("backup:${option.name}")
                )
            }
        }
        when (stage) {
            BackupStage.Failed -> ErrorSlip(
                title = "No se pudo guardar el respaldo",
                message = "Tus datos no cambiaron. Puede haber quedado un archivo incompleto.",
                retryLabel = "Intentar de nuevo",
                onRetry = { stage = BackupStage.Saving }
            )

            BackupStage.Unknown -> ErrorSlip(
                title = "No sabemos si el respaldo terminó",
                message = "La app se cerró mientras lo guardaba. Crea uno nuevo.",
                retryLabel = "Crear un respaldo nuevo",
                onRetry = { stage = BackupStage.Saving }
            )

            else -> Unit
        }
        Slip(Modifier.fillMaxWidth(), pinked = false) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Último respaldo", style = MaterialTheme.typography.headlineSmall)
                if (stage == BackupStage.Saved) {
                    Stamp("Guardado", StampInk.Good, land = true)
                }
            }
            LeaderLine("Gastos", "3")
            LeaderLine("Ingresos", "1")
            LeaderLine("Archivo", "4 KB")
        }
        SelloButton(
            text = if (stage == BackupStage.Saving) "Guardando…" else "Crear respaldo",
            onClick = { stage = BackupStage.Saving },
            loading = stage == BackupStage.Saving,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
