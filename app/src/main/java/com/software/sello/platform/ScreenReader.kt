package com.software.sello.platform

import android.content.Context
import android.view.accessibility.AccessibilityManager

/** Whether a screen reader is reading the screen now, for offers that must last longer then. */
fun interface ScreenReader {
    fun active(): Boolean
}

/** TalkBack and other screen readers turn on touch exploration. */
class AndroidScreenReader(private val context: Context) : ScreenReader {
    override fun active(): Boolean =
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled ==
            true
}
