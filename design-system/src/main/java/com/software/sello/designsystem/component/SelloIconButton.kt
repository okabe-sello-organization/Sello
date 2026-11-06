package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloTheme

/**
 * An icon that acts: back, close, edit, settings. Its target is at least 48 dp square
 * whatever the icon's size. [contentDescription] is what a screen reader says; it is
 * required, because the icon is the only label. [tint] defaults to the content colour
 * the surrounding bar or slip sets; disabled, it fades to the disabled alpha.
 *
 * @param iconSize the drawn icon's size; null keeps the icon's own 24 dp.
 */
@Composable
fun SelloIconButton(
    icon: SelloIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    iconSize: Dp? = null
) {
    val ink = tint.takeOrElse { LocalContentColor.current }
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = ink,
            disabledContentColor = ink.copy(alpha = SelloTheme.alpha.disabled)
        )
    ) {
        Icon(
            icon.painter(),
            contentDescription,
            if (iconSize == null) Modifier else Modifier.size(iconSize)
        )
    }
}
