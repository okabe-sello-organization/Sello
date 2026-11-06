package com.software.sello.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

/**
 * Feeds every Material slot from Sello tokens so stock components (dialogs, pickers,
 * ripple) never show a Material baseline or wallpaper-derived colour.
 */
fun SelloColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = brandSoft,
        onPrimaryContainer = onBrandSoft,
        inversePrimary = brandSoft,
        secondary = pastBar,
        onSecondary = onPastBar,
        secondaryContainer = pastBarSoft,
        onSecondaryContainer = onPastBarSoft,
        tertiary = semantic.gain,
        onTertiary = semantic.onGain,
        tertiaryContainer = semantic.gainSoft,
        onTertiaryContainer = semantic.onGainSoft,
        background = desk,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = paperAlt,
        onSurfaceVariant = inkSoft,
        surfaceTint = brand,
        inverseSurface = ink,
        inverseOnSurface = paper,
        error = semantic.loss,
        onError = semantic.onLoss,
        errorContainer = semantic.lossSoft,
        onErrorContainer = semantic.onLossSoft,
        outline = outline,
        outlineVariant = rule,
        scrim = scrim,
        surfaceBright = paper,
        surfaceDim = paperDim,
        surfaceContainerLowest = paper,
        surfaceContainerLow = paperAlt,
        surfaceContainer = paperAlt,
        surfaceContainerHigh = paperDim,
        surfaceContainerHighest = paperDim,
        primaryFixed = brandSoft,
        primaryFixedDim = brandSoft,
        onPrimaryFixed = onBrandSoft,
        onPrimaryFixedVariant = onBrandSoft,
        secondaryFixed = pastBarSoft,
        secondaryFixedDim = pastBarSoft,
        onSecondaryFixed = onPastBarSoft,
        onSecondaryFixedVariant = onPastBarSoft,
        tertiaryFixed = semantic.gainSoft,
        tertiaryFixedDim = semantic.gainSoft,
        onTertiaryFixed = semantic.onGainSoft,
        onTertiaryFixedVariant = semantic.onGainSoft
    )
}

internal fun SelloShapes.toMaterialShapes(): Shapes = Shapes(
    extraSmall = bar,
    small = control,
    medium = button,
    large = sheet,
    extraLarge = sheet
)
