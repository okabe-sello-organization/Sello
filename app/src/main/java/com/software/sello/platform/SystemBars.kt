package com.software.sello.platform

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Keeps the status- and navigation-bar icons readable on Sello's [dark] or light theme,
 * which may differ from the phone's. Both bars stay transparent; for 3-button navigation
 * the system draws its own contrast scrim, light or dark to match the icons.
 */
@Composable
fun SystemBarsFollowTheme(dark: Boolean) {
    val activity = LocalContext.current as? ComponentActivity ?: return
    DisposableEffect(activity, dark) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}
