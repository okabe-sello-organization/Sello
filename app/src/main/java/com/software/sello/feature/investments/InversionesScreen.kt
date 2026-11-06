package com.software.sello.feature.investments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.AnswerSlip
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.Footnote
import com.software.sello.designsystem.component.LeaderLine
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.SplitBar
import com.software.sello.designsystem.component.SplitSegment
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.signedText
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.GainDirection
import com.software.sello.domain.model.InvestmentReturn
import com.software.sello.presentation.date.DateLabels

/**
 * Inversiones (docs/design/system/features/investments.html): the value with Gana / Pierde and the
 * signed return, what produced it, when the values were recorded and the rate the total used; then
 * the bar and a row per investment, largest first. Loading and a failed read are never "no
 * investments" or zero. [onOpenRate] and row opening arrive with their screens.
 */
@Composable
fun InversionesScreen(
    state: InversionesState,
    onAction: (InversionesAction) -> Unit,
    dates: DateLabels,
    padding: PaddingValues,
    onOpenRate: (() -> Unit)? = null,
    openRows: Boolean = false,
    onRecordSeveral: (() -> Unit)? = null
) {
    val direction = LocalLayoutDirection.current
    val spacing = SelloTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(INVERSIONES_LIST_TAG),
        contentPadding = PaddingValues(
            start = padding.calculateStartPadding(direction),
            top = padding.calculateTopPadding() + spacing.lg,
            end = padding.calculateEndPadding(direction),
            bottom = padding.calculateBottomPadding() + spacing.lg
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.lg)
    ) {
        when (state) {
            InversionesState.Loading -> item {
                Column(
                    Modifier.testTag("inversiones.loading"),
                    verticalArrangement = Arrangement.spacedBy(spacing.lg)
                ) {
                    SlipSkeleton()
                    SlipSkeleton(lines = 2)
                }
            }

            InversionesState.Failed -> item {
                StatusSlip(
                    kind = StatusKind.LoadFailed(stringResource(R.string.inversiones_failed_title)),
                    message = stringResource(R.string.inversiones_failed_body),
                    modifier = Modifier.testTag("inversiones.failed"),
                    action = StatusAction(StatusSlipDefaults.retryLabel) {
                        onAction(InversionesAction.Retry)
                    }
                )
            }

            is InversionesState.Ready -> {
                if (state.stale) {
                    item {
                        StatusSlip(
                            kind = StatusKind.Older,
                            message = stringResource(R.string.inversiones_stale),
                            modifier = Modifier.testTag("inversiones.stale"),
                            action = StatusAction(StatusSlipDefaults.retryLabel) {
                                onAction(InversionesAction.Retry)
                            }
                        )
                    }
                }
                when {
                    state.empty -> item {
                        StatusSlip(
                            kind = StatusKind.Empty(
                                SelloIcon.TrendingUp,
                                stringResource(R.string.inversiones_empty_title)
                            ),
                            message = stringResource(R.string.inversiones_empty_body),
                            modifier = Modifier.testTag("inversiones.empty")
                        )
                    }

                    else -> {
                        item {
                            val total = state.total
                            if (total == null) {
                                NoTotal(state.missingRate)
                            } else {
                                Total(total, state.rate, state, dates, onOpenRate)
                            }
                        }
                        item { Rows(state, dates, onAction, openRows, onRecordSeveral) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Total(
    total: PortfolioTotalModel,
    rate: RateLineModel?,
    state: InversionesState.Ready,
    dates: DateLabels,
    onOpenRate: (() -> Unit)?
) {
    val gain = total.`return`.direction
    val stamp = when (gain) {
        GainDirection.Gain -> stringResource(R.string.investment_stamp_gain)
        GainDirection.Loss -> stringResource(R.string.investment_stamp_loss)
        GainDirection.Even -> null
    }
    AnswerSlip(
        label = stringResource(R.string.inversiones_value_label),
        amount = total.value,
        stamp = stamp,
        ink = if (gain == GainDirection.Loss) StampInk.Over else StampInk.Good,
        modifier = Modifier.testTag("inversiones.total"),
        description = listOfNotNull(
            stringResource(R.string.inversiones_value_label),
            total.value.spoken,
            stamp
        ).joinToString(", "),
        lossAmount = false
    ) {
        Performance(total.`return`, total.gain, Modifier.padding(top = 6.dp))
        Text(
            stringResource(R.string.inversiones_since, dates.monthYear(total.since)),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = 2.dp)
        )
        TearLine()
        LeaderLine(stringResource(R.string.investment_contributed)) { MoneyText(total.contributed) }
        LeaderLine(stringResource(R.string.investment_withdrawn)) { MoneyText(total.withdrawn) }
        LeaderLine(stringResource(R.string.investment_dividends)) { MoneyText(total.dividends) }
        LeaderLine(stringResource(R.string.investment_gain)) {
            MoneyText(total.gain, color = gainInk(gain))
        }
        val from = dates.shortDay(total.valuedFrom, state.today)
        val to = dates.shortDay(total.valuedTo, state.today)
        // A line that cannot open is as tall as its text; the foot spaces it out itself.
        val quiet = Modifier.padding(vertical = SelloTheme.spacing.sm)
        Footnote(
            if (from == to) {
                bold(stringResource(R.string.inversiones_valued_on, from), from)
            } else {
                bold(stringResource(R.string.inversiones_valued_between, from, to), from, to)
            },
            onClick = null,
            modifier = quiet.testTag("inversiones.valued")
        )
        rate?.let {
            val day = dates.shortDay(it.date, state.today)
            Footnote(
                bold(stringResource(R.string.inversiones_rate_line, it.rate, day), it.rate),
                onOpenRate,
                (if (onOpenRate == null) quiet else Modifier).testTag("inversiones.rate"),
                SelloIcon.Autorenew
            )
        }
    }
}

/** Investments exist but the total cannot be shown: none has a value, or a rate is missing. */
@Composable
private fun NoTotal(missingRate: Boolean) {
    Slip(Modifier.fillMaxWidth().testTag("inversiones.noTotal")) {
        Text(stringResource(R.string.inversiones_value_label), color = SelloTheme.colors.inkSoft)
        Text(
            stringResource(
                if (missingRate) {
                    R.string.inversiones_missing_rate
                } else {
                    R.string.inversiones_no_values
                }
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
    }
}

@Composable
private fun Rows(
    state: InversionesState.Ready,
    dates: DateLabels,
    onAction: (InversionesAction) -> Unit,
    openRows: Boolean,
    onRecordSeveral: (() -> Unit)?
) {
    val valued = state.rows.filter { it.value != null }
    // The bar leaves out a part of 0, so the drawn slices take the inks in turn and
    // neighbours never share one; a part of 0 takes the next ink after them.
    val ranked = valued.partition { it.share > 0f }.let { (drawn, empty) -> drawn + empty }
    Slip(Modifier.fillMaxWidth().testTag("inversiones.rows"), pinked = false) {
        Text(
            stringResource(
                if (state.total == null) R.string.inversiones_list else R.string.inversiones_where
            ),
            // A sentence, not a figure: tabular figures would widen its comma.
            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = null),
            modifier = Modifier.semantics { heading() }.padding(bottom = SelloTheme.spacing.sm)
        )
        if (state.total != null && valued.isNotEmpty()) {
            SplitBar(
                ranked.mapIndexed { rank, row ->
                    val ink = investmentInk(rank)
                    val percent = row.percent?.let { sharePercent(it) }
                    SplitSegment(
                        row.share,
                        percent?.let {
                            stringResource(R.string.investment_bar_share, row.name, it)
                        } ?: row.name,
                        ink.fill,
                        label = percent,
                        labelColor = ink.on
                    )
                },
                Modifier.padding(bottom = 6.dp).testTag("inversiones.bar"),
                gap = 3.dp,
                height = 22.dp
            )
        }
        state.rows.forEachIndexed { index, row ->
            val rank = ranked.indexOf(row)
            val described = row.description
            val detail = when {
                row.valuedOn == null && described != null ->
                    stringResource(R.string.investment_row_no_value_detail, described)

                row.valuedOn == null -> stringResource(R.string.investment_row_no_value)

                described != null -> stringResource(
                    R.string.investment_row_detail,
                    described,
                    dates.shortDay(row.valuedOn, state.today)
                )

                else -> stringResource(
                    R.string.investment_row_valued,
                    dates.shortDay(row.valuedOn, state.today)
                )
            }
            InvestmentRow(
                name = row.name,
                detail = sharedDetail(row.percent, detail),
                icon = InvestmentIcons.iconFor(row.iconKey),
                shade = investmentInk(rank),
                value = row.value,
                missingLabel = stringResource(R.string.investment_no_value),
                secondary = row.`return`?.let { percent(it) }
                    ?: stringResource(R.string.investment_contributed_row, row.contributed.digits),
                onOpen = if (openRows) {
                    { onAction(InversionesAction.Open(row.id)) }
                } else {
                    null
                },
                modifier = Modifier.testTag("inversiones.row.${row.id}"),
                loss = row.`return`?.direction == GainDirection.Loss,
                divider = index < state.rows.lastIndex,
                even = row.`return`?.direction == GainDirection.Even
            )
        }
        // Movements of several investments at once, on one receipt (SELLO-142).
        onRecordSeveral?.let {
            SelloButton(
                text = stringResource(R.string.inversiones_record_several),
                onClick = it,
                kind = ButtonKind.Text,
                icon = SelloIcon.ReceiptLong,
                contentPadding = PaddingValues(end = SelloTheme.spacing.md),
                modifier = Modifier.testTag(INVERSIONES_RECORD_SEVERAL_TAG)
            )
        }
    }
}

const val INVERSIONES_RECORD_SEVERAL_TAG = "inversiones.recordSeveral"

/** "40 %", or "<1 %" for a part above zero that would round to nothing. */
@Composable
private fun sharePercent(percent: SharePercent): String = when (percent) {
    is SharePercent.Whole -> stringResource(R.string.investment_share, percent.value)
    SharePercent.UnderOne -> stringResource(R.string.investment_share_under_one)
}

/** "40 % · valor del 12 oct": the row's part of the total leads its detail line. */
@Composable
private fun sharedDetail(percent: SharePercent?, detail: String): String =
    percent?.let { stringResource(R.string.investment_row_share, sharePercent(it), detail) }
        ?: detail

/** "▲ +5,6 % +2.066.000 de ganancia", or "0,0 % sin ganancia ni pérdida" (IN15). */
@Composable
internal fun Performance(
    `return`: InvestmentReturn,
    gain: MoneyTextValue,
    modifier: Modifier = Modifier
) {
    val words = when (`return`.direction) {
        GainDirection.Gain -> stringResource(R.string.investment_gain_words, gain.signedText())
        GainDirection.Loss -> stringResource(R.string.investment_loss_words, gain.signedText())
        GainDirection.Even -> stringResource(R.string.investment_even_words)
    }
    PerformanceLine(
        percentage = percent(`return`),
        amount = words,
        loss = `return`.direction == GainDirection.Loss,
        modifier = modifier.testTag("inversiones.return"),
        even = `return`.direction == GainDirection.Even
    )
}

/** "+5,6 %", "−4,3 %", "0,0 %": the sign is the gain's, even at "−0,0 %" (IN16). */
@Composable
internal fun percent(`return`: InvestmentReturn): String {
    val sign = when (`return`.direction) {
        GainDirection.Gain -> "+"
        GainDirection.Loss -> "−"
        GainDirection.Even -> ""
    }
    val number = "${`return`.tenths / 10},${`return`.tenths % 10}"
    return stringResource(R.string.investment_percent, sign + number)
}

@Composable
private fun gainInk(direction: GainDirection) = when (direction) {
    GainDirection.Gain -> SelloTheme.colors.semantic.gain
    GainDirection.Loss -> SelloTheme.colors.semantic.loss
    GainDirection.Even -> SelloTheme.colors.ink
}

/** [text] with each of [parts] in bold, as the design writes dates and the rate. */
@Composable
@ReadOnlyComposable
private fun bold(text: String, vararg parts: String): AnnotatedString = buildAnnotatedString {
    var at = 0
    parts.mapNotNull { part -> text.indexOf(part, at).takeIf { it >= 0 }?.let { it to part } }
        .forEach { (start, part) ->
            append(text.substring(at, start))
            withStyle(SpanStyle(fontWeight = SelloTheme.type.weight.emphasis)) { append(part) }
            at = start + part.length
        }
    append(text.substring(at))
}

const val INVERSIONES_LIST_TAG = "inversiones.list"
