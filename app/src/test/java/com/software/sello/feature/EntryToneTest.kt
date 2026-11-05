package com.software.sello.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureToImage
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.feature.bulk.BULK_ERROR_TAG
import com.software.sello.feature.bulk.BulkCategory
import com.software.sello.feature.bulk.BulkEntryScreen
import com.software.sello.feature.bulk.BulkEntryState
import com.software.sello.feature.bulk.BulkError
import com.software.sello.feature.bulk.BulkLineUi
import com.software.sello.feature.category.CategoryEditorScreen
import com.software.sello.feature.category.CategoryEditorState
import com.software.sello.feature.category.EDITOR_SAVE_ERROR_TAG
import com.software.sello.feature.category.SaveError
import com.software.sello.feature.expense.ENTRY_ERROR_TAG
import com.software.sello.feature.expense.EntryCategory
import com.software.sello.feature.expense.EntryError
import com.software.sello.feature.expense.ExpenseEntryScreen
import com.software.sello.feature.expense.ExpenseEntryState
import com.software.sello.feature.expense.Missing
import com.software.sello.feature.goals.CompletionState
import com.software.sello.feature.goals.GOAL_EDITOR_DONE_ERROR_TAG
import com.software.sello.feature.goals.GOAL_EDITOR_ERROR_TAG
import com.software.sello.feature.goals.GoalBulkSamples
import com.software.sello.feature.goals.GoalBulkScreen
import com.software.sello.feature.goals.GoalChoice
import com.software.sello.feature.goals.GoalDetailError
import com.software.sello.feature.goals.GoalEditorScreen
import com.software.sello.feature.goals.GoalEditorState
import com.software.sello.feature.goals.GoalMovementError
import com.software.sello.feature.goals.GoalMovementScreen
import com.software.sello.feature.goals.GoalMovementState
import com.software.sello.feature.goals.GoalSaveError
import com.software.sello.feature.goals.RECIBO_GOAL_ERROR_TAG
import com.software.sello.feature.goals.ReciboGoal
import com.software.sello.feature.goals.ReciboGoalPill
import com.software.sello.feature.goals.ReciboGoalState
import com.software.sello.feature.goals.ReciboReachedGoal
import com.software.sello.feature.income.IncomeFormError
import com.software.sello.feature.income.IncomeFormScreen
import com.software.sello.feature.income.IncomeFormState
import com.software.sello.feature.investments.ContributedTotal
import com.software.sello.feature.investments.INVESTMENT_EDITOR_ERROR_TAG
import com.software.sello.feature.investments.INVESTMENT_MOVEMENTS_ERROR_TAG
import com.software.sello.feature.investments.INVESTMENT_MOVEMENT_ERROR_TAG
import com.software.sello.feature.investments.InvestmentChoice
import com.software.sello.feature.investments.InvestmentEditorScreen
import com.software.sello.feature.investments.InvestmentEditorState
import com.software.sello.feature.investments.InvestmentMovementError
import com.software.sello.feature.investments.InvestmentMovementScreen
import com.software.sello.feature.investments.InvestmentMovementState
import com.software.sello.feature.investments.InvestmentMovementsError
import com.software.sello.feature.investments.InvestmentMovementsScreen
import com.software.sello.feature.investments.InvestmentMovementsState
import com.software.sello.feature.investments.InvestmentSaveError
import com.software.sello.feature.investments.MovementLineModel
import com.software.sello.feature.investments.RateEntryError
import com.software.sello.feature.investments.UsdRateEntryScreen
import com.software.sello.feature.investments.UsdRateEntryState
import com.software.sello.feature.recurrence.ScheduleCategory
import com.software.sello.feature.recurrence.ScheduleEditorError
import com.software.sello.feature.recurrence.ScheduleEditorScreen
import com.software.sello.feature.recurrence.ScheduleEditorState
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.presentation.date.dateLabels
import com.software.sello.revealed
import java.time.LocalDate
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The v2 entry design's tones (docs/design/v2/sello.css `.save-msg.warn`, `.entry-mid h3`):
 * an unknown outcome is said in the amber warning ink, never in error red (#289, #332), and
 * a form's section headings are in the softer ink, never the theme's ink (#349). Checked
 * by the pixels each draws, in all four themes.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EntryToneTest {
    @get:Rule
    val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 22)

    private val looks = listOf(
        Look(),
        Look(dark = true),
        Look(ink = SelloInk.Violeta),
        Look(ink = SelloInk.Violeta, dark = true)
    )

    private fun plain(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private class Inks(val warn: Color, val loss: Color, val ink: Color, val soft: Color)

    /** Shows [content] once, then in each theme checks the pixels of what [node] picks. */
    private fun inEveryLook(
        content: @Composable () -> Unit,
        node: () -> List<SemanticsNodeInteraction>,
        check: (String, PixelMap, Inks) -> Unit
    ) {
        var look by mutableStateOf(looks.first())
        var inks: Inks? = null
        rule.setContent {
            SelloTheme(ink = look.ink, darkTheme = look.dark, reducedMotion = true) {
                val colors = SelloTheme.colors
                inks = Inks(colors.semantic.warn, colors.semantic.loss, colors.ink, colors.inkSoft)
                content()
            }
        }
        looks.forEach { next ->
            look = next
            rule.waitForIdle()
            node().forEach { check(next.label, it.captureToImage().toPixelMap(), inks!!) }
        }
    }

    private fun warns(tag: String, content: @Composable () -> Unit) = inEveryLook(
        content,
        { listOf(rule.onNodeWithTag(tag, useUnmergedTree = true).revealed()) }
    ) { look, pixels, inks ->
        assertTrue("$tag in $look draws no warning ink", pixels.count(inks.warn) > 20)
        assertEquals("$tag in $look draws error red", 0, pixels.count(inks.loss))
    }

    /** Each heading of [texts] reads in capitals, in the softer ink, as the design draws it. */
    private fun softHeadings(vararg texts: String, content: @Composable () -> Unit) = inEveryLook(
        content,
        {
            texts.map {
                rule.onNode(isHeading() and hasText(it.uppercase()), useUnmergedTree = true)
            }
        }
    ) { look, pixels, inks ->
        assertTrue("a heading in $look draws no soft ink", pixels.count(inks.soft) > 20)
        assertEquals("a heading in $look draws the theme's ink", 0, pixels.count(inks.ink))
    }

    private fun PixelMap.count(color: Color, tolerance: Float = 0.04f): Int {
        var found = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                val pixel = this[x, y]
                if (abs(pixel.red - color.red) < tolerance &&
                    abs(pixel.green - color.green) < tolerance &&
                    abs(pixel.blue - color.blue) < tolerance
                ) {
                    found++
                }
            }
        }
        return found
    }

    private val expense = ExpenseEntryState(
        loading = false,
        categories = listOf(EntryCategory("a", "Alimentación", "restaurant")),
        categoryId = "a",
        amount = plain("48.700"),
        missing = null,
        date = today,
        today = today
    )

    private val goals = listOf(
        GoalChoice("a", "Viaje a la costa", "flight", 57, plain("1.710.000")),
        GoalChoice("b", "Celular nuevo", "phone_iphone", 37, plain("450.000"))
    )

    private val goalMovement = GoalMovementState(
        loading = false,
        amount = plain("350.000"),
        goals = goals,
        goalId = "a",
        date = today,
        today = today
    )

    private val bulk = BulkEntryState(
        lines = listOf(
            BulkLineUi("1", BulkCategory("t", "Transporte", "directions_bus"), plain("18.400"), "")
        ),
        highlighted = 0,
        date = today,
        today = today,
        total = plain("18.400"),
        loading = false
    )

    @Test
    fun anUnknownSaveOnAnotarIsAmber() = warns(ENTRY_ERROR_TAG) {
        ExpenseEntryScreen(expense.copy(error = EntryError.Unknown), dateLabels(), {})
    }

    @Test
    fun anUnknownDeleteOnACorrectionIsAmber() = warns(ENTRY_ERROR_TAG) {
        val unknown = expense.copy(
            editing = true,
            missing = Missing.Changes,
            error = EntryError.DeleteUnknown
        )
        ExpenseEntryScreen(unknown, dateLabels(), {})
    }

    @Test
    fun anUnknownIncomeSaveIsAmber() = warns("income.error") {
        IncomeFormScreen(
            IncomeFormState(
                date = today,
                today = today,
                loading = false,
                error = IncomeFormError.Unknown
            ),
            {}
        )
    }

    @Test
    fun anUnknownBulkSaveIsAmber() = warns(BULK_ERROR_TAG) {
        BulkEntryScreen(bulk.copy(error = BulkError.Unknown), dateLabels(), {})
    }

    @Test
    fun anUnknownGoalMovementIsAmber() = warns("goal.movement.error") {
        GoalMovementScreen(goalMovement.copy(error = GoalMovementError.Unknown)) {}
    }

    @Test
    fun anUnknownAportarVariosIsAmber() = warns("goal.bulk.error") {
        GoalBulkScreen(GoalBulkSamples.unknown) {}
    }

    @Test
    fun anUnknownInvestmentMovementIsAmber() = warns(INVESTMENT_MOVEMENT_ERROR_TAG) {
        InvestmentMovementScreen(
            InvestmentMovementState(
                date = today,
                today = today,
                loading = false,
                error = InvestmentMovementError.Unknown
            )
        ) {}
    }

    @Test
    fun anUnknownRateIsAmber() = warns("usd.rate.entry.error") {
        UsdRateEntryScreen(
            UsdRateEntryState(
                loading = false,
                loadFailed = false,
                rate = "4.000",
                problem = null,
                current = null,
                date = today,
                today = today,
                error = RateEntryError.Unknown
            )
        ) {}
    }

    @Test
    fun anUnknownGoalEditorSaveIsAmber() = warns(GOAL_EDITOR_ERROR_TAG) {
        GoalEditorScreen(
            GoalEditorState(
                loading = false,
                editing = true,
                changed = true,
                name = "Viaje a la costa",
                nameLength = 16,
                iconKey = "flight",
                amount = plain("6.000.000"),
                saveError = GoalSaveError.Unknown,
                unresolved = true
            )
        ) {}
    }

    @Test
    fun anUnknownInvestmentEditorSaveIsAmber() = warns(INVESTMENT_EDITOR_ERROR_TAG) {
        InvestmentEditorScreen(
            InvestmentEditorState(
                loading = false,
                editing = true,
                name = "Cripto",
                iconKey = "currency_bitcoin",
                saveError = InvestmentSaveError.Unknown
            )
        ) {}
    }

    @Test
    fun aGoalMovementsHeadingIsInTheSofterInk() = softHeadings("Meta") {
        GoalMovementScreen(goalMovement) {}
    }

    @Test
    fun aBulkReceiptsHeadingIsInTheSofterInk() = softHeadings("Categoría") {
        BulkEntryScreen(bulk, dateLabels(), {})
    }

    @Test
    fun aportarVariosHeadingIsInTheSofterInk() = softHeadings("Meta y tipo") {
        GoalBulkScreen(GoalBulkSamples.opening) {}
    }

    @Test
    fun anInvestmentMovementsHeadingsAreInTheSofterInk() = softHeadings("Tipo", "Inversión") {
        InvestmentMovementScreen(
            InvestmentMovementState(date = today, today = today, loading = false)
        ) {}
    }

    private val receipt = InvestmentMovementsState(
        loading = false,
        investments = listOf(InvestmentChoice("e", "Ecopetrol", "trending_up", usd = false)),
        lines = listOf(
            MovementLineModel(
                "1",
                InvestmentMovementKind.Contribution,
                plain("500.000"),
                valid = true,
                date = today,
                note = "",
                investmentId = "e",
                investmentName = "Ecopetrol"
            )
        ),
        contributed = ContributedTotal.One(plain("500.000")),
        today = today
    )

    @Test
    fun anUnknownVariosMovimientosIsAmber() = warns(INVESTMENT_MOVEMENTS_ERROR_TAG) {
        InvestmentMovementsScreen(receipt.copy(error = InvestmentMovementsError.Unknown)) {}
    }

    @Test
    fun variosMovimientosHeadingIsInTheSofterInk() = softHeadings("Inversión") {
        InvestmentMovementsScreen(receipt) {}
    }

    @Test
    fun anUnknownFixedPaymentSaveIsAmber() = warns("schedule-editor:error") {
        ScheduleEditorScreen(
            ScheduleEditorState(
                loading = false,
                amount = plain("950.000"),
                categories = listOf(ScheduleCategory("h", "Hogar", "home")),
                categoryId = "h",
                today = today,
                error = ScheduleEditorError.Unknown
            ),
            dateLabels(),
            {}
        )
    }

    @Test
    fun anUnknownCategorySaveIsAmber() = warns(EDITOR_SAVE_ERROR_TAG) {
        CategoryEditorScreen(
            CategoryEditorState(
                loading = false,
                iconKeys = CategoryIcons.keys,
                iconKey = "home",
                saveError = SaveError.Unknown
            ),
            {}
        )
    }

    @Test
    fun anUnknownMarcarComoLogradaInEditarMetaIsAmber() = warns(GOAL_EDITOR_DONE_ERROR_TAG) {
        GoalEditorScreen(
            GoalEditorState(
                loading = false,
                editing = true,
                name = "Viaje a la costa",
                nameLength = 16,
                iconKey = "flight",
                amount = plain("6.000.000"),
                completion = CompletionState.Failed(GoalDetailError.Unknown)
            )
        ) {}
    }

    @Test
    fun anUnknownMarcarComoLogradaOnReciboIsAmber() = warns(RECIBO_GOAL_ERROR_TAG) {
        ReciboGoal(
            ReciboGoalState.Shown(
                listOf(ReciboGoalPill("viaje", "Viaje a la costa", "flight", 57)),
                ReciboReachedGoal("curso", "Curso de inglés", "school"),
                CompletionState.Failed(GoalDetailError.Unknown)
            ),
            {},
            {},
            {}
        )
    }
}
