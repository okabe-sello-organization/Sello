package com.software.sello.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Violeta: Cobalto with its own brand, paper and line colours; the rest is Cobalto's. */
internal val VioletaLight = lightPalette(
    family = SelloInk.Violeta,
    brand = Color(0xFF5B2BD6),
    onBrand = CobaltoLight.onBrand,
    brandSoft = Color(0xFFE6DCFF),
    onBrandSoft = Color(0xFF2A0F78),
    desk = Color(0xFFE2DAF6),
    paper = CobaltoLight.paper,
    paperAlt = Color(0xFFF3EEFC),
    paperDim = Color(0xFFDCD2F2),
    ink = Color(0xFF150F2E),
    inkSoft = Color(0xFF4A4370),
    outline = Color(0xFF75679F),
    rule = Color(0xFFC4B7E6),
    pastBar = Color(0xFF5F5887),
    onPastBar = CobaltoLight.onPastBar,
    pastBarSoft = Color(0xFFE7E1F6),
    onPastBarSoft = CobaltoLight.onPastBarSoft
)

internal val VioletaDark = darkPalette(
    family = SelloInk.Violeta,
    brand = Color(0xFFB79CFF),
    onBrand = Color(0xFF22085E),
    brandSoft = Color(0xFF3A1F8F),
    onBrandSoft = Color(0xFFE9DFFF),
    desk = Color(0xFF0E0A1C),
    paper = Color(0xFF1A1433),
    paperAlt = Color(0xFF231B45),
    paperDim = Color(0xFF30265C),
    ink = Color(0xFFF1EEFB),
    inkSoft = Color(0xFFB3ABD6),
    outline = Color(0xFF8E82B8),
    rule = Color(0xFF372C66),
    band = Color(0xFF4420B0),
    onBand = CobaltoDark.onBand,
    goalBand = CobaltoDark.goalBand,
    investmentBand = Color(0xFF2E2750),
    pastBar = Color(0xFFABA3CF),
    onPastBar = CobaltoDark.onPastBar,
    pastBarSoft = Color(0xFF30265C),
    onPastBarSoft = CobaltoDark.onPastBarSoft,
    lightInk = VioletaLight.ink
)
