package com.software.sello.feature.category

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.component.MORE_MENU_TAG
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Deleting Café as committed images (ADR 0022), one per frame of borrar-categoria.html:
 * the ⋮, the Archivada slip, each confirmation, the list to move to and each slip that
 * says it did not happen. A visual change re-records them in the same pull request.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryRemovalScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val october = YearMonth.of(2026, 10)
    private val counts = RemovalCounts(34, pesos("412.000"), YearMonth.of(2026, 5), october, 1)

    /** Café, limit 50.000, opened from October with nothing changed. */
    private val editing = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "local_cafe",
        suggestions = listOf(
            LimitSuggestion(200_000, "200.000", "200.000 pesos"),
            LimitSuggestion(300_000, "300.000", "300.000 pesos"),
            LimitSuggestion(500_000, "500.000", "500.000 pesos")
        ),
        editing = true,
        name = "Café",
        limitText = "50000",
        limitAmount = pesos("50.000"),
        targetMonth = october
    )

    private fun capture(name: String, state: CategoryEditorState, look: Look = Look()) {
        rule.showSello(look) { CategoryEditorScreen(state, {}) }
        rule.captureStill("src/test/screenshots/$name-${look.label}.png")
    }

    private fun removal(step: RemovalStep) = editing.copy(removal = RemovalState("Café", step))

    private fun menu(look: Look) {
        rule.showSello(look) { CategoryEditorScreen(editing, {}) }
        rule.onNodeWithTag(MORE_MENU_TAG).performClick()
        rule.captureStill("src/test/screenshots/category-editor-menu-${look.label}.png")
    }

    @Test
    fun theMenu() = menu(Look())

    @Test
    fun theMenuDark() = menu(Look(dark = true))

    @Test
    fun anArchivedCategory() = capture(
        "category-editor-archived",
        editing.copy(archived = true, limitLocked = true, limitText = "", limitAmount = null)
    )

    @Test
    fun nothingInIt() = capture("category-removal-empty", removal(RemovalStep.Empty))

    @Test
    fun choose() = capture("category-removal-choose", removal(RemovalStep.Choose(counts, true)))

    @Test
    fun chooseDark() = capture(
        "category-removal-choose",
        removal(RemovalStep.Choose(counts, true)),
        Look(dark = true)
    )

    @Test
    fun chooseVioleta() = capture(
        "category-removal-choose",
        removal(RemovalStep.Choose(counts, true)),
        Look(ink = SelloInk.Violeta)
    )

    @Test
    fun pick() = capture(
        "category-removal-pick",
        removal(
            RemovalStep.Pick(
                counts,
                october,
                listOf(
                    MoveTarget("3", "Hogar", "home", pesos("200.000")),
                    MoveTarget("2", "Comida", "restaurant", pesos("120.000")),
                    MoveTarget("5", "Transporte", "directions_bus", pesos("60.000")),
                    MoveTarget("6", "Ropa", "checkroom", pesos("70.000"))
                )
            )
        )
    )

    @Test
    fun move() = capture(
        "category-removal-move",
        removal(
            RemovalStep.Move(
                counts,
                MoveFacts(
                    target = "Comida",
                    month = october,
                    targetSpent = pesos("80.000"),
                    targetSpentAfter = pesos("140.000"),
                    sourceLimit = RemovalLimit.Finite(pesos("50.000")),
                    targetLimit = RemovalLimit.Finite(pesos("200.000"))
                )
            )
        )
    )

    @Test
    fun borrarTodo() = capture("category-removal-all", removal(RemovalStep.All(counts, "")))

    @Test
    fun borrarTodoTyped() =
        capture("category-removal-all-typed", removal(RemovalStep.All(counts, "BORRAR")))

    @Test
    fun changed() = capture(
        "category-removal-changed",
        removal(
            RemovalStep.Problem(
                RemovalProblem.Changed(counts, counts.copy(expenses = 35, total = pesos("472.000")))
            )
        )
    )

    @Test
    fun targetClosed() = capture(
        "category-removal-target",
        removal(RemovalStep.Problem(RemovalProblem.TargetClosed("Comida")))
    )

    @Test
    fun notSaved() = capture(
        "category-removal-storage",
        removal(RemovalStep.Problem(RemovalProblem.NotSaved(counts)))
    )

    @Test
    fun cannotCount() = capture(
        "category-removal-count",
        removal(RemovalStep.Problem(RemovalProblem.CannotCount))
    )

    @Test
    fun checking() = capture(
        "category-removal-checking",
        editing.copy(removal = RemovalState("Café", RemovalStep.Checking(false), busy = true))
    )
}
