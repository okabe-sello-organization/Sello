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

/**
 * Día a día (SELLO-192): the chips and the month's figures, then every day newest first,
 * headed by what was spent that day, on one slip as the approved mock draws it. A row
 * opens its own editor through [onExpense] or [onIncome]; an empty month offers
 * [onRecord]. The search field of SELLO-193 goes at the top of the first slip.
 */
@Composable
fun DayByDayScreen(
    state: DayByDayState,
    onAction: (DayByDayAction) -> Unit,
    onExpense: (String) -> Unit,
    onIncome: (String) -> Unit,
    onRecord: () -> Unit,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(vertical = SelloTheme.spacing.lg)
            .testTag(DAY_BY_DAY_TAG),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) {
        when (state) {
            DayByDayState.Loading -> SlipSkeleton(Modifier.testTag("dayByDay.loading"))

            is DayByDayState.Failed -> StatusSlip(
                kind = StatusKind.LoadFailed(),
                message = stringResource(
                    when (state.problem) {
                        DayByDayProblem.Unread -> R.string.day_by_day_failed
                        DayByDayProblem.TooLarge -> R.string.day_by_day_too_large
                    }
                ),
                modifier = Modifier.testTag("dayByDay.failed"),
                action = StatusAction(StatusSlipDefaults.retryLabel) {
                    onAction(DayByDayAction.Retry)
                }
            )

            is DayByDayState.Ready -> Ready(state, onAction, onExpense, onIncome, onRecord)
        }
    }
}

@Composable
private fun ColumnScope.Ready(
    state: DayByDayState.Ready,
    onAction: (DayByDayAction) -> Unit,
    onExpense: (String) -> Unit,
    onIncome: (String) -> Unit,
    onRecord: () -> Unit
) {
    val months = monthNames()
    if (state.stale) {
        StatusSlip(
            kind = StatusKind.Older,
            message = stringResource(R.string.recibo_stale),
            modifier = Modifier.testTag("dayByDay.stale"),
            action = StatusAction(StatusSlipDefaults.retryLabel) { onAction(DayByDayAction.Retry) }
        )
    }
    // A month with nothing at all says so on its own; a chip that finds nothing keeps the
    // chips, so another can be picked.
    if (state.totalCount == 0L && state.filter == MovementFilter.All) {
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
            action = StatusAction(stringResource(R.string.expense_entry_submit), onClick = onRecord)
        )
        return
    }
    Slip(Modifier.fillMaxWidth().testTag("dayByDay.list"), pinked = false) {
        ChipFlow(Modifier.testTag("dayByDay.chips")) {
            MovementFilter.entries.forEach { filter ->
                SelloChip(
                    stringResource(filter.label()),
                    selected = state.filter == filter,
                    onClick = { onAction(DayByDayAction.Filter(filter)) },
                    modifier = Modifier.testTag("dayByDay.chip.${filter.name}")
                )
            }
        }
        Summary(state)
        if (state.days.isEmpty()) {
            Text(
                stringResource(
                    if (state.filter == MovementFilter.Incomes) {
                        R.string.day_by_day_no_incomes
                    } else {
                        R.string.day_by_day_no_expenses
                    },
                    months.inSentence(state.month)
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(top = SelloTheme.spacing.md).testTag("dayByDay.none")
            )
        }
        val dates = dateLabels()
        state.days.forEach { day ->
            DayHeading(day, dates, state.today)
            day.rows.forEach { row ->
                MovementLine(
                    row,
                    onClick = {
                        when (row) {
                            is DayRow.Spent -> onExpense(row.id)
                            is DayRow.Received -> onIncome(row.id)
                        }
                    }
                )
            }
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
    if (state.pageFailed) {
        StatusSlip(
            kind = StatusKind.ActionFailed(stringResource(R.string.day_by_day_page_failed_title)),
            message = stringResource(R.string.day_by_day_page_failed),
            modifier = Modifier.testTag("dayByDay.pageFailed"),
            action = StatusAction(StatusSlipDefaults.retryLabel) { onAction(DayByDayAction.Retry) }
        )
    }
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
 * category or "Ingreso · fuente", and the amount, an income's in gain ink with its plus.
 * One button that opens the movement's editor.
 */
@Composable
private fun MovementLine(row: DayRow, onClick: () -> Unit) {
    val colors = SelloTheme.colors
    val note = row.note ?: stringResource(R.string.category_detail_no_note)
    val caption = when (row) {
        is DayRow.Spent -> row.categoryName

        is DayRow.Received ->
            stringResource(
                R.string.day_by_day_income_of,
                incomeSourceLabel(row.sourceKey, row.sourceName)
            )
    }
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
