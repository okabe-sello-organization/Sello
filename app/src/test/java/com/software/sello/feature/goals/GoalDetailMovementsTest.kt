package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.MoreMenu
import com.software.sello.designsystem.component.MoreMenuItem
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
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
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.testing.pesos
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * A goal's movements as v2 draws them (owner, 2026-11-02): the amount at the end with the
 * balance under it, a note as the title, and month lines in the full list only. MT40.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalDetailMovementsTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)
    private val id = "00000000-0000-4000-9000-000000000001"

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun movementId(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    /**
     * MT40: aportes 200.000, 250.000, 260.000, 300.000, 350.000 and 350.000 on the 2nd of
     * May to October, then a retiro of 400.000 on 20 oct for the car: 1.310.000 saved.
     */
    private fun summary(
        lastYear: Boolean = false,
        target: Long = 3_000_000,
        completedOn: LocalDate? = null
    ): GoalSummary {
        val aportes = listOf(200_000L, 250_000L, 260_000L, 300_000L, 350_000L, 350_000L)
        val movements = aportes.mapIndexed { i, pesos ->
            movement(i + 1, GoalMovementKind.Contribution, pesos, LocalDate.of(2026, 5 + i, 2))
        } + movement(
            7,
            GoalMovementKind.Withdrawal,
            400_000,
            LocalDate.of(2026, 10, 20),
            "Arreglo del carro"
        ) + listOfNotNull(
            movement(8, GoalMovementKind.Contribution, 100_000, LocalDate.of(2025, 12, 15))
                .takeIf { lastYear }
        )
        val history = GoalHistory(
            Goal(
                GoalId.of(id).value(),
                GoalName.of("Viaje a la costa").value(),
                IconKey.of("flight").value(),
                null,
                completedOn,
                8,
                Instant.EPOCH,
                Instant.EPOCH
            ),
            listOf<GoalEvent>(GoalTargetChange(pesos(target), 0, LocalDate.of(2026, 5, 1))) +
                movements
        )
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    private fun movement(
        n: Int,
        kind: GoalMovementKind,
        pesos: Long,
        date: LocalDate,
        note: String? = null
    ) = GoalMovement(
        GoalMovementId.of(movementId(n)).value(),
        kind,
        pesos(pesos),
        date,
        n.toLong(),
        date,
        Note.of(note).value(),
        1,
        Instant.EPOCH,
        Instant.EPOCH
    )

    private fun show(all: Boolean = false, look: Look = Look(), lastYear: Boolean = false) {
        val ready = ReadState.Ready(
            GoalBoard(1, 1, listOf(summary(lastYear)), emptySet(), emptyList())
        )
        val detail = GoalsRenderModel(testMoney()).detail(ready, id, today)
            as GoalDetailState.Ready
        rule.showSello(look) {
            GoalDetailScreen(
                detail.copy(allMovements = all),
                {},
                dateLabels(),
                monthNames(),
                PaddingValues(16.dp),
                onEditMovement = {},
                onBulk = {}
            )
        }
    }

    private fun row(n: Int) = hasTestTag("goal.movement.${movementId(n)}")

    @Test
    fun aNoteNamesTheRetiroAndItsKindJoinsTheDay() {
        show()

        rule.onNode(
            row(7) and hasText("Arreglo del carro") and hasText("Retiro · mar 20 oct")
        ).assertExists()
        rule.onNode(row(6) and hasText("Aporte") and hasText("vie 2 oct")).assertExists()
    }

    @Test
    fun eachRowSaysItsAmountAndTheBalanceAfterIt() {
        show()

        rule.onNode(
            row(7) and hasContentDescription("menos 400.000 pesos") and hasText("saldo ") and
                hasContentDescription("1.310.000 pesos")
        ).assertExists()
        rule.onNode(
            row(6) and hasContentDescription("más 350.000 pesos") and
                hasContentDescription("1.710.000 pesos")
        ).assertExists()
        rule.onNode(row(4) and hasContentDescription("1.010.000 pesos")).assertExists()
    }

    @Test
    fun theShortListHasNoMonthLines() {
        show()

        rule.onNodeWithTag("goal.movements.month.2026-10").assertDoesNotExist()
    }

    @Test
    fun theFullListHasOneLineAMonthWithItsNet() {
        show(all = true)

        rule.onNode(
            hasTestTag("goal.movements.month.2026-10") and hasText("OCTUBRE") and
                hasContentDescription("menos 50.000 pesos")
        ).assertExists()
        rule.onNode(
            hasTestTag("goal.movements.month.2026-05") and hasText("MAYO") and
                hasContentDescription("más 200.000 pesos")
        ).assertExists()
        rule.onNode(row(1) and hasContentDescription("200.000 pesos")).assertExists()
    }

    @Test
    fun aMonthOfAnotherYearSaysItsYear() {
        show(all = true, lastYear = true)

        rule.onNode(
            hasTestTag("goal.movements.month.2025-12") and hasText("DICIEMBRE 2025") and
                hasContentDescription("más 100.000 pesos")
        ).assertExists()
        rule.onNode(hasTestTag("goal.movements.month.2026-10") and hasText("OCTUBRE"))
            .assertExists()
    }

    @Test
    fun theListClaro() = still(dark = false, all = false)

    @Test
    fun theListOscuro() = still(dark = true, all = false)

    @Test
    fun theFullListClaro() = still(dark = false, all = true)

    @Test
    fun theFullListOscuro() = still(dark = true, all = true)

    private fun still(dark: Boolean, all: Boolean) {
        show(all, Look(dark = dark))
        rule.onNodeWithTag(if (all) "goal.movement.${movementId(1)}" else GOAL_BULK_OPEN_TAG)
            .performScrollTo()
        rule.captureStill(
            "src/test/screenshots/goal-detail-movements${if (all) "-all" else ""}-" +
                "${if (dark) "oscuro" else "claro"}.png"
        )
    }

    private fun ready(
        target: Long = 3_000_000,
        completedOn: LocalDate? = null
    ): GoalDetailState.Ready {
        val board = ReadState.Ready(
            GoalBoard(
                1,
                1,
                listOf(summary(target = target, completedOn = completedOn)),
                emptySet(),
                emptyList()
            )
        )
        return GoalsRenderModel(testMoney()).detail(board, id, today) as GoalDetailState.Ready
    }

    /**
     * The whole screen as GoalDestinations frames it: the goal's name over the band with
     * the star pill, the pencil (or ⋮ once completed) and Aportar's dock.
     */
    private fun framed(name: String, detail: GoalDetailState, dark: Boolean) {
        val shown = detail as? GoalDetailState.Ready
        val active = shown != null && shown.goal.completedOn == null
        rule.showSello(Look(dark = dark)) {
            Box(Modifier.fillMaxSize()) {
                ShellScreen(
                    state = ShellState(
                        currentMonth = YearMonth.of(2026, 10),
                        selectedMonth = YearMonth.of(2026, 10),
                        monthPickerOpen = false,
                        pickerYear = 2026,
                        pendingEntry = null
                    ),
                    names = monthNames(),
                    onAction = {},
                    onBack = {},
                    titleOnly = shown?.goal?.name ?: stringResource(R.string.goal_title_fallback),
                    band = if (active) {
                        { GoalPin(shown!!, {}) }
                    } else {
                        null
                    },
                    actions = {
                        if (active) {
                            IconButton(onClick = {}) {
                                Icon(
                                    SelloIcon.EditNote.painter(),
                                    stringResource(R.string.goal_edit)
                                )
                            }
                        }
                        if (shown != null && !active) {
                            MoreMenu(
                                listOf(
                                    MoreMenuItem(
                                        stringResource(R.string.goal_delete_action),
                                        SelloIcon.Delete,
                                        {},
                                        loss = true
                                    )
                                )
                            )
                        }
                    },
                    entryDock = false,
                    dock = if (active) {
                        val label = stringResource(R.string.goal_dock_contribute)
                        SelloDock(label, label, {}, tone = ButtonTone.Gain, icon = SelloIcon.Flag)
                    } else {
                        null
                    }
                ) { padding ->
                    GoalDetailScreen(
                        state = detail,
                        onAction = {},
                        dates = dateLabels(),
                        months = monthNames(),
                        padding = padding,
                        onWithdraw = {},
                        onEditMovement = { _: String -> }.takeIf { active },
                        onBulk = {}.takeIf { active }
                    )
                }
            }
            LogradaMoment(shown?.completion ?: CompletionState.Idle, {})
        }
        rule.captureStill(
            "src/test/screenshots/goal-detail-$name-${if (dark) "oscuro" else "claro"}.png"
        )
    }

    @Test fun framedClaro() = framed("framed", ready(), dark = false)

    @Test fun framedOscuro() = framed("framed", ready(), dark = true)

    @Test fun loadingClaro() = framed("loading", GoalDetailState.Loading, dark = false)

    @Test fun loadingOscuro() = framed("loading", GoalDetailState.Loading, dark = true)

    @Test fun failedClaro() = framed("failed", GoalDetailState.Failed, dark = false)

    @Test fun failedOscuro() = framed("failed", GoalDetailState.Failed, dark = true)

    @Test fun missingClaro() = framed("missing", GoalDetailState.Missing, dark = false)

    @Test fun missingOscuro() = framed("missing", GoalDetailState.Missing, dark = true)

    @Test fun staleClaro() = framed("stale", ready().copy(stale = true), dark = false)

    @Test fun staleOscuro() = framed("stale", ready().copy(stale = true), dark = true)

    @Test fun busyClaro() = framed("busy", ready().copy(busy = true), dark = false)

    @Test fun busyOscuro() = framed("busy", ready().copy(busy = true), dark = true)

    /** A star's save not known: asked about again before anything else is sent. */
    private fun unknown() = ready().copy(error = GoalDetailError.Unknown)

    @Test fun unknownClaro() = framed("unknown", unknown(), dark = false)

    @Test fun unknownOscuro() = framed("unknown", unknown(), dark = true)

    private fun notSaved() = ready().copy(error = GoalDetailError.NotSaved)

    @Test fun notSavedClaro() = framed("not-saved", notSaved(), dark = false)

    @Test fun notSavedOscuro() = framed("not-saved", notSaved(), dark = true)

    /** 1.310.000 saved of a 1.000.000 target: reached, Marcar como lograda offered. */
    @Test fun reachedClaro() = framed("reached", ready(target = 1_000_000), dark = false)

    @Test fun reachedOscuro() = framed("reached", ready(target = 1_000_000), dark = true)

    private fun completed() = ready(target = 1_000_000, completedOn = LocalDate.of(2026, 10, 21))

    @Test fun completedClaro() = framed("completed", completed(), dark = false)

    @Test fun completedOscuro() = framed("completed", completed(), dark = true)

    private fun confirmDelete() = completed().copy(confirmingDelete = true)

    @Test fun deleteClaro() = framed("delete", confirmDelete(), dark = false)

    @Test fun deleteOscuro() = framed("delete", confirmDelete(), dark = true)

    /** The Lograda moment, over the screen, after Marcar como lograda. */
    private fun celebrating() = ready(target = 1_000_000).copy(
        completion = CompletionState.Celebrating(
            LogradaPlate(
                "Viaje a la costa",
                "flight",
                MoneyTextValue(MoneySign.None, "", "1.310.000", "1.310.000 pesos"),
                LocalDate.of(2026, 10, 22),
                6
            )
        )
    )

    @Test fun logradaClaro() = framed("lograda", celebrating(), dark = false)

    @Test fun logradaOscuro() = framed("lograda", celebrating(), dark = true)
}
