package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A dashed rule with a punched notch at each end. It separates the total of a slip
 * from what explains it. [bleed] must equal the slip's side padding so the notches
 * bite the paper's edge. Purely decorative: it has no semantics.
 */
@Composable
fun TearLine(
    modifier: Modifier = Modifier,
    bleed: Dp = SelloTheme.spacing.lg,
    verticalMargin: Dp = 14.dp
) {
    val rule = SelloTheme.colors.rule
    val desk = SelloTheme.colors.desk
    Canvas(
        modifier = modifier
            .padding(vertical = verticalMargin - NotchDiameter / 2)
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = bleed.roundToPx()
                val width = constraints.maxWidth + 2 * extra
                val placeable = measurable.measure(
                    constraints.copy(minWidth = width, maxWidth = width)
                )
                layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
            }
            .height(NotchDiameter)
    ) {
        val y = size.height / 2
        drawLine(
            color = rule,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        )
        drawCircle(desk, radius = size.height / 2, center = Offset(0f, y))
        drawCircle(desk, radius = size.height / 2, center = Offset(size.width, y))
    }
}

private val NotchDiameter = 14.dp
