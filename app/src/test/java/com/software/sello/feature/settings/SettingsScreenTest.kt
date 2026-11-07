package com.software.sello.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.platform.PhoneColor
import com.software.sello.revealed
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Configuración drawn from state given by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tusDatosComesFirstThenLoQueSeRepiteAndThereIsNoAvisos() {
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = false),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {}
            ) { Text("Tus datos") }
        }

        val tops = listOf(
            SETTINGS_DATA_TAG,
            SETTINGS_RECURRING_TAG,
            SETTINGS_APPEARANCE_TAG,
            SETTINGS_ORDERING_TAG,
            SETTINGS_SUMMARY_TAG,
            SETTINGS_INVESTMENTS_TAG
        ).map { rule.onNodeWithTag(it).getUnclippedBoundsInRoot().top }
        assertEquals(tops.sorted(), tops)
        assertEquals(0, rule.onAllNodes(hasText("Avisos")).fetchSemanticsNodes().size)
    }

    @Test
    fun resumensRowSaysGastosHormigaItsLineAndOpensItsForm() {
        var opened = 0
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = false, smallExpenseLine = "20.000"),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {},
                onOpenSmallExpenses = { opened++ }
            ) { Text("Tus datos") }
        }

        rule.onNodeWithTag(SETTINGS_SUMMARY_TAG).revealed()
        rule.onNodeWithText("Resumen").assertIsDisplayed()
        rule.onNodeWithText("Gastos hormiga").assertIsDisplayed()
        rule.onNodeWithText("Los que suma como pequeños").assertIsDisplayed()
        rule.onNodeWithText("Hasta 20.000").assertIsDisplayed()
        rule.onNodeWithTag(SETTINGS_SMALL_EXPENSES_TAG).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun aLineNeverReadShowsNoValueInItsPlace() {
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = false),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {}
            ) { Text("Tus datos") }
        }

        rule.onNodeWithTag(SETTINGS_SMALL_EXPENSES_TAG).revealed()
        val values = rule.onAllNodes(hasText("Hasta", substring = true)).fetchSemanticsNodes()
        assertEquals(0, values.size)
    }

    private val red = AppearancePreference(ink = InkPreference.Tuya(InkHue.of(22)!!))

    private fun showSettings(state: SettingsState, actions: MutableList<SettingsAction>) {
        rule.showSello {
            Box {
                SettingsScreen(
                    state = state,
                    onAction = { actions += it },
                    contentPadding = PaddingValues(),
                    onOpenSchedules = {},
                    onOpenRate = {}
                ) { Text("Tus datos") }
                state.tuya?.let {
                    TuyaSheet(it, state.appearance.ink) { action -> actions += action }
                }
            }
        }
    }

    @Test
    fun tuyaComesAfterTheHandMadeInksAndTappingItOpensTuTinta() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false), actions)

        val names = listOf("Cobalto", "Violeta", "Tuya").map {
            rule.onNodeWithText(it).getUnclippedBoundsInRoot().left
        }
        assertEquals(names.sorted(), names)
        rule.onNodeWithText("Tuya").assertIsNotSelected().performClick()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertDoesNotExist()

        assertEquals(listOf<SettingsAction>(SettingsAction.Tuya.Open), actions)
    }

    @Test
    fun chosenTuyaIsSelectedAndNamesItsColour() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, appearance = red), actions)

        rule.onNodeWithText("Tuya").assertIsSelected()
        rule.onNodeWithText("Cobalto").assertIsNotSelected()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG)
            .assertTextEquals("Rojo. Toca Tuya para cambiarla.")
        rule.onNodeWithText("Tuya").performClick()
        assertEquals(listOf<SettingsAction>(SettingsAction.Tuya.Open), actions)
    }

    @Test
    fun aKeptTuyaHueStaysOnItsSwatchUnchosenWhileAnotherInkIsInUse() {
        val actions = mutableListOf<SettingsAction>()
        val kept = AppearancePreference(ink = InkPreference.Cobalto, tuyaHue = InkHue.of(22)!!)
        showSettings(SettingsState(loading = false, appearance = kept), actions)

        rule.onNodeWithText("Cobalto").assertIsSelected()
        rule.onNodeWithText("Tuya").assertIsNotSelected().performClick()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertDoesNotExist()
        assertEquals(listOf<SettingsAction>(SettingsAction.Tuya.Open), actions)
    }

    @Test
    fun theSliderIsReadAsTheColoursNameAndTalkBackCanMoveIt() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, tuya = TuyaDraft(hue = 200)), actions)

        rule.onNodeWithTag(TUYA_SLIDER_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Petróleo"))
            .assertContentDescriptionEquals("Tono de tu tinta")
            .assertRangeInfoEquals(ProgressBarRangeInfo(200f, 0f..359f))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(218f) }
        // A step past an end stops at it.
        rule.onNodeWithTag(TUYA_SLIDER_TAG)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(380f) }

        assertEquals(
            listOf<SettingsAction>(
                SettingsAction.Tuya.HueChanged(218),
                SettingsAction.Tuya.HueChanged(359)
            ),
            actions
        )
        rule.onNodeWithTag(TUYA_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun dragingTheSliderToItsEndsGivesTheFirstAndLastHue() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, tuya = TuyaDraft(hue = 200)), actions)

        rule.onNodeWithTag(TUYA_SLIDER_TAG).performTouchInput { click(centerLeft) }
        rule.onNodeWithTag(TUYA_SLIDER_TAG).performTouchInput { click(centerRight) }

        assertEquals(
            listOf<SettingsAction>(
                SettingsAction.Tuya.HueChanged(0),
                SettingsAction.Tuya.HueChanged(359)
            ),
            actions
        )
    }

    @Test
    fun aRedOrGreenHueAddsTheNoteAndCanStillBeUsed() {
        val actions = mutableListOf<SettingsAction>()
        var hue by mutableIntStateOf(0)
        rule.showSello {
            TuyaSheet(TuyaDraft(hue), InkPreference.Cobalto) { actions += it }
        }
        for (each in listOf(0, 22, 49, 50, 121, 122, 147, 195, 196, 267)) {
            hue = each
            rule.waitForIdle()
            actions.clear()

            if (each in 0..49 || each in 122..195) {
                rule.onNodeWithTag(TUYA_NOTE_TAG)
                    .assertTextEquals("El rojo y el verde también marcan excesos y metas.")
            } else {
                rule.onNodeWithTag(TUYA_NOTE_TAG).assertDoesNotExist()
            }
            rule.onNodeWithTag(TUYA_USE_TAG).assertIsEnabled().performClick()
            assertEquals("$each", listOf<SettingsAction>(SettingsAction.Tuya.Use), actions)
        }
    }

    @Test
    fun aFailedSaveSaysTheInkInUseAndOffersReintentar() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(
            SettingsState(
                loading = false,
                appearance = AppearancePreference(ink = InkPreference.Violeta),
                tuya = TuyaDraft(hue = 22, failed = true)
            ),
            actions
        )

        rule.onNodeWithText("No se pudo guardar. Sigues con Violeta.").assertIsDisplayed()
        rule.onNodeWithText("Reintentar").performClick()
        rule.onNodeWithText("Cancelar").performClick()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.Tuya.Use, SettingsAction.Tuya.Cancel),
            actions
        )
    }

    @Test
    fun aFailedSaveOverTuyaNamesTheTuyaStillInUse() {
        showSettings(
            SettingsState(loading = false, appearance = red, tuya = TuyaDraft(147, failed = true)),
            mutableListOf()
        )

        rule.onNodeWithText("No se pudo guardar. Sigues con Tuya (Rojo).").assertIsDisplayed()
    }

    @Test
    fun whileSavingOnlyTheBusyButtonShowsAndNothingCanBeChanged() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(
            SettingsState(loading = false, busy = true, tuya = TuyaDraft(22, saving = true)),
            actions
        )

        rule.onNodeWithTag(TUYA_CANCEL_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(TUYA_SLIDER_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(TUYA_SLIDER_TAG).performTouchInput { click(centerLeft) }
        rule.onNodeWithTag(TUYA_USE_TAG).performClick()

        assertEquals(emptyList<SettingsAction>(), actions)
    }

    // SELLO-203: Sistema.

    private val sistema = AppearancePreference(ink = InkPreference.Sistema)

    /** The ink swatch named [name]: Tema's switch has a Sistema too. */
    private fun ink(name: String) =
        rule.onNode(hasText(name) and hasAnyAncestor(hasTestTag(SETTINGS_INK_TAG)))

    @Test
    fun sistemaSitsBetweenVioletaAndTuyaAndAppliesAtOnce() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, phone = PhoneColor.Hue(220)), actions)

        val names = listOf("Cobalto", "Violeta", "Sistema", "Tuya").map {
            ink(it).getUnclippedBoundsInRoot().left
        }
        assertEquals(names.sorted(), names)
        ink("Sistema").assertIsNotSelected().performClick()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertDoesNotExist()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.InkChosen(InkPreference.Sistema)),
            actions
        )
    }

    @Test
    fun chosenSistemaNamesThePhonesColourInLowerCase() {
        val actions = mutableListOf<SettingsAction>()
        val state =
            SettingsState(loading = false, appearance = sistema, phone = PhoneColor.Hue(220))
        showSettings(state, actions)

        ink("Sistema").assertIsSelected()
        ink("Cobalto").assertIsNotSelected()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertTextEquals(
            "Toma el color de tu fondo de pantalla: petróleo. Cambia cuando lo cambies."
        )
    }

    @Test
    fun onARedOrGreenPhoneColourSistemasCaptionAddsTheNote() {
        var phoneHue by mutableIntStateOf(24)
        rule.showSello {
            SettingsScreen(
                state = SettingsState(
                    loading = false,
                    appearance = sistema,
                    phone = PhoneColor.Hue(phoneHue)
                ),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {}
            ) { Text("Tus datos") }
        }
        for ((hue, name) in listOf(24 to "rojo", 140 to "verde")) {
            phoneHue = hue
            rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertTextEquals(
                "Toma el color de tu fondo de pantalla: $name. Cambia cuando lo cambies. " +
                    "El rojo y el verde también marcan excesos y metas."
            )
        }
    }

    @Test
    fun onAndroid11SistemaIsNotOfferedAndARestoredOneShowsCobaltoChosen() {
        val actions = mutableListOf<SettingsAction>()
        val state = SettingsState(loading = false, appearance = sistema, phone = PhoneColor.None)
        showSettings(state, actions)

        ink("Sistema").assertDoesNotExist()
        ink("Cobalto").assertIsSelected()
        ink("Tuya").assertIsNotSelected()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertDoesNotExist()
        assertEquals(emptyList<SettingsAction>(), actions)
    }

    @Test
    fun aGreyAccentKeepsSistemaChosenInCobaltoAndSaysSo() {
        val actions = mutableListOf<SettingsAction>()
        val state = SettingsState(loading = false, appearance = sistema, phone = PhoneColor.Grey)
        showSettings(state, actions)

        ink("Sistema").assertIsSelected()
        ink("Cobalto").assertIsNotSelected()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertTextEquals(
            "Tu teléfono no tiene un color de acento: Sello usa Cobalto mientras tanto."
        )
    }

    @Test
    fun aGreyAccentStillOffersSistema() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, phone = PhoneColor.Grey), actions)

        ink("Sistema").assertIsNotSelected().performClick()
        assertEquals(
            listOf<SettingsAction>(SettingsAction.InkChosen(InkPreference.Sistema)),
            actions
        )
    }
}
