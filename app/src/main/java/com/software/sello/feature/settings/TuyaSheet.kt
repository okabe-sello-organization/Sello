package com.software.sello.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
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
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.hueMeaning
import com.software.sello.designsystem.theme.hueName
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.presentation.appearance.label
import kotlin.math.roundToInt

/**
 * Tu tinta: a light and a dark preview of the hue being chosen, its name, a slider drawn in
 * the colours it gives, and Cancelar / Usar esta tinta. Nothing changes until Usar esta
 * tinta. Red and green hues add one line, since those colours also mark overspending and
 * goals. A save that failed keeps the sheet and the hue, names the ink still in use
 * ([current]) and offers Reintentar; while it saves, nothing else can be pressed.
 */
@Composable
internal fun TuyaSheet(
    draft: TuyaDraft,
    current: InkPreference,
    onAction: (SettingsAction) -> Unit
) {
    val cancel = { onAction(SettingsAction.Tuya.Cancel) }
    BackHandler(onBack = cancel)
    SelloSheet(
        title = stringResource(R.string.tuya_title),
        onDismiss = cancel,
        modifier = Modifier.testTag(TUYA_SHEET_TAG)
    ) {
        val name = stringResource(hueName(draft.hue))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            // The previews only show what the name and the slider already say.
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {}
        ) {
            TuyaPreview(draft.hue, dark = false, Modifier.weight(1f))
            TuyaPreview(draft.hue, dark = true, Modifier.weight(1f))
        }
        Text(
            name,
            style = MaterialTheme.typography.titleLarge,
            color = SelloTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 6.dp)
                .clearAndSetSemantics {}
        )
        HueSlider(
            hue = draft.hue,
            name = name,
            enabled = !draft.saving,
            onHue = { onAction(SettingsAction.Tuya.HueChanged(it)) },
            modifier = Modifier.testTag(TUYA_SLIDER_TAG)
        )
        // Always laid out, so the buttons don't move as the hue crosses red or green.
        val meaning = hueMeaning(draft.hue) != null
        Text(
            stringResource(R.string.tuya_meaning_note),
            style = MaterialTheme.typography.bodySmall,
            color = SelloTheme.colors.ink,
            modifier = Modifier
                .padding(start = 2.dp, end = 2.dp, top = 6.dp, bottom = SelloTheme.spacing.md)
                .alpha(if (meaning) 1f else 0f)
                .then(
                    if (meaning) {
                        Modifier.testTag(
                            TUYA_NOTE_TAG
                        )
                    } else {
                        Modifier.clearAndSetSemantics {
                        }
                    }
                )
        )
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
                    enabled = !draft.saving,
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
 * A small Recibo drawn in [hue]'s own theme: the band with the month, the remaining figure
 * with its stamp, and the dock. Sample content only; the stamp keeps its own green.
 */
@Composable
private fun TuyaPreview(hue: Int, dark: Boolean, modifier: Modifier = Modifier) {
    SelloTheme(ink = HueInk(hue), darkTheme = dark, reducedMotion = SelloTheme.reducedMotion) {
        val colors = SelloTheme.colors
        val corner = SelloTheme.shapes.inset
        Column(modifier.clip(corner).background(colors.desk)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.band)
                    .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 18.dp)
            ) {
                Text(
                    stringResource(R.string.tuya_preview_month),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBand,
                    modifier = Modifier.weight(1f)
                )
                Icon(SelloIcon.Settings.painter(), null, Modifier.size(16.dp), colors.onBand)
            }
            Column(Modifier.padding(horizontal = SelloTheme.spacing.sm).offset(y = (-12).dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(colors.paper, SelloTheme.shapes.stampSmall)
                        .padding(SelloTheme.spacing.sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.tuya_preview_label),
                            style = MaterialTheme.typography.labelSmall,
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
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = SelloTheme.type.weight.figure
                        ),
                        color = colors.ink
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = SelloTheme.spacing.sm)
                        .fillMaxWidth()
                        .height(30.dp)
                        .background(colors.brand, SelloTheme.shapes.stampSmall)
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
                style = MaterialTheme.typography.labelSmall,
                color = colors.inkSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
    }
}

/**
 * The hue slider: a track drawn in the light band of every hue and a thumb in the chosen
 * one. TalkBack reads it as the colour's [name] and can move it, a step at a time.
 */
@Composable
private fun HueSlider(
    hue: Int,
    name: String,
    enabled: Boolean,
    onHue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    val label = stringResource(R.string.tuya_slider)
    val update by rememberUpdatedState(onHue)
    val track = remember { Brush.horizontalGradient(trackStops()) }
    val thumbPx = with(LocalDensity.current) { ThumbSize.toPx() }
    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(SelloTheme.spacing.minTouchTarget)
            .semantics {
                contentDescription = label
                stateDescription = name
                progressBarRangeInfo = ProgressBarRangeInfo(
                    hue.toFloat(),
                    InkHue.RANGE.first.toFloat()..InkHue.RANGE.last.toFloat()
                )
                if (enabled) {
                    setProgress { target ->
                        // A screen reader's step can overshoot the ends; the slider stops there.
                        val wanted = target.roundToInt().coerceIn(InkHue.RANGE)
                        if (wanted != hue) update(wanted)
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled, thumbPx) {
                if (!enabled) return@pointerInput
                fun hueAt(x: Float): Int {
                    val span = (size.width - thumbPx).coerceAtLeast(1f)
                    val fraction = ((x - thumbPx / 2) / span).coerceIn(0f, 1f)
                    return (fraction * InkHue.RANGE.last).roundToInt()
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(hueAt(down.position.x))
                    horizontalDrag(down.id) { change ->
                        update(hueAt(change.position.x))
                        change.consume()
                    }
                }
            }
    ) {
        val span = maxWidth - ThumbSize
        Box(
            Modifier
                .padding(horizontal = ThumbSize / 2)
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(RoundedCornerShape(TrackHeight / 2))
                .background(track)
        )
        Box(
            Modifier
                .offset(x = span * (hue.toFloat() / InkHue.RANGE.last))
                .size(ThumbSize)
                .border(1.dp, colors.outline, CircleShape)
                .padding(1.dp)
                .border(3.dp, colors.paper, CircleShape)
                .background(HueInk(hue).swatch, CircleShape)
        )
    }
}

/** The light band at [TRACK_STOPS] + 1 evenly spaced hues, from 0 to 359. */
private fun trackStops() = List(TRACK_STOPS + 1) { step ->
    HueInk((step * InkHue.RANGE.last.toFloat() / TRACK_STOPS).roundToInt()).swatch
}

private const val TRACK_STOPS = 60

private val ThumbSize = 34.dp

private val TrackHeight = 28.dp

const val TUYA_SHEET_TAG = "settings:tuya"
const val TUYA_SLIDER_TAG = "settings:tuya:slider"
const val TUYA_NOTE_TAG = "settings:tuya:note"
const val TUYA_ERROR_TAG = "settings:tuya:error"
const val TUYA_CANCEL_TAG = "settings:tuya:cancel"
const val TUYA_USE_TAG = "settings:tuya:use"
