package com.software.sello.feature.recibo

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.feature.goals.RECIBO_GOAL_DOTS_TAG
import com.software.sello.feature.goals.ReciboGoal
import com.software.sello.feature.goals.ReciboGoalPill
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.navigation.DayByDayBarAction
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Recibo's Guía hint and backup reminder under the month's answer (SELLO-211), and the goal
 * nudge under the goal card (SELLO-213).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReciboNudgeScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private fun month(nudge: ReciboNudge) = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            plain("130.000"),
            ReciboStamp.OnTrack,
            perDay = ReciboPerDay(plain("18.550"), 7)
        ),
        listOf(
            ReciboCategory(
                id = "a",
                name = "Alimentación",
                iconKey = "restaurant",
                archived = false,
                figure = BudgetFigure(
                    ReciboHeadline.Remaining,
                    plain("130.000"),
                    ReciboStamp.OnTrack
                ),
                cellAmount = plain("130.000"),
                spent = plain("100.000"),
                limit = plain("230.000"),
                fraction = 0.43f,
                over = false,
                expenseCount = 6
            )
        ),
        stale = false,
        nudge = nudge
    )

    private val actions = mutableListOf<ReciboAction>()

    private val goals = ReciboGoalState.Shown(
        listOf(
            ReciboGoalPill("viaje", "Viaje a la costa", "flight", 57),
            ReciboGoalPill("fondo", "Fondo de emergencia", "home", 42)
        ),
        reached = null
    )

    private fun show(nudge: ReciboNudge, look: Look = Look()) = rule.showSello(look) {
        // As ReciboDestinations.kt frames the tab: the month bar, the settings action, Anotar.
        ShellScreen(
            octoberShell,
            monthNames(),
            {},
            navigation = appTabs(ShellTab.Recibo),
            actions = {
                DayByDayBarAction {}
                IconButton(onClick = {}) {
                    Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
                }
            }
        ) { padding ->
            ReciboScreen(month(nudge), { actions += it }, padding) {
                // Two goals taking turns, as Recibo's goal card shows them, for its nudge only.
                if (nudge == ReciboNudge.Goal) ReciboGoal(goals, {}, {}, {})
            }
        }
    }

    private fun shot(name: String, nudge: ReciboNudge, look: Look) {
        show(nudge, look)
        rule.captureStill("src/test/screenshots/recibo-nudge-$name-${look.label}.png")
    }

    @Test fun guideHintLight() = shot("guide", ReciboNudge.GuideHint, Look())

    @Test fun guideHintDark() = shot("guide", ReciboNudge.GuideHint, Look(dark = true))

    @Test
    fun backupReminderLight() = shot("backup", ReciboNudge.BackupReminder(34), Look())

    @Test
    fun backupReminderDark() = shot("backup", ReciboNudge.BackupReminder(34), Look(dark = true))

    @Test
    fun noBackupYetLight() = shot("no-backup", ReciboNudge.BackupReminder(null), Look())

    @Test
    fun noBackupYetDark() = shot("no-backup", ReciboNudge.BackupReminder(null), Look(dark = true))

    @Test fun goalNudgeLight() = shot("goal", ReciboNudge.Goal, Look())

    @Test
    fun theGoalNudgeSaysHowToPutAGoalFirstAndSitsUnderTheCardAlone() {
        show(ReciboNudge.Goal)

        rule.onNodeWithText(
            "Tus metas destacadas salen primero aquí. Abre una en Metas y toca Destacar en Recibo."
        ).assertIsDisplayed()
        val dots = rule.onNodeWithTag(RECIBO_GOAL_DOTS_TAG).fetchSemanticsNode().boundsInRoot
        val nudge = rule.onNodeWithTag(RECIBO_GOAL_NUDGE_TAG).fetchSemanticsNode().boundsInRoot
        assert(nudge.top >= dots.bottom) { "the goal nudge $nudge is not under the card $dots" }
        rule.onNodeWithTag(RECIBO_GUIDE_HINT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_BACKUP_REMINDER_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Cerrar el aviso de las metas").performClick()

        assertEquals(listOf(ReciboAction.CloseGoalNudge), actions)
    }

    @Test
    fun theHintSaysWhereTheGuideIsAndItsTextOpensIt() {
        show(ReciboNudge.GuideHint)

        rule.onNodeWithText("¿Dudas? La Guía de Sello está en Configuración.").assertIsDisplayed()
        rule.onNodeWithTag(RECIBO_GUIDE_HINT_TAG).performClick()
        rule.onNodeWithContentDescription("Cerrar el aviso de la Guía").performClick()

        assertEquals(listOf(ReciboAction.OpenGuide, ReciboAction.CloseGuideHint), actions)
    }

    @Test
    fun theReminderSaysTheDaysAndItsTwoWaysOut() {
        show(ReciboNudge.BackupReminder(34))

        rule.onNodeWithText(
            "Tu último respaldo fue hace 34 días. Tus datos viven solo en este teléfono."
        ).assertIsDisplayed()
        rule.onNodeWithText("Ir a Respaldo").performClick()
        rule.onNodeWithText("No volver a mostrar").performClick()

        assertEquals(listOf(ReciboAction.OpenBackup, ReciboAction.TurnOffBackupReminder), actions)
    }

    @Test
    fun withNoBackupItSaysThereIsNoneYet() {
        show(ReciboNudge.BackupReminder(null))

        rule.onNodeWithText(
            "Aún no tienes ningún respaldo. Tus datos viven solo en este teléfono."
        ).assertIsDisplayed()
    }

    @Test
    fun theRowSitsUnderTheMonthsAnswer() {
        show(ReciboNudge.GuideHint)

        val summary = rule.onNodeWithTag(RECIBO_SUMMARY_TAG).fetchSemanticsNode().boundsInRoot
        val hint = rule.onNodeWithTag(RECIBO_GUIDE_HINT_TAG).fetchSemanticsNode().boundsInRoot
        assert(hint.top >= summary.bottom) { "the hint $hint is not under the answer $summary" }
    }
}
