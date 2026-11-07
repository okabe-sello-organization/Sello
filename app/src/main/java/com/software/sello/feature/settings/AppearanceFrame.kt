package com.software.sello.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.platform.SystemBarsFollowTheme

/**
 * The app drawn in the stored appearance. Each time Sello is on screen, after a recreation
 * too, the phone's colour is read again, so Sistema follows a wallpaper changed meanwhile.
 * Only the theme's tokens change: [content], with every saved route, draft and view model,
 * stays as it is.
 */
@Composable
fun AppearanceFrame(appearance: AppearanceViewModel, content: @Composable () -> Unit) {
    val preference by appearance.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_START) { appearance.onShown() }
    val theme = preference.appearance.asSelloTheme(isSystemInDarkTheme(), preference.phone)
    SystemBarsFollowTheme(theme.dark) {
        SelloTheme(ink = theme.ink, darkTheme = theme.dark, content = content)
    }
}
