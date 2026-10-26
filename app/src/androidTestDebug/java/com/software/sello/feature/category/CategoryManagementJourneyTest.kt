package com.software.sello.feature.category

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.await
import com.software.sello.awaitDescription
import com.software.sello.awaitTag
import com.software.sello.awaitText
import com.software.sello.feature.recibo.CATEGORY_DETAIL_EDIT_TAG
import com.software.sello.feature.recibo.CATEGORY_DETAIL_TAG
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.recibo.reciboCategoryTag
import com.software.sello.hideKeyboard
import com.software.sello.navigation.MONTH_PICKER_TAG
import com.software.sello.navigation.MONTH_SWITCHER_TAG
import com.software.sello.press
import com.software.sello.revealed
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Looking after a category in the installed app with its real database. "Café" has
 * 100.000 last month, where 20.000 was spent, and this month, where 10.000 was. Each
 * figure is worked out by hand from that and compared with what is stored.
 */
@RunWith(AndroidJUnit4::class)
class CategoryManagementJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val thisMonth get() = YearMonth.from(TestCommands.today)
    private val lastMonth get() = thisMonth.minusMonths(1)
    private lateinit var cafe: String

    @Before
    fun cafeWithHistory() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        TestCommands.createCategory("Café", 100_000)
        cafe = TestCommands.categoryId("Café")
        TestCommands.setMonthLimit("Café", lastMonth, 100_000)
        TestCommands.createExpense("Café", lastMonth.atDay(10), 20_000, "Del mes pasado")
        TestCommands.createExpense("Café", TestCommands.today, 10_000, "De hoy")
    }

    @After
    fun leaveNothingBehind() {
        scenario?.takeIf { it.state != Lifecycle.State.DESTROYED }?.hideKeyboard()
        scenario?.close()
        TestCommands.showCurrentMonth()
        TestData.reset()
    }

    private fun saying(text: String) = hasContentDescription(text, substring = true)

    private val monthNames = listOf(
        "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
        "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    )

    private fun pick(month: YearMonth) {
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).performClick()
        rule.awaitTag(MONTH_PICKER_TAG)
        if (month.year < thisMonth.year) {
            rule.onNodeWithContentDescription("Año anterior").performClick()
        }
        rule.onNodeWithContentDescription("${monthNames[month.monthValue - 1]} ${month.year}")
            .performScrollTo().performClick()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.await(saying("Restante del presupuesto: 90.000 pesos."))
    }

    private fun openCafe() {
        rule.onNodeWithTag(reciboCategoryTag(cafe)).performScrollTo().performClick()
        rule.awaitTag(CATEGORY_DETAIL_TAG)
    }

    private fun edit() {
        rule.awaitTag(CATEGORY_DETAIL_EDIT_TAG)
        rule.onNodeWithTag(CATEGORY_DETAIL_EDIT_TAG).assertIsDisplayed().performClick()
        rule.awaitText("Editar categoría")
        rule.awaitDescription("Nombre")
    }

    private fun save() {
        scenario!!.hideKeyboard()
        rule.onNodeWithTag(EDITOR_SUBMIT_TAG).press()
    }

    private fun limits() = TestData.rows(
        "SELECT month, kind, limit_minor FROM month_limit ORDER BY month"
    )

    @Test
    fun renamingAndRaisingTheLimitKeepsTheSameCategoryItsExpensesAndLastMonthsFigures() {
        launch()
        openCafe()
        edit()

        rule.onNodeWithContentDescription("Nombre").performTextReplacement("Cafetería")
        rule.typeLimit("120000")
        save()

        // 120.000 less the 10.000 of this month, under the new name, the same category.
        rule.awaitText("Categoría actualizada")
        rule.awaitTag(CATEGORY_DETAIL_TAG)
        rule.await(saying("Te quedan aquí 110.000 pesos."))
        rule.onNodeWithText("Cafetería").assertIsDisplayed()
        assertEquals(listOf("$cafe|Cafetería"), TestData.rows("SELECT id, name FROM category"))
        assertEquals(2L, TestData.long("SELECT COUNT(*) FROM expense WHERE category_id = '$cafe'"))

        // Last month is as it was: 100.000 less 20.000 (H01).
        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        pick(lastMonth)
        rule.awaitDescription("Restante del presupuesto: 80.000 pesos. Cumplido.")
        assertEquals(listOf("$lastMonth|finite|100000"), limits())
    }

    @Test
    fun aFinishedMonthsLimitIsCorrectedWithoutTouchingThisMonth() {
        launch()
        pick(lastMonth)
        rule.awaitDescription("Restante del presupuesto: 80.000 pesos. Cumplido.")
        openCafe()
        edit()
        rule.onNodeWithTag(EDITOR_SCOPE_TAG).revealed().assertIsDisplayed()

        rule.typeLimit("90000")
        save()

        // 90.000 less 20.000 in that month; this month still 100.000 less 10.000.
        rule.await(hasText("Límite de", substring = true))
        rule.await(saying("Te sobraron aquí 70.000 pesos."))
        assertEquals(listOf("$lastMonth|finite|90000"), limits())
        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        TestCommands.showCurrentMonth()
        rule.await(saying("Restante del presupuesto: 90.000 pesos."))
    }

    @Test
    fun archivingTheOnlyCategoryKeepsItsHistoryAndSendsAnotarBackToCreatingOne() {
        launch()
        openCafe()
        edit()

        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).performClick()
        rule.awaitText("¿Archivar Café?")
        rule.onAllNodes(hasText("Archivar")).onLast().performClick()

        // Nothing is erased: the category, its limit this month and both expenses stay.
        rule.awaitText("Categoría archivada")
        rule.await(saying("Te quedan aquí 90.000 pesos."))
        assertEquals(
            listOf("$cafe|Café|1"),
            TestData.rows("SELECT id, name, archived FROM category")
        )
        assertEquals(2L, TestData.count("expense"))
        rule.awaitText("De hoy")

        // No category can take an expense now, so Anotar explains and offers to create one.
        Espresso.pressBack()
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.awaitDescription("Anotar un gasto")
        rule.onNodeWithContentDescription("Anotar un gasto").performClick()
        rule.awaitTag(EDITOR_PREREQUISITE_TAG)
        // The form opens with the keyboard up for the name; Back first puts it away.
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()

        // Unarchived in the same month, it takes expenses again and nothing else changed.
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        openCafe()
        edit()
        rule.onNodeWithTag(EDITOR_ARCHIVE_TAG).performClick()
        rule.awaitText("¿Desarchivar Café?")
        rule.onAllNodes(hasText("Desarchivar")).onLast().performClick()
        rule.awaitText("Categoría desarchivada")
        rule.await(saying("Te quedan aquí 90.000 pesos."))
        assertEquals(
            listOf("$cafe|Café|0"),
            TestData.rows("SELECT id, name, archived FROM category")
        )
    }
}
