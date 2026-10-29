package com.software.sello.composition

import android.app.Application
import android.content.Context
import com.software.sello.data.FinancialStorage
import com.software.sello.data.StorageNames
import com.software.sello.devtools.ControlledFinancialClock
import com.software.sello.devtools.FencedCategoryCommands
import com.software.sello.devtools.FencedExpenseCommands
import com.software.sello.devtools.FencedGoalCommands
import com.software.sello.devtools.FencedIncomeCommands
import com.software.sello.devtools.FencedInvestmentCommands
import com.software.sello.devtools.FencedScheduleCatchUp
import com.software.sello.devtools.FencedScheduleCommands
import com.software.sello.devtools.SandboxBadges
import com.software.sello.devtools.SandboxProfiles
import com.software.sello.devtools.SandboxRegistry
import com.software.sello.devtools.SandboxSession
import com.software.sello.devtools.SandboxSessionStore
import com.software.sello.devtools.SandboxTime
import com.software.sello.devtools.SandboxTools
import com.software.sello.devtools.Scenario
import com.software.sello.devtools.ScenarioRunner
import com.software.sello.devtools.ScenarioSchedules
import com.software.sello.devtools.Scenarios
import com.software.sello.devtools.SharedPreferencesSessionStore
import com.software.sello.devtools.TestFinancialTime
import com.software.sello.devtools.WriteFence
import com.software.sello.devtools.restartApp
import com.software.sello.domain.model.FinancialProfile
import com.software.sello.domain.port.CategoryCommands
import com.software.sello.domain.port.ExpenseCommands
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.GoalCommands
import com.software.sello.domain.port.IncomeCommands
import com.software.sello.domain.port.InvestmentCommands
import com.software.sello.domain.port.ScheduleCatchUpStore
import com.software.sello.domain.port.ScheduleCommands
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/** The sandbox's own files, beside the normal debug profile's and never shared with them. */
val SandboxNames = StorageNames(database = "sandbox.db", preferences = "sandbox-preferences")

/**
 * Erases a whole set of data other than the app's own: the database with its journal
 * files, its set-aside copy and its preferences. Only the sandbox's reset calls it, at
 * the start of a process, before anything has opened those files. It exists only in
 * the debug build, and the app's own files are refused outright.
 */
fun eraseFinancialStorage(context: Context, names: StorageNames) {
    require(names.database != StorageNames.Normal.database) { "the app's own data is never erased" }
    require(names.preferences != StorageNames.Normal.preferences) {
        "the app's own data is never erased"
    }
    context.deleteDatabase(names.database)
    context.deleteDatabase(names.setAsideDatabase)
    // Where DataStore keeps a preferences file named [StorageNames.preferences].
    File(context.filesDir, "datastore/${names.preferences}.preferences_pb").delete()
    // A restore staged or half finished in the sandbox goes with its data.
    File(context.filesDir, names.restoreFolder).deleteRecursively()
    File(context.filesDir, names.resetFolder).deleteRecursively()
}

/**
 * The debug build's choice of data. A pending reset erases the sandbox's files first,
 * while nothing has opened them; then the session says which profile this process uses.
 */
fun variantComposition(application: Application): VariantComposition {
    val store = SharedPreferencesSessionStore(application)
    val pending = store.read()
    if (pending.wipe) {
        eraseFinancialStorage(application, SandboxNames)
        // A loaded scenario keeps its start, zone and name; a plain reset has none.
        store.write(pending.copy(wipe = false, changed = false, nextStep = 0))
    }
    val restart = { restartApp(application) }
    val composition = DebugComposition(store, restart) { SandboxRegistry.tools = it }
    if (composition.inSandbox) SandboxBadges.install(application)
    return composition
}

/**
 * In both profiles every financial command runs through one [WriteFence], so a move
 * between them waits for writes in progress. A history is found by its key in
 * [scenarios], the tools' list unless a test gives its own. In the sandbox the financial clock is a
 * [ControlledFinancialClock] on the sandbox's stored date, or today's in its financial
 * zone the first time; audit and elapsed time stay the real ones. Under device tests
 * the app's own profile is held on [TestFinancialTime.fixedDate] the same way.
 */
class DebugComposition(
    private val store: SandboxSessionStore,
    private val restart: () -> Unit,
    private val scenarios: (String?) -> Scenario? = Scenarios::find,
    private val publish: (SandboxTools) -> Unit
) : VariantComposition {
    private val session = store.read()

    val inSandbox: Boolean get() = session.active

    override val names: StorageNames = if (session.active) SandboxNames else StorageNames.Normal

    override val newDataZone: ZoneId? = session.zone.takeIf { session.active }

    init {
        // Until the data opens there is nothing to fence and no date to show.
        publish(SandboxTools(SandboxProfiles(store, null, restart), null))
    }

    override fun modules(profile: FinancialProfile): List<Module> {
        val fence = WriteFence()
        val scope = CoroutineScope(SupervisorJob() + SystemDispatcherProvider().main)
        val time = if (session.active) sandboxTime(profile, scope) else null
        // Device tests hold the app's own profile on one day; see TestFinancialTime.
        val held = if (time == null) heldTime(profile) else null
        val saved: () -> Unit = { time?.markChanged() }
        val profiles = SandboxProfiles(store, fence, restart)
        publish(SandboxTools(profiles, time))
        return listOf(
            module {
                single<ExpenseCommands> {
                    FencedExpenseCommands(
                        get<FinancialStorage>().expenseCommands(get(), get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<IncomeCommands> {
                    FencedIncomeCommands(
                        get<FinancialStorage>().incomeCommands(get(), get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<CategoryCommands> {
                    FencedCategoryCommands(
                        get<FinancialStorage>().categoryCommands(get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<GoalCommands> {
                    FencedGoalCommands(
                        get<FinancialStorage>().goalCommands(get(), get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<InvestmentCommands> {
                    FencedInvestmentCommands(
                        get<FinancialStorage>().investmentCommands(get(), get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<ScheduleCommands> {
                    FencedScheduleCommands(
                        get<FinancialStorage>().scheduleCommands(get(), get(), get()),
                        fence,
                        saved
                    )
                }
                single<ScheduleCatchUpStore> {
                    FencedScheduleCatchUp(
                        get<FinancialStorage>().scheduleCatchUp(get(), get()),
                        fence,
                        saved
                    )
                }
                if (held != null) single<FinancialClock> { held }
                if (time != null) {
                    single<FinancialClock> { time.clock }
                    // Built with the graph, on the same fenced commands the screens use.
                    single(createdAtStart = true) {
                        ScenarioRunner(
                            scenarios(session.scenario),
                            profile.generation,
                            time,
                            get(),
                            get(),
                            get(),
                            get(),
                            // The catch-up Sello itself runs, on these fenced ports.
                            ScenarioSchedules(get(), get()),
                            get(),
                            store,
                            scope
                        ).also {
                            publish(SandboxTools(profiles, time, it))
                            it.resume()
                        }
                    }
                }
            }
        )
    }

    private fun heldTime(profile: FinancialProfile): ControlledFinancialClock? =
        TestFinancialTime.fixedDate?.let { date ->
            ControlledFinancialClock(FinancialDay(date, profile.zone))
        }

    private fun sandboxTime(profile: FinancialProfile, scope: CoroutineScope): SandboxTime {
        val today = SystemAuditClock().now().atZone(profile.zone).toLocalDate()
        val clock = ControlledFinancialClock(FinancialDay(session.date ?: today, profile.zone))
        return SandboxTime(clock, store, scope)
    }
}
