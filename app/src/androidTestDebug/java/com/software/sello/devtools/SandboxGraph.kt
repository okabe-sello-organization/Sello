package com.software.sello.devtools

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.composition.DebugComposition
import com.software.sello.composition.SandboxNames
import com.software.sello.composition.eraseFinancialStorage
import com.software.sello.composition.openStorage
import com.software.sello.composition.productionModules
import com.software.sello.domain.model.Outcome
import com.software.sello.platform.ProcessFinancialZone
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication

/**
 * The debug app's graph on the sandbox's own data, built as a new process builds it:
 * the stored [session], real Room, the production modules and the debug composition.
 * Each [open] is a new process; [close] ends it. The installed app's graph is not used.
 */
class SandboxGraph(var session: SandboxSession) {
    val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    val store = MemoryStore(session)
    var tools: SandboxTools? = null
        private set
    var graph: KoinApplication? = null
        private set

    val koin get() = checkNotNull(graph).koin

    fun open(): SandboxGraph {
        close()
        val composition = DebugComposition(store, {}) { tools = it }
        val opened = openStorage(
            context,
            composition.newDataZone ?: ZoneId.of("America/Bogota"),
            SystemDispatcherProvider(),
            SystemAuditClock(),
            SandboxNames
        ) as Outcome.Success
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

    fun close() {
        graph?.endProcess()
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

/**
 * Ends [this] graph as the end of its process would: the application scope's work, such
 * as a catch-up under way, is stopped and waited for before the graph and its storage
 * close, so nothing of the old process still holds the database when the next opens it.
 */
fun KoinApplication.endProcess() {
    runBlocking { koin.get<CoroutineScope>().coroutineContext.job.cancelAndJoin() }
    close()
}
