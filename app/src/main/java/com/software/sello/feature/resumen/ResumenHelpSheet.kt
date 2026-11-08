package com.software.sello.feature.resumen

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.HelpEntry
import com.software.sello.designsystem.component.HelpSheet

/** A help sheet's words: its title, its entries as term and explanation, and its example. */
private class HelpCopy(
    @StringRes val title: Int,
    val entries: List<Pair<Int, Int>>,
    @StringRes val example: Int
)

private fun ResumenHelp.copy(): HelpCopy = when (this) {
    ResumenHelp.Month -> HelpCopy(
        R.string.resumen_help_month_title,
        listOf(
            R.string.resumen_help_month_same_days to R.string.resumen_help_month_same_days_body,
            R.string.resumen_help_month_closed to R.string.resumen_help_month_closed_body,
            R.string.resumen_help_month_percent to R.string.resumen_help_month_percent_body,
            R.string.resumen_help_month_none to R.string.resumen_help_month_none_body
        ),
        R.string.resumen_help_month_example
    )

    ResumenHelp.SameDay -> HelpCopy(
        R.string.resumen_help_same_day_title,
        listOf(R.string.resumen_help_same_day_title to R.string.resumen_help_same_day_body),
        R.string.resumen_help_same_day_example
    )

    ResumenHelp.SmallExpenses -> HelpCopy(
        R.string.resumen_help_small_title,
        listOf(
            R.string.resumen_help_small_counts to R.string.resumen_help_small_counts_body,
            R.string.resumen_help_small_line to R.string.resumen_help_small_line_body,
            R.string.resumen_help_small_shows to R.string.resumen_help_small_shows_body,
            R.string.resumen_help_small_share to R.string.resumen_help_small_share_body
        ),
        R.string.resumen_help_small_example
    )

    ResumenHelp.Weekdays -> HelpCopy(
        R.string.resumen_help_weekdays_title,
        listOf(
            R.string.resumen_help_weekdays_average to R.string.resumen_help_weekdays_average_body,
            R.string.resumen_help_weekdays_period to R.string.resumen_help_weekdays_period_body,
            R.string.resumen_help_weekdays_busiest to R.string.resumen_help_weekdays_busiest_body,
            R.string.resumen_help_weekdays_shows to R.string.resumen_help_weekdays_shows_body
        ),
        R.string.resumen_help_weekdays_example
    )
}

/**
 * What a **?** on a Resumen card names for TalkBack: the title of the sheet it opens, or,
 * where that title repeats the card's own, what the sheet answers.
 */
@Composable
internal fun resumenHelpLabel(help: ResumenHelp): String = stringResource(
    if (help == ResumenHelp.SmallExpenses) R.string.resumen_help_small_label else help.copy().title
)

/**
 * Resumen's own Back, which returns to Recibo: composed after the screen, so it would win
 * over an open help sheet's Back, as after a restart with the sheet open. While a sheet is
 * open it steps aside, and Back closes the sheet.
 */
@Composable
fun ResumenTabBack(state: ResumenState, enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled && (state as? ResumenState.Ready)?.help == null, onBack = onBack)
}

/**
 * The open [help] sheet. Back and the scrim close it; "Ver más en la Guía de Sello" closes
 * it and opens the Guía (its Resumen page), so coming back finds the screen as it was.
 */
@Composable
internal fun ResumenHelpSheet(
    help: ResumenHelp,
    onAction: (ResumenAction) -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val close = { onAction(ResumenAction.CloseHelp) }
    BackHandler(onBack = close)
    val copy = help.copy()
    HelpSheet(
        title = stringResource(copy.title),
        entries = copy.entries.map { (term, body) ->
            HelpEntry(stringResource(term), stringResource(body))
        },
        onDismiss = close,
        modifier = modifier.testTag("$RESUMEN_HELP_TAG.${help.name}"),
        example = stringResource(copy.example),
        onMore = {
            close()
            onOpenGuide()
        }
    )
}

const val RESUMEN_HELP_TAG = "resumen.help"
