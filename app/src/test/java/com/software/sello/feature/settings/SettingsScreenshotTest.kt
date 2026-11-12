package com.software.sello.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.Look
import com.software.sello.captureStill
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.feature.backup.BACKUP_CARD_CREATE_TAG
import com.software.sello.feature.backup.BackupFailure
import com.software.sello.feature.backup.BackupState
import com.software.sello.feature.backup.EXPORT_ROW_TAG
import com.software.sello.feature.backup.ExportProblem
import com.software.sello.feature.backup.ExportState
import com.software.sello.feature.backup.TusDatos
import com.software.sello.navigation.ShellScreen
import com.software.sello.navigation.ShellState
import com.software.sello.platform.PhoneColor
import com.software.sello.presentation.appearance.shown
import com.software.sello.presentation.month.monthNames
import com.software.sello.revealed
import com.software.sello.showSello
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * What Configuración looks like in its frame, with its Tus datos card in each state, kept
 * as committed images (ADR 0022).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    private val zone = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 10, 22)

    private fun last(at: String) = LastBackup(
        at = Instant.parse(at),
        name = "sello-respaldo.json",
        bytes = 48_200,
        summary = BackupSummary(8, 120, 6, 2, 3, 1)
    )

    private val recent = BackupState(
        loading = false,
        last = last("2026-10-20T15:00:00Z"),
        zone = zone,
        today = today
    )

    private val loaded = SettingsState(
        loading = false,
        appearanceLoaded = true,
        orderingLoaded = true,
        investmentsLoaded = true,
        smallExpensesLoaded = true,
        smallExpenseLine = "20.000",
        schedules = ScheduleCountLine.Read(payments = 4, incomes = 1),
        rate = RateLine.Read("4.150,25", LocalDate.of(2026, 10, 20)),
        // A phone on Android 12 or later with a blue wallpaper, as the mock's.
        phone = PhoneColor.Hue(240)
    )

    private fun capture(
        name: String,
        look: Look,
        state: SettingsState = loaded,
        backup: BackupState = recent,
        export: ExportState = ExportState(),
        open: String? = null,
        reveal: String? = null
    ) {
        rule.showSello(look) {
            Box {
                ShellScreen(
                    state = ShellState(
                        currentMonth = YearMonth.of(2026, 10),
                        selectedMonth = YearMonth.of(2026, 10),
                        monthPickerOpen = false,
                        pickerYear = 2026,
                        pendingEntry = null
                    ),
                    names = monthNames(),
                    onAction = {},
                    onBack = {},
                    screenTitle = "Configuración",
                    pastMonthRibbon = false
                ) { padding ->
                    SettingsScreen(state, {}, padding, {}, {}) {
                        TusDatos(backup, {}, export, {}, true, {}, {}, {})
                    }
                }
                state.tuya?.let { TuyaSheet(it, state.appearance.ink.shown(state.phone)) {} }
            }
        }
        if (open != null) rule.onNodeWithTag(open).revealed().performClick()
        if (reveal != null) rule.onNodeWithTag(reveal).revealed()
        rule.captureStill("src/test/screenshots/settings-$name-${look.label}.png")
    }

    @Test fun loadedLight() = capture("loaded", Look())

    @Test fun loadedDark() = capture("loaded", Look(dark = true))

    /** Resumen's section: Gastos hormiga and its line (SELLO-194). */
    @Test
    fun resumenLight() = capture("resumen", Look(), reveal = SETTINGS_SMALL_EXPENSES_TAG)

    @Test
    fun resumenDark() = capture("resumen", Look(dark = true), reveal = SETTINGS_SMALL_EXPENSES_TAG)

    @Test fun loadingLight() = capture("loading", Look(), loaded.copy(loading = true))

    @Test fun loadingDark() = capture("loading", Look(dark = true), loaded.copy(loading = true))

    /** A preference could not be read: the last good values under the notice. */
    @Test
    fun failedLight() =
        capture("failed", Look(), loaded.copy(appearanceFailure = PreferenceFailure.Unavailable))

    @Test
    fun failedDark() = capture(
        "failed",
        Look(dark = true),
        loaded.copy(appearanceFailure = PreferenceFailure.Unavailable)
    )

    /** Counts and the rate could not be read; a write is under way. */
    private val busy = loaded.copy(
        busy = true,
        schedules = ScheduleCountLine.Unreadable,
        rate = RateLine.Unreadable
    )

    @Test fun busyLight() = capture("busy", Look(), busy)

    @Test fun busyDark() = capture("busy", Look(dark = true), busy)

    @Test
    fun orderSlipLight() = capture("order-slip", Look(), open = SETTINGS_ORDER_ROW_TAG)

    @Test
    fun orderSlipDark() = capture("order-slip", Look(dark = true), open = SETTINGS_ORDER_ROW_TAG)

    // Tus datos, the card at the top of Configuración.

    private val never = recent.copy(last = null)
    private val old = recent.copy(last = last("2026-08-30T15:00:00Z"))
    private val unreadable = recent.copy(recordFailed = true)
    private val saving = recent.copy(saving = true)
    private val failed = recent.copy(failure = BackupFailure.Write)
    private val unknown = recent.copy(unknownOutcome = true)

    @Test fun dataNeverLight() = capture("data-never", Look(), backup = never)

    @Test fun dataNeverDark() = capture("data-never", Look(dark = true), backup = never)

    @Test fun dataOldLight() = capture("data-old", Look(), backup = old)

    @Test fun dataOldDark() = capture("data-old", Look(dark = true), backup = old)

    @Test fun dataUnreadableLight() = capture("data-unreadable", Look(), backup = unreadable)

    @Test
    fun dataUnreadableDark() = capture("data-unreadable", Look(dark = true), backup = unreadable)

    @Test fun dataSavingLight() = capture("data-saving", Look(), backup = saving)

    @Test fun dataSavingDark() = capture("data-saving", Look(dark = true), backup = saving)

    @Test fun dataFailedLight() = capture("data-failed", Look(), backup = failed)

    @Test fun dataFailedDark() = capture("data-failed", Look(dark = true), backup = failed)

    @Test fun dataUnknownLight() = capture("data-unknown", Look(), backup = unknown)

    @Test fun dataUnknownDark() = capture("data-unknown", Look(dark = true), backup = unknown)

    @Test
    fun exportWorkingLight() =
        capture("export-working", Look(), export = ExportState(working = true))

    @Test
    fun exportWorkingDark() =
        capture("export-working", Look(dark = true), export = ExportState(working = true))

    @Test
    fun exportSavedLight() =
        capture("export-saved", Look(), export = ExportState(saved = "sello-gastos.csv"))

    @Test
    fun exportSavedDark() =
        capture("export-saved", Look(dark = true), export = ExportState(saved = "sello-gastos.csv"))

    @Test
    fun exportFailedLight() =
        capture("export-failed", Look(), export = ExportState(problem = ExportProblem.Write))

    @Test
    fun exportFailedDark() = capture(
        "export-failed",
        Look(dark = true),
        export = ExportState(problem = ExportProblem.Write)
    )

    @Test
    fun disclosureLight() = capture("disclosure", Look(), open = BACKUP_CARD_CREATE_TAG)

    @Test
    fun disclosureDark() = capture("disclosure", Look(dark = true), open = BACKUP_CARD_CREATE_TAG)

    @Test fun exportSlipLight() = capture("export-slip", Look(), open = EXPORT_ROW_TAG)

    @Test fun exportSlipDark() = capture("export-slip", Look(dark = true), open = EXPORT_ROW_TAG)

    @Test fun dataLoadingLight() = capture("data-loading", Look(), backup = BackupState())

    @Test
    fun dataLoadingDark() = capture("data-loading", Look(dark = true), backup = BackupState())

    /** A save just confirmed: the Guardado stamp, landed. */
    private val justSaved = recent.copy(last = last("2026-10-22T15:00:00Z"), justSaved = true)

    @Test fun dataJustSavedLight() = capture("data-just-saved", Look(), backup = justSaved)

    @Test
    fun dataJustSavedDark() = capture("data-just-saved", Look(dark = true), backup = justSaved)

    // SELLO-202: Tuya and Tu tinta.

    private val red = InkPreference.Tuya(TuyaColour.Hue(InkHue.of(22)!!))

    private val tuyaChosen = loaded.copy(appearance = AppearancePreference(ink = red))

    @Test
    fun tuyaLight() =
        capture("tuya", Look(ink = HueInk(22)), tuyaChosen, reveal = SETTINGS_INK_NOTE_TAG)

    @Test
    fun tuyaDark() = capture(
        "tuya",
        Look(ink = HueInk(22), dark = true),
        tuyaChosen,
        reveal = SETTINGS_INK_NOTE_TAG
    )

    private val tuyaKept =
        loaded.copy(appearance = AppearancePreference(tuya = TuyaColour.Hue(InkHue.of(22)!!)))

    @Test
    fun tuyaKeptLight() = capture("tuya-kept", Look(), tuyaKept, reveal = SETTINGS_INK_TAG)

    @Test
    fun tuyaKeptDark() =
        capture("tuya-kept", Look(dark = true), tuyaKept, reveal = SETTINGS_INK_TAG)

    private val sheet = loaded.copy(tuya = TuyaDraft(hue = 200))

    @Test fun tuTintaLight() = capture("tu-tinta", Look(), sheet)

    @Test fun tuTintaDark() = capture("tu-tinta", Look(dark = true), sheet)

    private val redSheet = loaded.copy(tuya = TuyaDraft(hue = 22))

    @Test fun tuTintaRedLight() = capture("tu-tinta-red", Look(), redSheet)

    @Test fun tuTintaRedDark() = capture("tu-tinta-red", Look(dark = true), redSheet)

    private val failedSheet = loaded.copy(tuya = TuyaDraft(hue = 200, failed = true))

    @Test fun tuTintaFailedLight() = capture("tu-tinta-failed", Look(), failedSheet)

    @Test fun tuTintaFailedDark() = capture("tu-tinta-failed", Look(dark = true), failedSheet)

    private val savingSheet = loaded.copy(busy = true, tuya = TuyaDraft(hue = 200, saving = true))

    @Test fun tuTintaSavingLight() = capture("tu-tinta-saving", Look(), savingSheet)

    @Test fun tuTintaSavingDark() = capture("tu-tinta-saving", Look(dark = true), savingSheet)

    // SELLO-203: Sistema.

    private val sistema =
        loaded.copy(appearance = AppearancePreference(ink = InkPreference.Sistema))

    @Test
    fun sistemaLight() =
        capture("sistema", Look(ink = HueInk(240)), sistema, reveal = SETTINGS_INK_NOTE_TAG)

    @Test
    fun sistemaDark() = capture(
        "sistema",
        Look(ink = HueInk(240), dark = true),
        sistema,
        reveal = SETTINGS_INK_NOTE_TAG
    )

    /** A red wallpaper: the hue as it is, and the note. */
    private val sistemaRed = sistema.copy(phone = PhoneColor.Hue(24))

    @Test
    fun sistemaRedLight() =
        capture("sistema-red", Look(ink = HueInk(24)), sistemaRed, reveal = SETTINGS_INK_NOTE_TAG)

    @Test
    fun sistemaRedDark() = capture(
        "sistema-red",
        Look(ink = HueInk(24), dark = true),
        sistemaRed,
        reveal = SETTINGS_INK_NOTE_TAG
    )

    /** Android 11, after restoring a backup made with Sistema: Cobalto, and no Sistema. */
    private val android11 = sistema.copy(phone = PhoneColor.None)

    @Test
    fun android11Light() = capture("android-11", Look(), android11, reveal = SETTINGS_INK_TAG)

    @Test
    fun android11Dark() =
        capture("android-11", Look(dark = true), android11, reveal = SETTINGS_INK_TAG)

    /** A monochrome style: a grey accent, Sistema still chosen and drawn in Cobalto. */
    private val sistemaGrey = sistema.copy(phone = PhoneColor.Grey)

    @Test
    fun sistemaGreyLight() =
        capture("sistema-grey", Look(), sistemaGrey, reveal = SETTINGS_INK_NOTE_TAG)

    @Test
    fun sistemaGreyDark() =
        capture("sistema-grey", Look(dark = true), sistemaGrey, reveal = SETTINGS_INK_NOTE_TAG)
}
