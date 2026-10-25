package com.software.sello.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.DigitGrouping
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceGrid
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SwitchRow
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme

@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.slipGap)) { content() }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SelloTheme.colors.inkSoft)
}

private val CategoryFixtures = listOf(
    "Alimentación" to SelloIcon.Restaurant,
    "Transporte" to SelloIcon.DirectionsBus,
    "Café" to SelloIcon.LocalCafe,
    "Hogar" to SelloIcon.Home,
    "Una categoría de nombre largo" to SelloIcon.ReceiptLong
)

private val SourceFixtures =
    listOf("Salario", "Freelance", "Ingresos pasivos", "Transferencia", "Otro")

@Composable
fun ChipExample() {
    val strings = LocalResources.current
    var category by rememberSaveable { mutableIntStateOf(-1) }
    var goal by rememberSaveable { mutableStateOf(false) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        Caption("Categorías: se acomodan en varias líneas")
        ChipFlow {
            CategoryFixtures.forEachIndexed { index, (name, icon) ->
                SelloChip(name, category == index, { category = index }, icon = icon)
            }
        }
        if (category < 0) FieldMessageText(FieldMessage("Elige una categoría"))
        Caption("Fuentes: una sola línea que se desliza")
        ChipFlow(singleLine = true) {
            SourceFixtures.forEachIndexed { index, name ->
                SelloChip(name, source == index, { source = index })
            }
        }
        Caption("Deshabilitado")
        ChipFlow {
            SelloChip("Archivada", selected = false, onClick = {}, enabled = false)
            SelloChip(
                strings.getString(R.string.catalog_demo_viaje_a_la_costa),
                selected = goal,
                onClick = { goal = !goal },
                icon = SelloIcon.Flight,
                trailing = strings.getString(R.string.catalog_demo_57)
            )
        }
        SelloButton("Quitar selección", { category = -1 }, kind = ButtonKind.Text)
    }
}

@Composable
fun FieldPillExample() {
    var presses by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)) {
            FieldPill("Hoy, jue 22", SelloIcon.Event, { presses++ })
            FieldPill("Nota", SelloIcon.EditNote, { presses++ }, placeholder = true)
        }
        Caption("Pulsaciones: $presses")
    }
}

private const val NAME_LIMIT = 24
private const val NOTE_LIMIT = 60

private fun count(text: String) = text.trim().let { it.codePointCount(0, it.length) }

@Composable
fun FormFieldExample() {
    var name by rememberSaveable { mutableStateOf("Mercado de plaza") }
    var note by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("500000") }
    Slip(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)) {
            // Drawn in groups; the value stays as typed, shown here in the message.
            FormField(
                label = "Límite en pesos",
                value = limit,
                onValueChange = { limit = it },
                message = FieldMessage("Escrito: $limit", isError = false),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DigitGrouping(),
                modifier = Modifier.testTag("field:limit")
            )
            FormField(
                label = "Nombre",
                value = name,
                onValueChange = { name = it },
                message = when {
                    name.isBlank() -> FieldMessage("Escribe un nombre")
                    count(name) > NAME_LIMIT -> FieldMessage("Máximo $NAME_LIMIT caracteres")
                    else -> null
                },
                counter = "${count(name)}/$NAME_LIMIT",
                modifier = Modifier.testTag("field:name")
            )
            FormField(
                label = "Nota (opcional)",
                value = note,
                onValueChange = { note = it },
                message = if (count(note) > NOTE_LIMIT) {
                    FieldMessage("Máximo $NOTE_LIMIT caracteres")
                } else {
                    FieldMessage("Solo tú la ves", isError = false)
                },
                counter = "${count(note)}/$NOTE_LIMIT",
                modifier = Modifier.testTag("field:note")
            )
            FormField("Deshabilitado", "No editable", {}, enabled = false)
        }
    }
}

@Composable
fun SwitchExample() {
    var repeats by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Slip(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
            SwitchRow("Avisarme al acercarme al límite", repeats, { repeats = it })
            SwitchRow(
                title = "Con icono y detalle",
                checked = !repeats,
                onCheckedChange = { repeats = !it },
                icon = SelloIcon.Event,
                subtitle = "El renglón completo es el control"
            )
            SwitchRow("Deshabilitado", checked = true, onCheckedChange = {}, enabled = false)
            SegmentedSwitch(listOf("Sistema", "Claro", "Oscuro"), mode, { mode = it })
        }
    }
}

/** The actual controls combined as Settings uses them, with catalog-local state only. */
@Composable
fun SettingsControlsExample() {
    var theme by rememberSaveable { mutableIntStateOf(0) }
    var ink by rememberSaveable { mutableStateOf("Cobalto") }
    var order by rememberSaveable { mutableStateOf("Más usadas") }
    var direction by rememberSaveable { mutableIntStateOf(0) }
    Slip(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
            Caption("Tema")
            SegmentedSwitch(
                listOf("Sistema", "Claro", "Oscuro"),
                theme,
                { theme = it },
                modifier = Modifier.testTag("settings-controls:theme")
            )
            Caption("Tinta")
            ChipFlow(modifier = Modifier.testTag("settings-controls:ink")) {
                listOf("Cobalto", "Violeta").forEach { choice ->
                    SelloChip(choice, selected = ink == choice, onClick = { ink = choice })
                }
            }
            Caption("Orden de categorías")
            ChipFlow(modifier = Modifier.testTag("settings-controls:order")) {
                listOf(
                    "Más usadas",
                    "Alfabético",
                    "Monto gastado",
                    "Límite",
                    "Último gasto agregado"
                ).forEach { choice ->
                    SelloChip(choice, selected = order == choice, onClick = { order = choice })
                }
            }
            SegmentedSwitch(
                listOf("Mayor a menor", "Menor a mayor"),
                direction,
                { direction = it },
                modifier = Modifier.testTag("settings-controls:direction")
            )
        }
    }
}

/** One icon chosen, one tapped changes it, and a disabled copy that ignores taps. */
@Composable
fun IconChoiceExample() {
    var picked by rememberSaveable { mutableStateOf("home") }
    val choices = remember {
        listOf(
            IconChoice("restaurant", SelloIcon.Restaurant, "Comida"),
            IconChoice("home", SelloIcon.Home, "Hogar"),
            IconChoice("directions_bus", SelloIcon.DirectionsBus, "Transporte"),
            IconChoice("theaters", SelloIcon.Theaters, "Entretenimiento"),
            IconChoice("medical_services", SelloIcon.MedicalServices, "Salud"),
            IconChoice("checkroom", SelloIcon.Checkroom, "Ropa"),
            IconChoice("local_cafe", SelloIcon.LocalCafe, "Café"),
            IconChoice("school", SelloIcon.School, "Educación"),
            IconChoice("pets", SelloIcon.Pets, "Mascotas"),
            IconChoice("fitness_center", SelloIcon.FitnessCenter, "Gimnasio"),
            IconChoice("shopping_cart", SelloIcon.ShoppingCart, "Mercado"),
            IconChoice("flight", SelloIcon.Flight, "Viajes"),
            IconChoice("phone_iphone", SelloIcon.PhoneIphone, "Celular"),
            IconChoice("child_care", SelloIcon.ChildCare, "Hijos")
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
        Slip(modifier = Modifier.fillMaxWidth(), pinked = false) {
            IconChoiceGrid(choices, picked, { picked = it }, Modifier.testTag("icons:grid"))
        }
        Text(
            "Elegido: $picked",
            style = MaterialTheme.typography.bodyLarge,
            color = SelloTheme.colors.ink,
            modifier = Modifier.testTag("icons:picked")
        )
        Slip(modifier = Modifier.fillMaxWidth(), pinked = false) {
            IconChoiceGrid(choices.take(4), "home", { picked = it }, enabled = false)
        }
    }
}
