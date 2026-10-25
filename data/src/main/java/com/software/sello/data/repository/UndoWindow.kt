package com.software.sello.data.repository

import com.software.sello.data.local.SelloDatabase
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.policy.UndoPolicy
import com.software.sello.domain.port.MonotonicClock
import kotlin.time.Duration
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The one deletion that can still be undone, whichever kind of record it removed. It
 * is held here, in memory, and shared by every workflow that can delete: that is what
 * makes there be one offer and not one per kind, and what makes it end with the
 * process, since a new one starts with none.
 *
 * [monotonic] measures the time a deletion can be undone, and nothing else.
 */
internal class UndoWindow(private val monotonic: MonotonicClock) {
    /** What a deletion removed. An offer is only ever answered to the workflow of its kind. */
    enum class Kind { Expense, Income, GoalMovement, InvestmentMovement }

    /** The deletion on offer, and the clock reading taken when it committed. */
    class Pending(
        val kind: Kind,
        val deletionId: OperationId,
        val subjectId: String,
        val deletedAt: Duration
    ) {
        val until: Duration get() = deletedAt + UndoPolicy.WINDOW
    }

    @Volatile
    private var pending: Pending? = null

    /** One deletion or undo at a time, so [pending] always names the deletion that committed last. */
    private val oneAtATime = Mutex()

    /** The deletion of [kind] that can be undone right now, or null. */
    fun offered(kind: Kind): Pending? = pending?.takeIf {
        it.kind == kind && UndoPolicy.canUndo(it.deletedAt, monotonic.elapsed())
    }

    /** A deletion committed: it takes the place of whatever was on offer. Its time starts now. */
    fun opened(kind: Kind, deletionId: OperationId, subjectId: String) {
        pending = Pending(kind, deletionId, subjectId, monotonic.elapsed())
    }

    /** The deletion on offer was undone. */
    fun closed() {
        pending = null
    }

    /**
     * Runs a deletion or an undo together with its change to the offer. Like the
     * transaction inside it, it is checked for cancellation before and after and never
     * stopped in between: a deletion that committed always leaves its offer behind.
     */
    suspend fun <T> changing(change: suspend () -> T): T {
        currentCoroutineContext().ensureActive()
        val outcome = withContext(NonCancellable) { oneAtATime.withLock { change() } }
        currentCoroutineContext().ensureActive()
        return outcome
    }
}

/**
 * Removes what was kept to undo any earlier deletion, of any kind, which makes
 * those deletions final. Called inside the transaction of a new deletion, and when
 * storage is opened.
 */
internal suspend fun SelloDatabase.discardKeptCopies() {
    deletedExpenseDao().clear()
    deletedIncomeDao().clear()
    deletedGoalEventDao().clear()
    deletedInvestmentMovementDao().clear()
}
