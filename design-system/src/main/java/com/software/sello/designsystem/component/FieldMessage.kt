package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/** How a [FieldMessage] reads: something to fix, a doubt to clear, or a neutral hint. */
enum class FieldTone {
    /** Something is wrong and must be fixed: loss ink, with the error icon. */
    Error,

    /** Nothing is known to be wrong, but it must be checked first, such as an unknown save. */
    Warning,

    /** A neutral hint in the softer ink, without an icon. */
    Hint
}

/**
 * What a control says about its own value: an error the user must fix, a warning to
 * check, or a neutral hint. Features decide the words from their typed result; every
 * Sello control shows them the same way. A control never corrects the value instead.
 */
@Immutable
data class FieldMessage(val text: String, val tone: FieldTone = FieldTone.Error) {
    /** Whether the value itself is wrong; only an error marks its control. */
    val isError: Boolean get() = tone == FieldTone.Error
}

/** The shared look of a [FieldMessage], as the v2 entry's save message. Announced when it appears. */
@Composable
fun FieldMessageText(message: FieldMessage, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    val color = when (message.tone) {
        FieldTone.Error -> colors.semantic.loss
        FieldTone.Warning -> colors.semantic.warn
        FieldTone.Hint -> colors.inkSoft
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }
    ) {
        if (message.tone != FieldTone.Hint) {
            Icon(
                painter = SelloIcon.Error.painter(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(message.text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}
