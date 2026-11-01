package com.software.sello.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Cobalto, the base ink: the first-run default. */
internal val CobaltoLight = SelloColors(
    family = SelloInk.Cobalto,
    isDark = false,
    brand = Color(0xFF1F3BE0),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0xFFDCE2FF),
    onBrandSoft = Color(0xFF0B1C7A),
    desk = Color(0xFFD9E1F7),
    paper = Color(0xFFFFFFFF),
    paperAlt = Color(0xFFEEF2FC),
    paperDim = Color(0xFFD3DCF3),
    ink = Color(0xFF0E1630),
    inkSoft = Color(0xFF434E72),
    outline = Color(0xFF6975A0),
    rule = Color(0xFFB9C5E6),
    band = Color(0xFF1F3BE0),
    onBand = Color(0xFFFFFFFF),
    goalBand = Color(0xFF0A6B4A),
    investmentBand = Color(0xFF0E1630),
    pastBar = Color(0xFF566287),
    onPastBar = Color(0xFFFFFFFF),
    pastBarSoft = Color(0xFFE1E7F6),
    onPastBarSoft = Color(0xFF17203C),
    semantic = LightSemantic,
    categories = LightCategories,
    investments = LightInvestments
)

internal val CobaltoDark = SelloColors(
    family = SelloInk.Cobalto,
    isDark = true,
    brand = Color(0xFF8FA6FF),
    onBrand = Color(0xFF06175C),
    brandSoft = Color(0xFF23368F),
    onBrandSoft = Color(0xFFDDE4FF),
    desk = Color(0xFF090D1C),
    paper = Color(0xFF151C36),
    paperAlt = Color(0xFF1D2646),
    paperDim = Color(0xFF2A3560),
    ink = Color(0xFFEEF1FB),
    inkSoft = Color(0xFFA9B3D6),
    outline = Color(0xFF8490BA),
    rule = Color(0xFF2F3B66),
    band = Color(0xFF2238B8),
    onBand = Color(0xFFFFFFFF),
    goalBand = Color(0xFF0F5C40),
    investmentBand = Color(0xFF2C3454),
    pastBar = Color(0xFFA7B2D2),
    onPastBar = Color(0xFF17203C),
    pastBarSoft = Color(0xFF2A3560),
    onPastBarSoft = Color(0xFFE1E7F6),
    semantic = DarkSemantic,
    categories = DarkCategories,
    investments = DarkInvestments
)
