package com.software.sello.designsystem.component

import com.software.sello.designsystem.icon.SelloIcon

/** Synthetic receipt lines for the bulk receipt's tests, worded as the app words them. */
internal fun categoryLine(key: String, title: String, digits: String, note: String? = null) =
    BulkLine(key, SelloIcon.Restaurant, BulkMarkTone.Category, title, pesos(digits), detail = note)

internal val threeExpenses = listOf(
    BulkLine(
        "1",
        SelloIcon.DirectionsBus,
        BulkMarkTone.Category,
        "Transporte",
        pesos("18.400"),
        detail = "Uber"
    ),
    BulkLine(
        "2",
        SelloIcon.MedicalServices,
        BulkMarkTone.Category,
        "Salud",
        pesos("75.100"),
        detail = "Farmacia Pasteur"
    ),
    BulkLine(
        "3",
        SelloIcon.Restaurant,
        BulkMarkTone.Category,
        "Comida",
        pesos("32.500"),
        detail = "Carulla"
    )
)

internal val expenseTotal = BulkTotal("Total", pesos("126.000"))

internal val missingCategory = BulkLine(
    "2",
    mark = null,
    markTone = BulkMarkTone.Category,
    title = "Farmacia Pasteur",
    amount = pesos("75.100"),
    reason = "Elige una categoría"
)
