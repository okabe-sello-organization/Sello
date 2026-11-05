package com.software.sello.designsystem.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.software.sello.designsystem.theme.SelloTheme

/**
 * A section heading of an entry or movement form, such as Tipo, Meta or Inversión:
 * uppercase, heavy and spaced, in the softer ink, as the v2 entry design draws it.
 */
@Composable
fun EntryHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.W800,
            letterSpacing = 0.05.em
        ),
        color = SelloTheme.colors.inkSoft,
        modifier = modifier.semantics { heading() }
    )
}
