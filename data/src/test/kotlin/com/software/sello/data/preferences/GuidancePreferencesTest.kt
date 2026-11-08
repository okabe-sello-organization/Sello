package com.software.sello.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.CorrectionAnnouncementPreference
import com.software.sello.domain.model.Guidance
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.BackupRecord
import com.software.sello.domain.port.BackupSummary
import com.software.sello.domain.port.LastBackup
import com.software.sello.domain.port.PreferenceFailure
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.ResetPreview
import com.software.sello.domain.port.Resets
import com.software.sello.domain.usecase.RememberExistingUse
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The guidance seen (SELLO-205) on a real preferences file: each test's "process" is a
 * DataStore on that file, and a new process opens the same file after the last one ended.
 */
class GuidancePreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var job: Job? = null
    private var store: DataStore<Preferences>? = null

    /** Ends the process before, as a death would, and opens the file again. */
    private suspend fun process(): DataStoreUserPreferences {
        job?.cancelAndJoin()
        val next = Job()
        job = next
        val opened = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + next),
            produceFile = { folder.root.resolve("sello-preferences.preferences_pb") }
        )
        store = opened
        return DataStoreUserPreferences(opened)
    }

    @After
    fun end() = runBlocking { job?.cancelAndJoin() ?: Unit }

    /** Every key on the file by its stored name, with its value. */
    private suspend fun stored(): Map<String, Any> =
        store!!.data.first().asMap().mapKeys { it.key.name }

    private suspend fun raw(change: suspend (MutablePreferences) -> Unit) {
        store!!.edit { change(it) }
    }

    private suspend fun DataStoreUserPreferences.guidance() = observeGuidance().first()

    private val nov8: LocalDate = LocalDate.of(2026, 11, 8)

    private val newUserNudges = setOf(
        Nudge.BulkEntry,
        Nudge.FixedPayment,
        Nudge.Carry,
        Nudge.Deleting,
        Nudge.Investments,
        Nudge.ReciboGoal
    )

    @Test
    fun `a new phone has seen nothing and has not been checked`() = runBlocking {
        assertEquals(PreferenceRead.Ready(Guidance()), process().guidance())
    }

    @Test
    fun `what is seen, shown and turned off reads back in a new process`() = runBlocking {
        val first = process()
        assertEquals(PreferenceWrite.Saved, first.markSeen(Nudge.Deleting))
        assertEquals(PreferenceWrite.Saved, first.markSeen(Nudge.GuideHint))
        assertEquals(PreferenceWrite.Saved, first.markShown(Nudge.BackupReminder, nov8))
        assertEquals(
            PreferenceWrite.Saved,
            first.markShown(Nudge.Carry, LocalDate.of(2026, 11, 9))
        )
        assertEquals(PreferenceWrite.Saved, first.turnOffBackupReminder())

        assertEquals(
            PreferenceRead.Ready(
                Guidance(
                    GuidanceMemory(
                        seen = setOf(Nudge.Deleting, Nudge.GuideHint),
                        lastShown = ShownNudge(Nudge.Carry, LocalDate.of(2026, 11, 9)),
                        // Only the backup reminder moves its own day.
                        backupReminderShown = nov8,
                        backupReminderOff = true
                    )
                )
            ),
            process().guidance()
        )
    }

    @Test
    fun `each value is kept under a stable name, never an ordinal or a class name`() = runBlocking {
        val preferences = process()
        Nudge.entries.forEach { preferences.markSeen(it) }
        preferences.markShown(Nudge.BackupReminder, nov8)
        preferences.turnOffBackupReminder()
        preferences.rememberExistingUse(wasUsingSello = false)

        assertEquals(
            mapOf(
                "guidance_seen_guide_hint_v1" to true,
                "guidance_seen_backup_reminder_v1" to true,
                "guidance_seen_bulk_entry_v1" to true,
                "guidance_seen_fixed_payment_v1" to true,
                "guidance_seen_carry_v1" to true,
                "guidance_seen_deleting_v1" to true,
                "guidance_seen_investments_v1" to true,
                "guidance_seen_recibo_goal_v1" to true,
                "guidance_last_shown_v1" to "2026-11-08|backup_reminder",
                "guidance_backup_reminder_shown_v1" to "2026-11-08",
                "guidance_backup_reminder_off_v1" to true,
                "guidance_existing_use_checked_v1" to true
            ),
            stored()
        )
    }

    // --- Restablecer ---

    @Test
    fun `reset keeps every guidance value and clears everything else as before`() = runBlocking {
        val preferences = process()
        preferences.setTheme(ThemePreference.Dark)
        preferences.setInk(InkPreference.Violeta)
        preferences.setCategoryOrder(CategoryOrder.Alphabetical)
        preferences.setSmallExpenseLine(SmallExpenseLine.of(15_000)!!)
        preferences.dismissPastExpenseAnnouncement()
        preferences.markCatchUpNoticeSeen(Instant.parse("2026-11-01T10:00:00Z"))
        preferences.backupCompleted(
            LastBackup(
                Instant.parse("2026-11-02T10:00:00Z"),
                "sello-respaldo.json",
                1_234,
                BackupSummary(1, 2, 3, 0, 0)
            )
        )
        // A key this version no longer knows goes too, as clear() took it before.
        raw { it[stringPreferencesKey("retired_setting_v1")] = "x" }
        preferences.markSeen(Nudge.Investments)
        preferences.markShown(Nudge.BackupReminder, nov8)
        preferences.turnOffBackupReminder()
        preferences.rememberExistingUse(wasUsingSello = true)
        val guidanceBefore = preferences.guidance()

        assertEquals(PreferenceWrite.Saved, preferences.resetAll())

        val after = process()
        assertEquals(guidanceBefore, after.guidance())
        assertEquals(
            setOf(
                "guidance_seen_investments_v1",
                "guidance_seen_bulk_entry_v1",
                "guidance_seen_fixed_payment_v1",
                "guidance_seen_carry_v1",
                "guidance_seen_deleting_v1",
                "guidance_seen_recibo_goal_v1",
                "guidance_last_shown_v1",
                "guidance_backup_reminder_shown_v1",
                "guidance_backup_reminder_off_v1",
                "guidance_existing_use_checked_v1"
            ),
            stored().keys
        )
        assertEquals(
            PreferenceRead.Ready(AppearancePreference()),
            after.observeAppearance().first()
        )
        assertEquals(
            PreferenceRead.Ready(CategoryOrderingPreference()),
            after.observeCategoryOrdering().first()
        )
        assertEquals(
            PreferenceRead.Ready(SmallExpenseLine.DEFAULT),
            after.observeSmallExpenseLine().first()
        )
        assertEquals(
            PreferenceRead.Ready(CorrectionAnnouncementPreference(false, false)),
            after.observeCorrectionAnnouncements().first()
        )
        assertEquals(PreferenceRead.Ready(null), after.observeCatchUpNoticeSeen().first())
        assertEquals(
            PreferenceRead.Ready(BackupRecord(null, false)),
            after.observeBackups().first()
        )
    }

    @Test
    fun `reset with no guidance stored leaves an empty file`() = runBlocking {
        val preferences = process()
        preferences.setTheme(ThemePreference.Light)

        assertEquals(PreferenceWrite.Saved, preferences.resetAll())

        assertEquals(emptyMap<String, Any>(), stored())
    }

    // --- Values that cannot be read ---

    /** Each a guidance value that cannot be read, beside a good one. */
    private val damages: List<suspend (MutablePreferences) -> Unit> = listOf(
        { it[stringPreferencesKey("guidance_seen_carry_v1")] = "true" },
        { it[intPreferencesKey("guidance_seen_guide_hint_v1")] = 1 },
        { it[stringPreferencesKey("guidance_last_shown_v1")] = "2026-11-08" },
        {
            it[
                stringPreferencesKey(
                    "guidance_last_shown_v1"
                )
            ] = "2026-11-08|walkthrough"
        },
        { it[stringPreferencesKey("guidance_last_shown_v1")] = "2026-02-30|carry" },
        { it[stringPreferencesKey("guidance_last_shown_v1")] = "2026-11-08|carry|x" },
        { it[longPreferencesKey("guidance_last_shown_v1")] = 20_261_108L },
        { it[stringPreferencesKey("guidance_backup_reminder_shown_v1")] = "8/11/2026" },
        { it[stringPreferencesKey("guidance_backup_reminder_off_v1")] = "no" },
        { it[intPreferencesKey("guidance_existing_use_checked_v1")] = 0 }
    )

    @Test
    fun `a stored value that cannot be read fails the read, never read as seen or not`() =
        runBlocking {
            damages.forEachIndexed { index, damage ->
                val preferences = process()
                raw { it.clear() }
                preferences.markSeen(Nudge.Deleting)
                raw(damage)
                val before = stored()

                assertEquals(
                    "damage $index",
                    PreferenceRead.Failed<Guidance>(PreferenceFailure.InvalidValue, null),
                    preferences.guidance()
                )
                // A merge or the one-time check would have to read it: they write nothing.
                assertEquals(
                    "damage $index",
                    PreferenceWrite.Failed(PreferenceFailure.InvalidValue),
                    preferences.mergeRestored(GuidanceMemory(seen = setOf(Nudge.Carry)))
                )
                assertEquals(
                    "damage $index",
                    PreferenceWrite.Failed(PreferenceFailure.InvalidValue),
                    preferences.rememberExistingUse(wasUsingSello = true)
                )
                assertEquals("damage $index", before, stored())
            }
        }

    // --- People already using Sello ---

    @Test
    fun `someone already using Sello has every new-user nudge seen, not the hint or reminder`() =
        runBlocking {
            val preferences = process()

            assertEquals(PreferenceWrite.Saved, preferences.rememberExistingUse(true))

            assertEquals(
                PreferenceRead.Ready(Guidance(GuidanceMemory(seen = newUserNudges), true)),
                process().guidance()
            )
        }

    @Test
    fun `someone new has nothing marked, and the check is still remembered`() = runBlocking {
        assertEquals(PreferenceWrite.Saved, process().rememberExistingUse(false))

        assertEquals(
            PreferenceRead.Ready(Guidance(GuidanceMemory(), existingUseChecked = true)),
            process().guidance()
        )
    }

    @Test
    fun `the check is answered once, so later data marks nothing`() = runBlocking {
        val preferences = process()
        preferences.rememberExistingUse(false)

        assertEquals(PreferenceWrite.Saved, process().rememberExistingUse(true))

        assertEquals(
            PreferenceRead.Ready(Guidance(GuidanceMemory(), existingUseChecked = true)),
            process().guidance()
        )
    }

    @Test
    fun `the check keeps what was already seen`() = runBlocking {
        val preferences = process()
        preferences.markSeen(Nudge.GuideHint)

        preferences.rememberExistingUse(true)

        assertEquals(
            PreferenceRead.Ready(
                Guidance(GuidanceMemory(seen = newUserNudges + Nudge.GuideHint), true)
            ),
            process().guidance()
        )
    }

    // --- Restore ---

    @Test
    fun `a restore merges - either's seen, either's off, the later day, nothing forgotten`() =
        runBlocking {
            val preferences = process()
            preferences.markSeen(Nudge.GuideHint)
            preferences.markShown(Nudge.GuideHint, LocalDate.of(2026, 11, 1))
            preferences.markShown(Nudge.BackupReminder, LocalDate.of(2026, 10, 2))
            preferences.markShown(Nudge.Deleting, LocalDate.of(2026, 11, 3))
            preferences.rememberExistingUse(false)

            assertEquals(
                PreferenceWrite.Saved,
                preferences.mergeRestored(
                    GuidanceMemory(
                        seen = setOf(Nudge.Carry),
                        lastShown = ShownNudge(Nudge.Carry, LocalDate.of(2026, 11, 2)),
                        backupReminderShown = LocalDate.of(2026, 10, 30),
                        backupReminderOff = true
                    )
                )
            )

            assertEquals(
                PreferenceRead.Ready(
                    Guidance(
                        GuidanceMemory(
                            seen = setOf(Nudge.GuideHint, Nudge.Carry),
                            lastShown = ShownNudge(Nudge.Deleting, LocalDate.of(2026, 11, 3)),
                            backupReminderShown = LocalDate.of(2026, 10, 30),
                            backupReminderOff = true
                        ),
                        existingUseChecked = true
                    )
                ),
                process().guidance()
            )
        }

    @Test
    fun `a restore onto a new phone takes the backup's memory and leaves the check alone`() =
        runBlocking {
            val backup = GuidanceMemory(
                seen = setOf(Nudge.Investments),
                lastShown = ShownNudge(Nudge.Investments, nov8),
                backupReminderShown = nov8,
                backupReminderOff = false
            )

            assertEquals(PreferenceWrite.Saved, process().mergeRestored(backup))

            assertEquals(PreferenceRead.Ready(Guidance(backup, false)), process().guidance())
            assertEquals(
                setOf(
                    "guidance_seen_investments_v1",
                    "guidance_last_shown_v1",
                    "guidance_backup_reminder_shown_v1"
                ),
                stored().keys
            )
        }

    // --- Failed reads and writes ---

    private class UnavailableDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("not readable") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw IOException("not writable")
    }

    /** Reads an empty file; every write fails as a full or broken disk would. */
    private class UnwritableDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> =
            MutableStateFlow(emptyPreferences())

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences = throw IOException("not writable")
    }

    @Test
    fun `a failed read gives nothing to show, never an empty memory`() = runBlocking {
        assertEquals(
            PreferenceRead.Failed<Guidance>(PreferenceFailure.Unavailable, null),
            DataStoreUserPreferences(UnavailableDataStore()).guidance()
        )
    }

    @Test
    fun `a failed write is reported, never thrown, and the nudge stays to show again`() =
        runBlocking {
            val preferences = DataStoreUserPreferences(UnwritableDataStore())
            val failed = PreferenceWrite.Failed(PreferenceFailure.Unavailable)

            assertEquals(failed, preferences.markSeen(Nudge.GuideHint))
            assertEquals(failed, preferences.markShown(Nudge.GuideHint, nov8))
            assertEquals(failed, preferences.turnOffBackupReminder())
            assertEquals(failed, preferences.rememberExistingUse(true))
            assertEquals(failed, preferences.mergeRestored(GuidanceMemory()))
            assertEquals(failed, preferences.resetAll())
            assertEquals(PreferenceRead.Ready(Guidance()), preferences.guidance())
        }

    @Test
    fun `a death between the check's read and its write leaves the file to check again`() =
        runBlocking {
            val dying = process()
            // The check reads its answer and the history, then the process ends before its
            // one write: the real file holds nothing of it.
            val counts = object : Resets {
                override suspend fun preview(): Outcome<ResetPreview, StorageFailure> {
                    job!!.cancelAndJoin()
                    return Outcome.Success(ResetPreview(BackupSummary(0, 4, 0, 0, 0), 1, 1))
                }

                override suspend fun reset(preview: ResetPreview, operationId: OperationId) =
                    throw AssertionError("never erases")

                override suspend fun pending() = false

                override suspend fun finishPending(): ResetOutcome.Erased? = null
            }
            runCatching { RememberExistingUse(counts, dying).run() }

            val next = process()
            assertEquals(PreferenceRead.Ready(Guidance()), next.guidance())
            assertEquals(emptyMap<String, Any>(), stored())

            assertEquals(PreferenceWrite.Saved, next.rememberExistingUse(true))

            // Its one write lands whole: every new-user nudge and the check, read back after
            // that process ends too.
            assertEquals(
                PreferenceRead.Ready(Guidance(GuidanceMemory(seen = newUserNudges), true)),
                process().guidance()
            )
        }

    @Test
    fun `reset clears guidance that cannot be read with the rest, so it starts over`() =
        runBlocking {
            damages.forEachIndexed { index, damage ->
                val preferences = process()
                raw { it.clear() }
                preferences.setTheme(ThemePreference.Dark)
                preferences.markSeen(Nudge.Deleting)
                raw(damage)

                assertEquals("damage $index", PreferenceWrite.Saved, preferences.resetAll())

                assertEquals("damage $index", emptyMap<String, Any>(), stored())
                assertEquals(
                    "damage $index",
                    PreferenceRead.Ready(Guidance()),
                    process().guidance()
                )
            }
        }

    @Test
    fun `a stored false reads as not seen, not as damage`() = runBlocking {
        val preferences = process()
        preferences.markSeen(Nudge.Carry)
        raw { it[booleanPreferencesKey("guidance_seen_deleting_v1")] = false }

        assertEquals(
            PreferenceRead.Ready(Guidance(GuidanceMemory(seen = setOf(Nudge.Carry)))),
            preferences.guidance()
        )
    }
}
