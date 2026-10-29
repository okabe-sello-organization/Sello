package com.software.sello.feature.reset

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateCategory
import com.software.sello.domain.port.CreateExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.feature.backup.BACKUP_CARD_RESET_TAG
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.settings.SETTINGS_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.platform.ProcessAppRestart
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.dsl.module

/**
 * Borrar todos los datos in the installed app, against its own database: Configuración
 * → Borrar todos los datos, the counts, the word, the outcome, then a new category and
 * expense in the empty history. The restart that follows is recorded instead of ending
 * the test process.
 */
@RunWith(AndroidJUnit4::class)
class ResetReuseJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val koin get() = GlobalContext.get()

    /** The field is found by its label, which is also what a screen reader calls it. */
    private val word get() = rule.onNodeWithContentDescription("Escribe BORRAR para confirmar")
    private var scenario: ActivityScenario<MainActivity>? = null
    private var restarts = 0
    private lateinit var zone: String

    @Before
    fun syntheticHistory() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        zone = TestData.rows("SELECT financial_zone FROM profile").single()
        koin.loadModules(listOf(module { single<AppRestart> { AppRestart { restarts++ } } }))
        TestCommands.createCategory("Mercado", 50_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 7_000, note = "Plaza")
        TestCommands.createExpense("Mercado", TestCommands.today, 8_000)
        runBlocking {
            koin.get<AppearancePreferences>().setTheme(ThemePreference.Dark)
            koin.get<AppearancePreferences>().setInk(InkPreference.Violeta)
        }
    }

    @After
    fun leaveNoFixture() {
        scenario?.close()
        koin.loadModules(listOf(module { single<AppRestart> { ProcessAppRestart(context) } }))
        TestData.reset()
        TestData.execute("DELETE FROM operation_receipt")
        // An erase advanced the generation and took the device zone; others expect these.
        TestData.execute("UPDATE profile SET generation = 1, financial_zone = '$zone'")
        File(context.filesDir, "sello-reset").deleteRecursively()
        TestCommands.showCurrentMonth()
        runBlocking {
            koin.get<AppearancePreferences>().setTheme(ThemePreference.System)
            koin.get<AppearancePreferences>().setInk(InkPreference.Cobalto)
            koin.get<CategoryOrderingPreferences>().setCategoryOrder(CategoryOrder.MostUsed)
            koin.get<CategoryOrderingPreferences>()
                .setCategoryOrderDirection(CategoryOrderDirection.Descending)
        }
    }

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun operation() = OperationId.of(UUID.randomUUID().toString()).value()

    private fun openReset() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_TAG)
        rule.onNodeWithTag(BACKUP_CARD_RESET_TAG).performScrollTo().performClick()
        rule.awaitTag(RESET_COUNTS_TAG)
    }

    private fun history() = listOf(
        TestData.rows("SELECT name FROM category"),
        TestData.rows("SELECT amount_minor FROM expense ORDER BY sequence"),
        TestData.rows("SELECT generation FROM profile")
    )

    @Test
    fun anEraseConfirmedWithTheWordLeavesAnEmptyHistoryThatCanBeUsedAgain() {
        openReset()
        rule.onNodeWithText("Se borran 1 categoría, 2 gastos y 0 ingresos", substring = true)
            .assertExists()
        rule.onNodeWithText("no se borran", substring = true).assertExists()
        rule.onNodeWithTag(RESET_CONTINUE_TAG).performScrollTo().performClick()
        word.performScrollTo().performTextInput("borrar")
        rule.onNodeWithTag(RESET_CONFIRM_TAG).performScrollTo().assertIsNotEnabled()
        // Nothing has changed before the word.
        assertEquals(listOf(listOf("Mercado"), listOf("7000", "8000"), listOf("1")), history())

        word.performTextReplacement("BORRAR")
        rule.onNodeWithTag(RESET_CONFIRM_TAG).performScrollTo().assertIsEnabled().performClick()
        rule.awaitTag(RESET_DONE_TAG)

        assertEquals(listOf(emptyList(), emptyList(), listOf("2")), history())
        assertEquals(
            listOf("history.reset"),
            TestData.rows("SELECT kind FROM operation_receipt")
        )
        runBlocking {
            assertEquals(
                PreferenceRead.Ready(AppearancePreference()),
                koin.get<AppearancePreferences>().observeAppearance().first()
            )
        }
        rule.onNodeWithTag(RESET_LEAVE_TAG).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, restarts)

        // The empty history takes a new category and expense under its generation.
        val categories = koin.get<CategoryCommands>()
        runBlocking {
            val created = categories.submit(
                CreateCategory(
                    operation(),
                    2,
                    CategoryName.of("Comida").value(),
                    IconKey.of("home").value(),
                    BudgetLimit.Unlimited
                )
            )
            assertTrue("$created", created is CommandOutcome.Committed)
            val expense = koin.get<ExpenseCommands>().create(
                CreateExpense(
                    operation(),
                    2,
                    CategoryId.of(TestCommands.categoryId("Comida")).value(),
                    TransactionAmount.of(Money.cop(12_000)).value(),
                    TestCommands.today,
                    Note.of(null).value()
                )
            )
            assertTrue("$expense", expense is ExpenseCommandOutcome.Committed)
        }
        assertEquals(listOf(listOf("Comida"), listOf("12000"), listOf("2")), history())
    }
}
