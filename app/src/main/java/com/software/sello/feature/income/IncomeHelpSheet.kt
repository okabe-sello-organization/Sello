package com.software.sello.feature.income

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.software.sello.R
import com.software.sello.designsystem.component.HelpEntry
import com.software.sello.designsystem.component.HelpSheet
import com.software.sello.feature.guide.GuidePage

/** What the **?** on "Entró en {mes}" names for TalkBack: the title of its sheet. */
@Composable
internal fun incomeHelpLabel(): String = stringResource(R.string.income_help_title)

/**
 * The open [help] sheet. Back and the scrim close it; "Ver más en la Guía de Sello" closes
 * it and opens the Guía's Resumen page, where Ingresos is explained.
 */
@Composable
internal fun IncomeHelpSheet(
    help: IncomeHelp,
    onAction: (IncomeHistoryAction) -> Unit,
    onGuide: (GuidePage) -> Unit,
    modifier: Modifier = Modifier
) {
    val close = { onAction(IncomeHistoryAction.CloseHelp) }
    BackHandler(onBack = close)
    val (title, entries, example) = when (help) {
        IncomeHelp.CameIn -> Triple(
            R.string.income_help_title,
            listOf(
                R.string.income_help_came_in to R.string.income_help_came_in_body,
                R.string.income_help_in_budgets to R.string.income_help_in_budgets_body,
                R.string.income_help_unassigned to R.string.income_help_unassigned_body,
                R.string.income_help_plan to R.string.income_help_plan_body
            ),
            R.string.income_help_example
        )

        IncomeHelp.Unassigned -> Triple(
            R.string.income_help_unassigned_title,
            listOf(
                R.string.income_help_unassigned_title to
                    R.string.income_help_unassigned_term_body,
                R.string.income_help_unassigned_negative to
                    R.string.income_help_unassigned_negative_body
            ),
            R.string.income_help_unassigned_example
        )
    }
    HelpSheet(
        title = stringResource(title),
        entries = entries.map { (term, body) ->
            HelpEntry(stringResource(term), stringResource(body))
        },
        onDismiss = close,
        modifier = modifier.testTag("$INCOME_HELP_TAG.${help.name}"),
        example = stringResource(example),
        onMore = {
            close()
            onGuide(GuidePage.Summary)
        }
    )
}

const val INCOME_HELP_TAG = "income.help"
const val INCOME_HERO_HELP_TAG = "income.hero.help"
