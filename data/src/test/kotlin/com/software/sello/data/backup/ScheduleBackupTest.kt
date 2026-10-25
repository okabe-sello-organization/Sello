package com.software.sello.data.backup

import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.Recurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.Schedule
import com.software.sello.domain.model.ScheduleId
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.port.StoredSchedule
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Schedules and entry origins in version 3 backups. The file holds the recurrence
 * examples' rent, weekly bus and twice-monthly salary (docs/testing/recurrence-examples.md,
 * RE05, RE10), the entries catch-up recorded with their origins, one manual expense,
 * a pause and an end. Every malformed case breaks one thing of that file.
 */
class ScheduleBackupTest {
    private val codec = BackupCodec()
    private val file = String(resource("valid-schedules-v3.json"))

    private fun id(number: Int) = "00000000-0000-4000-8000-%012d".format(number)

    private fun d(text: String) = LocalDate.parse(text)

    private fun pesos(amount: Long) =
        (TransactionAmount.of(Money.cop(amount)) as Outcome.Success).value

    private fun variant(from: String, to: String, occurrence: Int = 1): String {
        val parts = file.split(from)
        assertTrue("'$from' must occur at least $occurrence times", parts.size - 1 >= occurrence)
        return parts.take(occurrence).joinToString(from) + to +
            parts.drop(occurrence).joinToString(from)
    }

    private fun rejection(text: String): BackupRejection =
        when (val read = codec.read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    @Test
    fun `schedules read back with their pauses checkpoints and creation order`() {
        val plan = codec.read(file.toByteArray()).value()

        val rent = StoredSchedule(
            schedule = Schedule(
                ScheduleId.of(id(201)).value(),
                ScheduledEntry.Payment(
                    CategoryId.of(id(1)).value(),
                    pesos(950_000),
                    Note.of("Arriendo").value()
                ),
                Recurrence.of(RepeatRule.Monthly.of(5).value(), d("2026-09-01")).value()
            ),
            sequence = 0,
            handled = Occurrence(d("2026-10-05"), 1),
            version = 1,
            createdAt = Instant.parse("2026-09-01T12:10:00Z"),
            updatedAt = Instant.parse("2026-09-01T12:10:00Z")
        )
        val bus = StoredSchedule(
            schedule = Schedule(
                ScheduleId.of(id(202)).value(),
                ScheduledEntry.Payment(CategoryId.of(id(2)).value(), pesos(20_000), null),
                Recurrence.of(
                    RepeatRule.EveryWeeks.of(1).value(),
                    d("2026-09-26"),
                    null,
                    listOf(SchedulePause(d("2026-11-01"), null))
                ).value()
            ),
            sequence = 1,
            handled = Occurrence(d("2026-10-24"), 1),
            version = 1,
            createdAt = Instant.parse("2026-09-26T09:00:00Z"),
            updatedAt = Instant.parse("2026-10-24T18:00:00Z")
        )
        val salary = StoredSchedule(
            schedule = Schedule(
                ScheduleId.of(id(203)).value(),
                ScheduledEntry.Income(IncomeSource.Salary, pesos(800_000), null),
                Recurrence.of(
                    RepeatRule.TwiceMonthly.of(15, 30).value(),
                    d("2026-10-01"),
                    d("2026-12-31")
                ).value()
            ),
            sequence = 2,
            handled = Occurrence(d("2026-10-15"), 1),
            version = 1,
            createdAt = Instant.parse("2026-10-01T08:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T08:00:00Z")
        )
        assertEquals(listOf(rent, bus, salary), plan.contents.financial.schedules)
        assertEquals(3, plan.counts.schedules)
    }

    @Test
    fun `entries keep the occurrence that recorded them and a manual one has none`() {
        val financial = codec.read(file.toByteArray()).value().contents.financial

        assertEquals(
            listOf(
                EntryOrigin(ScheduleId.of(id(201)).value(), Occurrence(d("2026-10-05"), 1)),
                EntryOrigin(ScheduleId.of(id(202)).value(), Occurrence(d("2026-10-10"), 1)),
                EntryOrigin(ScheduleId.of(id(202)).value(), Occurrence(d("2026-10-17"), 1)),
                null,
                EntryOrigin(ScheduleId.of(id(202)).value(), Occurrence(d("2026-10-24"), 1))
            ),
            financial.expenses.map { it.origin }
        )
        assertEquals(
            EntryOrigin(ScheduleId.of(id(203)).value(), Occurrence(d("2026-10-15"), 1)),
            financial.incomes.single().origin
        )
    }

    @Test
    fun `the same contents always make the same file, as version 4`() {
        val plan = codec.read(file.toByteArray()).value()

        assertEquals(writtenAsVersion4(file), String(codec.encode(plan.contents).value()))
    }

    @Test
    fun `version 1 and 2 files still read, with no schedules and no origins`() {
        for (name in listOf("valid-g01.json", "valid-g01-v2.json", "valid-goals-v2.json")) {
            val financial = codec.read(resource(name)).value().contents.financial

            assertEquals(name, emptyList<StoredSchedule>(), financial.schedules)
            assertTrue(
                name,
                (
                    financial.expenses.map { it.origin } +
                        financial.incomes.map { it.origin }
                    ).all { it == null }
            )
        }
    }

    @Test
    fun `a version 3 file must say what each schedule is and how it repeats`() {
        val path = "financial.schedules"
        val cases = listOf(
            variant("\"kind\": \"payment\"", "\"kind\": \"gasto\"") to
                BackupRejection.InvalidValue("$path[0].kind"),
            variant(
                "\"categoryId\": \"00000000-0000-4000-8000-000000000001\",\n        \"source\": null",
                "\"categoryId\": \"00000000-0000-4000-8000-000000000001\",\n        \"source\": \"salary\""
            ) to BackupRejection.InvalidValue("$path[0].source"),
            variant(
                "\"categoryId\": null,\n        \"source\": \"salary\"",
                "\"categoryId\": \"00000000-0000-4000-8000-000000000001\",\n        \"source\": \"salary\""
            ) to BackupRejection.InvalidValue("$path[2].categoryId"),
            variant("\"repeat\": \"monthly\"", "\"repeat\": \"weekly\"") to
                BackupRejection.InvalidValue("$path[0].repeat"),
            variant("\"first\": 5,", "\"first\": 32,") to
                BackupRejection.InvalidValue("$path[0].first"),
            variant(
                "\"first\": 5,\n        \"second\": null",
                "\"first\": 5,\n        \"second\": 20"
            ) to
                BackupRejection.InvalidValue("$path[0].second"),
            variant("\"second\": 30", "\"second\": 15") to
                BackupRejection.InvalidValue("$path[2].second"),
            variant("\"second\": 30", "\"second\": \"30\"") to
                BackupRejection.InvalidValue("$path[2].second"),
            variant("\"end\": \"2026-12-31\"", "\"end\": \"2026-09-30\"") to
                BackupRejection.InvalidValue("$path[2].end"),
            variant(
                "\"amount\": 950000,\n        \"currency\": \"COP\",\n        \"note\": \"Arriendo\",\n        \"repeat\"",
                "\"amount\": 0,\n        \"currency\": \"COP\",\n        \"note\": \"Arriendo\",\n        \"repeat\""
            ) to
                BackupRejection.AmountOutOfRange("$path[0].amount"),
            variant(
                "\"categoryId\": \"00000000-0000-4000-8000-000000000002\",\n        \"source\": null",
                "\"categoryId\": \"00000000-0000-4000-8000-000000000009\",\n        \"source\": null"
            ) to BackupRejection.DanglingReference("$path[1].categoryId"),
            variant(
                "\"sequence\": 2,\n        \"handled\"",
                "\"sequence\": 1,\n        \"handled\""
            ) to
                BackupRejection.DuplicateEntry("$path[2].sequence"),
            variant(
                "\"id\": \"00000000-0000-4000-8000-000000000203\"",
                "\"id\": \"00000000-0000-4000-8000-000000000202\""
            ) to
                BackupRejection.DuplicateId("$path[2].id")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    @Test
    fun `pauses and checkpoints must fit their schedule`() {
        val path = "financial.schedules"
        val cases = listOf(
            variant(
                "\"pauses\": [\n          {\n            \"from\": \"2026-11-01\"",
                "\"pauses\": [\n          {\n            \"from\": \"2026-09-01\""
            ) to BackupRejection.InvalidValue("$path[1].pauses"),
            variant(
                "\"date\": \"2026-10-05\",\n          \"slot\": 1\n        },\n        \"pauses\"",
                "\"date\": \"2026-10-05\",\n          \"slot\": 2\n        },\n        \"pauses\""
            ) to
                BackupRejection.InvalidValue("$path[0].handled.slot")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    /** A schedule paying into an archived category must be paused, as archiving leaves it (rule 9). */
    @Test
    fun `a running payment into an archived category is refused`() {
        val archived = variant(
            "\"name\": \"Hogar\",\n        \"icon\": \"home\",\n        \"archived\": false",
            "\"name\": \"Hogar\",\n        \"icon\": \"home\",\n        \"archived\": true"
        )

        assertEquals(
            BackupRejection.InvalidValue("financial.schedules[0].pauses"),
            rejection(archived)
        )
    }

    @Test
    fun `an origin is recorded once and never past its schedules checkpoint`() {
        val cases = listOf(
            // The 17 October bus recorded twice.
            variant(
                "\"date\": \"2026-10-24\",\n          \"slot\": 1\n        }\n      }\n    ],\n    \"incomes\"",
                "\"date\": \"2026-10-17\",\n          \"slot\": 1\n        }\n      }\n    ],\n    \"incomes\""
            ) to BackupRejection.DuplicateEntry("financial.expenses[4].origin"),
            // A rent the checkpoint (5 October) says has not been recorded yet.
            variant(
                "\"schedule\": \"00000000-0000-4000-8000-000000000201\",\n          \"date\": \"2026-10-05\"",
                "\"schedule\": \"00000000-0000-4000-8000-000000000201\",\n          \"date\": \"2026-11-05\""
            ) to BackupRejection.InvalidValue("financial.expenses[0].origin"),
            variant(
                "\"slot\": 1\n        }\n      },\n      {\n        \"id\": \"00000000-0000-4000-8000-000000000102\"",
                "\"slot\": 3\n        }\n      },\n      {\n        \"id\": \"00000000-0000-4000-8000-000000000102\""
            ) to
                BackupRejection.InvalidValue("financial.expenses[0].origin.slot")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    @Test
    fun `an origin may name a schedule that was deleted since`() {
        val deleted = variant(
            "\"schedule\": \"00000000-0000-4000-8000-000000000201\",\n          \"date\": \"2026-10-05\"",
            "\"schedule\": \"00000000-0000-4000-8000-000000000299\",\n          \"date\": \"2026-10-05\""
        )

        assertEquals(
            id(299),
            codec.read(
                deleted.toByteArray()
            ).value().contents.financial.expenses.first().origin?.schedule?.value
        )
    }
}
