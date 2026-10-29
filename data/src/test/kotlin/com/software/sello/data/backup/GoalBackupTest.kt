package com.software.sello.data.backup

import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalPlace
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.PlaceState
import com.software.sello.domain.policy.GoalPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goals in version 2 backups. The goal file is G01 plus one goal whose history is
 * approved example MT07 (docs/testing/goal-examples.md): 3.000.000 target, 1.710.000
 * saved on 2 October, 400.000 withdrawn on 20 October. Every malformed case breaks
 * one thing of that file.
 */
class GoalBackupTest {
    private val codec = BackupCodec()
    private val goals = String(resource("valid-goals-v2.json"))

    private fun variant(from: String, to: String): String {
        assertEquals("'$from' must occur once in the fixture", 1, goals.split(from).size - 1)
        return goals.replace(from, to)
    }

    private fun rejection(text: String): BackupRejection =
        when (val read = codec.read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    @Test
    fun `a goal and its history read back to the approved figures`() {
        val plan = codec.read(goals.toByteArray()).value()
        val history = plan.contents.financial.goals.single()

        val progress = (GoalPolicy.progress(history) as Outcome.Success).value
        assertEquals(Money.cop(1_310_000), progress.saved)
        assertEquals(43L, progress.percentage)
        assertEquals(PlaceState.Kept(LocalDate.of(2026, 10, 2)), progress.state(GoalPlace.Half))
        assertEquals(listOf(0L, 1L, 3L), history.events.map { it.sequence })
        assertTrue(history.events.first() is GoalTargetChange)
        assertEquals(1L, history.goal.version)
        assertTrue(history.events.filterIsInstance<GoalMovement>().all { it.version == 1L })
        assertEquals(
            GoalId.of("00000000-0000-4000-8000-000000000301").value(),
            plan.contents.financial.featuredGoal
        )
        assertEquals(1 to 3, plan.counts.goals to plan.counts.goalEvents)
    }

    @Test
    fun `a file with goals is written back byte for byte, as version 6`() {
        val plan = codec.read(goals.toByteArray()).value()

        assertEquals(
            writtenAsVersion6(String(resource("valid-goals-v3.json"))),
            String(codec.encode(plan.contents).value())
        )
    }

    @Test
    fun `a version 1 file still reads, holding no goals`() {
        val plan = codec.read(resource("valid-g01.json")).value()

        assertEquals(g01(), plan.contents)
        assertEquals(emptyList<Any>(), plan.contents.financial.goals)
        assertNull(plan.contents.financial.featuredGoal)
    }

    @Test
    fun `every version 2 key is required and version 1 takes none of them`() {
        val noFeatured = variant(
            "    \"featuredGoal\": \"00000000-0000-4000-8000-000000000301\"\n",
            ""
        ).replace("    ],\n  },", "    ]\n  },")
        assertTrue(rejection(noFeatured) is BackupRejection.Malformed)

        val v1WithGoals = variant("\"version\": 2,", "\"version\": 1,")
        assertTrue(rejection(v1WithGoals) is BackupRejection.Malformed)
    }

    @Test
    fun `a withdrawal above what was saved at its point rejects the file`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].history[2].amount"),
            rejection(variant("\"amount\": 400000,", "\"amount\": 1710001,"))
        )
    }

    @Test
    fun `the history must be in recording order and begin with the target`() {
        assertEquals(
            BackupRejection.DuplicateEntry("financial.goals[0].history[2].sequence"),
            rejection(
                variant(
                    "\"sequence\": 3,\n            \"kind\": \"withdrawal\"",
                    "\"sequence\": 1,\n            \"kind\": \"withdrawal\""
                )
            )
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].history[0].kind"),
            rejection(variant("\"kind\": \"target\",", "\"kind\": \"contribution\","))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].history[1].kind"),
            rejection(variant("\"kind\": \"contribution\",", "\"kind\": \"gift\","))
        )
    }

    @Test
    fun `a target carries no identifier or date and a movement needs both`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].history[0].id"),
            rejection(
                variant(
                    "\"id\": null,\n            \"sequence\": 0,",
                    "\"id\": \"00000000-0000-4000-8000-000000000399\",\n            \"sequence\": 0,"
                )
            )
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].history[1].date"),
            rejection(
                variant(
                    "\"date\": \"2026-10-02\",\n            \"recordedOn\"",
                    "\"date\": null,\n            \"recordedOn\""
                )
            )
        )
    }

    @Test
    fun `amounts, names and references follow the app's own rules`() {
        assertEquals(
            BackupRejection.AmountOutOfRange("financial.goals[0].history[1].amount"),
            rejection(variant("\"amount\": 1710000,", "\"amount\": 0,"))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.goals[0].name"),
            rejection(variant("\"name\": \"Viaje a la costa\",", "\"name\": \" Viaje\","))
        )
        assertEquals(
            BackupRejection.DanglingReference("financial.featuredGoal"),
            rejection(
                variant(
                    "\"featuredGoal\": \"00000000-0000-4000-8000-000000000301\"",
                    "\"featuredGoal\": \"00000000-0000-4000-8000-000000000999\""
                )
            )
        )
        assertEquals(
            BackupRejection.DuplicateId("financial.goals[0].history[2].id"),
            rejection(
                variant(
                    "\"id\": \"00000000-0000-4000-8000-000000000303\",",
                    "\"id\": \"00000000-0000-4000-8000-000000000302\","
                )
            )
        )
    }
}
