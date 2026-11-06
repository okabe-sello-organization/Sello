package com.software.sello.designsystem.theme

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * What each screen says about the status bar: the ink that reads on what it draws under
 * it, the colour of the bar it sits in. The window's owner
 * turns the latest word into icon colour. The screen that spoke last wins, so one
 * entering over another takes the bar; when it leaves, the one under it has it again.
 */
@Stable
class StatusBarInks {
    private val claims = mutableStateListOf<Claim>()

    /**
     * Called as soon as what is under the status bar may have changed, in the same frame:
     * a screen inside a layout that measures first says so only while being laid out.
     */
    var onChange: () -> Unit = {}

    /** Whether the status bar should draw light icons, or null when no screen has said. */
    val lightIcons: Boolean? get() = claims.lastOrNull()?.ink?.let { it.luminance() > 0.5f }

    internal fun add(claim: Claim) {
        claims.add(claim)
        onChange()
    }

    internal fun remove(claim: Claim) {
        claims.remove(claim)
        onChange()
    }

    internal fun changed() = onChange()

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
    SideEffect {
        if (claim.ink != ink) {
            claim.ink = ink
            inks.changed()
        }
    }
    DisposableEffect(inks, claim) {
        inks.add(claim)
        onDispose { inks.remove(claim) }
    }
}

/**
 * Whether a window opened over the shown screen, such as a sheet or a full-screen
 * dialog, should draw light status icons: its scrim only dims what the screen says is
 * under the status bar. Without a word from a screen, the theme decides.
 */
@Composable
fun statusBarLightIconsUnder(): Boolean =
    LocalStatusBarInks.current?.lightIcons ?: SelloTheme.colors.isDark

/**
 * Asks this dialog's own window for dark (or light) status- and navigation-bar icons.
 * Call it inside a full-screen `Dialog`'s content; a floating dialog leaves the bars to
 * the window under it. Android 11 drops an appearance set before the window is attached
 * and also needs the older light-bar flags on the decor view.
 */
@Composable
fun DialogBarIcons(darkStatusIcons: Boolean, darkNavigationIcons: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window, darkStatusIcons, darkNavigationIcons) {
        val apply = { setDarkBarIcons(window, darkStatusIcons, darkNavigationIcons) }
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = apply()

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        if (view.isAttachedToWindow) apply() else view.addOnAttachStateChangeListener(listener)
        onDispose { view.removeOnAttachStateChangeListener(listener) }
    }
}

@Suppress("DEPRECATION")
private fun setDarkBarIcons(window: Window, darkStatus: Boolean, darkNavigation: Boolean) {
    val asked = (if (darkStatus) APPEARANCE_LIGHT_STATUS_BARS else 0) or
        (if (darkNavigation) APPEARANCE_LIGHT_NAVIGATION_BARS else 0)
    window.insetsController?.setSystemBarsAppearance(
        asked,
        APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
    )
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
        val decor = window.decorView
        var flags = decor.systemUiVisibility
        flags = if (darkStatus) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        }
        flags = if (darkNavigation) {
            flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            flags and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
        }
        decor.systemUiVisibility = flags
    }
}
