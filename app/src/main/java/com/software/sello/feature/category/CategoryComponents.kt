package com.software.sello.feature.category

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme

/** A row with a switch at the end. The whole row toggles it. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    spokenSubtitle: String? = null
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else DISABLED_SWITCH_ALPHA)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
    ) {
        if (icon != null) {
            Icon(icon.painter(), null, Modifier.size(20.dp), colors.brand)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.ink)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.inkSoft,
                    // Amounts are read in words ("menos 40.000 pesos"), not as glyphs.
                    modifier = if (spokenSubtitle != null) {
                        Modifier.semantics { contentDescription = spokenSubtitle }
                    } else {
                        Modifier
                    }
                )
            }
        }
        val spec = if (SelloTheme.reducedMotion) snap() else SelloMotion.standard<Dp>()
        val thumb by animateDpAsState(if (checked) ThumbTravel else 0.dp, spec, "thumb")
        Box(
            modifier = Modifier
                .size(TrackWidth, TrackHeight)
                .clip(CircleShape)
                .background(if (checked) colors.brand else colors.paperDim)
                .border(1.5.dp, if (checked) colors.brand else colors.outline, CircleShape)
                .padding(ThumbInset)
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(thumb.roundToPx(), 0) }
                    .size(ThumbSize)
                    .background(if (checked) colors.onBrand else colors.outline, CircleShape)
            )
        }
    }
}

private val TrackWidth = 48.dp

private val TrackHeight = 28.dp

private val ThumbSize = 20.dp

private val ThumbInset = 4.dp

private val ThumbTravel = TrackWidth - ThumbSize - ThumbInset * 2

private const val DISABLED_SWITCH_ALPHA = 0.38f
