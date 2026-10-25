package com.software.sello.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The in-app keypad for amounts: 1 to 9, then 000, 0 and backspace. It only reports
 * presses; the draft, its 12-digit limit and every validation belong to the caller.
 * With [onComma] (dollars and the USD rate) the 000 key is the decimal comma instead,
 * and a hardware comma or period types it.
 *
 * A hardware keyboard works while focus is inside the keypad: digits, Backspace, and
 * Enter for [onSubmit] when one is given. A long press on backspace, or its "Borrar
 * todo" accessibility action, calls [onClearAll].
 */
@Composable
fun AmountKeypad(
    onDigit: (Int) -> Unit,
    onTripleZero: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSubmit: (() -> Unit)? = null,
    compact: Boolean = false,
    onComma: (() -> Unit)? = null
) {
    val hardware = Modifier.onPreviewKeyEvent { event ->
        if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val digit = HardwareDigits[event.key]
        when {
            digit != null -> onDigit(digit)

            event.key == Key.Backspace -> onBackspace()

            (event.key == Key.Comma || event.key == Key.Period || event.key == Key.NumPadDot) &&
                onComma != null -> onComma()

            (event.key == Key.Enter || event.key == Key.NumPadEnter) && onSubmit != null ->
                onSubmit()

            else -> return@onPreviewKeyEvent false
        }
        true
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else KeyGap),
        modifier = modifier.then(hardware)
    ) {
        for (row in listOf(1..3, 4..6, 7..9)) {
            KeyRow(compact) {
                for (digit in row) {
                    Key(enabled, compact = compact, onClick = {
                        onDigit(digit)
                    }) { KeyLabel(digit.toString()) }
                }
            }
        }
        KeyRow(compact) {
            if (onComma == null) {
                Key(enabled, compact = compact, onClick = onTripleZero) { KeyLabel("000") }
            } else {
                Key(
                    enabled,
                    compact = compact,
                    onClick = onComma,
                    description = stringResource(R.string.sello_decimal_comma)
                ) { KeyLabel(",") }
            }
            Key(enabled, compact = compact, onClick = { onDigit(0) }) { KeyLabel("0") }
            Key(
                enabled = enabled,
                compact = compact,
                delete = compact,
                onClick = onBackspace,
                onLongClick = onClearAll,
                longClickLabel = stringResource(R.string.sello_clear_all),
                description = stringResource(R.string.sello_backspace)
            ) {
                Icon(
                    painter = SelloIcon.Backspace.painter(),
                    contentDescription = null,
                    tint = if (compact) SelloTheme.colors.onBrandSoft else SelloTheme.colors.ink,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyRow(compact: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 0.dp else KeyGap),
        content = content
    )
}

@Composable
private fun KeyLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
        color = SelloTheme.colors.ink
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.Key(
    enabled: Boolean,
    compact: Boolean = false,
    delete: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    longClickLabel: String? = null,
    description: String? = null,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            )
            .semantics { if (description != null) contentDescription = description }
            .then(if (compact) Modifier.padding(2.dp) else Modifier)
            .alpha(if (enabled) 1f else DISABLED_KEY_ALPHA)
            .clip(SelloTheme.shapes.control)
            .background(if (delete) SelloTheme.colors.brandSoft else SelloTheme.colors.paper)
    ) { content() }
}

private val KeyGap = 6.dp
private const val DISABLED_KEY_ALPHA = 0.38f

private val HardwareDigits = mapOf(
    Key.Zero to 0, Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4,
    Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad0 to 0, Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4,
    Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9
)

/**
 * The Sello pad sliding up over a form, as when typing a goal's amount: [doneLabel]
 * ("Listo") closes it through [onDone], and each key is reported as on [AmountKeypad].
 * Reduced motion shows and hides it without moving.
 */
@Composable
fun SlidingKeypad(
    visible: Boolean,
    doneLabel: String,
    onDone: () -> Unit,
    onDigit: (Int) -> Unit,
    onTripleZero: () -> Unit,
    onBackspace: () -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
    onComma: (() -> Unit)? = null
) {
    val reduced = SelloTheme.reducedMotion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (reduced) EnterTransition.None else slideInVertically { it },
        exit = if (reduced) ExitTransition.None else slideOutVertically { it }
    ) {
        Column(
            Modifier.fillMaxWidth().background(SelloTheme.colors.desk)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SelloButton(doneLabel, onDone, kind = ButtonKind.Text)
            }
            AmountKeypad(
                onDigit,
                onTripleZero,
                onBackspace,
                onClearAll,
                onSubmit = onDone,
                onComma = onComma
            )
        }
    }
}
