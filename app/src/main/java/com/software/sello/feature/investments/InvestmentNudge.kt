package com.software.sello.feature.investments

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.software.sello.R
import com.software.sello.designsystem.component.NudgeRow
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The investments nudge under the answer (SELLO-213): money put in or taken out is a
 * movement, from Anotar movimiento, and the value is written by hand when it changes. A ✕.
 */
@Composable
internal fun InvestmentNudge(onAction: (InvestmentDetailAction) -> Unit) {
    val template = stringResource(R.string.investment_nudge)
    val part = stringResource(R.string.inversiones_dock_move)
    val (before, after) = template.split("%1\$s", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
    val ink = SelloTheme.colors.ink
    val weight = SelloTheme.type.weight.emphasis
    NudgeRow(
        icon = SelloIcon.Insights,
        text = buildAnnotatedString {
            append(before)
            withStyle(SpanStyle(color = ink, fontWeight = weight)) { append(part) }
            append(after)
        },
        onClose = { onAction(InvestmentDetailAction.CloseNudge) },
        closeLabel = stringResource(R.string.investment_nudge_close),
        modifier = Modifier.testTag(INVESTMENT_NUDGE_TAG)
    )
}

const val INVESTMENT_NUDGE_TAG = "investment.nudge"
