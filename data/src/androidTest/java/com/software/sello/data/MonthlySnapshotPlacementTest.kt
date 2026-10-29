package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthlySnapshot
import com.software.sello.domain.port.CommandOutcome
import com.software.sello.domain.port.ExpenseCommandOutcome
import com.software.sello.domain.port.SnapshotState
import java.time.YearMonth
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a month's figures are worked out. The computation dispatcher here runs
 * nothing until the test says so, so a snapshot that appears while it is held was
 * worked out somewhere else: on the caller, which in the app is the main thread.
 */
@RunWith(AndroidJUnit4::class)
class MonthlySnapshotPlacementTest {
    @get:Rule
    val files = DatabaseFiles()

    /** Keeps what it is given until [release]; [waitForWork] says when something arrived. */
    private class HeldDispatcher : CoroutineDispatcher() {
        private val held = ConcurrentLinkedQueue<Runnable>()
        private val arrived = Semaphore(0)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            held += block
            arrived.release()
        }

        fun waitForWork(): Boolean = arrived.tryAcquire(10, TimeUnit.SECONDS)

        /** True if nothing arrives in a time far longer than a read of three rows takes. */
        fun staysIdle(): Boolean = !arrived.tryAcquire(2, TimeUnit.SECONDS)

        fun release() {
            while (true) (held.poll() ?: return).run()
        }
    }

    private val computation = HeldDispatcher()
    private val ledger by lazy { TestLedger(files, computation).also { it.start() } }
    private val october = YearMonth.of(2026, 10)

    private suspend fun spend(pesos: Long) {
        val outcome = ledger.expenses.create(ledger.createExpense(100, "2026-10-03", pesos))
        assertTrue("$outcome", outcome is CommandOutcome.Committed)
    }

    /** Mercado, limit 100.000, with 10.000 and 5.000 spent: revision 3. */
    private suspend fun mercadoWithTwoExpenses() {
        ledger.categories.submit(ledger.createCategory("Mercado"))
        spend(10_000)
        spend(5_000)
    }

    @Test
    fun theFiguresAreWorkedOutOnTheComputationDispatcherNotOnTheCaller() = runBlocking {
        mercadoWithTwoExpenses()

        val reading = async(Dispatchers.Default) { ledger.snapshots.read(october) }

        assertTrue("nothing was handed to the computation dispatcher", computation.waitForWork())
        assertFalse("a snapshot appeared while computation was held", reading.isCompleted)
        computation.release()
        val snapshot: MonthlySnapshot = withTimeout(10_000) { reading.await() }.valueOrFail()
        assertEquals(Money.cop(15_000), snapshot.spent)
        assertEquals(3L, snapshot.revision)
    }

    @Test
    fun theFiguresAreOfTheRowsThatWereReadEvenIfMoreIsSavedBeforeTheyAreWorkedOut() = runBlocking {
        mercadoWithTwoExpenses()
        val reading = async(Dispatchers.Default) { ledger.snapshots.read(october) }
        assertTrue(computation.waitForWork())

        // Saved after the rows were read and before the figures were worked out.
        spend(70_000)
        computation.release()

        val snapshot = withTimeout(10_000) { reading.await() }.valueOrFail()
        assertEquals(Money.cop(15_000) to 3L, snapshot.spent to snapshot.revision)
        assertEquals(listOf(2), snapshot.categories.map { it.expenseCount })

        // The next read is whole and of the later revision: never a mix of the two.
        val later = async(Dispatchers.Default) { ledger.snapshots.read(october) }
        assertTrue(computation.waitForWork())
        computation.release()
        val after = withTimeout(10_000) { later.await() }.valueOrFail()
        assertEquals(Money.cop(85_000) to 4L, after.spent to after.revision)
    }

    @Test
    fun anObservationCancelledWhileItsFiguresAreWorkedOutNeverDeliversThem() = runBlocking {
        mercadoWithTwoExpenses()
        val seen = ConcurrentLinkedQueue<SnapshotState>()
        val watching = launch(Dispatchers.Default) {
            ledger.snapshots.observe(october).collect { seen += it }
        }
        assertTrue(computation.waitForWork())

        // What switching month does to the observation of the month left behind.
        watching.cancel()
        computation.release()
        withTimeout(10_000) { watching.join() }

        assertEquals(listOf<SnapshotState>(SnapshotState.Loading), seen.toList())
        assertTrue(computation.staysIdle())
    }
}
