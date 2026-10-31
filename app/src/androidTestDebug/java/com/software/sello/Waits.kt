package com.software.sello

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.view.Choreographer
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
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.software.sello.composition.SelloApplication
import com.software.sello.composition.StartupState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

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

/**
 * Back twice, as a person leaves a form that opened with the system keyboard up: the
 * first Back puts the keyboard away, the second leaves the form. Each Back is the real
 * system key. Closing the keyboard and pressing Back at once raced with the form still
 * bringing the keyboard up (CI saw it come back about 70 ms after closing), so Back
 * only closed it. Here each step waits for the keyboard to be really up, then really down.
 */
fun ComposeTestRule.pressBackPastTheKeyboard() {
    waitUntil(PATIENCE_MILLIS) { keyboardShown() }
    Espresso.pressBack()
    waitUntil(PATIENCE_MILLIS) { !keyboardShown() }
    waitForIdle()
    Espresso.pressBack()
}

/** Whether the resumed activity's window reports the system keyboard as shown. */
private fun keyboardShown(): Boolean {
    var shown = false
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val resumed = ActivityLifecycleMonitorRegistry.getInstance()
            .getActivitiesInStage(Stage.RESUMED)
            .single()
        shown = resumed.window.decorView.rootWindowInsets
            ?.isVisible(WindowInsets.Type.ime()) == true
    }
    return shown
}

/**
 * Whether the system still means to show the keyboard, asked of its input method service.
 * The window learns of a hide only once the keyboard app has acted on it, which a busy
 * device can take seconds to do, or never; the service learns of a direct hide at once.
 * A field left puts the keyboard away two frames on, so this lets the app draw three first.
 */
fun systemKeyboardRequested(): Boolean {
    repeat(3) {
        val drawn = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            Choreographer.getInstance().postFrameCallback { drawn.countDown() }
        }
        check(drawn.await(PATIENCE_MILLIS, TimeUnit.MILLISECONDS)) { "No frame was drawn" }
    }
    val dump = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand("dumpsys input_method")
        .let { ParcelFileDescriptor.AutoCloseInputStream(it) }
        .bufferedReader()
        .use { it.readText() }
    check("mInputShown=" in dump) { "dumpsys input_method no longer reports mInputShown" }
    return "mShowRequested=true" in dump || "mInputShown=true" in dump
}

/** Waits until the installed app's data has opened or failed; see [SelloTestRunner]. */
fun SelloApplication.awaitOpened(): StartupState = runBlocking {
    withTimeout(PATIENCE_MILLIS) { startup.state.first { it != StartupState.Opening } }
}

/** The installed app's own view models. A test run starts on data that opened. */
fun SelloApplication.readyViewModels(): ViewModelProvider.Factory =
    (awaitOpened() as StartupState.Ready).viewModels
