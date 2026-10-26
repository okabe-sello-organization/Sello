package com.software.sello.feature.settings

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.Currency
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
import com.software.sello.feature.investments.HandInvestmentPreferences
import com.software.sello.feature.investments.HandInvestmentReads
import com.software.sello.feature.investments.rateEntry
import com.software.sello.feature.investments.ratesBoard
import com.software.sello.feature.investments.testMoney
import com.software.sello.feature.recurrence.HandScheduleReads
import java.time.LocalDate
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

    private val investments = HandInvestmentReads(InvestmentBoardState.Loading)
    private val investmentSettings = HandInvestmentPreferences()

    private fun settings() = SettingsViewModel(
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

        investments.board.value = InvestmentBoardState.Ready(ratesBoard())
        runCurrent()
        assertEquals(RateLine.None, model.state.value.rate)

        // IN31: 4.000,00 on 24 oct stays current after 3.900,00 dated 22 oct is entered.
        investments.board.value = InvestmentBoardState.Ready(
            ratesBoard(rateEntry(400_000, "2026-10-24", 1), rateEntry(390_000, "2026-10-22", 2))
        )
        runCurrent()
        assertEquals(RateLine.Read("4.000,00", LocalDate.of(2026, 10, 24)), model.state.value.rate)

        investments.board.value =
            InvestmentBoardState.Failed(StorageFailure.Unavailable("hand"), null)
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
}

private class HandAppearancePreferences : AppearancePreferences {
    val values = MutableStateFlow<PreferenceRead<AppearancePreference>>(
        PreferenceRead.Ready(AppearancePreference())
    )

    override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = values

    override suspend fun setTheme(theme: ThemePreference): PreferenceWrite {
        val current = (values.value as PreferenceRead.Ready).value
        values.value = PreferenceRead.Ready(current.copy(theme = theme))
        return PreferenceWrite.Saved
    }

    override suspend fun setInk(ink: InkPreference): PreferenceWrite {
        val current = (values.value as PreferenceRead.Ready).value
        values.value = PreferenceRead.Ready(current.copy(ink = ink))
        return PreferenceWrite.Saved
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
