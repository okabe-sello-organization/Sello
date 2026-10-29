package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloTheme

/** Fractions are approximate geometry only. The caller provides exact speech and adjacent figures. */
@Immutable
data class SplitSegment(
    val fraction: Float,
    val spoken: String,
    val color: Color,
    val striped: Boolean = false
)

/**
 * One bar split by [segments], with [gap] of paper between them (Inversiones uses 2dp).
 * The exact values belong in the lines or rows next to it.
 */
@Composable
fun SplitBar(segments: List<SplitSegment>, modifier: Modifier = Modifier, gap: Dp = 0.dp) {
    require(segments.all { it.fraction.isFinite() && it.fraction >= 0f })
    val total = segments.sumOf { it.fraction.toDouble() }.toFloat()
    val summary = segments.joinToString(". ") { it.spoken }
    Canvas(
        modifier.fillMaxWidth().height(14.dp).clip(SelloTheme.shapes.control).semantics {
            contentDescription =
                summary
        }
    ) {
        if (total == 0f) return@Canvas
        val drawn = segments.filter { it.fraction > 0f }
        val space = gap.toPx()
        val room = (size.width - space * (drawn.size - 1)).coerceAtLeast(0f)
        var start = 0f
        drawn.forEach { segment ->
            val width = room * (segment.fraction / total)
            if (segment.striped) {
                clipRect(start, 0f, start + width, size.height) {
                    drawRect(
                        segment.color.copy(alpha = 0.15f),
                        Offset(start, 0f),
                        androidx.compose.ui.geometry.Size(width, size.height)
                    )
                    var x = start - size.height
                    while (x < start + width) {
                        drawLine(
                            segment.color,
                            Offset(x, size.height),
                            Offset(x + size.height, 0f),
                            2.dp.toPx()
                        )
                        x += 7.dp.toPx()
                    }
                }
            } else {
                drawRect(
                    segment.color,
                    Offset(start, 0f),
                    androidx.compose.ui.geometry.Size(width, size.height)
                )
            }
            start += width + space
        }
    }
}
