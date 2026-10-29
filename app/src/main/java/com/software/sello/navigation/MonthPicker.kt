package com.software.sello.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.MonthCell
import com.software.sello.designsystem.component.MonthGrid
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.month.MonthNames
import java.time.YearMonth

/**
 * The twelve months of [year]. A month after [current] cannot be picked. A cell's
 * identifier is its month as `yyyy-MM`.
 */
fun monthCells(year: Int, current: YearMonth, names: MonthNames): List<MonthCell> =
    (1..12).map { number ->
        val month = YearMonth.of(year, number)
        MonthCell(
            id = month.toString(),
            label = names.short(month),
            spoken = names.title(month),
            enabled = month <= current,
            isCurrent = month == current
        )
    }

/** The picker's content, the same in a bottom sheet and in a side panel. */
@Composable
fun MonthPicker(
    state: ShellState,
    names: MonthNames,
    onAction: (ShellAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val year = state.pickerYear
    Column(
        modifier = modifier.fillMaxWidth().testTag(MONTH_PICKER_TAG).padding(SelloTheme.spacing.lg)
    ) {
        // The title, and at its right the way back to the month in progress. With a
        // large font the two do not fit on one line and the button goes under the title.
        val current = stringResource(
            R.string.month_picker_current_description,
            names.title(state.currentMonth)
        )
        FlowRow(
            horizontalArrangement = Arrangement.SpaceBetween,
            itemVerticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = SelloTheme.spacing.sm)
        ) {
            Text(
                text = stringResource(R.string.month_picker_title),
                style = MaterialTheme.typography.titleMedium,
                color = SelloTheme.colors.ink,
                modifier = Modifier.semantics { heading() }
            )
            // Always there, and the same as picking the current month's cell: from any
            // year being browsed, one press shows the month in progress.
            SelloButton(
                text = stringResource(R.string.month_picker_current),
                onClick = { onAction(ShellAction.PickMonth(state.currentMonth)) },
                kind = ButtonKind.Text,
                modifier = Modifier
                    .testTag(MONTH_PICKER_CURRENT_TAG)
                    .semantics { contentDescription = current }
            )
        }
        MonthGrid(
            yearLabel = year.toString(),
            months = monthCells(year, state.currentMonth, names),
            selectedId = state.selectedMonth.toString(),
            onPick = { onAction(ShellAction.PickMonth(YearMonth.parse(it))) },
            previousYearLabel = stringResource(R.string.month_picker_previous_year),
            nextYearLabel = stringResource(R.string.month_picker_next_year),
            onPreviousYear = if (year > ShellViewModel.EARLIEST_YEAR) {
                { onAction(ShellAction.ShowPickerYear(year - 1)) }
            } else {
                null
            },
            onNextYear = if (year < state.currentMonth.year) {
                { onAction(ShellAction.ShowPickerYear(year + 1)) }
            } else {
                null
            }
        )
    }
}

const val MONTH_PICKER_TAG = "shell:month-picker"
const val MONTH_PICKER_CURRENT_TAG = "shell:month-picker:current"
