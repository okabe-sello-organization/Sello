package com.software.sello.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.selloColors

/**
 * The ink choice: one round swatch per ink in its own colour, named under it, with a
 * ring around the chosen one. Each swatch is a radio button.
 */
@Composable
internal fun InkSwatches(
    inks: List<Pair<SelloInk, String>>,
    selected: SelloInk,
    onSelect: (SelloInk) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = SelloTheme.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.lg),
        modifier = modifier.selectableGroup()
    ) {
        inks.forEach { (ink, label) ->
            val chosen = ink == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .selectable(
                        selected = chosen,
                        enabled = enabled,
                        role = Role.RadioButton
                    ) { onSelect(ink) }
                    .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)
                    .padding(SelloTheme.spacing.xs)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(SwatchRing)
                        .border(
                            width = 2.5.dp,
                            color = if (chosen) colors.ink else colors.paper,
                            shape = CircleShape
                        )
                ) {
                    // The ink as its light band shows it, the same in both themes.
                    Box(
                        Modifier
                            .size(SwatchSize)
                            .background(selloColors(ink, isDark = false).band, CircleShape)
                    )
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (chosen) FontWeight.W700 else FontWeight.W600
                    ),
                    color = if (chosen) colors.ink else colors.inkSoft,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private val SwatchSize = 44.dp

private val SwatchRing = 56.dp

private const val DISABLED_ROW_ALPHA = 0.38f
