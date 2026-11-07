package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.backup.RoomRestores
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.data.repository.RoomScheduleRecordReads
import com.software.sello.data.reset.RoomResets
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.ChangeSchedule
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.FinancialClock
import com.software.sello.domain.port.FinancialDay
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceReset
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.ResetOutcome
import com.software.sello.domain.port.RestoreOutcome
import com.software.sello.domain.port.ScheduleCommandOutcome
import com.software.sello.domain.port.ScheduleRecord
import com.software.sello.domain.port.StoredSchedule
import com.software.sello.domain.usecase.CatchUpResult
import com.software.sello.domain.usecase.CatchUpSchedules
import com.software.sello.domain.usecase.CatchUpStop
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schedules through a real backup, restore and erase, each on real database files
 * (docs/testing/recurrence-examples.md, RE05, RE10). The working checkpoint is that a
 * fresh installation restored from the backup records nothing the backup already holds
 * and goes on recording after it.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleBackupRestoreTest {
    @get:Rule
    val files = DatabaseFiles()

    @get:Rule
    val fresh = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val other by lazy { TestLedger(fresh).also { it.start() } }
    private val dispatchers = realDispatchers
    private val investments = HandInvestmentPreferences()
    private val smallExpenses = HandSmallExpensePreferences()
    private val preferences = object : AppearancePreferences, CategoryOrderingPreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = flowOf(
            PreferenceRead.Ready(
                AppearancePreference(ThemePreference.System, InkPreference.Cobalto)
            )
        )

        override suspend fun setTheme(theme: ThemePreference) = PreferenceWrite.Saved

        override suspend fun setInk(ink: InkPreference) = PreferenceWrite.Saved

        override suspend fun restoreInk(ink: InkPreference, tuyaHue: InkHue?) = setInk(ink)

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(
                PreferenceRead.Ready(
                    CategoryOrderingPreference(
                        CategoryOrder.MostUsed,
                        CategoryOrderDirection.Descending
                    )
                )
            )

        override suspend fun setCategoryOrder(order: CategoryOrder) = PreferenceWrite.Saved

        override suspend fun setCategoryOrderDirection(direction: CategoryOrderDirection) =
            PreferenceWrite.Saved
    }
    private val notice = NoticeSeen()
    private var nextId = 5_000

    private fun d(text: String) = LocalDate.parse(text)

    private suspend fun TestLedger.schedule(
        start: String,
        rule: RepeatRule,
        entry: ScheduledEntry
    ): ScheduleId {
        goTo(start)
        val created = schedules.submit(CreateSchedule(operation(), 1, entry, rule, d(start), null))
        return (created as CommandOutcome.Committed).value.scheduleId
    }

    /** Everything due up to [today], in as many batches as it takes, by a process started now. */
    private suspend fun TestLedger.catchUp(today: String): List<String> {
        val store = RoomScheduleCatchUp(database, { audit }, { uuid(nextRecord++) }, notice)
        val generation = long("SELECT generation FROM profile")
        val recorded = mutableListOf<String>()
        while (true) {
            val batch = store.recordDue(generation, d(today), 50) as CatchUpBatch.Recorded
            recorded += batch.occurrences.map { it.origin.occurrence.date.toString() }
            if (batch.occurrences.size < 50) return recorded
        }
    }

    /**
     * RE10's rent and bus, RE05's salary, caught up to 24 October, the bus then paused.
     * Hogar is record 100, Transporte 101.
     */
    private suspend fun history() {
        ledger.goTo("2026-09-01")
        ledger.categories.submit(ledger.createCategory("Hogar"))
        ledger.categories.submit(ledger.createCategory("Transporte"))
        ledger.schedule(
            "2026-09-01",
            RepeatRule.Monthly.of(5).valueOrFail(),
            ScheduledEntry.Payment(
                ledger.categoryId(100),
                transactionPesos(950_000),
                Note.of("Arriendo").valueOrFail()
            )
        )
        val bus = ledger.schedule(
            "2026-09-26",
            RepeatRule.EveryWeeks.of(1).valueOrFail(),
            ScheduledEntry.Payment(ledger.categoryId(101), transactionPesos(20_000), null)
        )
        ledger.schedule(
            "2026-10-01",
            RepeatRule.TwiceMonthly.of(15, 30).valueOrFail(),
            ScheduledEntry.Income(IncomeSource.Salary, transactionPesos(800_000), null)
        )
        ledger.goTo("2026-10-24")
        ledger.catchUp("2026-10-24")
        val version = ledger.scheduleReads.book().valueOrFail().schedules.single {
            it.schedule.id ==
                bus
        }.version
        assertTrue(
            ledger.schedules.submit(
                PauseSchedule(ledger.operation(), 1, bus, version)
            ) is CommandOutcome.Committed
        )
    }

    private suspend fun backup(): ByteArray = BackupExport(
        RoomBackupSnapshots(ledger.database),
        preferences,
        preferences,
        investments,
        smallExpenses,
        { ledger.audit },
        ledger.clock,
        dispatchers
    )
        .create().valueOrFail().bytes

    private fun restoresInto(target: TestLedger, folder: DatabaseFiles) = RoomRestores(
        target.database,
        folder.restoreFolder,
        preferences,
        preferences,
        investments,
        smallExpenses,
        notice,
        { uuid(nextId++) },
        { target.audit },
        dispatchers,
        Mutex(),
        { true }
    )

    /** What a schedule is, without its version, which a restore starts again at 1. */
    private fun StoredSchedule.terms() = listOf(schedule, sequence, handled, createdAt, updatedAt)

    private fun TestLedger.entries() = rows(
        "expense",
        "effective_date",
        "amount_minor",
        "origin_schedule_id",
        "origin_date",
        "origin_slot"
    ) +
        rows(
            "income",
            "effective_date",
            "amount_minor",
            "origin_schedule_id",
            "origin_date",
            "origin_slot"
        )

    @Test
    fun aBackupWithSchedulesRestoresAndCatchUpGoesOnAfterWhatItHolds() = runBlocking {
        history()
        val before = ledger.scheduleReads.book().valueOrFail().schedules
        val entries = ledger.entries()
        val bytes = backup()

        other.goTo("2026-10-24")
        val restores = restoresInto(other, fresh)
        val staged = restores.stage(bytes).valueOrFail()
        assertEquals(3, staged.replacement.schedules)
        assertEquals(0, staged.current.schedules)
        val outcome = restores.replace(staged)

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(3, (outcome as RestoreOutcome.Restored).summary.schedules)
        val after = other.scheduleReads.book().valueOrFail().schedules
        assertEquals(before.map { it.terms() }, after.map { it.terms() })
        assertTrue(after.all { it.version == 1L })
        assertEquals(entries, other.entries())

        // Nothing the backup holds is recorded again, on the day it was made or later.
        assertEquals(emptyList<String>(), other.catchUp("2026-10-24"))
        other.goTo("2026-11-05")
        // Then the salary of 30 October and the rent of 5 November; the bus is paused.
        assertEquals(listOf("2026-10-30", "2026-11-05"), other.catchUp("2026-11-05"))
    }

    /**
     * A new phone restored from the backup: the catch-up notice lists nothing the backup
     * brought, only what Sello records after the restore (owner, 2026-10-25).
     */
    @Test
    fun aFreshInstallationRestoredListsOnlyWhatIsRecordedAfterInTheNotice() = runBlocking {
        history()
        val bytes = backup()
        other.goTo("2026-10-24")
        other.audit = Instant.parse("2026-10-24T20:00:00Z")
        val restores = restoresInto(other, fresh)

        restores.replace(restores.stage(bytes).valueOrFail())
        val reads = RoomScheduleRecordReads(other.database)

        assertEquals(listOf(Instant.parse("2026-10-24T20:00:00Z")), notice.marks)
        assertEquals(Outcome.Success(emptyList<ScheduleRecord>()), reads.recordedAfter(notice.seen))
        other.goTo("2026-11-05")
        other.audit = Instant.parse("2026-11-05T13:00:00Z")
        other.catchUp("2026-11-05")
        assertEquals(
            listOf("2026-10-30", "2026-11-05"),
            (reads.recordedAfter(notice.seen) as Outcome.Success).value.map {
                when (it) {
                    is ScheduleRecord.Payment -> it.expense.date.toString()
                    is ScheduleRecord.Earning -> it.income.date.toString()
                }
            }
        )
    }

    /**
     * #340: restored at 20:00 on 24 October, which marks what the backup brought seen;
     * the clock is then set back to 08:00. What catch-up records after is still listed.
     */
    @Test
    fun aClockSetBackAfterARestoreStillListsWhatIsRecordedAfter() = runBlocking {
        history()
        val bytes = backup()
        other.goTo("2026-10-24")
        other.audit = Instant.parse("2026-10-24T20:00:00Z")
        val restores = restoresInto(other, fresh)
        restores.replace(restores.stage(bytes).valueOrFail())
        val reads = RoomScheduleRecordReads(other.database)

        other.goTo("2026-11-05")
        other.audit = Instant.parse("2026-10-24T08:00:00Z")
        other.catchUp("2026-11-05")

        assertEquals(
            listOf("2026-10-30", "2026-11-05"),
            (reads.recordedAfter(notice.seen) as Outcome.Success).value.map {
                when (it) {
                    is ScheduleRecord.Payment -> it.expense.date.toString()
                    is ScheduleRecord.Earning -> it.income.date.toString()
                }
            }
        )
    }

    /**
     * SELLO-146: this process started in Madrid; at 01:00 on 15 November there (still
     * the 14th in Bogotá) a backup made in Bogotá is restored. Catch-up records nothing,
     * least of all the salary of the 15th, until a new process reads the restored zone;
     * that one records the salary of 30 October and the rent of 5 November.
     */
    @Test
    fun afterARestoreCatchUpWaitsForTheRestartAndItsZone() = runBlocking {
        history()
        val bytes = backup()
        val entries = ledger.entries()
        other.database.openHelper.writableDatabase.execSQL(
            "UPDATE profile SET financial_zone = 'Europe/Madrid'"
        )
        val madrid = ZoneId.of("Europe/Madrid")
        val processDay = object : FinancialClock {
            override val today = MutableStateFlow(FinancialDay(d("2026-11-15"), madrid))
        }
        val started = other.long("SELECT generation FROM profile")
        val store =
            RoomScheduleCatchUp(other.database, {
                other.audit
            }, { uuid(other.nextRecord++) }, notice)
        val restores = restoresInto(other, fresh)

        restores.replace(restores.stage(bytes).valueOrFail())
        val result = CatchUpSchedules(store, processDay, started).run()

        assertEquals(CatchUpResult(emptyList(), CatchUpStop.Stale), result)
        assertEquals(entries, other.entries())
        assertEquals("America/Bogota", other.rows("profile", "financial_zone").single())

        // The restart: a new process on the restored zone, where it is still the 14th.
        other.reopen()
        assertEquals(listOf("2026-10-30", "2026-11-05"), other.catchUp("2026-11-14"))
    }

    /**
     * SELLO-145: rent twice a month on 15 and 30, recorded through [recordedOn] (its second
     * slot), becomes monthly on 15 from the day after [changedOn]. The backup restores, and
     * the restored schedule records nothing again, then [next] up to 15 December.
     */
    private fun aRuleChangedFromTwiceAMonthRestores(
        recordedOn: String,
        changedOn: String,
        next: List<String>
    ) = runBlocking {
        ledger.goTo("2026-10-01")
        ledger.categories.submit(ledger.createCategory("Hogar"))
        val rent = ScheduledEntry.Payment(
            ledger.categoryId(100),
            transactionPesos(300_000),
            null
        )
        val id = ledger.schedule(
            "2026-10-01",
            RepeatRule.TwiceMonthly.of(15, 30).valueOrFail(),
            rent
        )
        ledger.goTo(recordedOn)
        ledger.catchUp(recordedOn)
        ledger.goTo(changedOn)
        val changed = ledger.schedules.submit(
            ChangeSchedule(
                ledger.operation(),
                1,
                id,
                1,
                rent,
                RepeatRule.Monthly.of(15).valueOrFail(),
                d(changedOn).plusDays(1),
                null
            )
        )
        assertTrue("$changed", changed is CommandOutcome.Committed)
        val before = ledger.scheduleReads.book().valueOrFail().schedules
        val entries = ledger.entries()

        val bytes = backup()
        other.goTo(changedOn)
        val restores = restoresInto(other, fresh)
        val outcome = restores.replace(restores.stage(bytes).valueOrFail())

        assertTrue("$outcome", outcome is RestoreOutcome.Restored)
        assertEquals(
            before.map { it.terms() },
            other.scheduleReads.book().valueOrFail().schedules.map { it.terms() }
        )
        assertEquals(entries, other.entries())
        assertEquals(emptyList<String>(), other.catchUp(changedOn))
        other.goTo("2026-12-15")
        assertEquals(next, other.catchUp("2026-12-15"))
    }

    @Test
    fun aRuleChangedAfterTheSecondSlotOfTwiceAMonthRestores() = aRuleChangedFromTwiceAMonthRestores(
        recordedOn = "2026-10-30",
        changedOn = "2026-11-02",
        next = listOf("2026-11-15", "2026-12-15")
    )

    @Test
    fun aRuleChangedOnTheDayOfTheSecondSlotOfTwiceAMonthRestores() =
        aRuleChangedFromTwiceAMonthRestores(
            recordedOn = "2026-11-30",
            changedOn = "2026-11-30",
            next = listOf("2026-12-15")
        )

    @Test
    fun aRestoreReplacesTheSchedulesThereNeverMerges() = runBlocking {
        history()
        val bytes = backup()
        other.goTo("2026-10-24")
        other.categories.submit(other.createCategory("Gimnasio"))
        other.schedule(
            "2026-10-24",
            RepeatRule.Monthly.of(28).valueOrFail(),
            ScheduledEntry.Payment(other.categoryId(100), transactionPesos(99_900), null)
        )

        val restores = restoresInto(other, fresh)
        val staged = restores.stage(bytes).valueOrFail()
        assertEquals(1, staged.current.schedules)
        restores.replace(staged)

        assertEquals(
            listOf(950_000L, 20_000L, 800_000L),
            other.scheduleReads.book().valueOrFail().schedules.map {
                it.schedule.entry.amount.money.minorUnits
            }
        )
    }

    /** SELLO-146: after an erase, too, catch-up waits for the restart. */
    @Test
    fun afterAnEraseCatchUpWaitsForTheRestart() = runBlocking {
        history()
        val started = ledger.long("SELECT generation FROM profile")
        val resets = RoomResets(
            ledger.database,
            files.resetFolder,
            files.restoreFolder,
            emptyList(),
            PreferenceReset { PreferenceWrite.Saved },
            { ledger.zone },
            { ledger.audit },
            dispatchers,
            Mutex()
        )
        assertTrue(
            resets.reset(resets.preview().valueOrFail(), ledger.operation()) is ResetOutcome.Erased
        )
        // The new history gets a monthly gym payment, due today, before the restart.
        val erased = ledger.long("SELECT generation FROM profile")
        val gym = ledger.nextRecord
        ledger.categories.submit(ledger.createCategory("Gimnasio", generation = erased))
        val created = ledger.schedules.submit(
            CreateSchedule(
                ledger.operation(),
                erased,
                ScheduledEntry.Payment(ledger.categoryId(gym), transactionPesos(99_900), null),
                RepeatRule.Monthly.of(24).valueOrFail(),
                d("2026-10-24"),
                null
            )
        )
        assertTrue("$created", created is CommandOutcome.Committed)
        val store =
            RoomScheduleCatchUp(ledger.database, {
                ledger.audit
            }, { uuid(ledger.nextRecord++) }, notice)

        assertEquals(CatchUpBatch.Stale, store.recordDue(started, d("2026-10-24"), 50))
        assertEquals(0L, ledger.long("SELECT COUNT(*) FROM expense"))
        // The restart: a new process records it once.
        ledger.reopen()
        assertEquals(listOf("2026-10-24"), ledger.catchUp("2026-10-24"))
        assertEquals(1L, ledger.long("SELECT COUNT(*) FROM expense"))
    }

    @Test
    fun erasingEverythingCountsAndRemovesEverySchedule() = runBlocking {
        history()
        val resets = RoomResets(
            ledger.database,
            files.resetFolder,
            files.restoreFolder,
            emptyList(),
            PreferenceReset { PreferenceWrite.Saved },
            { ledger.zone },
            { ledger.audit },
            dispatchers,
            Mutex()
        )
        val preview = resets.preview().valueOrFail()
        assertEquals(3, preview.current.schedules)

        resets.reset(preview, ledger.operation())

        for (table in listOf("schedule", "schedule_pause")) {
            assertEquals(table, 0L, ledger.long("SELECT COUNT(*) FROM $table"))
        }
    }
}
