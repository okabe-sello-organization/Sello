package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One receipt line of a [CarryBreakdown]: [label], an amount already formatted with its
 * sign, and an optional [caption] in words under the label ("Te pasaste 20.000 en meses
 * anteriores"). A [MoneySign.Minus] amount is drawn in the loss ink. With [onLabelHelp] the
 * label is underlined and opens what it means.
 */
@Immutable
data class CarryLine(
    val label: String,
    val amount: MoneyTextValue,
    val caption: String? = null,
    val emphasis: LineEmphasis = LineEmphasis.Normal,
    val onLabelHelp: (() -> Unit)? = null
)

/**
 * Base, signed carry, capacity and what was spent, as receipt lines (ADR 0020 CP27): the
 * capacity is never shown without the base and the carry beside it. [footnote] says where
 * the carry comes from and when it was read. Every line is read on its own, "label, amount,
 * caption"; amounts are exact and drawn whole, wrapping under their label at large text.
 */
@Composable
fun CarryBreakdown(
    lines: List<CarryLine>,
    modifier: Modifier = Modifier,
    footnote: String? = null
) {
    Column(modifier.fillMaxWidth().padding(top = SelloTheme.spacing.xs)) {
        lines.forEach { line ->
            LeaderLine(
                line.label,
                caption = line.caption,
                emphasis = line.emphasis,
                onLabelHelp = line.onLabelHelp
            ) {
                MoneyText(
                    line.amount,
                    color = if (line.amount.sign == MoneySign.Minus) {
                        SelloTheme.colors.semantic.loss
                    } else {
                        SelloTheme.colors.ink
                    },
                    fontWeight = if (line.emphasis == LineEmphasis.Strong) {
                        SelloTheme.type.weight.figure
                    } else {
                        SelloTheme.type.weight.strong
                    }
                )
            }
        }
        if (footnote != null) {
            Text(
                footnote,
                style = MaterialTheme.typography.labelSmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(top = SelloTheme.spacing.xs)
            )
        }
    }
}

/**
 * One change in a list shown before saving, such as a later month whose limit with carry
 * moves: [label] (the month), [value] ("180.000 → 200.000 (+20.000)") and an optional
 * [caption] under it ("Restante 110.000 → 130.000"), stronger when [emphasized], as for a
 * verdict that flips. Text is already formatted by the application; [spoken] is the whole
 * row as a screen reader says it, without arrows.
 */
@Composable
fun ChangeRow(
    label: String,
    value: String,
    spoken: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    emphasized: Boolean = false
) {
    FlowRow(
        modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(vertical = SelloTheme.spacing.xs)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = SelloTheme.type.lineTitle,
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f, fill = true)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = SelloTheme.type.weight.strong,
                    fontFeatureSettings = "tnum"
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = with(SelloTheme.type.weight) {
                            if (emphasized) emphasis else regular
                        },
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (emphasized) SelloTheme.colors.ink else SelloTheme.colors.inkSoft,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** One month of a [CarryCorrectionContent], as a [ChangeRow] draws it; every text is written. */
@Immutable
data class CarryCorrectionLine(
    val label: String,
    val value: String,
    val spoken: String,
    val caption: String? = null,
    val emphasized: Boolean = false
)

/** One category of a [CarryCorrectionContent]: its [icon] when known, [name] and [lines]. */
@Immutable
data class CarryCorrectionGroup(
    val name: String,
    val icon: SelloIcon?,
    val lines: List<CarryCorrectionLine>
)

/**
 * What a correction changes, as the content of a confirmation (ADR 0020 CP20–CP22):
 * [changed] in warn ink when the list was read again because the data moved, [what] the
 * correction in words, a [lead] heading, then each category with its months, and
 * [footer] under them. Every text arrives written.
 */
@Composable
fun CarryCorrectionContent(
    groups: List<CarryCorrectionGroup>,
    footer: String,
    changed: String? = null,
    what: String? = null,
    lead: String? = null
) {
    Column(
        Modifier.testTag(CARRY_CORRECTION_TAG),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (changed != null) {
            Text(
                changed,
                style = MaterialTheme.typography.bodyMedium,
                color = SelloTheme.colors.semantic.warn,
                modifier = Modifier.padding(
                    bottom = SelloTheme.spacing.sm
                ).testTag(CARRY_CORRECTION_CHANGED_TAG)
            )
        }
        what?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SelloTheme.colors.inkSoft)
        }
        if (lead != null) {
            Text(
                lead,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = SelloTheme.spacing.sm).semantics { heading() }
            )
        }
        groups.forEach { group ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
                modifier = Modifier.padding(top = SelloTheme.spacing.sm)
            ) {
                group.icon?.let {
                    Icon(it.painter(), null, Modifier.size(18.dp), SelloTheme.colors.brand)
                }
                Text(group.name, style = MaterialTheme.typography.titleSmall)
            }
            group.lines.forEach {
                ChangeRow(
                    it.label,
                    it.value,
                    it.spoken,
                    caption = it.caption,
                    emphasized = it.emphasized
                )
            }
        }
        Text(
            footer,
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft,
            modifier = Modifier.padding(top = SelloTheme.spacing.sm)
        )
    }
}

const val CARRY_CORRECTION_TAG = "carry:correction"

const val CARRY_CORRECTION_CHANGED_TAG = "carry:correction.changed"
