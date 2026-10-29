package com.software.sello.catalog.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.catalog.R
import com.software.sello.designsystem.component.Blotter
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.CategoryCell
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.CategoryGrid
import com.software.sello.designsystem.component.ChartPeriod
import com.software.sello.designsystem.component.ChartPoint
import com.software.sello.designsystem.component.ChartReference
import com.software.sello.designsystem.component.ColumnsChart
import com.software.sello.designsystem.component.LineAction
import com.software.sello.designsystem.component.LineChart
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MonthBar
import com.software.sello.designsystem.component.MonthCell
import com.software.sello.designsystem.component.MonthGrid
import com.software.sello.designsystem.component.MonthSwitcher
import com.software.sello.designsystem.component.PastMonthRibbon
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.component.SelloScaffold
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipHeading
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.delay

@Composable
private fun Stack(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.slipGap)) { content() }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = SelloTheme.colors.inkSoft)
}

// ---- Categories ----

/** A synthetic category: what was spent against an optional limit. */
private class CategoryFixture(
    val name: String,
    val icon: SelloIcon,
    val spent: Long,
    val limit: Long?
) {
    val left: Long? get() = limit?.let { it - spent }
    val over: Boolean get() = (left ?: 0) < 0
    val fraction: Float? get() = limit?.let { if (it == 0L) 1f else spent.toFloat() / it }

    val description: String
        get() = when (val remaining = left) {
            null -> "$name. Gastado ${copFixture(spent).spoken}. Sin límite."

            else -> if (remaining < 0) {
                "$name. Pasado por ${copFixture(
                    -remaining
                ).spoken} de ${copFixture(limit!!).spoken}."
            } else {
                "$name. Quedan ${copFixture(remaining).spoken} de ${copFixture(limit!!).spoken}."
            }
        }
}

private val Categories = listOf(
    CategoryFixture("Entretenimiento", SelloIcon.Theaters, 171_500, 150_000),
    CategoryFixture("Alimentación", SelloIcon.Restaurant, 612_400, 900_000),
    CategoryFixture("Café", SelloIcon.LocalCafe, 71_200, 80_000),
    CategoryFixture("Hogar", SelloIcon.Home, 0, 220_000),
    CategoryFixture("Transporte sin límite", SelloIcon.DirectionsBus, 86_000, null),
    CategoryFixture("Un nombre de categoría largo", SelloIcon.ReceiptLong, 1, 999_999_999_999)
)

@Composable
fun CategoryCircleExample() {
    var replay by rememberSaveable { mutableIntStateOf(0) }
    Slip(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)) {
            Caption("Vacío, a medias, lleno, pasado y sin límite")
            key(replay) {
                Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
                    CategoryCircle(SelloIcon.Home, 0f, staggerIndex = 0)
                    CategoryCircle(SelloIcon.Restaurant, 0.68f, staggerIndex = 1)
                    CategoryCircle(SelloIcon.LocalCafe, 1f, staggerIndex = 2)
                    CategoryCircle(SelloIcon.Theaters, 1.14f, over = true, staggerIndex = 3)
                    CategoryCircle(
                        SelloIcon.DirectionsBus,
                        null,
                        staggerIndex = 4,
                        unlimited = true
                    )
                }
            }
            Caption("Archivadas: en gris, con o sin límite")
            Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
                CategoryCircle(SelloIcon.Home, 0.4f, animate = false, archived = true)
                CategoryCircle(
                    SelloIcon.LocalCafe,
                    1.2f,
                    over = true,
                    animate = false,
                    archived = true
                )
                CategoryCircle(
                    SelloIcon.ReceiptLong,
                    null,
                    animate = false,
                    unlimited = true,
                    archived = true
                )
            }
            Caption("Tamaños: 28, 46 y 62")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
            ) {
                for (size in listOf(28, 46, 62)) {
                    CategoryCircle(SelloIcon.Restaurant, 0.5f, size = size.dp, animate = false)
                }
            }
            SelloButton("Volver a llenar", { replay++ }, kind = ButtonKind.Outlined)
        }
    }
}

@Composable
fun CategoryCellExample() {
    var opened by rememberSaveable { mutableStateOf("Ninguna") }
    Stack {
        Slip(modifier = Modifier.fillMaxWidth()) {
            CategoryGrid(Categories) { index, category ->
                CategoryCell(
                    name = category.name,
                    icon = category.icon,
                    amount = copFixture(category.left ?: category.spent),
                    fraction = category.fraction,
                    description = category.description,
                    onClick = { opened = category.name },
                    over = category.over,
                    amountCaption = if (category.limit == null) "sin límite" else null,
                    staggerIndex = index,
                    unlimited = category.limit == null,
                    modifier = Modifier.testTag("category:$index"),
                    // A long press, or a screen reader's action, offers this.
                    actions = listOf(
                        LineAction("Editar categoría") { opened = "Editar: ${category.name}" }
                    )
                )
            }
        }
        Text(
            "Abierta: $opened",
            style = MaterialTheme.typography.bodyLarge,
            color = SelloTheme.colors.ink,
            modifier = Modifier.testTag("category:opened")
        )
    }
}

// ---- Navigation ----

private val TwoTabs = listOf(
    SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
    SelloDestination("resumen", "Resumen", SelloIcon.Insights)
)

@Composable
private fun rememberTabs(tabs: List<SelloDestination>): SelloNavigation {
    var selected by rememberSaveable { mutableStateOf(tabs.first().id) }
    return SelloNavigation(tabs, selected) { selected = it }
}

private val MonthNames = listOf(
    "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
)

/** Pretends "today" is October 2026: later months cannot be picked. */
private fun monthsOf(year: Int): List<MonthCell> = MonthNames.mapIndexed { index, name ->
    MonthCell(
        id = "$year-${index + 1}",
        label = name,
        spoken = "$name de $year",
        enabled = year < 2026 || index <= 9,
        hasData = year == 2026 && index in 6..9,
        isCurrent = year == 2026 && index == 9
    )
}

@Composable
fun MonthSwitcherExample() {
    var year by rememberSaveable { mutableIntStateOf(2026) }
    var picked by rememberSaveable { mutableStateOf("2026-10") }
    var opens by rememberSaveable { mutableIntStateOf(0) }
    Stack {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonthSwitcher("Octubre 2026", { opens++ }, Modifier.testTag("month:switcher"))
            Caption("  abierto $opens veces")
        }
        Slip(modifier = Modifier.fillMaxWidth(), pinked = false) {
            MonthGrid(
                yearLabel = year.toString(),
                months = monthsOf(year),
                selectedId = picked,
                onPick = { picked = it },
                previousYearLabel = "Año anterior",
                nextYearLabel = "Año siguiente",
                onPreviousYear = { year-- },
                onNextYear = if (year < 2026) ({ year++ }) else null
            )
        }
        Text(
            "Elegido: $picked",
            style = MaterialTheme.typography.bodyLarge,
            color = SelloTheme.colors.ink,
            modifier = Modifier.testTag("month:picked")
        )
    }
}

/**
 * The frame with real components inside it. Change Window to see it rearrange. The top
 * bar is the v2 month bar; the ribbon slot under it holds a past month until "Volver a
 * octubre" is pressed, and pressing the month brings it back.
 */
@Composable
fun ScaffoldExample() {
    val scroll = rememberScrollState()
    var entries by rememberSaveable { mutableIntStateOf(0) }
    var opened by rememberSaveable { mutableStateOf(Categories.first().name) }
    var past by rememberSaveable { mutableStateOf(true) }
    var metasDock by rememberSaveable { mutableStateOf(false) }
    SelloScaffold(
        title = "Octubre 2026",
        topBar = {
            MonthBar(
                month = if (past) "Septiembre" else "Octubre",
                year = "2026",
                onClick = { past = true },
                description = "Cambiar de mes. " +
                    if (past) "Mes seleccionado: Septiembre 2026" else "Mes actual: Octubre 2026",
                buttonModifier = Modifier.testTag("scaffold:month")
            ) {
                IconButton(onClick = {}) { Icon(SelloIcon.Settings.painter(), "Configuración") }
            }
        },
        ribbon = if (past) {
            {
                PastMonthRibbon(
                    buildAnnotatedString {
                        append("Estás viendo ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("septiembre") }
                    },
                    "Volver a octubre",
                    { past = false },
                    Modifier.testTag("scaffold:ribbon")
                )
            }
        } else {
            null
        },
        blotter = Blotter.Tall,
        blotterScroll = { scroll.value },
        navigation = rememberTabs(TwoTabs),
        // Metas' own action is green with the flag, so it cannot be taken for Anotar.
        dock = if (metasDock) {
            SelloDock(
                stringResource(R.string.catalog_demo_aportar_a_una_meta),
                stringResource(R.string.catalog_demo_aportar_a_una_meta),
                { entries++ },
                tone = ButtonTone.Gain,
                icon = SelloIcon.Flag
            )
        } else {
            SelloDock("Anotar un gasto", "Anotar", { entries++ })
        },
        // The catalog's own chrome already keeps clear of the system bars.
        windowInsets = WindowInsets(0, 0, 0, 0),
        secondaryPane = { padding ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
                Slip(modifier = Modifier.fillMaxWidth()) {
                    Caption("Segundo panel: detalle")
                    Text(opened, style = MaterialTheme.typography.headlineSmall)
                    repeat(SECOND_PANE_LINES) { Caption("Renglón ${it + 1}") }
                }
            }
        }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.slipGap),
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(padding)
        ) {
            Slip(modifier = Modifier.fillMaxWidth()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Caption("Te quedan")
                    Stamp("Al día", StampInk.Good)
                }
                MoneyText(copFixture(937_200), style = MoneyStyle.Hero)
                Caption("Anotaciones de prueba: $entries")
                SelloChip(
                    stringResource(R.string.catalog_demo_dock_de_metas),
                    metasDock,
                    { metasDock = !metasDock },
                    Modifier.testTag("scaffold:metas-dock")
                )
            }
            Slip(modifier = Modifier.fillMaxWidth()) {
                CategoryGrid(Categories) { index, category ->
                    CategoryCell(
                        name = category.name,
                        icon = category.icon,
                        amount = copFixture(category.left ?: category.spent),
                        fraction = category.fraction,
                        description = category.description,
                        onClick = { opened = category.name },
                        over = category.over,
                        amountCaption = if (category.limit == null) "gastado" else null,
                        staggerIndex = index
                    )
                }
            }
            Slip(modifier = Modifier.fillMaxWidth().testTag("scaffold:last")) {
                Caption("Último slip: nunca queda bajo el dock")
            }
        }
    }
}

private const val SECOND_PANE_LINES = 12

// ---- Charts ----

private val Weekdays = listOf("lun", "mar", "mié", "jue", "vie", "sáb", "dom")
private val WeekdaySpend = listOf(23_000L, 48_500L, 21_000L, 49_200L, 12_000L, 86_000L, 24_300L)

private fun weekdayPoints(): List<ChartPoint> = Weekdays.mapIndexed { index, day ->
    val amount = WeekdaySpend[index]
    ChartPoint(
        id = day,
        axisLabel = day,
        value = amount,
        spoken = "$day, ${copFixture(amount).spoken}",
        period = if (index == 5) ChartPeriod.Current else ChartPeriod.Past,
        valueLabel = if (index == 5) "86 mil" else null
    )
}

/** Two amounts floating point cannot tell apart, an over-limit month and one to come. */
private val HugeMonths = listOf(
    Triple("jul", 9_007_199_254_740_992L, ChartPeriod.Past),
    Triple("ago", 9_007_199_254_740_993L, ChartPeriod.Past),
    Triple("sep", 9_100_000_000_000_000L, ChartPeriod.Over),
    Triple("oct", 4_000_000_000_000_000L, ChartPeriod.Current),
    Triple("nov", 6_000_000_000_000_000L, ChartPeriod.Upcoming)
)

private fun hugePoints(): List<ChartPoint> = HugeMonths.map { (month, amount, period) ->
    ChartPoint(month, month, amount, "$month, ${copFixture(amount).spoken}", period)
}

@Composable
private fun SelectableChart(
    question: String,
    points: List<ChartPoint>,
    amounts: Map<String, Long>,
    tag: String,
    chart: @Composable (selected: String?, onSelect: (String) -> Unit) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // The selected value shows in the heading for a moment, then the answer returns.
    LaunchedEffect(selected) {
        if (selected != null) {
            delay(SELECTION_MILLIS)
            selected = null
        }
    }
    val shown = selected ?: points.maxBy { it.value }.id
    Slip(modifier = Modifier.fillMaxWidth()) {
        SlipHeading(if (selected == null) question else shown) {
            MoneyText(
                copFixture(amounts.getValue(shown)),
                Modifier.testTag("$tag:answer"),
                MoneyStyle.Title
            )
        }
        chart(selected) { selected = it }
    }
}

private const val SELECTION_MILLIS = 3_000L

@Composable
fun ColumnsChartExample() {
    Stack {
        Slip {
            SlipHeading("Gasto anotado y margen de esta semana") { Text("70.000 por gastar") }
            ColumnsChart(
                points = listOf(
                    ChartPoint("1", "sem 1", 100_000, "Semana 1: 100.000 pesos anotados"),
                    ChartPoint(
                        "2",
                        "esta",
                        90_000,
                        "Semana actual: 20.000 pesos anotados y 70.000 pesos por gastar",
                        ChartPeriod.Current,
                        stripedFromValue = 20_000
                    )
                ),
                summary = "La parte sólida es gasto anotado y la rayada es lo que queda por gastar"
            )
        }
        Slip {
            SlipHeading("Cinco semanas en un mes") { Text("Desde el 29") }
            ColumnsChart(
                points = fiveWeekPoints(),
                summary = "Gasto por semana. En una columna angosta, la semana es solo su número."
            )
        }
        SelectableChart(
            question = "Tu día más caro",
            points = weekdayPoints(),
            amounts = Weekdays.zip(WeekdaySpend).toMap(),
            tag = "chart:week"
        ) { selected, onSelect ->
            ColumnsChart(
                points = weekdayPoints(),
                summary = "Gasto por día de la semana. El más alto es sábado, con 86.000 pesos.",
                selectedId = selected,
                onSelect = onSelect,
                modifier = Modifier.testTag("chart:week")
            )
        }
        SelectableChart(
            question = "El mes más alto",
            points = hugePoints(),
            amounts = HugeMonths.associate { it.first to it.second },
            tag = "chart:huge"
        ) { selected, onSelect ->
            ColumnsChart(
                points = hugePoints(),
                summary = "Gasto por mes con valores cercanos al máximo.",
                selectedId = selected,
                onSelect = onSelect,
                reference = ChartReference(8_000_000_000_000_000, "Límite 8.000 B"),
                modifier = Modifier.testTag("chart:huge")
            )
        }
        Slip(modifier = Modifier.fillMaxWidth()) {
            SlipHeading("Cada día gastas") {
                Text("Aún nada", style = MaterialTheme.typography.headlineSmall)
            }
            ColumnsChart(
                points = emptyList(),
                summary = "Sin gastos este mes.",
                emptyText = "Cuando anotes gastos, aquí verás tus días."
            )
        }
    }
}

/** A month's fifth week starts on its 29th: at a large text size "sem 2" no longer fits. */
private fun fiveWeekPoints(): List<ChartPoint> {
    val weeks = listOf(
        Triple("1", 120_000L, "120.000"),
        Triple("2", 80_000L, "80.000"),
        Triple("3", 150_000L, "150.000"),
        Triple("4", 60_000L, "60.000")
    )
    return weeks.map { (number, value, spoken) ->
        ChartPoint(
            number,
            "sem $number",
            value,
            "Semana $number: gastado $spoken pesos",
            shortAxisLabel = number
        )
    } + ChartPoint(
        "5",
        "esta",
        30_000,
        "Semana actual: gastado 30.000 pesos",
        ChartPeriod.Current,
        shortAxisLabel = "5"
    )
}

@Composable
fun LineChartExample() {
    val days = listOf("1 oct", "2 oct", "3 oct")
    val cumulative = listOf(10_000L, 25_000L, 30_000L)
    val points = days.mapIndexed { index, day ->
        ChartPoint(day, day, cumulative[index], "$day, ${copFixture(cumulative[index]).spoken}")
    }
    SelectableChart(
        "Así crecen tus gastos",
        points,
        days.zip(cumulative).toMap(),
        "chart:cumulative"
    ) {
            selected,
            onSelect
        ->
        LineChart(
            points,
            "Gasto acumulado: 10.000, 25.000 y 30.000 pesos.",
            selectedId = selected,
            onSelect = onSelect
        )
    }
    SelectableChart(
        question = "El día con más gasto",
        points = weekdayPoints(),
        amounts = Weekdays.zip(WeekdaySpend).toMap(),
        tag = "chart:line"
    ) { selected, onSelect ->
        LineChart(
            points = weekdayPoints(),
            summary = "Gasto por día de la semana como línea.",
            selectedId = selected,
            onSelect = onSelect,
            reference = ChartReference(60_000, "Promedio 60 mil"),
            modifier = Modifier.testTag("chart:line")
        )
    }
}
