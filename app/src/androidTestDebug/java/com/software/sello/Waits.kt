package com.software.sello

import android.app.Activity
import android.view.WindowInsets
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import com.software.sello.composition.SelloApplication
import com.software.sello.composition.StartupState

/*
 * The app reads its database off the main thread, which the test framework does not
 * wait for. A test that looks at the screen right after starting the app, or after a
 * step that reads or writes, waits here for what it expects instead of assuming the
 * read has finished.
 */

private const val PATIENCE_MILLIS = 15_000L

fun ComposeTestRule.await(matcher: SemanticsMatcher) {
    waitUntil(PATIENCE_MILLIS) {
        onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

fun ComposeTestRule.awaitTag(tag: String) = await(hasTestTag(tag))

fun ComposeTestRule.awaitText(text: String) = await(hasText(text))

fun ComposeTestRule.awaitDescription(description: String) =
    await(hasContentDescription(description))

/**
 * Presses a control through its accessibility action instead of by screen position.
 * After typing, the system keyboard may still be sliding in and moving things; a
 * press aimed at where the control was a moment ago can miss.
 */
fun SemanticsNodeInteraction.press(): SemanticsNodeInteraction =
    performSemanticsAction(SemanticsActions.OnClick)

/**
 * Scrolls to this node when a scrolling container holds it, and leaves it be otherwise:
 * on a tall screen a form that fits has no scrolling container, and scrolling to one of
 * its controls would fail.
 */
fun SemanticsNodeInteraction.revealed(): SemanticsNodeInteraction {
    var parent = fetchSemanticsNode().parent
    while (parent != null) {
        if (SemanticsActions.ScrollBy in parent.config) return performScrollTo()
        parent = parent.parent
    }
    return this
}

/**
 * Puts the system keyboard away before a test ends. A keyboard left up by a test that
 * typed would otherwise still hold the window's focus when the next test starts.
 */
fun ActivityScenario<out Activity>.hideKeyboard() {
    onActivity { it.window.insetsController?.hide(WindowInsets.Type.ime()) }
}

/** The installed app's own view models. A test run starts on data that opened. */
fun SelloApplication.readyViewModels(): ViewModelProvider.Factory =
    (startup.state.value as StartupState.Ready).viewModels
