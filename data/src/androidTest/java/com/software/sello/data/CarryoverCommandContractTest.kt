package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CarryEnableRefusal
import com.software.sello.domain.model.CarryoverFailure
import com.software.sello.domain.model.DropPreview
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SnapshotFailure
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.CarryCorrection
import com.software.sello.domain.port.ArchiveCategory
import com.software.sello.domain.port.CategoryCommandOutcome
import com.software.sello.domain.port.CategoryRejection
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CorrectionConfirmation
import com.software.sello.domain.port.DeleteExpense
import com.software.sello.domain.port.DisableCarry
import com.software.sello.domain.port.EditExpense
import com.software.sello.domain.port.EnableCarry
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.ExpenseRejection
import com.software.sello.domain.port.SetDefaultLimit
import com.software.sello.domain.port.SetMonthLimit
import com.software.sello.domain.port.UnarchiveCategory
import java.time.LocalDate
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
 * Signed carryover's commands against a real database file (ADR 0020 CP1–CP4, CP9–CP13,
 * CP20–CP25, CP28). Each case reads the stored chains, limits, receipts and revision
 * itself. Expected amounts are copied from the examples in
 * docs/testing/signed-carryover-examples.md, never worked out with the code under test.
 */
@RunWith(AndroidJUnit4::class)
class CarryoverCommandContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val histories by lazy { CarryHistories(ledger) }

    private fun cop(pesos: Long) = Money.cop(pesos)

    private fun month(text: String) = YearMonth.parse(text)

    private fun receipts() = ledger.long("SELECT COUNT(*) FROM operation_receipt")

    private fun versionOf(category: Int) =
        ledger.long("SELECT version FROM category WHERE id = '${uuid(category)}'")

    private fun defaultRows() =
        ledger.rows("default_limit", "category_id", "effective_month", "kind", "limit_minor")

    private fun monthRows() =
        ledger.rows("month_limit", "category_id", "month", "kind", "limit_minor")

    /** Everything a refused command must leave as it was. */
    private fun state() = listOf(
        ledger.chainRows(),
        defaultRows(),
        monthRows(),
        ledger.rows("category", "id", "archived", "version"),
        ledger.rows("expense", "id", "category_id", "effective_date", "amount_minor"),
        listOf("revision ${ledger.revision()}", "receipts ${receipts()}")
    )

    private fun enable(category: Int, version: Long, generation: Long = 1) =
        EnableCarry(ledger.operation(), generation, ledger.categoryId(category), version)

    private fun disable(category: Int, version: Long, confirmed: Long? = null) = DisableCarry(
        ledger.operation(),
        1,
        ledger.categoryId(category),
        version,
        confirmed?.let(::cop)
    )

    private suspend fun history(category: Int, through: String) =
        ledger.carry.history(ledger.categoryId(category), month(through)).valueOrFail()

    private suspend fun budget(category: Int, of: String) =
        ledger.snapshots.read(month(of)).valueOrFail().categories
            .single { it.category.id == ledger.categoryId(category) }

    private fun CarryCorrection.listed() = months.map { it.text() }

    private fun pesos(amount: Long) = TransactionAmount.of(cop(amount)).valueOrFail()

    @Test
    fun turningCarryOnStoresOneOpenChainItsReceiptAndOneRevision() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        val operation = ledger.operation()

        val outcome = ledger.categories.submit(
            EnableCarry(operation, 1, ledger.categoryId(comida), 1)
        )

        val change = ledger.change(operation, OperationKind.EnableCarry, comida, revision = 2)
        assertEquals(Committed(change, replayed = false), outcome)
        assertEquals(listOf("${uuid(100)}|2027-01|null"), ledger.chainRows())
        assertEquals(2, versionOf(comida))
        assertEquals(
            listOf("${operation.value}|category.enable_carry|1|2|${uuid(100)}"),
            ledger.rows(
                "operation_receipt",
                "operation_id",
                "kind",
                "generation",
                "revision",
                "subject_id"
            ).filter { it.startsWith(operation.value) }
        )
        assertEquals(2, ledger.revision())
        assertEquals(Outcome.Success(change), ledger.categories.find(operation))
        // The base history is not touched: carry is a setting, never a stored amount.
        assertEquals(listOf("${uuid(100)}|2027-01|finite|100000"), defaultRows())
        assertEquals(emptyList<String>(), monthRows())
    }

    /** SC08 and SC46: each refusal has its own reason and changes nothing. */
    @Test
    fun turningCarryOnIsRefusedWithoutAFiniteCappedBaseNowAndAfter() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-02-01")
        ledger.categories.submit(
            ledger.createCategory("Ocio", BudgetLimit.Unlimited)
        )
        val archivada = histories.category("Archivada", 100_000, "2027-02-01")
        ledger.categories.submit(
            ArchiveCategory(ledger.operation(), 1, ledger.categoryId(archivada), 1)
        )
        val lujo = histories.category("Lujo", 1_000_000_000_000, "2027-02-01")
        val viajes = ledger.nextRecord
        ledger.categories.submit(ledger.createCategory("Viajes", BudgetLimit.Unlimited))
        ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(viajes),
                1,
                month("2027-02"),
                finite(100_000)
            )
        )
        val cero = histories.category("Cero", 0, "2027-02-01")
        val tope = histories.category("Tope", 999_999_999_999, "2027-02-01")
        histories.enable(comida, version = 1)
        val before = state()

        val refused = listOf(
            ledger.categories.submit(enable(comida, 2)),
            ledger.categories.submit(enable(101, 1)),
            ledger.categories.submit(enable(archivada, 2)),
            ledger.categories.submit(enable(lujo, 1)),
            ledger.categories.submit(enable(viajes, 2))
        )
        val unchanged = state()
        val zero = ledger.categories.submit(enable(cero, 1))
        val cap = ledger.categories.submit(enable(tope, 1))

        assertEquals(
            listOf(
                Rejected(Specific(CategoryRejection.CarryRefused(CarryEnableRefusal.AlreadyOn))),
                Rejected(
                    Specific(CategoryRejection.CarryRefused(CarryEnableRefusal.UnlimitedThisMonth))
                ),
                Rejected(Specific(CategoryRejection.CarryRefused(CarryEnableRefusal.Archived))),
                Rejected(
                    Specific(
                        CategoryRejection.CarryRefused(
                            CarryEnableRefusal.BaseAboveLimit(cop(1_000_000_000_000))
                        )
                    )
                ),
                Rejected(
                    Specific(
                        CategoryRejection.CarryRefused(CarryEnableRefusal.DefaultAfterNotFinite)
                    )
                )
            ),
            refused
        )
        assertEquals(before, unchanged)
        assertTrue("$zero", zero is Committed)
        assertTrue("$cap", cap is Committed)
        assertEquals(
            listOf(
                "${uuid(comida)}|2027-02|null",
                "${uuid(cero)}|2027-02|null",
                "${uuid(tope)}|2027-02|null"
            ),
            ledger.chainRows()
        )
    }

    /**
     * SC46: with a finite default from feb, turning on is accepted; making the default
     * unlimited later asks to confirm the exact balance and ends the chain only then.
     */
    @Test
    fun aDefaultMadeUnlimitedAfterTurningOnAsksForTheBalanceFirst() = runBlocking {
        val viajes = ledger.nextRecord
        ledger.goTo("2027-02-01")
        ledger.categories.submit(ledger.createCategory("Viajes", BudgetLimit.Unlimited))
        val id = ledger.categoryId(viajes)
        ledger.categories.submit(SetDefaultLimit(ledger.operation(), 1, id, 1, finite(100_000)))
        histories.enable(viajes, version = 2)
        ledger.goTo("2027-03-04")
        val before = state()

        val asked =
            ledger.categories.submit(
                SetDefaultLimit(ledger.operation(), 1, id, 3, BudgetLimit.Unlimited)
            )
        val unchanged = state()
        val confirmed = ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, id, 3, BudgetLimit.Unlimited, cop(100_000))
        )

        // feb spent nothing: mar K 100.000, C 200.000 → 100.000.
        assertEquals(
            Rejected(
                Specific(
                    CategoryRejection.DropUnconfirmed(
                        DropPreview(
                            month("2027-03"),
                            carryIn = cop(100_000),
                            base = cop(100_000),
                            capacityBefore = cop(200_000),
                            capacityAfter = cop(100_000),
                            remainingBefore = cop(200_000),
                            remainingAfter = cop(100_000)
                        )
                    )
                )
            ),
            asked
        )
        assertEquals(before, unchanged)
        assertTrue("$confirmed", confirmed is Committed)
        assertEquals(listOf("${uuid(viajes)}|2027-02|2027-03"), ledger.chainRows())
        assertNull(budget(viajes, "2027-03").budget)
    }

    /** SC17 and SC19: turning off with a deficit, then on again in abr. */
    @Test
    fun turningOffADeficitNeedsTheExactBalanceAndOnAgainStartsAtZero() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        histories.spend(comida, "2027-01-10", 120_000)
        histories.spend(comida, "2027-02-10", 30_000)
        val before = state()

        val unconfirmed = ledger.categories.submit(disable(comida, 2))
        val wrongBalance = ledger.categories.submit(disable(comida, 2, confirmed = -10_000))
        val unchanged = state()
        val operation = ledger.operation()
        val confirmed = ledger.categories.submit(
            DisableCarry(operation, 1, ledger.categoryId(comida), 2, cop(-20_000))
        )

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
        assertEquals(before, unchanged)
        assertEquals(
            Committed(ledger.change(operation, OperationKind.DisableCarry, comida, 5), false),
            confirmed
        )
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        val february = budget(comida, "2027-02").budget!!
        assertEquals(listOf(cop(100_000), cop(70_000)), listOf(february.limit, february.remaining))
        assertNull(february.carry)
        // ene keeps its figures; the dropped balance is derived, not stored (CP12).
        val history = history(comida, "2027-02")
        assertEquals(
            listOf("2027-01|K 0|C 100000|S 120000|R -20000|Over"),
            history.replay.months.map { it.text() }
        )
        assertEquals(listOf("2027-01|2027-02|-20000"), history.replay.settled.map { it.text() })

        // feb closes with 50.000 spent: mar is base only, 100.000, not 130.000.
        histories.spend(comida, "2027-02-20", 20_000)
        ledger.goTo("2027-03-01")
        val march = budget(comida, "2027-03").budget!!
        assertEquals(cop(100_000), march.limit)
        assertNull(march.carry)

        // SC19: on again 3 abr is a new chain at 0; ene and feb keep their figures.
        ledger.goTo("2027-04-03")
        histories.enable(comida, version = 3)
        assertEquals(
            listOf("${uuid(comida)}|2027-01|2027-02", "${uuid(comida)}|2027-04|null"),
            ledger.chainRows()
        )
        val april = history(comida, "2027-04")
        assertEquals(
            listOf(
                "2027-01|K 0|C 100000|S 120000|R -20000|Over",
                "2027-04|K 0|C 100000|S 0|R 100000|OnTrack"
            ),
            april.replay.months.map { it.text() }
        )
    }

    /** SC18 and SC19: nothing to confirm in the start month or with nothing carried. */
    @Test
    fun withNothingCarriedTurningOffAsksNothing() = runBlocking {
        val hogar = histories.category("Hogar", 100_000, "2027-01-05")
        histories.enable(hogar, version = 1)
        val ocio = histories.category("Ocio", 100_000, "2027-01-05")
        histories.enable(ocio, version = 1)
        histories.spend(hogar, "2027-01-10", 100_000)
        histories.spend(ocio, "2027-01-10", 120_000)
        val comida = histories.category("Comida", 100_000, "2027-02-03")
        histories.enable(comida, version = 1)
        ledger.goTo("2027-02-10")

        val inItsStartMonth = ledger.categories.submit(disable(comida, 2))
        val nothingCarried = ledger.categories.submit(disable(hogar, 2))
        // Off and on again in feb: feb's carry-in is 0, ene's deficit stays in ene.
        ledger.categories.submit(disable(ocio, 2, confirmed = -20_000))
        histories.enable(ocio, version = 3)

        assertTrue("$inItsStartMonth", inItsStartMonth is Committed)
        assertTrue("$nothingCarried", nothingCarried is Committed)
        assertEquals(
            listOf(
                "${uuid(hogar)}|2027-01|2027-02",
                "${uuid(ocio)}|2027-01|2027-02",
                "${uuid(ocio)}|2027-02|null"
            ),
            ledger.chainRows()
        )
        assertEquals(
            listOf(
                "2027-01|K 0|C 100000|S 120000|R -20000|Over",
                "2027-02|K 0|C 100000|S 0|R 100000|OnTrack"
            ),
            history(ocio, "2027-02").replay.months.map { it.text() }
        )
        assertEquals(
            Rejected(Specific(CategoryRejection.NotCarrying)),
            ledger.categories.submit(disable(comida, 3))
        )
    }

    /** SC20: archiving shows the balance; unarchiving never turns carry back on. */
    @Test
    fun archivingWithABalanceNeedsItConfirmedAndUnarchivingLeavesCarryOff() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        histories.spend(comida, "2027-01-10", 120_000)
        ledger.goTo("2027-02-12")
        val id = ledger.categoryId(comida)
        val before = state()

        val asked = ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, id, 2))
        val unchanged = state()
        val archived =
            ledger.categories.submit(ArchiveCategory(ledger.operation(), 1, id, 2, cop(-20_000)))

        assertEquals(
            Rejected(
                Specific(
                    CategoryRejection.DropUnconfirmed(
                        DropPreview(
                            month("2027-02"),
                            carryIn = cop(-20_000),
                            base = cop(100_000),
                            capacityBefore = cop(80_000),
                            capacityAfter = cop(100_000),
                            remainingBefore = cop(80_000),
                            remainingAfter = cop(100_000)
                        )
                    )
                )
            ),
            asked
        )
        assertEquals(before, unchanged)
        assertTrue("$archived", archived is Committed)
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertEquals(
            listOf("${uuid(comida)}|2027-01|finite|100000", "${uuid(comida)}|2027-03|paused|null"),
            defaultRows()
        )
        assertEquals(cop(100_000), budget(comida, "2027-02").budget?.limit)

        ledger.goTo("2027-06-01")
        val unarchived = ledger.categories.submit(UnarchiveCategory(ledger.operation(), 1, id, 3))
        assertTrue("$unarchived", unarchived is Committed)
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertNull(budget(comida, "2027-06").budget?.carry)
        histories.enable(comida, version = 4)
        assertEquals(cop(0), budget(comida, "2027-06").budget?.carry?.carryIn)
    }

    /** SC21: the default or this month's own limit made unlimited drops a surplus once confirmed. */
    @Test
    fun anUnlimitedLimitDropsTheBalanceOnlyOnceConfirmed() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        val hogar = histories.category("Hogar", 100_000, "2027-01-05")
        histories.enable(hogar, version = 1)
        histories.spend(comida, "2027-01-10", 20_000)
        histories.spend(hogar, "2027-01-10", 20_000)
        ledger.goTo("2027-02-05")
        val surplus = DropPreview(
            month("2027-02"),
            carryIn = cop(80_000),
            base = cop(100_000),
            capacityBefore = cop(180_000),
            capacityAfter = cop(100_000),
            remainingBefore = cop(180_000),
            remainingAfter = cop(100_000)
        )
        val before = state()

        val defaultAsked = ledger.categories.submit(
            SetDefaultLimit(
                ledger.operation(),
                1,
                ledger.categoryId(comida),
                2,
                BudgetLimit.Unlimited
            )
        )
        val monthAsked = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(hogar),
                2,
                month("2027-02"),
                BudgetLimit.Unlimited
            )
        )
        val unchanged = state()
        val defaultConfirmed = ledger.categories.submit(
            SetDefaultLimit(
                ledger.operation(),
                1,
                ledger.categoryId(comida),
                2,
                BudgetLimit.Unlimited,
                cop(80_000)
            )
        )
        val monthConfirmed = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(hogar),
                2,
                month("2027-02"),
                BudgetLimit.Unlimited,
                confirmedCarryIn = cop(80_000)
            )
        )

        assertEquals(Rejected(Specific(CategoryRejection.DropUnconfirmed(surplus))), defaultAsked)
        assertEquals(Rejected(Specific(CategoryRejection.DropUnconfirmed(surplus))), monthAsked)
        assertEquals(before, unchanged)
        assertTrue("$defaultConfirmed", defaultConfirmed is Committed)
        assertTrue("$monthConfirmed", monthConfirmed is Committed)
        assertEquals(
            listOf("${uuid(comida)}|2027-01|2027-02", "${uuid(hogar)}|2027-01|2027-02"),
            ledger.chainRows()
        )
        assertNull(budget(comida, "2027-02").budget)
        assertNull(budget(hogar, "2027-02").budget)

        // Back to a finite limit in jun: carry stays off until turned on.
        ledger.goTo("2027-06-01")
        ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, ledger.categoryId(comida), 3, finite(100_000))
        )
        val june = budget(comida, "2027-06").budget!!
        assertEquals(cop(100_000), june.limit)
        assertNull(june.carry)
        assertNull(budget(hogar, "2027-03").budget?.carry)
    }

    /** SC22 and CP13: a past chain month cannot become unlimited; a finite correction is confirmed. */
    @Test
    fun aPastChainMonthStaysFiniteAndItsCorrectionNamesTheSettledBalance() = runBlocking {
        histories.sc01()
        ledger.categories.submit(disable(100, 2, confirmed = -40_000))
        val id = ledger.categoryId(100)
        val revision = ledger.revision()
        val before = state()

        val unlimited = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 3, month("2027-03"), BudgetLimit.Unlimited)
        )
        val asked = ledger.categories.submit(
            SetMonthLimit(ledger.operation(), 1, id, 3, month("2027-03"), finite(150_000))
        )
        val unchanged = state()
        val correction =
            ((asked as Rejected).reason as Specific).reason
                as CategoryRejection.CorrectionUnconfirmed
        val confirmed = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                id,
                3,
                month("2027-03"),
                finite(150_000),
                confirmation = correction.confirmation
            )
        )

        assertEquals(
            Rejected(Specific(CategoryRejection.ChainMonthUnlimited(month("2027-03")))),
            unlimited
        )
        assertEquals(revision, correction.confirmation.revision)
        // mar's carry-in does not change, so no later month of the chain is listed; the
        // balance the chain settled in abr is.
        assertEquals(emptyList<String>(), correction.correction.listed())
        assertEquals(
            listOf("100|2027-04|-40000→10000"),
            correction.correction.settled.map {
                it.text()
            }
        )
        assertEquals(
            listOf("100|2027-03|C 210000→260000|R -40000→10000|Over→WithinLimit"),
            correction.correction.edited.map { it.text() }
        )
        assertEquals(before, unchanged)
        assertTrue("$confirmed", confirmed is Committed)
        assertEquals(listOf("${uuid(100)}|2027-03|finite|150000"), monthRows())
        val history = history(100, "2027-04")
        assertEquals(
            "2027-03|K 110000|C 260000|S 250000|R 10000|WithinLimit",
            history.replay.months.last().text()
        )
        assertEquals(listOf("2027-01|2027-04|10000"), history.replay.settled.map { it.text() })
        // abr is outside the chain: base 100.000 only.
        val april = budget(100, "2027-04").budget!!
        assertEquals(cop(100_000), april.limit)
        assertNull(april.carry)
    }

    /** CP25: no base above 999.999.999.999 while a chain needs it; other categories are unbounded. */
    @Test
    fun aChainBaseIsCappedAtTheTransactionCap() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        val ocio = histories.category("Ocio", 100_000, "2027-01-05")
        ledger.goTo("2027-02-05")
        val id = ledger.categoryId(comida)
        val above = finite(1_000_000_000_000)
        val before = state()

        val refused = listOf(
            ledger.categories.submit(SetDefaultLimit(ledger.operation(), 1, id, 2, above)),
            ledger.categories.submit(
                SetMonthLimit(ledger.operation(), 1, id, 2, month("2027-02"), above)
            ),
            ledger.categories.submit(
                SetMonthLimit(ledger.operation(), 1, id, 2, month("2027-01"), above)
            )
        )
        val unchanged = state()
        val atTheCap = ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, id, 2, finite(999_999_999_999))
        )
        val unbounded = ledger.categories.submit(
            SetDefaultLimit(ledger.operation(), 1, ledger.categoryId(ocio), 1, above)
        )

        assertEquals(
            List(3) {
                Rejected(Specific(CategoryRejection.ChainBaseAboveLimit(cop(1_000_000_000_000))))
            },
            refused
        )
        assertEquals(before, unchanged)
        assertTrue("$atTheCap", atTheCap is Committed)
        assertTrue("$unbounded", unbounded is Committed)
        assertEquals(
            listOf(
                "${uuid(comida)}|2027-01|finite|100000",
                "${uuid(comida)}|2027-02|finite|999999999999",
                "${uuid(ocio)}|2027-01|finite|100000",
                "${uuid(ocio)}|2027-02|finite|1000000000000"
            ),
            defaultRows()
        )
    }

    /** SC25 and SC34: the list is bound to the change and the revision it was read at. */
    @Test
    fun aDeletionInAClosedChainMonthIsSavedOnlyAtTheRevisionItsListWasSeen() = runBlocking {
        histories.sc01()
        val delete = { confirmed: CorrectionConfirmation? ->
            DeleteExpense(ledger.operation(1_900), 1, expenseIdOf(101), 1, confirmed)
        }
        val revision = ledger.revision()
        val expenses = {
            ledger.rows("expense", "id", "category_id", "effective_date", "amount_minor")
        }
        val before = expenses() to ledger.chainRows()

        val asked = ledger.expenses.delete(delete(null))
        ledger.categories.submit(ledger.createCategory("Transporte"))
        val moved = ledger.expenses.delete(delete(asked.refusedCorrection().confirmation))
        val unchanged = expenses() to ledger.chainRows()
        val saved = ledger.expenses.delete(delete(moved.refusedCorrection().confirmation))

        val listed = listOf(
            "100|2027-02|C 180000→200000|R 110000→130000|WithinLimit→WithinLimit",
            "100|2027-03|C 210000→230000|R -40000→-20000|Over→Over",
            "100|2027-04|C 60000→80000|R 60000→80000|OnTrack→OnTrack"
        )
        val first = asked.refusedCorrection()
        val second = moved.refusedCorrection()
        assertEquals(revision, first.confirmation.revision)
        assertEquals(listed, first.correction.listed())
        assertEquals(
            mapOf(
                month("2027-02") to cop(20_000),
                month("2027-03") to cop(20_000),
                month("2027-04") to cop(20_000)
            ),
            first.correction.overallCapacityChange
        )
        // Another change landed: refused with nothing changed and the list read again.
        assertEquals(revision + 1, second.confirmation.revision)
        assertEquals(first.confirmation.change, second.confirmation.change)
        assertEquals(listed, second.correction.listed())
        assertEquals(before, unchanged)
        assertTrue("$saved", saved is CommandOutcome.Committed)
        assertEquals(revision + 2, ledger.revision())
        assertEquals(
            listOf(
                "2027-01|K 0|C 100000|S 0|R 100000|WithinLimit",
                "2027-02|K 100000|C 200000|S 70000|R 130000|WithinLimit",
                "2027-03|K 130000|C 230000|S 250000|R -20000|Over",
                "2027-04|K -20000|C 80000|S 0|R 80000|OnTrack"
            ),
            history(100, "2027-04").replay.months.map { it.text() }
        )
        // Every base stays 100.000.
        assertEquals(
            listOf("${uuid(100)}|2027-01|finite|100000"),
            defaultRows().filter { it.startsWith(uuid(100)) }
        )
    }

    /** SC31 and SC33: this month's own expenses and months outside every chain need no list. */
    @Test
    fun anExpenseThatReachesNoClosedChainMonthIsSavedWithoutAList() = runBlocking {
        histories.sc01()
        val ocio = histories.category("Ocio", 100_000, "2027-04-01")
        ledger.goTo("2027-04-10")

        val thisMonth = ledger.expenses.create(ledger.createExpense(100, "2027-04-10", 15_000))
        val beforeTheChain = ledger.expenses.create(ledger.createExpense(100, "2026-12-05", 30_000))
        val noCarry = ledger.expenses.create(ledger.createExpense(ocio, "2027-01-15", 30_000))

        assertTrue("$thisMonth", thisMonth is CommandOutcome.Committed)
        assertTrue("$beforeTheChain", beforeTheChain is CommandOutcome.Committed)
        assertTrue("$noCarry", noCarry is CommandOutcome.Committed)
        assertEquals(
            "2027-04|K -40000|C 60000|S 15000|R 45000|OnTrack",
            history(100, "2027-04").replay.months.last().text()
        )
    }

    /** SC26: a move between two chains lists both; the overall capacity does not change. */
    @Test
    fun movingAnExpenseBetweenChainsListsEachCategory() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        val transporte = histories.category("Transporte", 50_000, "2027-01-05")
        histories.enable(transporte, version = 1)
        histories.spend(comida, "2027-01-10", 20_000)
        histories.spend(transporte, "2027-01-10", 10_000)
        val february = histories.spend(comida, "2027-02-10", 70_000)
        histories.spend(comida, "2027-03-10", 250_000)
        ledger.goTo("2027-04-05")
        val edit = { confirmed: CorrectionConfirmation? ->
            EditExpense(
                ledger.operation(1_900),
                1,
                expenseIdOf(february),
                1,
                ledger.categoryId(transporte),
                pesos(70_000),
                LocalDate.parse("2027-02-10"),
                null,
                confirmed
            )
        }
        val asked = ledger.expenses.edit(edit(null))
        val saved = ledger.expenses.edit(edit(asked.refusedCorrection().confirmation))

        val correction = asked.listedCorrection()
        assertEquals(
            listOf(
                "100|2027-03|C 210000→280000|R -40000→30000|Over→WithinLimit",
                "101|2027-03|C 140000→70000|R 140000→70000|WithinLimit→WithinLimit",
                "100|2027-04|C 60000→130000|R 60000→130000|OnTrack→OnTrack",
                "101|2027-04|C 190000→120000|R 190000→120000|OnTrack→OnTrack"
            ),
            correction.listed()
        )
        assertEquals(
            listOf(
                "100|2027-02|C 180000→180000|R 110000→180000|WithinLimit→WithinLimit",
                "101|2027-02|C 90000→90000|R 90000→20000|WithinLimit→WithinLimit"
            ),
            correction.edited.map { it.text() }
        )
        assertEquals(emptyMap<YearMonth, Money>(), correction.overallCapacityChange)
        assertTrue("$saved", saved is CommandOutcome.Committed)
        assertEquals(
            listOf("${uuid(february)}|${uuid(transporte)}"),
            ledger.rows("expense", "id", "category_id").filter { it.startsWith(uuid(february)) }
        )
    }

    /** CP23: a correction that would leave a month impossible to calculate is refused. */
    @Test
    fun aChangeThatOverflowsAChainIsRefusedWithNothingWritten() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        val ene = histories.spend(comida, "2027-01-10", 300_000)
        ledger.goTo("2027-04-05")
        // Damage no command can make: ene's own limit 9.223.372.036.854.725.807.
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(comida),
            "month" to "2027-01",
            "kind" to "finite",
            "limit_minor" to 9_223_372_036_854_725_807L,
            "currency" to "COP"
        )
        val before = state()

        // Without ene's 300.000, feb's capacity would be 9.223.372.036.854.825.807. Refused
        // before any list: there is nothing a confirmation could accept.
        val deleted = ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, expenseIdOf(ene), 1)
        )
        val corrected = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(comida),
                2,
                month("2027-02"),
                finite(400_000)
            )
        )

        assertEquals(
            CommandOutcome.Rejected(
                Specific(ExpenseRejection.CarryUnavailable(CarryoverFailure.Unrepresentable))
            ),
            deleted
        )
        assertEquals(
            Rejected(
                Specific(CategoryRejection.CarryUnavailable(CarryoverFailure.Unrepresentable))
            ),
            corrected
        )
        assertEquals(before, state())
        // As stored, every month still fits: abr C 9.223.372.036.854.725.807.
        assertEquals(
            cop(9_223_372_036_854_725_807),
            budget(comida, "2027-04").budget?.limit
        )
    }

    @Test
    fun theSameOperationAgainReturnsTheOriginalEvenAfterReopening() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        val on = enable(comida, 1)
        val first = ledger.categories.submit(on)
        histories.spend(comida, "2027-01-10", 120_000)
        ledger.goTo("2027-02-10")
        val off = disable(comida, 2, confirmed = -20_000)
        val dropped = ledger.categories.submit(off)
        val stored = state()

        val again = ledger.categories.submit(on)
        val conflict = ledger.categories.submit(
            DisableCarry(on.operationId, 1, ledger.categoryId(comida), 1, null)
        )
        ledger.reopen()
        val afterReopening = listOf(ledger.categories.submit(on), ledger.categories.submit(off))

        val enabled = ledger.change(on.operationId, OperationKind.EnableCarry, comida, 2)
        val disabled = ledger.change(off.operationId, OperationKind.DisableCarry, comida, 4)
        assertEquals(Committed(enabled, false), first)
        assertEquals(Committed(disabled, false), dropped)
        assertEquals(Committed(enabled, true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), conflict)
        assertEquals(listOf(Committed(enabled, true), Committed(disabled, true)), afterReopening)
        assertEquals(stored, state())
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
    }

    @Test
    fun aStaleVersionOrAReplacedHistoryIsRefusedWithNothingWritten() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        val before = state()

        val replaced = ledger.categories.submit(enable(comida, 1, generation = 2))
        val stale = ledger.categories.submit(enable(comida, 7))

        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), replaced)
        assertEquals(Rejected(CommandRejection.VersionConflict(current = 1)), stale)
        assertEquals(before, state())
    }

    @Test
    fun twoTogglesOfTheSameVersionAtOnceOneWinsAndTheOtherConflicts() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        val go = CompletableDeferred<Unit>()

        val outcomes = List(8) {
            val command = enable(comida, 1)
            async(Dispatchers.Default) {
                go.await()
                ledger.categories.submit(command)
            }
        }.also { go.complete(Unit) }.awaitAll()

        assertEquals(1, outcomes.count { it is Committed })
        assertEquals(
            List(7) { Rejected(CommandRejection.VersionConflict(current = 2)) },
            outcomes.filter { it !is Committed }
        )
        assertEquals(listOf("${uuid(comida)}|2027-01|null"), ledger.chainRows())
        assertEquals(2, ledger.revision())
    }

    @Test
    fun aFailureAfterTheChainWasWrittenLeavesNoChainVersionRevisionOrReceipt() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_category_update BEFORE UPDATE ON category " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val command = enable(comida, 1)
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
    fun aCallerCancelledWhileDroppingGetsTheCancellationAndTheDropStaysSaved() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        histories.spend(comida, "2027-01-10", 120_000)
        ledger.goTo("2027-02-10")
        val command = disable(comida, 2, confirmed = -20_000)
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

        // Cancellation proves nothing about rollback: the drop committed with its receipt.
        val change = ledger.change(command.operationId, OperationKind.DisableCarry, comida, 4)
        assertNull(returned)
        assertTrue(cancellation != null)
        assertEquals(listOf("${uuid(comida)}|2027-01|2027-02"), ledger.chainRows())
        assertEquals(Outcome.Success(change), ledger.categories.find(command.operationId))
        assertEquals(Committed(change, replayed = true), ledger.categories.submit(command))
        assertEquals(4, ledger.revision())
    }

    /**
     * Review round 1, R1: a chain broken by a damaged row (feb, inside SC01's chain, made
     * unlimited, which no command can do) is repaired by correcting that row; nothing else
     * that leaves it broken saves, and its balance cannot be dropped while nobody can show it.
     */
    @Test
    fun aCorrectionThatRepairsABrokenChainSavesAndTheMonthReadsAgain() = runBlocking {
        histories.sc01()
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(100),
            "month" to "2027-02",
            "kind" to "unlimited",
            "limit_minor" to null,
            "currency" to "COP"
        )
        val broken = CarryoverFailure.NoFiniteBase(month("2027-02"))
        val before = state()
        val brokenRead = ledger.snapshots.read(month("2027-03"))

        val stillBroken = ledger.expenses.create(ledger.createExpense(100, "2027-03-15", 5_000))
        val drop = ledger.categories.submit(disable(100, 2))
        val unchanged = state()
        val repaired = ledger.categories.submit(
            SetMonthLimit(
                ledger.operation(),
                1,
                ledger.categoryId(100),
                2,
                month("2027-02"),
                finite(100_000)
            )
        )

        assertEquals(
            Outcome.Failure(
                SnapshotFailure.Carryover(broken)
            ),
            brokenRead
        )
        assertEquals(
            CommandOutcome.Rejected(Specific(ExpenseRejection.CarryUnavailable(broken))),
            stillBroken
        )
        assertEquals(Rejected(Specific(CategoryRejection.CarryUnavailable(broken))), drop)
        assertEquals(before, unchanged)
        assertTrue("$repaired", repaired is Committed)
        // SC01 again: abr K −40.000, C 60.000.
        val april = budget(100, "2027-04").budget!!
        assertEquals(listOf(cop(60_000), cop(-40_000)), listOf(april.limit, april.carry?.carryIn))
    }

    /**
     * R1: a chain whose stored state overflows (CP23; damage no command can make: ene's own
     * limit 9.223.372.036.854.725.807, nothing spent, so feb's capacity does not fit) is
     * repaired by the expense that brings it back in range; removing it again is refused.
     */
    @Test
    fun anExpenseThatBringsAnOverflowingChainBackIntoRangeSaves() = runBlocking {
        val comida = histories.category("Comida", 100_000, "2027-01-05")
        histories.enable(comida, version = 1)
        ledger.goTo("2027-04-05")
        ledger.database.insertRaw(
            "month_limit",
            "category_id" to uuid(comida),
            "month" to "2027-01",
            "kind" to "finite",
            "limit_minor" to 9_223_372_036_854_725_807L,
            "currency" to "COP"
        )
        val brokenBefore = ledger.snapshots.read(month("2027-04"))

        val repaired = ledger.expenses.create(ledger.createExpense(comida, "2027-01-10", 300_000))
        val undone = ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, expenseIdOf(ledger.nextRecord - 1), 1)
        )

        assertEquals(
            Outcome.Failure(
                SnapshotFailure.Unrepresentable
            ),
            brokenBefore
        )
        assertTrue("$repaired", repaired is CommandOutcome.Committed)
        // ene R 9.223.372.036.854.425.807; feb C …525.807; mar …625.807; abr C …725.807.
        assertEquals(cop(9_223_372_036_854_725_807), budget(comida, "2027-04").budget?.limit)
        assertEquals(
            CommandOutcome.Rejected(
                Specific(ExpenseRejection.CarryUnavailable(CarryoverFailure.Unrepresentable))
            ),
            undone
        )
    }

    /**
     * Review round 1, R2: a confirmation is for the change whose list was shown. A deletion
     * of feb's 70.000 cannot save on the confirmation of ene's 20.000 at the same revision.
     */
    @Test
    fun aConfirmationSavesOnlyTheChangeWhoseListWasShown() = runBlocking {
        histories.sc01()
        val revision = ledger.revision()
        val shown = ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, expenseIdOf(101), 1)
        ).refusedCorrection()
        val expenses = ledger.rows("expense", "id", "amount_minor")

        val other = ledger.expenses.delete(
            DeleteExpense(ledger.operation(), 1, expenseIdOf(102), 1, shown.confirmation)
        )
        val unchanged = ledger.rows("expense", "id", "amount_minor")
        val ownList = ledger.expenses.delete(
            DeleteExpense(
                ledger.operation(),
                1,
                expenseIdOf(102),
                1,
                other.refusedCorrection().confirmation
            )
        )

        // ene's list was shown; feb's is its own, the same as SC26 shows for Comida.
        assertEquals(3, shown.correction.months.size)
        assertEquals(revision, other.refusedCorrection().confirmation.revision)
        assertEquals(
            listOf(
                "100|2027-03|C 210000→280000|R -40000→30000|Over→WithinLimit",
                "100|2027-04|C 60000→130000|R 60000→130000|OnTrack→OnTrack"
            ),
            other.listedCorrection().months.map { it.text() }
        )
        assertEquals(expenses, unchanged)
        assertTrue("$ownList", ownList is CommandOutcome.Committed)
        assertEquals(revision + 1, ledger.revision())
    }
}
