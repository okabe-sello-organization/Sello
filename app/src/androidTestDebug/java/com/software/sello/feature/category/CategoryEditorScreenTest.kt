package com.software.sello.feature.category

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.presentation.category.CategoryIcons
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The form alone, given state by hand: what each state shows and what each control reports. */
@RunWith(AndroidJUnit4::class)
class CategoryEditorScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<CategoryEditorAction>()
    private val ready = CategoryEditorState(
        loading = false,
        iconKeys = CategoryIcons.keys,
        iconKey = "home",
        suggestions = listOf(
            LimitSuggestion(200_000, "200.000", "200.000 pesos"),
            LimitSuggestion(300_000, "300.000", "300.000 pesos"),
            LimitSuggestion(500_000, "500.000", "500.000 pesos")
        )
    )

    private fun show(state: CategoryEditorState, fontScale: Float = 1f) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    CategoryEditorScreen(state, { actions += it })
                }
            }
        }
    }

    /** A field is found by its label, which is also what a screen reader calls it. */
    private val name get() = rule.onNodeWithContentDescription("Nombre")
    private val limit get() = rule.onNodeWithContentDescription("Límite en pesos")

    @Test
    fun whileItLoadsThereIsNoFormAndAFailureOffersRetry() {
        show(CategoryEditorState(loading = false, loadFailed = true))

        rule.onNodeWithTag(EDITOR_LOAD_FAILED_TAG).assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Retry), actions)
    }

    @Test
    fun anEmptyFormCannotBeSubmittedAndStartsAskingForAnAmountWithSinLimiteOff() {
        show(ready)

        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).performScrollTo().assertIsOff()
        limit.performScrollTo().assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("200.000").performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Hogar").assertIsSelected()
        rule.onNodeWithTag(EDITOR_PREREQUISITE_TAG).assertDoesNotExist()
    }

    @Test
    fun withSinLimiteChosenTheAmountAndSuggestionsStayGreyedOutAndCannotBeUsed() {
        // An amount typed before the switch is kept, greyed out, and is not sent.
        show(ready.copy(name = "Mercado", unlimited = true, limitText = "450000"))

        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).performScrollTo().assertIsOn()
        limit.performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        assertEquals("450.000", drawnLimit())
        for (suggestion in listOf("200.000 pesos", "300.000 pesos", "500.000 pesos")) {
            rule.onNodeWithContentDescription(suggestion).performScrollTo().assertIsNotEnabled()
        }
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Submit), actions)
    }

    @Test
    fun theNameIsFocusedOnceTheNewFormIsReady() {
        var state by mutableStateOf(CategoryEditorState())
        rule.setContent { SelloTheme(reducedMotion = true) { CategoryEditorScreen(state, {}) } }
        state = ready

        rule.waitForIdle()
        name.assertIsFocused()
    }

    @Test
    fun theNameIsFocusedWhenAnExistingCategoryOpens() {
        show(editing)

        rule.waitForIdle()
        name.assertIsFocused()
    }

    @Test
    fun aFormThatCannotBeChangedDoesNotTakeFocus() {
        show(ready.copy(name = "Mercado", busy = true))

        rule.waitForIdle()
        rule.onAllNodes(isFocused()).assertCountEquals(0)
    }

    private fun total(digits: String, changes: Boolean) = LimitTotal(
        october,
        MoneyTextValue(MoneySign.None, "", digits, "$digits pesos"),
        changes
    )

    @Test
    fun theMonthsTotalSaysWhatItWouldBecomeWithThisLimit() {
        show(ready.copy(limitText = "200000", limitTotal = total("1.400.000", changes = true)))

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).performScrollTo().assertIsDisplayed()
            .assertContentDescriptionEquals("Tu límite total de octubre pasaría a 1.400.000 pesos")
    }

    @Test
    fun aTotalTheLimitDoesNotChangeIsSaidAsItIs() {
        show(editing.copy(limitTotal = total("1.200.000", changes = false)))

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).performScrollTo()
            .assertContentDescriptionEquals("Tu límite total de octubre es 1.200.000 pesos")
    }

    @Test
    fun withNoTotalToShowThereIsNoLine() {
        show(ready)

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).assertDoesNotExist()
    }

    @Test
    fun switchingSinLimiteOffAgainShowsTheAmountThatHadBeenTyped() {
        show(ready.copy(name = "Mercado", unlimited = false, limitText = "450000"))

        limit.performScrollTo()

        assertEquals("450.000", drawnLimit())
    }

    @Test
    fun anAmountThatIsMissingIsSaidOnTheFieldWhenCreatingWasTried() {
        show(ready.copy(name = "Mercado", limitError = LimitError.Required))

        rule.onNodeWithText("Escribe el límite o activa Sin límite.").performScrollTo()
            .assertIsDisplayed()
        limit.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.Error,
                "Escribe el límite o activa Sin límite."
            )
        )
    }

    @Test
    fun everyControlReportsWhatWasDoneAndChangesNothingItself() {
        show(ready.copy(name = "Mer", unlimited = false))

        name.performTextInput("c")
        rule.onNodeWithContentDescription("Mascotas").performScrollTo().performClick()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).performScrollTo().performClick()
        limit.performScrollTo().performTextInput("5")
        // A suggestion is read with its currency, not as a bare number.
        rule.onNodeWithContentDescription("300.000 pesos").performScrollTo().performClick()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().performClick()
        rule.onNodeWithContentDescription("Volver").performClick()

        // The form is given the same state again, so the field goes back to "Mer" and
        // says so; what matters is that the typed text was reported first, as typed,
        // after the name already there.
        assertEquals(CategoryEditorAction.NameChanged("Merc"), actions.first())
        assertEquals(
            listOf(
                CategoryEditorAction.IconPicked("pets"),
                CategoryEditorAction.UnlimitedChanged(true),
                CategoryEditorAction.LimitChanged("5"),
                CategoryEditorAction.SuggestionPicked(300_000),
                CategoryEditorAction.Submit,
                CategoryEditorAction.Back
            ),
            actions.filterNot { it is CategoryEditorAction.NameChanged }
        )
        name.assertTextContains("Mer")
    }

    @Test
    fun errorsAreWrittenOutNextToWhatWasTypedWhichStaysAsItWas() {
        show(
            ready.copy(
                name = "CAFE",
                nameError = NameError.Taken,
                unlimited = false,
                limitText = "1e3",
                limitError = LimitError.NotAnAmount,
                saveError = SaveError.NotSaved
            )
        )

        rule.onNodeWithText("Ya tienes una categoría con ese nombre.").assertIsDisplayed()
        name.assertTextContains("CAFE")
        rule.onNodeWithText("Escribe solo el número, por ejemplo 300000 o 300.000.")
            .performScrollTo().assertIsDisplayed()
        limit.assertTextContains("1e3")
        rule.onNodeWithTag(EDITOR_SAVE_ERROR_TAG).performScrollTo().assertIsDisplayed()
        // A name known to be taken cannot be sent (owner decision, SELLO-020): the
        // button waits for another name instead of offering a save that must fail.
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun whileSavingNothingCanBeChangedOrSentAgain() {
        show(ready.copy(name = "Mercado", busy = true))

        name.assertIsNotEnabled()
        rule.onNodeWithContentDescription("Mascotas").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().performClick()

        assertEquals(emptyList<CategoryEditorAction>(), actions)
    }

    @Test
    fun anUnknownOutcomeOffersToCheckNotToCreateAgain() {
        show(ready.copy(name = "Mercado", saveError = SaveError.Unknown))

        rule.onNodeWithText("Crear categoría").assertDoesNotExist()
        name.assertIsNotEnabled()
        rule.onNodeWithText("Comprobar si se guardó").performScrollTo().performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Retry), actions)
    }

    @Test
    fun openedForAnEntryItExplainsWhyACategoryComesFirst() {
        show(ready.copy(forEntry = true))

        rule.onNodeWithTag(EDITOR_PREREQUISITE_TAG).assertIsDisplayed()
        rule.onNodeWithText(
            "Para anotar un gasto primero necesitas una categoría. Crea la primera aquí."
        )
            .assertIsDisplayed()
    }

    @Test
    fun discardingADraftIsAskedForAndEitherAnswerIsReported() {
        show(ready.copy(name = "Mer", confirmDiscard = true))

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        rule.onNodeWithText("Seguir editando").performClick()
        rule.onNodeWithText("Descartar").performClick()

        assertEquals(
            listOf(CategoryEditorAction.KeepEditing, CategoryEditorAction.DiscardConfirmed),
            actions
        )
    }

    @Test
    fun atTwiceTheFontSizeEveryControlCanStillBeReached() {
        show(ready.copy(name = "Mercado de plaza", unlimited = false), fontScale = 2f)

        name.performScrollTo().assertIsDisplayed()
        rule.onNodeWithContentDescription("Hijos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).performScrollTo().assertIsOff()
        rule.onNodeWithText("500.000").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(
            EDITOR_SUBMIT_TAG
        ).performScrollTo().assertIsDisplayed().assertIsEnabled()
    }

    private fun drawnLimit(): String? = limit.fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.EditableText)?.text

    @Test
    fun aLimitIsDrawnInGroupsSoItsSizeCanBeRead() {
        show(ready.copy(unlimited = false, limitText = "400000"))

        limit.performScrollTo()

        assertEquals("400.000", drawnLimit())
    }

    @Test
    fun aSuggestedLimitIsDrawnTheWayItsChipReads() {
        show(ready.copy(unlimited = false, limitText = "500000"))

        limit.performScrollTo()

        assertEquals("500.000", drawnLimit())
        // The field and the chip now read the same; the chip is the one that is not typed in.
        rule.onNode(hasText("500.000") and !hasSetTextAction()).performScrollTo().assertIsSelected()
    }

    @Test
    fun whatIsTypedIntoAGroupedLimitIsReportedWithoutTheSeparators() {
        show(ready.copy(unlimited = false, limitText = "400000"))

        limit.performScrollTo().performTextInput("7")

        // One digit more than the six there were, and nothing the person did not type.
        val reported = (actions.single() as CategoryEditorAction.LimitChanged).text
        assertEquals(7, reported.length)
        assertEquals(true, reported.all { it in '0'..'9' })
        assertEquals("400000", reported.replaceFirst("7", ""))
    }

    @Test
    fun aLimitThatIsNotAnAmountIsDrawnAsTypedBesideItsError() {
        show(ready.copy(unlimited = false, limitText = "1e3", limitError = LimitError.NotAnAmount))

        limit.performScrollTo()

        assertEquals("1e3", drawnLimit())
        rule.onNodeWithText("Escribe solo el número, por ejemplo 300000 o 300.000.")
            .performScrollTo().assertIsDisplayed()
    }

    // --- Changing an existing category ---

    private val october = java.time.YearMonth.of(2026, 10)

    /** "Café", limit 100.000, opened from October and not changed yet. */
    private val editing get() = ready.copy(
        editing = true,
        name = "Café",
        iconKey = "local_cafe",
        limitText = "100000",
        targetMonth = october
    )

    @Test
    fun anExistingCategoryIsTitledAndWordedAsAChangeWithNothingToSaveYet() {
        show(editing)

        rule.onNodeWithText("Editar categoría").assertIsDisplayed()
        rule.onNodeWithText("Nueva categoría").assertDoesNotExist()
        rule.onNodeWithText("Crear categoría").assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("Guardar cambios").assertExists()
        rule.onNodeWithTag(EDITOR_NO_CHANGES_TAG).assertIsDisplayed()
        rule.onNodeWithText("Aún no cambiaste nada.").assertExists()
    }

    @Test
    fun onceSomethingChangedItCanBeSaved() {
        show(editing.copy(name = "Cafetería", changed = true))

        rule.onNodeWithTag(EDITOR_NO_CHANGES_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsEnabled().performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Submit), actions)
    }

    @Test
    fun aNameAnotherCategoryHasIsSaidOnTheFieldAndCannotBeSaved() {
        show(editing.copy(name = "Mercado", changed = true, nameError = NameError.Taken))

        rule.onNodeWithText("Ya tienes una categoría con ese nombre.").assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun inTheCurrentMonthItSaysTheLimitAppliesFromThisMonthOn() {
        show(editing)

        rule.onNodeWithTag(EDITOR_SCOPE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Vale desde Octubre 2026 en adelante. Los meses anteriores no cambian."
        ).assertExists()
    }

    @Test
    fun fromAFinishedMonthItNamesThatMonthAndSaysNothingElseChanges() {
        show(editing.copy(targetMonth = october.minusMonths(1), historical = true))

        rule.onNodeWithTag(EDITOR_SCOPE_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Cambias solo el límite de Septiembre 2026. " +
                "Los demás meses y el límite habitual no cambian."
        ).assertExists()
    }

    @Test
    fun whenOnlyPartWasSavedItSaysWhichPartAndThatTheRestWasNot() {
        show(
            editing.copy(
                changed = true,
                saveError = SaveError.NotSaved,
                savedParts = listOf(EditPart.Name, EditPart.Icon)
            )
        )

        rule.onNodeWithTag(EDITOR_SAVED_PARTS_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Se guardó: nombre, ícono. Lo demás no se guardó; inténtalo de nuevo.")
            .assertExists()
        rule.onNodeWithText("No se pudo guardar. No se creó la categoría; inténtalo de nuevo.")
            .assertDoesNotExist()
    }

    @Test
    fun aRefusedChangeWithNothingSavedSaysTheCategoryWasLeftAsItWas() {
        show(editing.copy(changed = true, saveError = SaveError.NotSaved))

        rule.onNodeWithText(
            "No se pudo guardar. La categoría quedó como estaba; inténtalo de nuevo."
        )
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aConflictOffersToReloadOrToKeepWhatWasTypedAndNothingElse() {
        show(editing.copy(name = "Cafetería", changed = true, saveError = SaveError.Conflict))

        rule.onNodeWithText("Esta categoría cambió mientras la editabas.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_CONFLICT_RELOAD_TAG).performScrollTo().performClick()
        rule.onNodeWithTag(EDITOR_CONFLICT_KEEP_TAG).performScrollTo().performClick()

        assertEquals(
            listOf(
                CategoryEditorAction.ReloadDiscardingDraft,
                CategoryEditorAction.ReloadKeepingDraft
            ),
            actions
        )
        rule.onNodeWithText("Volver a cargar").assertExists()
        rule.onNodeWithText("Conservar mis cambios").assertExists()
    }

    @Test
    fun aCategoryThatNoLongerExistsIsSaidAndNothingCanBeSent() {
        show(editing.copy(saveError = SaveError.CategoryGone))

        rule.onNodeWithText("Esta categoría ya no existe. No se cambió nada.")
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun anArchiveThatCouldNotRecordItsDuePaymentsFirstSaysSo() {
        show(editing.copy(saveError = SaveError.PaymentsPending))

        rule.onNodeWithText(
            "No se archivó: Sello no pudo anotar antes los pagos fijos de esta categoría " +
                "que ya tocaban. Inténtalo de nuevo."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun archivingIsOfferedInTheTopBarAndANewCategoryHasNoSuchAction() {
        show(editing)

        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).assertIsDisplayed().performClick()

        rule.onNodeWithText("Archivar").assertExists()
        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Archive), actions)
    }

    @Test
    fun aNewCategoryHasNothingToArchive() {
        show(ready)

        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).assertDoesNotExist()
    }

    @Test
    fun beforeArchivingItSaysWhatStaysWhatStopsAndFromWhen() {
        show(
            editing.copy(
                changed = true,
                archiveQuestion = ArchiveQuestion(
                    unarchive = false,
                    expensesThisMonth = 13,
                    currentMonth = october,
                    resumesBudget = false,
                    discardsChanges = true
                )
            )
        )

        rule.onNodeWithText("¿Archivar Café?").assertIsDisplayed()
        rule.onNodeWithText(
            "Deja de aceptar gastos nuevos. Octubre conserva su límite; desde noviembre " +
                "queda sin presupuesto hasta que la desarchives. Sus 13 gastos de octubre y " +
                "los de meses anteriores se conservan. Los cambios sin guardar se descartan."
        ).assertIsDisplayed()
        rule.onNodeWithText("Cancelar").performClick()
        rule.onAllNodesWithText("Archivar").onLast().performClick()

        assertEquals(
            listOf(CategoryEditorAction.ArchiveDismissed, CategoryEditorAction.ArchiveConfirmed),
            actions
        )
    }

    @Test
    fun whenTheExpensesCouldNotBeCountedNoNumberIsGiven() {
        show(
            editing.copy(
                archiveQuestion = ArchiveQuestion(false, null, october, false, false)
            )
        )

        rule.onNodeWithText(
            "Deja de aceptar gastos nuevos. Octubre conserva su límite; desde noviembre " +
                "queda sin presupuesto hasta que la desarchives. Sus gastos se conservan."
        ).assertIsDisplayed()
    }

    @Test
    fun anArchivedCategorySaysSoHasNoLimitToChangeAndIsOfferedToUnarchive() {
        show(editing.copy(name = "Mercado", archived = true, limitLocked = true, limitText = ""))

        rule.onNodeWithTag(EDITOR_ARCHIVED_TAG).assertIsDisplayed()
        rule.onNodeWithText("Desarchivar").assertIsDisplayed()
        rule.onNodeWithContentDescription("Límite en pesos").assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).assertDoesNotExist()
        rule.onNodeWithText(
            "Está archivada: no tiene límite de aquí en adelante. Desarchívala para ponerle uno."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun beforeUnarchivingItSaysWhetherTheBudgetComesBack() {
        var question by mutableStateOf(ArchiveQuestion(true, 0, october, true, false))
        rule.setContent {
            SelloTheme(reducedMotion = true) {
                CategoryEditorScreen(
                    editing.copy(name = "Mercado", archived = true, archiveQuestion = question),
                    { actions += it }
                )
            }
        }

        rule.onNodeWithText("¿Desarchivar Mercado?").assertIsDisplayed()
        rule.onNodeWithText(
            "Vuelve a aceptar gastos y recupera su último límite desde octubre. " +
                "Los meses en que estuvo archivada siguen sin presupuesto."
        ).assertIsDisplayed()
        question = question.copy(resumesBudget = false)
        rule.waitForIdle()
        rule.onNodeWithText(
            "Vuelve a aceptar gastos. Su límite de octubre sigue como estaba y nada más cambia."
        ).assertIsDisplayed()
    }
}
