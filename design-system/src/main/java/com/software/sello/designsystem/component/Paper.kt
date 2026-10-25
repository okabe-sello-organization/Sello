package com.software.sello.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * clipboard text to the caller untouched.
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
    pasteLabel: String = stringResource(R.string.sello_paste)
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
    Slip(modifier.fillMaxWidth()) {
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
    }
}

/**
 * Keeps the amount, action and pad stable; choices scroll independently in either layout.
 * A portrait window too short for that scrolls the amount with the choices instead.
 *
 * The entry's only text input is the docked [NoteComposer], whose keyboard covers this
 * screen. A host that pads for that keyboard would otherwise squeeze the entry or flip it
 * into two columns behind the sheet, so while the keyboard is open the entry keeps the
 * height it had without it.
 */
@Composable
fun EntryLayout(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    amount: @Composable () -> Unit,
    choices: @Composable () -> Unit,
    action: @Composable () -> Unit,
    keypad: @Composable () -> Unit
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        val settled = remember(maxWidth) { SettledHeight(maxHeight) }
        SideEffect { if (!keyboardOpen) settled.height = maxHeight }
        val height = if (keyboardOpen) maxOf(settled.height, maxHeight) else maxHeight
        val twoPane = maxWidth >= 600.dp || height < 480.dp
        Box(
            Modifier.fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .height(height)
        ) {
            EntryPanes(
                twoPane,
                height,
                header,
                amount,
                choices,
                action,
                keypad
            )
        }
    }
}

private class SettledHeight(var height: Dp)

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
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                header()
                amount()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) { choices() }
            }
            Column(
                Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Bottom
            ) {
                Column(
                    Modifier.heightIn(max = availableHeight)
                        .verticalScroll(rememberScrollState(), reverseScrolling = true),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    action()
                    keypad()
                }
            }
        }
    } else if (availableHeight >= PinnedAmountMinHeight) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            header()
            amount()
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                choices()
            }
            action()
            keypad()
        }
    } else {
        // Too short to pin the amount and still leave two rows of choices: the header,
        // amount and choices scroll together; the action and pad stay put.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
    detail: @Composable () -> Unit = {}
) {
    Slip(modifier.fillMaxWidth()) {
        val answer = if (description == null) {
            Modifier
        } else {
            Modifier.clearAndSetSemantics { contentDescription = description }
        }
        Column(answer.fillMaxWidth()) {
            // Stacked at large text so the rotated stamp never steals the label's width.
            if (stamp == null) {
                Text(label, color = SelloTheme.colors.inkSoft)
            } else if (LocalDensity.current.fontScale >= 1.3f) {
                Text(label, color = SelloTheme.colors.inkSoft)
                Stamp(stamp, ink, Modifier.padding(vertical = 8.dp), angle = -5f)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), color = SelloTheme.colors.inkSoft)
                    Stamp(stamp, ink, Modifier.padding(vertical = 8.dp), angle = -5f)
                }
            }
            FittedMoneyText(
                amount,
                color = if (lossAmount) {
                    SelloTheme.colors.semantic.loss
                } else {
                    SelloTheme.colors.ink
                }
            )
        }
        detail()
    }
}

@Composable
fun FactLine(text: AnnotatedString, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = SelloTheme.colors.inkSoft
    )
}

@Composable
fun Footnote(
    text: AnnotatedString,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: SelloIcon = SelloIcon.Event
) {
    Row(
        modifier.fillMaxWidth().heightIn(
            min = 48.dp
        ).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon.painter(), null, Modifier.size(18.dp), SelloTheme.colors.brand)
        Text(
            text,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.inkSoft
        )
        Icon(SelloIcon.ChevronRight.painter(), null, tint = SelloTheme.colors.outline)
    }
}

@Composable
fun SuggestionNote(
    title: String,
    evidence: String,
    advice: String,
    actionLabel: String,
    dismissLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    Column(
        modifier.fillMaxWidth().background(
            colors.brandSoft,
            SelloTheme.shapes.control
        ).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SelloIcon.Insights.painter(),
                null,
                Modifier.padding(end = 8.dp),
                colors.onBrandSoft
            )
            Text(
                title,
                Modifier.weight(1f),
                color = colors.onBrandSoft,
                fontWeight = FontWeight.W800
            )
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                Icon(SelloIcon.Close.painter(), dismissLabel, tint = colors.onBrandSoft)
            }
        }
        Text(evidence, color = colors.onBrandSoft)
        Text(advice, color = colors.onBrandSoft, fontWeight = FontWeight.W800)
        SelloButton(actionLabel, onAction, kind = ButtonKind.Text)
    }
}

@Composable
fun DangerSlip(
    title: String,
    consequences: AnnotatedString,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Slip(
        modifier.fillMaxWidth().border(
            1.5.dp,
            SelloTheme.colors.semantic.loss,
            SelloTheme.shapes.control
        ),
        pinked = false
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(consequences, Modifier.padding(vertical = 8.dp))
        SelloButton(
            actionLabel,
            onAction,
            Modifier.fillMaxWidth(),
            kind = ButtonKind.Outlined,
            tone = ButtonTone.Loss
        )
    }
}

@Composable
fun ReviewInvitation(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = SelloTheme.colors.brand
    Row(
        modifier.fillMaxWidth().heightIn(
            min = 48.dp
        ).clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                drawRoundRect(
                    color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        1.5.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                            floatArrayOf(5.dp.toPx(), 4.dp.toPx())
                        )
                    )
                )
            }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), color = color)
        Icon(SelloIcon.ChevronRight.painter(), null, tint = color)
    }
}
