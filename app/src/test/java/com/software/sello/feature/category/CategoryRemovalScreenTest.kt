package com.software.sello.feature.category

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.showSello
import com.software.sello.testing.pesos
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Each step of deleting Café, given by hand (borrar-categoria.html): what it says, in the
 * design's words, and what each control reports. On the JVM (ADR 0022), with the real
 * text engine: under Robolectric's stand-in one, the Borrar todo slip did not go idle on a
 * window wider than 320 dp (seen 2026-11-05; the cause was not traced further).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryRemovalScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<CategoryEditorAction>()
    private val october = YearMonth.of(2026, 10)
    private val counts = RemovalCounts(34, pesos("412.000"), YearMonth.of(2026, 5), october, 1)

    private val editing = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "local_cafe",
        editing = true,
        name = "Café",
        limitText = "50000",
        limitAmount = pesos("50.000"),
        targetMonth = october
    )

    private fun show(step: RemovalStep, busy: Boolean = false) {
        rule.showSello(Look()) {
            CategoryEditorScreen(
                editing.copy(removal = RemovalState("Café", step, busy)),
                { actions += it }
            )
        }
    }

    private fun button(text: String) = rule.onNode(hasText(text) and hasClickAction())

    @Test
    fun anEmptyCategoryIsAskedOnceInRed() {
        show(RemovalStep.Empty)

        rule.onNodeWithText("¿Borrar Café?").assertIsDisplayed()
        rule.onNodeWithText(
            "No tiene gastos ni pagos fijos. Se borran la categoría y su límite. " +
                "Esto no se puede deshacer."
        ).assertIsDisplayed()
        button("Cancelar").performClick()
        button("Borrar").performClick()

        assertEquals(listOf(RemovalAction.Dismiss, RemovalAction.Confirm), actions)
    }

    @Test
    fun theChoiceSaysWhatItHoldsAndOffersMoverBorrarTodoAndArchivar() {
        show(RemovalStep.Choose(counts, canArchive = true))

        rule.onNodeWithText(
            "Café tiene 34 gastos por 412.000, de mayo a octubre de 2026, y 1 pago fijo. " +
                "¿Qué hacemos con ellos?"
        ).assertIsDisplayed()
        rule.onNodeWithText("Los totales de cada mes no cambian.").assertIsDisplayed()
        rule.onNodeWithText("Desaparecen de todos los meses.").assertIsDisplayed()
        rule.onNodeWithTag(REMOVAL_MOVE_CHOICE_TAG).performClick()
        rule.onNodeWithTag(REMOVAL_ALL_CHOICE_TAG).performClick()
        rule.onNodeWithText("¿Solo quieres dejar de usarla? Archívala: no se borra nada.")
            .performClick()
        button("Cancelar").performClick()

        assertEquals(
            listOf(
                RemovalAction.Move,
                RemovalAction.DeleteAll,
                RemovalAction.ArchiveInstead,
                RemovalAction.Dismiss
            ),
            actions
        )
    }

    @Test
    fun anArchivedCategoryIsNotPointedToArchiving() {
        show(RemovalStep.Choose(counts, canArchive = false))

        rule.onNodeWithTag(REMOVAL_ARCHIVE_TAG).assertDoesNotExist()
    }

    @Test
    fun oneMonthAndOnlyFixedPaymentsAreWordedAsSuch() {
        show(
            RemovalStep.Choose(
                RemovalCounts(1, pesos("8.000"), october, october, 0),
                canArchive = true
            )
        )

        rule.onNodeWithText(
            "Café tiene 1 gasto por 8.000, en octubre de 2026. ¿Qué hacemos con él?"
        ).assertIsDisplayed()
    }

    @Test
    fun theListOffersEachCategoryWithWhatIsLeftAndSaysWhyArchivedOnesAreNot() {
        val comida = MoveTarget("2", "Comida", "restaurant", pesos("120.000"))
        val hogar = MoveTarget("3", "Hogar", "home", pesos("200.000"))
        show(RemovalStep.Pick(counts, october, listOf(hogar, comida)))

        rule.onNodeWithText("Mover los gastos de Café a").assertIsDisplayed()
        rule.onNodeWithText("34 gastos y 1 pago fijo. Lo que queda en octubre:").assertIsDisplayed()
        rule.onNodeWithText("Las archivadas no aparecen: no aceptan gastos.").assertIsDisplayed()
        rule.onNodeWithTag(removalTargetTag("2")).assertIsDisplayed().performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Pick("2")), actions)
    }

    @Test
    fun aListThatCouldNotBeReadOffersToReadItAgain() {
        show(RemovalStep.Pick(counts, october, null, failed = true))

        rule.onNodeWithText("No pudimos leer tus categorías.").assertIsDisplayed()
        button("Reintentar").performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Next), actions)
    }

    @Test
    fun theMoveSaysTheExactFactsAndMoverYBorrarSendsIt() {
        show(RemovalStep.Move(counts, moveFacts))

        rule.onNodeWithText("¿Mover a Comida y borrar Café?").assertIsDisplayed()
        rule.onNodeWithText(
            "Los 34 gastos y su pago fijo pasan a Comida, en sus mismas fechas. Los límites " +
                "y el arrastre de Café se borran. Esto no se puede deshacer."
        ).assertIsDisplayed()
        // Each row is read as one sentence; on screen "80.000 → 140.000", "50.000, se borra".
        rule.onNodeWithContentDescription(
            "Gastado en Comida, octubre: de 80.000 pesos a 140.000 pesos"
        ).assertIsDisplayed()
        rule.onNodeWithContentDescription("Límite de Café, octubre: 50.000 pesos, se borra")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Límite de Comida: 200.000 pesos, no cambia")
            .assertIsDisplayed()
        button("Mover y borrar").performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Confirm), actions)
    }

    @Test
    fun borrarTodoCountsWhatGoesAndStaysOffUntilTheWordIsTyped() {
        show(RemovalStep.All(counts, ""))

        rule.onNodeWithText("¿Borrar Café y todo lo suyo?").assertIsDisplayed()
        rule.onNodeWithContentDescription("34 gastos, may – oct 2026: 412.000 pesos")
            .assertIsDisplayed()
        rule.onNodeWithContentDescription("Pagos fijos: 1").assertIsDisplayed()
        rule.onNodeWithContentDescription("Sus límites y su arrastre: Todos los meses")
            .assertIsDisplayed()
        rule.onNodeWithText(
            "Los meses pasados mostrarán menos gasto. Esto no se puede deshacer."
        ).assertIsDisplayed()
        button("Borrar todo").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Escribe BORRAR para confirmar")
            .assertIsDisplayed()
            .performTextInput("BORRAR")

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Word("BORRAR")), actions)
    }

    @Test
    fun withTheWordTypedBorrarTodoSendsIt() {
        show(RemovalStep.All(counts, "BORRAR"))

        button("Borrar todo").assertIsEnabled().performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Confirm), actions)
    }

    @Test
    fun changedContentsSayTheNewCountsAndRevisar() {
        show(
            RemovalStep.Problem(
                RemovalProblem.Changed(
                    counts,
                    counts.copy(expenses = 35, total = pesos("472.000"))
                )
            )
        )

        rule.onNodeWithText("Café cambió").assertIsDisplayed()
        rule.onNodeWithText("No se borró ni se movió nada.").assertIsDisplayed()
        rule.onNodeWithText(
            "Mientras lo veías, cambió lo que tiene Café: ahora son 35 gastos por 472.000, " +
                "no 34 por 412.000."
        ).assertIsDisplayed()
        rule.onNodeWithText("Revisa las cifras nuevas antes de seguir.").assertIsDisplayed()
        button("Revisar").performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Next), actions)
    }

    @Test
    fun aTargetThatClosedOffersAnother() {
        show(RemovalStep.Problem(RemovalProblem.TargetClosed("Comida")))

        rule.onNodeWithText("Comida ya no acepta gastos").assertIsDisplayed()
        rule.onNodeWithText("No se borró ni se movió nada.").assertIsDisplayed()
        rule.onNodeWithText(
            "Comida se archivó o se borró mientras elegías. Los gastos de Café no pueden ir allí."
        ).assertIsDisplayed()
        button("Elegir otra").assertIsDisplayed()
    }

    @Test
    fun storageThatFailedSaysWhatIsStillThere() {
        show(RemovalStep.Problem(RemovalProblem.NotSaved(counts)))

        rule.onNodeWithText("No se pudo guardar").assertIsDisplayed()
        rule.onNodeWithText(
            "El teléfono no pudo guardar el cambio. Café sigue con sus 34 gastos, sus límites " +
                "y su pago fijo."
        ).assertIsDisplayed()
        button("Reintentar").assertIsDisplayed()
    }

    @Test
    fun countsThatCouldNotBeReadOfferNoDeletion() {
        show(RemovalStep.Problem(RemovalProblem.CannotCount))

        rule.onNodeWithText("Aún no se puede borrar").assertIsDisplayed()
        rule.onNodeWithText("No se borró ni se movió nada.").assertDoesNotExist()
        rule.onNodeWithText(
            "No pudimos contar lo que tiene Café. Sello no borra nada sin decirte antes " +
                "exactamente qué se pierde."
        ).assertIsDisplayed()
        button("Borrar").assertDoesNotExist()
        button("Reintentar").assertIsDisplayed()
    }

    @Test
    fun anUnknownOutcomeIsCheckedAndOnlyAskedAgainWhenTheCheckFailed() {
        show(RemovalStep.Checking(failed = false), busy = true)

        rule.onNodeWithText("Comprobando…").assertIsDisplayed()
        rule.onNodeWithText(
            "Sello no supo si terminó de borrar Café y lo está revisando. No se borrará dos veces."
        ).assertIsDisplayed()
        button("Comprobar de nuevo").assertDoesNotExist()
        button("Cancelar").assertDoesNotExist()
    }

    @Test
    fun aCheckThatFailedCanBeAskedAgain() {
        show(RemovalStep.Checking(failed = true))

        button("Comprobar de nuevo").performClick()

        assertEquals(listOf<CategoryEditorAction>(RemovalAction.Next), actions)
    }

    @Test
    fun whileSendingNothingCanBePressedTwice() {
        show(RemovalStep.Move(counts, moveFacts), busy = true)

        button("Cancelar").assertIsNotEnabled()
    }

    private val moveFacts = MoveFacts(
        target = "Comida",
        month = october,
        targetSpent = pesos("80.000"),
        targetSpentAfter = pesos("140.000"),
        sourceLimit = RemovalLimit.Finite(pesos("50.000")),
        targetLimit = RemovalLimit.Finite(pesos("200.000"))
    )
}
