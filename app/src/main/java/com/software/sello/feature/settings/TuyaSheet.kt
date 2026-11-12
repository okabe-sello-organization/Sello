package com.software.sello.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.ButtonPair
import com.software.sello.designsystem.component.FieldMessage
import com.software.sello.designsystem.component.FieldMessageText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloSheet
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StampInk
import com.software.sello.designsystem.component.rememberEditedText
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.ColourInk
import com.software.sello.designsystem.theme.HueMeaning
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.colourMeaning
import com.software.sello.designsystem.theme.colourName
import com.software.sello.domain.model.InkPreference
import com.software.sello.presentation.appearance.label

/**
 * Tu tinta: a light and a dark preview of the colour being made, its name and its code (a
 * field: an exact code can be typed), a colour square and a colour bar, Antes and Ahora
 * tiles, one line holding the hint or the note on red and green, and Cancelar / Usar esta
 * tinta. Nothing changes until Usar esta tinta. A save that failed keeps the sheet and the
 * colour, names the ink still in use ([current]) and offers Reintentar; while it saves,
 * nothing else can be pressed. TalkBack doesn't reach the square and the bar (ADR 0028).
 */
@Composable
internal fun TuyaSheet(
    draft: TuyaDraft,
    current: InkPreference,
    onAction: (SettingsAction) -> Unit
) {
    val cancel = { onAction(SettingsAction.Tuya.Cancel) }
    val enabled = !draft.saving
    val rgb = draft.colour.rgb
    BackHandler(onBack = cancel)
    SelloSheet(
        title = stringResource(R.string.tuya_title),
        onDismiss = cancel,
        modifier = Modifier.testTag(TUYA_SHEET_TAG)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // The previews only show what the name and the code already say.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
        ) {
            TuyaPreview(rgb, dark = false, Modifier.weight(1f))
            TuyaPreview(rgb, dark = true, Modifier.weight(1f))
        }
        val name = stringResource(colourName(rgb))
        Row(
            horizontalArrangement = Arrangement.spacedBy(
                SelloTheme.spacing.sm,
                Alignment.CenterHorizontally
            ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.xs)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.titleLarge,
                color = SelloTheme.colors.ink,
                modifier = Modifier.testTag(TUYA_NAME_TAG)
            )
            CodeField(draft.code, enabled, onAction)
        }
        ColourSquare(draft.pick, rgb, enabled, onAction)
        ColourBar(draft.pick.hue, enabled, onAction)
        Row(
            horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
            modifier = Modifier.fillMaxWidth().padding(top = SelloTheme.spacing.xs)
        ) {
            ColourTile(
                rgb = draft.before.rgb,
                caption = stringResource(R.string.tuya_before, current.label()),
                now = false,
                onClick = { onAction(SettingsAction.Tuya.BeforeTapped) }.takeIf { enabled },
                modifier = Modifier.weight(1f).testTag(TUYA_BEFORE_TAG)
            )
            ColourTile(
                rgb = rgb,
                caption = stringResource(R.string.tuya_now, name),
                now = true,
                onClick = null,
                modifier = Modifier.weight(1f)
            )
        }
        TuyaLine(colourMeaning(rgb))
        if (draft.failed) {
            FieldMessageText(
                FieldMessage(stringResource(R.string.tuya_failed, current.label())),
                Modifier.padding(bottom = SelloTheme.spacing.md).testTag(TUYA_ERROR_TAG)
            )
        }
        ButtonPair(
            firstWeight = 1f,
            secondWeight = 2f,
            first = {
                SelloButton(
                    text = stringResource(R.string.tuya_cancel),
                    onClick = cancel,
                    kind = ButtonKind.Outlined,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth().testTag(TUYA_CANCEL_TAG)
                )
            }
        ) {
            SelloButton(
                text = stringResource(if (draft.failed) R.string.tuya_retry else R.string.tuya_use),
                onClick = { onAction(SettingsAction.Tuya.Use) },
                loading = draft.saving,
                modifier = Modifier.fillMaxWidth().testTag(TUYA_USE_TAG)
            )
        }
    }
}

/**
 * The code beside the name: a normal text field in a dashed box with a pencil. What is
 * typed goes to the view model as it is; leaving the field (or Listo) shows the current
 * colour's code again. Drawn in capitals, whatever the case typed.
 */
@Composable
private fun CodeField(code: String, enabled: Boolean, onAction: (SettingsAction) -> Unit) {
    val colors = SelloTheme.colors
    val label = stringResource(R.string.tuya_code)
    val edited = rememberEditedText(code)
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val style = MaterialTheme.typography.titleSmall.copy(
        fontFamily = FontFamily.Monospace,
        color = colors.inkSoft
    )
    // Wide enough for "#" and six digits, whatever is typed, so the name never moves.
    val measurer = rememberTextMeasurer()
    val width = with(LocalDensity.current) {
        measurer.measure(CODE_WIDEST, style).size.width.toDp() + 2.dp
    }
    val dash = colors.outline
    BasicTextField(
        value = edited.field,
        onValueChange = {
            edited.edit(it)?.let { text -> onAction(SettingsAction.Tuya.CodeTyped(text)) }
        },
        enabled = enabled,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(colors.brand),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        visualTransformation = Capitals,
        modifier = Modifier
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .onFocusChanged {
                if (focused && !it.isFocused) onAction(SettingsAction.Tuya.CodeLeft)
                focused = it.isFocused
            }
            .semantics { contentDescription = label }
            .testTag(TUYA_CODE_TAG),
        decorationBox = { field ->
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
                    modifier = Modifier
                        .drawBehind {
                            val stroke = 1.5.dp.toPx()
                            drawRoundRect(
                                color = dash,
                                topLeft = Offset(stroke / 2, stroke / 2),
                                size = size.copy(
                                    width = size.width - stroke,
                                    height = size.height - stroke
                                ),
                                cornerRadius = CornerRadius(6.dp.toPx()),
                                style = Stroke(
                                    width = stroke,
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(4.dp.toPx(), 3.dp.toPx())
                                    )
                                )
                            )
                        }
                        .padding(horizontal = 6.dp, vertical = SelloTheme.spacing.xs)
                ) {
                    Box(Modifier.width(width)) { field() }
                    Icon(SelloIcon.Edit.painter(), null, Modifier.size(14.dp), colors.inkSoft)
                }
            }
        }
    )
}

/** Shows the code in capitals; the text and the cursor stay as typed. */
private object Capitals : VisualTransformation {
    override fun filter(text: AnnotatedString) =
        TransformedText(AnnotatedString(text.text.uppercase()), OffsetMapping.Identity)
}

/**
 * The colour square: the bar's hue from white (left) to full (right), darkening to black at
 * the bottom; a thumb on the colour being made. A tap or a drag moves it.
 */
@Composable
private fun ColourSquare(
    pick: TuyaPick,
    rgb: Int,
    enabled: Boolean,
    onAction: (SettingsAction) -> Unit
) {
    val update by rememberUpdatedState(onAction)
    val full = opaque(TuyaPick(pick.hue, 1f, 1f).rgb())
    BoxWithConstraints(
        Modifier
            .padding(top = SelloTheme.spacing.xs)
            .fillMaxWidth()
            .height(SquareHeight)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                trackDrags { at ->
                    update(
                        SettingsAction.Tuya.SquareMoved(
                            saturation = (at.x / size.width).coerceIn(0f, 1f),
                            value = 1f - (at.y / size.height).coerceIn(0f, 1f)
                        )
                    )
                }
            }
            .testTag(TUYA_SQUARE_TAG)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(SelloTheme.shapes.control)
                .background(Brush.horizontalGradient(listOf(Color.White, full)))
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        )
        Thumb(
            fill = opaque(rgb),
            size = SquareThumb,
            modifier = Modifier.offset(
                x = maxWidth * pick.saturation - SquareThumb / 2,
                y = maxHeight * (1f - pick.value) - SquareThumb / 2
            )
        )
    }
}

/** The colour bar: every hue, red to red; a thumb on the bar's hue. A tap or a drag moves it. */
@Composable
private fun ColourBar(hue: Float, enabled: Boolean, onAction: (SettingsAction) -> Unit) {
    val update by rememberUpdatedState(onAction)
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(SelloTheme.spacing.minTouchTarget)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                trackDrags { at ->
                    val fraction = (at.x / size.width).coerceIn(0f, 1f)
                    update(
                        SettingsAction.Tuya.BarMoved((fraction * FULL_TURN).coerceAtMost(LAST_HUE))
                    )
                }
            }
            .testTag(TUYA_BAR_TAG)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(BarHeight)
                .clip(RoundedCornerShape(BarHeight / 2))
                .background(Brush.horizontalGradient(BarStops))
        )
        Thumb(
            fill = opaque(TuyaPick(hue, 1f, 1f).rgb()),
            size = BarHeight,
            modifier = Modifier.offset(x = maxWidth * (hue / FULL_TURN) - BarHeight / 2)
        )
    }
}

/** Calls [at] with the place of the first touch and of every move after it, consumed. */
private suspend fun PointerInputScope.trackDrags(at: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        at(down.position)
        drag(down.id) { change ->
            at(change.position)
            change.consume()
        }
    }
}

@Composable
private fun Thumb(fill: Color, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .border(1.dp, SelloTheme.colors.outline, CircleShape)
            .padding(1.dp)
            .border(3.dp, Color.White, CircleShape)
            .background(fill, CircleShape)
    )
}

/**
 * Antes or Ahora: the colour with a white "Aa" and its caption. Antes ([onClick]) goes back
 * to the ink in use when the sheet opened; Ahora is outlined in ink.
 */
@Composable
private fun ColourTile(
    rgb: Int,
    caption: String,
    now: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val shape = SelloTheme.shapes.control
    Column(
        modifier
            .clip(shape)
            .border(1.5.dp, if (now) colors.ink else colors.rule, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(6.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(opaque(rgb), RoundedCornerShape(5.dp))
                .padding(horizontal = 9.dp)
                .clearAndSetSemantics {}
        ) {
            Text(
                stringResource(R.string.tuya_sample),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 5.dp)
        ) {
            Text(
                caption,
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSoft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!now) {
                Icon(SelloIcon.Autorenew.painter(), null, Modifier.size(14.dp), colors.inkSoft)
            }
        }
    }
}

/**
 * The one line under the tiles: the hint, or near the excess red or the goal green the note in
 * a soft red box. All three are laid out, unseen, so the tallest sets the height and nothing
 * below moves when it changes.
 */
@Composable
private fun TuyaLine(meaning: HueMeaning?) {
    val hint = stringResource(R.string.tuya_hint)
    val red = stringResource(R.string.tuya_near_red)
    val green = stringResource(R.string.tuya_near_green)
    val shown = when (meaning) {
        HueMeaning.Red -> red
        HueMeaning.Green -> green
        null -> hint
    }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = SelloTheme.spacing.sm, bottom = 10.dp)
            .testTag(TUYA_LINE_TAG)
    ) {
        listOf(hint to false, red to true, green to true).forEach { (text, note) ->
            LineContent(text, note, Modifier.alpha(0f).clearAndSetSemantics {})
        }
        LineContent(
            shown,
            note = meaning != null,
            modifier = Modifier
                .matchParentSize()
                .semantics(mergeDescendants = true) {}
                .testTag(if (meaning != null) TUYA_NOTE_TAG else TUYA_HINT_TAG)
        )
    }
}

@Composable
private fun LineContent(text: String, note: Boolean, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (note) colors.semantic.lossSoft else Color.Transparent,
                SelloTheme.shapes.stampSmall
            )
            .padding(horizontal = 10.dp, vertical = SelloTheme.spacing.sm)
    ) {
        if (note) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(SelloTheme.spacing.sm)
                    .background(colors.semantic.loss, CircleShape)
            )
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = if (note) colors.semantic.onLossSoft else colors.inkSoft
        )
    }
}

/**
 * A small Recibo drawn in [rgb]'s own theme: the band, Restante with AL DÍA, a category over
 * its limit, a goal's band, a selected chip and Anotar. Sample content only; the stamps and
 * the goal keep their own colours.
 */
@Composable
private fun TuyaPreview(rgb: Int, dark: Boolean, modifier: Modifier = Modifier) {
    SelloTheme(ink = ColourInk(rgb), darkTheme = dark, reducedMotion = SelloTheme.reducedMotion) {
        val colors = SelloTheme.colors
        val small = MaterialTheme.typography.labelSmall
        Column(modifier.clip(SelloTheme.shapes.inset).background(colors.desk)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.band)
                    .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 20.dp)
            ) {
                Text(
                    stringResource(R.string.tuya_preview_month),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBand,
                    modifier = Modifier.weight(1f)
                )
                Icon(SelloIcon.Settings.painter(), null, Modifier.size(14.dp), colors.onBand)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = SelloTheme.spacing.sm).offset(y = (-13).dp)
            ) {
                PreviewCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tuya_preview_label),
                            style = small,
                            color = colors.inkSoft,
                            modifier = Modifier.weight(1f)
                        )
                        Stamp(
                            stringResource(R.string.tuya_preview_stamp),
                            StampInk.Good,
                            haptic = false
                        )
                    }
                    Text(
                        stringResource(R.string.tuya_preview_amount),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = SelloTheme.type.weight.figure
                        ),
                        color = colors.ink
                    )
                }
                PreviewCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tuya_preview_category),
                            style = small,
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Stamp(
                            stringResource(R.string.tuya_preview_over),
                            StampInk.Over,
                            haptic = false
                        )
                    }
                    Text(
                        stringResource(R.string.tuya_preview_over_by),
                        style = small,
                        color = colors.semantic.loss
                    )
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(
                            stringResource(R.string.tuya_preview_goal),
                            style = small,
                            color = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            stringResource(R.string.tuya_preview_goal_share),
                            style = small,
                            color = colors.inkSoft
                        )
                    }
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(colors.paperAlt)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(GOAL_SHARE)
                                .fillMaxHeight()
                                .background(colors.goalBand)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PreviewChip(stringResource(R.string.tuya_preview_month_chip), chosen = true)
                    PreviewChip(stringResource(R.string.tuya_preview_year_chip), chosen = false)
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(colors.brand, RoundedCornerShape(6.dp))
                ) {
                    Icon(SelloIcon.Add.painter(), null, Modifier.size(14.dp), colors.onBrand)
                    Text(
                        stringResource(R.string.tuya_preview_dock),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onBrand
                    )
                }
            }
            Text(
                stringResource(
                    if (dark) R.string.settings_theme_dark else R.string.settings_theme_light
                ),
                style = small,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset(y = (-7).dp)
            )
        }
    }
}

@Composable
private fun PreviewCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SelloTheme.colors.paper, RoundedCornerShape(6.dp))
            .padding(horizontal = SelloTheme.spacing.sm, vertical = 7.dp)
    ) { content() }
}

@Composable
private fun PreviewChip(text: String, chosen: Boolean) {
    val colors = SelloTheme.colors
    val shape = RoundedCornerShape(50)
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (chosen) colors.onBrandSoft else colors.inkSoft,
        modifier = Modifier
            .then(
                if (chosen) {
                    Modifier.background(colors.brandSoft, shape)
                } else {
                    Modifier.border(1.dp, colors.rule, shape)
                }
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}

private fun opaque(rgb: Int) = Color(OPAQUE or rgb)

private const val OPAQUE = 0xFF000000.toInt()
private const val FULL_TURN = 360f

/** The bar's right end: just short of a full turn, which is red again at 0. */
private const val LAST_HUE = 359.9f
private const val GOAL_SHARE = 0.6f

/** The code as wide as it can be drawn: monospaced, any seven characters. */
private const val CODE_WIDEST = "#DDDDDD"

/** Red, yellow, green, cyan, blue, magenta and red again: the bar's hues 0 to 360. */
private val BarStops = listOf(
    Color(0xFFFF0000),
    Color(0xFFFFFF00),
    Color(0xFF00FF00),
    Color(0xFF00FFFF),
    Color(0xFF0000FF),
    Color(0xFFFF00FF),
    Color(0xFFFF0000)
)

private val SquareHeight = 132.dp
private val SquareThumb = 22.dp
private val BarHeight = 26.dp

const val TUYA_SHEET_TAG = "settings:tuya"
const val TUYA_NAME_TAG = "settings:tuya:name"
const val TUYA_CODE_TAG = "settings:tuya:code"
const val TUYA_SQUARE_TAG = "settings:tuya:square"
const val TUYA_BAR_TAG = "settings:tuya:bar"
const val TUYA_BEFORE_TAG = "settings:tuya:before"
const val TUYA_LINE_TAG = "settings:tuya:line"
const val TUYA_HINT_TAG = "settings:tuya:hint"
const val TUYA_NOTE_TAG = "settings:tuya:note"
const val TUYA_ERROR_TAG = "settings:tuya:error"
const val TUYA_CANCEL_TAG = "settings:tuya:cancel"
const val TUYA_USE_TAG = "settings:tuya:use"
