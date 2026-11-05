package com.software.sello.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * What each screen says about the status bar: the ink that reads on what it draws under
 * it, as v2's `.statusbar` takes its colour from the bar it sits in. The window's owner
 * turns the latest word into icon colour. The screen that spoke last wins, so one
 * entering over another takes the bar; when it leaves, the one under it has it again.
 */
@Stable
class StatusBarInks {
    private val claims = mutableStateListOf<Claim>()

    /** Whether the status bar should draw light icons, or null when no screen has said. */
    val lightIcons: Boolean? get() = claims.lastOrNull()?.ink?.let { it.luminance() > 0.5f }

    internal fun add(claim: Claim) = claims.add(claim)

    internal fun remove(claim: Claim) = claims.remove(claim)

    internal class Claim(ink: Color) {
        var ink by mutableStateOf(ink)
    }
}

/** Where screens find the window's [StatusBarInks]; none outside an app window. */
val LocalStatusBarInks = staticCompositionLocalOf<StatusBarInks?> { null }

/** Says that [ink] reads on what this screen draws under the status bar, while it is shown. */
@Composable
fun StatusBarInk(ink: Color) {
    val inks = LocalStatusBarInks.current ?: return
    val claim = remember { StatusBarInks.Claim(ink) }
    SideEffect { claim.ink = ink }
    DisposableEffect(inks, claim) {
        inks.add(claim)
        onDispose { inks.remove(claim) }
    }
}
