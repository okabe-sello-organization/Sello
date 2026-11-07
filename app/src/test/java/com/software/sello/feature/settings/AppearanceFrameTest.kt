package com.software.sello.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.designsystem.theme.HueInk
import com.software.sello.designsystem.theme.SelloTheme
import com.software.sello.designsystem.theme.selloColors
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.platform.PhoneColor
import com.software.sello.platform.SystemColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The frame MainActivity draws the app in (SELLO-203): it reads the phone's colour again each
 * time Sello is started, and a new colour changes only the ink, never a draft.
 */
@RunWith(AndroidJUnit4::class)
class AppearanceFrameTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** A lifecycle the test moves by hand, as the system moves the activity's. */
    private class HandLifecycle : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }

    private val stored = MutableStateFlow<PreferenceRead<AppearancePreference>>(
        PreferenceRead.Ready(AppearancePreference(ThemePreference.Light, InkPreference.Sistema))
    )
    private val preferences = object : AppearancePreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = stored
        override suspend fun setTheme(theme: ThemePreference) = error("Not written here")
        override suspend fun setInk(ink: InkPreference): PreferenceWrite = error("Not written here")
        override suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?): PreferenceWrite =
            error("Not written here")
    }

    private var reads = 0
    private var phone: PhoneColor = PhoneColor.Hue(240)
    private val systemColor = SystemColor {
        reads++
        phone
    }
    private val owner = HandLifecycle()

    @Test
    fun thePhonesColourIsReadAgainEachTimeSelloIsStarted() {
        val model = AppearanceViewModel(preferences, systemColor)
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AppearanceFrame(model) {}
            }
        }
        rule.waitForIdle()
        // Read once when Sello started; not again until it is on screen.
        assertEquals(1, reads)

        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { assertEquals(2, reads) }

        // Away, then back: the wallpaper may have changed meanwhile.
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.runOnIdle { assertEquals(3, reads) }
    }

    @Test
    fun aNewPhoneColourChangesTheInkAndKeepsTheDraftAcrossARecreation() {
        owner.registry.currentState = Lifecycle.State.STARTED
        val model = AppearanceViewModel(preferences, systemColor)
        var brand = Color.Unspecified
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                AppearanceFrame(model) {
                    brand = SelloTheme.colors.brand
                    var draft by rememberSaveable { mutableStateOf("") }
                    BasicTextField(draft, { draft = it }, Modifier.testTag(DRAFT))
                }
            }
        }
        rule.onNodeWithTag(DRAFT).performTextInput("Almuerzo")
        rule.runOnIdle { assertEquals(selloColors(HueInk(240), isDark = false).brand, brand) }

        // A new wallpaper: Android recreates the activity, which starts again.
        phone = PhoneColor.Hue(24)
        restoration.emulateSavedInstanceStateRestore()

        rule.onNodeWithTag(DRAFT).assertTextEquals("Almuerzo")
        rule.runOnIdle { assertEquals(selloColors(HueInk(24), isDark = false).brand, brand) }

        // And when Sello is only away and back, with no recreation, likewise.
        phone = PhoneColor.Hue(140)
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        rule.runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED }
        rule.onNodeWithTag(DRAFT).assertTextEquals("Almuerzo")
        rule.runOnIdle { assertEquals(selloColors(HueInk(140), isDark = false).brand, brand) }
    }

    private companion object {
        const val DRAFT = "draft"
    }
}
