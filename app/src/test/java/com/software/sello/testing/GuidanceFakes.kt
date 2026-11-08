package com.software.sello.testing

import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.GuidancePreferences
import com.software.sello.domain.port.HistoryStart
import com.software.sello.domain.port.HistoryStartReads
import com.software.sello.domain.port.HistoryStartState
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ReadState
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The guidance kept in memory, written as the DataStore port writes it. [failWrites] makes
 * every write fail and change nothing; [failReads] makes the read fail with the last value.
 */
class HandGuidance(initial: Guidance = Guidance(existingUseChecked = true)) : GuidancePreferences {
    var stored: Guidance = initial
        private set
    val reads = MutableStateFlow<PreferenceRead<Guidance>>(PreferenceRead.Ready(initial))
    var failWrites = false

    /** Every write asked for, in order, as "seen X", "shown X day", "off" or "existing". */
    val writes = mutableListOf<String>()

    fun failReads() {
        reads.value = PreferenceRead.Failed(PreferenceFailure.Unavailable, stored)
    }

    fun readAgain() {
        reads.value = PreferenceRead.Ready(stored)
    }

    /** Set by hand, as another screen or a restore would have written it. */
    fun set(memory: GuidanceMemory) = keep(stored.copy(memory = memory))

    override fun observeGuidance(): Flow<PreferenceRead<Guidance>> = reads

    override suspend fun markSeen(nudge: Nudge): PreferenceWrite = write("seen $nudge") {
        it.copy(seen = it.seen + nudge)
    }

    override suspend fun markShown(nudge: Nudge, day: LocalDate): PreferenceWrite =
        write("shown $nudge $day") {
            it.copy(
                lastShown = ShownNudge(nudge, day),
                backupReminderShown =
                if (nudge == Nudge.BackupReminder) day else it.backupReminderShown
            )
        }

    override suspend fun turnOffBackupReminder(): PreferenceWrite =
        write("off") { it.copy(backupReminderOff = true) }

    override suspend fun rememberExistingUse(wasUsingSello: Boolean): PreferenceWrite {
        writes += "existing"
        if (failWrites) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        val seen = if (wasUsingSello) Nudge.entries.filter { it.forNewUsers }.toSet() else emptySet()
        keep(Guidance(stored.memory.copy(seen = stored.memory.seen + seen), existingUseChecked = true))
        return PreferenceWrite.Saved
    }

    override suspend fun mergeRestored(memory: GuidanceMemory): PreferenceWrite {
        if (failWrites) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        set(stored.memory.mergedWith(memory))
        return PreferenceWrite.Saved
    }

    private fun write(label: String, change: (GuidanceMemory) -> GuidanceMemory): PreferenceWrite {
        writes += label
        if (failWrites) return PreferenceWrite.Failed(PreferenceFailure.Unavailable)
        keep(stored.copy(memory = change(stored.memory)))
        return PreferenceWrite.Saved
    }

    private fun keep(guidance: Guidance) {
        stored = guidance
        if (reads.value is PreferenceRead.Ready) reads.value = PreferenceRead.Ready(guidance)
    }
}

/** Where the history begins, as a test sets it; it starts with no history. */
class HandHistoryStart(
    initial: HistoryStartState = ReadState.Ready(HistoryStart(null, null))
) : HistoryStartReads {
    val state = MutableStateFlow(initial)

    fun starts(firstExpense: String?, firstMovement: String?) {
        state.value = ReadState.Ready(
            HistoryStart(firstExpense?.let(LocalDate::parse), firstMovement?.let(LocalDate::parse))
        )
    }

    fun fail() {
        val lastGood = (state.value as? ReadState.Ready)?.value
        state.value = ReadState.Failed(StorageFailure.Unavailable("SQLiteException"), lastGood)
    }

    override fun observe(): Flow<HistoryStartState> = state
}
