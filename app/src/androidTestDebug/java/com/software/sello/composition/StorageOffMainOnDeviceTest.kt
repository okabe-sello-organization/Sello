package com.software.sello.composition

import android.content.Context
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.software.sello.domain.model.Outcome
import com.software.sello.platform.SystemAuditClock
import com.software.sello.platform.SystemDispatcherProvider
import java.time.ZoneId
import java.util.Collections
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app's start opens and sets aside its data from the main thread. Both must do
 * their file work elsewhere: the main thread here reports every disk read or write it
 * makes, and none may come from opening or setting aside the data (issue #214).
 */
@RunWith(AndroidJUnit4::class)
class StorageOffMainOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dispatchers = SystemDispatcherProvider()

    @Before
    fun start() = eraseFinancialStorage(context, SandboxNames)

    @After
    fun end() = eraseFinancialStorage(context, SandboxNames)

    /** Runs [work] from the main thread and returns the disk access it made there itself. */
    private fun <T> onMain(work: suspend () -> T): Pair<T, List<Violation>> {
        val seen = Collections.synchronizedList(mutableListOf<Violation>())
        lateinit var before: StrictMode.ThreadPolicy
        instrumentation.runOnMainSync {
            before = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(Executor { it.run() }) { seen += it }
                    .build()
            )
        }
        val result = try {
            runBlocking { withContext(Dispatchers.Main) { work() } }.also {
                // A main-thread violation is reported once the thread is next free, and
                // only while the policy that saw it is still set.
                instrumentation.waitForIdleSync()
            }
        } finally {
            instrumentation.runOnMainSync { StrictMode.setThreadPolicy(before) }
        }
        // Only what the storage calls did: the app's main thread may be busy with others.
        return result to seen.filter { violation ->
            violation.stackTrace.any { it.className.startsWith(STORAGE_MODULE) }
        }
    }

    @Test
    fun openingTheDataReadsAndWritesNothingOnTheMainThread() {
        val (opened, onMain) = onMain {
            openStorage(
                context,
                ZoneId.of("America/Bogota"),
                dispatchers,
                SystemAuditClock(),
                SandboxNames
            )
        }

        assertTrue("$opened", opened is Outcome.Success)
        (opened as Outcome.Success).value.storage.close()
        assertEquals(emptyList<Violation>(), onMain)
    }

    @Test
    fun settingTheDataAsideReadsAndWritesNothingOnTheMainThread() {
        val opened = runBlocking {
            openStorage(
                context,
                ZoneId.of("America/Bogota"),
                dispatchers,
                SystemAuditClock(),
                SandboxNames
            )
        } as Outcome.Success
        opened.value.storage.close()

        val (moved, onMain) = onMain { setAsideStorage(context, dispatchers, SandboxNames) }

        assertEquals(Outcome.Success(Unit), moved)
        assertEquals(emptyList<Violation>(), onMain)
    }

    private companion object {
        const val STORAGE_MODULE = "com.software.sello.composition.StorageModuleKt"
    }
}
