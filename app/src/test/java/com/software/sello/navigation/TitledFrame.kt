package com.software.sello.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.software.sello.presentation.month.monthNames
import java.time.YearMonth

/**
 * A screen in the plain titled frame its destination gives it, October 2026 current and
 * shown, with no dock. For captures whose content alone, such as a loading skeleton, is
 * too plain to tell from a blank drawing.
 */
@Composable
fun TitledFrame(title: String, content: @Composable (PaddingValues) -> Unit) {
    ShellScreen(
        ShellState(
            currentMonth = YearMonth.of(2026, 10),
            selectedMonth = YearMonth.of(2026, 10),
            monthPickerOpen = false,
            pickerYear = 2026,
            pendingEntry = null
        ),
        monthNames(),
        {},
        titleOnly = title,
        entryDock = false,
        content = content
    )
}
