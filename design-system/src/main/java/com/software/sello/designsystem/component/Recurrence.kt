package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The sheet that says how something repeats, over the screen it belongs to: a scrim that
 * closes it when tapped, and paper docked at the bottom with [title] as its heading and
 * [content] under it, scrolling when it is taller than the screen. It
 * keeps clear of the system bars. Back is the screen's to handle, as is what lies behind.
 */
@Composable
fun RepeatSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SelloTheme.colors
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .clearAndSetSemantics {}
        )
        Surface(
            color = colors.paper,
            shape = SelloTheme.shapes.sheet,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .padding(top = SheetTopGap)
                .semantics { paneTitle = title }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp, bottom = 10.dp)
                        .semantics { heading() }
                )
                content()
            }
        }
    }
}

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
                    fontWeight = if (soon) FontWeight.W700 else FontWeight.W800
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
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),
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
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),
        color = colors.onBrandSoft,
        modifier = modifier
            .background(colors.brandSoft, CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp)
    )
}

/**
 * Two buttons side by side, sharing the width by [firstWeight] and [secondWeight], or
 * one above the other at full width when a word of either label would not fit its share.
 */
@Composable
fun ButtonPair(
    firstWeight: Float,
    secondWeight: Float,
    modifier: Modifier = Modifier,
    gap: Dp = 10.dp,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit
) {
    Layout(
        contents = listOf(first, second),
        modifier = modifier.fillMaxWidth()
    ) { (firstButton, secondButton), constraints ->
        val space = gap.roundToPx()
        val width = constraints.maxWidth
        val firstWidth = ((width - space) * firstWeight / (firstWeight + secondWeight)).toInt()
        val secondWidth = width - space - firstWidth
        val a = firstButton.single()
        val b = secondButton.single()
        val besides = a.minIntrinsicWidth(Constraints.Infinity) <= firstWidth &&
            b.minIntrinsicWidth(Constraints.Infinity) <= secondWidth
        if (besides) {
            // Both as tall as the taller one, so a label on two lines leaves no step.
            val height = maxOf(a.minIntrinsicHeight(firstWidth), b.minIntrinsicHeight(secondWidth))
            val placedA = a.measure(Constraints.fixedWidth(firstWidth).copy(minHeight = height))
            val placedB = b.measure(Constraints.fixedWidth(secondWidth).copy(minHeight = height))
            layout(width, maxOf(placedA.height, placedB.height)) {
                placedA.placeRelative(0, 0)
                placedB.placeRelative(firstWidth + space, 0)
            }
        } else {
            val full = Constraints.fixedWidth(width)
            val placedA = a.measure(full)
            val placedB = b.measure(full)
            layout(width, placedA.height + space + placedB.height) {
                placedA.placeRelative(0, 0)
                placedB.placeRelative(0, placedA.height + space)
            }
        }
    }
}

private val SheetTopGap = 24.dp

/** The reference's scrim: the desk's darkest ink at 55 %. */
private val Scrim = Color(0xFF050814).copy(alpha = 0.55f)
