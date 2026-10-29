package com.software.sello.devtools

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the debug build remembers about its sandbox from one process to the next.
 *
 * - [active]: this process opened the sandbox's data rather than the normal profile's.
 * - [date]: the sandbox's financial date; none until the sandbox is first entered.
 * - [changed]: a financial change was saved in this sandbox, so its date may no
 *   longer move back without a reset.
 * - [wipe]: the sandbox's data is erased at the next start, before anything opens it.
 * - [zone]: the financial zone a new sandbox is created in; the device's when absent.
 * - [scenario]: the loaded history as `id@version`, and [nextStep] the first of its
 *   steps not yet confirmed. A step at [nextStep] may already have committed; running
 *   it again uses the same operation and returns that result.
 * - [loading]: the history was asked to load and is not all confirmed yet; the next
 *   process carries on from [nextStep].
 */
data class SandboxSession(
    val active: Boolean = false,
    val date: LocalDate? = null,
    val changed: Boolean = false,
    val wipe: Boolean = false,
    val zone: ZoneId? = null,
    val scenario: String? = null,
    val nextStep: Int = 0,
    val loading: Boolean = false
)

/** Where [SandboxSession] is kept. Writes finish before they return. */
interface SandboxSessionStore {
    fun read(): SandboxSession

    fun write(session: SandboxSession)
}

/** The session in the debug app's private preferences, separate from both profiles' data. */
class SharedPreferencesSessionStore(context: Context) : SandboxSessionStore {
    private val preferences =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): SandboxSession = SandboxSession(
        active = preferences.getBoolean(ACTIVE, false),
        date = preferences.getString(DATE, null)?.let(LocalDate::parse),
        changed = preferences.getBoolean(CHANGED, false),
        wipe = preferences.getBoolean(WIPE, false),
        zone = preferences.getString(ZONE, null)?.let(ZoneId::of),
        scenario = preferences.getString(SCENARIO, null),
        nextStep = preferences.getInt(NEXT_STEP, 0),
        loading = preferences.getBoolean(LOADING, false)
    )

    override fun write(session: SandboxSession) {
        // Written before returning, not later: the process may end right after this.
        preferences.edit(commit = true) {
            putBoolean(ACTIVE, session.active)
            putString(DATE, session.date?.toString())
            putBoolean(CHANGED, session.changed)
            putBoolean(WIPE, session.wipe)
            putString(ZONE, session.zone?.id)
            putString(SCENARIO, session.scenario)
            putInt(NEXT_STEP, session.nextStep)
            putBoolean(LOADING, session.loading)
        }
    }

    private companion object {
        const val FILE_NAME = "sandbox-session"
        const val ACTIVE = "active"
        const val DATE = "date"
        const val CHANGED = "changed"
        const val WIPE = "wipe"
        const val ZONE = "zone"
        const val SCENARIO = "scenario"
        const val NEXT_STEP = "next_step"
        const val LOADING = "loading"
    }
}
