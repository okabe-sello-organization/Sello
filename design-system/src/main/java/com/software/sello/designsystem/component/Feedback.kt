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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.testTag
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
 * The frame every dialog slip shares: plain paper over a 55 % dim, [title] as its
 * heading, [content] under it, scrolling when the slip would not fit, and [buttons], when
 * there are any, under a tear line where they stay in view. Back and a tap outside are
 * [onDismissRequest]; a caller that must not be dismissed, such as while busy, passes one
 * that does nothing. [ConfirmSlip], [ChoiceSlip] and [OptionSlip] are drawn with it; use it
 * for a slip with one answer or none, and put two answers in a [ButtonPair].
 */
@Composable
fun DialogSlip(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    buttons: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    Dialog(onDismissRequest = onDismissRequest) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val scrim = SelloTheme.alpha.scrim
        SideEffect { window?.setDimAmount(scrim) }
        // The dialog's own window would take the system's text size; keep the caller's.
        CompositionLocalProvider(LocalDensity provides density) {
            Slip(
                pinked = false,
                contentPadding = SlipDefaults.PlainPadding,
                modifier = modifier.semantics { paneTitle = title }
            ) {
                Text(
                    text = title,
                    style = SelloTheme.type.dialogTitle,
                    modifier = Modifier.semantics { heading() }
                )
                Spacer(Modifier.height(SelloTheme.spacing.sm))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    content()
                }
                if (buttons != null) {
                    TearLine(bleed = 20.dp)
                    buttons()
                }
            }
        }
    }
}

/**
 * A confirmation for something that cannot be undone. [message] says what is affected
 * and how much, with the consequence in bold. Back and a tap outside cancel. While
 * [busy] the action shows a spinner and nothing can be pressed or dismissed, so the
 * action is requested once. The two buttons share the width; when a word of a label would
 * not fit they go one above the other, the action on top. A slip whose labels are long
 * can narrow [buttonPadding] so each stays on one line.
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
    dismissLabel: String = stringResource(R.string.sello_cancel),
    buttonPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    DialogSlip(
        title = title,
        onDismissRequest = { if (!busy) onDismiss() },
        buttons = {
            ConfirmButtons(
                dismissLabel = dismissLabel,
                onDismiss = onDismiss,
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                destructive = destructive,
                busy = busy,
                confirmWeight = 1f,
                buttonPadding = buttonPadding
            )
        }
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * [ConfirmSlip] with any [content] under the title, such as points to read before
 * saving a file. The content scrolls when the slip would not fit; the buttons stay
 * in view and go one above the other, the action on top, when a label would not fit.
 * The action stays off until [confirmEnabled], such as until a word that confirms it is
 * typed.
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
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    DialogSlip(
        title = title,
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = modifier,
        buttons = {
            // The action's label is usually the longer one: it gets two thirds.
            ConfirmButtons(
                dismissLabel = dismissLabel,
                onDismiss = onDismiss,
                confirmLabel = confirmLabel,
                onConfirm = onConfirm,
                destructive = destructive,
                busy = busy,
                confirmWeight = 2f,
                confirmEnabled = confirmEnabled
            )
        },
        content = content
    )
}

/** Cancelar outlined and the action filled, in loss ink when [destructive]. */
@Composable
private fun ConfirmButtons(
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    destructive: Boolean,
    busy: Boolean,
    confirmWeight: Float,
    confirmEnabled: Boolean = true,
    buttonPadding: PaddingValues = ButtonDefaults.ContentPadding
) {
    ButtonPair(
        firstWeight = 1f,
        secondWeight = confirmWeight,
        gap = SelloTheme.spacing.md,
        first = {
            SelloButton(
                text = dismissLabel,
                onClick = onDismiss,
                kind = ButtonKind.Outlined,
                enabled = !busy,
                contentPadding = buttonPadding
            )
        },
        second = {
            SelloButton(
                text = confirmLabel,
                onClick = onConfirm,
                tone = if (destructive) ButtonTone.Loss else ButtonTone.Brand,
                enabled = confirmEnabled,
                loading = busy,
                contentPadding = buttonPadding
            )
        }
    )
}

/**
 * A question with two answers, neither of them destructive, such as whether a change
 * applies to the next ones too: [firstLabel] outlined, [secondLabel] filled, and
 * [backLabel] under them to go back without answering. The slip is [DialogSlip].
 * Back and a tap outside are [onBack]. The answers stand one above the other, the filled
 * one on top, when they do not fit side by side. While [busy] the answer given shows a
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
    ChoiceSlip(
        title = title,
        firstLabel = firstLabel,
        onFirst = onFirst,
        secondLabel = secondLabel,
        onSecond = onSecond,
        backLabel = backLabel,
        onBack = onBack,
        busy = busy
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * [ChoiceSlip] with any [content] under the title, such as a choice of format and the
 * points to read before acting. The content scrolls when the slip would not fit; the
 * answers stay in view.
 */
@Composable
fun ChoiceSlip(
    title: String,
    firstLabel: String,
    onFirst: () -> Unit,
    secondLabel: String,
    onSecond: () -> Unit,
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // Which answer was given, so the spinner shows on that one.
    var answered by remember { mutableIntStateOf(0) }
    DialogSlip(
        title = title,
        onDismissRequest = { if (!busy) onBack() },
        modifier = modifier,
        buttons = {
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
        },
        content = content
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

/** The skeleton's resting pulse, one role (no alpha token). */
private const val SKELETON_REST_ALPHA = 0.7f
private val SkeletonWidths = listOf(0.45f, 0.8f, 0.6f)
