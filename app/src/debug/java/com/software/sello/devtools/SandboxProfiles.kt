package com.software.sello.devtools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import com.software.sello.MainActivity

/**
 * Moves the debug app between its normal profile and the sandbox. Each move waits for
 * financial writes to finish ([WriteFence.close]), records the choice, then replaces
 * the process: no screen, draft, observer or pending action of the old profile can
 * reach the other one's data, because none of them survives.
 *
 * [fence] is absent when the data did not open and nothing can write. [restart] ends
 * this process and opens the app again.
 */
class SandboxProfiles(
    private val store: SandboxSessionStore,
    private val fence: WriteFence?,
    private val restart: () -> Unit
) {
    val inSandbox: Boolean get() = store.read().active

    /** Opens the sandbox where it was left, or new on today's date the first time. */
    suspend fun enter() = move { it.copy(active = true, wipe = false) }

    /** Back to the normal debug profile. The sandbox keeps its data for next time. */
    suspend fun leave() = move { it.copy(active = false) }

    /**
     * Erases the sandbox's data and opens it again, new, on today's date. This is the
     * only way its date goes back once a change was saved. The normal profile is never
     * touched.
     */
    suspend fun reset() = move { SandboxSession(active = true, wipe = true) }

    /**
     * Erases the sandbox like [reset] and opens it again at [scenario]'s start, in its
     * zone, where the new process loads the whole history up to its last day. Loading
     * the same history again loads it from the beginning onto a new sandbox.
     */
    suspend fun load(scenario: Scenario) = move {
        SandboxSession(
            active = true,
            wipe = true,
            date = scenario.start,
            zone = scenario.zone,
            scenario = scenario.key,
            loading = true
        )
    }

    private suspend fun move(change: (SandboxSession) -> SandboxSession) {
        fence?.close()
        store.write(change(store.read()))
        restart()
    }
}

/** Ends this process and opens the app again in a new task, as a cold start does. */
fun restartApp(context: Context) {
    val main = Intent.makeRestartActivityTask(ComponentName(context, MainActivity::class.java))
    context.startActivity(main)
    Process.killProcess(Process.myPid())
}
