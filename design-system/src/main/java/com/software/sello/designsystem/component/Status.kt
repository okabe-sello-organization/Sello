package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.R
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * What news a [StatusSlip] brings. The kind decides the slip's icon, ink, layout and
 * button; a caller only gives the words and the action.
 */
sealed interface StatusKind {
    /**
     * Nothing to show yet, such as no expenses this month. The only kind whose [icon] the
     * caller picks, to fit what is missing. Its title is required.
     */
    data class Empty(val icon: SelloIcon) : StatusKind

    /**
     * Content that could not be read, and nothing read before to show instead. It never
     * sits above figures: older figures that are still shown take [Older].
     */
    data object LoadFailed : StatusKind

    /**
     * Figures still shown are the last ones read: a refresh could not update them. It has
     * no title.
     */
    data object Older : StatusKind

    /** Something the person did, such as a save, an export or a backup, failed. */
    data object ActionFailed : StatusKind

    /**
     * The outcome of something the person did is not known yet. It offers to check and
     * never says it failed.
     */
    data object Unknown : StatusKind

    /** The thing shown no longer exists, such as a category deleted meanwhile. */
    data object Gone : StatusKind
}

/**
 * The one action of a [StatusSlip]. A null [label] takes the kind's own: Reintentar,
 * Comprobar for [StatusKind.Unknown], Volver for [StatusKind.Gone]; [StatusKind.Empty] has
 * none and needs one. While [busy] it shows a spinner and ignores presses, so it is
 * requested once.
 */
class StatusAction(val label: String? = null, val busy: Boolean = false, val onClick: () -> Unit)

/**
 * A slip that says what kind of news it brings: nothing yet, a failed read, older
 * figures, a failed action, an unknown outcome or something gone. [kind] decides the
 * icon, ink, layout and button; [title] and [message] are the words and [action] at
 * most one thing to do.
 *
 * [StatusKind.Empty], [StatusKind.LoadFailed] and [StatusKind.Gone] are centred under a
 * large decorative icon and stand in for content. [StatusKind.Older],
 * [StatusKind.ActionFailed] and [StatusKind.Unknown] are flat inline slips that sit with
 * the content they speak of, and are read aloud when they appear. A null [title] takes
 * the kind's own where it has one ("No se pudo cargar", "Ya no está"); Empty needs one
 * and Older has none.
 */
@Composable
fun StatusSlip(
    kind: StatusKind,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: StatusAction? = null
) {
    require(kind !is StatusKind.Empty || title != null) { "An empty slip needs a title" }
    require(kind != StatusKind.Older || title == null) { "An older-figures slip has no title" }
    require(kind !is StatusKind.Empty || action == null || action.label != null) {
        "An empty slip's action needs a label"
    }
    val look = kind.look()
    val shownTitle = title ?: look.defaultTitle?.let { stringResource(it) }
    val shownAction = action?.let {
        ShownAction(
            label = it.label ?: stringResource(checkNotNull(look.defaultLabel)),
            busy = it.busy,
            onClick = it.onClick
        )
    }
    when (look.layout) {
        StatusLayout.Centred -> CentredStatus(look, shownTitle, message, shownAction, modifier)
        StatusLayout.Inline -> InlineStatus(look, shownTitle, message, shownAction, modifier)
    }
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
    val defaultTitle: Int?,
    val defaultLabel: Int?
)

internal fun StatusKind.look(): StatusLook = when (this) {
    is StatusKind.Empty -> StatusLook(
        layout = StatusLayout.Centred,
        icon = icon,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = false,
        defaultTitle = null,
        defaultLabel = null
    )

    StatusKind.LoadFailed -> StatusLook(
        layout = StatusLayout.Centred,
        icon = SelloIcon.Error,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = R.string.sello_error_title,
        defaultLabel = R.string.sello_retry
    )

    StatusKind.Older -> StatusLook(
        layout = StatusLayout.Inline,
        icon = null,
        iconInk = StatusInk.Warn,
        messageInk = StatusInk.Warn,
        button = ButtonKind.Text,
        announced = true,
        defaultTitle = null,
        defaultLabel = R.string.sello_retry
    )

    StatusKind.ActionFailed -> StatusLook(
        layout = StatusLayout.Inline,
        icon = SelloIcon.Error,
        iconInk = StatusInk.Loss,
        messageInk = StatusInk.Loss,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = null,
        defaultLabel = R.string.sello_retry
    )

    StatusKind.Unknown -> StatusLook(
        layout = StatusLayout.Inline,
        icon = SelloIcon.Info,
        iconInk = StatusInk.Warn,
        messageInk = StatusInk.Warn,
        button = ButtonKind.Outlined,
        announced = true,
        defaultTitle = null,
        defaultLabel = R.string.sello_check
    )

    StatusKind.Gone -> StatusLook(
        layout = StatusLayout.Centred,
        icon = SelloIcon.Block,
        iconInk = StatusInk.Brand,
        messageInk = StatusInk.InkSoft,
        button = ButtonKind.Outlined,
        announced = false,
        defaultTitle = R.string.sello_status_gone_title,
        defaultLabel = R.string.sello_back
    )
}

@Composable
private fun StatusInk.color(): Color = when (this) {
    StatusInk.Brand -> SelloTheme.colors.brand
    StatusInk.InkSoft -> SelloTheme.colors.inkSoft
    StatusInk.Warn -> SelloTheme.colors.semantic.warn
    StatusInk.Loss -> SelloTheme.colors.semantic.loss
}

private class ShownAction(val label: String, val busy: Boolean, val onClick: () -> Unit)

private fun Modifier.announcedIf(announced: Boolean): Modifier =
    if (announced) semantics { liveRegion = LiveRegionMode.Polite } else this

/** Empty, LoadFailed and Gone: centred under a large decorative icon. */
@Composable
private fun CentredStatus(
    look: StatusLook,
    title: String?,
    message: String,
    action: ShownAction?,
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
    message: String,
    action: ShownAction?,
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
private fun StatusButton(action: ShownAction, kind: ButtonKind, modifier: Modifier = Modifier) {
    SelloButton(
        text = action.label,
        onClick = action.onClick,
        modifier = modifier,
        kind = kind,
        loading = action.busy
    )
}

private val INLINE_ICON = 24.dp
