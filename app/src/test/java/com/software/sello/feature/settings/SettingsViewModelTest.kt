package com.software.sello.feature.settings

import androidx.lifecycle.SavedStateHandle
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.ThemePreference
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
import com.software.sello.testing.testMoney
import java.time.LocalDate
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

    private fun settings(handle: SavedStateHandle = SavedStateHandle()) = SettingsViewModel(
        handle,
        appearance,
        ordering,
        investmentSettings,
        HandScheduleReads(),
        investments,
        testMoney()
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

    // SELLO-202: Tu tinta.

    private val red = InkPreference.Tuya(InkHue.of(22)!!)

    private fun SettingsViewModel.tuya() = state.value.tuya

    @Test
    fun tuTintaOpensOnTheInkInUseAndNothingIsSavedUntilUsarEstaTinta() = runTest {
        val model = settings()
        runCurrent()

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(TuyaDraft(hue = 267), model.tuya())

        model.onAction(SettingsAction.Tuya.HueChanged(22))
        runCurrent()
        assertEquals(TuyaDraft(hue = 22), model.tuya())
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(listOf<InkPreference>(red), appearance.inkWrites)
        assertEquals(red, model.state.value.appearance.ink)
        assertNull(model.tuya())
        assertFalse(model.state.value.busy)
    }

    @Test
    fun tuTintaStartsOnVioletaOrOnTuyasOwnHue() = runTest {
        val model = settings()
        appearance.values.value =
            PreferenceRead.Ready(AppearancePreference(ink = InkPreference.Violeta))
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(286, model.tuya()?.hue)
        model.onAction(SettingsAction.Tuya.Cancel)

        appearance.values.value = PreferenceRead.Ready(AppearancePreference(ink = red))
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(22, model.tuya()?.hue)
    }

    @Test
    fun cancelingForgetsTheHueAndKeepsTheInk() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.HueChanged(147))
        model.onAction(SettingsAction.Tuya.Cancel)
        runCurrent()

        assertNull(model.tuya())
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)

        model.onAction(SettingsAction.Tuya.Open)
        runCurrent()
        assertEquals(267, model.tuya()?.hue)
    }

    @Test
    fun aFailedSaveKeepsTheSheetAndHueAndTheInkInUseThenReintentarSaves() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.HueChanged(22))
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        assertEquals(TuyaDraft(hue = 22, failed = true), model.tuya())
        assertEquals(InkPreference.Cobalto, model.state.value.appearance.ink)
        // The sheet says it; the screen behind does not repeat it.
        assertNull(model.state.value.failure)
        assertFalse(model.state.value.busy)

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(listOf<InkPreference>(red, red), appearance.inkWrites)
        assertEquals(red, model.state.value.appearance.ink)
        assertNull(model.tuya())
    }

    @Test
    fun movingTheHueAfterAFailureClearsTheMessage() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        appearance.nextInkWrite = PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        model.onAction(SettingsAction.Tuya.HueChanged(30))
        runCurrent()
        assertEquals(TuyaDraft(hue = 30), model.tuya())
    }

    @Test
    fun whileSavingASecondTapAndEveryOtherSheetActionDoNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.HueChanged(22))
        val gate = CompletableDeferred<Unit>()
        appearance.inkGate = gate

        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Use)
        model.onAction(SettingsAction.Tuya.HueChanged(40))
        model.onAction(SettingsAction.Tuya.Cancel)
        model.onAction(SettingsAction.InkChosen(InkPreference.Violeta))
        runCurrent()

        assertEquals(listOf<InkPreference>(red), appearance.inkWrites)
        assertEquals(TuyaDraft(hue = 22, saving = true), model.tuya())
        assertTrue(model.state.value.busy)

        gate.complete(Unit)
        runCurrent()
        assertEquals(red, model.state.value.appearance.ink)
        assertNull(model.tuya())
        assertFalse(model.state.value.busy)
    }

    @Test
    fun theHueBeingChosenSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        val model = settings(handle)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.HueChanged(200))
        runCurrent()

        // What the system keeps of the handle is all a new process gets.
        val restored = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })
        val again = settings(restored)
        runCurrent()

        assertEquals(TuyaDraft(hue = 200), again.tuya())
        assertEquals(InkPreference.Cobalto, again.state.value.appearance.ink)
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun aHueOutsideTheCircleIsNeverTakenOrSaved() = runTest {
        val handle = SavedStateHandle()
        val model = settings(handle)
        runCurrent()
        model.onAction(SettingsAction.Tuya.Open)
        model.onAction(SettingsAction.Tuya.HueChanged(359))
        model.onAction(SettingsAction.Tuya.HueChanged(360))
        model.onAction(SettingsAction.Tuya.HueChanged(-1))
        runCurrent()
        assertEquals(359, model.tuya()?.hue)

        // A kept hue Sello could not have written opens no sheet and saves nothing.
        val damaged = SavedStateHandle(handle.keys().associateWith { 360 })
        val again = settings(damaged)
        runCurrent()
        assertNull(again.tuya())
        again.onAction(SettingsAction.Tuya.Use)
        runCurrent()
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
    }

    @Test
    fun aHueChangeWithTheSheetClosedOpensNothing() = runTest {
        val model = settings()
        runCurrent()
        model.onAction(SettingsAction.Tuya.HueChanged(22))
        model.onAction(SettingsAction.Tuya.Use)
        runCurrent()

        assertNull(model.tuya())
        assertEquals(emptyList<InkPreference>(), appearance.inkWrites)
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
            val current = (values.value as PreferenceRead.Ready).value
            values.value = PreferenceRead.Ready(current.copy(ink = ink))
        }
        return result
    }
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
