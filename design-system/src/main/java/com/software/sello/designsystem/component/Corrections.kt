package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A short statement about something that just happened, with at most one thing to do
 * about it, such as "Gasto borrado" and "Deshacer". It is announced when it appears.
 * [spoken] is what is read aloud, when that should say more than fits on the line.
 * While [busy] the action stays in view and cannot be pressed. An [icon] leads the
 * message in place of the confirmation check, such as the repeat icon on what Sello
 * recorded by itself.
 *
 * It holds no time: whoever shows it decides how long, and takes it away. With
 * [remaining] (1 to 0) it shows that time as a lighter band over the bar that shrinks
 * from the end, and a thin line along its foot.
 */
@Composable
fun SelloNotice(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    busy: Boolean = false,
    spoken: String = message,
    confirmation: Boolean = false,
    icon: SelloIcon? = null,
    remaining: Float? = null
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.md),
        modifier = modifier
            .widthIn(max = NoticeMaxWidth)
            .fillMaxWidth()
            .clip(SelloTheme.shapes.control)
            .background(colors.ink)
            .drawBehind {
                val left = remaining?.coerceIn(0f, 1f) ?: return@drawBehind
                val width = size.width * left
                drawRect(
                    colors.paper.copy(alpha = TIME_BAND_ALPHA),
                    size = Size(width, size.height)
                )
                val line = TIME_LINE.toPx()
                drawRect(
                    colors.brandSoft,
                    topLeft = Offset(0f, size.height - line),
                    size = Size(width, line)
                )
            }
            .padding(start = SelloTheme.spacing.lg, end = SelloTheme.spacing.xs)
            .heightIn(min = SelloTheme.spacing.minTouchTarget)
    ) {
        if (icon != null) {
            // Brand-soft on the ink bar, light or dark: the brand itself is too faint on
            // the dark scheme's light bar.
            Icon(icon.painter(), null, Modifier.size(22.dp), colors.brandSoft)
        } else if (confirmation) {
            Icon(
                SelloIcon.Check.painter(),
                null,
                Modifier.size(22.dp).testTag(SELLO_NOTICE_CHECK_TAG),
                colors.semantic.onGain.takeIf { colors.isDark } ?: colors.semantic.gain
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.paper,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = SelloTheme.spacing.sm)
                .semantics {
                    contentDescription = spoken
                    liveRegion = LiveRegionMode.Polite
                }
        )
        if (actionLabel != null && onAction != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(SelloTheme.shapes.control)
                    .clickable(enabled = !busy, role = Role.Button, onClick = onAction)
                    .heightIn(min = SelloTheme.spacing.minTouchTarget)
                    .padding(horizontal = SelloTheme.spacing.md)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),
                    color = colors.paper.copy(alpha = if (busy) BUSY_ALPHA else 1f)
                )
            }
        }
    }
}

private val NoticeMaxWidth = 560.dp

/** The confirmation check of a [SelloNotice]; decorative, so it says nothing. */
const val SELLO_NOTICE_CHECK_TAG = "sello-notice:check"

/** How much the time left lightens the bar, and the line along its foot. */
private const val TIME_BAND_ALPHA = 0.14f

private val TIME_LINE = 3.dp

private const val BUSY_ALPHA = 0.5f
