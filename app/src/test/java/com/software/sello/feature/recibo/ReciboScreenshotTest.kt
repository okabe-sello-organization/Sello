package com.software.sello.feature.recibo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.R
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.icon.SelloIcon
import com.software.sello.designsystem.icon.painter
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.feature.goals.CompletionState
import com.software.sello.feature.goals.GoalDetailError
import com.software.sello.feature.goals.ReciboGoal
import com.software.sello.feature.goals.ReciboGoalPill
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.feature.goals.ReciboReachedGoal
import com.software.sello.navigation.CategoryEditAction
import com.software.sello.navigation.CorrectionNoticeBar
import com.software.sello.navigation.DayByDayBarAction
import com.software.sello.navigation.ShellDetail
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.navigation.ShellTab
import com.software.sello.navigation.appTabs
import com.software.sello.navigation.octoberShell
import com.software.sello.presentation.carry.CarryProblem
import com.software.sello.presentation.carry.CarryProblemKind
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.month.monthNames
import com.software.sello.showSello
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
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

    /**
     * 3 January 2027, the mock's: 2.310.000 left of the month, 79.650 a day for the 29 days
     * from today to the 31st, and the year card in the invitation's place (SELLO-200).
     */
    private val january = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            plain("2.310.000"),
            ReciboStamp.OnTrack,
            perDay = ReciboPerDay(plain("79.650"), 29)
        ),
        listOf(
            category(
                id = "a",
                name = "Alimentación",
                headline = ReciboHeadline.Remaining,
                amount = plain("850.000"),
                stamp = ReciboStamp.OnTrack,
                limit = "900.000",
                fraction = 0.06f
            ),
            category(
                id = "b",
                name = "Hogar",
                headline = ReciboHeadline.Remaining,
                amount = plain("1.100.000"),
                stamp = ReciboStamp.OnTrack,
                limit = "1.500.000",
                fraction = 0.27f
            )
        ),
        stale = false,
        invitation = ReciboInvitation(YearMonth.of(2026, 12), closesYear = true)
    )

    private fun captureYearCard(look: Look) {
        val invited = january
        rule.showSello(look) {
            Box(Modifier.fillMaxSize().background(SelloTheme.colors.desk)) {
                ReciboScreen(invited, {}, PaddingValues(12.dp))
            }
        }
        rule.captureStill("src/test/screenshots/recibo-year-card-${look.label}.png")
    }

    @Test
    fun yearCardCobaltoLight() = captureYearCard(Look())

    @Test
    fun yearCardCobaltoDark() = captureYearCard(Look(dark = true))

    @Test
    fun yearCardVioletaLight() = captureYearCard(Look(ink = SelloInk.Violeta))

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
    fun olderGoalsDark() =
        captureGoals("stale", listOf(viaje, fondo, bici), 0, Look(dark = true), stale = true)

    @Test
    fun oneGoal() = captureGoals("one", listOf(viaje), 0, Look())

    @Test
    fun oneGoalDark() = captureGoals("one", listOf(viaje), 0, Look(dark = true))

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
    fun markCompleteUnknownDark() = captureGoals(
        "mark-unknown",
        listOf(viaje),
        0,
        Look(dark = true),
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

    private fun shot(
        name: String,
        state: ReciboState,
        look: Look,
        goal: ReciboGoalState? = null,
        notice: CorrectionNotice? = null,
        shell: ShellState = octoberShell
    ) {
        rule.showSello(look) {
            // As ReciboDestinations.kt frames the tab: the month bar, the settings action, Anotar.
            ShellScreen(
                shell,
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Recibo),
                notice = { if (notice != null) CorrectionNoticeBar(notice) {} },
                actions = {
                    DayByDayBarAction {}
                    IconButton(onClick = {}) {
                        Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
                    }
                }
            ) { padding ->
                ReciboScreen(
                    state,
                    {},
                    padding,
                    goal = { if (goal != null) ReciboGoal(goal, {}, {}, {}) }
                )
            }
        }
        rule.captureStill("src/test/screenshots/recibo-$name-${look.label}.png")
    }

    @Test fun loadingLight() = shot("loading", ReciboState.Loading, Look())

    @Test fun loadingDark() = shot("loading", ReciboState.Loading, Look(dark = true))

    @Test fun firstRunLight() = shot("first-run", ReciboState.FirstRun(), Look())

    @Test fun firstRunDark() = shot("first-run", ReciboState.FirstRun(), Look(dark = true))

    @Test fun failedLight() = shot("failed", ReciboState.Failed, Look())

    @Test fun failedDark() = shot("failed", ReciboState.Failed, Look(dark = true))

    @Test fun staleLight() = shot("stale", overspent.copy(stale = true), Look())

    @Test fun staleDark() = shot("stale", overspent.copy(stale = true), Look(dark = true))

    private val problem =
        CarryProblem("a", "Comida", CarryProblemKind.Missing, YearMonth.of(2026, 9))

    @Test fun carryProblemLight() = shot(
        "carry-problem",
        overspent.copy(carryProblem = problem),
        Look()
    )

    @Test
    fun carryProblemDark() =
        shot("carry-problem", overspent.copy(carryProblem = problem), Look(dark = true))

    @Test fun carryFailedLight() = shot("carry-failed", ReciboState.CarryFailed(problem), Look())

    @Test
    fun carryFailedDark() =
        shot("carry-failed", ReciboState.CarryFailed(problem), Look(dark = true))

    @Test
    fun goalsFailedLight() = shot("goals-failed", overspent, Look(), ReciboGoalState.Failed)

    @Test
    fun goalsFailedDark() =
        shot("goals-failed", overspent, Look(dark = true), ReciboGoalState.Failed)

    private val marking =
        ReciboGoalState.Shown(listOf(celular), null, CompletionState.Busy, stale = false)

    /** Celular nuevo reached 100 % elsewhere: the green row offers Marcar como lograda. */
    private val reached = ReciboGoalState.Shown(
        listOf(viaje),
        ReciboReachedGoal("celular", "Celular nuevo", "phone_iphone")
    )

    @Test fun goalsReachedLight() = shot("goals-reached", overspent, Look(), reached)

    @Test fun goalsReachedDark() = shot("goals-reached", overspent, Look(dark = true), reached)

    @Test fun goalsBusyLight() = shot("goals-busy", overspent, Look(), marking)

    @Test fun goalsBusyDark() = shot("goals-busy", overspent, Look(dark = true), marking)

    // ---------- The correction notice bar over Recibo (shell.html#screen-notice) ----------

    private val notices = mapOf(
        // A deletion that changed three later months' limits, still undoable.
        "deleted-carry" to CorrectionNotice.Deleted(canUndo = true, carryMonths = 3),
        "undoing" to CorrectionNotice.Deleted(canUndo = true, undoing = true),
        "undo-failed" to CorrectionNotice.Deleted(canUndo = true, undoFailed = true),
        "unknown" to CorrectionNotice.DeleteUnknown(),
        "checking" to CorrectionNotice.DeleteUnknown(checking = true),
        "refused" to CorrectionNotice.NotDeleted,
        "undo-blocked" to CorrectionNotice.UndoBlocked,
        "restored" to CorrectionNotice.Restored,
        // Deshacer asked after its time was over: the deletion stands.
        "too-late" to CorrectionNotice.UndoTooLate,
        "recorded" to CorrectionNotice.ExpenseRecorded(plain("48.700"), "Comida"),
        // Borrar categoría done: the editor and the detail closed onto Recibo.
        "category-deleted" to CorrectionNotice.CategoryDeleted("Café", 34, movedTo = "Comida"),
        // The limit sheet saved: what limit was set.
        "limit-adjusted" to CorrectionNotice.LimitAdjusted(
            "Café",
            plain("150.000"),
            YearMonth.of(2026, 10),
            fromThisMonth = false
        )
    )

    private fun notice(name: String, look: Look) =
        shot("notice-$name", overspent, look, notice = notices.getValue(name))

    @Test fun noticeDeletedCarryLight() = notice("deleted-carry", Look())

    @Test fun noticeDeletedCarryDark() = notice("deleted-carry", Look(dark = true))

    @Test fun noticeUndoingLight() = notice("undoing", Look())

    @Test fun noticeUndoingDark() = notice("undoing", Look(dark = true))

    @Test fun noticeUndoFailedLight() = notice("undo-failed", Look())

    @Test fun noticeUndoFailedDark() = notice("undo-failed", Look(dark = true))

    @Test fun noticeUnknownLight() = notice("unknown", Look())

    @Test fun noticeUnknownDark() = notice("unknown", Look(dark = true))

    @Test fun noticeCheckingLight() = notice("checking", Look())

    @Test fun noticeCheckingDark() = notice("checking", Look(dark = true))

    @Test fun noticeRefusedLight() = notice("refused", Look())

    @Test fun noticeRefusedDark() = notice("refused", Look(dark = true))

    @Test fun noticeUndoBlockedLight() = notice("undo-blocked", Look())

    @Test fun noticeUndoBlockedDark() = notice("undo-blocked", Look(dark = true))

    @Test fun noticeTooLateLight() = notice("too-late", Look())

    @Test fun noticeTooLateDark() = notice("too-late", Look(dark = true))

    @Test fun noticeRestoredLight() = notice("restored", Look())

    @Test fun noticeRestoredDark() = notice("restored", Look(dark = true))

    @Test fun noticeCategoryDeletedLight() = notice("category-deleted", Look())

    @Test fun noticeCategoryDeletedDark() = notice("category-deleted", Look(dark = true))

    @Test fun noticeLimitAdjustedLight() = notice("limit-adjusted", Look())

    @Test fun noticeLimitAdjustedDark() = notice("limit-adjusted", Look(dark = true))

    @Test fun noticeRecordedLight() = notice("recorded", Look())

    @Test fun noticeRecordedDark() = notice("recorded", Look(dark = true))

    // ---------- Recibo's other months and sections (recibo.html#screen-recibo) ----------

    /** September 2026 seen from October: finished, the ribbon over it. */
    private val september = octoberShell.copy(selectedMonth = YearMonth.of(2026, 9))

    /** September closed within its limit: 1.200.000 − 1.080.000 = 120.000 left over. */
    private val finished = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            plain("120.000"),
            ReciboStamp.WithinLimit,
            closed = ReciboClosedMonth(plain("1.080.000"), plain("1.200.000"))
        ),
        listOf(
            category(
                id = "a",
                name = "Alimentación",
                headline = ReciboHeadline.LeftOver,
                amount = plain("160.000"),
                stamp = ReciboStamp.WithinLimit,
                limit = "900.000",
                fraction = 0.82f
            ),
            category(
                id = "b",
                name = "Transporte",
                headline = ReciboHeadline.Overspent,
                amount = plain("40.000"),
                stamp = ReciboStamp.OverLimit,
                limit = "200.000",
                fraction = 1f,
                over = true
            ),
            category(
                id = "c",
                name = "Entretenimiento",
                headline = ReciboHeadline.LeftOver,
                amount = plain("0"),
                stamp = ReciboStamp.WithinLimit,
                limit = "100.000",
                fraction = 1f
            )
        ),
        stale = false
    )

    @Test fun finishedLight() = shot("finished", finished, Look(), shell = september)

    @Test
    fun finishedDark() = shot("finished", finished, Look(dark = true), shell = september)

    private val onTrack = overspent.categories.take(2) + overspent.categories[3]

    /** 1.200.000 − 838.000 = 362.000 left; 50.000 of it after the 312.000 reserved for 2 payments. */
    private val reserved = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.Remaining,
            plain("50.000"),
            ReciboStamp.OnTrack,
            perDay = ReciboPerDay(plain("5.000"), 10),
            reserved = ReciboReserved(plain("312.000"), 2)
        ),
        onTrack,
        stale = false
    )

    @Test fun reservedLight() = shot("reserved", reserved, Look())

    @Test fun reservedDark() = shot("reserved", reserved, Look(dark = true))

    /** 800.000 left and 950.000 reserved: 950.000 − 800.000 = 150.000 short. */
    private val review = ReciboState.Month(
        ReciboSummary(
            ReciboAnswerLabel.ShortForPayments,
            plain("150.000"),
            ReciboStamp.Review,
            adjustable = true,
            reserved = ReciboReserved(plain("950.000"), 3),
            shortfall = ReciboShortfall(plain("800.000"), over = false, reserved = plain("950.000"))
        ),
        onTrack,
        stale = false
    )

    @Test fun reviewLight() = shot("review", review, Look())

    @Test fun reviewDark() = shot("review", review, Look(dark = true))

    /** Mascotas archived this month, with what it spent before: under its own heading. */
    private val withArchived = overspent.copy(
        categories = overspent.categories + category(
            id = "e",
            name = "Mascotas",
            headline = ReciboHeadline.Spent,
            amount = plain("35.000"),
            stamp = ReciboStamp.Paused,
            limit = null,
            fraction = null
        ).copy(archived = true, iconKey = "pets")
    )

    @Test fun archivedLight() = shot("archived", withArchived, Look())

    @Test fun archivedDark() = shot("archived", withArchived, Look(dark = true))

    /**
     * Landscape (891 × 412): Recibo with Alimentación's detail beside it, as
     * ReciboDestinations.kt lays out two panes; the open cell is tinted.
     */
    private fun twoPanes(look: Look) {
        // 900.000 − 287.600 left = 612.400 spent.
        val food = overspent.categories.first().copy(spent = plain("612.400"))
        val detail = CategoryDetailState.Ready(
            category = food,
            month = YearMonth.of(2026, 10),
            today = LocalDate.of(2026, 10, 22),
            stale = false,
            canRecord = true,
            expenses = ExpenseListState.Rows(
                listOf(
                    ExpenseRow("1", LocalDate.of(2026, 10, 21), "Mercado", plain("48.700"), false),
                    ExpenseRow("2", LocalDate.of(2026, 10, 18), null, plain("33.500"), false)
                ),
                more = true
            )
        )
        rule.showSello(look) {
            ShellScreen(
                octoberShell.copy(detailCategoryId = food.id),
                monthNames(),
                {},
                navigation = appTabs(ShellTab.Recibo),
                actions = {
                    DayByDayBarAction {}
                    IconButton(onClick = {}) {
                        Icon(SelloIcon.Settings.painter(), stringResource(R.string.settings_open))
                    }
                },
                detail = ShellDetail(food.name, actions = {
                    CategoryEditAction(true) {}
                }) { padding ->
                    CategoryDetailScreen(
                        state = detail,
                        months = monthNames(),
                        dates = dateLabels(),
                        showName = true,
                        onAction = {},
                        onRecordHere = {},
                        contentPadding = padding
                    )
                }
            ) { padding ->
                ReciboScreen(overspent, {}, padding, selectedCategoryId = food.id)
            }
        }
        rule.captureStill("src/test/screenshots/recibo-two-panes-${look.label}.png")
    }

    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun twoPanesLight() = twoPanes(Look())

    @Test
    @Config(qualifiers = "w891dp-h412dp-mdpi")
    fun twoPanesDark() = twoPanes(Look(dark = true))
}
