package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * What is being created, as a large circle beside its name (v2 create): tapping it opens
 * the icons to choose from. With no [icon] yet it is a dashed ring in [tone] around a
 * centred +, inviting the choice; with one it is filled in [tone], with a small pencil.
 * [caption] sits under it ("Elegir ícono", "Cambiar") and [description] is what a screen
 * reader says, which should name the chosen icon.
 */
@Composable
fun IconPickerCircle(
    icon: SelloIcon?,
    caption: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Brand,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    val (fill, onFill) = tone.inks()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .widthIn(min = CircleSize)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .clip(SelloTheme.shapes.control)
            .clickable(enabled = enabled, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(CircleSize).drawBehind {
                if (icon != null) {
                    drawCircle(fill)
                } else {
                    val stroke = 2.5.dp.toPx()
                    drawCircle(fill.copy(alpha = EMPTY_TINT))
                    drawCircle(
                        fill,
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(
                            stroke,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                            )
                        )
                    )
                }
            }
        ) {
            Icon(
                (icon ?: SelloIcon.Add).painter(),
                contentDescription = null,
                tint = if (icon != null) onFill else fill,
                modifier = Modifier.size(if (icon != null) 30.dp else 34.dp)
            )
            if (icon != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(24.dp)
                        .background(colors.paper, CircleShape)
                ) {
                    Icon(
                        SelloIcon.EditNote.painter(),
                        contentDescription = null,
                        tint = fill,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Text(
            caption,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),
            color = fill,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The icons to choose from, under a [RepeatSheet]-style heading, as round cells. Icons in
 * [inUse] are drawn dashed and can still be chosen; [inUseNote] says so under the grid and
 * is added to what a screen reader says for each of them.
 */
@Composable
fun IconChoiceSheetContent(
    choices: List<IconChoice>,
    selectedId: String?,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    tone: ButtonTone = ButtonTone.Brand,
    inUse: Set<String> = emptySet(),
    inUseNote: String? = null
) {
    val colors = SelloTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
        }
        IconChoiceGrid(
            choices = choices,
            selectedId = selectedId,
            onPick = onPick,
            maxColumns = 6,
            tone = tone,
            round = true,
            inUse = inUse,
            inUseLabel = inUseNote
        )
        if (inUseNote != null && inUse.isNotEmpty()) {
            Text(
                inUseNote,
                style = MaterialTheme.typography.bodySmall,
                color = colors.inkSoft
            )
        }
    }
}

private val CircleSize = 68.dp
private const val EMPTY_TINT = 0.08f
private const val DISABLED_ALPHA = 0.5f
