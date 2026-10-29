package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryCommand
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.ChangeCategoryIcon
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.RenameCategory
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Category and limit commands against a real database file. Each case reads the
 * stored rows, receipts and revision itself. The financial day starts on 9 October 2026.
 */
@RunWith(AndroidJUnit4::class)
class CategoryCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val october = YearMonth.of(2026, 10)
    private val millis get() = ledger.auditMillis.toEpochMilli()

    private fun categoryRows() =
        ledger.rows("category", "id", "name", "name_key", "icon", "archived", "version")

    private fun defaultRows() =
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor")

    private fun monthRows() =
        ledger.rows("month_limit", "category_id", "month", "kind", "limit_minor")

    private fun receipts() = ledger.long("SELECT COUNT(*) FROM operation_receipt")

    /** Creates "Mercado" with 100.000: category 100, version 1, revision 1. */
    private suspend fun mercado() {
        ledger.categories.submit(ledger.createCategory("Mercado"))
    }

    private fun rename(version: Long, name: String, category: Int = 100) = RenameCategory(
        ledger.operation(),
        1,
        ledger.categoryId(category),
        version,
        ledger.name(name)
    )

    private fun archive(version: Long, category: Int = 100) =
        ArchiveCategory(ledger.operation(), 1, ledger.categoryId(category), version)

    private fun unarchive(version: Long, category: Int = 100) =
        UnarchiveCategory(ledger.operation(), 1, ledger.categoryId(category), version)

    private fun everyEdit(version: Long, category: Int = 100): List<CategoryCommand> {
        val id = ledger.categoryId(category)
        return listOf(
            RenameCategory(ledger.operation(), 1, id, version, ledger.name("Otro nombre")),
            ChangeCategoryIcon(ledger.operation(), 1, id, version, ledger.icon("home")),
            ArchiveCategory(ledger.operation(), 1, id, version),
            UnarchiveCategory(ledger.operation(), 1, id, version),
            SetDefaultLimit(ledger.operation(), 1, id, version, finite(50_000)),
            SetMonthLimit(ledger.operation(), 1, id, version, october, finite(50_000))
        )
    }

    @Test
    fun aNewCategoryIsStoredWithItsLimitReceiptAndOneRevision() = runBlocking {
        val operation = ledger.operation()

        val outcome = ledger.categories.submit(
            ledger.createCategory(" Café ", finite(100_000), operation, icon = "local_cafe")
        )

        assertEquals(
            Committed(ledger.change(operation, OperationKind.CreateCategory, 100, 1), false),
            outcome
        )
        assertEquals(listOf("${uuid(100)}|Café|cafe|local_cafe|0|1"), categoryRows())
        assertEquals(
            listOf("$millis|$millis"),
            ledger.rows("category", "created_at", "updated_at")
        )
        // The limit is October's and the default from October on; no other month has a row.
        assertEquals(listOf("${uuid(100)}|2026-10|finite|100000"), defaultRows())
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(
            listOf("${operation.value}|category.create|1|1|${uuid(100)}|$millis"),
            ledger.rows(
                "operation_receipt",
                "operation_id",
                "kind",
                "generation",
                "revision",
                "subject_id",
                "committed_at"
            )
        )
        assertEquals(1, ledger.revision())
        assertEquals(
            Outcome.Success(ledger.change(operation, OperationKind.CreateCategory, 100, 1)),
            ledger.categories.find(operation)
        )
    }

    @Test
    fun aNameAlreadyHeldIgnoringCaseAndAccentsIsRefusedEvenWhenItsHolderIsArchived() = runBlocking {
        ledger.categories.submit(ledger.createCategory(" Café "))

        val sameName = ledger.categories.submit(ledger.createCategory("cafe"))
        ledger.categories.submit(archive(version = 1))
        val afterArchive = ledger.categories.submit(ledger.createCategory("CAFE"))

        assertEquals(Rejected(Specific(CategoryRejection.NameTaken)), sameName)
        assertEquals(Rejected(Specific(CategoryRejection.NameTaken)), afterArchive)
        assertEquals(listOf("${uuid(100)}|Café|cafe|shopping_cart|1|2"), categoryRows())
        assertEquals(2, ledger.revision())
        assertEquals(2, receipts())
    }

    @Test
    fun renamingKeepsTheIdentityExpensesAndLimits() = runBlocking {
        mercado()
        ledger.categories.submit(ledger.createCategory("Transporte"))
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 10_000))
        val limitsBefore = defaultRows()

        val renamed = ledger.categories.submit(rename(1, "Plaza de mercado"))
        val ownNameRecased = ledger.categories.submit(rename(2, "PLAZA DE MERCADO"))
        val taken = ledger.categories.submit(rename(3, "transporte"))

        assertTrue(renamed is Committed && ownNameRecased is Committed)
        assertEquals(Rejected(Specific(CategoryRejection.NameTaken)), taken)
        assertEquals(
            listOf(
                "${uuid(100)}|PLAZA DE MERCADO|plaza de mercado|shopping_cart|0|3",
                "${uuid(101)}|Transporte|transporte|shopping_cart|0|1"
            ),
            categoryRows()
        )
        assertEquals(
            listOf("${uuid(100)}|2026-10-03|10000"),
            ledger.rows("expense", "category_id", "effective_date", "amount_minor")
        )
        assertEquals(limitsBefore, defaultRows())
        assertEquals(5, ledger.revision())
    }

    @Test
    fun changingTheIconChangesOnlyTheIconAndVersion() = runBlocking {
        mercado()
        val operation = ledger.operation()

        val outcome = ledger.categories.submit(
            ChangeCategoryIcon(operation, 1, ledger.categoryId(100), 1, ledger.icon("storefront"))
        )

        assertEquals(
            Committed(ledger.change(operation, OperationKind.ChangeCategoryIcon, 100, 2), false),
            outcome
        )
        assertEquals(listOf("${uuid(100)}|Mercado|mercado|storefront|0|2"), categoryRows())
    }

    @Test
    fun anEditAgainstAnOlderVersionIsAConflictForEveryKindOfEdit() = runBlocking {
        mercado()
        ledger.categories.submit(rename(1, "Plaza"))
        val rows = categoryRows() to defaultRows()

        for (stale in everyEdit(version = 1)) {
            assertEquals(
                "$stale",
                Rejected(CommandRejection.VersionConflict(current = 2)),
                ledger.categories.submit(stale)
            )
        }

        assertEquals(rows, categoryRows() to defaultRows())
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(2, ledger.revision())
        assertEquals(2, receipts())
    }

    @Test
    fun anEditOfACategoryThatDoesNotExistIsRejectedForEveryKindOfEdit() = runBlocking {
        mercado()

        for (missing in everyEdit(version = 1, category = 999)) {
            assertEquals(
                "$missing",
                Rejected(Specific(CategoryRejection.CategoryMissing)),
                ledger.categories.submit(missing)
            )
        }

        assertEquals(1, ledger.revision())
        assertEquals(1, receipts())
    }

    @Test
    fun archivingStopsNewExpensesAndKeepsEverythingAlreadyRecorded() = runBlocking {
        mercado()
        ledger.expenses.create(ledger.createExpense(100, "2026-10-03", 20_000))
        val archiveOperation = archive(version = 1)

        val archived = ledger.categories.submit(archiveOperation)
        val newExpense = ledger.expenses.create(ledger.createExpense(100, "2026-10-05", 5_000))
        val replay = ledger.categories.submit(archiveOperation)
        val again = ledger.categories.submit(archive(version = 2))
        val staleEditor = ledger.categories.submit(archive(version = 1))

        val change = ledger.change(
            archiveOperation.operationId,
            OperationKind.ArchiveCategory,
            100,
            revision = 3
        )
        assertEquals(Committed(change, replayed = false), archived)
        assertEquals(
            ExpenseCommandOutcome.Rejected(ExpenseRejection.CategoryArchived),
            newExpense
        )
        assertEquals(Committed(change, replayed = true), replay)
        assertEquals(Rejected(Specific(CategoryRejection.AlreadyArchived)), again)
        assertEquals(Rejected(CommandRejection.VersionConflict(current = 2)), staleEditor)
        assertEquals(listOf("${uuid(100)}|Mercado|mercado|shopping_cart|1|2"), categoryRows())
        assertEquals(
            listOf("${uuid(100)}|2026-10-03|20000"),
            ledger.rows("expense", "category_id", "effective_date", "amount_minor")
        )
        // October keeps its 100.000; the pause starts in November.
        assertEquals(
            listOf("${uuid(100)}|2026-10|finite|100000", "${uuid(100)}|2026-11|paused|null"),
            defaultRows()
        )
        assertEquals(3, ledger.revision())
    }

    @Test
    fun whatAnArchivedCategoryStillAcceptsAndWhatItRefuses() = runBlocking {
        mercado()
        val id = ledger.categoryId(100)

        val notArchived = ledger.categories.submit(unarchive(version = 1))
        ledger.categories.submit(archive(version = 1))
        val newDefault = ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, id, 2, finite(50_000))
        )
        val renamed = ledger.categories.submit(rename(2, "Plaza"))
        val correction = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 3, october, finite(90_000))
        )

        assertEquals(Rejected(Specific(CategoryRejection.NotArchived)), notArchived)
        assertEquals(Rejected(Specific(CategoryRejection.CategoryArchived)), newDefault)
        assertTrue(renamed is Committed && correction is Committed)
        assertEquals(listOf("${uuid(100)}|Plaza|plaza|shopping_cart|1|4"), categoryRows())
        assertEquals(listOf("${uuid(100)}|2026-10|finite|90000"), monthRows())
    }

    @Test
    fun aLimitCannotBeSetForAMonthTheStoredTextCannotHold() = runBlocking {
        mercado()
        val id = ledger.categoryId(100)
        val receiptsBefore = receipts()
        val revisionBefore = ledger.revision()

        // Year -1 would be stored as "-0001-01" and make the category's limits unreadable.
        val before = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 1, YearMonth.of(-1, 1), finite(1))
        )
        val oldest = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 1, YearMonth.of(0, 1), finite(7))
        )

        assertEquals(Rejected(Specific(CategoryRejection.InvalidMonth)), before)
        assertTrue("$oldest", oldest is Committed)
        assertEquals(listOf("${uuid(100)}|0000-01|finite|7"), monthRows())
        assertEquals(receiptsBefore + 1, receipts())
        assertEquals(revisionBefore + 1, ledger.revision())
    }

    private fun raw(sql: String) = ledger.database.openHelper.writableDatabase.execSQL(sql)

    @Test
    fun aVersionThatCannotAdvanceRefusesTheChangeAndWritesNothing() = runBlocking {
        mercado()
        raw("UPDATE category SET version = ${Long.MAX_VALUE - 1}")

        val last = ledger.categories.submit(rename(Long.MAX_VALUE - 1, "Plaza"))
        val beyond = ledger.categories.submit(rename(Long.MAX_VALUE, "Galería"))

        assertTrue("$last", last is Committed)
        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("category", uuid(100), "version")
                )
            ),
            beyond
        )
        assertEquals(
            listOf("${uuid(100)}|Plaza|plaza|shopping_cart|0|${Long.MAX_VALUE}"),
            categoryRows()
        )
        // Created and renamed once: two receipts, revision 2, and no third of either.
        assertEquals(2, receipts())
        assertEquals(2, ledger.revision())
    }

    @Test
    fun aRevisionThatCannotAdvanceRefusesANewCategoryAndWritesNothing() = runBlocking {
        ledger.revision()
        raw("UPDATE profile SET revision = ${Long.MAX_VALUE}")

        val outcome = ledger.categories.submit(ledger.createCategory("Mercado"))

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("profile", null, "revision")
                )
            ),
            outcome
        )
        assertEquals(emptyList<String>(), categoryRows())
        assertEquals(0, receipts())
        assertEquals(0, ledger.long("SELECT COUNT(*) FROM default_limit"))
        assertEquals(Long.MAX_VALUE, ledger.revision())
    }

    @Test
    fun aLimitCannotBeSetForAMonthThatHasNotStarted() = runBlocking {
        mercado()
        val id = ledger.categoryId(100)

        val future = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 1, YearMonth.of(2026, 11), finite(1))
        )
        val current = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 1, october, finite(0))
        )
        val past = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                id,
                2,
                YearMonth.of(2025, 2),
                BudgetLimit.Unlimited
            )
        )

        assertEquals(
            Rejected(Specific(CategoryRejection.FutureMonth(currentMonth = october))),
            future
        )
        assertTrue(current is Committed && past is Committed)
        assertEquals(
            listOf("${uuid(100)}|2025-02|unlimited|null", "${uuid(100)}|2026-10|finite|0"),
            monthRows()
        )
        assertEquals(listOf("${uuid(100)}|Mercado|mercado|shopping_cart|0|3"), categoryRows())
    }

    @Test
    fun theSameOperationAgainReturnsTheOriginalAndDifferentInputIsAConflict() = runBlocking {
        val create = ledger.createCategory("Mercado", finite(0))
        ledger.categories.submit(create)
        val id = ledger.categoryId(100)
        val setLimit = SetDefaultLimit(ledger.operation(), 1, id, 1, finite(50_000))
        ledger.categories.submit(setLimit)
        val expense = ledger.createExpense(100, "2026-10-03", 10_000)
        ledger.expenses.create(expense)
        val before = Triple(categoryRows(), defaultRows(), ledger.revision())

        val replays = listOf(create, setLimit).map { ledger.categories.submit(it) }
        val conflicts = listOf(
            // Zero is not unlimited, so this is different input.
            create.copy(limit = BudgetLimit.Unlimited),
            create.copy(name = ledger.name("Mercados")),
            create.copy(icon = ledger.icon("home")),
            setLimit.copy(limit = finite(50_001)),
            setLimit.copy(expectedVersion = 2),
            setLimit.copy(categoryId = ledger.categoryId(101)),
            // The same identifier used for a different kind of change.
            ArchiveCategory(setLimit.operationId, 1, id, 1),
            ArchiveCategory(expense.operationId, 1, id, 2)
        ).map { ledger.categories.submit(it) }
        val expenseUnderACategoryOperation =
            ledger.expenses.create(expense.copy(operationId = create.operationId))

        assertEquals(
            listOf(
                Committed(
                    ledger.change(create.operationId, OperationKind.CreateCategory, 100, 1),
                    replayed = true
                ),
                Committed(
                    ledger.change(setLimit.operationId, OperationKind.SetDefaultLimit, 100, 2),
                    replayed = true
                )
            ),
            replays
        )
        assertEquals(List(8) { Rejected(CommandRejection.OperationConflict) }, conflicts)
        assertEquals(
            ExpenseCommandOutcome.Rejected(ExpenseRejection.OperationConflict),
            expenseUnderACategoryOperation
        )
        assertEquals(before, Triple(categoryRows(), defaultRows(), ledger.revision()))
        // An expense's receipt is not a category change, and the reverse.
        assertEquals(Outcome.Success(null), ledger.categories.find(expense.operationId))
        assertEquals(Outcome.Success(null), ledger.expenses.find(create.operationId))
    }

    @Test
    fun aCommandPreparedAgainstAReplacedHistoryIsRejected() = runBlocking {
        val create = ledger.createCategory("Mercado")
        ledger.categories.submit(create)
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE profile SET generation = 2")

        val outcomes = (everyEdit(version = 1) + create + ledger.createCategory("Otra"))
            .map { ledger.categories.submit(it) }

        assertEquals(
            List(8) { Rejected(CommandRejection.StaleGeneration(current = 2)) },
            outcomes
        )
        assertEquals(1, ledger.revision())
        assertEquals(1, receipts())
    }

    @Test
    fun twoEditorsOfTheSameVersionAtOnceOneWinsAndTheOtherConflicts() = runBlocking {
        mercado()
        val go = CompletableDeferred<Unit>()

        val outcomes = List(16) { index ->
            val command = rename(1, "Nombre $index")
            async(Dispatchers.Default) {
                go.await()
                ledger.categories.submit(command)
            }
        }.also { go.complete(Unit) }.awaitAll()

        assertEquals(1, outcomes.count { it is Committed })
        assertEquals(
            List(15) { Rejected(CommandRejection.VersionConflict(current = 2)) },
            outcomes.filter { it !is Committed }
        )
        assertEquals(2, ledger.long("SELECT version FROM category"))
        assertEquals(2, ledger.revision())
        assertEquals(2, receipts())
    }

    @Test
    fun manyCreationsOfTheSameNameAtOnceCreateOneCategory() = runBlocking {
        val go = CompletableDeferred<Unit>()

        val outcomes = List(16) {
            val command = ledger.createCategory("Mercado")
            async(Dispatchers.Default) {
                go.await()
                ledger.categories.submit(command)
            }
        }.also { go.complete(Unit) }.awaitAll()

        assertEquals(1, outcomes.count { it is Committed })
        assertEquals(15, outcomes.count { it == Rejected(Specific(CategoryRejection.NameTaken)) })
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM category"))
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM default_limit"))
        assertEquals(1, ledger.revision())
    }

    @Test
    fun aFailureAfterTheLimitWasWrittenLeavesNoLimitVersionRevisionOrReceipt() = runBlocking {
        mercado()
        // The database itself refuses the category update, after the limit row was written.
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_category_update BEFORE UPDATE ON category " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val command = SetMonthLimit(
            ledger.operation(),
            1,
            ledger.categoryId(100),
            1,
            october,
            finite(90_000)
        )

        val outcome = ledger.categories.submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertEquals(emptyList<String>(), monthRows())
        assertEquals(listOf("${uuid(100)}|Mercado|mercado|shopping_cart|0|1"), categoryRows())
        assertEquals(1, ledger.revision())
        assertEquals(1, receipts())
        assertEquals(Outcome.Success(null), ledger.categories.find(command.operationId))
    }

    @Test
    fun aCallerCancelledWhileCreatingGetsTheCancellationAndCanRecoverTheCategory() = runBlocking {
        val create = ledger.createCategory("Mercado")
        lateinit var caller: Job
        var returned: CategoryCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAnIdIsTaken = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.categories.submit(create)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAnIdIsTaken = {}

        val change = ledger.change(create.operationId, OperationKind.CreateCategory, 100, 1)
        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(Outcome.Success(change), ledger.categories.find(create.operationId))
        assertEquals(Committed(change, replayed = true), ledger.categories.submit(create))
        assertEquals(1, ledger.long("SELECT COUNT(*) FROM category"))
        assertEquals(1, ledger.revision())
    }

    @Test
    fun receiptsAndReplaySurviveClosingAndReopeningTheFile() = runBlocking {
        val create = ledger.createCategory("Mercado")
        ledger.categories.submit(create)
        val archiveOperation = archive(version = 1)
        ledger.categories.submit(archiveOperation)

        ledger.reopen()

        val archived = ledger.change(
            archiveOperation.operationId,
            OperationKind.ArchiveCategory,
            100,
            revision = 2
        )
        assertEquals(
            Outcome.Success(archived),
            ledger.categories.find(archiveOperation.operationId)
        )
        assertEquals(
            Committed(archived, replayed = true),
            ledger.categories.submit(archiveOperation)
        )
        assertEquals(listOf("${uuid(100)}|Mercado|mercado|shopping_cart|1|2"), categoryRows())
        assertEquals(2, ledger.revision())
    }

    @Test
    fun aDamagedCategoryRowRejectsTheEditWithoutWriting() = runBlocking {
        mercado()
        ledger.database.openHelper.writableDatabase.execSQL("UPDATE category SET name_key = 'x'")

        val outcome = ledger.categories.submit(rename(1, "Plaza"))

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("category", uuid(100), "name_key")
                )
            ),
            outcome
        )
        assertEquals(1, ledger.revision())
        assertEquals(1, receipts())
    }
}
