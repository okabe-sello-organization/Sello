package com.software.sello.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
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
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.TuyaColour
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
    fun theGuideComesFirstThenTusDatosThenLoQueSeRepiteAndThereIsNoAvisos() {
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
            SETTINGS_GUIDE_TAG,
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
    fun theGuidesRowNamesItAndOpensItEvenWhileSettingsLoad() {
        var opened = 0
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = true),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {},
                onOpenGuide = { opened++ }
            ) { Text("Tus datos") }
        }

        rule.onNodeWithText("Guía de Sello").assertIsDisplayed()
        rule.onNodeWithText("Cómo funciona cada parte de Sello").assertIsDisplayed()
        rule.onNodeWithTag(SETTINGS_GUIDE_TAG).performClick()

        assertEquals(1, opened)
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
    fun gastosHormigasLineIsSpokenAsAnAmountInPesos() {
        rule.showSello {
            SettingsScreen(
                state = SettingsState(loading = false, smallExpenseLine = "20.000"),
                onAction = {},
                contentPadding = PaddingValues(),
                onOpenSchedules = {},
                onOpenRate = {}
            ) { Text("Tus datos") }
        }

        // As the UI sweep reads a row: what a screen reader says for the whole button.
        val row = rule.onNodeWithTag(SETTINGS_SMALL_EXPENSES_TAG).revealed().fetchSemanticsNode()
        val spoken = row.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertEquals(listOf("Hasta 20.000 pesos"), spoken)
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

    private val red =
        AppearancePreference(ink = InkPreference.Tuya(TuyaColour.Hue(InkHue.of(22)!!)))

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
        val kept =
            AppearancePreference(
                ink = InkPreference.Cobalto,
                tuya = TuyaColour.Hue(InkHue.of(22)!!)
            )
        showSettings(SettingsState(loading = false, appearance = kept), actions)

        rule.onNodeWithText("Cobalto").assertIsSelected()
        rule.onNodeWithText("Tuya").assertIsNotSelected().performClick()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG).assertDoesNotExist()
        assertEquals(listOf<SettingsAction>(SettingsAction.Tuya.Open), actions)
    }

    @Test
    fun chosenExactTuyaNamesItsColour() {
        val grey = AppearancePreference(
            ink = InkPreference.Tuya(TuyaColour.Exact(InkColour.of(0x5F6368)!!))
        )
        showSettings(SettingsState(loading = false, appearance = grey), mutableListOf())

        rule.onNodeWithText("Tuya").assertIsSelected()
        rule.onNodeWithTag(SETTINGS_INK_NOTE_TAG)
            .assertTextEquals("Gris. Toca Tuya para cambiarla.")
    }

    // SELLO-216: Tu tinta with any colour.

    /** The sheet on [rgb], opened from Cobalto. */
    private fun draft(
        rgb: Int,
        code: String = codeOf(rgb),
        saving: Boolean = false,
        failed: Boolean = false
    ) = TuyaDraft(
        colour = InkColour.of(rgb.toLong())!!,
        pick = TuyaPick.of(rgb, 0f),
        code = code,
        before = InkColour.of(0x1F3BE0)!!,
        saving = saving,
        failed = failed
    )

    @Test
    fun theSheetNamesTheColourShowsItsCodeAndAntesAndAhora() {
        showSettings(SettingsState(loading = false, tuya = draft(0x5F6368)), mutableListOf())

        rule.onNodeWithTag(TUYA_NAME_TAG).assertTextEquals("Gris")
        rule.onNodeWithTag(TUYA_CODE_TAG)
            .assertTextEquals("#5F6368")
            .assertContentDescriptionEquals("Código del color")
        rule.onNodeWithText("Antes · Cobalto").assertIsDisplayed()
        rule.onNodeWithText("Ahora · Gris").assertIsDisplayed()
    }

    @Test
    fun typingACodeSendsItAsTypedAndListoLeavesTheField() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, tuya = draft(0x5F6368)), actions)

        rule.onNodeWithTag(TUYA_CODE_TAG).performClick().performTextReplacement("c72842")
        rule.onNodeWithTag(TUYA_CODE_TAG).performImeAction()

        assertEquals(
            listOf(SettingsAction.Tuya.CodeTyped("c72842"), SettingsAction.Tuya.CodeLeft),
            actions
        )
    }

    @Test
    fun theSquareAndTheBarSendWhereTheyAreTouchedAndAntesGoesBack() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(SettingsState(loading = false, tuya = draft(0x5F6368)), actions)

        rule.onNodeWithTag(TUYA_SQUARE_TAG).revealed().performTouchInput { click(topLeft) }
        rule.onNodeWithTag(TUYA_SQUARE_TAG).performTouchInput { click(center) }
        rule.onNodeWithTag(TUYA_BAR_TAG).revealed().performTouchInput { click(centerLeft) }
        rule.onNodeWithTag(TUYA_BAR_TAG).performTouchInput { click(center) }
        rule.onNodeWithTag(TUYA_BEFORE_TAG).revealed().performClick()

        assertEquals(
            listOf(
                SettingsAction.Tuya.SquareMoved(saturation = 0f, value = 1f),
                SettingsAction.Tuya.SquareMoved(saturation = 0.5f, value = 0.5f),
                SettingsAction.Tuya.BarMoved(0f),
                SettingsAction.Tuya.BarMoved(180f),
                SettingsAction.Tuya.BeforeTapped
            ),
            actions
        )
    }

    @Test
    fun theLineHoldsTheHintOrTheNoteAtOneHeightAndEveryColourCanBeUsed() {
        val actions = mutableListOf<SettingsAction>()
        var rgb by mutableIntStateOf(0x5F6368)
        rule.showSello {
            TuyaSheet(draft(rgb), InkPreference.Cobalto) { actions += it }
        }
        val hint = "Arrastra para elegir, o toca el código para escribir uno exacto."
        val heights = mutableSetOf<Float>()
        // Grey, yellow and blue get the hint; the mock's red and green get their notes.
        for ((each, line) in listOf(
            0x5F6368 to hint,
            0xF4C430 to hint,
            0x1F3BE0 to hint,
            0xC62828 to "Se parece al rojo que marca los excesos.",
            0x2E7D32 to "Se parece al verde de las metas y de lo que va bien."
        )) {
            rgb = each
            rule.waitForIdle()
            actions.clear()
            val tag = if (line == hint) TUYA_HINT_TAG else TUYA_NOTE_TAG
            rule.onNodeWithTag(tag).revealed().assertTextEquals(line)
            rule.onNodeWithTag(if (line == hint) TUYA_NOTE_TAG else TUYA_HINT_TAG)
                .assertDoesNotExist()
            heights +=
                rule.onNodeWithTag(TUYA_LINE_TAG).getUnclippedBoundsInRoot().run {
                    bottom - top
                }.value
            rule.onNodeWithTag(TUYA_USE_TAG).revealed().assertIsEnabled().performClick()
            assertEquals(
                "%06X".format(each),
                listOf<SettingsAction>(SettingsAction.Tuya.Use),
                actions
            )
        }
        assertEquals(1, heights.size)
    }

    @Test
    fun aFailedSaveSaysTheInkInUseAndOffersReintentar() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(
            SettingsState(
                loading = false,
                appearance = AppearancePreference(ink = InkPreference.Violeta),
                tuya = draft(0xC72842, failed = true)
            ),
            actions
        )

        rule.onNodeWithText(
            "No se pudo guardar. Sigues con Violeta."
        ).revealed().assertIsDisplayed()
        rule.onNodeWithText("Reintentar").revealed().performClick()
        rule.onNodeWithText("Cancelar").revealed().performClick()

        assertEquals(
            listOf<SettingsAction>(SettingsAction.Tuya.Use, SettingsAction.Tuya.Cancel),
            actions
        )
    }

    @Test
    fun aFailedSaveOverTuyaNamesTheTuyaStillInUse() {
        showSettings(
            SettingsState(loading = false, appearance = red, tuya = draft(0x2E7D32, failed = true)),
            mutableListOf()
        )

        rule.onNodeWithText("No se pudo guardar. Sigues con Tuya (Rojo).").revealed()
            .assertIsDisplayed()
        rule.onNodeWithText("Antes · Tuya (Rojo)").assertExists()
    }

    @Test
    fun whileSavingOnlyTheBusyButtonShowsAndNothingCanBeChanged() {
        val actions = mutableListOf<SettingsAction>()
        showSettings(
            SettingsState(loading = false, busy = true, tuya = draft(0xC72842, saving = true)),
            actions
        )

        rule.onNodeWithTag(TUYA_CANCEL_TAG).revealed().assertIsNotEnabled()
        rule.onNodeWithTag(TUYA_CODE_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(TUYA_SQUARE_TAG).revealed().performTouchInput { click(center) }
        rule.onNodeWithTag(TUYA_BAR_TAG).revealed().performTouchInput { click(center) }
        rule.onNodeWithTag(TUYA_BEFORE_TAG).revealed().performClick()
        rule.onNodeWithTag(TUYA_USE_TAG).revealed().performClick()

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
