package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One receipt line of a [CarryBreakdown]: [label], an amount already formatted with its
 * sign, and an optional [caption] in words under the label ("Te pasaste 20.000 en meses
 * anteriores"). A [MoneySign.Minus] amount is drawn in the loss ink.
 */
@Immutable
data class CarryLine(
    val label: String,
    val amount: MoneyTextValue,
    val caption: String? = null,
    val emphasis: LineEmphasis = LineEmphasis.Normal
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
    Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
        lines.forEach { line ->
            LeaderLine(line.label, caption = line.caption, emphasis = line.emphasis) {
                MoneyText(
                    line.amount,
                    color = if (line.amount.sign == MoneySign.Minus) {
                        SelloTheme.colors.semantic.loss
                    } else {
                        SelloTheme.colors.ink
                    },
                    fontWeight = if (line.emphasis == LineEmphasis.Strong) {
                        FontWeight.W900
                    } else {
                        FontWeight.W700
                    }
                )
            }
        }
        if (footnote != null) {
            Text(
                footnote,
                style = MaterialTheme.typography.labelSmall,
                color = SelloTheme.colors.inkSoft,
                modifier = Modifier.padding(top = 4.dp)
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
            .padding(vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),
            modifier = Modifier.padding(end = 10.dp)
        )
        Column(Modifier.weight(1f, fill = true)) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.W700,
                    fontFeatureSettings = "tnum"
                ),
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth()
            )
            if (caption != null) {
                Text(
                    caption,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = if (emphasized) FontWeight.W800 else FontWeight.W400,
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
