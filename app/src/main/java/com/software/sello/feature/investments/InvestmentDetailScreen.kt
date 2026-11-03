package com.software.sello.feature.investments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MovementBalance
import com.software.sello.designsystem.component.MovementMonthLine
import com.software.sello.designsystem.component.MovementRow
import com.software.sello.designsystem.component.MovementTone
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.presentation.date.DateLabels
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth

/**
 * One investment (v2 page): "Vale" with Gana / Pierde, the value, the signed return,
 * when the value was recorded and since when, what produced it and **Anotar el valor de
 * hoy**; the recorded values as dots with what was put in as steps; then the movements,
 * newest first, each opening its correction. Everything stays in its own currency.
 */
@Composable
fun InvestmentDetailScreen(
    state: InvestmentDetailState,
    onAction: (InvestmentDetailAction) -> Unit,
    dates: DateLabels,
    padding: PaddingValues,
    months: MonthNames = monthNames()
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(vertical = SelloTheme.spacing.lg).testTag(INVESTMENT_DETAIL_TAG),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) {
        when (state) {
            InvestmentDetailState.Loading, InvestmentDetailState.Gone ->
                SlipSkeleton(Modifier.testTag("investment.loading"))

            InvestmentDetailState.Failed -> ErrorSlip(
                stringResource(R.string.inversiones_failed_body),
                { onAction(InvestmentDetailAction.Retry) },
                Modifier.testTag("investment.failed"),
                title = stringResource(R.string.investment_failed_title),
                icon = SelloIcon.Error
            )

            is InvestmentDetailState.Ready -> {
                if (state.stale) {
                    ErrorSlip(
                        stringResource(R.string.investment_stale),
                        { onAction(InvestmentDetailAction.Retry) },
                        Modifier.testTag("investment.stale")
                    )
                }
                Answer(state, dates, onAction)
                state.chart?.takeIf {
                    it.values.isNotEmpty()
                }?.let { Chart(it, state, dates, onAction) }
                Movements(state, dates, months, onAction)
            }
        }
    }
}

@Composable
private fun Answer(
    state: InvestmentDetailState.Ready,
    dates: DateLabels,
    onAction: (InvestmentDetailAction) -> Unit
) {
    val value = state.value
    val record = @Composable {
        SelloButton(
            stringResource(R.string.investment_record_value),
            { onAction(InvestmentDetailAction.RecordValue) },
            Modifier.fillMaxWidth()
                .padding(top = SelloTheme.spacing.md)
                .testTag(INVESTMENT_RECORD_VALUE_TAG),
            kind = ButtonKind.Outlined,
            icon = SelloIcon.Insights,
            tone = ButtonTone.Ink
        )
    }
    val since = dates.monthYear(state.since)
    if (value == null || state.`return` == null || state.gain == null) {
        Slip(Modifier.fillMaxWidth().testTag("investment.answer")) {
            Text(stringResource(R.string.investment_value_label), color = SelloTheme.colors.inkSoft)
            Text(
                stringResource(R.string.investment_no_value_answer),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Text(
                stringResource(R.string.investment_since, since),
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
            TearLine()
            LeaderLine(stringResource(R.string.investment_contributed)) {
                MoneyText(state.contributed)
            }
            record()
        }
        return
    }
    val gain = state.`return`.direction
    val stamp = when (gain) {
        GainDirection.Gain -> stringResource(R.string.investment_stamp_gain)
        GainDirection.Loss -> stringResource(R.string.investment_stamp_loss)
        GainDirection.Even -> null
    }
    AnswerSlip(
        label = stringResource(R.string.investment_value_label),
        amount = value,
        stamp = stamp,
        ink = if (gain == GainDirection.Loss) StampInk.Over else StampInk.Good,
        modifier = Modifier.testTag("investment.answer"),
        description = listOfNotNull(
            stringResource(R.string.investment_value_label),
            value.spoken,
            stamp
        ).joinToString(", "),
        lossAmount = false
    ) {
        Performance(state.`return`, state.gain, Modifier.padding(top = 6.dp))
        Text(
            stringResource(
                R.string.investment_valued_since,
                dates.shortDay(state.valuedOn!!, state.today),
                since
            ),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = 2.dp).testTag("investment.valued")
        )
        TearLine()
        LeaderLine(stringResource(R.string.investment_contributed)) { MoneyText(state.contributed) }
        LeaderLine(stringResource(R.string.investment_withdrawn)) { MoneyText(state.withdrawn) }
        LeaderLine(stringResource(R.string.investment_dividends)) { MoneyText(state.dividends) }
        LeaderLine(stringResource(R.string.investment_gain)) {
            MoneyText(
                state.gain,
                color = when (gain) {
                    GainDirection.Gain -> SelloTheme.colors.semantic.gain
                    GainDirection.Loss -> SelloTheme.colors.semantic.loss
                    GainDirection.Even -> SelloTheme.colors.ink
                }
            )
        }
        record()
    }
}

@Composable
private fun Chart(
    chart: ValuesChartModel,
    state: InvestmentDetailState.Ready,
    dates: DateLabels,
    onAction: (InvestmentDetailAction) -> Unit
) {
    // The line above the chart reads the picked value, the newest until another is tapped.
    val picked = chart.values.firstOrNull { it.id == state.selectedValue } ?: chart.values.last()
    Slip(Modifier.fillMaxWidth().testTag("investment.chart"), pinked = false) {
        Text(
            stringResource(R.string.investment_chart_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            modifier = Modifier.semantics { heading() }.padding(bottom = SelloTheme.spacing.xs)
        )
        Picked(picked, dates)
        RecordedValuesChart(
            values = chart.values.map {
                RecordedMark(
                    it.id,
                    it.x,
                    it.y,
                    stringResource(
                        R.string.investment_chart_value,
                        dates.dayMonthYear(it.date),
                        it.amount.spoken,
                        it.putIn?.spoken.orEmpty()
                    )
                )
            },
            contributions = chart.putIn.map { RecordedMark(it.id, it.x, it.y, it.amount.spoken) },
            grid = chart.grid.map { GridMark(it.y, axisLabel(it)) },
            summary = stringResource(
                R.string.investment_chart_summary,
                dates.monthYear(chart.start),
                dates.monthYear(chart.end)
            ),
            selectedId = picked.id,
            onSelect = { onAction(InvestmentDetailAction.SelectValue(it)) },
            axis = Triple(
                dates.monthShortYear(chart.start),
                dates.monthShortYear(chart.middle),
                dates.monthShortYear(chart.end)
            ),
            legend = stringResource(R.string.investment_chart_values) to
                stringResource(R.string.investment_chart_put)
        )
    }
}

/** The picked value's day, what it was worth and what was put in by then. */
@Composable
private fun Picked(point: ChartPoint, dates: DateLabels) {
    val colors = SelloTheme.colors
    val style = MaterialTheme.typography.bodySmall
    Row(
        Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.xs)
            .semantics(mergeDescendants = true) {}
            .testTag("investment.chart.picked"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
    ) {
        Text(
            dates.dayMonthYear(point.date),
            Modifier.weight(1f),
            style = style,
            color = colors.inkSoft
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.investment_chart_picked_value) + " ",
                style = style,
                color = colors.inkSoft
            )
            MoneyText(point.amount, textStyle = style, fontWeight = FontWeight.W800)
        }
        point.putIn?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.investment_chart_picked_put) + " ",
                    style = style,
                    color = colors.inkSoft
                )
                MoneyText(it, textStyle = style, fontWeight = FontWeight.W800)
            }
        }
    }
}

@Composable
private fun axisLabel(mark: AxisMark): String = when (mark.unit) {
    AxisUnit.One -> mark.figure
    AxisUnit.Thousands -> stringResource(R.string.investment_axis_thousands, mark.figure)
    AxisUnit.Millions -> stringResource(R.string.investment_axis_millions, mark.figure)
}

@Composable
private fun Movements(
    state: InvestmentDetailState.Ready,
    dates: DateLabels,
    months: MonthNames,
    onAction: (InvestmentDetailAction) -> Unit
) {
    val colors = SelloTheme.colors
    Slip(Modifier.fillMaxWidth().testTag("investment.movements"), pinked = false) {
        Text(
            stringResource(R.string.investment_movements),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
            modifier = Modifier.semantics { heading() }
        )
        val shown = if (state.allMovements) state.movements else state.movements.take(SHORT_LIST)
        // The full list is grouped by month, as the goal's is; the short list is not.
        val month: (InvestmentMovementModel) -> YearMonth = { YearMonth.from(it.date) }
        shown.forEachIndexed { index, movement ->
            if (state.allMovements && (index == 0 || month(shown[index - 1]) != month(movement))) {
                MovementMonthLine(
                    // Another year's month says its year, as its rows' days do.
                    month(movement).let {
                        if (it.year == state.today.year) months.name(it) else months.title(it)
                    },
                    net = null,
                    modifier = Modifier.testTag("investment.movements.month.${month(movement)}")
                )
            }
            val next = shown.getOrNull(index + 1)
            val day = dates.day(movement.date, state.today)
            val kind = stringResource(
                when (movement.kind) {
                    InvestmentMovementKind.Contribution -> R.string.investment_kind_contribution
                    InvestmentMovementKind.Valuation -> R.string.investment_kind_valuation
                    InvestmentMovementKind.Withdrawal -> R.string.investment_kind_withdrawal
                    InvestmentMovementKind.Dividend -> R.string.investment_kind_dividend
                }
            )
            MovementRow(
                // A note names the movement better than its kind, as on the goal.
                title = movement.note ?: kind,
                detail = movement.note?.let {
                    stringResource(R.string.investment_movement_note, kind, day)
                } ?: day,
                amount = movement.amount,
                modifier = Modifier.testTag("investment.movement.${movement.id}")
                    .clickable(role = Role.Button) {
                        onAction(InvestmentDetailAction.OpenMovement(movement.id))
                    },
                icon = when (movement.kind) {
                    InvestmentMovementKind.Valuation -> SelloIcon.Insights
                    InvestmentMovementKind.Withdrawal -> SelloIcon.ArrowBack
                    else -> SelloIcon.Add
                },
                tone = when (movement.kind) {
                    InvestmentMovementKind.Valuation -> MovementTone.Value
                    InvestmentMovementKind.Withdrawal -> MovementTone.Out
                    else -> MovementTone.In
                },
                amountColor = when (movement.kind) {
                    InvestmentMovementKind.Contribution,
                    InvestmentMovementKind.Dividend -> colors.semantic.gain

                    else -> Color.Unspecified
                },
                balance = movement.figure?.let { figure(it) },
                rule = next != null && !(state.allMovements && month(next) != month(movement))
            )
        }
        // The foot (v2 *Anotar varios*): Varios movimientos, and Ver los N while some are hidden.
        Row(verticalAlignment = Alignment.CenterVertically) {
            SelloButton(
                stringResource(R.string.investment_movements_title),
                { onAction(InvestmentDetailAction.RecordSeveral) },
                Modifier.testTag(INVESTMENT_RECORD_SEVERAL_TAG),
                kind = ButtonKind.Text,
                icon = SelloIcon.ReceiptLong
            )
            Spacer(Modifier.weight(1f))
            if (!state.allMovements && state.movements.size > SHORT_LIST) {
                SelloButton(
                    pluralStringResource(
                        R.plurals.investment_movements_all,
                        state.movements.size,
                        state.movements.size
                    ),
                    { onAction(InvestmentDetailAction.ShowAllMovements) },
                    Modifier.testTag("investment.showAll"),
                    kind = ButtonKind.Text
                )
            }
        }
    }
}

/** The figure under a movement's amount (IN37–IN40). */
@Composable
private fun figure(figure: MovementFigure): MovementBalance = when (figure) {
    is MovementFigure.Gain -> when (figure.direction) {
        GainDirection.Gain -> MovementBalance(
            stringResource(R.string.investment_movement_gain),
            figure.amount,
            SelloTheme.colors.semantic.gain
        )

        GainDirection.Loss -> MovementBalance(
            stringResource(R.string.investment_movement_loss),
            figure.amount,
            SelloTheme.colors.semantic.loss
        )

        GainDirection.Even -> MovementBalance(
            stringResource(R.string.investment_movement_even),
            null
        )
    }

    is MovementFigure.PutIn -> MovementBalance(
        stringResource(R.string.investment_movement_put_in),
        figure.amount
    )

    is MovementFigure.Dividends -> MovementBalance(
        stringResource(R.string.investment_movement_dividends),
        figure.amount
    )
}

private const val SHORT_LIST = 4

const val INVESTMENT_DETAIL_TAG = "investment.detail"
const val INVESTMENT_RECORD_VALUE_TAG = "investment.recordValue"
const val INVESTMENT_RECORD_SEVERAL_TAG = "investment.recordSeveral"
