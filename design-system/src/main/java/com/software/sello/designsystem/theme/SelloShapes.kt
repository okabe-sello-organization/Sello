package com.software.sello.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Corner tokens. The slip's pinked bottom edge belongs to the slip component. */
@Immutable
data class SelloShapes(
    val control: CornerBasedShape = RoundedCornerShape(10.dp),
    val button: CornerBasedShape = RoundedCornerShape(13.dp),
    val bar: CornerBasedShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
    val stampSmall: CornerBasedShape = RoundedCornerShape(8.dp),
    val stampLarge: CornerBasedShape = RoundedCornerShape(10.dp),
    val stampHero: CornerBasedShape = RoundedCornerShape(14.dp),
    val sheet: CornerBasedShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
)

/** The 4dp grid and the fixed distances the reference names. */
@Immutable
data class SelloSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val minTouchTarget: Dp = 48.dp
)

internal val LocalSelloShapes = staticCompositionLocalOf { SelloShapes() }
internal val LocalSelloSpacing = staticCompositionLocalOf { SelloSpacing() }
