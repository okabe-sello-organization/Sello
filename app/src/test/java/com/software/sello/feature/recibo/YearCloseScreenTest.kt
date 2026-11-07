package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** The year card on Recibo and the big moment it opens (SELLO-200). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YearCloseScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun month(number: Int, mark: YearCloseMark = YearCloseMark.Within) =
        YearCloseMonth(YearMonth.of(2026, number), mark)

    /** The mock's 2026: March and July over, 1.240 expenses on 318 days, 2 goals reached. */
    private val ready = YearCloseState.Ready(
        year = 2026,
        months = (1..12).map {
            if (it == 3 ||
                it == 7
            ) {
                month(it, YearCloseMark.Over)
            } else {
                month(it)
            }
        },
        expenses = 1_240,
        recordedDays = 318,
        withinMonths = 10,
        limitedMonths = 12,
        goalsReached = 2
    )

    private val noLimitsNoGoals = ready.copy(
        months = (1..12).map { month(it, YearCloseMark.NoLimit) },
        withinMonths = 0,
        limitedMonths = 0,
        goalsReached = 0
    )

    private fun show(state: YearCloseState, reduced: Boolean = true, onDone: () -> Unit = {}) =
        show(state, reduced, onDone) {}

    private fun show(
        state: YearCloseState,
        reduced: Boolean,
        onDone: () -> Unit,
        onViewYear: (Int) -> Unit
    ) {
        rule.setContent {
            SelloTheme(reducedMotion = reduced) { YearCloseMoment(state, onDone, onViewYear) }
        }
    }

    @Test
    fun theCardSaysTheYearAndOpensOrPutsAwayIt() {
        val actions = mutableListOf<ReciboAction>()
        rule.showSello {
            ReciboScreen(
                ReciboState.Month(
                    summary = ReciboSummary(
                        ReciboAnswerLabel.Spent,
                        pesos("100"),
                        ReciboStamp.NoLimit
                    ),
                    categories = emptyList(),
                    stale = false,
                    invitation = ReciboInvitation(YearMonth.of(2026, 12), closesYear = true)
                ),
                { actions += it },
                PaddingValues(12.dp)
            )
        }
        rule.onNodeWithText("¡Cerraste el año!").assertDoesNotExist()
        rule.onNodeWithContentDescription("¡Cerraste el año! Mira cómo te fue en 2026")
            .performClick()
        rule.onNodeWithTag(RECIBO_YEAR_CARD_DISMISS_TAG).performClick()
        // No month line beside it.
        rule.onNodeWithTag(RECIBO_INVITATION_TAG).assertDoesNotExist()

        assertEquals(
            listOf(
                ReciboAction.OpenYear(2026),
                ReciboAction.DismissInvitation(YearMonth.of(2026, 12))
            ),
            actions
        )
    }

    @Test
    fun thePlateCountsAndNeverShowsMoney() {
        show(ready)

        rule.onNodeWithText("AÑO CERRADO").assertExists()
        rule.onNodeWithText("2026").assertExists()
        rule.onNodeWithText("1.240").assertExists()
        rule.onNodeWithText("gastos anotados").assertExists()
        rule.onNodeWithText("318").assertExists()
        rule.onNodeWithText("días con algo anotado").assertExists()
        rule.onNodeWithText("10 de 12").assertExists()
        rule.onNodeWithText("meses cumplidos").assertExists()
        rule.onNodeWithText("2").assertExists()
        rule.onNodeWithText("metas logradas").assertExists()
        rule.onNodeWithContentDescription("Marzo: excedido").assertExists()
        rule.onNodeWithContentDescription("Enero: cumplido").assertExists()
    }

    @Test
    fun withoutLimitsOrGoalsThoseLinesAreLeftOut() {
        show(noLimitsNoGoals)

        rule.onNodeWithText("1.240").assertExists()
        rule.onNodeWithText("318").assertExists()
        rule.onNodeWithText("meses cumplidos").assertDoesNotExist()
        rule.onNodeWithText("metas logradas").assertDoesNotExist()
        rule.onNodeWithContentDescription("Enero: sin límite").assertExists()
    }

    @Test
    fun oneMonthAndOneGoalAreSaidInTheSingular() {
        show(ready.copy(withinMonths = 1, limitedMonths = 1, goalsReached = 1))

        rule.onNodeWithText("1 de 1").assertExists()
        rule.onNodeWithText("mes cumplido").assertExists()
        rule.onNodeWithText("meta lograda").assertExists()
    }

    @Test
    fun aFailedReadSaysSoInsteadOfAnyCount() {
        show(YearCloseState.Failed(2026))

        rule.onNodeWithTag(YEAR_CLOSE_FAILED_TAG).assertExists()
        rule.onNodeWithTag(YEAR_CLOSE_FACTS_TAG).assertDoesNotExist()
        rule.onNodeWithText("Ver tu año").assertExists()
    }

    @Test
    fun closedOrStillReadingShowsNothing() {
        rule.setContent {
            SelloTheme {
                YearCloseMoment(YearCloseState.Loading(2026), {}, {})
                YearCloseMoment(YearCloseState.Closed, {}, {})
            }
        }

        rule.onNodeWithText("Ver tu año").assertDoesNotExist()
    }

    @Test
    fun withReducedMotionItOpensOnTheFinalScreen() {
        rule.mainClock.autoAdvance = false
        show(ready, reduced = true)

        rule.onNodeWithText("¡Un año\ncon Sello!").assertExists()
        rule.onNodeWithTag(YEAR_CLOSE_PLATE_TAG).assertExists()
        rule.onNodeWithText("Ver tu año").assertExists()
    }

    @Test
    fun theMonthsLandThenTheTitlePlateAndActionsFollow() {
        rule.mainClock.autoAdvance = false
        show(ready, reduced = false)
        // The months land a tenth of a second apart from 0.3 s; June's lands at 0.8 s.
        rule.mainClock.advanceTimeBy(900)
        rule.onNodeWithContentDescription("Junio: cumplido").assertExists()
        rule.onNodeWithContentDescription("Diciembre: cumplido").assertDoesNotExist()
        rule.onNodeWithText("¡Un año\ncon Sello!").assertDoesNotExist()
        rule.mainClock.advanceTimeBy(2_400)
        rule.onNodeWithContentDescription("Diciembre: cumplido").assertExists()
        rule.onNodeWithText("¡Un año\ncon Sello!").assertExists()
        rule.onNodeWithText("Ver tu año").assertExists()
    }

    @Test
    fun verTuAnoOpensTheYearAndListoCloses() {
        val viewed = mutableListOf<Int>()
        var done = 0
        show(ready, reduced = true, onDone = { done++ }) { viewed += it }

        rule.onNodeWithText("Ver tu año").performClick()
        rule.onNodeWithText("Listo").performClick()

        assertEquals(listOf(2026), viewed)
        assertEquals(1, done)
    }
}

/** The card and the big moment, kept as committed images (ADR 0022). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class YearCloseScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val ready = YearCloseState.Ready(
        year = 2026,
        months = (1..12).map {
            YearCloseMonth(
                YearMonth.of(2026, it),
                if (it == 3 || it == 7) YearCloseMark.Over else YearCloseMark.Within
            )
        },
        expenses = 1_240,
        recordedDays = 318,
        withinMonths = 10,
        limitedMonths = 12,
        goalsReached = 2
    )

    /** Reduced motion, as every screenshot: the final screen at once, without chads. */
    private fun still(name: String, state: YearCloseState, look: Look) {
        rule.showSello(look) { Host { YearCloseMoment(state, {}, {}) } }
        rule.captureStill("src/test/screenshots/year-close-$name-${look.label}.png")
    }

    @Test
    fun reducedCobaltoLight() = still("reduced", ready, Look())

    @Test
    fun reducedCobaltoDark() = still("reduced", ready, Look(dark = true))

    @Test
    fun failedCobaltoLight() = still("failed", YearCloseState.Failed(2026), Look())

    /** With motion, just after the actions arrive: the plate stamped, chads falling. */
    @Test
    fun finalWithChadsCobaltoLight() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            SelloTheme(reducedMotion = false) { Host { YearCloseMoment(ready, {}, {}) } }
        }
        rule.mainClock.advanceTimeBy(3_700)
        rule.captureStill("src/test/screenshots/year-close-final-cobalto-light-font1.0.png")
    }
}

/** The screen the moment opens over: a capture is the size of the window under the dialog. */
@Composable
private fun Host(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) { content() }
}
