package com.software.sello.presentation.appearance

import androidx.annotation.StringRes
import com.software.sello.R
import com.software.sello.designsystem.theme.SelloInk
import com.software.sello.domain.model.InkPreference

/** One ink a person can pick: its stored preference, its palettes and its name. */
class InkChoice(val preference: InkPreference, val ink: SelloInk, @StringRes val label: Int)

/** Every ink in the picker, in its order. Each preference and each palette appears once. */
val InkChoices = listOf(
    InkChoice(InkPreference.Cobalto, SelloInk.Cobalto, R.string.settings_ink_cobalto),
    InkChoice(InkPreference.Violeta, SelloInk.Violeta, R.string.settings_ink_violeta)
)

val InkPreference.choice: InkChoice
    get() = InkChoices.first { it.preference == this }
