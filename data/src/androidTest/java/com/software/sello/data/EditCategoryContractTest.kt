package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CarrySetting
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.EditCategory
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.LimitEdit
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The category form's one save (SELLO-188) against a real database file: name, icon,
 * limit and carry are stored together under one receipt, or nothing is. Each case reads
 * the stored rows itself. Amounts are worked out by hand in each test: `C = B + K`,
 * `R = C − S`, and a closed month's `R` is the next month's `K` (ADR 0020).
 */
@RunWith(AndroidJUnit4::class)
class EditCategoryContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val histories by lazy { CarryHistories(ledger) }

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun month(text: String) = YearMonth.parse(text)

    private fun defaultRows() =
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor")

    /** Everything a refused save must leave as it was. */
    private fun state() = listOf(
        ledger.chainRows(),
        defaultRows(),
        ledger.rows("month_limit", "category_id", "month", "kind", "limit_minor"),
        ledger.rows("category", "id", "name", "icon", "archived", "version"),
        listOf(
            "revision ${ledger.revision()}",
            "receipts ${ledger.long("SELECT COUNT(*) FROM operation_receipt")}"
        )
    )

    private fun edit(
        category: Int,
        version: Long,
        month: String,
        name: String? = null,
        limit: LimitEdit? = null,
        carry: CarrySetting? = null,
        confirmed: Long? = null,
        generation: Long = 1
    ) = EditCategory(
        operationId = ledger.operation(),
        generation = generation,
        categoryId = ledger.categoryId(category),
        expectedVersion = version,
        month = month(month),
        name = name?.let(ledger::name),
        limit = limit,
        carry = carry,
        confirmedCarryIn = confirmed?.let(::cop)
    )

    private fun fromThisMonth(pesos: Long) = LimitEdit.FromThisMonth(finite(pesos))

    private suspend fun february(category: Int) =
        ledger.snapshots.read(month("2027-02")).valueOrFail().categories
            .single { it.category.id == ledger.categoryId(category) }.budget!!

    /**
     * Comida, base 100.000, carrying from ene 2027: ene spends 120.000 (R −20.000), so feb
     * receives K −20.000; feb spends 30.000. Today is 10 feb; Comida is at version 2 and
     * the revision is 4 (create, turn on, two expenses).
     */
    private suspend fun deficitInFebruary(): Int {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        histories.spend(comida, "2027-01-10", 120_000)
        histories.spend(comida, "2027-02-10", 30_000)
        return comida
    }

    @Test
    fun oneSaveStoresTheNameTheLimitAndCarryOnUnderOneReceipt() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        val command = edit(
            comida,
            version = 1,
            month = "2027-01",
            name = "Mercado",
            limit = fromThisMonth(150_000),
            carry = CarrySetting.On
        )

        val outcome = ledger.categories.submit(command)

        val change = ledger.change(command.operationId, OperationKind.EditCategory, comida, 2)
        assertEquals(Committed(change, replayed = false), outcome)
        assertEquals(listOf("${uuid(comida)}|2027-01|null"), ledger.chainRows())
        assertEquals(listOf("${uuid(comida)}|2027-01|finite|150000"), defaultRows())
        assertEquals(
            listOf("${uuid(comida)}|Mercado|shopping_cart|0|2"),
            ledger.rows("category", "id", "name", "icon", "archived", "version")
        )
        // One receipt for the whole save and one revision: creating was revision 1.
        assertEquals(
            listOf("${command.operationId.value}|category.edit|1|2|${uuid(comida)}"),
            ledger.rows(
                "operation_receipt",
                "operation_id",
                "kind",
                "generation",
                "revision",
                "subject_id"
            ).filter { it.startsWith(command.operationId.value) }
        )
        assertEquals(2, ledger.revision())
        assertEquals(Outcome.Success(change), ledger.categories.find(command.operationId))
    }

    @Test
    fun turningCarryOnIsJudgedOnTheLimitTheSameSaveSets() = runBlocking {
        ledger.goTo("2027-01-05")
        val ocio = ledger.nextRecord
        val created = ledger.categories.submit(
            ledger.createCategory("Ocio", BudgetLimit.Unlimited)
        )
        assertTrue("$created", created is Committed)
        val id = ledger.categoryId(ocio)
        val before = state()

        // On its own, carry cannot start on an unlimited month (CP3).
        val alone = ledger.categories.submit(EnableCarry(ledger.operation(), 1, id, 1))
        val withUnlimited = ledger.categories.submit(
            edit(
                ocio,
                1,
                "2027-01",
                limit = LimitEdit.FromThisMonth(BudgetLimit.Unlimited),
                carry = CarrySetting.On
            )
        )
        val aboveTheCap = ledger.categories.submit(
            edit(
                ocio,
                1,
                "2027-01",
                limit = fromThisMonth(1_000_000_000_000),
                carry = CarrySetting.On
            )
        )
        val unchanged = state()
        val together = ledger.categories.submit(
            edit(ocio, 1, "2027-01", limit = fromThisMonth(80_000), carry = CarrySetting.On)
        )

        val unlimited = Rejected(
            Specific(CategoryRejection.CarryRefused(CarryEnableRefusal.UnlimitedThisMonth))
        )
        assertEquals(unlimited, alone)
        assertEquals(unlimited, withUnlimited)
        assertEquals(
            Rejected(
                Specific(
                    CategoryRejection.CarryRefused(
                        CarryEnableRefusal.BaseAboveLimit(cop(1_000_000_000_000))
                    )
                )
            ),
            aboveTheCap
        )
        assertEquals(before, unchanged)
        assertTrue("$together", together is Committed)
        assertEquals(listOf("${uuid(ocio)}|2027-01|null"), ledger.chainRows())
        assertEquals(listOf("${uuid(ocio)}|2027-01|finite|80000"), defaultRows())
    }

    @Test
    fun aDropNotConfirmedForTheRealBalanceWritesNoPartOfTheSave() = runBlocking {
        val comida = deficitInFebruary()
        val before = state()

        val parts = { confirmed: Long? ->
            edit(
                comida,
                version = 2,
                month = "2027-02",
                name = "Mercado",
                limit = fromThisMonth(150_000),
                carry = CarrySetting.Off,
                confirmed = confirmed
            )
        }
        val unconfirmed = ledger.categories.submit(parts(null))
        val wrongBalance = ledger.categories.submit(parts(-10_000))
        val unchanged = state()
        val command = parts(-20_000)
        val confirmed = ledger.categories.submit(command)

        // feb: B 100.000, K −20.000, C 80.000, S 30.000, R 50.000; dropped: C 100.000, R 70.000.
        val preview = Rejected(
            Specific(
                CategoryRejection.DropUnconfirmed(
                    DropPreview(
                        month("2027-02"),
                        carryIn = cop(-20_000),
                        base = cop(100_000),
                        capacityBefore = cop(80_000),
                        capacityAfter = cop(100_000),
                        remainingBefore = cop(50_000),
                        remainingAfter = cop(70_000)
                    )
                )
            )
        )
        assertEquals(preview, unconfirmed)
        assertEquals(preview, wrongBalance)
        // The name and the limit were checked first and still were not written.
        assertEquals(before, unchanged)
        assertEquals(
            Committed(
                ledger.change(command.operationId, OperationKind.EditCategory, comida, 5),
                false
            ),
            confirmed
        )
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertEquals(
            listOf(
                "${uuid(comida)}|2027-01|finite|100000",
                "${uuid(comida)}|2027-02|finite|150000"
            ),
            defaultRows()
        )
        // feb is base only now: C 150.000, S 30.000, R 120.000.
        val february = february(comida)
        assertEquals(listOf(cop(150_000), cop(120_000)), listOf(february.limit, february.remaining))
        assertNull(february.carry)
        assertEquals(3, ledger.long("SELECT version FROM category WHERE id = '${uuid(comida)}'"))
    }

    @Test
    fun turningOffAndUnlimitedTogetherDropTheBalanceOnce() = runBlocking {
        val comida = deficitInFebruary()
        val command = edit(
            comida,
            version = 2,
            month = "2027-02",
            limit = LimitEdit.FromThisMonth(BudgetLimit.Unlimited),
            carry = CarrySetting.Off,
            confirmed = -20_000
        )

        val outcome = ledger.categories.submit(command)

        assertTrue("$outcome", outcome is Committed)
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertEquals(
            listOf(
                "${uuid(comida)}|2027-01|finite|100000",
                "${uuid(comida)}|2027-02|unlimited|null"
            ),
            defaultRows()
        )
        assertEquals(5, ledger.revision())
    }

    @Test
    fun aLimitAboveTheChainCapWritesNoPartOfTheSave() = runBlocking {
        val comida = deficitInFebruary()
        val before = state()

        val outcome = ledger.categories.submit(
            edit(comida, 2, "2027-02", name = "Mercado", limit = fromThisMonth(1_000_000_000_000))
        )

        assertEquals(
            Rejected(Specific(CategoryRejection.ChainBaseAboveLimit(cop(1_000_000_000_000)))),
            outcome
        )
        assertEquals(before, state())
    }

    @Test
    fun theSameSaveAgainReturnsTheOriginalEvenAfterReopening() = runBlocking {
        val comida = deficitInFebruary()
        val command = edit(
            comida,
            version = 2,
            month = "2027-02",
            name = "Mercado",
            carry = CarrySetting.Off,
            confirmed = -20_000
        )
        val first = ledger.categories.submit(command)
        val stored = state()

        val again = ledger.categories.submit(command)
        val conflict = ledger.categories.submit(command.copy(name = ledger.name("Otra")))
        ledger.reopen()
        val afterReopening = ledger.categories.submit(command)

        val change = ledger.change(command.operationId, OperationKind.EditCategory, comida, 5)
        assertEquals(Committed(change, false), first)
        assertEquals(Committed(change, true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), conflict)
        assertEquals(Committed(change, true), afterReopening)
        assertEquals(stored, state())
    }

    @Test
    fun anOldVersionHistoryOrMonthAndAnEditThatIsNotOneAreRefusedWithNothingWritten() =
        runBlocking {
            val comida = histories.category("Comida", 100_000, "2027-01-05")
            val before = state()
            val past = LimitEdit.OfMonth(month("2026-12"), finite(50_000))

            val refused = listOf(
                edit(comida, 7, "2027-01", name = "Mercado"),
                edit(comida, 1, "2027-01", name = "Mercado", generation = 2),
                edit(comida, 1, "2027-02", carry = CarrySetting.On),
                edit(comida, 1, "2026-12", carry = CarrySetting.On),
                edit(comida, 1, "2027-01"),
                edit(comida, 1, "2027-01", limit = past, carry = CarrySetting.On),
                edit(comida, 1, "2027-01", carry = CarrySetting.Off)
            ).map { ledger.categories.submit(it) }

            assertEquals(
                listOf(
                    Rejected(CommandRejection.VersionConflict(current = 1)),
                    Rejected(CommandRejection.StaleGeneration(current = 1)),
                    Rejected(Specific(CategoryRejection.MonthMoved(month("2027-01")))),
                    Rejected(Specific(CategoryRejection.MonthMoved(month("2027-01")))),
                    Rejected(Specific(CategoryRejection.NotOneEdit)),
                    Rejected(Specific(CategoryRejection.NotOneEdit)),
                    Rejected(Specific(CategoryRejection.NotCarrying))
                ),
                refused
            )
            assertEquals(before, state())
        }

    @Test
    fun aFailureAfterTheLimitAndChainWereWrittenRollsEveryPartBack() = runBlocking {
        val comida = deficitInFebruary()
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_category_update BEFORE UPDATE ON category " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val command = edit(
            comida,
            version = 2,
            month = "2027-02",
            name = "Mercado",
            limit = fromThisMonth(150_000),
            carry = CarrySetting.Off,
            confirmed = -20_000
        )
        val before = state()

        val outcome = ledger.categories.submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertEquals(before, state())
        assertEquals(Outcome.Success(null), ledger.categories.find(command.operationId))
    }

    @Test
    fun aCallerCancelledWhileSavingGetsTheCancellationAndTheSaveStaysWhole() = runBlocking {
        val comida = deficitInFebruary()
        val command = edit(
            comida,
            version = 2,
            month = "2027-02",
            name = "Mercado",
            carry = CarrySetting.Off,
            confirmed = -20_000
        )
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

        // Cancellation proves nothing about rollback: the whole save committed.
        val change = ledger.change(command.operationId, OperationKind.EditCategory, comida, 5)
        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertEquals(
            listOf("${uuid(comida)}|Mercado|shopping_cart|0|3"),
            ledger.rows("category", "id", "name", "icon", "archived", "version")
        )
        assertEquals(Outcome.Success(change), ledger.categories.find(command.operationId))
    }
}
