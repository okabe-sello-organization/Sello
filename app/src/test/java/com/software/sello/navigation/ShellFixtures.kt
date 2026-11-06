package com.software.sello.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import java.time.YearMonth

/** The frame's state in screen captures: October 2026 is current and shown. */
val octoberShell = ShellState(
    currentMonth = YearMonth.of(2026, 10),
    selectedMonth = YearMonth.of(2026, 10),
    monthPickerOpen = false,
    pickerYear = 2026,
    pendingEntry = null
)

/** The four tabs as SelloApp.kt gives them, with [selected] chosen. */
@Composable
fun appTabs(selected: ShellTab) = SelloNavigation(
    listOf(
        SelloDestination(
            ShellTab.Recibo.id,
            stringResource(R.string.tab_recibo),
            SelloIcon.ReceiptLong
        ),
        SelloDestination(
            ShellTab.Resumen.id,
            stringResource(R.string.tab_resumen),
            SelloIcon.Insights
        ),
        SelloDestination(ShellTab.Metas.id, stringResource(R.string.tab_metas), SelloIcon.Flag),
        SelloDestination(
            ShellTab.Inversiones.id,
            stringResource(R.string.tab_inversiones),
            SelloIcon.AccountBalance
        )
    ),
    selected.id
) {}
