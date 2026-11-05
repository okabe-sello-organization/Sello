package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.goals.CompletionState
import com.software.sello.feature.goals.GoalDetailError
import com.software.sello.feature.goals.ReciboGoal
import com.software.sello.feature.goals.ReciboGoalPill
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.showSello
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Recibo looks like in an overspent month, kept as committed images (ADR 0022). The host
 * gate compares each capture with its baseline in `src/test/screenshots/`; a visual change
 * re-records them in the same pull request, and the image diff is how the change is reviewed.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReciboScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private fun category(
        id: String,
        name: String,
        headline: ReciboHeadline,
        amount: MoneyTextValue,
        stamp: ReciboStamp,
        limit: String?,
        fraction: Float?,
        over: Boolean = false
    ) = ReciboCategory(
        id = id,
        name = name,
        iconKey = "restaurant",
        archived = false,
        figure = BudgetFigure(headline, amount, stamp),
        cellAmount = amount,
        spent = plain("1"),
        limit = limit?.let { plain(it) },
        fraction = fraction,
        over = over,
        expenseCount = 8
    )

    /** An overspent month: the answer in the red, a category in each of the main states. */
    private val overspent = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            minus("43.700"),
            ReciboStamp.Over,
            perDay = ReciboPerDay(plain("0"), 9),
            adjustable = true
        ),
        listOf(
            category(
                id = "a",
                name = "Alimentación",
                headline = ReciboHeadline.Remaining,
                amount = plain("287.600"),
                stamp = ReciboStamp.OnTrack,
                limit = "900.000",
                fraction = 0.68f
            ),
            category(
                id = "b",
                name = "Transporte",
                headline = ReciboHeadline.Remaining,
                amount = plain("55.000"),
                stamp = ReciboStamp.AtLimit,
                limit = "200.000",
                fraction = 1f
            ),
            category(
                id = "c",
                name = "Entretenimiento",
                headline = ReciboHeadline.Overspent,
                amount = minus("21.500"),
                stamp = ReciboStamp.Over,
                limit = "100.000",
                fraction = 1f,
                over = true
            ),
            category(
                id = "d",
                name = "Arte",
                headline = ReciboHeadline.Spent,
                amount = plain("80.000"),
                stamp = ReciboStamp.NoLimit,
                limit = null,
                fraction = null
            )
        ),
        stale = false
    )

    private fun capture(look: Look) {
        rule.showSello(look) { ReciboScreen(overspent, {}, PaddingValues(12.dp)) }
        rule.captureStill("src/test/screenshots/recibo-${look.label}.png")
    }

    /** The first week of a month: the same screen with the dashed invitation under the answer. */
    private fun captureInvited(look: Look) {
        val invited = overspent.copy(invitation = ReciboInvitation(YearMonth.of(2026, 10)))
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ReciboScreen(invited, {}, PaddingValues(12.dp))
            }
        }
        rule.captureStill("src/test/screenshots/recibo-invitation-${look.label}.png")
    }

    @Test
    fun invitationCobaltoLight() = captureInvited(Look())

    @Test
    fun invitationCobaltoDark() = captureInvited(Look(dark = true))

    @Test
    fun cobaltoLight() = capture(Look())

    @Test
    fun cobaltoDark() = capture(Look(dark = true))

    @Test
    fun violetaLight() = capture(Look(ink = SelloInk.Violeta))

    // ---------- Recibo's goals (SELLO-144, home.html's carousel scenes) ----------

    private val viaje = ReciboGoalPill("viaje", "Viaje a la costa", "flight", 57)
    private val celular =
        ReciboGoalPill("celular", "Celular nuevo", "phone_iphone", 100, reached = true)
    private val fondo = ReciboGoalPill("fondo", "Fondo de emergencia", "home", 42)
    private val bici = ReciboGoalPill("bici", "Bicicleta", "fitness_center", 18)
    private val curso = ReciboGoalPill("curso", "Curso de inglés", "school", 5)

    /** Recibo with [pills] in the goal slot, moved [forward] goals on before the capture. */
    private fun captureGoals(
        name: String,
        pills: List<ReciboGoalPill>,
        forward: Int,
        look: Look,
        stale: Boolean = false,
        completion: CompletionState = CompletionState.Idle
    ) {
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ReciboScreen(
                    overspent,
                    {},
                    PaddingValues(12.dp),
                    goal = {
                        ReciboGoal(
                            ReciboGoalState.Shown(pills, null, completion, stale = stale),
                            {},
                            {},
                            {}
                        )
                    }
                )
            }
        }
        repeat(forward) { step ->
            val shown = pills[step]
            val node = rule.onNodeWithContentDescription(
                "${shown.name}, ${shown.percentage} por ciento. Meta ${step + 1} de ${pills.size}. Ver meta"
            ).fetchSemanticsNode()
            val next = node.config[SemanticsActions.CustomActions].single {
                it.label ==
                    "Meta siguiente"
            }
            rule.runOnIdle { next.action() }
        }
        rule.captureStill("src/test/screenshots/recibo-goals-$name-${look.label}.png")
    }

    @Test
    fun olderGoals() = captureGoals("stale", listOf(viaje, fondo, bici), 0, Look(), stale = true)

    @Test
    fun oneGoal() = captureGoals("one", listOf(viaje), 0, Look())

    /** Marcar como lograda with an unknown outcome: titled as a save, Comprobar only. */
    @Test
    fun markCompleteUnknown() = captureGoals(
        "mark-unknown",
        listOf(viaje),
        0,
        Look(),
        completion = CompletionState.Failed(GoalDetailError.Unknown)
    )

    @Test
    fun threeGoalsOnTheSecond() =
        captureGoals("three-second", listOf(viaje, fondo, bici), 1, Look())

    @Test
    fun threeGoalsOnTheSecondDark() =
        captureGoals("three-second", listOf(viaje, fondo, bici), 1, Look(dark = true))

    @Test
    fun fiveGoalsOnTheReachedSecond() =
        captureGoals("five-reached", listOf(viaje, celular, fondo, bici, curso), 1, Look())

    @Test
    fun fiveGoalsOnTheReachedSecondDark() = captureGoals(
        "five-reached",
        listOf(viaje, celular, fondo, bici, curso),
        1,
        Look(dark = true)
    )
}
