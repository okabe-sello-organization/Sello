package com.software.sello.feature.backup

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.TestCommands
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.AppRestart
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.feature.recibo.RECIBO_SUMMARY_TAG
import com.software.sello.feature.settings.SETTINGS_BACKUP_TAG
import com.software.sello.feature.settings.SETTINGS_TAG
import com.software.sello.navigation.SETTINGS_ACTION_TAG
import com.software.sello.platform.ProcessAppRestart
import com.software.sello.platform.TestDocuments
import com.software.sello.presentation.restore.RESTORE_DONE_TAG
import com.software.sello.presentation.restore.RESTORE_LEAVE_TAG
import java.io.File
import java.time.Instant
import java.time.YearMonth
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
 * Restoring a backup in the installed app: Configuración → Respaldo → Elegir archivo,
 * a preview, confirmation, and the outcome, against the app's own database. The system
 * picker is answered by the test with a document from a real content provider, and
 * the restart that follows a restore is recorded instead of ending the test process.
 */
@RunWith(AndroidJUnit4::class)
class RestoreJourneyTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val koin get() = GlobalContext.get()
    private var scenario: ActivityScenario<MainActivity>? = null
    private var picker: Instrumentation.ActivityMonitor? = null
    private var restarts = 0
    private lateinit var zone: String

    @Before
    fun syntheticHistory() {
        TestData.reset()
        TestCommands.showCurrentMonth()
        zone = TestData.rows("SELECT financial_zone FROM profile").single()
        koin.loadModules(listOf(module { single<AppRestart> { AppRestart { restarts++ } } }))
        // The current history: two expenses in Mercado.
        TestCommands.createCategory("Mercado", 50_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 7_000)
        TestCommands.createExpense("Mercado", TestCommands.today, 8_000)
    }

    @After
    fun leaveNoFixture() {
        picker?.let(instrumentation::removeMonitor)
        scenario?.close()
        koin.loadModules(listOf(module { single<AppRestart> { ProcessAppRestart(context) } }))
        // The session lives as long as the process; each test starts it from nothing.
        koin.get<RestoreSession>().run {
            dismiss()
            finish()
            cancel()
        }
        TestData.reset()
        // A restore advanced the generation; every other test prepares against 1.
        TestData.execute("UPDATE profile SET generation = 1, financial_zone = '$zone'")
        File(context.filesDir, "sello-restore").deleteRecursively()
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

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun amount(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).value()

    /** G01 in the stored zone, dark violet and alphabetical; written by the real encoder. */
    private fun backup(): ByteArray {
        val day = TestCommands.today
        val at = Instant.parse("2026-10-22T14:30:00Z")
        val comida = CategoryId.of(id(1)).value()
        fun expense(number: Int, daysAgo: Long, pesos: Long) = Expense(
            ExpenseId.of(id(number)).value(), comida, day.minusDays(daysAgo), number.toLong(),
            amount(pesos), null, 1, at, at
        )
        return BackupCodec().encode(
            BackupContents(
                exportedAt = at,
                financial = FinancialSnapshot(
                    zone = java.time.ZoneId.of(zone),
                    revision = 9,
                    categories = listOf(
                        Category(
                            comida,
                            CategoryName.of("Comida").value(),
                            IconKey.of("restaurant").value(),
                            false,
                            1,
                            at,
                            at
                        )
                    ),
                    defaultLimits = listOf(
                        DefaultLimit(
                            comida,
                            YearMonth.from(day),
                            AutomaticBudget.Limit(BudgetLimit.finite(Money.cop(100_000)).value())
                        )
                    ),
                    monthLimits = emptyList(),
                    expenses = listOf(
                        expense(101, 2, 10_000),
                        expense(102, 1, 15_000),
                        expense(103, 0, 5_000)
                    ),
                    incomes = listOf(
                        Income(
                            IncomeId.of(
                                id(201)
                            ).value(),
                            day, 1, amount(200_000), IncomeSource.Salary, null, 1, at, at
                        )
                    ),
                    goals = emptyList(),
                    featuredGoal = null,
                    schedules = emptyList()
                ),
                preferences = PortablePreferences(
                    AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
                    CategoryOrderingPreference(
                        CategoryOrder.Alphabetical,
                        CategoryOrderDirection.Ascending
                    )
                )
            )
        ).value()
    }

    /** Puts [bytes] in a provider document and answers the open dialog with it. */
    private fun answerWith(bytes: ByteArray): Instrumentation.ActivityMonitor {
        val uri = TestDocuments.uri("ok", "respaldo-${UUID.randomUUID()}.json")
        context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_OPEN_DOCUMENT) {
                    Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri))
                } else {
                    null
                }
        }
        instrumentation.addMonitor(monitor)
        picker = monitor
        return monitor
    }

    private fun openRespaldoAndChoose() {
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        rule.awaitTag(RECIBO_SUMMARY_TAG)
        rule.onNodeWithTag(SETTINGS_ACTION_TAG).performClick()
        rule.awaitTag(SETTINGS_TAG)
        rule.onNodeWithTag(SETTINGS_BACKUP_TAG).performScrollTo().performClick()
        rule.awaitTag(BACKUP_TAG)
        rule.onNodeWithTag(RESTORE_CHOOSE_TAG).performScrollTo().performClick()
    }

    private fun financialRows() = listOf(
        TestData.rows("SELECT name FROM category ORDER BY name"),
        TestData.rows("SELECT amount_minor FROM expense ORDER BY sequence"),
        TestData.rows("SELECT amount_minor FROM income"),
        TestData.rows("SELECT generation FROM profile")
    )

    @Test
    fun aConfirmedRestoreReplacesTheHistoryAndAppliesThePreferences() {
        answerWith(backup())
        openRespaldoAndChoose()
        rule.awaitTag(RESTORE_PREVIEW_TAG)

        // Nothing has changed while the preview is on screen.
        assertEquals(
            listOf(listOf("Mercado"), listOf("7000", "8000"), emptyList(), listOf("1")),
            financialRows()
        )
        rule.onNodeWithText("¿Reemplazar tus datos?").assertExists()
        rule.onNodeWithTag(RESTORE_CONFIRM_TAG).performScrollTo().performClick()
        rule.awaitTag(RESTORE_DONE_TAG)

        assertEquals(
            listOf(
                listOf("Comida"),
                listOf("10000", "15000", "5000"),
                listOf("200000"),
                listOf("2")
            ),
            financialRows()
        )
        runBlocking {
            assertEquals(
                PreferenceRead.Ready(
                    AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
                ),
                koin.get<AppearancePreferences>().observeAppearance().first()
            )
        }
        rule.onNodeWithTag(RESTORE_LEAVE_TAG).performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(1, restarts)
    }

    @Test
    fun aFileThatIsNotABackupChangesNothing() {
        answerWith("{\"not\": \"a backup\"}".toByteArray())
        val before = financialRows()
        openRespaldoAndChoose()
        rule.awaitTag(RESTORE_REJECTED_TAG)

        rule.onNodeWithText("No es un respaldo de Sello.", substring = true).assertExists()
        assertEquals(before, financialRows())
        assertEquals(0, restarts)
    }

    @Test
    fun cancellingThePreviewChangesNothing() {
        answerWith(backup())
        val before = financialRows()
        openRespaldoAndChoose()
        rule.awaitTag(RESTORE_PREVIEW_TAG)

        rule.onNodeWithTag(RESTORE_CANCEL_TAG).performScrollTo().performClick()
        rule.awaitTag(RESTORE_CHOOSE_TAG)

        assertEquals(before, financialRows())
        assertTrue(File(context.filesDir, "sello-restore").list().isNullOrEmpty())
        assertEquals(0, restarts)
    }

    @Test
    fun theTopBarsBackArrowAfterARestoreStartsTheAppAgain() {
        answerWith(backup())
        openRespaldoAndChoose()
        rule.awaitTag(RESTORE_PREVIEW_TAG)
        rule.onNodeWithTag(RESTORE_CONFIRM_TAG).performScrollTo().performClick()
        rule.awaitTag(RESTORE_DONE_TAG)

        // The arrow, not the system Back: the screens behind it still show the old history.
        rule.onNodeWithContentDescription("Volver").performClick()
        rule.waitForIdle()

        assertEquals(1, restarts)
    }
}
