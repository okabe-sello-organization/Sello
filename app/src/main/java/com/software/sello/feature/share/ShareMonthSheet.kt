package com.software.sello.feature.share

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.software.sello.R
import com.software.sello.designsystem.component.ButtonKind
import com.software.sello.designsystem.component.CategoryCircle
import com.software.sello.designsystem.component.MoneyStyle
import com.software.sello.designsystem.component.MoneyText
import com.software.sello.designsystem.component.SelloButton
import com.software.sello.designsystem.component.SelloSheet
import com.software.sello.designsystem.component.SlipSkeleton
import com.software.sello.designsystem.component.Stamp
import com.software.sello.designsystem.component.StatusAction
import com.software.sello.designsystem.component.StatusKind
import com.software.sello.designsystem.component.StatusSlip
import com.software.sello.designsystem.component.StatusSlipDefaults
import com.software.sello.designsystem.component.TearLine
import com.software.sello.designsystem.component.signedText
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.recibo.answerLabel
import com.software.sello.feature.recibo.stampInk
import com.software.sello.feature.recibo.stampText
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.month.MonthNames
import com.software.sello.presentation.month.monthNames
import kotlin.math.ceil
import kotlin.math.max

const val SHARE_MONTH_ACTION_TAG = "resumen.share"
const val SHARE_SHEET_TAG = "share.sheet"
const val SHARE_IMAGE_TAG = "share.image"
const val SHARE_CONFIRM_TAG = "share.confirm"
const val SHARE_CANCEL_TAG = "share.cancel"
const val SHARE_WAITING_TAG = "share.waiting"
const val SHARE_FAILED_TAG = "share.failed"

/** Resumen's share button: opens the preview of the month on screen; nothing is written. */
@Composable
fun ShareMonthButton(viewModels: ViewModelProvider.Factory) {
    val model: ShareMonthViewModel = viewModel(factory = viewModels)
    IconButton(
        onClick = { model.onAction(ShareMonthAction.Open) },
        modifier = Modifier.testTag(SHARE_MONTH_ACTION_TAG)
    ) {
        Icon(SelloIcon.Share.painter(), stringResource(R.string.share_month_open))
    }
}

/**
 * The preview sheet over the whole of Resumen while it is open. Compartir imagen draws the
 * image shown into a PNG, has it placed in `cache/exports/` and opens Android's share
 * sheet with read access to that one file. The view model is the screen entry's own, as
 * [ShareMonthButton]'s.
 */
@Composable
fun ShareMonthRoot(viewModels: ViewModelProvider.Factory) {
    val model: ShareMonthViewModel = viewModel(factory = viewModels)
    val state by model.state.collectAsStateWithLifecycle()
    val open = state as? ShareMonthState.Open ?: return
    val layer = rememberGraphicsLayer()
    val capturing = open.step == ShareStep.Capturing
    LaunchedEffect(capturing) {
        if (!capturing) return@LaunchedEffect
        val image = try {
            layer.toImageBitmap()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        }
        model.onAction(image?.let(ShareMonthAction::Captured) ?: ShareMonthAction.CaptureFailed)
    }
    val context = LocalContext.current
    val chooserTitle = stringResource(R.string.share_month_chooser)
    val ready = open.step as? ShareStep.ReadyToShare
    LaunchedEffect(ready) {
        val share = ready?.share ?: return@LaunchedEffect
        val uri = share.uri.toUri()
        val send = Intent(Intent.ACTION_SEND)
            .setType(share.mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        try {
            context.startActivity(
                Intent.createChooser(send, chooserTitle)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            )
        } catch (_: ActivityNotFoundException) {
            Unit
        }
        model.onAction(ShareMonthAction.Opened)
    }
    ShareMonthSheet(open, model::onAction, capture = Modifier.recordInto(layer))
}

/**
 * Compartir [month]: the exact image, what is and isn't in it, Compartir imagen and
 * Cancelar. Older figures or a failed read show why there is nothing to share instead.
 * While the image is drawn and placed nothing can be pressed. [capture] goes on the image
 * itself, so what is shared is what is shown.
 */
@Composable
fun ShareMonthSheet(
    state: ShareMonthState.Open,
    onAction: (ShareMonthAction) -> Unit,
    capture: Modifier = Modifier
) {
    val names = monthNames()
    val month = names.inSentence(state.month)
    val cancel = { onAction(ShareMonthAction.Cancel) }
    BackHandler(onBack = cancel)
    SelloSheet(
        title = stringResource(R.string.share_month_title, month),
        onDismiss = cancel,
        modifier = Modifier.testTag(SHARE_SHEET_TAG)
    ) {
        val retry = StatusAction(StatusSlipDefaults.retryLabel) { onAction(ShareMonthAction.Retry) }
        when (val preview = state.preview) {
            SharePreview.Loading -> SlipSkeleton()

            is SharePreview.Ready -> {
                MonthShareCard(
                    preview.image,
                    names,
                    Modifier
                        .clip(ImageCorner)
                        .border(1.dp, SelloTheme.colors.rule, ImageCorner)
                        .then(capture)
                )
                Text(
                    stringResource(R.string.share_month_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = SelloTheme.colors.inkSoft,
                    modifier = Modifier
                        .padding(top = SelloTheme.spacing.md)
                        .fillMaxWidth()
                        .clip(SelloTheme.shapes.control)
                        .background(SelloTheme.colors.paperAlt)
                        .padding(SelloTheme.spacing.md)
                )
            }

            SharePreview.Waiting -> StatusSlip(
                kind = StatusKind.Older,
                message = stringResource(R.string.share_month_waiting, month),
                modifier = Modifier.testTag(SHARE_WAITING_TAG),
                action = retry
            )

            SharePreview.Failed -> StatusSlip(
                kind = StatusKind.LoadFailed(),
                message = stringResource(R.string.share_month_read_failed, month),
                action = retry
            )

            SharePreview.Empty -> StatusSlip(
                kind = StatusKind.Empty(
                    SelloIcon.Share,
                    stringResource(R.string.share_month_empty_title)
                ),
                message = stringResource(R.string.share_month_empty, names.name(state.month))
            )
        }
        if (state.step == ShareStep.Failed) {
            StatusSlip(
                kind = StatusKind.ActionFailed(),
                message = stringResource(R.string.share_month_failed),
                modifier = Modifier.padding(top = SelloTheme.spacing.md).testTag(SHARE_FAILED_TAG)
            )
        }
        Spacer(Modifier.height(SelloTheme.spacing.md))
        if (state.preview is SharePreview.Ready) {
            SelloButton(
                text = stringResource(R.string.share_month_confirm),
                onClick = { onAction(ShareMonthAction.Confirm) },
                icon = SelloIcon.Share,
                loading = state.busy,
                modifier = Modifier.fillMaxWidth().testTag(SHARE_CONFIRM_TAG)
            )
            Spacer(Modifier.height(SelloTheme.spacing.sm))
        }
        SelloButton(
            text = stringResource(R.string.share_month_cancel),
            onClick = cancel,
            kind = ButtonKind.Outlined,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(SHARE_CANCEL_TAG)
        )
    }
}

/**
 * The image to share: always the light paper in the person's ink, whatever the app's
 * theme, so it reads the same in any chat. Only [MonthShareImage]'s fields are drawn.
 */
@Composable
fun MonthShareCard(image: MonthShareImage, names: MonthNames, modifier: Modifier = Modifier) {
    SelloTheme(ink = SelloTheme.ink, darkTheme = false, reducedMotion = true) {
        val colors = SelloTheme.colors
        val side = SelloTheme.spacing.lg
        Column(modifier.fillMaxWidth().background(colors.paper).testTag(SHARE_IMAGE_TAG)) {
            Row(
                Modifier.fillMaxWidth().background(colors.band)
                    .padding(start = side, end = side, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    names.title(image.month),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = SelloTheme.type.weight.emphasis,
                    color = colors.onBand
                )
                Text(
                    if (image.closed) {
                        stringResource(R.string.share_month_closed)
                    } else {
                        stringResource(
                            R.string.share_month_as_of,
                            image.asOf.dayOfMonth,
                            names.inSentence(image.month)
                        )
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onBand.copy(alpha = SelloTheme.alpha.secondary)
                )
            }
            Column(Modifier.padding(horizontal = side, vertical = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        answerLabel(image.label),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.inkSoft
                    )
                    Stamp(stampText(image.stamp), stampInk(image.stamp))
                }
                MoneyText(
                    image.amount,
                    Modifier.padding(top = SelloTheme.spacing.sm),
                    style = MoneyStyle.Card,
                    color = colors.ink
                )
                Text(
                    image.limit?.let {
                        stringResource(
                            R.string.share_month_spent_of,
                            image.spent.signedText(showPlus = false),
                            it.signedText(showPlus = false)
                        )
                    } ?: stringResource(
                        R.string.share_month_spent,
                        image.spent.signedText(showPlus = false)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft
                )
                TearLine(bleed = side, verticalMargin = SelloTheme.spacing.md)
                image.categories.forEach { ShareLine(it) }
            }
            Column(Modifier.padding(horizontal = side)) {
                TearLine(bleed = side, verticalMargin = SelloTheme.spacing.sm)
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text(
                        stringResource(R.string.share_month_brand),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = SelloTheme.type.weight.emphasis,
                        color = colors.inkSoft
                    )
                    Text(
                        stringResource(R.string.share_month_budget_only),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.inkSoft
                    )
                }
            }
        }
    }
}

/** One category: its circle, name and limit or "sin límite", what it spent and a bar. */
@Composable
private fun ShareLine(line: MonthShareCategory) {
    val colors = SelloTheme.colors
    val limit = line.limit?.let {
        stringResource(R.string.share_month_limit, it.signedText(showPlus = false))
    } ?: stringResource(R.string.share_month_no_limit)
    val spoken = line.limit?.let {
        stringResource(R.string.share_month_line_spoken, line.name, line.spent.spoken, it.spoken)
    } ?: stringResource(R.string.share_month_line_spoken_open, line.name, line.spent.spoken)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp).clearAndSetSemantics {
            contentDescription = spoken
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryCircle(
            CategoryIcons.iconFor(line.iconKey),
            fraction = if (line.over) 1f else null,
            size = 32.dp,
            over = line.over,
            animate = false,
            unlimited = line.limit == null,
            archived = line.archived
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = SelloTheme.type.weight.emphasis)) {
                            append(line.name)
                        }
                        withStyle(SpanStyle(color = colors.inkSoft)) { append(" $limit") }
                    },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink
                )
                MoneyText(
                    line.spent,
                    Modifier.padding(start = SelloTheme.spacing.sm),
                    color = if (line.over) colors.semantic.loss else colors.ink,
                    fontWeight = SelloTheme.type.weight.emphasis
                )
            }
            line.fraction?.let { Track(it, line.over) }
        }
    }
}

/** How full the limit is: geometry only, the figures beside it are exact. */
@Composable
private fun Track(fraction: Float, over: Boolean) {
    val colors = SelloTheme.colors
    Box(
        Modifier.padding(top = 6.dp).fillMaxWidth().height(7.dp)
            .clip(TrackCorner).background(colors.paperAlt)
    ) {
        Box(
            Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(7.dp).clip(TrackCorner)
                .background(if (over) colors.semantic.loss else colors.brand)
        )
    }
}

/**
 * Draws the image as usual and also records it, at least [ExportWidthPx] wide, into
 * [layer], from which Compartir imagen takes the PNG. The rounded corner and border around
 * it are on screen only: the PNG is the whole paper.
 */
private fun Modifier.recordInto(layer: GraphicsLayer): Modifier = drawWithContent {
    drawContent()
    val scale = max(1f, ExportWidthPx / size.width)
    layer.record(
        size = IntSize(ceil(size.width * scale).toInt(), ceil(size.height * scale).toInt())
    ) {
        scale(scale, pivot = Offset.Zero) { this@drawWithContent.drawContent() }
    }
}

private val ImageCorner = RoundedCornerShape(14.dp)
private val TrackCorner = RoundedCornerShape(4.dp)

/** A phone screen's width, so the image reads sharply when opened full screen. */
private const val ExportWidthPx = 1080f
