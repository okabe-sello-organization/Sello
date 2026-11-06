package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloMotion
import com.software.sello.designsystem.theme.SelloTheme
import kotlinx.coroutines.launch

/**
 * Display-only fitting. Exact grouped digits and their spoken equivalent come from the
 * caller. [startSize] in sp overrides the largest size tried, as on a goal card.
 */
@Composable
fun FittedMoneyText(
    value: MoneyTextValue,
    modifier: Modifier = Modifier,
    color: Color = SelloTheme.colors.ink,
    entry: Boolean = false,
    startSize: Int? = null,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 300.dp
        val digits = value.digits.count { it.isDigit() }
        val initial = when {
            startSize != null -> startSize
            !entry -> 60
            digits <= 6 -> 45
            digits <= 9 -> if (narrow) 31 else 35
            else -> if (narrow) 21 else 26
        }
        val base = MaterialTheme.typography.displayMedium
        val steps = (
            listOf(initial) +
                listOf(44, 35, 31, 26, 21, 18, 16, 14, 12).filter { it < initial }
            ).distinct()
        val selected = steps.firstOrNull { size ->
            val style = base.copy(fontSize = size.sp, lineHeight = (size + 2).sp)
            measurer.measure(
                moneyAnnotatedString(value, MoneyStyle.Total, style, color),
                style,
                softWrap = false
            ).size.width <=
                constraints.maxWidth
        } ?: 12
        // At the minimum accessible size an extreme aggregate remains exact and scrollable.
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            MoneyText(
                value,
                style = MoneyStyle.Total,
                color = color,
                textStyle = base.copy(
                    fontSize = selected.sp,
                    lineHeight = (
                        selected +
                            2
                        ).sp
                ),
                singleLine = true,
                onTextLayout = onTextLayout
            )
        }
    }
}

/**
 * The amount being entered: [label] (or [error] in its place, so nothing moves), the
 * [date] pill, an [icon] circle and the fitted amount. Text pasted that cannot be an
 * amount is shown as it was in [rejectedText]; with [onPaste] a paste button hands the
 * clipboard text to the caller untouched. [content] follows the amount inside the slip.
 * On a form with several figures, [onClick] makes the slip choose itself for the pad and
 * [active] edges it in ink while the pad types into it.
 */
@Composable
fun EntryAmountSlip(
    label: String,
    amount: MoneyTextValue?,
    modifier: Modifier = Modifier,
    error: String? = null,
    date: @Composable () -> Unit = {},
    icon: SelloIcon? = null,
    tone: ButtonTone = ButtonTone.Brand,
    limitPulse: Int = 0,
    rejectedText: String? = null,
    onPaste: ((String) -> Unit)? = null,
    pasteLabel: String = stringResource(R.string.sello_paste),
    amountAlpha: Float = 1f,
    onClick: (() -> Unit)? = null,
    active: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    val reduced = SelloTheme.reducedMotion
    val shake = remember { Animatable(0f) }
    LaunchedEffect(limitPulse, reduced) {
        if (limitPulse > 0 && !reduced) {
            shake.snapTo(-1f)
            shake.animateTo(1f, SelloMotion.feed())
            shake.animateTo(0f, SelloMotion.feed())
        } else {
            shake.snapTo(0f)
        }
    }
    val colors = SelloTheme.colors
    val ink = when (tone) {
        ButtonTone.Gain -> colors.semantic.gain
        ButtonTone.Loss -> colors.semantic.loss
        else -> colors.ink
    }
    Slip(
        modifier.fillMaxWidth(),
        onClick = onClick,
        outline = if (active) colors.ink else null
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                error ?: label,
                style = MaterialTheme.typography.labelMedium,
                color = if (error ==
                    null
                ) {
                    colors.inkSoft
                } else {
                    colors.semantic.loss
                },
                modifier = Modifier.weight(1f).semantics {
                    liveRegion =
                        LiveRegionMode.Polite
                }
            )
            date()
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
        ) {
            if (icon != null) {
                Box(
                    Modifier.size(46.dp).background(
                        if (tone ==
                            ButtonTone.Gain
                        ) {
                            colors.semantic.gainSoft
                        } else {
                            colors.brandSoft
                        },
                        CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon.painter(),
                        null,
                        tint = if (tone ==
                            ButtonTone.Gain
                        ) {
                            colors.semantic.onGainSoft
                        } else {
                            colors.onBrandSoft
                        }
                    )
                }
            }
            val moved = Modifier.weight(1f).graphicsLayer {
                translationX = shake.value * 4.dp.toPx()
                alpha = amountAlpha
            }
            if (rejectedText != null) {
                Text(
                    rejectedText,
                    moved,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.semantic.loss,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                FittedMoneyText(
                    amount
                        ?: MoneyTextValue(
                            MoneySign.None,
                            "",
                            "0",
                            stringResource(R.string.sello_amount_empty)
                        ),
                    moved,
                    if (amount == null) colors.outline else ink,
                    entry = true
                )
            }
            if (onPaste != null) {
                val clipboard = LocalClipboard.current
                val scope = rememberCoroutineScope()
                IconButton(
                    onClick = {
                        scope.launch {
                            val clip = clipboard.getClipEntry()?.clipData
                            val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                            if (text != null) onPaste(text.toString())
                        }
                    }
                ) { Icon(SelloIcon.ContentPaste.painter(), pasteLabel, tint = colors.brand) }
            }
        }
        content()
    }
}

/**
 * A second slip tucked behind the entry's amount slip: its visible strip, [text] after a
 * plus (**Otro gasto**, **Otro movimiento**), opens the bulk receipt with what is typed.
 * [EntryLayout]'s `more` slot puts it right under the amount, a medium space of it hidden
 * behind the amount slip. While not [enabled] it dims and waits.
 */
@Composable
fun MoreSlip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    val shape = remember { PinkedBottomShape(corner = 0.dp) }
    // How much of it hides behind the amount slip.
    val hidden = SelloTheme.spacing.md
    Row(
        Modifier
            .zIndex(-1f)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val tuck = hidden.roundToPx()
                layout(placeable.width, (placeable.height - tuck).coerceAtLeast(0)) {
                    placeable.place(0, -tuck)
                }
            }
            .padding(horizontal = MoreSlipInset)
            .then(modifier)
            .fillMaxWidth()
            .alpha(if (enabled) 1f else SelloTheme.alpha.disabled)
            .clip(shape)
            .background(colors.paperAlt)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(
                top = hidden,
                bottom = MoreSlipTeeth,
                start = SelloTheme.spacing.md,
                end = SelloTheme.spacing.md
            )
            .heightIn(min = MoreSlipStrip),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(SelloIcon.Add.painter(), null, Modifier.size(18.dp), tint = colors.brand)
        Spacer(Modifier.width(SelloTheme.spacing.xs))
        Text(
            text,
            style = SelloTheme.type.lineTitle,
            color = colors.brand,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** How far [MoreSlip] sits in from the amount slip's edges, so it reads as the one behind. */
private val MoreSlipInset = 10.dp

/**
 * [MoreSlip]'s visible strip above its teeth, 40dp with them; the part tucked behind the
 * amount slip takes taps too, so the target is 52dp.
 */
private val MoreSlipStrip = 35.dp
private val MoreSlipTeeth = 5.dp

/**
 * Keeps the amount, action and pad stable; choices scroll independently in either layout.
 * A portrait window too short for that scrolls the amount with the choices instead.
 *
 * The entry's only text input is the docked [NoteComposer], whose keyboard covers this
 * screen. A host that pads for that keyboard would otherwise squeeze the entry or flip it
 * into two columns behind the sheet, so while the keyboard is open the entry keeps the
 * height it had without it.
 *
 * With a [band] colour, the band under the form's title row carries on behind the header
 * and down to the middle of the amount slip, as a tab's band holds its answer slip: from
 * edge to edge of the window, or in two panes only as far as the amount's pane, so the
 * action beside it never sits on a band of its own colour.
 *
 * [more], usually a [MoreSlip], sits right under the amount and moves with it; the band
 * still ends at the amount slip's middle.
 */
@Composable
fun EntryLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    choices: @Composable () -> Unit,
    action: @Composable () -> Unit,
    keypad: @Composable () -> Unit,
    band: Color? = null,
    more: (@Composable () -> Unit)? = null
) {
    val reach = remember { BandReach() }
    val paneGap = SelloTheme.spacing.md
    val windowWidth = LocalWindowInfo.current.containerSize.width
    val banded = if (band == null) {
        Modifier
    } else {
        Modifier
            .onGloballyPositioned { reach.layout = it.positionInWindow() }
            .drawBehind {
                val depth = reach.amountMiddle - reach.layout.y
                // In the window's coordinates: where the band ends on the right.
                val end = if (reach.twoPane) {
                    reach.amountEnd + (paneGap / 2).toPx()
                } else {
                    maxOf(windowWidth.toFloat(), reach.layout.x + size.width)
                }
                if (depth > 0f) {
                    drawRect(band, topLeft = Offset(-reach.layout.x, 0f), size = Size(end, depth))
                }
            }
    }
    val placedAmount: @Composable () -> Unit = if (band == null) {
        amount
    } else {
        {
            Box(
                Modifier.onGloballyPositioned {
                    val at = it.positionInWindow()
                    reach.amountMiddle = at.y + it.size.height / 2f
                    reach.amountEnd = at.x + it.size.width
                }
            ) { amount() }
        }
    }
    val amountAndMore: @Composable () -> Unit = if (more == null) {
        placedAmount
    } else {
        {
            Column(Modifier.fillMaxWidth()) {
                placedAmount()
                more()
            }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().then(banded)) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val settled = remember(maxWidth) { SettledHeight(maxHeight) }
        SideEffect { if (!keyboardOpen) settled.height = maxHeight }
        val height = if (keyboardOpen) maxOf(settled.height, maxHeight) else maxHeight
        val twoPane = maxWidth >= 600.dp || height < 480.dp
        SideEffect { reach.twoPane = twoPane }
        Box(
            Modifier.fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(height)
        ) {
            EntryPanes(
                twoPane,
                height,
                header,
                amountAndMore,
                choices,
                action,
                keypad
            )
        }
    }
}

private class SettledHeight(var height: Dp)

/**
 * Where the entry, the middle and the end of its amount sit in the window, and whether
 * it is in two panes; read when drawing.
 */
private class BandReach {
    var layout by mutableStateOf(Offset.Zero)
    var amountMiddle by mutableFloatStateOf(0f)
    var amountEnd by mutableFloatStateOf(0f)
    var twoPane by mutableStateOf(false)
}

@Composable
private fun EntryPanes(
    twoPane: Boolean,
    availableHeight: Dp,
    header: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    choices: @Composable () -> Unit,
    action: @Composable () -> Unit,
    keypad: @Composable () -> Unit
) {
    if (twoPane) {
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
        ) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                header()
                amount()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
                ) { choices() }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(
                    Modifier.heightIn(max = availableHeight)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true),
                    verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
                ) {
                    action()
                    keypad()
                }
            }
        }
    } else if (availableHeight >= PinnedAmountMinHeight) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
        ) {
            header()
            amount()
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                choices()
            }
            action()
            keypad()
        }
    } else {
        // Too short to pin the amount and still leave two rows of choices: the header,
        // amount and choices scroll together; the action and pad stay put.
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
        ) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
            ) {
                header()
                amount()
                choices()
            }
            action()
            keypad()
        }
    }
}

/**
 * The shortest portrait window that pins the switch, amount, action and pad (about
 * 490dp) and still leaves two rows of chips (about 110dp) visible between them.
 */
private val PinnedAmountMinHeight = 600.dp

/**
 * A screen's answer: a label with its stamp, if it has one, then one large amount, in
 * loss ink when [lossAmount] (by default when [ink] is [StampInk.Over]). Inversiones'
 * Pierde keeps the value in ink: a value is not an overrun. With [description], the
 * label, stamp and amount are read as that one sentence; [detail] is read on its own.
 * [heading], such as a category's name, comes first; [leading], such as the category's
 * circle, stands beside the amount, or above it when the slip is too narrow.
 */
@Composable
fun AnswerSlip(
    label: String,
    amount: MoneyTextValue,
    stamp: String?,
    ink: StampInk,
    modifier: Modifier = Modifier,
    description: String? = null,
    lossAmount: Boolean = ink == StampInk.Over,
    heading: @Composable () -> Unit = {},
    leading: (@Composable () -> Unit)? = null,
    detail: @Composable () -> Unit = {}
) {
    Slip(modifier.fillMaxWidth()) {
        heading()
        val answer = if (description == null) {
            Modifier
        } else {
            Modifier.clearAndSetSemantics { contentDescription = description }
        }
        Column(answer.fillMaxWidth()) {
            if (stamp == null) {
                Text(label, color = SelloTheme.colors.inkSoft)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), color = SelloTheme.colors.inkSoft)
                    Stamp(
                        stamp,
                        ink,
                        Modifier.padding(vertical = SelloTheme.spacing.sm),
                        angle = -5f
                    )
                }
            }
            val figure = @Composable {
                FittedMoneyText(
                    amount,
                    color = if (lossAmount) {
                        SelloTheme.colors.semantic.loss
                    } else {
                        SelloTheme.colors.ink
                    }
                )
            }
            if (leading == null) {
                figure()
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < AnswerBesideMinWidth) {
                        Column {
                            leading()
                            figure()
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)
                        ) {
                            leading()
                            Column(Modifier.weight(1f)) { figure() }
                        }
                    }
                }
            }
        }
        detail()
    }
}

/** Narrower than this, [AnswerSlip]'s leading part goes above the amount. */
private val AnswerBesideMinWidth = 240.dp

@Composable
fun FactLine(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = SelloTheme.colors.inkSoft
    )
}

/**
 * A quiet line at a slip's foot: [icon] and [text]. With [onClick] it opens something,
 * ends in a chevron and is at least a touch target tall; without, it only says something
 * and is as tall as its text.
 */
@Composable
fun Footnote(
    text: AnnotatedString,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: SelloIcon = SelloIcon.Event
) {
    val target = if (onClick == null) {
        Modifier
    } else {
        Modifier.heightIn(
            min = SelloTheme.spacing.minTouchTarget
        ).clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier.fillMaxWidth().then(target),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        Icon(icon.painter(), null, Modifier.size(18.dp), SelloTheme.colors.brand)
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
        if (onClick != null) {
            Icon(SelloIcon.ChevronRight.painter(), null, tint = SelloTheme.colors.outline)
        }
    }
}
