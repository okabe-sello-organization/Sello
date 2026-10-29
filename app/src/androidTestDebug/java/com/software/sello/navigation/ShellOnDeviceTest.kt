package com.software.sello.navigation

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.MainActivity
import com.software.sello.R
import com.software.sello.TestData
import com.software.sello.awaitTag
import com.software.sello.designsystem.component.SCAFFOLD_BAR_TAG
import com.software.sello.designsystem.component.SCAFFOLD_DOCK_TAG
import com.software.sello.domain.port.FinancialClock
import com.software.sello.feature.recibo.RECIBO_FIRST_RUN_TAG
import java.time.YearMonth
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/** The installed app, started the way a person or a link starts it. */
@RunWith(AndroidJUnit4::class)
class ShellOnDeviceTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val koin get() = GlobalContext.get()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val current get() = YearMonth.from(koin.get<FinancialClock>().today.value.date)
    private var scenario: ActivityScenario<MainActivity>? = null

    /** The month bar names [month]; its words are one button, so it is read as a label. */
    private fun monthShown(month: YearMonth) {
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).assertIsDisplayed()
            .assert(hasContentDescription(title(month), substring = true))
    }

    private fun title(month: YearMonth) = context.getString(
        R.string.month_title,
        context.resources.getStringArray(R.array.month_names)[month.monthValue - 1],
        month.year
    )

    private fun launch(link: String? = null): ActivityScenario<MainActivity> {
        val intent = Intent(context, MainActivity::class.java)
        if (link != null) {
            intent.action = Intent.ACTION_VIEW
            intent.data = Uri.parse(link)
        }
        return ActivityScenario.launch<MainActivity>(intent).also { scenario = it }
    }

    /** Starts the app and waits until Recibo has read the month. */
    private fun launchToRecibo(link: String? = null): ActivityScenario<MainActivity> =
        launch(link).also { rule.awaitTag(RECIBO_FIRST_RUN_TAG) }

    private fun shell(): ShellViewModel {
        lateinit var viewModel: ShellViewModel
        scenario!!.onActivity { viewModel = ViewModelProvider(it)[ShellViewModel::class.java] }
        return viewModel
    }

    private fun financialRows(): Long = SQLiteDatabase.openDatabase(
        context.getDatabasePath("sello.db").path,
        null,
        SQLiteDatabase.OPEN_READONLY
    ).use { database ->
        listOf("category", "default_limit", "month_limit", "expense", "income").sumOf {
            database.rawQuery("SELECT COUNT(*) FROM $it", null).use { rows ->
                rows.moveToFirst()
                rows.getLong(0)
            }
        }
    }

    @Before
    fun newInstallation() = TestData.reset()

    @After
    fun leaveTheSessionAsANewInstallationHasIt() {
        scenario?.close()
        // The month session lives as long as the process, which the tests share.
        koin.get<MonthSession>().select(current)
    }

    @Test
    fun aNewInstallationOpensOnAnHonestEmptyReciboForTheCurrentMonth() {
        launchToRecibo()

        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertIsDisplayed()
        monthShown(current)
        rule.onNodeWithText(context.getString(R.string.recibo_first_run_title)).assertIsDisplayed()
        rule.onNodeWithTag(SCAFFOLD_BAR_TAG).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.tab_recibo)).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.tab_resumen)).assertIsDisplayed()
        rule.onNodeWithTag(SCAFFOLD_DOCK_TAG).assertIsDisplayed()
        assertEquals(0, financialRows())
    }

    @Test
    fun resumenKeepsTheSharedMonthThroughRecreationAndBackReturnsToRecibo() {
        val app = launchToRecibo()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.month_picker_previous_year))
            .performClick()
        val march = YearMonth.of(current.year - 1, 3)
        rule.onNodeWithContentDescription(title(march)).performClick()
        rule.onNodeWithText(context.getString(R.string.tab_resumen)).performClick()
        rule.awaitTag("resumen.income")
        monthShown(march)

        app.recreate()

        rule.awaitTag("resumen.income")
        monthShown(march)
        Espresso.pressBack()
        rule.awaitTag(RECIBO_FIRST_RUN_TAG)
        monthShown(march)
        assertEquals(0, financialRows())
    }

    @Test
    fun backClosesTheMonthPickerBeforeItLeavesTheApp() {
        val app = launchToRecibo()
        rule.onNodeWithTag(MONTH_SWITCHER_TAG).performClick()
        rule.onNodeWithTag(MONTH_PICKER_TAG).assertIsDisplayed()

        Espresso.pressBack()

        rule.onNodeWithTag(MONTH_PICKER_TAG).assertDoesNotExist()
        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, app.state)
    }

    @Test
    fun leavingAndReturningAreReportedToTheMonthSession() {
        val app = launchToRecibo()
        val session = koin.get<MonthSession>()
        assertNull(session.save().backgroundedAtMillis)

        // Stopped, as when another app is in front; the timing rule itself is tested
        // with a controlled clock.
        app.moveToState(Lifecycle.State.CREATED)
        val whileAway = session.save().backgroundedAtMillis
        app.moveToState(Lifecycle.State.RESUMED)

        assertNotNull(whileAway)
        assertNull(session.save().backgroundedAtMillis)
        rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertIsDisplayed()
    }

    @Test
    fun aHostileOrForeignLinkOpensTheAppAndIsOtherwiseIgnored() {
        val links = listOf(
            "sello://anotar/../borrar?monto=5000",
            "sello://guardar?monto=5000&categoria=3f2c1a9e-7b4d-4c61-9a0e-5d8f2b6c7e10",
            "https://example.com/anotar?monto=5000",
            "content://com.software.sello.debug/databases/sello.db"
        )

        for (link in links) {
            launchToRecibo(link)

            rule.onNodeWithTag(RECIBO_FIRST_RUN_TAG).assertIsDisplayed()
            assertNull(link, shell().state.value.pendingEntry)
            assertEquals(link, 0, financialRows())
            scenario?.close()
        }
    }
}
