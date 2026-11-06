package com.software.sello.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One choice in a set: a category, an income source, a goal with its percentage as
 * [trailing]. It looks 36dp tall, grows with the font size and always takes a 48dp touch
 * target. Put the set in a [ChipFlow] so it is announced as one group.
 */
@Composable
fun SelloChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: SelloIcon? = null,
    enabled: Boolean = true,
    trailing: String? = null
) {
    val colors = SelloTheme.colors
    val spec = if (SelloTheme.reducedMotion) snap() else SelloMotion.quick<Color>()
    val fill by animateColorAsState(if (selected) colors.brand else colors.paper, spec, "chip")
    val content = if (selected) colors.onBrand else colors.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else SelloTheme.alpha.disabled)
            .heightIn(min = 36.dp)
            .clip(SelloTheme.shapes.control)
            .background(fill)
            .border(1.5.dp, if (selected) colors.brand else colors.rule, SelloTheme.shapes.control)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = SelloTheme.spacing.md, vertical = SelloTheme.spacing.xs)
    ) {
        if (icon != null) {
            Icon(
                painter = icon.painter(),
                contentDescription = null,
                tint = if (selected) colors.onBrand else colors.brand,
                modifier = Modifier.size(18.dp)
            )
        }
        // A label too long for the row takes a second line, not an ellipsis.
        Text(
            text = label,
            style = SelloTheme.type.chipLabel,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = content.copy(alpha = SelloTheme.alpha.secondary),
                maxLines = 1
            )
        }
    }
}

/**
 * The chip that ends a set of choices to create a new one, such as Nueva after the
 * expense categories. It looks like a [SelloChip] with a dashed brand outline and a
 * plus, and it is a button, never one of the choices.
 */
@Composable
fun AddChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(if (enabled) 1f else SelloTheme.alpha.disabled)
            .heightIn(min = 36.dp)
            .clip(SelloTheme.shapes.control)
            .background(colors.paper)
            .dashedOutline(colors.brand, 1.5.dp, SelloTheme.shapes.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = SelloTheme.spacing.md)
    ) {
        Icon(
            painter = SelloIcon.Add.painter(),
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = SelloTheme.type.chipLabel,
            color = colors.brand,
            maxLines = 1
        )
    }
}

/**
 * Lays out a set of chips. By default they wrap onto more lines; with [singleLine]
 * they scroll sideways and fade at the trailing edge to show there are more.
 */
@Composable
fun ChipFlow(
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    content: @Composable () -> Unit
) {
    if (singleLine) {
        // The last chips fade out over this much, and the row scrolls clear of it.
        val trailingFade = SelloTheme.spacing.lg
        Row(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            modifier = modifier
                .selectableGroup()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fade = trailingFade.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = size.width - fade,
                            endX = size.width
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .horizontalScroll(rememberScrollState())
                .padding(end = trailingFade)
        ) { content() }
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ChipGap),
            modifier = modifier.selectableGroup()
        ) { content() }
    }
}

/** A tappable value on an entry sheet, such as the date or the note. */
@Composable
fun FieldPill(
    text: String,
    icon: SelloIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 42.dp)
            .clip(SelloTheme.shapes.control)
            .background(colors.paper)
            .border(1.5.dp, colors.rule, SelloTheme.shapes.control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = SelloTheme.spacing.md)
    ) {
        Icon(
            painter = icon.painter(),
            contentDescription = null,
            tint = colors.brand,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = SelloTheme.type.chipLabel,
            color = if (placeholder) colors.inkSoft else colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val ChipGap = 6.dp
