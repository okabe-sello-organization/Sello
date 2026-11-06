package com.software.sello.testing

import com.software.sello.presentation.month.MonthNames

/** The app's own month names, as `strings.xml` writes them. */
fun testMonthNames() = MonthNames(
    full = listOf(
        "Enero",
        "Febrero",
        "Marzo",
        "Abril",
        "Mayo",
        "Junio",
        "Julio",
        "Agosto",
        "Septiembre",
        "Octubre",
        "Noviembre",
        "Diciembre"
    ),
    short = listOf(
        "Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Sep", "Oct", "Nov", "Dic"
    ),
    titleFormat = "%1\$s %2\$d"
)
