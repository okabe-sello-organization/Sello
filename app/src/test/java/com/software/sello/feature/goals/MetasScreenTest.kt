package com.software.sello.feature.goals

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.test.performScrollToIndex
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
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.GoalBoard
import com.software.sello.domain.port.GoalBoardState
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.GoalReads
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReadState
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.feature.expense.HandExpenseCommands
import com.software.sello.feature.guide.GuidePage
import com.software.sello.feature.income.HandIncomeCommands
import com.software.sello.feature.investments.HandInvestmentCommands
import com.software.sello.navigation.HandCategoryReads
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.navigation.MetasDestination
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.navigation.ShellState
import com.software.sello.presentation.corrections.Corrections
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import com.software.sello.sweepLooks
import com.software.sello.testing.testMoney
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
 * and an expanded window, in all four themes.
 */
@RunWith(AndroidJUnit4::class)
class MetasScreenTest {
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
        emptySet(),
        emptyList()
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
        onContribute: (() -> Unit)? = null,
        onOpenGuide: (GuidePage) -> Unit = {}
    ) {
        val factory = viewModelFactory {
            initializer {
                // Commands and notices that do nothing: nothing here sends one.
                val ids = RecordIdSource { "00000000-0000-4000-a000-000000000001" }
                val commands = HandGoalCommands()
                MetasViewModel(
                    SavedStateHandle(),
                    Reads(ReadState.Ready(board)),
                    commands,
                    clock,
                    money,
                    ids,
                    Corrections(
                        HandExpenseCommands(),
                        HandIncomeCommands(),
                        commands,
                        HandInvestmentCommands(),
                        { Duration.ZERO },
                        ids,
                        CoroutineScope(Dispatchers.Main),
                        HandCategoryReads(),
                        money
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
                    onContribute = onContribute,
                    onOpenGuide = onOpenGuide
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
        destination(GoalBoard(1, 0, emptyList(), emptySet(), emptyList()), onCreate = { created++ })

        rule.onNodeWithText("Ponle nombre a algo que quieras").assertExists()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Anotar un gasto").assertDoesNotExist()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assert(hasText("Crear una meta")).performClick()
        assertEquals(1, created)
        rule.onNodeWithTag(METAS_CREATE_TAG).assertDoesNotExist()
        rule.onNodeWithTag(METAS_TOTAL_TAG).assertDoesNotExist()
    }

    @Test
    fun metasOffersNoAnotarVariosBetweenTheActiveAndCompletedGoals() {
        destination(board)

        val completed = "metas.completed.${board.goals[2].history.goal.id.value}"
        rule.onNodeWithTag(METAS_LIST_TAG).performScrollToNode(hasTestTag(completed))
        rule.onNodeWithTag(completed).assertExists()
        rule.onNodeWithText("Anotar varios").assertDoesNotExist()
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
    fun metasAndTheDetailKeepEveryFigureWholeInEveryThemeAndWindow() {
        val render = GoalsRenderModel(money)
        val metas = render.metas(ReadState.Ready(board), today)
        val detail = render.detail(
            ReadState.Ready(board),
            board.goals[1].history.goal.id.value,
            today
        )
        var look by mutableStateOf(SweepLook(SelloInk.Cobalto, false, SweepWindow(320, 640)))
        var showDetail by mutableStateOf(false)
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
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
            val saved = onHero("333.333.333.333 pesos").getUnclippedBoundsInRoot()
            assertTrue("$each: detail amount at $saved", saved.right <= window.right + 0.5.dp)
            // The one aporte's balance is the same figure, under its amount in the list.
            val balance = rule.onNode(
                hasContentDescription("333.333.333.333 pesos") and
                    hasAnyAncestor(hasTestTag("goal.movements")),
                useUnmergedTree = true
            ).performScrollTo().getUnclippedBoundsInRoot()
            assertTrue("$each: balance at $balance", balance.right <= window.right + 0.5.dp)
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
        val ready = ReadState.Ready(GoalBoard(1, 1, listOf(summary), emptySet(), emptyList()))
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

    /** Celular nuevo at its target (450.000 + 750.000 = 1.200.000) with [completion] on Metas. */
    private fun showReached(completion: CompletionState, actions: MutableList<MetasAction>) {
        val reached = goal(2, "Celular nuevo", 1_200_000, listOf(450_000, 750_000))
        val ready = ReadState.Ready(GoalBoard(1, 1, listOf(reached), emptySet(), emptyList()))
        val metas = (GoalsRenderModel(money).metas(ready, today) as MetasState.Ready)
            .copy(completion = completion)
        rule.showSello(Look(density = 1f)) {
            MetasScreen(metas, { actions += it }, dateLabels(), PaddingValues(16.dp))
        }
    }

    private val celular = "00000000-0000-4000-9000-000000000002"

    @Test
    fun aRefusedMarcarComoLogradaIsSaidAndReintentarClearsIt() {
        val actions = mutableListOf<MetasAction>()
        showReached(CompletionState.Failed(GoalDetailError.NotSaved), actions)

        rule.onNodeWithText("No se guardó").assertExists()
        rule.onNodeWithText("No se guardó el cambio. Puedes intentarlo de nuevo.").assertExists()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(listOf<MetasAction>(MetasAction.Retry), actions)
        // Certain nothing was written: marking again is a new attempt.
        rule.onNodeWithTag("metas.markComplete.$celular").assertIsEnabled()
    }

    @Test
    fun anUnknownMarcarComoLogradaOffersTheCheckAndLocksTheButtonUntilThen() {
        val actions = mutableListOf<MetasAction>()
        showReached(CompletionState.Failed(GoalDetailError.Unknown), actions)

        rule.onNodeWithText("Falta comprobar si se guardó").assertExists()
        // The title is read aloud when it appears; the line is a FieldMessageText, read
        // aloud on its own.
        rule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite) and
                hasText("Falta comprobar si se guardó"),
            useUnmergedTree = true
        ).assertExists()
        rule.onNodeWithText("No sabemos si se guardó el cambio. Compruébalo antes de seguir.")
            .assertExists()
        rule.onNodeWithTag("metas.markComplete.$celular").assertIsNotEnabled()
        rule.onNodeWithText("Comprobar si se guardó").performClick()
        assertEquals(listOf<MetasAction>(MetasAction.Retry), actions)
    }

    /** Metas sending a completion, the reached card first; [completion] set by the test. */
    private fun showSending(): (CompletionState) -> Unit {
        val reached = goal(4, "Celular nuevo", 1_200_000, listOf(450_000, 750_000))
        val ready = ReadState.Ready(
            GoalBoard(1, 1, listOf(reached) + board.goals, emptySet(), emptyList())
        )
        val metas = GoalsRenderModel(money).metas(ready, today) as MetasState.Ready
        var completion by mutableStateOf<CompletionState>(CompletionState.Busy)
        rule.showSello(Look(density = 1f)) {
            Box(Modifier.requiredSize(412.dp, 891.dp)) {
                MetasScreen(
                    metas.copy(completion = completion),
                    {},
                    dateLabels(),
                    PaddingValues(16.dp)
                )
            }
        }
        return { completion = it }
    }

    @Test
    fun aFailureThatArrivesWhileMetasIsShownComesIntoView() {
        val complete = showSending()

        complete(CompletionState.Failed(GoalDetailError.Unknown))

        rule.onNodeWithTag(METAS_COMPLETION_PROBLEM_TAG).assertIsDisplayed()
    }

    @Test
    fun aFailureThatArrivesAfterAScrollIsBroughtIntoView() {
        val complete = showSending()
        // The reached card scrolled up to the top, the total out of view.
        rule.onNodeWithTag(METAS_LIST_TAG).performScrollToIndex(1)

        complete(CompletionState.Failed(GoalDetailError.Unknown))

        rule.onNodeWithTag(METAS_COMPLETION_PROBLEM_TAG).assertIsDisplayed()
    }

    @Test
    fun aSecondRefusalInTheSameFrameIsBroughtIntoViewAgain() {
        // Celular nuevo at its target; storage refuses both completions at once.
        val reached = goal(4, "Celular nuevo", 1_200_000, listOf(450_000, 750_000))
        val ids = RecordIdSource { "00000000-0000-4000-a000-000000000001" }
        val commands = HandGoalCommands()
        repeat(2) {
            commands.queue += CommandOutcome.Rejected(CommandRejection.VersionConflict(5))
        }
        val ready = ReadState.Ready(
            GoalBoard(1, 1, listOf(reached) + board.goals, emptySet(), emptyList())
        )
        val model = MetasViewModel(
            SavedStateHandle(),
            Reads(ready),
            commands,
            clock,
            money,
            ids,
            Corrections(
                HandExpenseCommands(),
                HandIncomeCommands(),
                commands,
                HandInvestmentCommands(),
                { Duration.ZERO },
                ids,
                CoroutineScope(Dispatchers.Main),
                HandCategoryReads(),
                money
            )
        ) { false }
        rule.showSello(Look(density = 1f)) {
            val state by model.state.collectAsState()
            Box(Modifier.requiredSize(412.dp, 891.dp)) {
                MetasScreen(state, model::onAction, dateLabels(), PaddingValues(16.dp))
            }
        }
        val button = "metas.markComplete.00000000-0000-4000-9000-000000000004"
        rule.onNodeWithTag(button).performClick()
        rule.onNodeWithTag(METAS_COMPLETION_PROBLEM_TAG).assertIsDisplayed()

        // Back down to the card and pressed again: refused again with no frame between.
        rule.onNodeWithTag(METAS_LIST_TAG).performScrollToIndex(2)
        rule.onNodeWithTag(button).performClick()
        rule.waitForIdle()

        assertEquals(2, commands.sent.size)
        rule.onNodeWithTag(METAS_COMPLETION_PROBLEM_TAG).assertIsDisplayed()
    }

    @Test
    fun guardadoEnMetasQuestionMarkOpensItsSheetAndVerMasOpensTheGuia() {
        val guides = mutableListOf<GuidePage>()
        destination(board, onOpenGuide = guides::add)

        rule.onNodeWithTag(METAS_SAVED_HELP_TAG)
            .assertContentDescriptionEquals("Qué es Guardado en metas")
            .performClick()

        rule.onNodeWithTag(METAS_HELP_TAG).assertExists()
        rule.onNodeWithText("Aparte de tu presupuesto", useUnmergedTree = true).assertExists()
        rule.onNode(hasText("Ver más en la Guía de Sello"), useUnmergedTree = true)
            .performScrollTo()
            .performClick()
        assertEquals(listOf(GuidePage.Goals), guides)
        rule.onNodeWithTag(METAS_HELP_TAG).assertDoesNotExist()
    }
}
