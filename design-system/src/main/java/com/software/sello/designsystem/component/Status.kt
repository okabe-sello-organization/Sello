package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * What news a [StatusSlip] brings, with the words that belong to it. The kind decides the
 * slip's icon, ink, layout and button; the compiler checks that each kind gets the words
 * it needs: Empty a title, Older none, the others an optional one.
 */
sealed interface StatusKind {
    /**
     * Nothing to show yet, such as no expenses this month, under [title]. The only kind
     * whose [icon] the caller picks, to fit what is missing.
     */
    data class Empty(val icon: SelloIcon, val title: String) : StatusKind

    /**
     * Content that could not be read, and nothing read before to show instead. It never
     * sits above figures: older figures that are still shown take [Older]. A null [title]
     * is "No se pudo cargar".
     */
    data class LoadFailed(val title: String? = null) : StatusKind

    /** Figures still shown are the last ones read: a refresh could not update them. */
    data object Older : StatusKind

    /** Something the person did, such as a save, an export or a backup, failed. */
    data class ActionFailed(val title: String? = null) : StatusKind

    /**
     * The outcome of something the person did is not known yet. Its action checks, or
     * starts a safe new attempt when it cannot be checked; it never says it failed.
     */
    data class Unknown(val title: String? = null) : StatusKind

    /**
     * The thing shown no longer exists, such as a category deleted meanwhile. A null
     * [title] is "Ya no está".
     */
    data class Gone(val title: String? = null) : StatusKind
}

/**
 * The one action of a [StatusSlip], with its [label]; [StatusSlipDefaults] has each
 * kind's usual one. While [busy] it shows a spinner, says it is busy and ignores presses,
 * so it is requested once.
 */
class StatusAction(val label: String, val busy: Boolean = false, val onClick: () -> Unit)

/** The usual action labels of a [StatusSlip]. */
object StatusSlipDefaults {
    /** Reading or doing it again: LoadFailed, Older and ActionFailed. */
    val retryLabel: String
        @Composable get() = stringResource(R.string.sello_retry)

    /** Finding out how an Unknown outcome ended. */
    val checkLabel: String
        @Composable get() = stringResource(R.string.sello_check)

    /** The way back from something Gone. */
    val backLabel: String
        @Composable get() = stringResource(R.string.sello_back)
}

/**
 * A slip that says what kind of news it brings: nothing yet, a failed read, older
 * figures, a failed action, an unknown outcome or something gone. [kind] decides the
 * icon, ink, layout and button and carries the title; [message] is the sentence and
 * [action] at most one thing to do.
 *
 * [StatusKind.Empty], [StatusKind.LoadFailed] and [StatusKind.Gone] are centred under a
 * large decorative icon and stand in for content. [StatusKind.Older],
 * [StatusKind.ActionFailed] and [StatusKind.Unknown] are flat inline slips that sit with
 * the content they speak of. Every kind but Empty is read aloud when it appears.
 */
@Composable
fun StatusSlip(
    kind: StatusKind,
    message: String,
    modifier: Modifier = Modifier,
    action: StatusAction? = null
) {
    StatusSlip(kind, AnnotatedString(message), modifier, action)
}

/**
 * [StatusSlip] whose [message] has styled parts, such as figures in bold. Only their
 * weight, style and other shape is kept: a part's own colour or brush is dropped, so the
 * kind's ink colours the whole sentence.
 */
@Composable
fun StatusSlip(
    kind: StatusKind,
    message: AnnotatedString,
    modifier: Modifier = Modifier,
    action: StatusAction? = null
) {
    val look = kind.look()
    val title = kind.title() ?: look.defaultTitle?.let { stringResource(it) }
    val inKindsInk = message.withoutInk()
    when (look.layout) {
        StatusLayout.Centred -> CentredStatus(look, title, inKindsInk, action, modifier)
        StatusLayout.Inline -> InlineStatus(look, title, inKindsInk, action, modifier)
    }
}

/**
 * [this] with no colour or brush of its own on any part, so the text's colour shows. Every
 * other style and annotation, such as how a figure is read aloud, is kept.
 */
private fun AnnotatedString.withoutInk(): AnnotatedString = mapAnnotations { range ->
    val style = range.item as? SpanStyle
    if (style == null || (style.color == Color.Unspecified && style.brush == null)) {
        range
    } else {
        AnnotatedString.Range(
            SpanStyle(
                fontSize = style.fontSize,
                fontWeight = style.fontWeight,
                fontStyle = style.fontStyle,
                fontSynthesis = style.fontSynthesis,
                fontFamily = style.fontFamily,
                fontFeatureSettings = style.fontFeatureSettings,
                letterSpacing = style.letterSpacing,
                baselineShift = style.baselineShift,
                textGeometricTransform = style.textGeometricTransform,
                localeList = style.localeList,
                background = style.background,
                textDecoration = style.textDecoration,
                shadow = style.shadow,
                platformStyle = style.platformStyle,
                drawStyle = style.drawStyle
            ),
            range.start,
            range.end,
            range.tag
        )
    }
}

private fun StatusKind.title(): String? = when (this) {
    is StatusKind.Empty -> title
    is StatusKind.LoadFailed -> title
    StatusKind.Older -> null
    is StatusKind.ActionFailed -> title
    is StatusKind.Unknown -> title
    is StatusKind.Gone -> title
}

/** The test tag of a [StatusSlip]'s icon, where its kind has one. */
const val STATUS_ICON_TAG = "status-slip:icon"

/** Where a kind's slip sits: centred in place of content, or inline beside it. */
internal enum class StatusLayout { Centred, Inline }

/** A theme ink, named so a kind's look can be checked without composing it. */
internal enum class StatusInk { Brand, InkSoft, Warn, Loss }

/** Everything a kind decides. */
internal data class StatusLook(
    val layout: StatusLayout,
    val icon: SelloIcon?,
    val iconInk: StatusInk,
    val messageInk: StatusInk,
    val button: ButtonKind,
    val announced: Boolean,
    val defaultTitle: Int?
)

internal fun StatusKind.look(): StatusLook = when (this) {
    is StatusKind.Empty -> StatusLook(
        layout = StatusLayout.Centred,
        icon = icon,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = false,
        defaultTitle = null
    )

    is StatusKind.LoadFailed -> StatusLook(
        layout = StatusLayout.Centred,
        icon = SelloIcon.Error,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = R.string.sello_error_title
    )

    StatusKind.Older -> StatusLook(
        layout = StatusLayout.Inline,
        icon = null,
        iconInk = StatusInk.Warn,
        messageInk = StatusInk.Warn,
        button = ButtonKind.Text,
        announced = true,
        defaultTitle = null
    )

    is StatusKind.ActionFailed -> StatusLook(
        layout = StatusLayout.Inline,
        icon = SelloIcon.Error,
        iconInk = StatusInk.Loss,
        messageInk = StatusInk.Loss,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = null
    )

    is StatusKind.Unknown -> StatusLook(
        layout = StatusLayout.Inline,
        icon = SelloIcon.Info,
        iconInk = StatusInk.Warn,
        messageInk = StatusInk.Warn,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = null
    )

    is StatusKind.Gone -> StatusLook(
        layout = StatusLayout.Centred,
        icon = SelloIcon.Block,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = R.string.sello_status_gone_title
    )
}

@Composable
private fun StatusInk.color(): Color = when (this) {
    StatusInk.Brand -> SelloTheme.colors.brand
    StatusInk.InkSoft -> SelloTheme.colors.inkSoft
    StatusInk.Warn -> SelloTheme.colors.semantic.warn
    StatusInk.Loss -> SelloTheme.colors.semantic.loss
}

private fun Modifier.announcedIf(announced: Boolean): Modifier =
    if (announced) semantics { liveRegion = LiveRegionMode.Polite } else this

/** Empty, LoadFailed and Gone: centred under a large decorative icon. */
@Composable
private fun CentredStatus(
    look: StatusLook,
    title: String?,
    message: AnnotatedString,
    action: StatusAction?,
    modifier: Modifier
) {
    Slip(modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = SelloTheme.spacing.md)
        ) {
            look.icon?.let { StatusIcon(it, look.iconInk, 44.dp) }
            Spacer(Modifier.height(SelloTheme.spacing.sm))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.announcedIf(look.announced)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(SelloTheme.spacing.xs))
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = look.messageInk.color(),
                    textAlign = TextAlign.Center
                )
            }
            if (action != null) {
                Spacer(Modifier.height(SelloTheme.spacing.lg))
                StatusButton(action, look.button)
            }
        }
    }
}

/** Older, ActionFailed and Unknown: a flat slip beside the content it speaks of. */
@Composable
private fun InlineStatus(
    look: StatusLook,
    title: String?,
    message: AnnotatedString,
    action: StatusAction?,
    modifier: Modifier
) {
    Slip(modifier = modifier.fillMaxWidth(), pinked = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md)) {
            look.icon?.let { StatusIcon(it, look.iconInk, INLINE_ICON) }
            Column(
                verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.xs),
                modifier = Modifier.weight(1f).announcedIf(look.announced)
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = look.messageInk.color()
                )
            }
        }
        if (action != null) {
            if (look.button != ButtonKind.Text) Spacer(Modifier.height(SelloTheme.spacing.md))
            // The button lines up with the words, not the icon beside them.
            val indent = if (look.icon == null) 0.dp else INLINE_ICON + SelloTheme.spacing.md
            StatusButton(action, look.button, Modifier.padding(start = indent))
        }
    }
}

@Composable
private fun StatusIcon(icon: SelloIcon, ink: StatusInk, size: Dp) {
    Icon(
        painter = icon.painter(),
        contentDescription = null,
        tint = ink.color(),
        modifier = Modifier.size(size).testTag(STATUS_ICON_TAG)
    )
}

@Composable
private fun StatusButton(action: StatusAction, kind: ButtonKind, modifier: Modifier = Modifier) {
    val busy = stringResource(R.string.sello_busy)
    // A text button's words start where the message does.
    val padding = if (kind == ButtonKind.Text) TextButtonPadding else ButtonDefaults.ContentPadding
    SelloButton(
        text = action.label,
        onClick = action.onClick,
        // A text button is drawn 46 dp tall; the touch target is 48 dp (ARCHITECTURE §6).
        modifier = modifier
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
            .semantics { if (action.busy) stateDescription = busy },
        kind = kind,
        loading = action.busy,
        contentPadding = padding
    )
}

private val TextButtonPadding: PaddingValues
    @Composable @ReadOnlyComposable
    get() = PaddingValues(
        start = 0.dp,
        top = SelloTheme.spacing.sm,
        end = 24.dp,
        bottom = SelloTheme.spacing.sm
    )

private val INLINE_ICON = 24.dp
