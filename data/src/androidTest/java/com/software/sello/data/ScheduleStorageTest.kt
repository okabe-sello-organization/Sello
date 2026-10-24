package com.software.sello.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.entity.ExpenseEntity
import com.software.sello.data.mapper.toDomain
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.ScheduleBook
import com.software.sello.domain.port.StoredSchedule
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Schedules and entry origins (schema 5) written straight into the real database and
 * read back through the real queries and mappers. The schedules are those of the
 * recurrence examples RE05, RE10 and RE21 (docs/testing/recurrence-examples.md).
 * The category Hogar is record 100; schedules are 200 and 201.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleStorageTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val millis = recordedAt.toEpochMilli()
    private val rent = uuid(200)
    private val salary = uuid(201)

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) = TransactionAmount.of(Money.cop(amount)).valueOrFail()

    private fun hogar() = runBlocking {
        ledger.categories.submit(ledger.createCategory("Hogar"))
    }

    /** RE10's rent: monthly on the 5th, 950.000 in Hogar, handled through 5 October. */
    private fun schedule(id: String = rent, vararg changes: Pair<String, Any?>) {
        val columns = linkedMapOf<String, Any?>(
            "id" to id,
            "kind" to "payment",
            "category_id" to uuid(100),
            "source_key" to null,
            "source_name" to null,
            "amount_minor" to 950_000L,
            "currency" to "COP",
            "note" to "Arriendo",
            "rule" to "monthly",
            "rule_first" to 5,
            "rule_second" to null,
            "start_date" to "2026-09-01",
            "end_date" to null,
            "sequence" to 0L,
            "handled_date" to "2026-10-05",
            "handled_slot" to 1,
            "version" to 1L,
            "created_at" to millis,
            "updated_at" to millis
        )
        columns.putAll(changes)
        ledger.database.insertRaw("schedule", *columns.toList().toTypedArray())
    }

    private fun pause(schedule: String, from: String, until: String?) = ledger.database.insertRaw(
        "schedule_pause",
        "schedule_id" to schedule,
        "from_date" to from,
        "until_date" to until
    )

    private suspend fun book() = ledger.scheduleReads.book()

    @Test
    fun withNoScheduleTheBookIsEmptyAtTheProfilesRevision() = runBlocking {
        val revision = ledger.revision()

        assertEquals(Outcome.Success(ScheduleBook(1, revision, emptyList())), book())
    }

    @Test
    fun storedSchedulesReadBackInCreationOrderWithTheirPausesAndCheckpoints() = runBlocking {
        hogar()
        // RE05's salary, created after the rent: twice a month on the 15th and 30th.
        schedule(
            salary,
            "kind" to "income",
            "category_id" to null,
            "source_key" to "salary",
            "amount_minor" to 800_000L,
            "note" to null,
            "rule" to "twice_monthly",
            "rule_first" to 15,
            "rule_second" to 30,
            "start_date" to "2026-10-01",
            "sequence" to 7L,
            "handled_date" to "2026-10-15",
            "handled_slot" to 1
        )
        schedule(rent, "sequence" to 3L, "version" to 2L)
        // RE21: paused on 3 October, resumed on 5 December; and a pause still open.
        pause(rent, "2026-10-03", "2026-12-05")
        pause(rent, "2027-02-01", null)

        val expected = listOf(
            StoredSchedule(
                schedule = Schedule(
                    ScheduleId.of(rent).valueOrFail(),
                    ScheduledEntry.Payment(
                        CategoryId.of(uuid(100)).valueOrFail(),
                        pesos(950_000),
                        Note.of("Arriendo").valueOrFail()
                    ),
                    Recurrence.of(
                        RepeatRule.Monthly.of(5).valueOrFail(),
                        d("2026-09-01"),
                        null,
                        listOf(
                            SchedulePause(d("2026-10-03"), d("2026-12-05")),
                            SchedulePause(d("2027-02-01"), null)
                        )
                    ).valueOrFail()
                ),
                sequence = 3,
                handled = Occurrence(d("2026-10-05"), 1),
                version = 2,
                createdAt = recordedAt,
                updatedAt = recordedAt
            ),
            StoredSchedule(
                schedule = Schedule(
                    ScheduleId.of(salary).valueOrFail(),
                    ScheduledEntry.Income(IncomeSource.Salary, pesos(800_000), null),
                    Recurrence.of(
                        RepeatRule.TwiceMonthly.of(15, 30).valueOrFail(),
                        d("2026-10-01")
                    ).valueOrFail()
                ),
                sequence = 7,
                handled = Occurrence(d("2026-10-15"), 1),
                version = 1,
                createdAt = recordedAt,
                updatedAt = recordedAt
            )
        )

        assertEquals(expected, book().valueOrFail().schedules)
    }

    @Test
    fun aScheduleNotYetCaughtUpHasNoCheckpoint() = runBlocking {
        hogar()
        schedule(rent, "handled_date" to null, "handled_slot" to null)

        assertEquals(null, book().valueOrFail().schedules.single().handled)
    }

    /** Each damaged column fails the whole read, named, with nothing left out or repaired. */
    @Test
    fun everyDamagedColumnFailsTheReadNamingIt() {
        val cases = listOf(
            "kind" to arrayOf<Pair<String, Any?>>("kind" to "gasto"),
            "category_id" to arrayOf("category_id" to null),
            "source_key" to arrayOf("source_key" to "salary"),
            "currency" to arrayOf("currency" to "USD"),
            "amount_minor" to arrayOf("amount_minor" to 0L),
            "note" to arrayOf("note" to " "),
            "rule" to arrayOf("rule" to "weekly"),
            "rule_first" to arrayOf("rule_first" to 32),
            "rule_second" to arrayOf("rule_second" to 20),
            "rule_second" to
                arrayOf("rule" to "twice_monthly", "rule_first" to 15, "rule_second" to 15),
            "rule_first" to arrayOf("rule" to "every_days", "rule_first" to 366),
            "rule_first" to arrayOf("rule" to "every_weeks", "rule_first" to 0),
            "start_date" to arrayOf("start_date" to "2026-02-30"),
            "end_date" to arrayOf("end_date" to "2026-08-31"),
            "handled_slot" to arrayOf("handled_slot" to 2),
            "handled_slot" to arrayOf("handled_slot" to null),
            "handled_date" to arrayOf("handled_date" to null),
            "sequence" to arrayOf("sequence" to -1L),
            "version" to arrayOf("version" to 0L)
        )
        for ((column, changes) in cases) {
            runBlocking {
                ledger.database.openHelper.writableDatabase.execSQL("DELETE FROM schedule_pause")
                ledger.database.openHelper.writableDatabase.execSQL("DELETE FROM schedule")
                if (ledger.long("SELECT COUNT(*) FROM category") == 0L) hogar()
                schedule(rent, *changes)

                assertEquals(
                    "$column ${changes.toList()}",
                    Outcome.Failure(StorageFailure.Integrity("schedule", rent, column)),
                    book()
                )
            }
        }
    }

    @Test
    fun overlappingPausesFailTheRead() = runBlocking {
        hogar()
        schedule(rent)
        pause(rent, "2026-10-03", "2026-11-10")
        pause(rent, "2026-11-01", null)

        assertEquals(
            Outcome.Failure(StorageFailure.Integrity("schedule", rent, "pauses")),
            book()
        )
    }

    @Test
    fun aPauseCannotOutliveItsScheduleNorAScheduleItsCategory() {
        hogar()
        assertThrows(SQLiteConstraintException::class.java) { pause(uuid(999), "2026-10-03", null) }
        assertThrows(SQLiteConstraintException::class.java) {
            schedule(rent, "category_id" to uuid(998))
        }
    }

    // ---- Origins ----

    private fun recordedRent(expense: Int, origin: Triple<String?, String?, Int?>) =
        ledger.database.insertRaw(
            "expense",
            "id" to uuid(expense),
            "category_id" to uuid(100),
            "effective_date" to "2026-10-05",
            "sequence" to expense.toLong(),
            "amount_minor" to 950_000L,
            "currency" to "COP",
            "note" to "Arriendo",
            "version" to 1L,
            "created_at" to millis,
            "updated_at" to millis,
            "origin_schedule_id" to origin.first,
            "origin_date" to origin.second,
            "origin_slot" to origin.third
        )

    private fun storedExpense(expense: Int): ExpenseEntity = runBlocking {
        ledger.database.expenseDao().byId(uuid(expense))!!
    }

    @Test
    fun anExpenseKeepsTheOccurrenceThatRecordedItAndAManualOneHasNone() {
        hogar()
        recordedRent(300, Triple(rent, "2026-10-05", 1))
        recordedRent(301, Triple(null, null, null))

        assertEquals(
            EntryOrigin(ScheduleId.of(rent).valueOrFail(), Occurrence(d("2026-10-05"), 1)),
            storedExpense(300).toDomain().valueOrFail().origin
        )
        assertEquals(null, storedExpense(301).toDomain().valueOrFail().origin)
    }

    @Test
    fun anOriginIsAllThreeColumnsOrNone() {
        hogar()
        val damaged = listOf(
            Triple(rent, null, 1) to "origin_date",
            Triple(null, "2026-10-05", 1) to "origin_schedule_id",
            Triple(rent, "2026-10-05", null) to "origin_slot",
            Triple(rent, "2026-10-05", 3) to "origin_slot",
            Triple("Arriendo", "2026-10-05", 1) to "origin_schedule_id"
        )
        damaged.forEachIndexed { index, (origin, column) ->
            recordedRent(310 + index, origin)

            assertEquals(
                Outcome.Failure(StorageFailure.Integrity("expense", uuid(310 + index), column)),
                storedExpense(310 + index).toDomain()
            )
        }
    }

    @Test
    fun anOccurrenceIsRecordedAtMostOnce() {
        hogar()
        recordedRent(300, Triple(rent, "2026-10-05", 1))

        assertThrows(SQLiteConstraintException::class.java) {
            recordedRent(301, Triple(rent, "2026-10-05", 1))
        }
        // The other slot of the same date is a different occurrence (RE06).
        recordedRent(302, Triple(rent, "2026-10-05", 2))
    }

    @Test
    fun aDeletedOccurrenceComesBackWithItsOriginOnUndo() = runBlocking {
        hogar()
        val created = ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 950_000))
        val expense = (created as ExpenseCommandOutcome.Committed).expense.expenseId
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE expense SET origin_schedule_id = '$rent', origin_date = '2026-10-05', " +
                "origin_slot = 1 WHERE id = '${expense.value}'"
        )
        val deletion = ledger.operation()

        ledger.expenses.delete(DeleteExpense(deletion, 1, expense, 1))
        assertEquals(
            listOf("$rent|2026-10-05|1"),
            ledger.rows("deleted_expense", "origin_schedule_id", "origin_date", "origin_slot")
        )
        val restored = ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))

        assertTrue("$restored", restored is ExpenseCommandOutcome.Committed)
        assertEquals(
            listOf("$rent|2026-10-05|1"),
            ledger.rows("expense", "origin_schedule_id", "origin_date", "origin_slot")
        )
    }

    @Test
    fun undoIsRefusedWhileAnotherEntryRecordsTheSameOccurrence() = runBlocking {
        hogar()
        val created = ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 950_000))
        val expense = (created as ExpenseCommandOutcome.Committed).expense.expenseId
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE expense SET origin_schedule_id = '$rent', origin_date = '2026-10-05', " +
                "origin_slot = 1 WHERE id = '${expense.value}'"
        )
        val deletion = ledger.operation()
        ledger.expenses.delete(DeleteExpense(deletion, 1, expense, 1))
        recordedRent(400, Triple(rent, "2026-10-05", 1))

        val restored = ledger.expenses.restore(RestoreExpense(ledger.operation(), 1, deletion))

        assertEquals(ExpenseCommandOutcome.Rejected(ExpenseRejection.UndoConflict), restored)
    }
}
