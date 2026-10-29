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
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

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
                    composition.modules(opened.value.profile)
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
}
