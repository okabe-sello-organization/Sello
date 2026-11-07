package com.software.sello.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.platform.SystemColor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The root needs only the usable appearance value and the phone's colour, for Sistema, never
 * the settings write API.
 */
class AppearanceViewModel(
    preferences: AppearancePreferences,
    private val systemColor: SystemColor
) : ViewModel() {
    val state: StateFlow<AppearanceState> = combine(
        preferences.observeAppearance().map { read ->
            when (read) {
                is PreferenceRead.Ready -> AppearanceState(read.value, loading = false)

                is PreferenceRead.Failed -> AppearanceState(
                    appearance = read.lastGood ?: AppearancePreference(),
                    loading = false,
                    failure = read.failure
                )
            }
        },
        systemColor.hue
    ) { state, hue -> state.copy(systemHue = hue) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AppearanceState(loading = true, systemHue = systemColor.hue.value)
        )

    /**
     * Sello is on screen again: the phone's colour may have changed while it was away, and
     * Sistema takes the new one. Only the ink changes; no screen's state is touched.
     */
    fun onShown() = systemColor.refresh()
}

/** [systemHue] is the phone's colour, for Sistema: null when it has none. */
data class AppearanceState(
    val appearance: AppearancePreference = AppearancePreference(),
    val loading: Boolean = false,
    val failure: PreferenceFailure? = null,
    val systemHue: Int? = null
)
