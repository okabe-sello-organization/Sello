package com.software.sello.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.GoalChange
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalMovementLine
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.OperationId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.OperationReceipt
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import com.software.sello.domain.port.CommandOutcome.Committed
import com.software.sello.domain.port.CommandOutcome.Rejected
import com.software.sello.domain.port.CommandRejection
import com.software.sello.domain.port.CommandRejection.Specific
import com.software.sello.domain.port.CommittedGoal
import com.software.sello.domain.port.CompleteGoal
import com.software.sello.domain.port.CreateGoal
import com.software.sello.domain.port.GoalCommandOutcome
import com.software.sello.domain.port.GoalRejection
import com.software.sello.domain.port.GoalSummary
import com.software.sello.domain.port.ReceiptGoal
import com.software.sello.domain.port.RecordGoalMovement
import com.software.sello.domain.port.RecordGoalMovements
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
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
 * Several aportes and retiros on one receipt, of one goal (SELLO-128) or several
 * (SELLO-139), against a real database file: every line of every goal in one transaction,
 * or none. Viaje is goal 100 with a target of 3.000.000 (its target event is 101); Moto,
 * made by [moto], is goal 200 with a target of 1.200.000 (its target event is 201);
 * operations are numbered from 1000; today is 9 October 2026. Every expected figure is
 * worked out by hand in the test's comment.
 */
@RunWith(AndroidJUnit4::class)
class GoalReceiptContractTest {
    @get:Rule
    val files = DatabaseFiles()

    private val ledger by lazy { TestLedger(files).also { it.start() } }
    private val today = LocalDate.of(2026, 10, 9)
    private val viaje = GoalId.of(uuid(100)).valueOrFail()
    private val motoId = GoalId.of(uuid(200)).valueOrFail()

    private fun day(month: Int, day: Int) = LocalDate.of(2026, month, day)

    private fun aporte(
        pesos: Long,
        date: LocalDate = today,
        note: String? = null,
        goal: GoalId = viaje
    ) = GoalMovementLine(
        goal,
        GoalMovementKind.Contribution,
        transactionPesos(pesos),
        date,
        Note.of(note).valueOrFail()
    )

    private fun retiro(pesos: Long, date: LocalDate = today, goal: GoalId = viaje) =
        GoalMovementLine(goal, GoalMovementKind.Withdrawal, transactionPesos(pesos), date, null)

    private fun events() =
        ledger.rows("goal_event", "sequence", "id", "kind", "amount_minor", "version")

    private fun receipts() = ledger.rows("operation_receipt", "revision", "kind", "subject_id")

    private fun goalRow() = ledger.rows("goal", "next_sequence", "version").single()

    private fun goalRows() = ledger.rows("goal", "id", "next_sequence", "version")

    /** Every movement with its goal, for a receipt of several goals. */
    private fun goalEvents() =
        ledger.rows("goal_event", "goal_id", "sequence", "id", "kind", "amount_minor")

    private suspend fun summary(goal: GoalId = viaje): GoalSummary =
        (ledger.goalReads.goal(goal) as Outcome.Success).value!!

    private suspend fun version(goal: GoalId = viaje) = summary(goal).history.goal.version

    /** The lines, with the version of each goal they name as read now unless [versions]. */
    private suspend fun receipt(
        vararg lines: GoalMovementLine,
        operation: OperationId = ledger.operation(),
        versions: Map<GoalId, Long>? = null,
        generation: Long = 1
    ) = RecordGoalMovements(
        operation,
        generation,
        versions ?: lines.map { it.goalId }.distinct().associateWith { version(it) },
        lines.toList()
    )

    private suspend fun submit(command: RecordGoalMovements) = ledger.goals.submit(command)

    private fun committed(outcome: GoalCommandOutcome) = (outcome as Committed).value

    /** The goal, at version 1 and revision 1, and [saved] in one aporte before the receipt. */
    private suspend fun started(saved: Long = 0) {
        val created = ledger.goals.submit(
            CreateGoal(
                ledger.operation(),
                1,
                GoalName.of("Viaje a la costa").valueOrFail(),
                IconKey.of("flight").valueOrFail(),
                transactionPesos(3_000_000),
                YearMonth.of(2026, 12)
            )
        )
        assertTrue(created is Committed)
        if (saved > 0) {
            val recorded = ledger.goals.submit(
                RecordGoalMovement(
                    ledger.operation(),
                    1,
                    viaje,
                    1,
                    GoalMovementKind.Contribution,
                    transactionPesos(saved),
                    today,
                    null
                )
            )
            assertTrue(recorded is Committed)
        }
    }

    /** Moto, goal 200 with a target of 1.200.000, at version 1; [saved] in one aporte. */
    private suspend fun moto(saved: Long = 0) {
        ledger.nextRecord = 200
        val created = ledger.goals.submit(
            CreateGoal(
                ledger.operation(),
                1,
                GoalName.of("Moto").valueOrFail(),
                IconKey.of("two_wheeler").valueOrFail(),
                transactionPesos(1_200_000),
                null
            )
        )
        assertTrue(created is Committed)
        if (saved > 0) {
            val recorded = ledger.goals.submit(
                RecordGoalMovement(
                    ledger.operation(),
                    1,
                    motoId,
                    1,
                    GoalMovementKind.Contribution,
                    transactionPesos(saved),
                    today,
                    null
                )
            )
            assertTrue(recorded is Committed)
        }
    }

    private fun snapshot(): List<Any> =
        listOf(goalEvents(), goalRows(), ledger.revision(), receipts())

    /** [committed] as its replay answers: the same goals and movements, nothing celebrated. */
    private fun replayOf(committed: CommittedGoal) =
        committed.copy(goals = committed.goals.map { it.copy(change = null) })

    /** Nothing a refused receipt could have touched has moved since [before]. */
    private fun assertUnchanged(before: List<Any>) = assertEquals(before, snapshot())

    // ---- Committing ----

    @Test
    fun everyLineIsWrittenByDateWithOneVersionOneRevisionAndOneReceipt() = runBlocking {
        started()
        val command = receipt(
            aporte(750_000),
            aporte(750_000, day(8, 1)),
            retiro(300_000, day(9, 1))
        )

        val saved = committed(submit(command))

        // Recorded 1 Aug +750.000 (750.000, the quarter), 1 Sep −300.000 (450.000),
        // 9 Oct +750.000: 1.200.000 of 3.000.000 is 40 %, the quarter earned once.
        val ids = saved.movementIds!!.map { it.value }
        assertEquals(3, ids.toSet().size)
        assertEquals(
            listOf(
                "0|${uuid(101)}|target|3000000|1",
                "1|${ids[1]}|contribution|750000|1",
                "2|${ids[2]}|withdrawal|300000|1",
                "3|${ids[0]}|contribution|750000|1"
            ),
            events()
        )
        assertEquals("4|2", goalRow())
        assertEquals(2L, ledger.revision())
        assertEquals(
            listOf("1|goal.create|${uuid(100)}", "2|goal.record_movements|${uuid(100)}"),
            receipts()
        )
        assertEquals(OperationKind.RecordGoalMovements to viaje, saved.receipt.kind to saved.goalId)
        assertEquals(
            listOf(ReceiptGoal(viaje, GoalChange(listOf(GoalPlace.Quarter), emptyList(), false))),
            saved.goals
        )
        assertNull(saved.change)
        val progress = summary().progress
        assertEquals(Money.cop(1_200_000), progress.saved)
        assertEquals(40L, progress.percentage)
        val dates = summary().history.events.filterIsInstance<GoalMovement>()
            .associate { it.id.value to it.date }
        assertEquals(listOf(today, day(8, 1), day(9, 1)), ids.map(dates::getValue))
    }

    @Test
    fun aReceiptReachingTheTargetCelebratesEveryPlaceOnceAsOneChange() = runBlocking {
        started()

        // 1.000.000 + 1.000.000 + 1.000.000 = 3.000.000 of 3.000.000: 100 %.
        val saved = committed(
            submit(
                receipt(
                    aporte(1_000_000, day(8, 1)),
                    aporte(1_000_000, day(9, 1)),
                    aporte(1_000_000)
                )
            )
        )

        assertEquals(
            GoalChange(
                listOf(GoalPlace.Quarter, GoalPlace.Half, GoalPlace.ThreeQuarters),
                emptyList(),
                true
            ),
            saved.goals.single().change
        )
        assertEquals(100L, summary().progress.percentage)
    }

    @Test
    fun thirtyLinesAreOneReceipt() = runBlocking {
        started()

        // 30 × 1.000 = 30.000.
        val saved = committed(submit(receipt(*Array(30) { aporte(1_000) })))

        assertEquals(30, saved.movementIds!!.size)
        assertEquals(Money.cop(30_000), summary().progress.saved)
        assertEquals(2L, ledger.revision())
    }

    // ---- What a retiro may take ----

    @Test
    fun aRetiroUsesAnAporteOfTheSameReceiptDatedBeforeIt() = runBlocking {
        started(saved = 200_000)

        // 200.000, then 1 Sep +500.000 (700.000), then 10 Sep −600.000: 100.000 left.
        val outcome = submit(receipt(retiro(600_000, day(9, 10)), aporte(500_000, day(9, 1))))

        assertTrue(outcome is Committed)
        assertEquals(Money.cop(100_000), summary().progress.saved)
    }

    @Test
    fun aRetiroAboveWhatIsSavedBeforeItNamesItsLineAndWritesNothing() = runBlocking {
        started(saved = 200_000)
        val before = snapshot()

        // The retiro of line 1 is dated 1 Sep, before the aporte of line 0 (10 Sep): only
        // the 200.000 already saved is there for it.
        val outcome = submit(receipt(aporte(500_000, day(9, 10)), retiro(600_000, day(9, 1))))

        assertEquals(
            Rejected(Specific(GoalRejection.LineAboveSaved(1, viaje, Money.cop(200_000)))),
            outcome
        )
        assertUnchanged(before)
    }

    // ---- Refused input ----

    @Test
    fun aFutureDateNamesItsLineAndWritesNothing() = runBlocking {
        started()
        val before = snapshot()

        val outcome = submit(receipt(aporte(1_000), aporte(1_000, day(10, 10)), aporte(1_000)))

        assertEquals(Rejected(Specific(GoalRejection.LineFutureDate(1, today))), outcome)
        assertUnchanged(before)
    }

    @Test
    fun aDateTheStoredTextCannotHoldNamesItsLineAndWritesNothing() = runBlocking {
        started()
        val before = snapshot()

        // Year -1 would be stored as "-0001-01-01" and never read back.
        val outcome = submit(
            receipt(aporte(1_000), aporte(1_000, LocalDate.of(-1, 1, 1)), aporte(1_000))
        )

        assertEquals(Rejected(Specific(GoalRejection.LineInvalidDate(1))), outcome)
        assertUnchanged(before)
    }

    @Test
    fun noLineOrMoreThanThirtyIsRefused() = runBlocking {
        started()
        val before = snapshot()

        val none = submit(receipt())
        val tooMany = submit(receipt(*Array(31) { aporte(1_000) }))

        assertEquals(Rejected(Specific(GoalRejection.LineCount(0))), none)
        assertEquals(Rejected(Specific(GoalRejection.LineCount(31))), tooMany)
        assertUnchanged(before)
    }

    @Test
    fun aReceiptForAnOldVersionOfTheGoalOrACompletedGoalIsRefused() = runBlocking {
        started(saved = 200_000)
        val before = snapshot()

        val stale = submit(receipt(aporte(1_000), versions = mapOf(viaje to 1L)))

        assertEquals(Rejected(CommandRejection.VersionConflict(2)), stale)
        assertUnchanged(before)
        ledger.goals.submit(CompleteGoal(ledger.operation(), 1, viaje, version(), 10.seconds))
        assertEquals(
            Rejected(Specific(GoalRejection.Completed)),
            submit(receipt(aporte(1_000)))
        )
    }

    // ---- Replay, recovery and failure ----

    @Test
    fun theSameReceiptAgainIsTheSameMovementsAndADifferentOneUnderItsIdIsAConflict() = runBlocking {
        started()
        val operation = ledger.operation()
        val command = receipt(
            aporte(750_000),
            aporte(1_000, note = "Prima"),
            operation = operation
        )
        val first = committed(submit(command))

        val again = submit(command)
        val other = submit(command.copy(lines = command.lines.reversed()))
        val stale = submit(command.copy(generation = 2))

        assertEquals(Committed(replayOf(first), replayed = true), again)
        assertEquals(Rejected(CommandRejection.OperationConflict), other)
        assertEquals(Rejected(CommandRejection.StaleGeneration(current = 1)), stale)
        assertEquals(3, events().size)
        assertEquals(2L, ledger.revision())
    }

    @Test
    fun afterARestartTheReceiptIsFoundAndItsReplayNamesTheSameMovements() = runBlocking {
        started()
        val command = receipt(aporte(750_000), retiro(1_000))
        val first = committed(submit(command))
        val unsent = ledger.operation()

        ledger.reopen()

        val found = (ledger.goals.find(command.operationId) as Outcome.Success).value!!
        assertEquals(first.receipt to viaje, found.receipt to found.goalId)
        assertEquals(listOf(ReceiptGoal(viaje, null)), found.goals)
        // Its lines are not kept with the receipt: which movements it wrote is not known.
        assertNull(found.movementIds)
        assertEquals(Outcome.Success(null), ledger.goals.find(unsent))
        assertEquals(Committed(replayOf(first), replayed = true), submit(command))
        assertEquals(Money.cop(749_000), summary().progress.saved)
    }

    @Test
    fun aFailureAfterSomeLinesWereWrittenLeavesNoLineVersionRevisionOrReceipt() = runBlocking {
        started()
        // The database itself refuses the third line, after two were written.
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_third_line BEFORE INSERT ON goal_event " +
                "WHEN NEW.amount_minor = 3333 BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = snapshot()
        val command = receipt(aporte(1_111, day(8, 1)), aporte(2_222, day(9, 1)), aporte(3_333))

        val outcome = submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertUnchanged(before)
        assertEquals(Outcome.Success(null), ledger.goals.find(command.operationId))
    }

    @Test
    fun aDamagedHistoryRefusesTheReceiptWithNothingWritten() = runBlocking {
        started(saved = 200_000)
        val command = receipt(aporte(1_000), retiro(1_000))
        val damaged = ledger.rows("goal_event", "id", "kind")
            .single { it.endsWith("|contribution") }
            .substringBefore("|")
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE goal_event SET kind = 'gift' WHERE id = '$damaged'"
        )
        val before = snapshot()

        val outcome = submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("goal_event", damaged, "kind")
                )
            ),
            outcome
        )
        assertUnchanged(before)
    }

    @Test
    fun aRecordingPositionPastTheLargestRefusesTheReceiptWithNothingWritten() = runBlocking {
        started()
        ledger.database.openHelper.writableDatabase.execSQL(
            "UPDATE goal SET next_sequence = ${Long.MAX_VALUE - 1}"
        )
        val before = snapshot()

        // Line 0 takes MAX − 1 and line 1 takes MAX: no position is left for what follows.
        val outcome = submit(receipt(aporte(1_000), aporte(1_000)))

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Integrity("goal", uuid(100), "next_sequence")
                )
            ),
            outcome
        )
        assertUnchanged(before)
    }

    @Test
    fun aCallerCancelledWhileTheReceiptIsSavingIsToldSoAndCanRecoverIt() = runBlocking {
        started()
        val command = receipt(aporte(750_000), aporte(250_000))
        lateinit var caller: Job
        var returned: GoalCommandOutcome? = null
        var cancellation: CancellationException? = null
        ledger.whenAWriteIsStamped = { caller.cancel() }

        caller = launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
            try {
                returned = ledger.goals.submit(command)
            } catch (cancelled: CancellationException) {
                cancellation = cancelled
                throw cancelled
            }
        }
        caller.join()
        ledger.whenAWriteIsStamped = {}

        assertNull(returned)
        assertTrue(cancellation != null)
        // 750.000 + 250.000 = 1.000.000, written once.
        assertEquals(Money.cop(1_000_000), summary().progress.saved)
        val found = (ledger.goals.find(command.operationId) as Outcome.Success).value
        assertEquals(OperationKind.RecordGoalMovements, found!!.receipt.kind)
        assertNull(found.movementIds)
        val replayed = committed(submit(command))
        assertEquals(found.receipt, replayed.receipt)
        assertEquals(
            replayed.movementIds!!.map { it.value }.toSet(),
            ledger.rows("goal_event", "id", "kind").filter { it.endsWith("contribution") }
                .map { it.substringBefore("|") }.toSet()
        )
        assertEquals(Money.cop(1_000_000), summary().progress.saved)
    }

    // ---- Several goals on one receipt (SELLO-139) ----

    @Test
    fun linesOfTwoGoalsAreWrittenTogetherEachGoalWithItsOwnVersionAndChange() = runBlocking {
        started()
        moto()

        val saved = committed(
            submit(
                receipt(
                    aporte(750_000),
                    aporte(300_000, day(9, 1), goal = motoId),
                    aporte(750_000, day(8, 1)),
                    aporte(600_000, goal = motoId)
                )
            )
        )

        // Viaje: 1 Aug +750.000 (25 %, the quarter), 9 Oct +750.000: 1.500.000 of 3.000.000
        // is 50 %, the half. Moto: 1 Sep +300.000 of 1.200.000 (25 %, the quarter), 9 Oct
        // +600.000: 900.000 is 75 %, the half and three quarters.
        val ids = saved.movementIds!!.map { it.value }
        assertEquals(4, ids.toSet().size)
        assertEquals(
            listOf(
                "${uuid(100)}|0|${uuid(101)}|target|3000000",
                "${uuid(100)}|1|${ids[2]}|contribution|750000",
                "${uuid(100)}|2|${ids[0]}|contribution|750000",
                "${uuid(200)}|0|${uuid(201)}|target|1200000",
                "${uuid(200)}|1|${ids[1]}|contribution|300000",
                "${uuid(200)}|2|${ids[3]}|contribution|600000"
            ),
            goalEvents()
        )
        assertEquals(listOf("${uuid(100)}|3|2", "${uuid(200)}|3|2"), goalRows())
        assertEquals(3L, ledger.revision())
        assertEquals(
            "3|goal.record_movements|${uuid(100)},${uuid(200)}",
            receipts().last()
        )
        assertEquals(viaje, saved.goalId)
        assertEquals(
            listOf(
                ReceiptGoal(
                    viaje,
                    GoalChange(listOf(GoalPlace.Quarter, GoalPlace.Half), emptyList(), false)
                ),
                ReceiptGoal(
                    motoId,
                    GoalChange(
                        listOf(GoalPlace.Quarter, GoalPlace.Half, GoalPlace.ThreeQuarters),
                        emptyList(),
                        false
                    )
                )
            ),
            saved.goals
        )
        assertEquals(50L, summary(viaje).progress.percentage)
        assertEquals(Money.cop(900_000), summary(motoId).progress.saved)
        assertEquals(75L, summary(motoId).progress.percentage)
    }

    @Test
    fun aRetiroChecksOnlyItsOwnGoalAndItsRefusalWritesNothingInEitherGoal() = runBlocking {
        started(saved = 200_000)
        moto()
        val before = snapshot()

        // Moto saved nothing: Viaje's aporte of 1 Sep on the same receipt is not Moto's.
        val motoRetiro = submit(
            receipt(aporte(500_000, day(9, 1)), retiro(100_000, day(9, 10), goal = motoId))
        )
        // Viaje has its 200.000 only: Moto's aporte of 1 Sep is not Viaje's. Moto, named
        // first, is checked first and passes.
        val viajeRetiro = submit(
            receipt(aporte(500_000, day(9, 1), goal = motoId), retiro(600_000, day(9, 10)))
        )

        assertEquals(
            Rejected(Specific(GoalRejection.LineAboveSaved(1, motoId, Money.cop(0)))),
            motoRetiro
        )
        assertEquals(
            Rejected(Specific(GoalRejection.LineAboveSaved(1, viaje, Money.cop(200_000)))),
            viajeRetiro
        )
        assertUnchanged(before)
    }

    @Test
    fun aFailureWritingTheSecondGoalLeavesNothingOfTheFirst() = runBlocking {
        started()
        moto()
        // The database itself refuses Moto's line, after Viaje's two lines and row were written.
        ledger.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_moto_line BEFORE INSERT ON goal_event " +
                "WHEN NEW.amount_minor = 3333 BEGIN SELECT RAISE(ABORT, 'refused'); END"
        )
        val before = snapshot()
        val command = receipt(aporte(1_111), aporte(2_222), aporte(3_333, goal = motoId))

        val outcome = submit(command)

        assertEquals(
            Rejected(
                CommandRejection.StorageFailed(
                    StorageFailure.Unavailable("SQLiteConstraintException")
                )
            ),
            outcome
        )
        assertUnchanged(before)
        assertEquals(Outcome.Success(null), ledger.goals.find(command.operationId))
    }

    @Test
    fun aMissingChangedOrCompletedGoalOrUnmatchedVersionsRefuseTheWholeReceipt() = runBlocking {
        started(saved = 200_000)
        moto()
        val missing = GoalId.of(uuid(300)).valueOrFail()
        val both = arrayOf(aporte(1_000), aporte(1_000, goal = motoId))
        val before = snapshot()

        // Viaje is at version 2 and Moto at version 1.
        val changed = submit(receipt(*both, versions = mapOf(viaje to 2L, motoId to 5L)))
        val gone = submit(
            receipt(
                aporte(1_000),
                aporte(1_000, goal = missing),
                versions = mapOf(
                    viaje to 2L,
                    missing to 1L
                )
            )
        )
        val unread = submit(receipt(*both, versions = mapOf(viaje to 2L)))
        val extra = submit(receipt(aporte(1_000), versions = mapOf(viaje to 2L, motoId to 1L)))

        assertEquals(Rejected(CommandRejection.VersionConflict(1)), changed)
        assertEquals(Rejected(Specific(GoalRejection.GoalMissing)), gone)
        assertEquals(Rejected(Specific(GoalRejection.VersionsUnmatched)), unread)
        assertEquals(Rejected(Specific(GoalRejection.VersionsUnmatched)), extra)
        assertUnchanged(before)

        ledger.goals.submit(CompleteGoal(ledger.operation(), 1, motoId, 1, 10.seconds))
        val completed = snapshot()
        assertEquals(Rejected(Specific(GoalRejection.Completed)), submit(receipt(*both)))
        assertUnchanged(completed)
    }

    @Test
    fun aReceiptOfTwoGoalsIsFoundAfterARestartAndItsReplayIsTheSame() = runBlocking {
        started()
        moto()
        val command = receipt(aporte(750_000), aporte(300_000, goal = motoId), retiro(1_000))
        val first = committed(submit(command))

        ledger.reopen()

        val found = (ledger.goals.find(command.operationId) as Outcome.Success).value!!
        assertEquals(first.receipt to viaje, found.receipt to found.goalId)
        assertEquals(listOf(ReceiptGoal(viaje, null), ReceiptGoal(motoId, null)), found.goals)
        assertNull(found.movementIds)
        assertEquals(Committed(replayOf(first), replayed = true), submit(command))
        assertEquals(
            Rejected(CommandRejection.OperationConflict),
            submit(command.copy(versions = mapOf(viaje to 1L, motoId to 2L)))
        )
        assertEquals(
            Rejected(CommandRejection.OperationConflict),
            submit(command.copy(lines = command.lines.map { it.copy(goalId = viaje) }))
        )
        // 750.000 − 1.000 = 749.000 in Viaje and 300.000 in Moto, written once.
        assertEquals(Money.cop(749_000), summary(viaje).progress.saved)
        assertEquals(Money.cop(300_000), summary(motoId).progress.saved)
        assertEquals(3L, ledger.revision())
    }

    @Test
    fun aReceiptStoredBySello128sCommandIsStillFound() = runBlocking {
        started()
        // As SELLO-128 stored it: its one goal as the subject, under its own input identity.
        val operation = ledger.operation(5_000)
        ledger.database.operationReceiptDao().insert(
            OperationReceiptEntity(
                operationId = operation.value,
                kind = OperationKind.RecordGoalMovements.key,
                inputDigest = "b".repeat(64),
                generation = 1,
                revision = 1,
                subjectId = uuid(100),
                committedAt = 0
            )
        )

        val found = (ledger.goals.find(operation) as Outcome.Success).value!!

        assertEquals(
            CommittedGoal(
                OperationReceipt(operation, OperationKind.RecordGoalMovements, 1, 1, Instant.EPOCH),
                viaje,
                null,
                null,
                listOf(ReceiptGoal(viaje, null))
            ),
            found
        )
        // The same identifier with another input is not written again.
        assertEquals(
            Rejected(CommandRejection.OperationConflict),
            submit(receipt(aporte(1_000), operation = operation))
        )
    }
}
