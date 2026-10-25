package com.software.sello.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogExamplesTest {
    @Test
    fun exampleIdsAreStable() {
        // Tests and deep references rely on these; changing one is a deliberate edit here.
        assertEquals(
            listOf(
                "foundations.colors",
                "foundations.typography",
                "foundations.money-text",
                "foundations.shape-spacing",
                "foundations.motion",
                "foundations.icons",
                "paper.slip",
                "paper.tear-line",
                "paper.leader-line",
                "paper.action-line",
                "paper.total-line",
                "paper.key-value",
                "paper.answer-slip",
                "paper.footnotes",
                "paper.suggestion",
                "paper.plan-slip",
                "stamps.stamp",
                "money.money-text",
                "money.counting",
                "categories.category-circle",
                "categories.category-cell",
                "inputs.button",
                "inputs.chip",
                "inputs.chip-rows",
                "inputs.field-pill",
                "inputs.form-field",
                "inputs.switch",
                "inputs.settings-controls",
                "inputs.icon-choice",
                "inputs.icon-strip",
                "inputs.amount",
                "inputs.keypad",
                "inputs.note-composer",
                "inputs.entry",
                "navigation.scaffold",
                "navigation.entry-dock",
                "navigation.navigation-bar",
                "navigation.month-bar",
                "navigation.month-switcher",
                "navigation.past-month-ribbon",
                "charts.columns",
                "charts.line",
                "charts.split",
                "charts.comparison",
                "charts.ranking",
                "feedback.confirm-slip",
                "feedback.empty-slip",
                "feedback.error-slip",
                "feedback.notice",
                "feedback.bullet-list",
                "feedback.backup-states",
                "feedback.slip-skeleton",
                "feedback.danger-and-ticket",
                "feedback.celebration",
                "goals.milestones",
                "goals.goal-pill",
                "goals.goal-form",
                "goals.goal-cards",
                "goals.movements",
                "goals.investments"
            ),
            catalogExamples.map { it.id }
        )
    }

    @Test
    fun everyIdIsUniqueAndNamedAfterItsGroup() {
        val ids = catalogExamples.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // One entry per component: no two examples share a title either.
        val titles = catalogExamples.map { it.title }
        assertEquals(titles.size, titles.toSet().size)
        catalogExamples.forEach {
            assertTrue(it.id, Regex("${it.group.id}\\.[a-z]+(-[a-z]+)*").matches(it.id))
        }
    }

    @Test
    fun groupsCoverTheTenComponentTypesInOrder() {
        assertEquals(
            listOf(
                "foundations", "paper", "stamps", "money", "categories",
                "inputs", "navigation", "charts", "feedback", "goals"
            ),
            CatalogGroup.entries.map { it.id }
        )
    }
}
