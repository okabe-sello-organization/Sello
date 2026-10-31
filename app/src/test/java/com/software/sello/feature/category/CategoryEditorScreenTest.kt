package com.software.sello.feature.category

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.component.MoneySign
import com.software.sello.designsystem.component.MoneyTextValue
import com.software.sello.presentation.carry.CarryDirection
import com.software.sello.presentation.category.CategoryIcons
import com.software.sello.revealed
import com.software.sello.showSello
import com.software.sello.testing.pesos
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

    private fun show(state: CategoryEditorState) {
        rule.showSello(Look()) {
            CategoryEditorScreen(state, { actions += it })
        }
    }

    /** A field is found by its label, which is also what a screen reader calls it. */
    private val name get() = rule.onNodeWithContentDescription("Nombre")
    private val limit get() = rule.onNodeWithTag(EDITOR_LIMIT_TAG)

    /** A key of the Sello pad under the form. */
    private fun key(label: String) =
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(EDITOR_PAD_TAG)))

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

        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_MISSING_TAG).assertIsDisplayed()
        rule.onNodeWithText("Escribe un nombre.").assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().assertIsOff()
        limit.revealed().assertIsDisplayed()
        // The pad is there for the limit from the start; no field to open first.
        key("5").assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithText("200.000").revealed().assertIsDisplayed()
        // The chosen icon is in the circle beside the name, which says how to change it.
        rule.onNodeWithTag(EDITOR_ICON_TAG).assertContentDescriptionEquals("Ícono: Hogar. Cambiar")
        rule.onNodeWithTag(EDITOR_PREREQUISITE_TAG).assertDoesNotExist()
    }

    @Test
    fun withSinLimiteChosenTheAmountAndSuggestionsStayGreyedOutAndCannotBeUsed() {
        // An amount typed before the switch is kept, greyed out, and is not sent.
        show(
            ready.copy(
                name = "Mercado",
                unlimited = true,
                limitText = "450000",
                limitAmount = pesos("450.000")
            )
        )

        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().assertIsOn()
        rule.onNodeWithContentDescription("450.000 pesos").revealed().assertIsDisplayed()
        key("5").assertIsNotEnabled()
        for (suggestion in listOf("200.000 pesos", "300.000 pesos", "500.000 pesos")) {
            rule.onNodeWithContentDescription(suggestion).revealed().assertIsNotEnabled()
        }
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsEnabled().performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Submit), actions)
    }

    @Test
    fun theNameIsFocusedOnceTheNewFormIsReady() {
        var state by mutableStateOf(CategoryEditorState())
        rule.showSello { CategoryEditorScreen(state, {}) }
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

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).revealed().assertIsDisplayed()
            .assertContentDescriptionEquals("Tu límite total de octubre pasaría a 1.400.000 pesos")
    }

    @Test
    fun aTotalTheLimitDoesNotChangeIsSaidAsItIs() {
        show(editing.copy(limitTotal = total("1.200.000", changes = false)))

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).revealed()
            .assertContentDescriptionEquals("Tu límite total de octubre es 1.200.000 pesos")
    }

    @Test
    fun withNoTotalToShowThereIsNoLine() {
        show(ready)

        rule.onNodeWithTag(EDITOR_TOTAL_TAG).assertDoesNotExist()
    }

    @Test
    fun switchingSinLimiteOffAgainShowsTheAmountThatHadBeenTyped() {
        show(
            ready.copy(
                name = "Mercado",
                unlimited = false,
                limitText = "450000",
                limitAmount = pesos("450.000")
            )
        )

        rule.onNodeWithContentDescription("450.000 pesos").revealed().assertIsDisplayed()
        key("5").assertIsEnabled()
    }

    @Test
    fun anAmountThatIsMissingIsSaidOnTheFieldWhenCreatingWasTried() {
        show(ready.copy(name = "Mercado", limitError = LimitError.Required))

        // In the label's place, inside the limit's slip.
        rule.onNode(
            hasText("Escribe el límite o activa Sin límite.") and
                hasAnyAncestor(hasTestTag(EDITOR_LIMIT_TAG))
        ).revealed().assertIsDisplayed()
    }

    @Test
    fun everyControlReportsWhatWasDoneAndChangesNothingItself() {
        show(ready.copy(name = "Mer", unlimited = false))

        name.performTextInput("c")
        rule.onNodeWithTag(EDITOR_ICON_TAG).performClick()
        rule.onNodeWithContentDescription("Mascotas").performClick()
        // Choosing an icon closes the sheet.
        rule.onNodeWithTag(EDITOR_ICONS_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).revealed().performClick()
        key("5").performClick()
        key("000").performClick()
        rule.onNode(
            hasContentDescription("Borrar", substring = true) and
                hasAnyAncestor(hasTestTag(EDITOR_PAD_TAG))
        ).performClick()
        // A suggestion is read with its currency, not as a bare number.
        rule.onNodeWithContentDescription("300.000 pesos").revealed().performClick()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().performClick()
        rule.onNodeWithContentDescription("Volver").performClick()

        // The form is given the same state again, which is not a change, so the field
        // keeps what was typed; what matters is that the typed text was reported first,
        // as typed, after the name already there.
        assertEquals(CategoryEditorAction.NameChanged("Merc"), actions.first())
        assertEquals(
            listOf(
                CategoryEditorAction.IconPicked("pets"),
                CategoryEditorAction.UnlimitedChanged(true),
                CategoryEditorAction.LimitDigit(5),
                CategoryEditorAction.LimitTripleZero,
                CategoryEditorAction.LimitBackspace,
                CategoryEditorAction.SuggestionPicked(300_000),
                CategoryEditorAction.Submit,
                CategoryEditorAction.Back
            ),
            actions.filterNot { it is CategoryEditorAction.NameChanged }
        )
        name.assertTextContains("Merc")
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
            .revealed().assertIsDisplayed()
        rule.onNodeWithText("1e3").revealed().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SAVE_ERROR_TAG).revealed().assertIsDisplayed()
        // A name known to be taken cannot be sent (owner decision, SELLO-020): the
        // button waits for another name instead of offering a save that must fail.
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsNotEnabled()
    }

    @Test
    fun whileSavingNothingCanBeChangedOrSentAgain() {
        show(ready.copy(name = "Mercado", busy = true))

        name.assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_ICON_TAG).assertIsNotEnabled()
        key("5").assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().performClick()

        assertEquals(emptyList<CategoryEditorAction>(), actions)
    }

    @Test
    fun anUnknownOutcomeOffersToCheckNotToCreateAgain() {
        show(ready.copy(name = "Mercado", saveError = SaveError.Unknown))

        rule.onNodeWithText("Crear categoría").assertDoesNotExist()
        name.assertIsNotEnabled()
        rule.onNodeWithText("Comprobar si se guardó").revealed().performClick()

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
    fun theLimitIsTheAmountTheFormGivesAndItsSuggestionReadsTheSame() {
        show(ready.copy(unlimited = false, limitText = "500000", limitAmount = pesos("500.000")))

        rule.onNode(hasContentDescription("500.000 pesos") and !hasClickAction()).assertExists()
        rule.onNode(hasText("500.000") and hasClickAction()).revealed().assertIsSelected()
    }

    @Test
    fun aLimitThatIsNotAnAmountIsDrawnAsItCameBesideItsError() {
        show(ready.copy(unlimited = false, limitText = "1e3", limitError = LimitError.NotAnAmount))

        rule.onNodeWithText("1e3").revealed().assertIsDisplayed()
        rule.onNodeWithText("Escribe solo el número, por ejemplo 300000 o 300.000.")
            .revealed().assertIsDisplayed()
    }

    // --- Choosing the icon ---

    @Test
    fun theCircleOpensTheThirtyFiveIconsAndTheOnesInUseSaySoButCanBeChosen() {
        show(ready.copy(usedIconKeys = setOf("restaurant")))

        rule.onNodeWithTag(EDITOR_ICONS_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_ICON_TAG).performClick()

        rule.onNodeWithText("Elige un ícono").assertIsDisplayed()
        rule.onAllNodes(
            hasAnyAncestor(hasTestTag(EDITOR_ICONS_TAG)) and isSelectable()
        ).assertCountEquals(35)
        rule.onNodeWithContentDescription("Hogar").assertIsSelected()
        rule.onNodeWithContentDescription("Comida. Ya lo usa otra categoría; puedes repetirlo.")
            .assertIsEnabled().performClick()

        assertEquals(
            listOf<CategoryEditorAction>(CategoryEditorAction.IconPicked("restaurant")),
            actions
        )
    }

    // --- Changing an existing category ---

    private val october = java.time.YearMonth.of(2026, 10)

    /** "Café", limit 100.000, opened from October and not changed yet. */
    private val editing get() = ready.copy(
        editing = true,
        name = "Café",
        iconKey = "local_cafe",
        limitText = "100000",
        limitAmount = pesos("100.000"),
        targetMonth = october
    )

    @Test
    fun anExistingCategoryIsTitledAndWordedAsAChangeWithNothingToSaveYet() {
        show(editing)

        rule.onNodeWithText("Editar categoría").assertIsDisplayed()
        rule.onNodeWithText("Nueva categoría").assertDoesNotExist()
        rule.onNodeWithText("Crear categoría").assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsNotEnabled()
        rule.onNodeWithText("Guardar cambios").assertExists()
        rule.onNodeWithTag(EDITOR_NO_CHANGES_TAG).revealed().assertIsDisplayed()
        rule.onNodeWithText("Aún no cambiaste nada.").assertExists()
    }

    @Test
    fun onceSomethingChangedItCanBeSaved() {
        show(editing.copy(name = "Cafetería", changed = true))

        rule.onNodeWithTag(EDITOR_NO_CHANGES_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsEnabled().performClick()

        assertEquals(listOf<CategoryEditorAction>(CategoryEditorAction.Submit), actions)
    }

    @Test
    fun aNameAnotherCategoryHasIsSaidOnTheFieldAndCannotBeSaved() {
        show(editing.copy(name = "Mercado", changed = true, nameError = NameError.Taken))

        rule.onNodeWithText("Ya tienes una categoría con ese nombre.").assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsNotEnabled()
    }

    @Test
    fun inTheCurrentMonthItSaysTheLimitAppliesFromThisMonthOn() {
        show(editing)

        rule.onNodeWithTag(EDITOR_SCOPE_TAG).revealed().assertIsDisplayed()
        rule.onNodeWithText(
            "Vale desde Octubre 2026 en adelante. Los meses anteriores no cambian."
        ).assertExists()
    }

    @Test
    fun fromAFinishedMonthItNamesThatMonthAndSaysNothingElseChanges() {
        show(editing.copy(targetMonth = october.minusMonths(1), historical = true))

        rule.onNodeWithTag(EDITOR_SCOPE_TAG).revealed().assertIsDisplayed()
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

        rule.onNodeWithTag(EDITOR_SAVED_PARTS_TAG).revealed().assertIsDisplayed()
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
            .revealed().assertIsDisplayed()
    }

    @Test
    fun aConflictOffersToReloadOrToKeepWhatWasTypedAndNothingElse() {
        show(editing.copy(name = "Cafetería", changed = true, saveError = SaveError.Conflict))

        rule.onNodeWithText("Esta categoría cambió mientras la editabas.")
            .revealed().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_CONFLICT_RELOAD_TAG).revealed().performClick()
        rule.onNodeWithTag(EDITOR_CONFLICT_KEEP_TAG).revealed().performClick()

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
            .revealed().assertIsDisplayed()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).revealed().assertIsNotEnabled()
        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun anArchiveThatCouldNotRecordItsDuePaymentsFirstSaysSo() {
        show(editing.copy(saveError = SaveError.PaymentsPending))

        rule.onNodeWithText(
            "No se archivó: Sello no pudo anotar antes los pagos fijos de esta categoría " +
                "que ya tocaban. Inténtalo de nuevo."
        ).revealed().assertIsDisplayed()
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
        rule.onNodeWithTag(EDITOR_LIMIT_TAG).assertDoesNotExist()
        rule.onNodeWithTag(EDITOR_UNLIMITED_TAG).assertDoesNotExist()
        // Nothing to type: no pad either.
        rule.onNodeWithTag(EDITOR_PAD_TAG).assertDoesNotExist()
        rule.onNodeWithText(
            "Está archivada: no tiene límite de aquí en adelante. Desarchívala para ponerle uno."
        ).revealed().assertIsDisplayed()
    }

    @Test
    fun beforeUnarchivingItSaysWhetherTheBudgetComesBack() {
        var question by mutableStateOf(ArchiveQuestion(true, 0, october, true, false))
        rule.showSello {
            CategoryEditorScreen(
                editing.copy(name = "Mercado", archived = true, archiveQuestion = question),
                { actions += it }
            )
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

    private fun minus(digits: String) =
        MoneyTextValue(MoneySign.Minus, "", digits, "menos $digits pesos")

    private val carryOn = CarryCard.On(
        since = october.minusMonths(1),
        base = pesos("100.000"),
        carry = minus("40.000"),
        carryMagnitude = pesos("40.000"),
        direction = CarryDirection.Deficit,
        capacity = pesos("60.000")
    )

    @Test
    fun carryOffIsASwitchInTheLimitSlipThatAsksToTurnItOn() {
        show(editing.copy(carry = CarryCard.Off(october)))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assertIsOff().assertIsEnabled()
        rule.onNode(hasTestTag(EDITOR_CARRY_TAG) and hasAnyAncestor(hasTestTag(EDITOR_LIMIT_TAG)))
            .assertExists()
        rule.onNodeWithText("Pasar lo que sobre al mes siguiente").assertIsDisplayed()
        rule.onNodeWithText("Si te pasas, se descuenta del mes siguiente. Empieza en 0 este mes.")
            .assertIsDisplayed()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).performClick()
        assertEquals(listOf(CategoryEditorAction.EnableCarry), actions)
    }

    @Test
    fun carryOnSaysSinceWhenWhatItBringsAndThisMonthsLimitAndAsksToTurnItOff() {
        show(editing.copy(carry = carryOn))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assertIsOn()
        rule.onNodeWithText("Desde sep 2026 · trae −40.000 · este mes 60.000").assertIsDisplayed()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).performClick()
        assertEquals(listOf(CategoryEditorAction.DisableCarry), actions)
    }

    @Test
    fun carryCannotBeSwitchedWhileOtherChangesWaitToBeSaved() {
        show(editing.copy(carry = carryOn, changed = true))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assertIsOn().assertIsNotEnabled()
        rule.onNodeWithText("Guarda o descarta tus cambios para cambiar esto.", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("trae −40.000 · este mes 60.000", substring = true).assertIsDisplayed()
    }

    @Test
    fun anUnreadableCarryIsSaidWithoutASwitch() {
        show(editing.copy(carry = CarryCard.Unreadable))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assertDoesNotExist()
        rule.onNodeWithText(
            "No pudimos leer si esta categoría pasa lo que sobra."
        ).assertIsDisplayed()
    }

    @Test
    fun anArchivedCategoryStillShowsTheSwitchUnderItsLockedLimit() {
        show(editing.copy(archived = true, limitLocked = true, carry = CarryCard.Off(october)))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assertIsOff().assertIsDisplayed()
    }

    @Test
    fun aScreenReaderHearsTheCarryAmountsInWords() {
        show(editing.copy(carry = carryOn))
        rule.onNodeWithTag(EDITOR_CARRY_TAG).performScrollTo()

        rule.onNodeWithTag(EDITOR_CARRY_SWITCH_TAG).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf("Desde sep 2026. Trae menos 40.000 pesos. Límite de este mes: 60.000 pesos.")
            )
        )
    }
}
