package com.software.sello.feature.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.software.sello.designsystem.component.FittedMoneyText
import com.software.sello.designsystem.component.MilestonePlace
import com.software.sello.designsystem.component.MilestoneSpec
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.Slip
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * Recibo's goal: the pill itself is the progress bar, filled to [progress] with a brand
 * edge where the fill ends. The main part opens the goal and, with [openDescription], is
 * said that way ("Viaje a la costa, 57 por ciento. Ver meta") with its exact progress;
 * the round **+** is Aportar, said as [contributeLabel].
 *
 * With [reached] (100 % or more, not yet lograda) the pill is green, as every place
 * waiting at 100 %, and the round button is a ✓ that marks it lograda ([onComplete],
 * said as [completeLabel]); [completing] while that is saved.
 */
@Composable
internal fun GoalPill(
    name: String,
    percentage: String,
    progress: Float,
    icon: SelloIcon,
    contributeLabel: String,
    onOpen: () -> Unit,
    onContribute: () -> Unit,
    modifier: Modifier = Modifier,
    openDescription: String? = null,
    reached: Boolean = false,
    completeLabel: String = contributeLabel,
    onComplete: () -> Unit = {},
    completing: Boolean = false
) {
    val colors = SelloTheme.colors
    val fill = if (reached) 1f else progress.coerceIn(0f, 1f)
    val soft = if (reached) colors.semantic.gainSoft else colors.brandSoft
    val strong = if (reached) colors.semantic.gain else colors.brand
    val onSoft = if (reached) colors.semantic.onGainSoft else colors.onBrandSoft
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(CircleShape)
            .background(colors.paper).drawBehind {
                val width = size.width * fill
                drawRect(soft, size = Size(width, size.height))
                if (fill > 0f && !reached) {
                    val edge = 2.dp.toPx()
                    drawRect(
                        colors.brand,
                        topLeft = Offset(width - edge, 0f),
                        size = Size(edge, size.height)
                    )
                }
            }.padding(SelloTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f).heightIn(min = SelloTheme.spacing.minTouchTarget).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) {
                    openDescription?.let { contentDescription = it }
                    progressBarRangeInfo = ProgressBarRangeInfo(fill, 0f..1f)
                }
                .padding(start = 14.dp, end = SelloTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
        ) {
            Icon(icon.painter(), null, Modifier.size(22.dp), tint = onSoft)
            Text(
                name,
                Modifier.weight(1f),
                fontWeight = FontWeight.W800,
                color = onSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                percentage,
                color = colors.ink,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900)
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(strong)
                .clickable(
                    enabled = !completing,
                    role = Role.Button,
                    onClick = if (reached) onComplete else onContribute
                )
                .semantics { contentDescription = if (reached) completeLabel else contributeLabel }
        ) {
            Icon(
                if (reached) SelloIcon.Check.painter() else SelloIcon.Add.painter(),
                null,
                tint = if (reached) colors.semantic.onGain else colors.onBrand
            )
        }
    }
}

/**
 * Another goal at 100 %, on Recibo: a green pill with [name], [message] ("Llegó al
 * 100 %") and a filled [actionLabel] that marks it lograda; [busy] while that is saved.
 * Where the three do not fit on one line (a narrow screen, a long name) the
 * action moves under the name and the row becomes a card, so nothing is cut.
 */
@Composable
internal fun ReachedRow(
    name: String,
    message: String,
    actionLabel: String,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false
) {
    val colors = SelloTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val nameStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.W800)
    val messageStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)
    val actionStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widths = with(density) {
            listOf(name to nameStyle, message to messageStyle, actionLabel to actionStyle)
                .map { (text, style) -> measurer.measure(text, style).size.width.toDp() }
        }
        // Start padding, icon and gaps, the wider text, the action and its padding, end padding.
        val inline = 14.dp + 22.dp + 10.dp + maxOf(widths[0], widths[1]) + 10.dp +
            widths[2] + 28.dp + 6.dp <= maxWidth
        val shape = if (inline) CircleShape else SelloTheme.shapes.control
        val texts = @Composable { textModifier: Modifier ->
            Column(textModifier.semantics(mergeDescendants = true) {}) {
                Text(
                    name,
                    color = colors.ink,
                    style = nameStyle,
                    maxLines = if (inline) 1 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis
                )
                Text(message, color = colors.semantic.gain, style = messageStyle)
            }
        }
        val check = @Composable {
            Icon(SelloIcon.Check.painter(), null, Modifier.size(22.dp), tint = colors.semantic.gain)
        }
        val action = @Composable {
            // The v2 row's own compact button: 44dp, so the name keeps its room.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.heightIn(min = 44.dp).clip(CircleShape)
                    .background(colors.semantic.gain)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onComplete)
                    .alpha(if (busy) 0.6f else 1f)
                    .padding(horizontal = 14.dp)
            ) {
                Text(actionLabel, color = colors.semantic.onGain, style = actionStyle)
            }
        }
        val surface = Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(colors.semantic.gainSoft)
        if (inline) {
            Row(
                surface.padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                check()
                texts(Modifier.weight(1f))
                action()
            }
        } else {
            Column(
                surface.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    check()
                    texts(Modifier.weight(1f))
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { action() }
            }
        }
    }
}

/**
 * An active goal: the saved amount large, the target and its date on one line, a green
 * bar and the four places spread across the card. [flagged] shows the flag before
 * [date]; pass false with the caller's "Sin fecha" for a goal without one.
 */
@Composable
internal fun GoalCard(
    name: String,
    icon: SelloIcon,
    percentage: String,
    saved: MoneyTextValue,
    target: String,
    date: String,
    progress: Float,
    places: List<MilestoneSpec>,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    flagged: Boolean = true
) {
    val colors = SelloTheme.colors
    Slip(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
        ) {
            Icon(icon.painter(), null, Modifier.size(24.dp), tint = colors.semantic.gain)
            Text(
                name,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800)
            )
            Text(
                percentage,
                color = colors.semantic.gain,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.W900,
                    letterSpacing = (-0.02).em
                )
            )
        }
        FittedMoneyText(saved, Modifier.padding(top = SelloTheme.spacing.sm), startSize = 34)
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp, bottom = SelloTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                target,
                Modifier.weight(1f),
                color = colors.inkSoft,
                style = MaterialTheme.typography.bodySmall
            )
            if (flagged) {
                Icon(
                    SelloIcon.Flag.painter(),
                    null,
                    Modifier.padding(end = SelloTheme.spacing.xs).size(16.dp),
                    tint = colors.inkSoft
                )
            }
            Text(date, color = colors.inkSoft, style = MaterialTheme.typography.bodySmall)
        }
        Box(
            Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(colors.paperDim)
        ) {
            Box(
                Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(colors.semantic.gain)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            places.forEach { MilestonePlace(it) }
        }
    }
}

/**
 * A completed goal: its icon in ink, name and detail, and the stamp on the trailing edge.
 * With [land] the stamp comes down once as the row appears, as when revisiting Metas;
 * a revisit is no new result, so it makes no haptic.
 */
@Composable
internal fun CompletedGoalRow(
    name: String,
    detail: String,
    icon: SelloIcon,
    stamp: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    land: Boolean = false
) {
    Slip(modifier.fillMaxWidth(), onClick = onOpen) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon.painter(), null, Modifier.size(24.dp), tint = SelloTheme.colors.ink)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyLarge)
                Text(
                    detail,
                    color = SelloTheme.colors.inkSoft,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Stamp(stamp, StampInk.Good, land = land, haptic = false)
        }
    }
}

@Composable
internal fun GoalPinPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    Row(
        modifier.clip(
            CircleShape
        ).background(if (selected) colors.onBand.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.5.dp, if (selected) Color.Transparent else colors.onBand, CircleShape)
            .selectable(
                selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .padding(horizontal = SelloTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(SelloIcon.Star.painter(selected), null, tint = colors.onBand)
        Text(label, color = colors.onBand, fontWeight = FontWeight.W800)
    }
}
