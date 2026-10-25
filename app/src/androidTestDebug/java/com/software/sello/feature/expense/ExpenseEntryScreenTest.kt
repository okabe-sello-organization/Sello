package com.software.sello.feature.expense

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.presentation.date.dateLabels
import com.software.sello.presentation.recurrence.FollowingSchedule
import com.software.sello.presentation.recurrence.REPEAT_ROW_TAG
import com.software.sello.presentation.recurrence.REPEAT_SHEET_TAG
import com.software.sello.presentation.recurrence.RepeatAction
import com.software.sello.presentation.recurrence.RepeatChoice
import com.software.sello.presentation.recurrence.RepeatKind
import com.software.sello.presentation.recurrence.RepeatUi
import com.software.sello.revealed
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The form alone, given state by hand: what each state shows and what each control reports. */
@RunWith(AndroidJUnit4::class)
class ExpenseEntryScreenTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<ExpenseEntryAction>()
    private var pastExpenseAnnouncementDismissed = 0
    private val today = LocalDate.of(2026, 10, 9)
    private val food = EntryCategory("a", "Alimentación", "restaurant")
    private val coffee = EntryCategory("b", "Café", "local_cafe")

    private fun shown(digits: String) = MoneyTextValue(MoneySign.None, "", digits, "$digits pesos")

    private val empty = ExpenseEntryState(
        loading = false,
        categories = listOf(food, coffee),
        date = today,
        today = today
    )
    private val complete = empty.copy(
        amount = shown("48.700"),
        categoryId = "a",
        missing = null
    )
    private var created = 0

    private fun show(
        state: ExpenseEntryState,
        fontScale: Float = 1f,
        showPastExpenseAnnouncement: Boolean = false
    ) {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                SelloTheme(reducedMotion = true) {
                    ExpenseEntryScreen(
                        state = state,
                        dates = dateLabels(),
                        onAction = { actions += it },
                        onCreateCategory = { created++ },
                        showPastExpenseAnnouncement = showPastExpenseAnnouncement,
                        dismissPastExpenseAnnouncement = { pastExpenseAnnouncementDismissed++ }
                    )
                }
            }
        }
    }

    private fun key(label: String) =
        rule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(ENTRY_KEYPAD_TAG)))

    @Test
    fun anEmptyFormSaysTheAmountIsMissingOnTheSlipOnlyAndCannotBeSubmitted() {
        show(empty)

        rule.onNodeWithText("Gasto en pesos").assertIsDisplayed()
        rule.onNodeWithText("Hoy, vie 9 oct").assertIsDisplayed()
        // Nothing is chosen for the person.
        rule.onNode(hasText("Alimentación") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .assertIsNotSelected()
        // The slip already says there is no amount; no second line repeats it by the keypad.
        rule.onNodeWithContentDescription("Sin monto").assertIsDisplayed()
        rule.onNodeWithText("Escribe un monto.").assertDoesNotExist()
        rule.onNodeWithTag(ENTRY_MISSING_TAG).assertDoesNotExist()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()
        // As in v2, nothing previews the month before saving.
        rule.onNodeWithText("Vista previa, si lo anotas").assertDoesNotExist()
    }

    @Test
    fun everyControlReportsWhatWasDone() {
        show(complete)

        key("7").performClick()
        key("000").performClick()
        rule.onNodeWithText("Café").performScrollTo().performClick()
        rule.onNodeWithTag(ENTRY_DATE_TAG).revealed().performClick()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).performClick()
        rule.onNodeWithContentDescription("Cerrar").performClick()

        assertEquals(
            listOf(
                ExpenseEntryAction.Digit(7),
                ExpenseEntryAction.TripleZero,
                ExpenseEntryAction.CategoryPicked("b"),
                ExpenseEntryAction.OpenDatePicker,
                ExpenseEntryAction.Submit,
                ExpenseEntryAction.Back
            ),
            actions
        )
    }

    @Test
    fun theAmountIsReadInFullTheChipsAreOneChoiceAndTheButtonSaysTheWholeSentence() {
        show(complete)

        rule.onNodeWithContentDescription("48.700 pesos").assertIsDisplayed()
        rule.onNode(hasText("Alimentación") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .performScrollTo().assertIsSelected()
        rule.onNode(hasText("Café") and hasAnyAncestor(hasTestTag(ENTRY_CATEGORIES_TAG)))
            .assertIsNotSelected()
        rule.onNodeWithContentDescription("Anotar gasto de 48.700 pesos en Alimentación")
            .assertIsDisplayed().assertIsEnabled()
        rule.onNodeWithContentDescription("Fecha: Hoy, vie 9 oct").assertExists()
    }

    @Test
    fun nuevaEndsTheChipsAndOpensTheCategoryEditor() {
        show(complete)

        val chips = rule.onNodeWithTag(ENTRY_CATEGORIES_TAG).getUnclippedBoundsInRoot()
        val nueva = rule.onNodeWithTag(ENTRY_NEW_CATEGORY_TAG).performScrollTo()
            .assertHasClickAction()
        val shown = nueva.getUnclippedBoundsInRoot()
        assertTrue("$shown in $chips", shown.top >= chips.top && shown.bottom <= chips.bottom)
        nueva.performClick()

        assertEquals(1, created)
        assertEquals(emptyList<ExpenseEntryAction>(), actions)
    }

    @Test
    fun theNoteRowOpensTheComposerAndTheComposerReportsWhatIsWritten() {
        show(complete.copy(note = "Pan"))

        rule.onNodeWithTag(ENTRY_NOTE_TAG).performScrollTo().assertTextContains("Pan")
            .assertTextContains("Editar").performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.OpenNote), actions)
    }

    @Test
    fun anOpenComposerSaysWhatTheNoteBelongsToAndReportsItsButtons() {
        show(complete.copy(note = "Pan", noteDraft = "Pan", noteDraftLength = 3))

        rule.onNodeWithText("3/60").assertIsDisplayed()
        rule.onNodeWithText("Opcional").assertIsDisplayed()
        rule.onNodeWithContentDescription(
            "Nota del gasto en Alimentación, 48.700 pesos"
        ).assertExists()
            .performTextInput("y")
        rule.onNodeWithContentDescription("Listo").performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertEquals(
            listOf(
                ExpenseEntryAction.NoteDraftChanged("Pany"),
                ExpenseEntryAction.NoteDone,
                ExpenseEntryAction.NoteCancelled
            ),
            actions
        )
    }

    @Test
    fun overSixtyCharactersTheComposerKeepsTheTextSaysSoAndCannotBeFinished() {
        val long = "a".repeat(61)
        show(
            complete.copy(
                noteDraft = long,
                noteDraftLength = 61,
                noteDraftProblem = NoteProblem.TooLong
            )
        )

        rule.onNodeWithText(long).assertIsDisplayed()
        rule.onNodeWithText("61/60").assertIsDisplayed()
        rule.onNodeWithText("Máximo 60 caracteres. Tu texto sigue aquí.").assertIsDisplayed()
        rule.onNodeWithContentDescription("Listo").assertIsNotEnabled().performClick()

        assertEquals(emptyList<ExpenseEntryAction>(), actions)
    }

    @Test
    fun aThirteenthDigitSaysMaximoDoceDigitosInPlaceOfTheLabel() {
        show(complete.copy(amount = shown("999.999.999.999"), atLimit = true, limitPulse = 1))

        rule.onNodeWithText("Máximo 12 dígitos").assertIsDisplayed()
        rule.onNodeWithText("Gasto en pesos").assertDoesNotExist()
        rule.onNodeWithContentDescription("999.999.999.999 pesos").assertIsDisplayed()
    }

    @Test
    fun aRefusedPasteIsShownAsItWasWithTheReason() {
        show(empty.copy(amountText = "1e3", amountProblem = AmountProblem.NotAnAmount))

        rule.onNodeWithText("1e3", substring = true).assertIsDisplayed()
        rule.onNodeWithText(
            "Eso no es un monto. Escribe solo el número, por ejemplo 48700 o 48.700."
        ).assertIsDisplayed()
    }

    @Test
    fun whileSavingNothingCanBePressed() {
        show(complete.copy(busy = true))

        key("7").performClick()
        rule.onNodeWithText("Café").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).performClick()

        assertEquals(emptyList<ExpenseEntryAction>(), actions)
    }

    @Test
    fun eachWayOfNotSavingSaysThatNothingWasRecorded() {
        show(
            complete.copy(
                error = EntryError.CategoryGone,
                categoryId = null,
                missing = Missing.Category
            )
        )

        // Said above the button, with the pad, outside anything that scrolls.
        rule.onNodeWithText("Esa categoría ya no acepta gastos. Elige otra; no se anotó nada.")
            .assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()
    }

    @Test
    fun aDateThatCannotBeStoredIsSaidAsItsOwnReasonNotAsTheFuture() {
        show(complete.copy(error = EntryError.InvalidDate))

        rule.onNodeWithText("Esa fecha no es válida. No se anotó nada.").assertIsDisplayed()
        rule.onNodeWithText("La fecha no puede ser posterior a hoy. No se anotó nada.")
            .assertDoesNotExist()
    }

    @Test
    fun anUnknownOutcomeOffersToCheckNotToRecordAgain() {
        show(complete.copy(error = EntryError.Unknown))

        rule.onNodeWithText("Anotar gasto").assertDoesNotExist()
        rule.onNodeWithText("Comprobar si se guardó").assertIsDisplayed().performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.Retry), actions)
    }

    @Test
    fun aRecordedSaveDrawsNoReceiptAndOffersNothingMoreToSend() {
        // The moment between the confirmed save and the form closing.
        show(complete.copy(recorded = "e", busy = true))

        rule.onNodeWithText("Gasto anotado").assertDoesNotExist()
        rule.onNodeWithText("Recibido", ignoreCase = true).assertDoesNotExist()
        rule.onNodeWithText("Listo").assertDoesNotExist()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).performClick()

        assertEquals(emptyList<ExpenseEntryAction>(), actions)
    }

    @Test
    fun discardingIsAskedForAndEitherAnswerIsReported() {
        show(complete.copy(confirmDiscard = true))

        rule.onNodeWithText("¿Descartar este gasto?").assertIsDisplayed()
        rule.onNodeWithText("Seguir anotando").performClick()
        rule.onNodeWithText("Descartar").performClick()

        assertEquals(
            listOf(ExpenseEntryAction.KeepEditing, ExpenseEntryAction.DiscardConfirmed),
            actions
        )
    }

    @Test
    fun whenCategoriesCannotBeReadThereIsNoFormAndRetryIsOffered() {
        show(empty.copy(loadFailed = true))

        rule.onNodeWithTag(ENTRY_LOAD_FAILED_TAG).assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertDoesNotExist()
        rule.onNodeWithText("Reintentar").performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.Retry), actions)
    }

    @Test
    fun atTwiceTheFontSizeEveryControlCanStillBeReached() {
        show(complete, fontScale = 2f)

        rule.onNodeWithContentDescription("48.700 pesos").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Café").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_DATE_TAG).revealed().assertIsDisplayed()
        key("000").assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsDisplayed().assertIsEnabled()
    }

    // --- Correcting a recorded expense ---

    /** A recorded expense of Monday 5 October, opened and not changed yet. */
    private val correcting get() = complete.copy(
        editing = true,
        date = LocalDate.of(2026, 10, 5),
        missing = Missing.Changes
    )

    @Test
    fun aRecordedExpenseIsTitledAndWordedAsACorrectionWithNothingToSaveYet() {
        show(correcting)

        rule.onNodeWithText("Editar gasto").assertIsDisplayed()
        rule.onNodeWithText("lun 5 oct").assertIsDisplayed()
        // A correction keeps its kind and category: no switch and no Nueva.
        rule.onNodeWithTag("entry.kind").assertDoesNotExist()
        rule.onNodeWithTag(ENTRY_NEW_CATEGORY_TAG).assertDoesNotExist()
        rule.onNodeWithText("Guardar cambios").assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ENTRY_MISSING_TAG).assertIsDisplayed()
        rule.onNodeWithText("Aún no cambiaste nada.").assertExists()
        rule.onNodeWithText("Anotar gasto").assertDoesNotExist()
        rule.onNodeWithText("Anotar").assertDoesNotExist()
        rule.onNodeWithTag(ENTRY_PAST_MONTHS_TAG).assertDoesNotExist()
    }

    @Test
    fun onceSomethingChangedTheButtonSaysWhatWillBeSavedAndReportsIt() {
        show(correcting.copy(missing = null))

        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Guardar cambios: gasto de 48.700 pesos en Alimentación")
                )
            )
            .performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.Submit), actions)
    }

    @Test
    fun aRecordedExpenseCanBeDeletedFromTheTopBarAndANewOneCannot() {
        show(correcting)

        rule.onNodeWithTag(ENTRY_DELETE_TAG).assertIsDisplayed().assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf("Borrar este gasto")
                )
            )
            .performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.Delete), actions)
    }

    @Test
    fun aNewExpenseHasNothingToDelete() {
        show(complete)

        rule.onNodeWithTag(ENTRY_DELETE_TAG).assertDoesNotExist()
    }

    @Test
    fun itSaysWhichOtherMonthsFiguresWouldChangeBeforeAnythingIsSaved() {
        val september = java.time.YearMonth.of(2026, 9)
        show(correcting.copy(pastMonths = listOf(september, september.minusMonths(1))))

        rule.onNodeWithTag(ENTRY_PAST_MONTHS_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Corregir este gasto cambia las cifras de septiembre y de agosto.")
            .assertExists()
    }

    @Test
    fun withOneOtherMonthItNamesThatMonth() {
        show(correcting.copy(pastMonths = listOf(java.time.YearMonth.of(2026, 9))))

        rule.onNodeWithText("Corregir este gasto cambia las cifras de septiembre.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun firstTimePastExpenseNoticeCanBeDismissedWithoutRemovingThePermanentGuidance() {
        show(
            state = correcting.copy(pastMonths = listOf(java.time.YearMonth.of(2026, 9))),
            showPastExpenseAnnouncement = true
        )

        rule.onNodeWithTag(ENTRY_PAST_MONTHS_TAG).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_PAST_EXPENSE_ANNOUNCEMENT_TAG)
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Entendido").performScrollTo().performClick()

        assertEquals(1, pastExpenseAnnouncementDismissed)
        rule.onNodeWithTag(ENTRY_PAST_MONTHS_TAG).assertIsDisplayed()
    }

    @Test
    fun anExpenseThatChangedUnderneathIsSaidAndNothingCanBeSentOrDeletedFromHere() {
        show(correcting.copy(missing = null, error = EntryError.ExpenseChanged))

        rule.onNodeWithText(
            "Este gasto cambió mientras lo corregías. No se guardó nada; sal y vuelve a abrirlo."
        ).assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_SUBMIT_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(ENTRY_DELETE_TAG).assertIsNotEnabled()
        key("7").assertIsNotEnabled()
    }

    @Test
    fun aRefusedCorrectionSaysTheExpenseWasLeftAsItWasNotThatNothingWasRecorded() {
        show(correcting.copy(missing = null, error = EntryError.NotSaved))

        rule.onNodeWithText("No se pudo guardar. El gasto quedó como estaba; inténtalo de nuevo.")
            .assertIsDisplayed()
        rule.onNodeWithText("No se pudo guardar. No se anotó el gasto; inténtalo de nuevo.")
            .assertDoesNotExist()
    }

    @Test
    fun leavingWithChangesAsksAboutTheChanges() {
        show(correcting.copy(missing = null, confirmDiscard = true))

        rule.onNodeWithText("¿Descartar los cambios?").assertIsDisplayed()
        rule.onNodeWithText("El gasto queda como estaba.").assertIsDisplayed()
        rule.onNodeWithText("Seguir editando").performClick()

        assertEquals(listOf<ExpenseEntryAction>(ExpenseEntryAction.KeepEditing), actions)
    }

    @Test
    fun anExpenseThatCouldNotBeReadSaysSoAndCanBeAskedForAgain() {
        show(correcting.copy(loadFailed = true))

        rule.onNodeWithText("No pudimos leer este gasto.").assertIsDisplayed()
        rule.onNodeWithTag(ENTRY_DELETE_TAG).assertDoesNotExist()
    }

    // --- Se repite and Solo este (SELLO-100) ---

    private fun monthly(day: Int) =
        RepeatChoice.of((RepeatRule.Monthly.of(day) as Outcome.Success).value, null)

    @Test
    fun seRepiteIsARowForANewExpenseOnlyAndOpensTheSheet() {
        show(complete)
        rule.onNodeWithTag(REPEAT_ROW_TAG).performScrollTo().assertIsDisplayed().performClick()

        assertEquals(ExpenseEntryAction.Repeat(RepeatAction.Open), actions.last())
        rule.onNodeWithText("Anotar gasto").assertExists()
    }

    @Test
    fun aKeptChoiceNamesTheRuleAndTheButtonSaysItKeepsAPayment() {
        // jue 5 nov is next for the 5th from vie 9 oct.
        show(
            complete.copy(repeat = RepeatUi(choice = monthly(5), next = LocalDate.of(2026, 11, 5)))
        )

        rule.onNodeWithText(
            "El 5 de cada mes · próximo jue 5 nov"
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Guardar pago fijo").assertExists()
    }

    @Test
    fun theSheetWaitsForAChoiceThatRecordsSomethingAndQuitarTurnsItOff() {
        val twiceWithOne = RepeatChoice.of(
            (RepeatRule.Monthly.of(9) as Outcome.Success).value,
            null
        )
            .withKind(RepeatKind.TwiceMonthly)
        show(complete.copy(repeat = RepeatUi(sheet = twiceWithOne, sheetNext = null)))

        rule.onNode(
            hasText("Listo") and hasAnyAncestor(hasTestTag(REPEAT_SHEET_TAG))
        ).assertIsNotEnabled()
        rule.onNode(hasText("Quitar") and hasAnyAncestor(hasTestTag(REPEAT_SHEET_TAG)))
            .performScrollTo().performClick()
        rule.onNodeWithContentDescription("Día 15").performScrollTo().performClick()

        assertEquals(
            listOf(
                ExpenseEntryAction.Repeat(RepeatAction.Remove),
                ExpenseEntryAction.Repeat(RepeatAction.DayToggled(15))
            ),
            actions
        )
    }

    @Test
    fun newTermsOnARecordedPaymentAskSoloEsteOrEsteYLosSiguientes() {
        show(
            complete.copy(
                editing = true,
                askFollowing = true,
                following = FollowingSchedule(
                    "Arriendo",
                    (RepeatRule.Monthly.of(5) as Outcome.Success).value,
                    today
                )
            )
        )

        rule.onNodeWithText("¿Cambiar también los siguientes?").assertIsDisplayed()
        rule.onNodeWithTag(REPEAT_ROW_TAG).assertDoesNotExist()
        rule.onNodeWithText("Este y los siguientes").performClick()
        rule.onNodeWithText("Solo este").performClick()
        rule.onNodeWithText("Volver a editar").performClick()

        assertEquals(
            listOf(
                ExpenseEntryAction.FollowingChosen(true),
                ExpenseEntryAction.FollowingChosen(false),
                ExpenseEntryAction.FollowingDismissed
            ),
            actions
        )
    }
}
