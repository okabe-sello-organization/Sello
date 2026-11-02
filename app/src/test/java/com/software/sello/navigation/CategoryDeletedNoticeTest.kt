package com.software.sello.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.presentation.corrections.CorrectionNotice
import com.software.sello.showSello
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What Recibo says once a category's deletion has its receipt (SELLO-138). On the JVM. */
@RunWith(AndroidJUnit4::class)
class CategoryDeletedNoticeTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun eachWayOfDeletingIsSaidWithItsCount() {
        var notice by mutableStateOf(CorrectionNotice.CategoryDeleted("Café", 34, "Comida"))
        rule.showSello { CorrectionNoticeBar(notice) {} }

        rule.onNodeWithText("Café se borró; sus 34 gastos están en Comida").assertIsDisplayed()
        notice = CorrectionNotice.CategoryDeleted("Café", 34, null)
        rule.onNodeWithText("Café y sus 34 gastos se borraron").assertIsDisplayed()
        notice = CorrectionNotice.CategoryDeleted("Café", 0, null)
        rule.onNodeWithText("Café se borró").assertIsDisplayed()
        notice = CorrectionNotice.CategoryDeleted("Café", 1, "Comida")
        rule.onNodeWithText("Café se borró; su gasto está en Comida").assertIsDisplayed()
        notice = CorrectionNotice.CategoryDeleted("Café", 1, null)
        rule.onNodeWithText("Café y su gasto se borraron").assertIsDisplayed()
    }
}
