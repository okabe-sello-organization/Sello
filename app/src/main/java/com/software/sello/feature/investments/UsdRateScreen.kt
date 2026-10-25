package com.software.sello.feature.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.EmptySlip
import com.software.sello.designsystem.component.ErrorSlip
import com.software.sello.designsystem.component.FactLine
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.date.DateLabels

/**
 * Dólar (v2 *Dólares y la tasa*): "1 dólar vale" with the current rate and its date on
 * a pinked slip, what it is for, **Cambiar la tasa**, then the earlier rates, newest
 * date first. Rates are never deleted (rule 11). Loading and a failed read are never
 * "no rate".
 */
@Composable
fun UsdRateScreen(
    state: UsdRateState,
    onAction: (UsdRateAction) -> Unit,
    dates: DateLabels,
    padding: PaddingValues
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)
            .padding(vertical = SelloTheme.spacing.lg).testTag(USD_RATE_TAG),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg)
    ) {
        when (state) {
            UsdRateState.Loading -> SlipSkeleton(Modifier.testTag("usd.rate.loading"))

            UsdRateState.Failed -> ErrorSlip(
                stringResource(R.string.usd_rate_failed_body),
                { onAction(UsdRateAction.Retry) },
                Modifier.testTag("usd.rate.failed"),
                title = stringResource(R.string.usd_rate_failed_title),
                icon = SelloIcon.Error
            )

            is UsdRateState.Ready -> {
                if (state.stale) {
                    ErrorSlip(
                        stringResource(R.string.usd_rate_stale),
                        { onAction(UsdRateAction.Retry) },
                        Modifier.testTag("usd.rate.stale")
                    )
                }
                val current = state.current
                if (current == null) {
                    EmptySlip(
                        title = stringResource(R.string.usd_rate_empty_title),
                        body = stringResource(R.string.usd_rate_empty_body),
                        icon = SelloIcon.AccountBalance,
                        modifier = Modifier.testTag("usd.rate.empty"),
                        actionLabel = stringResource(R.string.usd_rate_enter),
                        onAction = { onAction(UsdRateAction.Change) }
                    )
                } else {
                    Current(current, state, dates, onAction)
                    if (state.history.isNotEmpty()) {
                        Slip(Modifier.fillMaxWidth().testTag("usd.rate.history"), pinked = false) {
                            Text(
                                stringResource(R.string.usd_rate_history),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() }.padding(bottom = 4.dp)
                            )
                            state.history.forEach { rate ->
                                LeaderLine(dates.dayMonthYear(rate.date), rate.rate)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Current(
    current: RateModel,
    state: UsdRateState.Ready,
    dates: DateLabels,
    onAction: (UsdRateAction) -> Unit
) {
    val day = dates.shortDay(current.date, state.today)
    Slip(Modifier.fillMaxWidth().testTag("usd.rate.current")) {
        val spoken = stringResource(R.string.usd_rate_spoken, current.rate, day)
        Column(Modifier.semantics(mergeDescendants = true) { contentDescription = spoken }) {
            Text(stringResource(R.string.usd_rate_label), color = SelloTheme.colors.inkSoft)
            Text(
                current.rate,
                style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = null),
                modifier = Modifier.padding(vertical = 4.dp)
            )
            Text(
                stringResource(R.string.usd_rate_caption, day),
                style = MaterialTheme.typography.bodySmall,
                color = SelloTheme.colors.inkSoft
            )
        }
        TearLine()
        FactLine(AnnotatedString(stringResource(R.string.usd_rate_fact)))
        SelloButton(
            stringResource(R.string.usd_rate_change),
            { onAction(UsdRateAction.Change) },
            Modifier.fillMaxWidth().padding(top = 12.dp).testTag(USD_RATE_CHANGE_TAG),
            kind = ButtonKind.Outlined,
            icon = SelloIcon.Autorenew
        )
    }
}

const val USD_RATE_TAG = "usd.rate"
const val USD_RATE_CHANGE_TAG = "usd.rate.change"
