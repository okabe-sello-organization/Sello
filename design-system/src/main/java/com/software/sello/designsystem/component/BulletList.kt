package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.software.sello.designsystem.theme.SelloTheme

/** One point: an optional bold [lead] that carries the point, then its explanation. */
@Immutable
data class Bullet(val lead: String?, val body: String)

/**
 * Points a person should read before acting, such as what a backup file exposes or
 * what a replacement will remove. Each reads aloud as one sentence, without the dot.
 */
@Composable
fun BulletList(bullets: List<Bullet>, modifier: Modifier = Modifier) {
    val colors = SelloTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
        modifier = modifier
    ) {
        bullets.forEach { bullet ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(SelloTheme.spacing.sm),
                modifier = Modifier.semantics(mergeDescendants = true) {}
            ) {
                Text(
                    "•",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft,
                    modifier = Modifier.clearAndSetSemantics {}
                )
                Text(
                    text = buildAnnotatedString {
                        if (bullet.lead != null) {
                            withStyle(
                                SpanStyle(
                                    color = colors.ink,
                                    fontWeight = SelloTheme.type.weight.strong
                                )
                            ) {
                                append(bullet.lead)
                            }
                            append(" ")
                        }
                        append(bullet.body)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.inkSoft
                )
            }
        }
    }
}
