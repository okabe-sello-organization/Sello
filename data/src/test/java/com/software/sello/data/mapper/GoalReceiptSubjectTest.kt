package com.software.sello.data.mapper

import com.software.sello.data.local.entity.OperationReceiptEntity
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.OperationKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.StorageFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A receipt of goal movements keeps its goals in its subject, in the order its lines
 * first name them (SELLO-139). A receipt stored by SELLO-128's command, with its one goal
 * there, reads the same way; anything else stored there is a damaged receipt.
 */
class GoalReceiptSubjectTest {
    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun receipt(kind: OperationKind, subject: String?) = OperationReceiptEntity(
        operationId = id(9),
        kind = kind.key,
        inputDigest = "a".repeat(64),
        generation = 1,
        revision = 3,
        subjectId = subject,
        committedAt = 1_000
    )

    private val damaged =
        Outcome.Failure(StorageFailure.Integrity("operation_receipt", id(9), "subject_id"))

    private fun <T> Outcome<T, *>.value(): T = (this as Outcome.Success).value

    private fun goal(number: Int) = GoalId.of(id(number)).value()

    @Test
    fun theKindKeepsTheKeySello128StoredItUnder() {
        assertEquals("goal.record_movements", OperationKind.RecordGoalMovements.key)
    }

    @Test
    fun aReceiptStoredWithOneGoalReadsBackThatGoal() {
        val stored = receipt(OperationKind.RecordGoalMovements, id(100)).toStored().value()

        assertEquals(listOf(goal(100)), stored.goalIds)
        assertNull(stored.goalId)
    }

    @Test
    fun aReceiptOfSeveralGoalsReadsBackItsGoalsInTheirOrder() {
        val stored = receipt(OperationKind.RecordGoalMovements, "${id(102)},${id(100)}")
            .toStored().value()

        assertEquals(listOf(goal(102), goal(100)), stored.goalIds)
    }

    @Test
    fun anyOtherGoalKindStillHasItsOneGoal() {
        val stored = receipt(OperationKind.RecordGoalMovement, id(100)).toStored().value()

        assertEquals(goal(100), stored.goalId)
        assertEquals(emptyList<GoalId>(), stored.goalIds)
        assertEquals(
            damaged,
            receipt(OperationKind.RecordGoalMovement, "${id(100)},${id(101)}").toStored()
        )
    }

    @Test
    fun aMissingEmptyRepeatedOrMalformedGoalIsADamagedReceipt() {
        listOf(null, "", "${id(100)},${id(100)}", "${id(100)},", "trip").forEach { subject ->
            assertEquals(
                subject,
                damaged,
                receipt(OperationKind.RecordGoalMovements, subject).toStored()
            )
        }
    }
}
