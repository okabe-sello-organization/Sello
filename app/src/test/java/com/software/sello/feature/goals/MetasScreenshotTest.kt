package com.software.sello.feature.goals

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.BandChip
import com.software.sello.designsystem.component.ButtonTone
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.component.SelloDock
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.StampNoticeBar
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.corrections.GoalCelebration
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.testMoney
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Metas looks like, kept as committed images (ADR 0022): Cobalto light, Cobalto
 * dark at twice the font size, and Violeta light. See ExpenseEntryScreenshotTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MetasScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 23)
    private val money = testMoney()

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun goal(
        number: Int,
        name: String,
        target: Long,
        aportes: List<Long>,
        completedOn: LocalDate? = null
    ): GoalSummary {
        val day = LocalDate.of(2026, 10, 2)
        val goal = Goal(
            GoalId.of("00000000-0000-4000-9000-%012d".format(number)).value(),
            GoalName.of(name).value(),
            IconKey.of("flight").value(),
            YearMonth.of(2026, 12).takeIf { completedOn == null },
            completedOn,
            1,
            Instant.ofEpochSecond(number.toLong()),
            Instant.ofEpochSecond(number.toLong())
        )
        val events =
            listOf(GoalTargetChange(TransactionAmount.of(Money.cop(target)).value(), 0, day)) +
                aportes.mapIndexed { index, pesos ->
                    GoalMovement(
                        GoalMovementId.of(
                            "00000000-0000-4000-8000-%04d%08d".format(number, index)
                        ).value(),
                        GoalMovementKind.Contribution,
                        TransactionAmount.of(Money.cop(pesos)).value(),
                        day,
                        index + 1L,
                        day,
                        null,
                        1,
                        Instant.EPOCH,
                        Instant.EPOCH
                    )
                }
        val history = GoalHistory(goal, events)
        return GoalSummary(history, GoalPolicy.progress(history).value())
    }

    /** One goal under way, one just started and one reached. */
    private val board = GoalBoard(
        1,
        1,
        listOf(
            goal(1, "Viaje a la costa", 3_000_000, listOf(750_000, 960_000)),
            goal(2, "Celular nuevo", 1_200_000, listOf(450_000)),
            goal(3, "Vacunas de Luna", 400_000, listOf(400_000), LocalDate.of(2026, 9, 30))
        ),
        emptySet(),
        emptyList()
    )
    private val metas = GoalsRenderModel(money).metas(ReadState.Ready(board), today)

    private fun capture(look: Look, name: String = "metas", onBulk: (() -> Unit)? = null) {
        rule.showSello(look) {
            MetasScreen(metas, {}, dateLabels(), PaddingValues(16.dp), onBulk = onBulk)
        }
        rule.captureStill("src/test/screenshots/$name-${look.label}.png")
    }

    /** Anotar varios at the foot of the active goals, before the completed one. */
    @Test
    fun anotarVariosLight() = capture(Look(), "metas-bulk", onBulk = {})

    @Test
    fun anotarVariosDark() = capture(Look(dark = true), "metas-bulk", onBulk = {})

    @Test
    fun violetaDark() = capture(Look(ink = SelloInk.Violeta, dark = true))

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    private fun shot(name: String, state: MetasState, look: Look, reveal: Boolean = false) {
        rule.showSello(look) {
            if (state == MetasState.Loading) {
                // As GoalDestinations.kt frames it before the list is read: no chip, no dock.
                ShellScreen(
                    octoberShell,
                    monthNames(),
                    {},
                    navigation = appTabs(ShellTab.Metas),
                    titleOnly = "Metas",
                    entryDock = false
                ) { padding -> MetasScreen(state, {}, dateLabels(), padding) }
            } else {
                MetasScreen(state, {}, dateLabels(), PaddingValues(16.dp))
            }
        }
        if (reveal) rule.onNodeWithText("Vacunas de Luna").revealed()
        rule.captureStill("src/test/screenshots/metas-$name-${look.label}.png")
    }

    @Test fun loadingLight() = shot("loading", MetasState.Loading, Look())

    @Test fun loadingDark() = shot("loading", MetasState.Loading, Look(dark = true))

    @Test fun failedLight() = shot("failed", MetasState.Failed, Look())

    @Test fun failedDark() = shot("failed", MetasState.Failed, Look(dark = true))

    private val empty = GoalsRenderModel(money)
        .metas(ReadState.Ready(board.copy(goals = emptyList())), today)

    @Test fun emptyLight() = shot("empty", empty, Look())

    @Test fun emptyDark() = shot("empty", empty, Look(dark = true))

    private val stale = (metas as MetasState.Ready).copy(stale = true)

    @Test fun staleLight() = shot("stale", stale, Look())

    @Test fun staleDark() = shot("stale", stale, Look(dark = true))

    /** Celular nuevo reached its target; Marcar como lograda is being saved. */
    private val busy = (
        GoalsRenderModel(money).metas(
            ReadState.Ready(
                board.copy(
                    goals = listOf(
                        goal(2, "Celular nuevo", 1_200_000, listOf(450_000, 750_000)),
                        goal(1, "Viaje a la costa", 3_000_000, listOf(750_000, 960_000))
                    )
                )
            ),
            today
        ) as MetasState.Ready
        ).copy(completion = CompletionState.Busy)

    @Test fun busyLight() = shot("busy", busy, Look())

    @Test fun busyDark() = shot("busy", busy, Look(dark = true))

    /** Marcar como lograda was refused: said above the goals, the button pressable again. */
    private val refused = busy.copy(completion = CompletionState.Failed(GoalDetailError.NotSaved))

    @Test fun refusedLight() = shot("refused", refused, Look())

    @Test fun refusedDark() = shot("refused", refused, Look(dark = true))

    /** Its outcome is not known: Comprobar si se guardó, and the button locked until then. */
    private val unknown = busy.copy(completion = CompletionState.Failed(GoalDetailError.Unknown))

    @Test fun unknownLight() = shot("unknown", unknown, Look())

    @Test fun unknownDark() = shot("unknown", unknown, Look(dark = true))

    /** The completed goals, below the active ones. */
    @Test fun completedLight() = shot("completed", metas, Look(), reveal = true)

    @Test fun completedDark() = shot("completed", metas, Look(dark = true), reveal = true)

    /**
     * An aporte made in Aportar earned Viaje a la costa's 50 % stamp: on Metas the ticket
     * takes the notice bar, its stamp already down. Framed as MetasDestination draws it.
     */
    private fun ticket(look: Look) {
        val earned = CorrectionNotice.GoalMovementRecorded(
            withdrawal = false,
            amount = MoneyTextValue(MoneySign.None, "", "350.000", "350.000 pesos"),
            celebration = GoalCelebration(
                goalId = "viaje",
                goalName = "Viaje a la costa",
                earned = listOf(GoalPlace.Half),
                relit = emptyList(),
                reachedMeta = false,
                missing = MoneyTextValue(MoneySign.None, "", "1.290.000", "1.290.000 pesos")
            )
        )
        rule.showSello(look) {
            ShellScreen(
                octoberShell,
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Metas),
                titleOnly = stringResource(R.string.metas_title),
                notice = { StampNoticeBar(earned, {}) { false } },
                titleChip = { compact ->
                    BandChip(stringResource(R.string.metas_create), {}, compact = compact)
                },
                entryDock = false,
                dock = SelloDock(
                    stringResource(R.string.metas_dock_contribute),
                    stringResource(R.string.metas_dock_contribute),
                    {},
                    tone = ButtonTone.Gain,
                    icon = SelloIcon.Flag
                )
            ) { padding -> MetasScreen(metas, {}, dateLabels(), padding) }
        }
        rule.captureStill("src/test/screenshots/metas-ticket-${look.label}.png")
    }

    @Test fun ticketLight() = ticket(Look())

    @Test fun ticketDark() = ticket(Look(dark = true))
}
