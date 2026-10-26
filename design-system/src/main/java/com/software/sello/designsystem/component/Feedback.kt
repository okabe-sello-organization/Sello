package com.software.sello.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A confirmation for something that cannot be undone. [message] says what is affected
 * and how much, with the consequence in bold. Back and a tap outside cancel. While
 * [busy] the action shows a spinner and nothing can be pressed or dismissed, so the
 * action is requested once.
 */
@Composable
fun ConfirmSlip(
    title: String,
    message: AnnotatedString,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
    busy: Boolean = false,
    dismissLabel: String = stringResource(R.string.sello_cancel)
) {
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(SCRIM_ALPHA) }
        Slip(
            pinked = false,
            contentPadding = SlipDefaults.PlainPadding,
            modifier = Modifier.semantics { paneTitle = title }
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                modifier = Modifier.semantics { heading() }
            )
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            Text(text = message, style = MaterialTheme.typography.bodyLarge)
            TearLine(bleed = 20.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
                SelloButton(
                    text = dismissLabel,
                    onClick = onDismiss,
                    kind = ButtonKind.Outlined,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                )
                SelloButton(
                    text = confirmLabel,
                    onClick = onConfirm,
                    kind = if (destructive) ButtonKind.Destructive else ButtonKind.Filled,
                    loading = busy,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * [ConfirmSlip] with any [content] under the title, such as points to read before
 * saving a file. The content scrolls when the slip would not fit, as at large text,
 * the buttons stay in view and go one above the other when a label would not fit.
 */
@Composable
fun ConfirmSlip(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
    busy: Boolean = false,
    dismissLabel: String = stringResource(R.string.sello_cancel),
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
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
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    content()
                }
                TearLine(bleed = 20.dp)
                // The action's label is usually the longer one: it gets two thirds.
                ButtonPair(
                    firstWeight = 1f,
                    secondWeight = 2f,
                    gap = SelloTheme.spacing.md,
                    first = {
                        SelloButton(
                            text = dismissLabel,
                            onClick = onDismiss,
                            kind = ButtonKind.Outlined,
                            enabled = !busy
                        )
                    },
                    second = {
                        SelloButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            kind = if (destructive) ButtonKind.Destructive else ButtonKind.Filled,
                            loading = busy
                        )
                    }
                )
            }
        }
    }
}

/**
 * A question with two answers, neither of them destructive, such as whether a change
 * applies to the next ones too: [firstLabel] outlined, [secondLabel] filled, and
 * [backLabel] under them to go back without answering. The slip is [ConfirmSlip]'s.
 * Back and a tap outside are [onBack]. The answers stand one above the other when they
 * do not fit side by side, as at large text. While [busy] the answer given shows a
 * spinner and nothing can be pressed or dismissed, so it is requested once.
 */
@Composable
fun ChoiceSlip(
    title: String,
    message: AnnotatedString,
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    busy: Boolean = false
) {
    // Which answer was given, so the spinner shows on that one.
    var answered by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Dialog(onDismissRequest = { if (!busy) onBack() }) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { window?.setDimAmount(SCRIM_ALPHA) }
        // The dialog's own window would take the system's text size; keep the caller's,
        // as the catalog's font control sets it.
        CompositionLocalProvider(LocalDensity provides density) {
            Slip(
                pinked = false,
                contentPadding = SlipDefaults.PlainPadding,
                modifier = Modifier.semantics { paneTitle = title }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(SelloTheme.spacing.sm))
                Text(text = message, style = MaterialTheme.typography.bodyLarge)
                TearLine(bleed = 20.dp)
                ButtonPair(
                    firstWeight = 1f,
                    secondWeight = 1f,
                    gap = SelloTheme.spacing.md,
                    first = {
                        SelloButton(
                            text = firstLabel,
                            onClick = {
                                answered = 1
                                onFirst()
                            },
                            kind = ButtonKind.Outlined,
                            enabled = !busy,
                            loading = busy && answered == 1
                        )
                    },
                    second = {
                        SelloButton(
                            text = secondLabel,
                            onClick = {
                                answered = 2
                                onSecond()
                            },
                            enabled = !busy,
                            loading = busy && answered == 2
                        )
                    }
                )
                SelloButton(
                    text = backLabel,
                    onClick = onBack,
                    kind = ButtonKind.Text,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp)
                )
            }
        }
    }
}

internal const val SCRIM_ALPHA = 0.55f

/** A slip with nothing to show yet: a title, one sentence and at most one action. */
@Composable
fun EmptySlip(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = EmptySlipContent(title, body, modifier, actionLabel, onAction, icon = null)

/** The v2 look of [EmptySlip]: the same content centred under a large decorative [icon]. */
@Composable
fun EmptySlip(
    title: String,
    body: String,
    icon: SelloIcon,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) = EmptySlipContent(title, body, modifier, actionLabel, onAction, icon)

@Composable
private fun EmptySlipContent(
    title: String,
    body: String,
    modifier: Modifier,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    icon: SelloIcon?
) {
    Slip(modifier = modifier.fillMaxWidth()) {
        StateLayout(icon) {
            StateText(title, body, icon != null)
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(SelloTheme.spacing.lg))
                SelloButton(text = actionLabel, onClick = onAction, kind = ButtonKind.Outlined)
            }
        }
    }
}

/**
 * A slip whose content could not be read. [message] says what failed in plain words.
 * It never shows a figure: a failed read has no amount, not a zero. With an [icon] it
 * takes the v2 look: centred under a large decorative icon.
 */
@Composable
fun ErrorSlip(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.sello_error_title),
    retryLabel: String = stringResource(R.string.sello_retry),
    icon: SelloIcon? = null
) {
    Slip(modifier = modifier.fillMaxWidth()) {
        StateLayout(icon) {
            Column(
                horizontalAlignment = if (icon !=
                    null
                ) {
                    Alignment.CenterHorizontally
                } else {
                    Alignment.Start
                },
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            ) { StateText(title, message, icon != null) }
            Spacer(Modifier.height(SelloTheme.spacing.lg))
            SelloButton(text = retryLabel, onClick = onRetry, kind = ButtonKind.Outlined)
        }
    }
}

/** Left-aligned as in v1.1, or centred under a decorative [icon] as in v2. */
@Composable
private fun StateLayout(icon: SelloIcon?, content: @Composable () -> Unit) {
    if (icon == null) {
        Column { content() }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = SelloTheme.spacing.md)
        ) {
            Icon(
                painter = icon.painter(),
                contentDescription = null,
                tint = SelloTheme.colors.brand,
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            content()
        }
    }
}

@Composable
private fun StateText(title: String, body: String, centred: Boolean) {
    val align = if (centred) TextAlign.Center else TextAlign.Start
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = align,
        modifier = Modifier.semantics { heading() }
    )
    Spacer(Modifier.height(SelloTheme.spacing.xs))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = SelloTheme.colors.inkSoft,
        textAlign = align
    )
}

/**
 * The loading state of a slip: bars where figures will be. Callers show it only when
 * loading lasts long enough to notice. With reduced motion the bars do not pulse.
 */
@Composable
fun SlipSkeleton(modifier: Modifier = Modifier, lines: Int = 3) {
    val loading = stringResource(R.string.sello_loading)
    val alpha = if (SelloTheme.reducedMotion) {
        SKELETON_REST_ALPHA
    } else {
        val pulse by rememberInfiniteTransition(label = "skeleton").animateFloat(
            initialValue = 0.4f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SKELETON_PULSE_MILLIS), RepeatMode.Reverse),
            label = "skeleton"
        )
        pulse
    }
    Slip(
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {
            contentDescription = loading
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
            modifier = Modifier.graphicsLayer { this.alpha = alpha }
        ) {
            repeat(lines.coerceAtLeast(1)) { index ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(SkeletonWidths[index % SkeletonWidths.size])
                        .height(if (index == 0) 28.dp else 14.dp)
                        .background(SelloTheme.colors.paperDim, SelloTheme.shapes.bar)
                )
            }
        }
    }
}

private const val SKELETON_PULSE_MILLIS = 900
private const val SKELETON_REST_ALPHA = 0.7f
private val SkeletonWidths = listOf(0.45f, 0.8f, 0.6f)
