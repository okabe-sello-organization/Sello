package com.software.sello.feature.investments

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.HelpEntry
import com.software.sello.designsystem.component.HelpSheet

/**
 * What a **?** or an underlined word explains on Inversiones and on an investment's page
 * (SELLO-209): [Value] the total's card, [Chart] the chart of values, [Gain] the word
 * Ganancia and the return beside it, [Rate] the total's "tasa del…". The open one is kept
 * by name in the screen's saved state, so it survives a restart.
 */
enum class InvestmentHelp { Value, Chart, Gain, Rate }

/**
 * The open [help] as a sheet over the whole screen. Back and the scrim call [onClose];
 * "Ver más en la Guía de Sello" calls [onGuide], which opens the
 * Guía's Inversiones page.
 */
@Composable
fun InvestmentHelpSheet(help: InvestmentHelp, onClose: () -> Unit, onGuide: () -> Unit) {
    BackHandler(onBack = onClose)
    val (title, entries, example) = when (help) {
        InvestmentHelp.Value -> Triple(
            R.string.investment_help_value_title,
            listOf(
                R.string.investment_help_value_yours to R.string.investment_help_value_yours_body,
                R.string.investment_help_value_counted to
                    R.string.investment_help_value_counted_body,
                R.string.investment_help_value_dollars to
                    R.string.investment_help_value_dollars_body
            ),
            R.string.investment_help_value_example
        )

        InvestmentHelp.Chart -> Triple(
            R.string.investment_help_chart_title,
            listOf(
                R.string.investment_help_chart_values to
                    R.string.investment_help_chart_values_body,
                R.string.investment_help_chart_put to R.string.investment_help_chart_put_body,
                R.string.investment_help_chart_gain to R.string.investment_help_chart_gain_body
            ),
            R.string.investment_help_chart_example
        )

        InvestmentHelp.Gain -> Triple(
            R.string.investment_help_gain_title,
            listOf(
                R.string.investment_help_gain to R.string.investment_help_gain_body,
                R.string.investment_help_return to R.string.investment_help_return_body
            ),
            R.string.investment_help_gain_example
        )

        InvestmentHelp.Rate -> Triple(
            R.string.investment_help_rate_title,
            listOf(
                R.string.investment_help_rate_date to R.string.investment_help_rate_date_body,
                R.string.investment_help_rate_use to R.string.investment_help_rate_use_body
            ),
            R.string.investment_help_rate_example
        )
    }
    HelpSheet(
        title = stringResource(title),
        entries = entries.map { (term, body) ->
            HelpEntry(stringResource(term), stringResource(body))
        },
        onDismiss = onClose,
        modifier = Modifier.testTag("$INVESTMENT_HELP_TAG.${help.name}"),
        example = stringResource(example),
        onMore = onGuide
    )
}

/** Restores an open sheet saved by name; anything else is no open sheet. */
internal fun investmentHelp(name: String?): InvestmentHelp? =
    InvestmentHelp.entries.firstOrNull { it.name == name }

const val INVESTMENT_HELP_TAG = "investment.help"
