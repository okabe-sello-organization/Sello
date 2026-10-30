package com.software.sello.devtools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.software.sello.composition.DebugComposition
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.composition.openStorage
import com.software.sello.composition.productionModules
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.MonotonicClock
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** Elapsed time that moves only when a test moves it: undo windows run on this, never on a wall. */
class ControlledMonotonicClock(var now: Duration = 1.seconds) : MonotonicClock {
    override fun elapsed(): Duration = now
}

/**
 * The debug app's graph on the sandbox's own data, on the JVM under Robolectric, built
 * as a new process builds it: the stored session, real Room, the production modules and
 * the debug composition. Each [open] is a new process; [close] ends it.
 */
class JvmSandbox(val scenario: Scenario) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val store = InMemorySessionStore(
        SandboxSession(
            active = true,
            date = scenario.start,
            zone = scenario.zone,
            scenario = scenario.key
        )
    )

    /** The graph's monotonic clock, shared by every process this sandbox opens. */
    val monotonic = ControlledMonotonicClock()
    private var graph: KoinApplication? = null
    var runner: ScenarioRunner? = null
        private set

    val koin get() = checkNotNull(graph).koin

    fun open(): JvmSandbox {
        close()
        val composition = DebugComposition(store, {}, { Scenarios.find(it) }) {
            runner = it.scenarios ?: runner
        }
        runner = null
        val opened = runBlocking {
            openStorage(
                context,
                scenario.zone,
                SystemDispatcherProvider(),
                SystemAuditClock(),
                SandboxNames
            )
        } as Outcome.Success
        val zone = ProcessFinancialZone().apply { initialize(opened.value.profile.zone) }
        graph = koinApplication {
            allowOverride(true)
            modules(
                productionModules(context, zone, opened.value.storage, SandboxNames) +
                    composition.modules(opened.value.profile) +
                    module { single<MonotonicClock> { monotonic } }
            )
        }
        return this
    }

    /**
     * Takes every step left, here on the test's thread: the runner's own [ScenarioRunner.load]
     * runs on the main thread, which a Robolectric test holds.
     */
    fun finish() = runBlocking {
        val loading = checkNotNull(runner)
        while (loading.step()) Unit
        assertTrue("${scenario.key}: ${loading.state.value.problem}", loading.state.value.finished)
    }

    fun close() {
        graph?.let { app ->
            runBlocking { app.koin.get<CoroutineScope>().coroutineContext.job.cancelAndJoin() }
            app.close()
        }
        graph = null
    }

    fun erase() {
        close()
        eraseFinancialStorage(context, SandboxNames)
    }

    /** Each row of [sql] on the sandbox's database file, columns joined by "|". */
    fun rows(sql: String): List<String> = SQLiteDatabase.openDatabase(
        context.getDatabasePath(SandboxNames.database).path,
        null,
        SQLiteDatabase.OPEN_READONLY
    ).use { db ->
        db.rawQuery(sql, null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        (0 until cursor.columnCount).joinToString("|") {
                            cursor.getString(it)
                                ?: "null"
                        }
                    )
                }
            }
        }
    }

    /** Every category's carry chains by name, as the database holds them (ADR 0020 CP4). */
    fun chains(): List<String> = rows(
        "SELECT c.name, h.start_month, h.end_month FROM carry_chain h " +
            "JOIN category c ON c.id = h.category_id ORDER BY c.name, h.start_month"
    )

    /**
     * A runner for this history on this process's ports, working on [scope] instead of the
     * main thread, which a Robolectric test holds: its [ScenarioRunner.load] can be joined.
     */
    fun runnerOn(generation: Long, scope: CoroutineScope) = ScenarioRunner(
        scenario,
        generation,
        SandboxTime(koin.get<FinancialClock>() as ControlledFinancialClock, store, scope),
        koin.get(),
        koin.get(),
        koin.get(),
        koin.get(),
        ScenarioSchedules(koin.get(), koin.get()),
        koin.get(),
        store,
        scope
    )

    /** Takes steps here, on the test's thread, until step [index] is the next one. */
    fun stepTo(index: Int) = runBlocking {
        val loading = checkNotNull(runner)
        while (loading.state.value.next < index) {
            assertTrue("${scenario.key}: ${loading.state.value.problem}", loading.step())
        }
    }

    /**
     * What a backup keeps, by name: no identifiers, versions, receipts or recording
     * positions.
     */
    fun restorable(): List<String> = rows(
        "SELECT c.name, e.amount_minor, e.effective_date, e.note, e.origin_date, " +
            "e.origin_slot FROM expense e JOIN category c ON c.id = e.category_id " +
            "ORDER BY e.effective_date, e.sequence"
    ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) +
        rows(
            "SELECT note, kind, amount_minor, rule, start_date, handled_date, handled_slot " +
                "FROM schedule ORDER BY sequence"
        ) +
        rows(
            "SELECT s.note, p.from_date, p.until_date FROM schedule_pause p " +
                "JOIN schedule s ON s.id = p.schedule_id ORDER BY s.note, p.from_date"
        ) +
        rows("SELECT name, archived FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows("SELECT name, target_month, completed_on FROM goal ORDER BY name") +
        rows(
            "SELECT g.name, e.sequence, e.kind, e.amount_minor, e.effective_date " +
                "FROM goal_event e JOIN goal g ON g.id = e.goal_id ORDER BY g.name, e.sequence"
        ) +
        rows("SELECT g.name FROM featured_goal f JOIN goal g ON g.id = f.goal_id") +
        rows("SELECT name, icon_key, currency FROM investment ORDER BY name") +
        rows(
            "SELECT i.name, m.sequence, m.kind, m.amount_minor, m.currency, m.effective_date " +
                "FROM investment_movement m JOIN investment i ON i.id = m.investment_id " +
                "ORDER BY i.name, m.sequence"
        ) +
        rows("SELECT rate_hundredths, effective_date, sequence FROM usd_rate ORDER BY sequence") +
        chains()

    /**
     * Everything a history wrote, without the random record IDs or audit times. A
     * catch-up batch has no operation of its own: its receipt is compared by kind and
     * revision.
     */
    fun history(): List<String> = rows(
        "SELECT CASE kind WHEN 'schedule.catch_up' THEN 'catch-up' ELSE operation_id END, " +
            "kind, revision FROM operation_receipt ORDER BY revision"
    ) +
        rows(
            "SELECT c.name, e.amount_minor, e.effective_date, e.note, e.origin_date, " +
                "e.origin_slot FROM expense e JOIN category c ON c.id = e.category_id " +
                "ORDER BY e.effective_date, e.sequence"
        ) +
        rows(
            "SELECT amount_minor, effective_date, source_key, origin_date, origin_slot " +
                "FROM income ORDER BY effective_date, sequence"
        ) +
        rows(
            "SELECT note, kind, amount_minor, rule, start_date, handled_date, handled_slot, " +
                "version FROM schedule ORDER BY sequence"
        ) +
        rows("SELECT name, archived, version FROM category ORDER BY name") +
        rows(
            "SELECT c.name, d.effective_month, d.kind, d.limit_minor FROM default_limit d " +
                "JOIN category c ON c.id = d.category_id ORDER BY c.name, d.effective_month"
        ) +
        rows("SELECT name, target_month, completed_on, version FROM goal ORDER BY name") +
        rows(
            "SELECT g.name, e.sequence, e.kind, e.amount_minor, e.effective_date " +
                "FROM goal_event e JOIN goal g ON g.id = e.goal_id ORDER BY g.name, e.sequence"
        ) +
        rows("SELECT g.name FROM featured_goal f JOIN goal g ON g.id = f.goal_id") +
        rows("SELECT name, currency, next_sequence, version FROM investment ORDER BY name") +
        rows(
            "SELECT i.name, m.sequence, m.kind, m.amount_minor, m.effective_date " +
                "FROM investment_movement m JOIN investment i ON i.id = m.investment_id " +
                "ORDER BY i.name, m.sequence"
        ) +
        rows("SELECT rate_hundredths, effective_date, sequence FROM usd_rate ORDER BY sequence") +
        chains()
}
