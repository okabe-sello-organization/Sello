package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupCounts
import com.software.sello.data.backup.BackupExport
import com.software.sello.data.backup.RoomBackupSnapshots
import com.software.sello.data.repository.RoomCategoryRemovals
import com.software.sello.data.repository.RoomScheduleCatchUp
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TuyaColour
import com.software.sello.domain.port.AppearancePreferences
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CatchUpBatch
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryOrderingPreferences
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedCategoryChange
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.CreateSchedule
import com.software.sello.domain.port.DeleteCategory
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.Disposal
import com.software.sello.domain.port.PauseSchedule
import com.software.sello.domain.port.PreferenceRead
import com.software.sello.domain.port.PreferenceWrite
import com.software.sello.domain.port.RemovalPreview
import com.software.sello.domain.port.RemovalPreviewFailure
import com.software.sello.domain.port.RestoreExpense
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.TargetPreview
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting a category (ADR 0024) against a real database file. [history] is the common
 * start; every expected amount is worked out by hand in the comment beside it.
 */
@RunWith(AndroidJUnit4::class)
class CategoryRemovalContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }

    /** A getter: after [TestLedger.reopen] it reads the new handle. */
    private val removals get() = RoomCategoryRemovals(ledger.database, ledger.clock)

    private val august = YearMonth.of(2026, 8)
    private val october = YearMonth.of(2026, 10)
    private val cafe by lazy { ledger.categoryId(100) }
    private val comida by lazy { ledger.categoryId(101) }
    private val ocio by lazy { ledger.categoryId(102) }

    /** The time stamped on what the deletion writes, after [history]'s. */
    private val later = Instant.parse("2026-10-09T16:00:00.123Z")

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun versionOf(category: Int) =
        ledger.long("SELECT version FROM category WHERE id = '${uuid(category)}'")

    private fun receipts() = ledger.long("SELECT COUNT(*) FROM operation_receipt")

    private fun categoryRows() = ledger.rows("category", "id", "name", "archived", "version")

    private fun expenseRows() = ledger.rows(
        "expense",
        "id",
        "category_id",
        "effective_date",
        "sequence",
        "amount_minor",
        "note",
        "origin_schedule_id",
        "origin_date",
        "origin_slot",
        "version",
        "created_at",
        "updated_at"
    )

    /** Each expense as its id, category, date, amount and note, all by number. */
    private fun expenseFacts() =
        ledger.rows("expense", "id", "category_id", "effective_date", "amount_minor", "note")
            .map { it.replace("00000000-0000-4000-8000-000000000", "") }

    private fun scheduleRows() =
        ledger.rows("schedule", "id", "category_id", "amount_minor", "handled_date", "version")

    private fun pauseRows() =
        ledger.rows("schedule_pause", "schedule_id", "from_date", "until_date")

    private fun defaultRows() =
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor")

    private fun monthRows() =
        ledger.rows("month_limit", "category_id", "month", "kind", "limit_minor")

    private fun keptRows() = ledger.rows("deleted_expense", "deletion_id", "category_id")

    /** Everything a deletion could touch, to show that a refusal touched none of it. */
    private fun state() = listOf(
        categoryRows(),
        expenseRows(),
        scheduleRows(),
        pauseRows(),
        defaultRows(),
        monthRows(),
        ledger.chainRows(),
        keptRows(),
        listOf("receipts ${receipts()}", "revision ${ledger.revision()}")
    )

    private fun committed(outcome: CommandOutcome<*, *>) =
        assertTrue("$outcome", outcome is Committed && !outcome.replayed)

    private suspend fun spend(category: Int, date: String, pesos: Long, note: String? = null) =
        committed(
            ledger.expenses.create(
                ledger.createExpense(category, date, pesos).copy(note = Note.of(note).valueOrFail())
            )
        )

    /**
     * On 1 October: Café (100) with 50.000, Comida (101) with 200.000, Ocio (102) with
     * 30.000, and Café's fixed payment (103) of 25.000 on the 5th of each month. On the 9th:
     * Café 20.000 on 14 August (104) and 15.000 "Tinto" on 3 October (105), Comida 80.000 on
     * 2 October (106), Ocio 12.000 on 4 October (107); catch-up then records Café's payment
     * of 5 October (108), noted "Suscripción". Café holds 3 expenses, 20.000 + 15.000 +
     * 25.000 = 60.000.
     */
    private suspend fun history() {
        ledger.goTo("2026-10-01")
        committed(ledger.categories.submit(ledger.createCategory("Café", finite(50_000))))
        committed(ledger.categories.submit(ledger.createCategory("Comida", finite(200_000))))
        committed(ledger.categories.submit(ledger.createCategory("Ocio", finite(30_000))))
        committed(
            ledger.schedules.submit(
                CreateSchedule(
                    ledger.operation(),
                    1,
                    ScheduledEntry.Payment(
                        cafe,
                        transactionPesos(25_000),
                        Note.of("Suscripción").valueOrFail()
                    ),
                    RepeatRule.Monthly.of(5).valueOrFail(),
                    LocalDate.parse("2026-10-05"),
                    null
                )
            )
        )
        ledger.goTo("2026-10-09")
        spend(100, "2026-08-14", 20_000)
        spend(100, "2026-10-03", 15_000, note = "Tinto")
        spend(101, "2026-10-02", 80_000)
        spend(102, "2026-10-04", 12_000)
        val batch = RoomScheduleCatchUp(ledger.database, { ledger.audit }, {
            uuid(ledger.nextRecord++)
        }, NoticeSeen()).recordDue(1, LocalDate.parse("2026-10-09"), 50)
        assertTrue("$batch", batch is CatchUpBatch.Recorded)
    }

    /** Comida in October: 80.000 spent of its 200.000. */
    private val comidaTarget by lazy { TargetPreview(comida, cop(80_000), finite(200_000)) }

    /**
     * Café after [history]: 3 expenses, 60.000, from August to October, one fixed payment,
     * October's 15.000 + 25.000 = 40.000 spent of its 50.000.
     */
    private fun cafePreview(target: TargetPreview?) = RemovalPreview(
        categoryId = cafe,
        expenses = 3,
        total = cop(60_000),
        firstMonth = august,
        lastMonth = october,
        schedules = 1,
        month = october,
        spentThisMonth = cop(40_000),
        limitThisMonth = finite(50_000),
        target = target
    )

    private fun deleteCafe(
        disposal: Disposal,
        confirmed: RemovalPreview,
        version: Long = versionOf(100),
        operation: OperationId = ledger.operation(),
        confirmation: CorrectionConfirmation? = null
    ) = DeleteCategory(operation, 1, cafe, version, disposal, confirmed, confirmation)

    private fun deletion(operation: OperationId, revision: Long, at: Instant = later) =
        CommittedCategoryChange(
            OperationReceipt(operation, OperationKind.DeleteCategory, 1, revision, at),
            cafe
        )

    // ---- The preview ----

    @Test
    fun thePreviewCountsWhatTheCategoryHoldsAndTheTargetsMonth() = runBlocking {
        history()

        assertEquals(
            Outcome.Success(cafePreview(target = null)),
            removals.preview(cafe, Disposal.DeleteAll)
        )
        assertEquals(
            Outcome.Success(cafePreview(comidaTarget)),
            removals.preview(cafe, Disposal.MoveTo(comida))
        )
        assertEquals(
            listOf(
                Outcome.Failure(RemovalPreviewFailure.CategoryMissing),
                Outcome.Failure(RemovalPreviewFailure.TargetMissing),
                Outcome.Failure(RemovalPreviewFailure.SameCategory)
            ),
            listOf(
                removals.preview(ledger.categoryId(999), Disposal.DeleteAll),
                removals.preview(cafe, Disposal.MoveTo(ledger.categoryId(999))),
                removals.preview(cafe, Disposal.MoveTo(cafe))
            )
        )

        // October's own limit wins over the default.
        committed(
            ledger.categories.submit(
                SetMonthLimit(ledger.operation(), 1, cafe, versionOf(100), october, finite(45_000))
            )
        )
        assertEquals(
            Outcome.Success(cafePreview(comidaTarget).copy(limitThisMonth = finite(45_000))),
            removals.preview(cafe, Disposal.MoveTo(comida))
        )

        // Ocio archived: it takes nothing, and in November its limit is paused.
        committed(ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ocio, 1)))
        assertEquals(
            Outcome.Failure(RemovalPreviewFailure.TargetArchived),
            removals.preview(cafe, Disposal.MoveTo(ocio))
        )
        ledger.goTo("2026-11-03")
        assertEquals(
            Outcome.Success(
                RemovalPreview(
                    categoryId = ocio,
                    expenses = 1,
                    total = cop(12_000),
                    firstMonth = october,
                    lastMonth = october,
                    schedules = 0,
                    month = YearMonth.of(2026, 11),
                    spentThisMonth = cop(0),
                    limitThisMonth = null,
                    target = null
                )
            ),
            removals.preview(ocio, Disposal.DeleteAll)
        )
    }

    // ---- Deleting ----

    @Test
    fun anEmptyCategoryIsDeletedWithItsLimitsAndNothingElse() = runBlocking {
        committed(ledger.categories.submit(ledger.createCategory("Café", finite(50_000))))
        committed(ledger.categories.submit(ledger.createCategory("Comida", finite(200_000))))
        committed(
            ledger.categories.submit(
                SetMonthLimit(ledger.operation(), 1, cafe, 1, YearMonth.of(2026, 9), finite(1))
            )
        )
        ledger.audit = later
        val empty =
            RemovalPreview(cafe, 0, cop(0), null, null, 0, october, cop(0), finite(50_000), null)
        assertEquals(Outcome.Success(empty), removals.preview(cafe, Disposal.DeleteAll))
        assertTrue(empty.isEmpty)
        val command = deleteCafe(Disposal.DeleteAll, empty)

        val outcome = ledger.categories.submit(command)

        assertEquals(Committed(deletion(command.operationId, 4), false), outcome)
        assertEquals(listOf("${uuid(101)}|Comida|0|1"), categoryRows())
        assertEquals(listOf("${uuid(101)}|2026-10|finite|200000"), defaultRows())
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(
            listOf("${command.operationId.value}|category.delete|4|${uuid(100)}"),
            ledger.rows("operation_receipt", "operation_id", "kind", "revision", "subject_id")
                .filter { it.contains("category.delete") }
        )
        assertEquals(4, ledger.revision())
        assertEquals(
            Outcome.Success(deletion(command.operationId, 4)),
            ledger.categories.find(command.operationId)
        )
        assertEquals(
            Outcome.Failure(RemovalPreviewFailure.CategoryMissing),
            removals.preview(cafe, Disposal.DeleteAll)
        )
    }

    @Test
    fun deletingAllRemovesWhatItHoldsAndNothingElse() = runBlocking {
        history()
        // A kept copy of a deleted Café expense, a pause, carry and a month limit.
        spend(100, "2026-10-06", 9_000)
        val removedExpense = ledger.nextRecord - 1
        val deleteExpense = DeleteExpense(ledger.operation(), 1, expenseIdOf(removedExpense), 1)
        committed(ledger.expenses.delete(deleteExpense))
        committed(
            ledger.schedules.submit(
                PauseSchedule(
                    ledger.operation(),
                    1,
                    ScheduleId.of(uuid(103)).valueOrFail(),
                    ledger.long("SELECT version FROM schedule")
                )
            )
        )
        committed(
            ledger.categories.submit(ledger.carryOn(ledger.operation(), 1, cafe, versionOf(100)))
        )
        committed(ledger.categories.submit(ledger.carryOn(ledger.operation(), 1, ocio, 1)))
        committed(
            ledger.categories.submit(
                SetMonthLimit(
                    ledger.operation(),
                    1,
                    cafe,
                    versionOf(100),
                    august,
                    finite(10_000)
                )
            )
        )
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM deleted_expense"))
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM schedule_pause"))
        val others = { rows: List<String> -> rows.filterNot { it.contains(uuid(100)) } }
        val octoberBefore = ledger.snapshots.read(october).valueOrFail()
        val revision = ledger.revision()
        ledger.audit = later
        val preview = removals.preview(cafe, Disposal.DeleteAll).valueOrFail()
        assertEquals(cafePreview(target = null), preview)
        val command = deleteCafe(Disposal.DeleteAll, preview)
        val kept = listOf(
            categoryRows(),
            others(expenseRows()),
            defaultRows().filterNot { it.startsWith(uuid(100)) },
            ledger.chainRows().filterNot { it.startsWith(uuid(100)) }
        )

        val outcome = ledger.categories.submit(command)

        assertEquals(Committed(deletion(command.operationId, revision + 1), false), outcome)
        assertEquals(
            listOf("${uuid(101)}|Comida|0|1", "${uuid(102)}|Ocio|0|2"),
            categoryRows()
        )
        assertEquals(
            listOf("106|101|2026-10-02|80000|null", "107|102|2026-10-04|12000|null"),
            expenseFacts()
        )
        assertEquals(
            listOf(
                kept[0].filterNot { it.startsWith(uuid(100)) },
                kept[1],
                kept[2],
                kept[3]
            ),
            listOf(categoryRows(), expenseRows(), defaultRows(), ledger.chainRows())
        )
        assertEquals(listOf("${uuid(102)}|2026-10|null"), ledger.chainRows())
        assertEquals(emptyList<String>(), scheduleRows())
        assertEquals(emptyList<String>(), pauseRows())
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(emptyList<String>(), keptRows())
        assertEquals(revision + 1, ledger.revision())
        // October loses Café's 15.000 + 25.000: 132.000 − 40.000 = 92.000; Comida and
        // Ocio keep 80.000 and 12.000.
        val octoberAfter = ledger.snapshots.read(october).valueOrFail()
        assertEquals(cop(132_000), octoberBefore.spent)
        assertEquals(cop(92_000), octoberAfter.spent)
        assertEquals(
            listOf("Comida 80000", "Ocio 12000"),
            octoberAfter.categories.map { "${it.category.name.value} ${it.spent.minorUnits}" }
                .sorted()
        )
    }

    @Test
    fun movingKeepsEveryExpenseAndPaymentAsItWasAndEveryMonthsTotal() = runBlocking {
        history()
        // A deleted Café expense kept for its undo goes with the others.
        spend(100, "2026-10-06", 9_000)
        val removedExpense = ledger.nextRecord - 1
        val deleteExpense = DeleteExpense(ledger.operation(), 1, expenseIdOf(removedExpense), 1)
        committed(ledger.expenses.delete(deleteExpense))
        val revision = ledger.revision()
        val before = expenseRows()
        val schedulesBefore = scheduleRows()
        val augustBefore = ledger.snapshots.read(august).valueOrFail()
        val octoberBefore = ledger.snapshots.read(october).valueOrFail()
        val comidaVersion = versionOf(101)
        ledger.audit = later
        val preview = removals.preview(cafe, Disposal.MoveTo(comida)).valueOrFail()
        assertEquals(cafePreview(comidaTarget), preview)
        val command = deleteCafe(Disposal.MoveTo(comida), preview)

        val outcome = ledger.categories.submit(command)

        assertEquals(Committed(deletion(command.operationId, revision + 1), false), outcome)
        assertEquals(
            listOf("${uuid(101)}|Comida|0|${comidaVersion + 1}", "${uuid(102)}|Ocio|0|1"),
            categoryRows()
        )
        assertEquals(
            listOf(
                "104|101|2026-08-14|20000|null",
                "105|101|2026-10-03|15000|Tinto",
                "106|101|2026-10-02|80000|null",
                "107|102|2026-10-04|12000|null",
                "108|101|2026-10-05|25000|Suscripción"
            ),
            expenseFacts()
        )
        // Date, sequence, amount, note, origin and creation stay; the category, version
        // and time of the last change are new.
        val cafeRows = setOf(uuid(104), uuid(105), uuid(108))
        assertEquals(
            before.map { row ->
                val columns = row.split("|").toMutableList()
                if (columns[0] in cafeRows) {
                    columns[1] = uuid(101)
                    columns[9] = (columns[9].toLong() + 1).toString()
                    columns[11] = later.toEpochMilli().toString()
                }
                columns.joinToString("|")
            },
            expenseRows()
        )
        assertEquals(
            listOf("${uuid(108)}|${uuid(103)}|2026-10-05|1"),
            ledger.rows("expense", "id", "origin_schedule_id", "origin_date", "origin_slot")
                .filter { !it.endsWith("|null|null|null") }
        )
        // The payment keeps its terms and the occurrence catch-up handled last.
        assertEquals(listOf("${uuid(103)}|${uuid(100)}|25000|2026-10-05|1"), schedulesBefore)
        assertEquals(listOf("${uuid(103)}|${uuid(101)}|25000|2026-10-05|2"), scheduleRows())
        assertEquals(
            listOf("${uuid(101)}|2026-10|finite|200000", "${uuid(102)}|2026-10|finite|30000"),
            defaultRows()
        )
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(listOf("${deleteExpense.operationId.value}|${uuid(101)}"), keptRows())
        // Each month's total is the same: August 20.000, October 15.000 + 25.000 + 80.000 +
        // 12.000 = 132.000; Comida's October is 80.000 + 15.000 + 25.000 = 120.000.
        val augustAfter = ledger.snapshots.read(august).valueOrFail()
        val octoberAfter = ledger.snapshots.read(october).valueOrFail()
        assertEquals(
            listOf(cop(20_000), cop(20_000)),
            listOf(augustBefore.spent, augustAfter.spent)
        )
        assertEquals(
            listOf(cop(132_000), cop(132_000)),
            listOf(octoberBefore.spent, octoberAfter.spent)
        )
        assertEquals(
            listOf("Comida 120000", "Ocio 12000"),
            octoberAfter.categories.map { "${it.category.name.value} ${it.spent.minorUnits}" }
                .sorted()
        )
        // Undoing the earlier deletion puts the expense where its siblings went.
        committed(
            ledger.expenses.restore(
                RestoreExpense(ledger.operation(), 1, deleteExpense.operationId)
            )
        )
        assertEquals(
            listOf("${uuid(removedExpense)}|${uuid(101)}|9000"),
            ledger.rows("expense", "id", "category_id", "amount_minor")
                .filter { it.startsWith(uuid(removedExpense)) }
        )
    }

    // ---- Replays and refusals ----

    @Test
    fun theSameOperationAgainReturnsTheFirstReceiptAndDifferentInputIsAConflict() = runBlocking {
        history()
        ledger.audit = later
        val command = deleteCafe(Disposal.MoveTo(comida), cafePreview(comidaTarget))
        val revision = ledger.revision()
        val first = ledger.categories.submit(command)
        val after = state()

        val replay = ledger.categories.submit(command)
        val conflicts = listOf(
            command.copy(disposal = Disposal.DeleteAll, confirmed = cafePreview(null)),
            command.copy(confirmed = cafePreview(comidaTarget).copy(expenses = 4)),
            command.copy(expectedVersion = command.expectedVersion + 1)
        ).map { ledger.categories.submit(it) }

        val change = deletion(command.operationId, revision + 1)
        assertEquals(Committed(change, replayed = false), first)
        assertEquals(Committed(change, replayed = true), replay)
        assertEquals(List(3) { Rejected(CommandRejection.OperationConflict) }, conflicts)
        assertEquals(after, state())
    }

    @Test
    fun aDoubleTapDeletesOnce() = runBlocking {
        history()
        val command = deleteCafe(Disposal.DeleteAll, cafePreview(null))
        val revision = ledger.revision()
        val go = CompletableDeferred<Unit>()

        val outcomes = List(2) {
            async(Dispatchers.Default) {
                go.await()
                ledger.categories.submit(command)
            }
        }.also { go.complete(Unit) }.awaitAll()

        val change = CommittedCategoryChange(
            OperationReceipt(
                command.operationId,
                OperationKind.DeleteCategory,
                1,
                revision + 1,
                ledger.auditMillis
            ),
            cafe
        )
        assertEquals(
            setOf(Committed(change, replayed = false), Committed(change, replayed = true)),
            outcomes.toSet()
        )
        assertEquals(revision + 1, ledger.revision())
    }

    @Test
    fun aStaleVersionOrAReplacedHistoryIsRefusedWithNothingWritten() = runBlocking {
        history()
        committed(
            ledger.categories.submit(
                ledger.renamed(ledger.operation(), 1, cafe, 1, ledger.name("Cafés"))
            )
        )
        val before = state()

        val stale = ledger.categories.submit(
            deleteCafe(Disposal.DeleteAll, cafePreview(null), version = 1)
        )
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE profile SET generation = 2")
        val replaced = ledger.categories.submit(deleteCafe(Disposal.DeleteAll, cafePreview(null)))

        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), stale)
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 2)), replaced)
        assertEquals(before, state())
    }

    @Test
    fun anExpenseAddedAfterThePreviewRefusesWithTheNewFigures() = runBlocking {
        history()
        val seen = removals.preview(cafe, Disposal.MoveTo(comida)).valueOrFail()
        spend(100, "2026-10-07", 10_000)
        val before = state()

        val outcome = ledger.categories.submit(deleteCafe(Disposal.MoveTo(comida), seen))

        // 4 expenses, 60.000 + 10.000 = 70.000; October 40.000 + 10.000 = 50.000.
        val current = cafePreview(comidaTarget).copy(
            expenses = 4,
            total = cop(70_000),
            spentThisMonth = cop(50_000)
        )
        assertEquals(Rejected(Specific(CategoryRejection.ContentsChanged(current))), outcome)
        assertEquals(before, state())
        committed(ledger.categories.submit(deleteCafe(Disposal.MoveTo(comida), current)))
        assertEquals(
            5,
            ledger.long("SELECT COUNT(*) FROM expense WHERE category_id = '${uuid(101)}'")
        )
    }

    @Test
    fun aNewMonthBetweenThePreviewAndTheConfirmationRefusesWithItsFigures() = runBlocking {
        history()
        val seen = removals.preview(cafe, Disposal.MoveTo(comida)).valueOrFail()
        ledger.goTo("2026-11-03")
        val before = state()

        val outcome = ledger.categories.submit(deleteCafe(Disposal.MoveTo(comida), seen))

        // November: nothing spent yet in either; both defaults carry on.
        val current = cafePreview(TargetPreview(comida, cop(0), finite(200_000))).copy(
            month = YearMonth.of(2026, 11),
            spentThisMonth = cop(0)
        )
        assertEquals(Rejected(Specific(CategoryRejection.ContentsChanged(current))), outcome)
        assertEquals(before, state())
    }

    @Test
    fun aBadTargetOrAMissingCategoryIsRefusedWithNothingWritten() = runBlocking {
        history()
        committed(ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, ocio, 1)))
        val before = state()
        val missing = ledger.categoryId(999)

        val outcomes = listOf(
            deleteCafe(Disposal.MoveTo(ocio), cafePreview(comidaTarget)),
            deleteCafe(Disposal.MoveTo(missing), cafePreview(comidaTarget)),
            deleteCafe(Disposal.MoveTo(cafe), cafePreview(comidaTarget)),
            deleteCafe(Disposal.DeleteAll, cafePreview(null)).copy(categoryId = missing)
        ).map { ledger.categories.submit(it) }

        assertEquals(
            listOf(
                Rejected(Specific(CategoryRejection.TargetArchived)),
                Rejected(Specific(CategoryRejection.TargetMissing)),
                Rejected(Specific(CategoryRejection.SameCategory)),
                Rejected(Specific(CategoryRejection.CategoryMissing))
            ),
            outcomes
        )
        assertEquals(before, state())
    }

    private fun CategoryCommandOutcome.refusedCorrection() =
        ((this as Rejected).reason as Specific).reason as CategoryRejection.CorrectionUnconfirmed

    /**
     * SC01's Comida (100) carries from January 2027; Café (104) spent 30.000 on 15 February
     * (105). Moved into Comida, February's 70.000 becomes 100.000, so March and April have
     * 30.000 less: the list of ADR 0020 CP20, confirmed for this change at this revision.
     */
    @Test
    fun movingIntoACarryingCategoryNeedsItsCorrectionConfirmedAtThatRevision() = runBlocking {
        val histories = CarryHistories(ledger)
        histories.sc01()
        val cafe = histories.category("Café", 50_000, "2027-01-05")
        histories.spend(cafe, "2027-02-15", 30_000)
        ledger.goTo("2027-04-05")
        val id = ledger.categoryId(cafe)
        val april = YearMonth.of(2027, 4)
        val february = YearMonth.of(2027, 2)
        // Café: 1 expense of 30.000 in February; nothing in April of its 50.000. Comida:
        // nothing in April of its 100.000.
        val preview = RemovalPreview(
            id,
            1,
            cop(30_000),
            february,
            february,
            0,
            april,
            cop(0),
            finite(50_000),
            TargetPreview(ledger.categoryId(100), cop(0), finite(100_000))
        )
        assertEquals(
            Outcome.Success(preview),
            removals.preview(id, Disposal.MoveTo(ledger.categoryId(100)))
        )
        val command = { confirmation: CorrectionConfirmation? ->
            DeleteCategory(
                ledger.operation(1_900),
                1,
                id,
                1,
                Disposal.MoveTo(ledger.categoryId(100)),
                preview,
                confirmation
            )
        }
        val revision = ledger.revision()
        val before = state()

        val asked = ledger.categories.submit(command(null)).refusedCorrection()
        val unchanged = state()
        // Another change lands: the list must be seen again at the new revision.
        committed(ledger.categories.submit(ledger.createCategory("Transporte")))
        val again = ledger.categories.submit(command(asked.confirmation)).refusedCorrection()
        val saved = ledger.categories.submit(command(again.confirmation))

        val listed = listOf(
            "100|2027-03|C 210000→180000|R -40000→-70000|Over→Over",
            "100|2027-04|C 60000→30000|R 60000→30000|OnTrack→OnTrack"
        )
        assertEquals(revision, asked.confirmation.revision)
        assertEquals(listed, asked.correction.months.map { it.text() })
        assertEquals(revision + 1, again.confirmation.revision)
        assertEquals(asked.confirmation.change, again.confirmation.change)
        assertEquals(listed, again.correction.months.map { it.text() })
        assertEquals(before, unchanged)
        assertTrue("$saved", saved is Committed)
        assertEquals(
            listOf("${uuid(105)}|${uuid(100)}|30000"),
            ledger.rows("expense", "id", "category_id", "amount_minor")
                .filter { it.startsWith(uuid(105)) }
        )
        assertEquals(listOf("${uuid(100)}|2027-01|null"), ledger.chainRows())
        assertEquals(0, ledger.long("SELECT COUNT(*) FROM category WHERE id = '${uuid(cafe)}'"))
    }

    // ---- Failure, cancellation and restart ----

    @Test
    fun aFailureAtTheLastStepLeavesEveryRowAsItWas() = runBlocking {
        history()
        spend(100, "2026-10-06", 9_000)
        committed(
            ledger.expenses.delete(
                DeleteExpense(ledger.operation(), 1, expenseIdOf(ledger.nextRecord - 1), 1)
            )
        )
        committed(ledger.categories.submit(ledger.carryOn(ledger.operation(), 1, cafe, 1)))
        // The database itself refuses the category's deletion, after everything else moved.
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_category_delete BEFORE DELETE ON category " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = state()
        val commands = listOf(
            deleteCafe(Disposal.MoveTo(comida), cafePreview(comidaTarget)),
            deleteCafe(Disposal.DeleteAll, cafePreview(null))
        )

        val outcomes = commands.map { ledger.categories.submit(it) }

        assertEquals(
            List(2) {
                Rejected(
                    CommandRejection.StorageFailed(
                        StorageFailure.Unavailable("SQLiteConstraintException")
                    )
                )
            },
            outcomes
        )
        assertEquals(before, state())
        for (command in commands) {
            assertEquals(Outcome.Success(null), ledger.categories.find(command.operationId))
        }
    }

    @Test
    fun aCallerCancelledWhileDeletingGetsTheCancellationAndTheDeletionStays() = runBlocking {
        history()
        val command = deleteCafe(Disposal.DeleteAll, cafePreview(null))
        val revision = ledger.revision()
        lateinit var caller: Job
        var returned: CategoryCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.categories.submit(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        val change = deletion(command.operationId, revision + 1, ledger.auditMillis)
        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(Outcome.Success(change), ledger.categories.find(command.operationId))
        assertEquals(Committed(change, replayed = true), ledger.categories.submit(command))
        assertEquals(0, ledger.long("SELECT COUNT(*) FROM category WHERE id = '${uuid(100)}'"))
    }

    @Test
    fun aDeletionAndItsReceiptSurviveReopeningTheFile() = runBlocking {
        history()
        ledger.audit = later
        val command = deleteCafe(Disposal.MoveTo(comida), cafePreview(comidaTarget))
        committed(ledger.categories.submit(command))
        val after = state()
        val revision = ledger.revision()

        ledger.reopen()

        val change = deletion(command.operationId, revision)
        assertEquals(after, state())
        assertEquals(Outcome.Success(change), ledger.categories.find(command.operationId))
        assertEquals(Committed(change, replayed = true), ledger.categories.submit(command))
        assertEquals(
            Outcome.Failure(RemovalPreviewFailure.CategoryMissing),
            removals.preview(cafe, Disposal.DeleteAll)
        )
    }

    /** The portable preferences a backup reads; never written here. */
    private object Preferences : AppearancePreferences, CategoryOrderingPreferences {
        override fun observeAppearance(): Flow<PreferenceRead<AppearancePreference>> = flowOf(
            PreferenceRead.Ready(
                AppearancePreference(ThemePreference.Dark, InkPreference.Violeta)
            )
        )

        override suspend fun setTheme(theme: ThemePreference): PreferenceWrite = error("read only")

        override suspend fun setInk(ink: InkPreference): PreferenceWrite = error("read only")

        override suspend fun restoreInk(ink: InkPreference, tuya: TuyaColour?) = setInk(ink)

        override fun observeCategoryOrdering(): Flow<PreferenceRead<CategoryOrderingPreference>> =
            flowOf(PreferenceRead.Ready(CategoryOrderingPreference()))

        override suspend fun setCategoryOrder(order: CategoryOrder): PreferenceWrite =
            error("read only")

        override suspend fun setCategoryOrderDirection(
            direction: CategoryOrderDirection
        ): PreferenceWrite = error("read only")
    }

    @Test
    fun aBackupAfterEitherDeletionHoldsNoReferenceToTheCategoryAndReadsBack() = runBlocking {
        history()
        committed(ledger.categories.submit(ledger.carryOn(ledger.operation(), 1, cafe, 1)))
        committed(
            ledger.categories.submit(deleteCafe(Disposal.MoveTo(comida), cafePreview(comidaTarget)))
        )
        // Then Ocio and its one expense go too.
        val ocioPreview = removals.preview(ocio, Disposal.DeleteAll).valueOrFail()
        committed(
            ledger.categories.submit(
                DeleteCategory(ledger.operation(), 1, ocio, 1, Disposal.DeleteAll, ocioPreview)
            )
        )
        val export = BackupExport(
            RoomBackupSnapshots(ledger.database),
            Preferences,
            Preferences,
            HandInvestmentPreferences(),
            HandSmallExpensePreferences(),
            HandGuidancePreferences(),
            { Instant.parse("2026-10-22T19:30:00Z") },
            ledger.clock,
            realDispatchers
        )

        val file = export.create().valueOrFail()
        val plan = BackupCodec().read(file.bytes).valueOrFail()

        // Comida alone, with its default, Café's three expenses and its own, and the payment;
        // the expenses by date: 14 Aug, 2, 3 and 5 Oct.
        assertEquals(
            BackupCounts(
                categories = 1,
                archivedCategories = 0,
                defaultLimits = 1,
                monthLimits = 0,
                expenses = 4,
                incomes = 0,
                goals = 0,
                goalEvents = 0,
                schedules = 1,
                investments = 0,
                investmentMovements = 0,
                usdRates = 0,
                carryChains = 0
            ),
            plan.counts
        )
        assertEquals(
            listOf(cop(20_000), cop(80_000), cop(15_000), cop(25_000)),
            plan.contents.financial.expenses.map { it.amount.money }
        )
        assertTrue(plan.contents.financial.expenses.all { it.categoryId == comida })
    }
}
