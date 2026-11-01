package com.software.sello.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A choice among a few short options, shown as one trough with a sliding marker. On a
 * coloured [band] the trough is a tint of the band and the marker is the band's ink.
 */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    band: Color? = null
) {
    val colors = SelloTheme.colors
    val trough = band?.let { lerp(it, colors.onBand, BAND_TROUGH) } ?: colors.paperDim
    val marker = if (band != null) colors.onBand else colors.paper
    val chosenText = band ?: colors.ink
    val otherText = if (band != null) colors.onBand else colors.inkSoft
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(trough, SelloTheme.shapes.control)
            .padding(SegmentInset)
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val spec = if (SelloTheme.reducedMotion) snap() else SelloMotion.standard<Dp>()
        val start by animateDpAsState(segment * selectedIndex, spec, "segment")
        Box(
            modifier = Modifier
                .offset { IntOffset(start.roundToPx(), 0) }
                .width(segment)
                .height(SegmentHeight)
                .background(marker, SegmentShape)
        )
        Row(modifier = Modifier.selectableGroup()) {
            options.forEachIndexed { index, option ->
                val selected = index == selectedIndex
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = SegmentHeight)
                        .clip(SegmentShape)
                        .selectable(
                            selected = selected,
                            enabled = enabled,
                            role = Role.RadioButton
                        ) { onSelect(index) }
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (selected) FontWeight.W700 else FontWeight.W600
                        ),
                        color = if (selected) chosenText else otherText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

private val SegmentInset = 3.dp

/** How far a band's trough leans towards the band's ink: a lighter shade of the band. */
private const val BAND_TROUGH = 0.16f

private val SegmentHeight = 42.dp

private val SegmentShape = RoundedCornerShape(8.dp)
