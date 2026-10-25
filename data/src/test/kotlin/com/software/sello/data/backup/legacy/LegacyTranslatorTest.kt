package com.software.sello.data.backup.legacy

import com.software.sello.data.backup.BackupBounds
import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.uuid
import com.software.sello.data.backup.value
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Occurrence
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.SchedulePause
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.RestoreRejection
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Translating FinanceTracker backups (ADR 0019, docs/data/legacy-export-format.md).
 * Every expected value and count is written out by hand from the fixture, never
 * computed with the rules under test.
 */
class LegacyTranslatorTest {
    private val bogota = ZoneId.of("America/Bogota")

    private fun translator(bounds: BackupBounds = BackupBounds()): LegacyTranslator {
        var next = 0
        return LegacyTranslator(ids = { uuid(++next) }, bounds = bounds)
    }

    private fun translate(text: String, bounds: BackupBounds = BackupBounds()) =
        translator(bounds).translate(text.toByteArray(), bogota)

    private fun translated(text: String): LegacyTranslation.Translated =
        translate(text) as? LegacyTranslation.Translated
            ?: throw AssertionError("expected a translation, got ${translate(text)}")

    private val mixed = String(
        requireNotNull(javaClass.getResourceAsStream("/legacy/financetracker-mixed.json"))
            .readBytes()
    )

    /** A minimal export with [parts] (`"key": value` pairs) on top of empty lists. */
    private fun legacy(vararg parts: String, version: String? = null): String {
        val given = parts.associate { it.substringBefore(':').trim().trim('"') to it }
        val defaults = listOf(
            "exportedAt" to "\"exportedAt\": \"2026-10-25 14:01\"",
            "preferences" to "\"preferences\": {\"themeMode\": \"SYSTEM\", " +
                "\"homeSortField\": \"ALPHABETICAL\", \"homeSortDirection\": \"ASC\"}",
            "categories" to "\"categories\": []",
            "expenses" to "\"expenses\": []",
            "income" to "\"income\": []"
        )
        val keys = defaults.map { it.first } + given.keys.filter { key ->
            defaults.none { it.first == key }
        }
        val body = keys.map { key -> given[key] ?: defaults.first { it.first == key }.second }
        return "{" + listOfNotNull(version?.let { "\"version\": $it" }).plus(body)
            .joinToString(", ") + "}"
    }

    private fun category(id: Int, name: String, limit: String = "null") =
        "{\"id\": $id, \"name\": \"$name\", \"colorArgb\": -1, \"iconKey\": \"work\", " +
            "\"monthlyLimitCop\": $limit, \"updatedAt\": 1787000000000}"

    private fun BackupContents.categoryNames() = financial.categories.map { it.name.value }

    // The mixed fixture: every reason at least once.

    @Test
    fun `the mixed export keeps what fits and counts everything else by reason`() {
        val result = translated(mixed)

        assertEquals(
            listOf(
                LegacyImportLine(LegacyRecord.Category, LegacyReason.Shortened, 1),
                LegacyImportLine(LegacyRecord.Category, LegacyReason.Renamed, 1),
                LegacyImportLine(LegacyRecord.Category, LegacyReason.InvalidText, 1),
                LegacyImportLine(LegacyRecord.Limit, LegacyReason.AmountOutOfRange, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.Shortened, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.Unreadable, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.DuplicateId, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.AmountOutOfRange, 2),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.InvalidDate, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.DateAfterExport, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.InvalidText, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.CategoryMissing, 1),
                LegacyImportLine(LegacyRecord.Expense, LegacyReason.CategoryLeftOut, 1),
                LegacyImportLine(LegacyRecord.RepeatingEntry, LegacyReason.CategoryLeftOut, 1),
                LegacyImportLine(LegacyRecord.RepeatingEntry, LegacyReason.UnknownRepeat, 1),
                LegacyImportLine(LegacyRecord.Goal, LegacyReason.AmountOutOfRange, 1),
                LegacyImportLine(LegacyRecord.Investment, LegacyReason.NotImportedYet, 2),
                LegacyImportLine(LegacyRecord.InvestmentEntry, LegacyReason.NotImportedYet, 3),
                LegacyImportLine(LegacyRecord.ExchangeRate, LegacyReason.NotImportedYet, 1)
            ),
            result.report.lines
        )
        val counts = result.contents.financial.counts
        assertEquals(5, counts.categories)
        assertEquals(5, counts.expenses)
        assertEquals(4, counts.incomes)
        assertEquals(1, counts.goals)
        assertEquals(3, counts.schedules)
    }

    @Test
    fun `categories keep names and icons, clashes are numbered and long names cut`() {
        val categories = translated(mixed).contents.financial.categories

        assertEquals(
            listOf("Mercado", "Ocio", "mercado_2", "Una categoría de nombre", "Carro"),
            categories.map { it.name.value }
        )
        assertEquals(
            listOf("shopping_cart", "celebrate", "work", "checkroom", "directions_car"),
            categories.map { it.icon.value }
        )
        assertTrue(categories.none { it.archived })
        assertEquals(Instant.ofEpochMilli(1786999692267), categories[0].createdAt)
        assertEquals(Instant.ofEpochMilli(1786999692267), categories[0].updatedAt)
    }

    @Test
    fun `each limit is a default from the earliest imported month and a bad one is left out`() {
        val financial = translated(mixed).contents.financial
        val byName = financial.categories.associate { it.id to it.name.value }

        // The earliest kept record is the June income; the negative limit of Carro is gone.
        assertEquals(
            mapOf(
                "Mercado" to AutomaticBudget.Limit(finite(500_000)),
                "Ocio" to AutomaticBudget.Limit(BudgetLimit.Unlimited),
                "mercado_2" to AutomaticBudget.Limit(finite(100_000)),
                "Una categoría de nombre" to AutomaticBudget.Limit(finite(200_000))
            ),
            financial.defaultLimits.associate { byName.getValue(it.categoryId) to it.budget }
        )
        assertTrue(financial.defaultLimits.all { it.effectiveMonth == YearMonth.of(2026, 6) })
        assertTrue(financial.monthLimits.isEmpty())
    }

    @Test
    fun `expenses keep amount, day and note in the old order, with a cut note`() {
        val financial = translated(mixed).contents.financial
        val byName = financial.categories.associate { it.id to it.name.value }

        assertEquals(
            listOf(
                Expected("Mercado", "2026-09-03", 1, 25_000, "Pan"),
                Expected("Mercado", "2026-10-02", 2, 120_000, null),
                Expected("Ocio", "2026-10-10", 3, 60_000, "Cine"),
                Expected(
                    "mercado_2",
                    "2026-10-11",
                    4,
                    8_000,
                    "Una nota que pasa de sesenta caracteres y hay que cortarla a"
                ),
                Expected("Una categoría de nombre", "2026-10-20", 5, 30_000, "Camisa")
            ),
            financial.expenses.map {
                Expected(
                    byName.getValue(it.categoryId),
                    it.date.toString(),
                    it.sequence,
                    it.amount.money.minorUnits,
                    it.note?.value
                )
            }
        )
        val first = financial.expenses.first()
        assertEquals(Instant.parse("2026-09-03T05:00:00Z"), first.createdAt)
        assertEquals(first.createdAt, first.updatedAt)
        assertTrue(financial.expenses.all { it.origin == null })
    }

    private data class Expected(
        val category: String,
        val date: String,
        val sequence: Long,
        val pesos: Long,
        val note: String?
    )

    @Test
    fun `incomes map sources and keep a link only on a handled occurrence of a kept schedule`() {
        val financial = translated(mixed).contents.financial
        val salary = financial.schedules.single {
            it.schedule.entry is ScheduledEntry.Income
        }.schedule.id

        assertEquals(
            listOf("2026-06-15", "2026-08-01", "2026-09-24", "2026-10-01"),
            financial.incomes.map { it.date.toString() }
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), financial.incomes.map { it.sequence })
        assertEquals(
            listOf(
                IncomeSource.Other(
                    com.software.sello.domain.model.SourceName.of("Prima").value()
                ),
                IncomeSource.Salary,
                IncomeSource.Salary,
                IncomeSource.Salary
            ),
            financial.incomes.map { it.source }
        )
        assertEquals(
            listOf(null, "Nómina", "Mensual", "Mensual"),
            financial.incomes.map { it.note?.value }
        )
        assertEquals(
            listOf(null, null, null, Occurrence(LocalDate.of(2026, 10, 1), 1)),
            financial.incomes.map { it.origin?.occurrence }
        )
        assertEquals(salary, financial.incomes.last().origin?.schedule)
    }

    @Test
    fun `repeating entries continue where the old app stopped`() {
        val financial = translated(mixed).contents.financial
        val byName = financial.categories.associate { it.id to it.name.value }
        val schedules = financial.schedules.sortedBy { it.sequence }

        // Streaming: the 31st of each month (30 September in a short month), inactive.
        val streaming = schedules[0]
        val payment = streaming.schedule.entry as ScheduledEntry.Payment
        assertEquals("Ocio", byName.getValue(payment.categoryId))
        assertEquals(45_000L, payment.amount.money.minorUnits)
        assertEquals("Streaming", payment.note?.value)
        assertEquals(RepeatRule.Monthly.of(31).value(), streaming.schedule.recurrence.rule)
        assertEquals(LocalDate.of(2026, 8, 31), streaming.schedule.recurrence.start)
        assertEquals(
            listOf(SchedulePause(LocalDate.of(2026, 10, 31), null)),
            streaming.schedule.recurrence.pauses
        )
        assertEquals(Occurrence(LocalDate.of(2026, 9, 30), 1), streaming.handled)

        // Plaza: weekly from 1 October; the 29th is next, so the 22nd was the last done.
        val plaza = schedules[1]
        assertEquals(RepeatRule.EveryWeeks.of(1).value(), plaza.schedule.recurrence.rule)
        assertEquals(Occurrence(LocalDate.of(2026, 10, 22), 1), plaza.handled)
        assertTrue(plaza.schedule.recurrence.pauses.isEmpty())

        // Salary: the old MONTHLY is the 1st of each month, whatever the start.
        val salary = schedules[2]
        val income = salary.schedule.entry as ScheduledEntry.Income
        assertEquals(IncomeSource.Salary, income.source)
        assertEquals(7_000_000L, income.amount.money.minorUnits)
        assertEquals(RepeatRule.Monthly.of(1).value(), salary.schedule.recurrence.rule)
        assertEquals(LocalDate.of(2026, 9, 24), salary.schedule.recurrence.start)
        assertEquals(Occurrence(LocalDate.of(2026, 10, 1), 1), salary.handled)
    }

    @Test
    fun `a goal keeps its saved amount as one contribution on the export day`() {
        val history = translated(mixed).contents.financial.goals.single()
        val exportDay = LocalDate.of(2026, 10, 25)

        assertEquals("Viaje", history.goal.name.value)
        assertEquals(YearMonth.of(2027, 3), history.goal.targetMonth)
        assertNull(history.goal.completedOn)
        assertEquals(2, history.events.size)
        val target = history.events[0] as GoalTargetChange
        assertEquals(18_000_000L, target.target.money.minorUnits)
        assertEquals(exportDay, target.recordedOn)
        val saved = history.events[1] as GoalMovement
        assertEquals(GoalMovementKind.Contribution, saved.kind)
        assertEquals(6_500_000L, saved.amount.money.minorUnits)
        assertEquals(exportDay, saved.date)
        assertEquals(exportDay, saved.recordedOn)
        assertTrue(saved.sequence > target.sequence)
    }

    @Test
    fun `the export moment, zone and preferences come across`() {
        val contents = translated(mixed).contents

        assertEquals(Instant.parse("2026-10-25T19:01:00Z"), contents.exportedAt)
        assertEquals(bogota, contents.financial.zone)
        assertEquals(
            PortablePreferences(
                AppearancePreference(ThemePreference.Dark, InkPreference.Cobalto),
                CategoryOrderingPreference(
                    CategoryOrder.AmountSpent,
                    CategoryOrderDirection.Descending
                )
            ),
            contents.preferences
        )
    }

    @Test
    fun `the released validator accepts the translation unchanged`() {
        val contents = translated(mixed).contents
        val codec = BackupCodec()

        val bytes = codec.encode(contents).value()

        assertEquals(contents, codec.read(bytes).value().contents)
    }

    // Recognising the file.

    @Test
    fun `what is not a FinanceTracker backup is left to the Sello reader`() {
        val others = listOf(
            "not json",
            "[]",
            "{\"format\": \"sello-backup\", \"version\": 3}",
            "{\"exportedAt\": \"2026-10-25 14:01\"}",
            legacy(version = "\"2\"")
        )
        for (text in others) {
            assertEquals(text, LegacyTranslation.NotLegacy, translate(text))
        }
    }

    @Test
    fun `versions 1 and 2 are read and a newer one is refused whole`() {
        assertTrue(translate(legacy(version = "1")) is LegacyTranslation.Translated)
        assertTrue(translate(legacy(version = "2")) is LegacyTranslation.Translated)
        assertEquals(
            LegacyTranslation.Rejected(RestoreRejection.NewerVersion),
            translate(legacy(version = "3"))
        )
    }

    @Test
    fun `an export moment that is not a date and time refuses the whole file`() {
        assertEquals(
            LegacyTranslation.Rejected(RestoreRejection.InvalidContent),
            translate(legacy("\"exportedAt\": \"ayer\""))
        )
    }

    @Test
    fun `a file beyond the structural bounds is refused whole`() {
        val bounds = BackupBounds(maxRecords = 2)
        val text = legacy(
            "\"categories\": [${category(1, "A")}, ${category(2, "B")}, ${category(3, "C")}]"
        )

        assertEquals(
            LegacyTranslation.Rejected(RestoreRejection.TooLarge),
            translate(text, bounds)
        )
    }

    @Test
    fun `an empty export translates to an empty history with nothing to report`() {
        val result = translated(legacy())

        assertTrue(result.report.lines.isEmpty())
        assertEquals(0, result.contents.financial.counts.categories)
        assertTrue(result.contents.financial.defaultLimits.isEmpty())
    }

    // Names.

    @Test
    fun `clashing names take the first free number after names already in use`() {
        val text = legacy(
            "\"categories\": [" +
                listOf(
                    category(1, "Comida"),
                    category(2, "comida"),
                    category(3, "COMIDA"),
                    category(4, "Comida_2"),
                    category(5, "Cómida")
                ).joinToString(", ") + "]"
        )

        val result = translated(text)

        assertEquals(
            listOf("Comida", "comida_3", "COMIDA_4", "Comida_2", "Cómida_5"),
            result.contents.categoryNames()
        )
        assertEquals(
            listOf(LegacyImportLine(LegacyRecord.Category, LegacyReason.Renamed, 3)),
            result.report.lines
        )
    }

    @Test
    fun `a clashing name that is already full is cut to leave room for its number`() {
        val full = "Abcdefghijklmnopqrstuvwx" // 24 code points
        val text = legacy(
            "\"categories\": [${category(1, full)}, ${category(2, full.lowercase())}]"
        )

        assertEquals(
            listOf(full, "abcdefghijklmnopqrstuv_2"),
            translated(text).contents.categoryNames()
        )
    }

    @Test
    fun `text is cut by code points, never inside a character`() {
        val emoji = "😀" // one code point, two UTF-16 units
        val note = "a".repeat(59) + emoji + "b"
        val text = legacy(
            "\"categories\": [${category(1, "A")}]",
            "\"expenses\": [{\"id\": 1, \"categoryId\": 1, \"amountCop\": 1000, " +
                "\"description\": \"$note\", \"date\": \"2026-10-01\"}]"
        )

        val kept = translated(text).contents.financial.expenses.single().note?.value

        assertEquals("a".repeat(59) + emoji, kept)
    }

    @Test
    fun `old source labels become Sello sources and anything else is Otro, cut to fit`() {
        val sources = listOf(
            "Salario",
            "Ingresos pasivos",
            "Freelance",
            "Transferencia",
            "Otro",
            "Un ingreso con nombre muy largo"
        )
        val text = legacy(
            "\"income\": [" + sources.mapIndexed { index, source ->
                "{\"id\": ${index + 1}, \"amountCop\": 1000, \"source\": \"$source\", " +
                    "\"date\": \"2026-10-01\"}"
            }.joinToString(", ") + "]"
        )

        val result = translated(text)

        assertEquals(
            listOf(
                "salary",
                "passive_income",
                "freelance",
                "transfer",
                "other:Otro",
                "other:Un ingreso con nombre mu"
            ),
            result.contents.financial.incomes.map {
                val other = it.source as? IncomeSource.Other
                if (other == null) it.source.key else "other:${other.name.value}"
            }
        )
        assertEquals(
            listOf(LegacyImportLine(LegacyRecord.Income, LegacyReason.Shortened, 1)),
            result.report.lines
        )
    }

    // Repeat rules.

    @Test
    fun `each old repeat rule has its Sello rule`() {
        val types = listOf("DAILY", "BIWEEKLY", "CUSTOM:10", "MONTHLY_ON_DAY", "CUSTOM:0")
        val text = legacy(
            "\"recurringIncome\": [" + types.mapIndexed { index, type ->
                "{\"id\": ${index + 1}, \"amountCop\": 1000, \"source\": \"Salario\", " +
                    "\"recurrenceType\": \"$type\", \"startDate\": \"2026-10-12\", " +
                    "\"nextDueDate\": \"2026-10-20\"}"
            }.joinToString(", ") + "]"
        )

        val result = translated(text)
        val schedules = result.contents.financial.schedules.sortedBy { it.sequence }

        assertEquals(
            listOf(
                RepeatRule.EveryDays.of(1).value(),
                RepeatRule.TwiceMonthly.of(1, 15).value(),
                RepeatRule.EveryDays.of(10).value(),
                RepeatRule.Monthly.of(12).value()
            ),
            schedules.map { it.schedule.recurrence.rule }
        )
        // Daily: the 19th; twice a month: the 15th; every 10 days: the 12th itself;
        // monthly on the 12th: the 12th itself.
        assertEquals(
            listOf("2026-10-19", "2026-10-15", "2026-10-12", "2026-10-12"),
            schedules.map { it.handled?.date.toString() }
        )
        assertEquals(
            listOf(LegacyImportLine(LegacyRecord.RepeatingEntry, LegacyReason.UnknownRepeat, 1)),
            result.report.lines
        )
    }

    @Test
    fun `a schedule whose next date is its first occurrence has handled nothing`() {
        val text = legacy(
            "\"recurringIncome\": [{\"id\": 1, \"amountCop\": 1000, \"source\": \"Salario\", " +
                "\"recurrenceType\": \"MONTHLY\", \"startDate\": \"2026-10-12\", " +
                "\"nextDueDate\": \"2026-11-01\"}]"
        )

        assertNull(translated(text).contents.financial.schedules.single().handled)
    }

    @Test
    fun `a next date before the start leaves the schedule out`() {
        val text = legacy(
            "\"recurringIncome\": [{\"id\": 1, \"amountCop\": 1000, \"source\": \"Salario\", " +
                "\"recurrenceType\": \"MONTHLY\", \"startDate\": \"2026-10-12\", " +
                "\"nextDueDate\": \"2026-09-01\"}]"
        )

        assertEquals(
            listOf(LegacyImportLine(LegacyRecord.RepeatingEntry, LegacyReason.InvalidDate, 1)),
            translated(text).report.lines
        )
    }

    // Goals and preferences.

    @Test
    fun `an achieved goal is completed on the export day and nothing saved adds no movement`() {
        val text = legacy(
            "\"goals\": [{\"id\": 1, \"name\": \"Moto\", \"targetAmountCop\": 5000000, " +
                "\"deadlineDate\": \"2026-12-31\", \"colorArgb\": -1, \"isAchieved\": true}]"
        )

        val goal = translated(text).contents.financial.goals.single()

        assertEquals(LocalDate.of(2026, 10, 25), goal.goal.completedOn)
        assertEquals(1, goal.events.size)
    }

    @Test
    fun `an unknown preference keeps the current preferences`() {
        val text = legacy(
            "\"preferences\": {\"themeMode\": \"NEON\", \"homeSortField\": \"ALPHABETICAL\", " +
                "\"homeSortDirection\": \"ASC\"}"
        )

        assertNull(translated(text).contents.preferences)
    }

    private fun finite(pesos: Long): BudgetLimit = BudgetLimit.finite(Money.cop(pesos)).value()
}
