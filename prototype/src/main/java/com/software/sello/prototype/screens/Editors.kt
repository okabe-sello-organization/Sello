package com.software.sello.prototype.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import com.software.sello.designsystem.component.CategoryCell
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.ConfirmSlip
import com.software.sello.designsystem.component.DigitGrouping
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldPill
import com.software.sello.designsystem.component.FormField
import com.software.sello.designsystem.component.IconChoice
import com.software.sello.designsystem.component.IconChoiceGrid
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SegmentedSwitch
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SwitchRow
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.prototype.AnotarRoute
import com.software.sello.prototype.BackButton
import com.software.sello.prototype.CategoryEditorRoute
import com.software.sello.prototype.DiscardGuard
import com.software.sello.prototype.LocalNav
import com.software.sello.prototype.LocalProto
import com.software.sello.prototype.Milestones
import com.software.sello.prototype.Overflow
import com.software.sello.prototype.ReciboRoute
import com.software.sello.prototype.Sample
import com.software.sello.prototype.ScreenTag
import com.software.sello.prototype.SlipTitle
import com.software.sello.prototype.Soft
import com.software.sello.prototype.cop
import com.software.sello.prototype.grouped

private val iconChoices = SelloIcon.entries
    .filterNot {
        it in setOf(
            SelloIcon.Add, SelloIcon.ArrowBack, SelloIcon.Backspace, SelloIcon.Check, SelloIcon.ChevronLeft,
            SelloIcon.ChevronRight, SelloIcon.ContentPaste, SelloIcon.EditNote, SelloIcon.Error, SelloIcon.Event,
            SelloIcon.Insights, SelloIcon.KeyboardArrowDown, SelloIcon.ReceiptLong, SelloIcon.Settings
        )
    }
    .map { IconChoice(it.name, it, it.name) }

/** A form screen: Back, a title, fields that scroll and one button at the end. */
@Composable
private fun EditorFrame(
    title: String,
    dirty: Boolean,
    onDelete: Pair<String, () -> Unit>?,
    content: @Composable (PaddingValues) -> Unit
) {
    val nav = LocalNav.current
    val leave = DiscardGuard(dirty, "¿Descartar los cambios?") { nav.popBackStack() }
    SelloScaffold(
        title = title,
        navigationIcon = { BackButton(leave) },
        actions = { if (onDelete != null) Overflow(onDelete) },
        content = content
    )
}

@Composable
private fun Form(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(bottom = SelloTheme.spacing.xl)
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = SelloTheme.colors.inkSoft)
}

/** Screen 5. Name, icon and monthly limit, with a live preview of the cell. */
@Composable
fun CategoryEditorScreen(route: CategoryEditorRoute) {
    val nav = LocalNav.current
    val proto = LocalProto.current
    val existing = route.id?.let { Sample.category(it) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var icon by rememberSaveable {
        mutableStateOf(existing?.icon?.name ?: SelloIcon.ShoppingCart.name)
    }
    var limit by rememberSaveable { mutableStateOf(existing?.limit?.toString() ?: "300000") }
    var noLimit by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    val duplicate =
        existing == null && Sample.categories.any { it.name.equals(name.trim(), ignoreCase = true) }
    ScreenTag(
        when {
            existing != null -> "5 · Editar categoría"
            route.forEntry -> "5 · Nueva categoría · antes del gasto"
            else -> "5 · Nueva categoría"
        }
    )
    EditorFrame(
        title = if (existing != null) "Editar categoría" else "Nueva categoría",
        dirty = name.isNotBlank() && name != existing?.name,
        onDelete = if (existing != null) "Borrar categoría" to { deleting = true } else null
    ) { padding ->
        Form(padding) {
            if (route.forEntry) {
                Slip(pinked = false) {
                    Soft(
                        "Para anotar un gasto necesitas al menos una categoría. Al crearla seguimos con el gasto."
                    )
                }
            }
            FormField(
                "Nombre",
                name,
                { name = it.take(24) },
                counter = "${name.length}/24",
                message = FieldMessage("Ya tienes una categoría con ese nombre").takeIf {
                    duplicate
                }
            )
            Label("Ícono")
            IconChoiceGrid(iconChoices, icon, { icon = it })
            SwitchRow("Sin límite", noLimit, {
                noLimit = it
            }, subtitle = "Se muestra lo gastado, sin círculo que se llena")
            FormField(
                "Límite mensual",
                if (noLimit) "" else limit,
                { limit = it.filter(Char::isDigit).take(12) },
                enabled = !noLimit,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DigitGrouping()
            )
            ChipFlow {
                listOf(200_000L, 300_000L, 500_000L).forEach { s ->
                    SelloChip(grouped(s), limit == "$s" && !noLimit, {
                        limit = "$s"
                    }, enabled = !noLimit)
                }
            }
            Soft("Tu límite total del mes pasaría a $ 4.240.000")
            Slip(pinked = false) {
                SlipTitle("Así se verá", "en tu recibo")
                CategoryCell(
                    name = name.ifBlank { "Nombre" },
                    icon = SelloIcon.valueOf(icon),
                    amount = cop(if (noLimit) 0 else limit.toLongOrNull() ?: 0),
                    fraction = if (noLimit) null else 0f,
                    description = "Vista previa",
                    onClick = {},
                    amountCaption = "Gastado".takeIf { noLimit }
                )
            }
            if (existing !=
                null
            ) {
                Soft(
                    "Cambiar el límite aplica a octubre y a los meses que vienen; los anteriores no cambian."
                )
            }
            SelloButton(
                if (existing != null) "Guardar cambios" else "Crear categoría",
                {
                    nav.popBackStack()
                    when {
                        route.forEntry -> {
                            proto.firstRun = false
                            nav.navigate(AnotarRoute())
                        }

                        existing != null -> proto.say("Cambios guardados")

                        else -> {
                            proto.firstRun = false
                            proto.say("Categoría creada")
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && !duplicate
            )
        }
    }
    if (deleting && existing != null) {
        ConfirmSlip(
            title = "¿Borrar ${existing.name}?",
            message = AnnotatedString(
                "Se borran la categoría y sus 13 gastos de octubre y los de meses anteriores. No se puede deshacer."
            ),
            confirmLabel = "Borrar",
            onConfirm = {
                deleting = false
                nav.popBackStack(ReciboRoute, inclusive = false)
                proto.say("${existing.name} borrada")
            },
            onDismiss = { deleting = false }
        )
    }
}

/** Screen 11. A new investment, optionally with a fixed rate and a target value. */
@Composable
fun InvestmentEditorScreen(id: String?) {
    val nav = LocalNav.current
    val proto = LocalProto.current
    val existing = id?.let { Sample.investment(it) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "CDT Davivienda 180 días") }
    var currency by rememberSaveable { mutableIntStateOf(0) }
    var icon by rememberSaveable { mutableStateOf(SelloIcon.AccountBalance.name) }
    var first by rememberSaveable { mutableStateOf("5000000") }
    var fixed by rememberSaveable { mutableStateOf(true) }
    var target by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    ScreenTag(if (existing != null) "11 · Editar inversión" else "11 · Nueva inversión")
    EditorFrame(
        title = if (existing != null) "Editar inversión" else "Nueva inversión",
        dirty = false,
        onDelete = if (existing != null) "Borrar inversión" to { deleting = true } else null
    ) { padding ->
        Form(padding) {
            FormField("Nombre", name, { name = it.take(40) }, counter = "${name.length}/40")
            Label("Moneda")
            SegmentedSwitch(
                listOf("COP", "USD", "EUR", "GBP"),
                currency,
                {
                    currency = it
                },
                enabled =
                    existing == null
            )
            Label("Ícono")
            IconChoiceGrid(iconChoices, icon, { icon = it })
            if (existing == null) {
                FormField(
                    "Primer aporte",
                    first,
                    { first = it.filter(Char::isDigit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = DigitGrouping()
                )
                FieldPill("jue 22 oct 2026", SelloIcon.Event, {})
            }
            SwitchRow("Tiene tasa fija", fixed, {
                fixed = it
            }, subtitle = "La app suma el interés sola")
            if (fixed) {
                FormField("Tasa anual", "10,8 %", {})
                LeaderLine("Paga interés", "Cada mes")
                LeaderLine("Vence", "20 abr 2027")
            }
            SwitchRow("Ponerle una meta de valor", target, { target = it }, subtitle = "Opcional")
            if (target) {
                FormField("Valor que quieres", "12.000.000", {})
                FieldPill("Para cuándo", SelloIcon.Event, {}, placeholder = true)
            }
            SelloButton(
                if (existing != null) "Guardar cambios" else "Crear inversión",
                {
                    nav.popBackStack()
                    proto.say(if (existing != null) "Cambios guardados" else "Inversión creada")
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank() && (existing != null || first.isNotBlank())
            )
        }
    }
    if (deleting && existing != null) {
        ConfirmSlip(
            title = "¿Borrar ${existing.name}?",
            message = AnnotatedString("Se borran la inversión y sus 23 movimientos."),
            confirmLabel = "Borrar",
            onConfirm = {
                deleting = false
                nav.popBackStack()
                nav.popBackStack()
                proto.say("${existing.name} borrada")
            },
            onDismiss = { deleting = false }
        )
    }
}

/** Screen 14. A goal: how much, by when, and the four stamps it will earn. */
@Composable
fun GoalEditorScreen(id: String?) {
    val nav = LocalNav.current
    val proto = LocalProto.current
    val existing = id?.let { Sample.goal(it) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "Cuota inicial del carro") }
    var icon by rememberSaveable { mutableStateOf(SelloIcon.Home.name) }
    var amount by rememberSaveable { mutableStateOf((existing?.target ?: 20_000_000).toString()) }
    var auto by rememberSaveable { mutableStateOf(true) }
    var deleting by rememberSaveable { mutableStateOf(false) }
    ScreenTag(if (existing != null) "14 · Editar meta" else "14 · Nueva meta")
    EditorFrame(
        title = if (existing != null) "Editar meta" else "Nueva meta",
        dirty = false,
        onDelete = if (existing != null) "Borrar meta" to { deleting = true } else null
    ) { padding ->
        Form(padding) {
            FormField("Nombre", name, { name = it.take(40) })
            Label("Ícono")
            IconChoiceGrid(iconChoices, icon, { icon = it })
            FormField(
                "Cuánto necesitas",
                amount,
                { amount = it.filter(Char::isDigit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = DigitGrouping()
            )
            Label("Para cuándo")
            FieldPill("dic 2028", SelloIcon.Event, {})
            Soft("Son $ 769.300 al mes durante 26 meses")
            Slip(pinked = false) {
                SlipTitle("Tus cuatro sellos", "fechas esperadas")
                Milestones(
                    listOf(
                        "25 %" to "may 27",
                        "50 %" to "dic 27",
                        "75 %" to "jun 28",
                        "Meta" to "dic 28"
                    )
                        .map { (l, m) -> Sample.Milestone(l, m, false) }
                )
            }
            SwitchRow("Aportar solo cada mes", auto, { auto = it }, subtitle = "$ 769.300 el día 2")
            SelloButton(
                if (existing != null) "Guardar cambios" else "Crear meta",
                {
                    nav.popBackStack()
                    proto.say(if (existing != null) "Cambios guardados" else "Meta creada")
                },
                Modifier.fillMaxWidth(),
                enabled = name.isNotBlank()
            )
        }
    }
    if (deleting && existing != null) {
        ConfirmSlip(
            title = "¿Borrar ${existing.name}?",
            message = AnnotatedString(
                "La plata guardada no es una cuenta: borrar solo quita el registro."
            ),
            confirmLabel = "Borrar",
            onConfirm = {
                deleting = false
                nav.popBackStack()
                nav.popBackStack()
                proto.say("${existing.name} borrada")
            },
            onDismiss = { deleting = false }
        )
    }
}
