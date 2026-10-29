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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme

/** A choice among a few short options, shown as one trough with a sliding marker. */
@Composable
fun SegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paperDim, SelloTheme.shapes.control)
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
                .background(colors.paper, SegmentShape)
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
                        color = if (selected) colors.ink else colors.inkSoft,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

private val SegmentInset = 3.dp

private val SegmentHeight = 42.dp

private val SegmentShape = RoundedCornerShape(8.dp)
