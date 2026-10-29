package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.SweepLook
import com.software.sello.SweepWindow
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.designsystem.component.SelloDestination
import com.software.sello.designsystem.component.SelloNavigation
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.expense.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.navigation.MetasDestination
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.corrections.ExpenseCorrections
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.money.MoneyFormatter
import com.software.sello.presentation.money.MoneyLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.sweepLooks
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Metas and a goal as they are drawn: the tab's own green dock only once its screen
 * exists, never a month or Anotar, and every figure whole on a narrow phone, a tablet
 * and an expanded window, at 1.0, 1.3 and 2.0 font scales, in all four themes.
 */
@RunWith(AndroidJUnit4::class)
class MetasScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 23)
    private val money = MoneyFormatter(object : MoneyLabels {
        override fun amount(currency: Currency, digits: String, isOne: Boolean) = "$digits pesos"
        override fun negative(amount: String) = "menos $amount"
        override fun positive(amount: String) = "más $amount"
    })

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

    /** Long names and the largest amounts a goal can hold, to test what fits. */
    private val board = GoalBoard(
        1,
        1,
        listOf(
            goal(1, "Viaje a la costa", 3_000_000, listOf(750_000, 960_000)),
            goal(
                2,
                "Un fondo para imprevistos y reparaciones",
                999_999_999_999,
                listOf(999_999_999_999 / 3)
            ),
            goal(3, "Vacunas de Luna", 400_000, listOf(400_000), LocalDate.of(2026, 9, 30))
        ),
        null
    )

    private val clock = object : FinancialClock {
        override val today =
            MutableStateFlow(FinancialDay(this@MetasScreenTest.today, ZoneId.of("America/Bogota")))
    }

    private class Reads(private val state: GoalBoardState) : GoalReads {
        override suspend fun board(): Outcome<GoalBoard, StorageFailure> =
            Outcome.Failure(StorageFailure.Unavailable("not used"))

        override fun observeBoard(): Flow<GoalBoardState> = flowOf(state)

        override suspend fun goal(id: GoalId): Outcome<GoalSummary?, StorageFailure> =
            Outcome.Failure(StorageFailure.Unavailable("not used"))
    }

    private val shell =
        ShellState(YearMonth.of(2026, 10), YearMonth.of(2026, 10), false, 2026, null)

    private fun destination(
        board: GoalBoard,
        onCreate: (() -> Unit)? = null,
        onContribute: (() -> Unit)? = null
    ) {
        val factory = viewModelFactory {
            initializer {
                // Commands and notices that do nothing: nothing here sends one.
                val ids = RecordIdSource { "00000000-0000-4000-a000-000000000001" }
                val commands = HandGoalCommands()
                MetasViewModel(
                    SavedStateHandle(),
                    Reads(GoalBoardState.Ready(board)),
                    commands,
                    clock,
                    money,
                    ids,
                    ExpenseCorrections(
                        HandExpenseCommands(),
                        HandIncomeCommands(),
                        commands,
                        HandInvestmentCommands(),
                        { Duration.ZERO },
                        ids,
                        CoroutineScope(Dispatchers.Main)
                    )
                ) { false }
            }
        }
        rule.showSello(Look(density = 1f)) {
            Box(Modifier.requiredSize(320.dp, 640.dp)) {
                MetasDestination(
                    state = shell,
                    viewModels = factory,
                    onShell = {},
                    tabs = SelloNavigation(
                        listOf(
                            SelloDestination("recibo", "Recibo", SelloIcon.ReceiptLong),
                            SelloDestination("resumen", "Resumen", SelloIcon.Insights),
                            SelloDestination("metas", "Metas", SelloIcon.Flag)
                        ),
                        "metas"
                    ) {},
                    onOpenGoal = {},
                    onCreate = onCreate,
                    onContribute = onContribute
                )
            }
        }
    }

    @Test
    fun onMetasTheDockReadsAportarAUnaMetaAndNeverAnotarOrAMonth() {
        var contributed = 0
        destination(board, onContribute = { contributed++ })

        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Aportar a una meta"))
        rule.onNodeWithContentDescription("Aportar a una meta").performClick()
        assertEquals(1, contributed)
        rule.onNodeWithContentDescription("Anotar un gasto").assertDoesNotExist()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        rule.onNode(hasText("Metas") and isHeading(), useUnmergedTree = true).assertExists()
    }

    @Test
    fun withGoalsNuevaMetaSitsBesideTheTitleTheTotalIsTheHeroAndThereIsNoGear() {
        var created = 0
        destination(board, onCreate = { created++ }, onContribute = {})

        // Settings is on Recibo only (owner, 2026-10-26).
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).assertDoesNotExist()
        rule.onNodeWithTag(METAS_TOTAL_TAG).assertExists()
        rule.onNodeWithTag(METAS_CREATE_TAG).assert(hasText("Nueva meta")).performClick()
        assertEquals(1, created)
        rule.onNodeWithContentDescription("Crear una meta").assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Aportar a una meta"))
    }

    @Test
    fun withNoGoalsOnlyTheDockCreatesOne() {
        var created = 0
        destination(GoalBoard(1, 0, emptyList(), null), onCreate = { created++ })

        rule.onNodeWithText("Ponle nombre a algo que quieras").assertExists()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un gasto").assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Crear una meta")).performClick()
        assertEquals(1, created)
        rule.onNodeWithTag(METAS_CREATE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(METAS_TOTAL_TAG).assertDoesNotExist()
    }

    @Test
    fun untilTheirScreensExistMetasOffersNoDockAndNoPlus() {
        destination(board)

        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assertDoesNotExist()
        rule.onNodeWithTag(METAS_CREATE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(METAS_TOTAL_TAG).assertExists()
    }

    @Test
    // A screen larger than every window below, so each window is laid out whole.
    @Config(qualifiers = "w900dp-h900dp-mdpi")
    fun metasAndTheDetailKeepEveryFigureWholeInEveryThemeSizeAndWindow() {
        val render = GoalsRenderModel(money)
        val metas = render.metas(GoalBoardState.Ready(board), today)
        val detail = render.detail(
            GoalBoardState.Ready(board),
            board.goals[1].history.goal.id.value,
            today
        )
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, 1f, SweepWindow(320, 640)))
        var showDetail by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, look.scale)) {
                SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                    Box(
                        Modifier.requiredSize(
                            look.window.width.dp,
                            look.window.height.dp
                        ).testTag("window")
                    ) {
                        if (showDetail) {
                            GoalDetailScreen(detail, {
                            }, dateLabels(), monthNames(), PaddingValues(16.dp))
                        } else {
                            MetasScreen(metas, {}, dateLabels(), PaddingValues(16.dp))
                        }
                    }
                }
            }
        }
        val windows = listOf(SweepWindow(320, 640), SweepWindow(600, 900), SweepWindow(840, 600))
        for (each in sweepLooks(windows)) {
            look = each
            showDetail = false
            rule.waitForIdle()
            val window = rule.onNodeWithTag("window").getUnclippedBoundsInRoot()
            fun whole(description: String, tag: String? = null) {
                if (tag != null) {
                    rule.onNodeWithTag(METAS_LIST_TAG).performScrollToNode(hasTestTag(tag))
                }
                val bounds = rule.onNodeWithContentDescription(description, useUnmergedTree = true)
                    .getUnclippedBoundsInRoot()
                assertTrue("$each: $description at $bounds", bounds.right <= window.right + 0.5.dp)
            }
            whole("1.710.000 pesos", "metas.goal.${board.goals[0].history.goal.id.value}")
            whole("333.333.333.333 pesos", "metas.goal.${board.goals[1].history.goal.id.value}")
            whole("Lograda", "metas.completed.${board.goals[2].history.goal.id.value}")

            showDetail = true
            rule.waitForIdle()
            val saved = rule.onNodeWithContentDescription(
                "333.333.333.333 pesos",
                useUnmergedTree = true
            )
                .getUnclippedBoundsInRoot()
            assertTrue("$each: detail amount at $saved", saved.right <= window.right + 0.5.dp)
            val next = rule.onNodeWithTag("goal.next").performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("$each: next line at $next", next.right <= window.right + 0.5.dp)
            // 999.999.999.999 × 50 % is 500.000.000.000 rounded up, less 333.333.333.333.
            rule.onNodeWithText(
                "Próximo sello: 50 %, faltan 166.666.666.667",
                useUnmergedTree = true
            )
                .assertExists()
            rule.onNodeWithTag("goal.stamps").performScrollTo().assertExists()
        }
    }

    private fun showDetail(summary: GoalSummary) {
        val ready = GoalBoardState.Ready(GoalBoard(1, 1, listOf(summary), null))
        val detail = GoalsRenderModel(money).detail(ready, summary.history.goal.id.value, today)
        rule.showSello(Look(density = 1f)) {
            GoalDetailScreen(detail, {}, dateLabels(), monthNames(), PaddingValues(16.dp))
        }
    }

    private fun onHero(description: String) = rule.onNode(
        hasContentDescription(description) and hasAnyAncestor(hasTestTag("goal.hero")),
        useUnmergedTree = true
    )

    @Test
    fun mt02AGoalPastSeventyFivePercentShowsItsPercentageAndThatStampEarned() {
        // 1.710.000 + 600.000 = 2.310.000 of 3.000.000: 77 %, past the 75 % place.
        showDetail(goal(1, "Viaje a la costa", 3_000_000, listOf(1_710_000, 600_000)))

        rule.onNodeWithText("77 %").assertExists()
        onHero("75 por ciento, logrado").assertExists()
    }

    @Test
    fun mt18ALowerTargetThatReachesSeventyFivePercentShowsItEarned() {
        // 1.710.000 of 2.000.000 is 85 %.
        showDetail(goal(1, "Viaje a la costa", 2_000_000, listOf(1_710_000)))

        rule.onNodeWithText("85 %").assertExists()
        onHero("75 por ciento, logrado").assertExists()
    }
}
