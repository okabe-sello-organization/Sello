package com.software.sello.feature.movements

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.ChipFlow
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SearchField
import com.software.sello.designsystem.component.SelloChip
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.port.MovementFilter
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.count.countText
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.income.incomeSourceLabel
import com.software.sello.presentation.month.monthNames
import java.time.LocalDate
import java.time.YearMonth

/**
 * Día a día (SELLO-192): the search field, the chips and the month's figures, then every
 * day newest first, headed by what was spent that day, on one slip as the approved mock
 * draws it. A row opens its own editor through [onExpense] or [onIncome]; an empty month
 * offers [onRecord]. While [query] holds a search (SELLO-193), the slip lists what [found]
 * found in every month instead, by month.
 */
@Composable
fun DayByDayScreen(
    state: DayByDayState,
    query: String,
    found: SearchState,
    onAction: (DayByDayAction) -> Unit,
    onExpense: (String) -> Unit,
    onIncome: (String) -> Unit,
    onRecord: () -> Unit,
    padding: PaddingValues
) {
    val open = { row: DayRow ->
        when (row) {
            is DayRow.Spent -> onExpense(row.id)
            is DayRow.Received -> onIncome(row.id)
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(vertical = SelloTheme.spacing.lg)
            .testTag(DAY_BY_DAY_TAG),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) {
        val stale = when (found) {
            SearchState.Off -> (state as? DayByDayState.Ready)?.stale == true
            is SearchState.Ready -> found.stale
            else -> false
        }
        if (stale) {
            StatusSlip(
                kind = StatusKind.Older,
                message = stringResource(R.string.recibo_stale),
                modifier = Modifier.testTag("dayByDay.stale"),
                action = StatusAction(StatusSlipDefaults.retryLabel) {
                    onAction(DayByDayAction.Retry)
                }
            )
        }
        // One slip at one place whatever shows in it, so the field keeps its focus and its
        // keyboard as the month's days give way to the results and back.
        Slip(Modifier.fillMaxWidth().testTag("dayByDay.list"), pinked = false) {
            SearchField(
                value = query,
                onValueChange = { onAction(DayByDayAction.Search(it)) },
                placeholder = stringResource(R.string.day_by_day_search),
                clearLabel = stringResource(R.string.day_by_day_search_clear),
                modifier = Modifier.testTag(SEARCH_TAG)
            )
            when (found) {
                SearchState.Off -> if (state is DayByDayState.Ready && !state.emptyMonth) {
                    MonthList(state, onAction, open)
                }

                is SearchState.Loading -> Chips(found.filter, onAction)

                is SearchState.Failed -> Chips(found.filter, onAction)

                is SearchState.Ready -> FoundList(found, onAction, open)
            }
        }
        when (found) {
            SearchState.Off -> MonthNews(state, onAction, onRecord)
            else -> FoundNews(found, onAction)
        }
    }
}

/** A month with nothing at all says so on its own, under the field, with Anotar gasto. */
private val DayByDayState.Ready.emptyMonth: Boolean
    get() = totalCount == 0L && filter == MovementFilter.All

@Composable
private fun Chips(selected: MovementFilter, onAction: (DayByDayAction) -> Unit) {
    ChipFlow(Modifier.testTag("dayByDay.chips")) {
        MovementFilter.entries.forEach { filter ->
            SelloChip(
                stringResource(filter.label()),
                selected = selected == filter,
                onClick = { onAction(DayByDayAction.Filter(filter)) },
                modifier = Modifier.testTag("dayByDay.chip.${filter.name}")
            )
        }
    }
}

@Composable
private fun MonthList(
    state: DayByDayState.Ready,
    onAction: (DayByDayAction) -> Unit,
    open: (DayRow) -> Unit
) {
    val months = monthNames()
    Chips(state.filter, onAction)
    Summary(state)
    val dates = dateLabels()
    state.days.forEach { day ->
        DayHeading(day, dates, state.today)
        day.rows.forEach { row -> MovementLine(row, onClick = { open(row) }) }
    }
    if (state.days.isNotEmpty()) {
        Text(
            pluralStringResource(
                R.plurals.day_by_day_count,
                state.totalCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                countText(state.totalCount),
                months.inSentence(state.month)
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.md).testTag("dayByDay.count")
        )
    }
    if (state.more) {
        SelloButton(
            stringResource(R.string.category_detail_more),
            { onAction(DayByDayAction.More) },
            kind = ButtonKind.Text,
            loading = state.busy,
            modifier = Modifier.testTag("dayByDay.more")
        )
    }
}

/** What the month's list says under its slip: loading, a failure, an empty month or chip. */
@Composable
private fun MonthNews(
    state: DayByDayState,
    onAction: (DayByDayAction) -> Unit,
    onRecord: () -> Unit
) {
    val months = monthNames()
    when (state) {
        DayByDayState.Loading -> SlipSkeleton(Modifier.testTag("dayByDay.loading"))

        is DayByDayState.Failed -> StatusSlip(
            kind = StatusKind.LoadFailed(),
            message = stringResource(state.problem.text()),
            modifier = Modifier.testTag("dayByDay.failed"),
            // Reading again cannot fix a figure too large or damaged data; a change to
            // the month reads it again by itself.
            action = if (state.problem == DayByDayProblem.Unread) {
                StatusAction(StatusSlipDefaults.retryLabel) { onAction(DayByDayAction.Retry) }
            } else {
                null
            }
        )

        is DayByDayState.Ready -> if (state.emptyMonth) {
            StatusSlip(
                kind = StatusKind.Empty(
                    SelloIcon.ReceiptLong,
                    stringResource(R.string.day_by_day_empty_title)
                ),
                message = stringResource(
                    R.string.day_by_day_empty_body,
                    months.inSentence(state.month)
                ),
                modifier = Modifier.testTag("dayByDay.empty"),
                action = StatusAction(
                    stringResource(R.string.expense_entry_submit),
                    onClick = onRecord
                )
            )

        } else {
            // A chip that finds nothing says so as the other news states do, under the chips.
            if (state.days.isEmpty()) {
                StatusSlip(
                    kind = StatusKind.Empty(
                        SelloIcon.ReceiptLong,
                        stringResource(
                            if (state.filter == MovementFilter.Incomes) {
                                R.string.day_by_day_no_incomes
                            } else {
                                R.string.day_by_day_no_expenses
                            },
                            months.inSentence(state.month)
                        )
                    ),
                    message = stringResource(R.string.day_by_day_none_body),
                    modifier = Modifier.testTag("dayByDay.none")
                )
            }
            PageFailed(state.pageFailure, onAction)
        }
    }
}

/** Why the next page could not be listed; what is listed stays above it. */
@Composable
private fun PageFailed(
    problem: DayByDayProblem?,
    onAction: (DayByDayAction) -> Unit,
    inSearch: Boolean = false
) {
    if (problem == null) return
    StatusSlip(
        kind = StatusKind.ActionFailed(stringResource(R.string.day_by_day_page_failed_title)),
        message = stringResource(
            when {
                problem == DayByDayProblem.Unread -> R.string.day_by_day_page_failed
                inSearch -> problem.foundText()
                else -> problem.text()
            }
        ),
        modifier = Modifier.testTag("dayByDay.pageFailed"),
        // Reading again cannot fix a figure too large or damaged data: no Reintentar.
        action = if (problem == DayByDayProblem.Unread) {
            StatusAction(StatusSlipDefaults.retryLabel) { onAction(DayByDayAction.Retry) }
        } else {
            null
        }
    )
}

/**
 * What a search found, as the approved mock draws it: what was searched with the count and
 * sum of every matching expense (and income, when any matched), then each month newest
 * first, headed by its own count and expense sum, and its rows with their day.
 */
@Composable
private fun FoundList(
    found: SearchState.Ready,
    onAction: (DayByDayAction) -> Unit,
    open: (DayRow) -> Unit
) {
    Chips(found.filter, onAction)
    if (found.months.isEmpty()) return
    FoundSummary(found)
    val dates = dateLabels()
    val months = monthNames()
    found.months.forEach { month ->
        MonthHeading(month, months.title(month.month))
        month.rows.forEach { row ->
            MovementLine(
                row.row,
                onClick = { open(row.row) },
                day = if (row.date == found.today) {
                    stringResource(R.string.day_by_day_found_today)
                } else {
                    "${dates.weekday(row.date)} ${row.date.dayOfMonth}"
                }
            )
        }
    }
    if (found.more) {
        SelloButton(
            stringResource(R.string.category_detail_more),
            { onAction(DayByDayAction.MoreFound) },
            kind = ButtonKind.Text,
            loading = found.busy,
            modifier = Modifier.testTag("dayByDay.found.more")
        )
    }
}

/** "En todos los meses · 9 gastos · 214.800", or "Monto exacto 18.000 · …" for an amount. */
@Composable
private fun FoundSummary(found: SearchState.Ready) {
    val colors = SelloTheme.colors
    val soft = MaterialTheme.typography.bodySmall
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg),
        modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag("dayByDay.found.summary")
    ) {
        Text(
            found.amount?.let { stringResource(R.string.day_by_day_found_amount, it.digits) }
                ?: stringResource(R.string.day_by_day_found_everywhere),
            style = soft,
            color = colors.inkSoft
        )
        found.expenses?.let {
            FoundCount(R.plurals.day_by_day_found_expenses, it, Color.Unspecified, "expenses")
        }
        found.incomes?.let {
            FoundCount(R.plurals.day_by_day_found_incomes, it, colors.semantic.gain, "incomes")
        }
    }
}

/** "9 gastos · 214.800": how many matched and what they add up to. */
@Composable
private fun FoundCount(plural: Int, total: FoundTotal, ink: Color, tag: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            pluralStringResource(plural, total.count.asQuantity(), countText(total.count)) + " · ",
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
        MoneyText(total.total, Modifier.testTag("dayByDay.found.$tag"), color = ink)
    }
}

/** "Octubre 2026", then "3 · 61.200": the month's matches and what its expenses add up to. */
@Composable
private fun MonthHeading(month: FoundMonth, title: String) {
    val colors = SelloTheme.colors
    val count = countText(month.count)
    val spoken = pluralStringResource(
        R.plurals.day_by_day_found_month,
        month.count.asQuantity(),
        title,
        count
    ) + (month.spent?.let { ", ${it.spoken}" } ?: "")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.lg)
            .testTag(foundMonthTag(month.month))
            // Said as one heading: "Octubre 2026: 3 encontrados, 61.200 pesos".
            .clearAndSetSemantics {
                heading()
                contentDescription = spoken
            }
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = SelloTheme.type.weight.emphasis
            ),
            color = colors.ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (month.spent == null) count else "$count · ",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = SelloTheme.type.weight.strong
            ),
            color = colors.inkSoft
        )
        month.spent?.let {
            MoneyText(
                it,
                color = colors.inkSoft,
                fontWeight = SelloTheme.type.weight.strong
            )
        }
    }
}

/** What a search says under its slip: reading, a failure, nothing found, a failed page. */
@Composable
private fun FoundNews(found: SearchState, onAction: (DayByDayAction) -> Unit) {
    when (found) {
        SearchState.Off -> Unit

        is SearchState.Loading -> SlipSkeleton(Modifier.testTag("dayByDay.found.loading"))

        // The query stays in the field; Reintentar searches it again.
        is SearchState.Failed -> StatusSlip(
            kind = StatusKind.LoadFailed(),
            message = stringResource(
                if (found.problem == DayByDayProblem.Unread) {
                    R.string.day_by_day_search_failed
                } else {
                    found.problem.foundText()
                }
            ),
            modifier = Modifier.testTag("dayByDay.found.failed"),
            action = if (found.problem == DayByDayProblem.Unread) {
                StatusAction(StatusSlipDefaults.retryLabel) { onAction(DayByDayAction.Retry) }
            } else {
                null
            }
        )

        is SearchState.Ready -> {
            if (found.months.isEmpty()) {
                StatusSlip(
                    kind = StatusKind.Empty(
                        SelloIcon.Search,
                        stringResource(R.string.day_by_day_not_found, found.words)
                    ),
                    message = stringResource(R.string.day_by_day_not_found_body),
                    modifier = Modifier.testTag("dayByDay.found.none")
                )
            }
            PageFailed(found.pageFailure, onAction, inSearch = true)
        }
    }
}

private fun Long.asQuantity() = coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

private fun DayByDayProblem.foundText() = when (this) {
    DayByDayProblem.TooLarge -> R.string.day_by_day_found_too_large
    else -> text()
}

private fun DayByDayProblem.text() = when (this) {
    DayByDayProblem.Unread -> R.string.day_by_day_failed
    DayByDayProblem.TooLarge -> R.string.day_by_day_too_large
    DayByDayProblem.Damaged -> R.string.day_by_day_damaged
}

private fun MovementFilter.label() = when (this) {
    MovementFilter.All -> R.string.day_by_day_all
    MovementFilter.Expenses -> R.string.day_by_day_expenses
    MovementFilter.Incomes -> R.string.day_by_day_incomes
}

/** "Octubre 2026 · Gastaste 1.240.300 · Entró 3.150.000": Recibo's and Resumen's figures. */
@Composable
private fun Summary(state: DayByDayState.Ready) {
    val colors = SelloTheme.colors
    val soft = MaterialTheme.typography.bodySmall
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg),
        modifier = Modifier.padding(top = SelloTheme.spacing.sm).testTag("dayByDay.summary")
    ) {
        Text(monthNames().title(state.month), style = soft, color = colors.inkSoft)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.day_by_day_spent) + " ",
                style = soft,
                color = colors.inkSoft
            )
            MoneyText(state.spent, Modifier.testTag("dayByDay.spent"))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.day_by_day_came_in) + " ",
                style = soft,
                color = colors.inkSoft
            )
            MoneyText(
                state.income,
                Modifier.testTag("dayByDay.income"),
                color = colors.semantic.gain
            )
        }
    }
}

/** "Hoy, mar 6" or "Lun 5", then what was spent that day; a day of incomes alone has none. */
@Composable
private fun DayHeading(day: DayGroup, dates: DateLabels, today: LocalDate) {
    val colors = SelloTheme.colors
    val label = dayLabel(day.date, dates, today)
    Column(Modifier.padding(top = SelloTheme.spacing.lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() }
                .testTag(dayTag(day.date))
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = SelloTheme.type.weight.strong
                ),
                color = colors.inkSoft,
                modifier = Modifier.weight(1f)
            )
            day.spent?.let {
                MoneyText(
                    it,
                    Modifier.testTag("${dayTag(day.date)}.spent"),
                    fontWeight = SelloTheme.type.weight.emphasis
                )
            }
        }
        // A dashed rule under the day, as the mock tears its days apart.
        val rule = colors.rule
        Canvas(Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.xs).height(2.dp)) {
            drawLine(
                rule,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
        }
    }
}

/** "Hoy, mar 6", or the weekday and day with a capital: "Lun 5". */
@Composable
private fun dayLabel(date: LocalDate, dates: DateLabels, today: LocalDate): String {
    val plain = "${dates.weekday(date)} ${date.dayOfMonth}"
    return if (date == today) {
        stringResource(R.string.date_today, plain)
    } else {
        plain.replaceFirstChar { it.titlecase() }
    }
}

/**
 * One movement: the category's icon or the income mark, the note (or *Sin nota*) over the
 * category or "Ingreso · fuente", with its [day] in search results, and the amount, an
 * income's in gain ink with its plus. One button that opens the movement's editor.
 */
@Composable
private fun MovementLine(row: DayRow, onClick: () -> Unit, day: String? = null) {
    val colors = SelloTheme.colors
    val note = row.note ?: stringResource(R.string.category_detail_no_note)
    val kind = when (row) {
        is DayRow.Spent -> row.categoryName

        is DayRow.Received ->
            stringResource(
                R.string.day_by_day_income_of,
                incomeSourceLabel(row.sourceKey, row.sourceName)
            )
    }
    val caption = day?.let { stringResource(R.string.day_by_day_found_caption, kind, it) } ?: kind
    val fixed = row is DayRow.Spent && row.fixed
    val fixedWord = stringResource(R.string.day_by_day_fixed)
    val spoken = listOfNotNull(
        note,
        fixedWord.takeIf { fixed },
        caption,
        row.amount.spoken
    ).joinToString(", ")
    Column(
        Modifier.fillMaxWidth()
            .testTag(movementTag(row.id))
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier.heightIn(min = 54.dp).padding(vertical = SelloTheme.spacing.xs)
        ) {
            when (row) {
                is DayRow.Spent -> CategoryCircle(
                    CategoryIcons.iconFor(row.iconKey),
                    fraction = null,
                    size = 32.dp,
                    animate = false,
                    archived = row.archived
                )

                is DayRow.Received -> IncomeMark()
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        note,
                        style = if (row.note != null) {
                            MaterialTheme.typography.bodyLarge
                        } else {
                            MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic)
                        },
                        color = if (row.note != null) colors.ink else colors.inkSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (fixed) FixedTag(fixedWord)
                }
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            MoneyText(
                row.amount,
                color = if (row is DayRow.Received) colors.semantic.gain else Color.Unspecified
            )
        }
        HorizontalDivider(thickness = 1.dp, color = colors.rule.copy(alpha = ROW_RULE_ALPHA))
    }
}

/** An income's mark: the payments icon in a gain ring, the size of a row's category circle. */
@Composable
private fun IncomeMark() {
    val gain = SelloTheme.colors.semantic.gain
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(32.dp).clip(CircleShape)
            .background(SelloTheme.colors.paper)
            .border(2.dp, gain, CircleShape)
    ) {
        Icon(SelloIcon.Payments.painter(), null, Modifier.size(16.dp), tint = gain)
    }
}

/** "Fijo": the expense was recorded by a fixed payment. */
@Composable
private fun FixedTag(text: String) {
    val colors = SelloTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = SelloTheme.type.weight.strong
        ),
        color = colors.onBrandSoft,
        maxLines = 1,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(colors.brandSoft, FixedTagShape)
            .padding(horizontal = 7.dp, vertical = 1.dp)
            .testTag(FIXED_TAG)
    )
}

/** The small tag behind Fijo, as behind a future expense's note (no shape token). */
private val FixedTagShape = RoundedCornerShape(6.dp)

/** Rows are parted by a lighter rule than a day's heading (no alpha token). */
private const val ROW_RULE_ALPHA = 0.5f

const val DAY_BY_DAY_TAG = "dayByDay"
const val FIXED_TAG = "dayByDay.fixed"

fun movementTag(id: String) = "dayByDay.row.$id"

fun dayTag(date: LocalDate) = "dayByDay.day.$date"

fun foundMonthTag(month: YearMonth) = "dayByDay.found.month.$month"

const val SEARCH_TAG = "dayByDay.search"
