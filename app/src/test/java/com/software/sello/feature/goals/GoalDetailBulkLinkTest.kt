package com.software.sello.feature.goals

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Aportar varios at the foot of a goal's movements, beside Ver los N, as v2 varios draws it. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w412dp-h914dp-mdpi")
class GoalDetailBulkLinkTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val id = "00000000-0000-4000-9000-000000000001"

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    /** Viaje a la costa: six aportes of 285.000 = 1.710.000 of 3.000.000. */
    private fun summary(done: Boolean = false): GoalSummary {
        val movements = (1..6).map { n ->
            GoalMovement(
                GoalMovementId.of("00000000-0000-4000-8000-%012d".format(n)).value(),
                GoalMovementKind.Contribution,
                pesos(285_000),
                LocalDate.of(2026, 4 + n, 2),
                n.toLong(),
                LocalDate.of(2026, 4 + n, 2),
                null,
                1,
                Instant.EPOCH,
                Instant.EPOCH
            )
        }
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of("Viaje a la costa").value(),
                IconKey.of("flight").value(),
                null,
                LocalDate.of(2026, 10, 20).takeIf { done },
                5,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf<GoalEvent>(GoalTargetChange(pesos(3_000_000), 0, LocalDate.of(2026, 5, 1))) +
                movements
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun show(summary: GoalSummary, onBulk: () -> Unit, look: Look = Look()) {
        val ready = ReadState.Ready(GoalBoard(1, 1, listOf(summary), null))
        val detail = GoalsRenderModel(testMoney()).detail(ready, id, today)
        rule.showSello(look) {
            GoalDetailScreen(
                detail,
                {},
                dateLabels(),
                monthNames(),
                PaddingValues(16.dp),
                onBulk = onBulk
            )
        }
    }

    @Test
    fun anActiveGoalOffersAportarVariosBesideVerLos() {
        var opened = 0
        show(summary(), { opened++ })

        rule.onNodeWithTag(GOAL_BULK_OPEN_TAG).performScrollTo().performClick()
        rule.onNodeWithTag("goal.showAll").assertExists()

        assertEquals(1, opened)
    }

    @Test
    fun aCompletedGoalOffersNoReceipt() {
        show(summary(done = true), {})

        rule.onNodeWithTag(GOAL_BULK_OPEN_TAG).assertDoesNotExist()
    }

    @Test
    fun theFootClaro() = foot(dark = false)

    @Test
    fun theFootOscuro() = foot(dark = true)

    private fun foot(dark: Boolean) {
        show(summary(), {}, Look(dark = dark))
        rule.onNodeWithTag(GOAL_BULK_OPEN_TAG).performScrollTo()
        rule.captureStill(
            "src/test/screenshots/goal-detail-bulk-link-${if (dark) "oscuro" else "claro"}.png"
        )
    }
}
