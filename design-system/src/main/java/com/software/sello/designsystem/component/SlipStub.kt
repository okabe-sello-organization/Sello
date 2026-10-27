package com.software.sello.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * The tear-off stub under a slip's [TearLine]: two halves side by side, split by a dashed
 * rule, each a [StubButton] to where the slip's figures come from. Both halves get the
 * same width and the taller one sets the height.
 */
@Composable
fun SlipStub(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val rule = SelloTheme.colors.rule
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.weight(1f)) { start() }
        Canvas(Modifier.width(2.dp).fillMaxHeight().padding(vertical = 6.dp)) {
            drawLine(
                rule,
                Offset(size.width / 2, 0f),
                Offset(size.width / 2, size.height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
            )
        }
        Box(Modifier.weight(1f)) { end() }
    }
}

/**
 * One half of a [SlipStub]: an icon, a short [title] in the brand ink and, when known,
 * one [fact] under it, centred in the half. One button, said as [label] then the fact;
 * [label] should contain the [title] so a spoken command finds it. [minHeight] may be
 * lowered for a compact stub, never below a 48dp target.
 */
@Composable
fun StubButton(
    icon: SelloIcon,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fact: String? = null,
    label: String = title,
    minHeight: Dp = 56.dp
) {
    val colors = SelloTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(SelloTheme.shapes.control)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = listOfNotNull(label, fact).joinToString(", ")
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag(STUB_CONTENT_TAG)
        ) {
            Icon(
                icon.painter(),
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.padding(end = 8.dp).size(24.dp)
            )
            Column(Modifier.clearAndSetSemantics {}) {
                Text(title, fontWeight = FontWeight.W800, color = colors.brand)
                if (fact != null) {
                    Text(fact, style = MaterialTheme.typography.labelSmall, color = colors.inkSoft)
                }
            }
        }
    }
}

/** The icon and words of a [StubButton], for tests that check where they sit. */
internal const val STUB_CONTENT_TAG = "stub.content"
