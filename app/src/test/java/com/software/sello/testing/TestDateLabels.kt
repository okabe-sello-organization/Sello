package com.software.sello.testing

import com.software.sello.presentation.date.DateLabels

/** The app's own short names, as `strings.xml` writes them. */
fun testDateLabels() = DateLabels(
    weekdays = listOf("lun", "mar", "mi\u00e9", "jue", "vie", "s\u00e1b", "dom"),
    months = listOf(
        "ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"
    ),
    todayFormat = "Hoy, %1\$s"
)
