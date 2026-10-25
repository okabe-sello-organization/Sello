package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A dashed outline drawn over the content's bounds: 5dp dashes, 4dp gaps, [width] wide,
 * with [corner] radius (half the height or more draws a pill). For things that are a
 * plan or an offer rather than a record, never for recorded money.
 */
internal fun Modifier.dashedOutline(color: Color, width: Dp, corner: Dp): Modifier =
    drawWithContent {
        drawContent()
        val stroke = width.toPx()
        val radius = minOf(corner.toPx(), size.height / 2) - stroke / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius),
            style = Stroke(
                stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            )
        )
    }

/**
 * A plan beside the records, not on the desk's paper: a dashed outline on the
 * background, [tag] in the brand's small capitals as its heading and [caption] at the
 * other end, then [content], usually receipt lines and [ExpectedChip]s.
 */
@Composable
fun PlanSlip(
    tag: String,
    caption: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = SelloTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .dashedOutline(colors.outline, 2.dp, 14.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Text(
                text = tag.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.W800,
                    letterSpacing = 0.6.sp
                ),
                color = colors.brand,
                modifier = Modifier.alignByBaseline().semantics {
                    heading()
                    contentDescription = tag
                }
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).alignByBaseline()
            )
        }
        content()
    }
}

/**
 * One amount still expected, such as "Salario · vie 30 oct · 1.000.000": a dashed pill
 * with a calendar, [text] soft and the [amount] in ink. It is information, not a
 * choice: it cannot be tapped. [spoken] is the whole sentence a screen reader says,
 * with the amount spoken exactly.
 */
@Composable
fun ExpectedChip(
    text: String,
    amount: MoneyTextValue,
    spoken: String,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .heightIn(min = 32.dp)
            .dashedOutline(colors.outline, 1.5.dp, 999.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = spoken }
    ) {
        Icon(SelloIcon.Event.painter(), null, Modifier.size(16.dp), colors.brand)
        // The text wraps, never the amount, so large text keeps the whole figure in the pill.
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkSoft,
            modifier = Modifier.weight(1f, fill = false)
        )
        MoneyText(
            amount,
            style = MoneyStyle.Line,
            color = colors.ink,
            fontWeight = FontWeight.W700,
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}
