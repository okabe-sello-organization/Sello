package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One payment or income that repeats: its [icon] in a circle, [name] over [caption] (the
 * category, the rule, the next date), an optional [pausedLabel] stamp and the [amount],
 * with an optional date [pill] under it. One still [soon] to come is in soft ink; an
 * [income] is in gain ink. [recorded], for one whose month already went through, shows a
 * check instead of its icon and softens a payment's amount. [dimmed], for one paused or
 * ended, softens it while every text keeps its contrast. The whole row is one button read
 * as [spoken]. A rule separates it from the next row unless it is the [last].
 */
@Composable
fun ScheduleRow(
    name: String,
    caption: String,
    amount: MoneyTextValue,
    icon: SelloIcon,
    onClick: () -> Unit,
    spoken: String,
    modifier: Modifier = Modifier,
    soon: Boolean = false,
    income: Boolean = false,
    pausedLabel: String? = null,
    dimmed: Boolean = false,
    last: Boolean = false,
    pill: String? = null,
    recorded: Boolean = false
) {
    val colors = SelloTheme.colors
    val amountInk = when {
        dimmed || soon -> colors.inkSoft
        income -> colors.semantic.gain
        recorded -> colors.inkSoft
        else -> colors.ink
    }
    val figure: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.End) {
            MoneyText(
                amount,
                color = amountInk,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = with(SelloTheme.type.weight) { if (soon) strong else emphasis }
                )
            )
            if (pill != null) DatePill(pill, Modifier.padding(top = 3.dp))
        }
    }
    val stamp: @Composable () -> Unit = {
        if (pausedLabel != null) Stamp(pausedLabel, StampInk.Neutral)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .drawBehind {
                if (!last) {
                    val line = 1.dp.toPx()
                    drawLine(
                        colors.rule,
                        Offset(0f, size.height - line / 2),
                        Offset(size.width, size.height - line / 2),
                        line
                    )
                }
            }
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick {
                    onClick()
                    true
                }
            }
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .background(if (recorded) colors.paperDim else colors.paperAlt, CircleShape)
        ) {
            Icon(
                if (recorded) SelloIcon.Check.painter() else icon.painter(),
                null,
                Modifier.size(19.dp),
                when {
                    recorded -> colors.semantic.gain
                    dimmed -> colors.inkSoft
                    else -> colors.brand
                }
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                name,
                style = SelloTheme.type.rowTitle,
                color = if (dimmed || recorded) colors.inkSoft else colors.ink
            )
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            // Under the caption, so a paused row's caption keeps the row's width.
            stamp()
        }
        figure()
    }
}

/** A day in a small brand pill, such as "Mañana, jue 4 feb": information, not a button. */
@Composable
private fun DatePill(text: String, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = SelloTheme.type.weight.strong
        ),
        color = colors.onBrandSoft,
        modifier = modifier
            .background(colors.brandSoft, CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    )
}
