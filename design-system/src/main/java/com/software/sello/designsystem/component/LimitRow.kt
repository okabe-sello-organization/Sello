package com.software.sello.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * One category whose limit can be changed: its circle, [name] with a [caption] under it
 * ("gastaste 90.000 de 50.000"), the signed [amount] it has left (in loss ink when
 * [over]) and a chevron. Without a limit, [fraction] is null and [amount] is what it
 * spent. The row is one button, read as [spoken].
 */
@Composable
fun LimitRow(
    name: String,
    icon: SelloIcon,
    caption: String,
    amount: MoneyTextValue?,
    fraction: Float?,
    over: Boolean,
    spoken: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SelloTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                onClick(label = null) {
                    onClick()
                    true
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CategoryCircle(icon, fraction, size = 36.dp, over = over, animate = false)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        }
        amount?.let {
            MoneyText(
                it,
                color = if (over) colors.semantic.loss else colors.ink,
                fontWeight = FontWeight.W800
            )
        }
        Icon(SelloIcon.ChevronRight.painter(), null, Modifier.size(18.dp), colors.outline)
    }
}
