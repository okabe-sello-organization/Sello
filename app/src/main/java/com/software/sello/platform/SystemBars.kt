package com.software.sello.platform

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.software.sello.designsystem.theme.LocalStatusBarInks
import com.software.sello.designsystem.theme.StatusBarInks

/**
 * The one owner of the window's bar style. The status-bar icons read on what the shown
 * screen says it draws under them (see `StatusBarInk`): white on the band, the theme's
 * ink on the desk, and the theme's when no screen has said. The navigation bar sits on
 * the theme's paper or desk, so its icons follow Sello's [dark] or light theme, which
 * may differ from the phone's. Both bars stay transparent; for 3-button navigation the
 * system draws its own contrast scrim, light or dark to match the icons.
 */
@Composable
fun SystemBarsFollowTheme(dark: Boolean, content: @Composable () -> Unit) {
    val inks = remember { StatusBarInks() }
    CompositionLocalProvider(LocalStatusBarInks provides inks, content = content)
    val activity = LocalContext.current as? ComponentActivity ?: return
    val lightStatusIcons = inks.lightIcons ?: dark
    DisposableEffect(activity, dark, lightStatusIcons) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                lightStatusIcons
            },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                dark
            }
        )
        onDispose {}
    }
}
