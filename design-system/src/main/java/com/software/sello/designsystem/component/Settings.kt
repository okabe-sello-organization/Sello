package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** Whether a [SettingRow] is ordinary or the one that deletes everything. */
enum class SettingTone { Normal, Loss }

/**
 * One line of Configuración: [icon], [title] with an optional [caption] under it, and
 * [value] at the end. With [onClick] the whole row is one button and a chevron says it
 * leads somewhere, unless it is the [SettingTone.Loss] row, which is red instead. A dashed
 * rule above separates it from the row before; the first row under a heading has none.
 * When they don't fit on one line, the title and value share the width and each wraps.
 */
@Composable
fun SettingRow(
    title: String,
    icon: SelloIcon,
    modifier: Modifier = Modifier,
    caption: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    tone: SettingTone = SettingTone.Normal,
    divider: Boolean = true,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    val accent = if (tone == SettingTone.Loss) colors.semantic.loss else colors.brand
    val rule = colors.rule
    val interaction = if (onClick == null) {
        Modifier.semantics(mergeDescendants = true) {}
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (divider) {
                    drawLine(
                        color = rule,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                        )
                    )
                }
            }
            .then(interaction)
            .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .padding(vertical = 10.dp)
    ) {
        Icon(icon.painter(), null, Modifier.size(20.dp), accent)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600),
                color = if (tone == SettingTone.Loss) accent else colors.ink
            )
            if (caption != null) {
                Text(caption, style = MaterialTheme.typography.labelMedium, color = colors.inkSoft)
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(VALUE_WEIGHT, fill = false)
            )
        }
        if (onClick != null && tone == SettingTone.Normal) {
            Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), colors.inkSoft)
        }
    }
}

/** The value may take up to this share of the row beside a title that takes the rest. */
private const val VALUE_WEIGHT = 0.8f

private const val DISABLED_ROW_ALPHA = 0.38f

/**
 * Choosing one of a few [options], such as the order of the categories: a dialog slip
 * with [title], one radio row per option with [selectedIndex] marked, and Cancelar. A
 * choice is reported with [onSelect]; Back, a tap outside and Cancelar are [onDismiss].
 * The rows scroll when they do not fit.
 */
@Composable
fun OptionSlip(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismiss) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(SCRIM_ALPHA) }
        // The dialog's own window would take the system's text size; keep the caller's.
        CompositionLocalProvider(LocalDensity provides density) {
            Slip(
                pinked = false,
                contentPadding = SlipDefaults.PlainPadding,
                modifier = modifier.semantics { paneTitle = title }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(SelloTheme.spacing.sm))
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                ) {
                    options.forEachIndexed { index, option ->
                        val chosen = index == selectedIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(selected = chosen, role = Role.RadioButton) {
                                    onSelect(index)
                                }
                                .heightIn(min = SelloTheme.spacing.minTouchTarget)
                                .padding(vertical = 6.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .border(
                                        2.dp,
                                        if (chosen) colors.brand else colors.outline,
                                        CircleShape
                                    )
                            ) {
                                if (chosen) {
                                    Box(Modifier.size(10.dp).background(colors.brand, CircleShape))
                                }
                            }
                            Text(
                                option,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (chosen) FontWeight.W700 else FontWeight.W400
                                ),
                                color = colors.ink
                            )
                        }
                    }
                }
                SelloButton(
                    text = stringResource(R.string.sello_cancel),
                    onClick = onDismiss,
                    kind = ButtonKind.Text,
                    modifier = Modifier.align(Alignment.End).padding(top = 6.dp)
                )
            }
        }
    }
}
