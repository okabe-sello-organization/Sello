package com.software.sello.feature.settings

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.InvestmentBoardState
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import com.software.sello.feature.investments.HandInvestmentPreferences
import com.software.sello.feature.investments.HandInvestmentReads
import com.software.sello.feature.investments.rateEntry
import com.software.sello.feature.investments.ratesBoard
import com.software.sello.feature.recurrence.HandScheduleReads
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.SystemColor
import com.software.sello.testing.testMoney
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val appearance = HandAppearancePreferences()
    private val ordering = HandOrderingPreferences()

    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private val investments = HandInvestmentReads(ReadState.Loading)
    private val investmentSettings = HandInvestmentPreferences()
    private val smallExpenses = HandSmallExpenses()

    /** The phone's accent colour: a hue, grey, or none as on Android 11. */
    private var phone: PhoneColor = PhoneColor.Hue(140)
    private val systemColor = SystemColor { phone }

    private fun settings(handle: SavedStateHandle = SavedStateHandle()) = SettingsViewModel(
        handle,
        appearance,
        ordering,
        investmentSettings,
        smallExpenses,
        HandScheduleReads(),
        investments,
        testMoney(),
        systemColor
    )

    @Test
    fun theDolarRowSaysTheCurrentRateNoneOrThatItCouldNotBeRead() = runTest {
        val model = settings()
        runCurrent()
        assertEquals(RateLine.Loading, model.state.value.rate)

        investments.board.value = ReadState.Ready(ratesBoard())
        runCurrent()
        assertEquals(RateLine.None, model.state.value.rate)

        // IN31: 4.000,00 on 24 oct stays current after 3.900,00 dated 22 oct is entered.
        investments.board.value = ReadState.Ready(
            ratesBoard(rateEntry(400_000, "2026-10-24", 1), rateEntry(390_000, "2026-10-22", 2))
        )
        runCurrent()
        assertEquals(RateLine.Read("4.000,00", LocalDate.of(2026, 10, 24)), model.state.value.rate)

        investments.board.value =
            ReadState.Failed(StorageFailure.Unavailable("hand"), null)
        runCurrent()
        assertEquals(RateLine.Unreadable, model.state.value.rate)
    }

    @Test
    fun theGastosHormigaRowSaysTheLineAndNeverADefaultItCouldNotRead() = runTest {
        smallExpenses.read.value = null
        val model = settings()
        runCurrent()
        // Configuración waits for every setting it shows.
        assertTrue(model.state.value.loading)

        smallExpenses.read.value = PreferenceRead.Ready(SmallExpenseLine.of(15_000)!!)
        runCurrent()
        assertFalse(model.state.value.loading)
        assertEquals("15.000", model.state.value.smallExpenseLine)
        assertNull(model.state.value.failure)

        // Not read again: the last line read stays, and the notice says so.
        smallExpenses.read.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, SmallExpenseLine.of(15_000))
        runCurrent()
        assertEquals("15.000", model.state.value.smallExpenseLine)
        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)
    }

    @Test
    fun aGastosHormigaLineNeverReadShowsNoValue() = runTest {
        smallExpenses.read.value = PreferenceRead.Failed(PreferenceFailure.InvalidValue, null)
        val model = settings()
        runCurrent()

        assertFalse(model.state.value.loading)
        assertNull(model.state.value.smallExpenseLine)
        assertEquals(PreferenceFailure.InvalidValue, model.state.value.failure)
    }

    @Test
    fun changingEachControlUsesItsNarrowPortAndShowsThePersistedValues() = runTest {
        val model = settings()
        runCurrent()

        model.onAction(SettingsAction.ThemeChosen(ThemePreference.Dark))
        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        model.onAction(SettingsAction.OrderChosen(CategoryOrder.LatestExpenseAdded))
        model.onAction(SettingsAction.DirectionChosen(CategoryOrderDirection.Ascending))
        runCurrent()

        assertEquals(
            AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
            model.state.value.appearance
        )
        assertEquals(
            CategoryOrderingPreference(
                CategoryOrder.LatestExpenseAdded,
                CategoryOrderDirection.Ascending
            ),
            model.state.value.categoryOrdering
        )
        assertFalse(model.state.value.busy)
    }

    @Test
    fun inversionesSettingsStartInPesosLargestValueFirstAndChangeThroughTheirPort() = runTest {
        val model = settings()
        runCurrent()
        assertEquals(InvestmentPreference(), model.state.value.investments)

        model.onAction(SettingsAction.NewInvestmentCurrencyChosen(Currency.USD))
        model.onAction(SettingsAction.InvestmentOrderChosen(InvestmentOrder.Return))
        runCurrent()

        assertEquals(
            InvestmentPreference(Currency.USD, InvestmentOrder.Return),
            model.state.value.investments
        )
        assertEquals(
            PreferenceRead.Ready(InvestmentPreference(Currency.USD, InvestmentOrder.Return)),
            investmentSettings.current.value
        )
    }

    @Test
    fun anUnreadableInversionesSettingIsReportedAndKeepsTheLastKnownValue() = runTest {
        val model = settings()
        runCurrent()
        val known = InvestmentPreference(Currency.USD, InvestmentOrder.Newest)
        investmentSettings.current.value =
            PreferenceRead.Failed(PreferenceFailure.Unavailable, known)
        runCurrent()

        assertEquals(known, model.state.value.investments)
        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)
        assertFalse(model.state.value.loading)
    }

    @Test
    fun anUnreadablePreferenceStaysVisibleAndRetryKeepsKnownValues() = runTest {
        val model = settings()
        runCurrent()
        appearance.values.value = PreferenceRead.Failed(
            PreferenceFailure.Unavailable,
            AppearancePreference(ThemePreference.Light, InkPreference.Cobalto)
        )
        runCurrent()

        assertEquals(ThemePreference.Light, model.state.value.appearance.theme)
        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)

        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ThemePreference.Dark))
        model.onAction(SettingsAction.Retry)
        runCurrent()

        assertEquals(ThemePreference.Dark, model.state.value.appearance.theme)
        assertEquals(null, model.state.value.failure)
    }

    @Test
    fun aFailedNonfinancialWriteIsReportedWithoutChangingTheDisplayedFinancialOrder() = runTest {
        val model = settings()
        runCurrent()
        ordering.nextWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.OrderChosen(CategoryOrder.AmountSpent))
        runCurrent()

        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)
        assertEquals(CategoryOrder.MostUsed, model.state.value.categoryOrdering.order)
        assertTrue(!model.state.value.busy)
    }

    // SELLO-202 and SELLO-216: Tu tinta.

    private val red = InkPreference.Tuya(TuyaColour.Hue(InkHue.of(22)!!))

    private fun colour(rgb: Int) = InkColour.of(rgb.toLong())!!

    private fun exact(rgb: Int) = InkPreference.Tuya(TuyaColour.Exact(colour(rgb)))

    /** Cobalto's light band, where the sheet opens with no Tuya yet. */
    private val cobalto = 0x1F3BE0

    private val crimson = exact(0xC72842)

    private fun SettingsViewModel.tuya() = state.value.tuya

    /** The sheet as it opens on [rgb], with Antes [before]. */
    private fun opened(rgb: Int, before: Int = cobalto) = TuyaDraft(
        colour = colour(rgb),
        pick = TuyaPick.of(rgb, 0f),
        code = codeOf(rgb),
        before = colour(before)
    )

    private fun bandOf(hue: Int) = HueInk(hue).swatch.toArgb() and 0xFFFFFF

    @Test
    fun tuTintaOpensOnTheInkInUseAndNothingIsSavedUntilUsarEstaTinta() = runTest {
        val model = settings()
        runCurrent()

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(cobalto), model.tuya())
        assertEquals("#1F3BE0", model.tuya()?.code)

        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        runCurrent()
        assertEquals(colour(0xC72842), model.tuya()?.colour)
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        // The exact colour is stored, never a hue.
        assertEquals(listOf<InkPreference>(crimson), appearance.inkWrites)
        assertEquals(crimson, model.state.value.appearance.ink)
        assertNull(model.tuya())
        assertFalse(model.state.value.busy)
    }

    @Test
    fun tuTintaOpensOnTuyasKeptColourElseItsKeptHuesColourAndAntesIsTheInkInUse() = runTest {
        val model = settings()
        val yellow = TuyaColour.Exact(colour(0xF4C430))
        appearance.values.value = PreferenceRead.Ready(
            AppearancePreference(ink = InkPreference.Violeta, tuya = yellow)
        )
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        // Violeta's light band is Antes.
        assertEquals(opened(0xF4C430, before = 0x5B2BD6), model.tuya())
        model.onAction(SettingsAction.Tuya.Cancel)

        // A hue saved before E39, in use: the colour it draws, which is also Antes.
        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ink = red))
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(bandOf(22), before = bandOf(22)), model.tuya())
    }

    @Test
    fun choosingAnotherInkKeepsTuyasColourAndTuTintaReopensOnIt() = runTest {
        val model = settings()
        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ink = crimson))
        runCurrent()

        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        runCurrent()
        assertEquals(
            AppearancePreference(ink = InkPreference.Violeta, tuya = crimson.colour),
            model.state.value.appearance
        )

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(0xC72842, before = 0x5B2BD6), model.tuya())
    }

    @Test
    fun theSquareAndTheBarMakeTheColourAndABlackKeepsTheBarsHue() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)

        model.onAction(SettingsAction.Tuya.BarMoved(120f))
        model.onAction(SettingsAction.Tuya.SquareMoved(saturation = 1f, value = 1f))
        runCurrent()
        assertEquals(colour(0x00FF00), model.tuya()?.colour)
        assertEquals(TuyaPick(120f, 1f, 1f), model.tuya()?.pick)
        assertEquals("#00FF00", model.tuya()?.code)

        model.onAction(SettingsAction.Tuya.SquareMoved(saturation = 0f, value = 0.5f))
        runCurrent()
        assertEquals(colour(0x808080), model.tuya()?.colour)

        // Down to black, the bar moved, and up again: the bar's new hue, at full.
        model.onAction(SettingsAction.Tuya.SquareMoved(saturation = 1f, value = 0f))
        model.onAction(SettingsAction.Tuya.BarMoved(240f))
        runCurrent()
        assertEquals(colour(0x000000), model.tuya()?.colour)
        assertEquals(TuyaPick(240f, 1f, 0f), model.tuya()?.pick)
        model.onAction(SettingsAction.Tuya.SquareMoved(saturation = 1f, value = 1f))
        runCurrent()
        assertEquals(colour(0x0000FF), model.tuya()?.colour)
        assertEquals("#0000FF", model.tuya()?.code)
        assertEquals(colour(cobalto), model.tuya()?.before)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun aPlaceOffTheSquareOrTheBarChangesNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        val before = model.tuya()

        model.onAction(SettingsAction.Tuya.SquareMoved(1.5f, 1f))
        model.onAction(SettingsAction.Tuya.SquareMoved(1f, -0.1f))
        model.onAction(SettingsAction.Tuya.SquareMoved(Float.NaN, 1f))
        model.onAction(SettingsAction.Tuya.BarMoved(360f))
        model.onAction(SettingsAction.Tuya.BarMoved(-1f))
        model.onAction(SettingsAction.Tuya.BarMoved(Float.NaN))
        runCurrent()

        assertEquals(before, model.tuya())
    }

    @Test
    fun aTypedCodeInAnyCaseMovesTheColourAnythingElseChangesNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)

        // Part of a code: shown as typed, the colour stays.
        model.onAction(SettingsAction.Tuya.CodeTyped("c7"))
        runCurrent()
        assertEquals("c7", model.tuya()?.code)
        assertEquals(colour(cobalto), model.tuya()?.colour)

        // Without the hash, in lower case.
        model.onAction(SettingsAction.Tuya.CodeTyped("c72842"))
        runCurrent()
        assertEquals("c72842", model.tuya()?.code)
        assertEquals(colour(0xC72842), model.tuya()?.colour)
        assertEquals(TuyaPick.of(0xC72842, 0f), model.tuya()?.pick)

        // A seventh digit is no code: the colour stays; an eighth character is not kept.
        model.onAction(SettingsAction.Tuya.CodeTyped("c728421"))
        runCurrent()
        assertEquals("c728421", model.tuya()?.code)
        assertEquals(colour(0xC72842), model.tuya()?.colour)
        model.onAction(SettingsAction.Tuya.CodeTyped("c7284211"))
        runCurrent()
        assertEquals("c728421", model.tuya()?.code)

        // Leaving the field shows the current code again.
        model.onAction(SettingsAction.Tuya.CodeLeft)
        runCurrent()
        assertEquals("#C72842", model.tuya()?.code)
        assertEquals(colour(0xC72842), model.tuya()?.colour)

        // With the hash, in capitals.
        model.onAction(SettingsAction.Tuya.CodeTyped("#00FF00"))
        runCurrent()
        assertEquals(colour(0x00FF00), model.tuya()?.colour)
        assertEquals(TuyaPick(120f, 1f, 1f), model.tuya()?.pick)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun aTypedGreyKeepsTheBarsHue() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.BarMoved(200f))
        model.onAction(SettingsAction.Tuya.CodeTyped("#808080"))
        runCurrent()

        assertEquals(colour(0x808080), model.tuya()?.colour)
        assertEquals(TuyaPick(200f, 0f, 128 / 255f), model.tuya()?.pick)
    }

    @Test
    fun antesGoesBackToTheInkInUseWhenTheSheetOpened() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        model.onAction(SettingsAction.Tuya.BeforeTapped)
        runCurrent()

        assertEquals(opened(cobalto), model.tuya())
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun aCancelledSaveIsNeitherSavedNorFailedAndFreesTheSheet() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        runCurrent()
        val chosen = model.tuya()!!
        val gate = CompletableDeferred<Unit>()
        appearance.inkGate = gate

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(chosen.copy(saving = true), model.tuya())

        // The write is cancelled under the save: the cancellation goes on up, never caught
        // as a failure or taken for a save.
        gate.completeExceptionally(CancellationException("left the screen"))
        runCurrent()

        assertEquals(chosen, model.tuya())
        assertFalse(model.state.value.busy)
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        assertNull(model.state.value.failure)

        appearance.inkGate = null
        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(crimson, model.state.value.appearance.ink)
        assertNull(model.tuya())
    }

    @Test
    fun cancelingForgetsTheColourAndKeepsTheInk() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#2E7D32"))
        model.onAction(SettingsAction.Tuya.Cancel)
        runCurrent()

        assertNull(model.tuya())
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(cobalto), model.tuya())
    }

    @Test
    fun aFailedSaveKeepsTheSheetAndColourAndTheInkInUseThenReintentarSaves() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        runCurrent()
        val chosen = model.tuya()!!
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        assertEquals(chosen.copy(failed = true), model.tuya())
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        // The sheet says it; the screen behind does not repeat it.
        assertNull(model.state.value.failure)
        assertFalse(model.state.value.busy)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(listOf<InkPreference>(crimson, crimson), appearance.inkWrites)
        assertEquals(crimson, model.state.value.appearance.ink)
        assertNull(model.tuya())
    }

    @Test
    fun usarEstaTintaShowsTheColoursCodeOverAPartOfOneTyped() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        model.onAction(SettingsAction.Tuya.CodeTyped("#C7"))
        runCurrent()
        assertEquals("#C7", model.tuya()?.code)
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        assertEquals(true, model.tuya()?.failed)
        assertEquals(colour(0xC72842), model.tuya()?.colour)
        assertEquals("#C72842", model.tuya()?.code)
    }

    @Test
    fun changingTheColourAfterAFailureClearsTheMessage() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(true, model.tuya()?.failed)

        // Typing part of a code changes no colour, so the message stays.
        model.onAction(SettingsAction.Tuya.CodeTyped("#1"))
        runCurrent()
        assertEquals(true, model.tuya()?.failed)

        model.onAction(SettingsAction.Tuya.BarMoved(30f))
        runCurrent()
        assertEquals(false, model.tuya()?.failed)
    }

    @Test
    fun whileSavingASecondTapAndEveryOtherSheetActionDoNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        runCurrent()
        val chosen = model.tuya()!!
        val gate = CompletableDeferred<Unit>()
        appearance.inkGate = gate

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Use)
        model.onAction(SettingsAction.Tuya.SquareMoved(0f, 0f))
        model.onAction(SettingsAction.Tuya.BarMoved(40f))
        model.onAction(SettingsAction.Tuya.CodeTyped("#00FF00"))
        model.onAction(SettingsAction.Tuya.CodeLeft)
        model.onAction(SettingsAction.Tuya.BeforeTapped)
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.Cancel)
        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        runCurrent()

        assertEquals(listOf<InkPreference>(crimson), appearance.inkWrites)
        assertEquals(chosen.copy(saving = true), model.tuya())
        assertTrue(model.state.value.busy)

        gate.complete(Unit)
        runCurrent()
        assertEquals(crimson, model.state.value.appearance.ink)
        assertNull(model.tuya())
        assertFalse(model.state.value.busy)
    }

    @Test
    fun theColourBeingMadeAndTheCodeBeingTypedSurviveProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val model = settings(handle)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.BarMoved(120f))
        model.onAction(SettingsAction.Tuya.SquareMoved(1f, 1f))
        model.onAction(SettingsAction.Tuya.CodeTyped("#00f"))
        runCurrent()

        // What the system keeps of the handle is all a new process gets.
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val again = settings(restored)
        runCurrent()

        assertEquals(
            TuyaDraft(
                colour = colour(0x00FF00),
                pick = TuyaPick(120f, 1f, 1f),
                code = "#00f",
                before = colour(cobalto)
            ),
            again.tuya()
        )
        assertEquals(InkPreference.Cobalto, again.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        again.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(listOf(exact(0x00FF00)), appearance.inkWrites)
    }

    @Test
    fun aKeptSheetSelloCouldNotHaveWrittenOpensNothingAndSavesNothing() = runTest {
        val handle = SavedStateHandle()
        val model = settings(handle)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        val kept = handle.keys().associateWith { handle.get<Any>(it) }

        // Each piece damaged in turn, in its own type: out of range or too long.
        for (key in kept.keys) {
            val damaged = when (val value = kept[key]) {
                is Int -> listOf(0x1000000, -1)
                is FloatArray -> listOf(floatArrayOf(360f, 1f, 1f), floatArrayOf(0f, 1f))
                is String -> listOf("#C7284211")
                else -> error("Unexpected $value under $key")
            }
            for (bad in damaged) {
                val again = settings(SavedStateHandle(kept + (key to bad)))
                runCurrent()
                assertNull("$key = $bad", again.tuya())
                again.onAction(SettingsAction.Tuya.Use)
                again.onAction(SettingsAction.Tuya.BeforeTapped)
                runCurrent()
                assertNull("$key = $bad", again.tuya())
            }
        }
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun sheetActionsWithTheSheetClosedOpenNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.BarMoved(22f))
        model.onAction(SettingsAction.Tuya.SquareMoved(1f, 1f))
        model.onAction(SettingsAction.Tuya.CodeTyped("#C72842"))
        model.onAction(SettingsAction.Tuya.CodeLeft)
        model.onAction(SettingsAction.Tuya.BeforeTapped)
        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        assertNull(model.tuya())
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    // SELLO-203: Sistema.

    @Test
    fun sistemaAppliesAtOnceWithNoSheetAndKeepsTuyasHue() = runTest {
        val model = settings()
        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ink = red))
        runCurrent()

        model.onAction(SettingsAction.InkChosen(InkPreference.Sistema))
        runCurrent()

        assertEquals(listOf<InkPreference>(InkPreference.Sistema), appearance.inkWrites)
        assertEquals(
            AppearancePreference(
                ink = InkPreference.Sistema,
                tuya = TuyaColour.Hue(InkHue.of(22)!!)
            ),
            model.state.value.appearance
        )
        assertNull(model.tuya())
        assertFalse(model.state.value.busy)
        assertNull(model.state.value.failure)
    }

    @Test
    fun aSecondTapOnSistemaWhileItSavesWritesNothingMore() = runTest {
        val model = settings()
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        appearance.inkGate = gate

        model.onAction(SettingsAction.InkChosen(InkPreference.Sistema))
        runCurrent()
        model.onAction(SettingsAction.InkChosen(InkPreference.Sistema))
        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        runCurrent()
        assertTrue(model.state.value.busy)

        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf<InkPreference>(InkPreference.Sistema), appearance.inkWrites)
        assertEquals(InkPreference.Sistema, model.state.value.appearance.ink)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun aSistemaThatCannotBeSavedSaysSoAndKeepsTheInkInUse() = runTest {
        val model = settings()
        appearance.values.value =
            PreferenceRead.Ready(AppearancePreference(ink = InkPreference.Violeta))
        runCurrent()
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.InkChosen(InkPreference.Sistema))
        runCurrent()

        assertEquals(PreferenceFailure.Unavailable, model.state.value.failure)
        assertEquals(InkPreference.Violeta, model.state.value.appearance.ink)
        assertFalse(model.state.value.busy)
    }

    @Test
    fun thePhonesColourIsShownAndFollowedWhenReadAgain() = runTest {
        val model = settings()
        runCurrent()
        assertEquals(PhoneColor.Hue(140), model.state.value.phone)

        phone = PhoneColor.Hue(24)
        // Not read again until Sello is back on screen.
        runCurrent()
        assertEquals(PhoneColor.Hue(140), model.state.value.phone)
        systemColor.refresh()
        runCurrent()
        assertEquals(PhoneColor.Hue(24), model.state.value.phone)

        // A monochrome style: grey, which is still an accent.
        phone = PhoneColor.Grey
        systemColor.refresh()
        runCurrent()
        assertEquals(PhoneColor.Grey, model.state.value.phone)
    }

    @Test
    fun withAGreyAccentSistemaCanStillBeChosenAndTuTintaStartsOnCobalto() = runTest {
        phone = PhoneColor.Grey
        systemColor.refresh()
        val model = settings()
        runCurrent()

        model.onAction(SettingsAction.InkChosen(InkPreference.Sistema))
        runCurrent()
        assertEquals(listOf<InkPreference>(InkPreference.Sistema), appearance.inkWrites)
        assertEquals(InkPreference.Sistema, model.state.value.appearance.ink)

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(cobalto), model.tuya())
    }

    @Test
    fun onAndroid11ThereIsNoPhoneColourAndARestoredSistemaStaysStored() = runTest {
        phone = PhoneColor.None
        systemColor.refresh()
        val model = settings()
        appearance.values.value =
            PreferenceRead.Ready(AppearancePreference(ink = InkPreference.Sistema))
        runCurrent()

        assertEquals(PhoneColor.None, model.state.value.phone)
        // The choice is kept as stored; the screen shows it as Cobalto.
        assertEquals(InkPreference.Sistema, model.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(cobalto), model.tuya())
    }

    @Test
    fun tuTintaOpenedFromSistemaStartsOnThePhonesHue() = runTest {
        val model = settings()
        appearance.values.value =
            PreferenceRead.Ready(AppearancePreference(ink = InkPreference.Sistema))
        runCurrent()

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()

        assertEquals(opened(bandOf(140), before = bandOf(140)), model.tuya())
    }

    @Test
    fun sistemaSurvivesARestartWithTheHueTheTuyaSheetKept() = runTest {
        appearance.values.value = PreferenceRead.Ready(
            AppearancePreference(
                ink = InkPreference.Sistema,
                tuya = TuyaColour.Hue(InkHue.of(22)!!)
            )
        )
        // A new view model on the same storage, as after process death.
        val model = settings()
        runCurrent()

        assertEquals(InkPreference.Sistema, model.state.value.appearance.ink)
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(opened(bandOf(22), before = bandOf(140)), model.tuya())
    }
}

private class HandAppearancePreferences : AppearancePreferences {
    val values = MutableStateFlow<PreferenceRead<AppearancePreference>>(
        PreferenceRead.Ready(AppearancePreference())
    )

    /** Every ink asked for, saved or not. */
    val inkWrites = mutableListOf<InkPreference>()
    var nextInkWrite: PreferenceWrite = PreferenceWrite.Saved

    /** While set, an ink write waits for it: a save still on its way. */
    var inkGate: CompletableDeferred<Unit>? = null

    override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = values

    override suspend fun setTheme(theme: ThemePreference): PreferenceWrite {
        val current = (values.value as PreferenceRead.Ready).value
        values.value = PreferenceRead.Ready(current.copy(theme = theme))
        return PreferenceWrite.Saved
    }

    override suspend fun setInk(ink: InkPreference): PreferenceWrite {
        inkWrites += ink
        inkGate?.await()
        val result = nextInkWrite.also { nextInkWrite = PreferenceWrite.Saved }
        if (result is PreferenceWrite.Saved) {
            // As storage does: Tuya brings its colour, another ink keeps Tuya's.
            val current = (values.value as PreferenceRead.Ready).value
            val tuya = (ink as? InkPreference.Tuya)?.colour ?: current.tuya
            values.value = PreferenceRead.Ready(current.copy(ink = ink, tuya = tuya))
        }
        return result
    }

    override suspend fun restoreInk(ink: InkPreference, tuya: TuyaColour?): PreferenceWrite =
        error("Configuración never restores")
}

private class HandOrderingPreferences : CategoryOrderingPreferences {
    val values = MutableStateFlow<PreferenceRead<CategoryOrderingPreference>>(
        PreferenceRead.Ready(CategoryOrderingPreference())
    )
    var nextWrite: PreferenceWrite = PreferenceWrite.Saved

    override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
        values

    override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite {
        val result = nextWrite.also { nextWrite = PreferenceWrite.Saved }
        if (result is PreferenceWrite.Saved) {
            val current = (values.value as PreferenceRead.Ready).value
            values.value = PreferenceRead.Ready(current.copy(order = order))
        }
        return result
    }

    override suspend fun setCategoryOrderDirection(
        direction: CategoryOrderDirection
    ): PreferenceWrite {
        val result = nextWrite.also { nextWrite = PreferenceWrite.Saved }
        if (result is PreferenceWrite.Saved) {
            val current = (values.value as PreferenceRead.Ready).value
            values.value = PreferenceRead.Ready(current.copy(direction = direction))
        }
        return result
    }
}
