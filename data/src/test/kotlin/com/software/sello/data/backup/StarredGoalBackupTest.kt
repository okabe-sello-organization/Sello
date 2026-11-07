package com.software.sello.data.backup

import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.Outcome
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Starred goals in backups (SELLO-143). Version 7 holds up to five in
 * `financial.starredGoals`; versions 2 to 6 hold one chosen goal in `featuredGoal`, read
 * as its one star unless that goal is completed. A version 7 file that breaks a rule is
 * refused, never trimmed. The goal is valid-goals-v2.json's MT07 goal (…301).
 */
class StarredGoalBackupTest {
    private val codec = BackupCodec()
    private val version2 = String(resource("valid-goals-v2.json"))
    private val viaje = goalId(301)

    /**
     * The same data as Sello writes it now: version 9, which keeps version 7's stars, MT07's
     * goal starred.
     */
    private val version7 = String(codec.encode(read(version2).contents).value())

    private fun goalId(n: Int) = GoalId.of("00000000-0000-4000-8000-%012d".format(n)).value()

    private fun read(text: String): BackupPlan = codec.read(text.toByteArray()).value()

    private fun rejection(text: String): BackupRejection =
        when (val read = codec.read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    private fun String.once(from: String, to: String): String {
        assertEquals("'$from' must occur once", 1, split(from).size - 1)
        return replace(from, to)
    }

    private fun String.completed() =
        once("\"completedOn\": null", "\"completedOn\": \"2026-10-21\"")

    private val starred = "\"starredGoals\": [\n      \"${viaje.value}\"\n    ]"

    private fun String.starring(vararg ids: String) = once(
        starred,
        "\"starredGoals\": [\n" + ids.joinToString(",\n") { "      \"$it\"" } + "\n    ]"
    )

    /** [version7] as version 6 wrote it: no ink hue, and the one star as the chosen goal. */
    private fun asVersion6(text: String) = text.once("\"version\": 9,", "\"version\": 6,")
        .once("\n    \"inkHue\": null,", "")
        .once(starred, "\"featuredGoal\": \"${viaje.value}\"")

    @Test
    fun `a version 2 to 6 file's chosen goal reads as its one star`() {
        assertTrue(version7.contains(starred))
        assertEquals(setOf(viaje), read(version2).contents.financial.starredGoals)
        assertEquals(setOf(viaje), read(asVersion6(version7)).contents.financial.starredGoals)
        assertEquals(read(version2).contents, read(version7).contents)
    }

    @Test
    fun `a version 2 to 6 file's chosen goal that is completed is dropped, not refused`() {
        for (text in listOf(version2.completed(), asVersion6(version7).completed())) {
            val financial = read(text).contents.financial

            assertEquals(emptySet<GoalId>(), financial.starredGoals)
            assertEquals(LocalDate.of(2026, 10, 21), financial.goals.single().goal.completedOn)
        }
    }

    @Test
    fun `a version 7 star on a completed goal is refused`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.starredGoals[0]"),
            rejection(version7.completed())
        )
    }

    @Test
    fun `a version 7 star on an unknown goal, twice on one goal or malformed is refused`() {
        assertEquals(
            BackupRejection.DanglingReference("financial.starredGoals[0]"),
            rejection(version7.starring(goalId(999).value))
        )
        assertEquals(
            BackupRejection.DuplicateId("financial.starredGoals[1]"),
            rejection(version7.starring(viaje.value, viaje.value))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.starredGoals[0]"),
            rejection(version7.starring("Viaje"))
        )
    }

    @Test
    fun `each version names its stars with its own key only`() {
        val featuredIn7 = version7.once(starred, "\"featuredGoal\": \"${viaje.value}\"")
        val starsIn6 = asVersion6(version7).once(
            "\"featuredGoal\": \"${viaje.value}\"",
            starred
        )

        assertTrue(rejection(featuredIn7) is BackupRejection.Malformed)
        assertTrue(rejection(starsIn6) is BackupRejection.Malformed)
    }

    /** MT07's goal again as goal 400 + [n], with movements of its own. */
    private fun copy(history: GoalHistory, n: Int) = GoalHistory(
        history.goal.copy(id = goalId(400 + n)),
        history.events.map { event ->
            if (event is GoalMovement) {
                event.copy(
                    id = GoalMovementId.of(
                        "00000000-0000-4000-8000-%012d".format(500 + 10 * n + event.sequence)
                    ).value()
                )
            } else {
                event
            }
        }
    )

    private fun withStars(count: Int): BackupContents {
        val contents = read(version2).contents
        val goals = (1..count).map { copy(contents.financial.goals.single(), it) }
        return contents.copy(
            financial = contents.financial.copy(
                goals = goals,
                // Given last created first: the file still writes them by identifier.
                starredGoals = goals.reversed().map { it.goal.id }.toSet()
            )
        )
    }

    @Test
    fun `five stars are written by identifier and read back as they were`() {
        val contents = withStars(5)
        val written = String(codec.encode(contents).value())

        assertEquals(contents, read(written).contents)
        assertTrue(
            written.contains(
                "\"starredGoals\": [\n" +
                    (401..405).joinToString(",\n") { "      \"${goalId(it).value}\"" } +
                    "\n    ]"
            )
        )
    }

    @Test
    fun `six stars are refused whole, never trimmed to five`() {
        val five = String(codec.encode(withStars(5)).value())
        val sixth = copy(read(version2).contents.financial.goals.single(), 6)
        val six = withStars(6)

        assertEquals(
            Outcome.Failure(BackupRejection.InvalidValue("financial.starredGoals")),
            codec.encode(six)
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.starredGoals"),
            rejection(
                five.once(
                    "      \"${goalId(405).value}\"\n",
                    "      \"${goalId(405).value}\",\n      \"${sixth.goal.id.value}\"\n"
                )
            )
        )
    }
}
