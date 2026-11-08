package com.software.sello.feature.guide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.revealed
import com.software.sello.showSello
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Guía de Sello's list and pages, drawn as given. On the JVM (ADR 0022). */
@RunWith(AndroidJUnit4::class)
class GuideScreenTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun theListHasTheMockedSectionsAndPagesInOrder() {
        rule.showSello { GuideScreen(PaddingValues(), onOpen = {}) }

        val shown = listOf(
            "Empieza aquí",
            "Tu primer mes en 3 pasos",
            "Gestos que ahorran tiempo",
            "Por pantalla",
            "Recibo",
            "Anotar y Anotar varios",
            "Resumen y Tu año",
            "Metas",
            "Inversiones y dólar",
            "Tus datos",
            "Respaldo"
        )
        val tops = shown.map {
            rule.onNodeWithText(it).revealed().assertIsDisplayed().getUnclippedBoundsInRoot().top
        }
        assertEquals(tops.sorted(), tops)
        rule.onNodeWithText("Categorías, límites y tu primer gasto").assertIsDisplayed()
        rule.onNodeWithText("Atajos para editar, borrar y limpiar").assertIsDisplayed()
        rule.onNodeWithText("Tus datos viven solo en este teléfono").revealed().assertIsDisplayed()
    }

    @Test
    fun aRowOpensItsPage() {
        val opened = mutableListOf<GuidePage>()
        rule.showSello { GuideScreen(PaddingValues(), onOpen = { opened += it }) }

        GuidePage.entries.forEach { rule.onNodeWithTag(guideRowTag(it)).revealed().performClick() }

        assertEquals(GuidePage.entries, opened)
    }

    @Test
    fun aPageShowsItsTopics() {
        rule.showSello { GuidePageScreen(GuidePage.Entry, PaddingValues()) }

        rule.onNodeWithText("Dos formas de llegar").revealed().assertIsDisplayed()
        rule.onNodeWithText(
            "Debajo del monto, toca Otro gasto: lo que ya escribiste pasa a ser el primer " +
                "renglón. O dentro de una categoría, toca Anotar varios: cada renglón " +
                "empieza en esa categoría."
        ).revealed().assertIsDisplayed()
    }

    @Test
    fun aPageIsNamedByAnIdThatNeverRepeats() {
        GuidePage.entries.forEach { assertEquals(it, GuidePage.byId(it.id)) }
        assertEquals(GuidePage.entries.size, GuidePage.entries.map { it.id }.toSet().size)
        assertNull(GuidePage.byId("trucos"))
    }
}
