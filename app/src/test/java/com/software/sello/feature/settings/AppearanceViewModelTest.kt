package com.software.sello.feature.settings

import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.SystemColor
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
import org.junit.Before
import org.junit.Test

/** The app's ink follows the phone's colour for Sistema (SELLO-203). */
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    @Before
    fun main() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun resetMain() = Dispatchers.resetMain()

    private val stored = MutableStateFlow<PreferenceRead<AppearancePreference>>(
        PreferenceRead.Ready(AppearancePreference(ink = InkPreference.Sistema))
    )
    private val preferences = object : AppearancePreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = stored
        override suspend fun setTheme(theme: ThemePreference) = error("Not written here")
        override suspend fun setInk(ink: InkPreference): PreferenceWrite = error("Not written here")
        override suspend fun restoreInk(ink: InkPreference, tuya: TuyaColour?): PreferenceWrite =
            error("Not written here")
    }

    /** How many times the phone's colour was read, and what it says now. */
    private var reads = 0
    private var phone: PhoneColor = PhoneColor.Hue(140)

    private fun model() = AppearanceViewModel(
        preferences,
        SystemColor {
            reads++
            phone
        }
    )

    @Test
    fun thePhonesColourIsReadAgainEachTimeSelloIsOnScreen() = runTest {
        val model = model()
        runCurrent()
        assertEquals(PhoneColor.Hue(140), model.state.value.phone)
        assertEquals(InkPreference.Sistema, model.state.value.appearance.ink)

        // The wallpaper changed while Sello was away: nothing moves until it is back.
        phone = PhoneColor.Hue(24)
        runCurrent()
        assertEquals(PhoneColor.Hue(140), model.state.value.phone)

        model.onShown()
        runCurrent()
        assertEquals(PhoneColor.Hue(24), model.state.value.phone)
        assertEquals(2, reads)
    }

    @Test
    fun onAndroid11ThePhoneHasNoColourAndSistemaIsDrawnInCobalto() = runTest {
        phone = PhoneColor.None
        val model = model()
        runCurrent()
        model.onShown()
        runCurrent()

        assertEquals(PhoneColor.None, model.state.value.phone)
        assertEquals(
            AppearanceTheme(SelloInk.Cobalto, false),
            model.state.value.appearance.asSelloTheme(false, model.state.value.phone)
        )
    }
}
