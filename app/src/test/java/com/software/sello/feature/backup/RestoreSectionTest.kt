package com.software.sello.feature.backup

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.designsystem.theme.selloColors
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.PendingPreferences
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.StagedRestore
import com.software.sello.platform.PhoneColor
import com.software.sello.showSello
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The restore part of Respaldo after an unknown outcome, given state by hand. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class RestoreSectionTest {
    @get:Rule
    val rule = createComposeRule()

    private val actions = mutableListOf<RestoreAction>()
    private val operation = OperationId.of("00000000-0000-4000-8000-000000005000")
        .let { (it as Outcome.Success).value }

    private companion object {
        const val SET_ASIDE =
            "No revisamos el archivo que elegiste: primero había que resolver esta restauración."
    }

    private fun show(step: RestoreStep, phone: PhoneColor = PhoneColor.Hue(240)) {
        rule.showSello(Look()) {
            RestoreSection(RestoreState(step = step, phone = phone), { actions += it })
        }
    }

    @Test
    fun anUnknownOutcomeIsNamedAndComprobarChecksWithoutConfirming() {
        show(RestoreStep.Unknown(operation))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText("Comprobar").performClick()

        assertEquals(listOf<RestoreAction>(RestoreAction.Check), actions)
    }

    @Test
    fun anUnknownOutcomeShownForAnEarlierRestoreSaysTheChosenFileWasNotChecked() {
        show(RestoreStep.Unknown(operation, chosenSetAside = true))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText(SET_ASIDE, substring = true).assertIsDisplayed()
    }

    @Test
    fun anEarlierRestoreShownRestoredSaysTheChosenFileWasNotChecked() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(null, null)
                ),
                chosenSetAside = true
            )
        )

        rule.onNodeWithText("Ir al recibo").assertIsDisplayed()
        rule.onNodeWithText(SET_ASIDE).assertIsDisplayed()
    }

    @Test
    fun anAppearanceStillToApplyNamesTuyaAndItsColour() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(
                        AppearancePreference(
                            ThemePreference.Dark,
                            InkPreference.Tuya(TuyaColour.Hue(InkHue.of(22)!!))
                        ),
                        null
                    )
                )
            )
        )

        rule.onNodeWithText("Apariencia: tema Oscuro, tinta Tuya (Rojo)").assertIsDisplayed()
    }

    @Test
    fun anAppearanceStillToApplyNamesTuyasExactColourByItsName() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(
                        AppearancePreference(
                            ThemePreference.Light,
                            InkPreference.Tuya(TuyaColour.Exact(InkColour.of(0x808080)!!))
                        ),
                        null
                    )
                )
            )
        )

        rule.onNodeWithText("Apariencia: tema Claro, tinta Tuya (Gris)").assertIsDisplayed()
    }

    @Test
    fun aGastosHormigaLineStillToApplyIsNamedAndAppliedByItsOwnName() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(null, null, null, SmallExpenseLine.of(15_000))
                )
            )
        )

        rule.onNodeWithText("Gastos hormiga: hasta 15.000").assertIsDisplayed()
        rule.onNodeWithText("Aplicar gastos hormiga").assertIsDisplayed()
    }

    @Test
    fun aGastosHormigaLineWithAnotherGroupIsAppliedAsPreferences() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(
                        AppearancePreference(ThemePreference.Light, InkPreference.Cobalto),
                        null,
                        null,
                        SmallExpenseLine.DEFAULT
                    )
                )
            )
        )

        rule.onNodeWithText("Gastos hormiga: hasta 20.000").assertIsDisplayed()
        rule.onNodeWithText("Aplicar preferencias").assertIsDisplayed()
    }

    @Test
    fun guidanceStillToMergeIsNamedAndAppliedByItsOwnName() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(null, null, guidance = GuidanceMemory())
                )
            )
        )

        rule.onNodeWithText("Guía: lo que ya viste").assertIsDisplayed()
        rule.onNodeWithText("Aplicar guía").assertIsDisplayed()
    }

    @Test
    fun guidanceWithAnotherGroupIsAppliedAsPreferences() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(
                        AppearancePreference(ThemePreference.Light, InkPreference.Cobalto),
                        CategoryOrderingPreference(),
                        guidance = GuidanceMemory()
                    )
                )
            )
        )

        rule.onNodeWithText("Guía: lo que ya viste").assertIsDisplayed()
        rule.onNodeWithText("Aplicar preferencias").assertIsDisplayed()
    }

    private val sistemaPending = RestoreStep.Restored(
        RestoreOutcome.Restored(
            BackupSummary(2, 3, 1, 0, 0),
            PendingPreferences(
                AppearancePreference(ThemePreference.Light, InkPreference.Sistema),
                null
            )
        )
    )

    @Test
    fun anAppearanceStillToApplyNamesSistemaWhereThePhoneHasAnAccent() {
        show(sistemaPending, PhoneColor.Hue(240))

        rule.onNodeWithText("Apariencia: tema Claro, tinta Sistema").assertIsDisplayed()
    }

    @Test
    fun aGreyAccentStillNamesSistema() {
        show(sistemaPending, PhoneColor.Grey)

        rule.onNodeWithText("Apariencia: tema Claro, tinta Sistema").assertIsDisplayed()
    }

    @Test
    fun onAndroid11ASistemaStillToApplyIsNamedCobaltoAsItWillShow() {
        show(sistemaPending, PhoneColor.None)

        rule.onNodeWithText("Apariencia: tema Claro, tinta Cobalto").assertIsDisplayed()
    }

    @Test
    fun anUnknownOutcomeWithoutASetAsideSaysNothingAboutAChosenFile() {
        show(RestoreStep.Unknown(operation))

        rule.onNodeWithText(SET_ASIDE, substring = true).assertDoesNotExist()
    }

    @Test
    fun aCheckUnderWayCannotBePressedAgain() {
        show(RestoreStep.Unknown(operation, checking = true))

        rule.onNodeWithText("No sabemos si se restauró").assertIsDisplayed()
        rule.onNodeWithText("Comprobando…").assertIsNotEnabled()
    }

    private val staged = StagedRestore(
        operationId = operation,
        backupAt = Instant.parse("2026-10-22T14:30:00Z"),
        current = BackupSummary(1, 2, 0, 0, 0),
        replacement = BackupSummary(2, 3, 1, 0, 0),
        generation = 1,
        revision = 4
    )

    private fun inkOf(text: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }

    /** A restore that did not happen is a problem: loss ink. */
    @Test
    fun aFileFoundNotReplacedSaysSoAboveItsPreviewInLossInk() {
        show(RestoreStep.Previewing(staged, notReplaced = true))

        assertEquals(
            selloColors(SelloInk.Cobalto, isDark = false).semantic.loss,
            inkOf("No se restauró: tus datos no cambiaron. Si aún quieres, confírmalo de nuevo.")
        )
    }

    /** Figures that changed while reviewing are a doubt to check: warn ink, like Borrar datos'. */
    @Test
    fun aPreviewCountedAgainSaysSoInWarnInk() {
        show(RestoreStep.Previewing(staged, refreshed = true))

        assertEquals(
            selloColors(SelloInk.Cobalto, isDark = false).semantic.warn,
            inkOf("Tus datos cambiaron mientras revisabas. Estas son las cifras de ahora.")
        )
    }

    @Test
    fun preferencesThatCouldNotBeReadAreNamedWithNothingToApply() {
        show(
            RestoreStep.Restored(
                RestoreOutcome.Restored(
                    BackupSummary(2, 3, 1, 0, 0),
                    PendingPreferences(null, null),
                    preferencesUnreadable = true
                )
            )
        )

        rule.onNodeWithText("Historial financiero restaurado").assertIsDisplayed()
        rule.onNodeWithText("No pudimos leer las preferencias del respaldo", substring = true)
            .assertIsDisplayed()
        rule.onNodeWithText("Ir al recibo").performClick()

        assertEquals(listOf<RestoreAction>(RestoreAction.Leave), actions)
    }
}
