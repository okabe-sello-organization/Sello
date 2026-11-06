package com.software.sello.feature.goals

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Aportar varios looks like on the reference phone (412×891 dp), in Claro and Oscuro, kept as
 * committed images (ADR 0022): the approved mock's goal states.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalBulkScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun capture(name: String, state: GoalBulkState, dark: Boolean) {
        val look = Look(dark = dark)
        rule.showSello(look) { GoalBulkScreen(state) {} }
        rule.captureStill(
            "src/test/screenshots/goal-bulk-$name-${if (dark) "oscuro" else "claro"}.png"
        )
    }

    @Test
    fun openingClaro() = capture("opening", GoalBulkSamples.opening, dark = false)

    @Test
    fun openingOscuro() = capture("opening", GoalBulkSamples.opening, dark = true)

    @Test
    fun threeLinesClaro() = capture("three-lines", GoalBulkSamples.three, dark = false)

    @Test
    fun threeLinesOscuro() = capture("three-lines", GoalBulkSamples.three, dark = true)

    @Test
    fun missingTypeClaro() = capture("missing-type", GoalBulkSamples.missing, dark = false)

    @Test
    fun missingTypeOscuro() = capture("missing-type", GoalBulkSamples.missing, dark = true)

    @Test
    fun refusedClaro() = capture("refused", GoalBulkSamples.refused, dark = false)

    @Test
    fun refusedOscuro() = capture("refused", GoalBulkSamples.refused, dark = true)

    @Test
    fun savingClaro() = capture("saving", GoalBulkSamples.saving, dark = false)

    @Test
    fun savingOscuro() = capture("saving", GoalBulkSamples.saving, dark = true)

    @Test
    fun unknownClaro() = capture("unknown", GoalBulkSamples.unknown, dark = false)

    @Test
    fun unknownOscuro() = capture("unknown", GoalBulkSamples.unknown, dark = true)

    @Test
    fun thirtyLinesClaro() = capture("thirty-lines", GoalBulkSamples.thirty, dark = false)

    @Test
    fun thirtyLinesOscuro() = capture("thirty-lines", GoalBulkSamples.thirty, dark = true)

    @Test
    fun twoGoalsClaro() = capture("two-goals", GoalBulkSamples.twoGoals, dark = false)

    @Test
    fun twoGoalsOscuro() = capture("two-goals", GoalBulkSamples.twoGoals, dark = true)

    @Test
    fun missingGoalClaro() = capture("missing-goal", GoalBulkSamples.missingGoal, dark = false)

    @Test
    fun missingGoalOscuro() = capture("missing-goal", GoalBulkSamples.missingGoal, dark = true)

    private val loading =
        GoalBulkState(lines = emptyList(), total = null, today = GoalBulkSamples.today)

    @Test fun loadingClaro() = capture("loading", loading, dark = false)

    @Test fun loadingOscuro() = capture("loading", loading, dark = true)

    private val failed = loading.copy(loading = false, loadFailed = true)

    @Test fun failedClaro() = capture("failed", failed, dark = false)

    @Test fun failedOscuro() = capture("failed", failed, dark = true)

    /** A refresh failed: the goals are as last read, under the notice, and the lines stay. */
    private val stale = GoalBulkSamples.three.copy(stale = true)

    @Test fun staleClaro() = capture("stale", stale, dark = false)

    @Test fun staleOscuro() = capture("stale", stale, dark = true)

    /** No active goal to put a line in. */
    private val noGoals = GoalBulkSamples.opening.copy(goals = emptyList())

    @Test fun noGoalsClaro() = capture("no-goals", noGoals, dark = false)

    @Test fun noGoalsOscuro() = capture("no-goals", noGoals, dark = true)

    private val discard = GoalBulkSamples.three.copy(confirmDiscard = true)

    @Test fun discardClaro() = capture("discard", discard, dark = false)

    @Test fun discardOscuro() = capture("discard", discard, dark = true)

    /** A line just removed: the notice offers to bring it back. */
    private val removed = GoalBulkSamples.three.copy(removed = RemovedGoalLine(withdrawal = false))

    @Test fun removedClaro() = capture("removed", removed, dark = false)

    @Test fun removedOscuro() = capture("removed", removed, dark = true)
}
