package com.software.sello.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme

private val schemes = SelloInk.entries.flatMap { ink -> listOf(false, true).map { ink to it } }

/**
 * Shows [content] in a fixed-width stage and runs [check] once in each of the four
 * colour schemes. [reset] runs before each scheme after the first. [densityScale] shrinks
 * every dp so a stage wider than the test screen still fits on it.
 */
fun ComposeContentTestRule.inEveryScheme(
    reducedMotion: Boolean = true,
    fontScale: Float = 1f,
    width: Dp = 360.dp,
    densityScale: Float = 1f,
    reset: () -> Unit = {},
    content: @Composable () -> Unit,
    check: (String) -> Unit
) {
    var scheme by mutableStateOf(schemes.first())
    setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density * densityScale, fontScale)
        ) {
            SelloTheme(
                ink = scheme.first,
                darkTheme = scheme.second,
                reducedMotion = reducedMotion
            ) {
                Box(Modifier.width(width)) { content() }
            }
        }
    }
    schemes.forEachIndexed { index, next ->
        if (index > 0) {
            reset()
            scheme = next
        }
        waitForIdle()
        check("${next.first} dark=${next.second}")
    }
}
