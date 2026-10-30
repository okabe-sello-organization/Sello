package com.software.sello.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A tinted note where Sello suggests something: the [evidence] it rests on, the [advice]
 * and one [actionLabel] that opens the place to act. Nothing is changed by showing it.
 * [evidenceSpoken] is how the evidence is read aloud when it holds an amount, which is said in
 * pesos. It can be sent away only when [onDismiss] is given, with [dismissLabel] as its spoken
 * name.
 */
@Composable
fun SuggestionNote(
    title: String,
    evidence: String,
    advice: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    evidenceSpoken: String? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null
) {
    val colors = SelloTheme.colors
    Column(
        modifier.fillMaxWidth().background(
            colors.brandSoft,
            SelloTheme.shapes.control
        ).padding(SelloTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                SelloIcon.Insights.painter(),
                null,
                Modifier.padding(end = SelloTheme.spacing.sm),
                colors.onBrandSoft
            )
            Text(
                title,
                Modifier.weight(1f),
                color = colors.onBrandSoft,
                fontWeight = FontWeight.W800
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(SelloIcon.Close.painter(), dismissLabel, tint = colors.onBrandSoft)
                }
            }
        }
        Text(
            evidence,
            Modifier.semantics { evidenceSpoken?.let { contentDescription = it } },
            color = colors.onBrandSoft
        )
        Text(advice, color = colors.onBrandSoft, fontWeight = FontWeight.W800)
        SelloButton(actionLabel, onAction, kind = ButtonKind.Text)
    }
}
