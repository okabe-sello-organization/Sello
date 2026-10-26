package com.software.sello.data.backup.legacy

import com.software.sello.data.backup.BackupBounds
import com.software.sello.data.backup.BackupContents
import com.software.sello.data.backup.FinancialSnapshot
import com.software.sello.data.backup.PortablePreferences
import com.software.sello.data.backup.reason
import com.software.sello.data.backup.scanStructure
import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
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
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TextError
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.policy.SchedulePolicy
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StoredSchedule
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.temporal.ChronoUnit
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What [LegacyTranslator] made of a file. */
sealed interface LegacyTranslation {
    /** Not a FinanceTracker backup; the Sello reader's answer stands. */
    data object NotLegacy : LegacyTranslation

    /** A FinanceTracker backup that cannot be imported at all. Nothing changes. */
    data class Rejected(val reason: RestoreRejection) : LegacyTranslation

    /** Everything that fits, and what was changed or left out on the way. */
    data class Translated(val contents: BackupContents, val report: LegacyImportReport) :
        LegacyTranslation
}

/**
 * Translates a FinanceTracker backup into the contents of a Sello backup (ADR 0019).
 * It reads only the bytes it is given and writes nothing; a record that breaks a
 * Sello rule is left out and counted, and the only repairs are cutting text to fit
 * and numbering clashing category names. Every rule is in
 * docs/data/legacy-export-format.md.
 */
class LegacyTranslator(
    private val ids: RecordIdSource,
    private val bounds: BackupBounds = BackupBounds()
) {
    fun translate(bytes: ByteArray, zone: ZoneId): LegacyTranslation {
        if (bytes.size > bounds.maxBytes) {
            return LegacyTranslation.Rejected(RestoreRejection.TooLarge)
        }
        val text = utf8(bytes) ?: return LegacyTranslation.NotLegacy
        scanStructure(text, bounds)?.let { return LegacyTranslation.Rejected(it.reason()) }
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return LegacyTranslation.NotLegacy
        } catch (_: IllegalArgumentException) {
            return LegacyTranslation.NotLegacy
        } as? JsonObject ?: return LegacyTranslation.NotLegacy
        if (!recognised(root)) return LegacyTranslation.NotLegacy
        val version = when (val raw = root["version"]) {
            null -> 1L
            else -> raw.whole() ?: return LegacyTranslation.NotLegacy
        }
        if (version < 1) return LegacyTranslation.NotLegacy
        if (version >
            NEWEST_VERSION
        ) {
            return LegacyTranslation.Rejected(RestoreRejection.NewerVersion)
        }
        val exported = try {
            LocalDateTime.parse(
                (root.getValue("exportedAt") as JsonPrimitive).content,
                exportedFormat
            )
        } catch (_: DateTimeException) {
            return LegacyTranslation.Rejected(RestoreRejection.InvalidContent)
        }
        return Translation(root, zone, exported).run()
    }

    private fun recognised(root: JsonObject): Boolean {
        val exportedAt = root["exportedAt"] as? JsonPrimitive
        return "format" !in root &&
            exportedAt?.isString == true &&
            root["preferences"] is JsonObject &&
            listOf("categories", "expenses", "income").all { root[it] is JsonArray }
    }

    /** One file's translation; the tally and the identifier maps live as long as it does. */
    private inner class Translation(
        private val root: JsonObject,
        private val zone: ZoneId,
        exported: LocalDateTime
    ) {
        private val exportedAt = exported.atZone(zone).toInstant().truncatedTo(ChronoUnit.MILLIS)
        private val exportDay = exported.toLocalDate()
        private val tally = HashMap<Pair<LegacyRecord, LegacyReason>, Int>()

        private fun count(record: LegacyRecord, reason: LegacyReason, by: Int = 1) {
            if (by > 0) tally.merge(record to reason, by, Int::plus)
        }

        fun run(): LegacyTranslation {
            val categories = categories()
            val known = categories.associateBy { it.legacyId }
            val left = categoryIdsLeftOut
            val schedules = schedules(known, left)
            val expenses = expenses(known, left, schedules)
            val incomes = incomes(schedules)
            val goals = goals()
            notImportedYet()
            val from = (expenses.map { it.date } + incomes.map { it.date }).minOrNull()
                ?.let(YearMonth::from) ?: YearMonth.from(exportDay)
            val contents = BackupContents(
                exportedAt = exportedAt,
                financial = FinancialSnapshot(
                    zone = zone,
                    revision = 0,
                    categories = categories.map { it.category },
                    defaultLimits = categories.mapNotNull { kept ->
                        kept.limit?.let {
                            DefaultLimit(kept.category.id, from, AutomaticBudget.Limit(it))
                        }
                    },
                    monthLimits = emptyList(),
                    expenses = expenses,
                    incomes = incomes,
                    goals = goals,
                    featuredGoal = null,
                    schedules = schedules.map { it.stored },
                    investments = emptyList(),
                    usdRates = emptyList()
                ),
                preferences = preferences()
            )
            val lines = tally.map { (key, count) -> LegacyImportLine(key.first, key.second, count) }
                .sortedWith(compareBy({ it.record.ordinal }, { it.reason.ordinal }))
            return LegacyTranslation.Translated(contents, LegacyImportReport(lines))
        }

        // Categories.

        private val categoryIdsLeftOut = HashSet<Long>()

        private inner class KeptCategory(
            val legacyId: Long,
            val category: Category,
            val limit: BudgetLimit?
        )

        private fun categories(): List<KeptCategory> {
            class Candidate(
                val legacyId: Long,
                val name: CategoryName,
                val cut: Boolean,
                val icon: IconKey,
                val limit: BudgetLimit?,
                val at: Instant
            )
            val candidates = records(
                "categories",
                LegacyRecord.Category,
                leftOut = categoryIdsLeftOut
            ) { fields ->
                val legacyId = fields.long("id")
                val rawName = fields.text("name")
                val icon = fields.text("iconKey")
                val limit = fields.optionalLong("monthlyLimitCop")
                val at = fields.moment("updatedAt")
                val name = fitted(rawName, CategoryName.MAX_CODE_POINTS, CategoryName::of)
                    ?: return@records Skip(LegacyReason.InvalidText)
                val budget = when (limit) {
                    null -> BudgetLimit.Unlimited

                    else -> when (val finite = BudgetLimit.finite(Money.cop(limit))) {
                        is Outcome.Success -> finite.value

                        is Outcome.Failure -> {
                            count(LegacyRecord.Limit, LegacyReason.AmountOutOfRange)
                            null
                        }
                    }
                }
                Keep(
                    Candidate(
                        legacyId = legacyId,
                        name = name.value,
                        cut = name.cut,
                        icon = (IconKey.of(icon) as? Outcome.Success)?.value ?: FALLBACK_ICON,
                        limit = budget,
                        at = at
                    )
                )
            }
            // The first holder of each name keeps it, whatever comes later; the rest are
            // numbered with the first number free among every name in use.
            val used = HashSet<String>()
            val holders = candidates.filter { used.add(it.name.uniquenessKey) }.toSet()
            return candidates.map { candidate ->
                val name = if (candidate in holders) {
                    if (candidate.cut) count(LegacyRecord.Category, LegacyReason.Shortened)
                    candidate.name
                } else {
                    count(LegacyRecord.Category, LegacyReason.Renamed)
                    numbered(candidate.name, used)
                }
                KeptCategory(
                    legacyId = candidate.legacyId,
                    category = Category(
                        id = CategoryId.of(ids.next()).identifier(),
                        name = name,
                        icon = candidate.icon,
                        archived = false,
                        version = 1,
                        createdAt = candidate.at,
                        updatedAt = candidate.at
                    ),
                    limit = candidate.limit
                )
            }
        }

        private fun numbered(name: CategoryName, used: MutableSet<String>): CategoryName {
            var number = 2
            while (true) {
                val suffix = "_$number"
                val base = name.value.cutTo(CategoryName.MAX_CODE_POINTS - suffix.length)
                val candidate = (CategoryName.of(base.trimEnd() + suffix) as Outcome.Success).value
                if (used.add(candidate.uniquenessKey)) return candidate
                number++
            }
        }

        // Repeating entries.

        private inner class KeptSchedule(
            val stored: StoredSchedule,
            val kind: LegacyRecord,
            val legacyId: Long
        )

        private fun schedules(known: Map<Long, KeptCategory>, left: Set<Long>): List<KeptSchedule> {
            val payments = records("recurringExpenses", LegacyRecord.RepeatingEntry) { fields ->
                val legacyId = fields.long("id")
                val category = fields.long("categoryId")
                val amount = fields.long("amountCop")
                val note = fields.text("description")
                val repeat = fields.text("recurrenceType")
                val start = fields.text("startDate")
                val next = fields.text("nextDueDate")
                val active = fields.flag("isActive", default = true)
                val kept = known[category]
                    ?: return@records Skip(categoryReason(category, left))
                schedule(legacyId, amount, note, repeat, start, next, active) { amountValue, text ->
                    ScheduledEntry.Payment(kept.category.id, amountValue, text)
                }
            }
            val incomes = records("recurringIncome", LegacyRecord.RepeatingEntry) { fields ->
                val legacyId = fields.long("id")
                val amount = fields.long("amountCop")
                val source = fields.text("source")
                val note = fields.optionalText("notes")
                val repeat = fields.text("recurrenceType")
                val start = fields.text("startDate")
                val next = fields.text("nextDueDate")
                val active = fields.flag("isActive", default = true)
                val mapped = source(source) ?: return@records Skip(LegacyReason.InvalidText)
                schedule(legacyId, amount, note, repeat, start, next, active, mapped.cut) {
                        amountValue,
                        text
                    ->
                    ScheduledEntry.Income(mapped.value, amountValue, text)
                }
            }
            var sequence = 0L
            return (
                payments.map { LegacyRecord.Expense to it } +
                    incomes.map { LegacyRecord.Income to it }
                )
                .map { (kind, planned) ->
                    sequence++
                    KeptSchedule(
                        StoredSchedule(
                            schedule = Schedule(
                                ScheduleId.of(ids.next()).identifier(),
                                planned.entry,
                                planned.recurrence
                            ),
                            sequence = sequence,
                            handled = planned.handled,
                            version = 1,
                            createdAt = planned.createdAt,
                            updatedAt = planned.createdAt
                        ),
                        kind,
                        planned.legacyId
                    )
                }
        }

        private inner class PlannedSchedule(
            val legacyId: Long,
            val entry: ScheduledEntry,
            val recurrence: Recurrence,
            val handled: Occurrence?,
            val createdAt: Instant
        )

        @Suppress("LongParameterList")
        private fun schedule(
            legacyId: Long,
            amount: Long,
            rawNote: String,
            repeat: String,
            startText: String,
            nextText: String,
            active: Boolean,
            alreadyCut: Boolean = false,
            entry: (TransactionAmount, Note?) -> ScheduledEntry
        ): RecordResult<PlannedSchedule> {
            val value = transaction(amount) ?: return Skip(LegacyReason.AmountOutOfRange)
            val start = date(startText) ?: return Skip(LegacyReason.InvalidDate)
            val next = date(nextText) ?: return Skip(LegacyReason.InvalidDate)
            if (next.isBefore(start)) return Skip(LegacyReason.InvalidDate)
            val note = fitted(rawNote, Note.MAX_CODE_POINTS, Note::of)
                ?: return Skip(LegacyReason.InvalidText)
            val rule = rule(repeat, start) ?: return Skip(LegacyReason.UnknownRepeat)
            val pauses = if (active) emptyList() else listOf(SchedulePause(next, null))
            val recurrence = when (val built = Recurrence.of(rule, start, null, pauses)) {
                is Outcome.Success -> built.value
                is Outcome.Failure -> return Skip(LegacyReason.InvalidDate)
            }
            if (note.cut || alreadyCut) count(LegacyRecord.RepeatingEntry, LegacyReason.Shortened)
            return Keep(
                PlannedSchedule(
                    legacyId = legacyId,
                    entry = entry(value, note.value),
                    recurrence = recurrence,
                    // Everything before the next date was recorded by the old app.
                    handled = SchedulePolicy.occurrences(recurrence, start, next.minusDays(1))
                        .lastOrNull(),
                    createdAt = startOf(start)
                )
            )
        }

        private fun rule(type: String, start: LocalDate): RepeatRule? = when {
            type == "DAILY" -> RepeatRule.EveryDays.of(1).orNull()

            type == "WEEKLY" -> RepeatRule.EveryWeeks.of(1).orNull()

            type == "BIWEEKLY" -> RepeatRule.TwiceMonthly.of(1, 15).orNull()

            type == "MONTHLY" -> RepeatRule.Monthly.of(1).orNull()

            type == "MONTHLY_ON_DAY" -> RepeatRule.Monthly.of(start.dayOfMonth).orNull()

            type.startsWith(CUSTOM) -> type.removePrefix(CUSTOM)
                .takeIf { plainWhole.matches(it) }?.toIntOrNull()
                ?.let { RepeatRule.EveryDays.of(it).orNull() }

            else -> null
        }

        /**
         * The origin of an entry linked to [legacySchedule]: only an occurrence the kept
         * schedule has already handled, and only once.
         */
        private fun origin(
            schedules: List<KeptSchedule>,
            kind: LegacyRecord,
            legacySchedule: Long?,
            date: LocalDate,
            claimed: MutableSet<EntryOrigin>
        ): EntryOrigin? {
            val stored = schedules.firstOrNull { it.kind == kind && it.legacyId == legacySchedule }
                ?.stored ?: return null
            val handled = stored.handled ?: return null
            val occurrence = SchedulePolicy.occurrences(stored.schedule.recurrence, date, date)
                .firstOrNull() ?: return null
            if (occurrence.date.isAfter(handled.date)) return null
            if (occurrence.date == handled.date && occurrence.slot > handled.slot) return null
            return EntryOrigin(stored.schedule.id, occurrence).takeIf(claimed::add)
        }

        // Expenses and incomes.

        private fun expenses(
            known: Map<Long, KeptCategory>,
            left: Set<Long>,
            schedules: List<KeptSchedule>
        ): List<Expense> {
            class Planned(
                val legacyId: Long,
                val category: CategoryId,
                val date: LocalDate,
                val amount: TransactionAmount,
                val note: Note?,
                val link: Long?
            )
            val planned = records("expenses", LegacyRecord.Expense) { fields ->
                val legacyId = fields.long("id")
                val category = fields.long("categoryId")
                val amount = fields.long("amountCop")
                val note = fields.text("description")
                val date = fields.text("date")
                val link = fields.optionalLong("recurringExpenseId")
                val kept = known[category]
                    ?: return@records Skip(categoryReason(category, left))
                val value =
                    transaction(amount) ?: return@records Skip(LegacyReason.AmountOutOfRange)
                val day = when (val read = day(date)) {
                    is Keep -> read.value
                    is Skip -> return@records read
                }
                val text = fitted(note, Note.MAX_CODE_POINTS, Note::of)
                    ?: return@records Skip(LegacyReason.InvalidText)
                if (text.cut) count(LegacyRecord.Expense, LegacyReason.Shortened)
                Keep(Planned(legacyId, kept.category.id, day, value, text.value, link))
            }
            val claimed = HashSet<EntryOrigin>()
            return planned.sortedWith(compareBy({ it.date }, { it.legacyId }))
                .mapIndexed { index, it ->
                    Expense(
                        id = ExpenseId.of(ids.next()).identifier(),
                        categoryId = it.category,
                        date = it.date,
                        sequence = index + 1L,
                        amount = it.amount,
                        note = it.note,
                        version = 1,
                        createdAt = startOf(it.date),
                        updatedAt = startOf(it.date),
                        origin = origin(schedules, LegacyRecord.Expense, it.link, it.date, claimed)
                    )
                }
        }

        private fun incomes(schedules: List<KeptSchedule>): List<Income> {
            class Planned(
                val legacyId: Long,
                val date: LocalDate,
                val amount: TransactionAmount,
                val source: IncomeSource,
                val note: Note?,
                val link: Long?
            )
            val planned = records("income", LegacyRecord.Income) { fields ->
                val legacyId = fields.long("id")
                val amount = fields.long("amountCop")
                val source = fields.text("source")
                val date = fields.text("date")
                val note = fields.optionalText("notes")
                val link = fields.optionalLong("recurringIncomeId")
                val value =
                    transaction(amount) ?: return@records Skip(LegacyReason.AmountOutOfRange)
                val day = when (val read = day(date)) {
                    is Keep -> read.value
                    is Skip -> return@records read
                }
                val mapped = source(source) ?: return@records Skip(LegacyReason.InvalidText)
                val text = fitted(note, Note.MAX_CODE_POINTS, Note::of)
                    ?: return@records Skip(LegacyReason.InvalidText)
                if (mapped.cut || text.cut) count(LegacyRecord.Income, LegacyReason.Shortened)
                Keep(Planned(legacyId, day, value, mapped.value, text.value, link))
            }
            val claimed = HashSet<EntryOrigin>()
            return planned.sortedWith(compareBy({ it.date }, { it.legacyId }))
                .mapIndexed { index, it ->
                    Income(
                        id = IncomeId.of(ids.next()).identifier(),
                        date = it.date,
                        sequence = index + 1L,
                        amount = it.amount,
                        source = it.source,
                        note = it.note,
                        version = 1,
                        createdAt = startOf(it.date),
                        updatedAt = startOf(it.date),
                        origin = origin(schedules, LegacyRecord.Income, it.link, it.date, claimed)
                    )
                }
        }

        /** A recorded day: real, storable and not after the export. */
        private fun day(text: String): RecordResult<LocalDate> {
            val day = date(text) ?: return Skip(LegacyReason.InvalidDate)
            if (day.isAfter(exportDay)) return Skip(LegacyReason.DateAfterExport)
            return Keep(day)
        }

        private fun source(label: String): Fitted<IncomeSource>? {
            sourceLabels[label]?.let { return Fitted(it, cut = false) }
            val name = fitted(label, SourceName.MAX_CODE_POINTS, SourceName::of) ?: return null
            return Fitted(IncomeSource.Other(name.value), name.cut)
        }

        // Goals.

        // A goal's `id` is not read: nothing refers to it.
        private fun goals(): List<GoalHistory> = records(
            "goals",
            LegacyRecord.Goal,
            identified = false
        ) { fields ->
            val rawName = fields.text("name")
            val target = fields.long("targetAmountCop")
            val saved = fields.optionalLong("currentAmountCop") ?: 0
            val deadline = fields.text("deadlineDate")
            val achieved = fields.flag("isAchieved", default = false)
            val targetAmount =
                transaction(target) ?: return@records Skip(LegacyReason.AmountOutOfRange)
            val savedAmount = if (saved == 0L) {
                null
            } else {
                transaction(saved) ?: return@records Skip(LegacyReason.AmountOutOfRange)
            }
            val month = date(deadline)?.let(YearMonth::from)
                ?.takeIf(EffectiveDates::isStorable)
                ?: return@records Skip(LegacyReason.InvalidDate)
            val name = fitted(rawName, GoalName.MAX_CODE_POINTS, GoalName::of)
                ?: return@records Skip(LegacyReason.InvalidText)
            val goal = Goal(
                id = GoalId.of(ids.next()).identifier(),
                name = name.value,
                icon = GOAL_ICON,
                targetMonth = month,
                completedOn = exportDay.takeIf { achieved },
                version = 1,
                createdAt = exportedAt,
                updatedAt = exportedAt
            )
            val events = listOfNotNull(
                GoalTargetChange(targetAmount, 1, exportDay),
                savedAmount?.let {
                    GoalMovement(
                        id = GoalMovementId.of(ids.next()).identifier(),
                        kind = GoalMovementKind.Contribution,
                        amount = it,
                        date = exportDay,
                        sequence = 2,
                        recordedOn = exportDay,
                        note = null,
                        version = 1,
                        createdAt = exportedAt,
                        updatedAt = exportedAt
                    )
                }
            )
            val history = GoalHistory(goal, events)
            if (GoalPolicy.progress(history) is Outcome.Failure) {
                return@records Skip(LegacyReason.AmountOutOfRange)
            }
            if (name.cut) count(LegacyRecord.Goal, LegacyReason.Shortened)
            Keep(history)
        }

        // What v1 does not import, and preferences.

        private fun notImportedYet() {
            listOf(
                "investments" to LegacyRecord.Investment,
                "investmentEntries" to LegacyRecord.InvestmentEntry,
                "exchangeRates" to LegacyRecord.ExchangeRate,
                "merchantRules" to LegacyRecord.MerchantRule
            ).forEach { (key, record) ->
                count(record, LegacyReason.NotImportedYet, (root[key] as? JsonArray)?.size ?: 0)
            }
        }

        private fun preferences(): PortablePreferences? {
            val preferences = root["preferences"] as JsonObject
            fun text(key: String) = (preferences[key] as? JsonPrimitive)
                ?.takeIf { it.isString }?.content
            val theme = themes[text("themeMode")] ?: return null
            val order = orders[text("homeSortField")] ?: return null
            val direction = directions[text("homeSortDirection")] ?: return null
            return PortablePreferences(
                AppearancePreference(theme, InkPreference.Cobalto),
                CategoryOrderingPreference(order, direction)
            )
        }

        // Shared.

        private fun categoryReason(legacyCategory: Long, left: Set<Long>) = if (legacyCategory in
            left
        ) {
            LegacyReason.CategoryLeftOut
        } else {
            LegacyReason.CategoryMissing
        }

        private fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

        /**
         * Each record of [key] read with [read], in the old app's order: by `id` when
         * [identified], file order otherwise and for ties. A record not in the expected
         * shape, or repeating an `id` already seen, is left out and counted as such; the
         * `id` of each record left out for any other reason is added to [leftOut].
         */
        private fun <T> records(
            key: String,
            record: LegacyRecord,
            identified: Boolean = true,
            leftOut: MutableSet<Long>? = null,
            read: (Fields) -> RecordResult<T>
        ): List<T> {
            val rows = (root[key] as? JsonArray).orEmpty()
            val readable = rows.mapNotNull { row ->
                val fields = (row as? JsonObject)?.let(::Fields)
                val id = if (identified) fields?.id() else null
                if (fields == null || (identified && id == null)) {
                    count(record, LegacyReason.Unreadable)
                    null
                } else {
                    id to fields
                }
            }.sortedBy { (id, _) -> id ?: 0 }
            val seen = HashSet<Long>()
            return readable.mapNotNull { (id, fields) ->
                if (id != null && !seen.add(id)) {
                    count(record, LegacyReason.DuplicateId)
                    return@mapNotNull null
                }
                val result = try {
                    read(fields)
                } catch (_: UnreadableRecord) {
                    Skip(LegacyReason.Unreadable)
                }
                when (result) {
                    is Keep -> result.value

                    is Skip -> {
                        count(record, result.reason)
                        if (id != null) leftOut?.add(id)
                        null
                    }
                }
            }
        }
    }

    private sealed interface RecordResult<out T>

    private class Keep<T>(val value: T) : RecordResult<T>

    private class Skip(val reason: LegacyReason) : RecordResult<Nothing>

    private class Fitted<T>(val value: T, val cut: Boolean)

    /** A field missing or of the wrong type. */
    private class UnreadableRecord : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    /** One record's fields, read strictly: a wrong type is [UnreadableRecord], never a guess. */
    private class Fields(val row: JsonObject) {
        private fun present(key: String): JsonElement? = row[key]?.takeUnless { it is JsonNull }

        fun long(key: String): Long = optionalLong(key) ?: throw UnreadableRecord()

        /** The record's `id`, or null when it is missing or not an integer. */
        fun id(): Long? = present("id")?.integer()

        fun optionalLong(key: String): Long? {
            val value = present(key) ?: return null
            return value.integer() ?: throw UnreadableRecord()
        }

        fun text(key: String): String = optionalText(key, null) ?: throw UnreadableRecord()

        fun optionalText(key: String): String = optionalText(key, "")!!

        private fun optionalText(key: String, default: String?): String? {
            val value = present(key) ?: return default
            val primitive = value as? JsonPrimitive
            if (primitive == null || !primitive.isString) throw UnreadableRecord()
            return primitive.content
        }

        fun flag(key: String, default: Boolean): Boolean {
            val value = present(key) ?: return default
            val primitive = value as? JsonPrimitive
            if (primitive == null || primitive.isString) throw UnreadableRecord()
            return when (primitive.content) {
                "true" -> true
                "false" -> false
                else -> throw UnreadableRecord()
            }
        }

        fun moment(key: String): Instant = try {
            Instant.ofEpochMilli(long(key))
        } catch (_: DateTimeException) {
            throw UnreadableRecord()
        }
    }

    private companion object {
        const val NEWEST_VERSION = 2L
        const val CUSTOM = "CUSTOM:"

        /** How FinanceTracker writes `exportedAt`: local time, no zone, no seconds. */
        val exportedFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm")
            .withResolverStyle(ResolverStyle.STRICT)
        val FALLBACK_ICON = (IconKey.of("receipt_long") as Outcome.Success).value
        val GOAL_ICON = (IconKey.of("savings") as Outcome.Success).value
        val plainWhole = Regex("0|[1-9][0-9]*")
        val integer = Regex("-?(0|[1-9][0-9]*)")

        val sourceLabels: Map<String, IncomeSource> = mapOf(
            "Salario" to IncomeSource.Salary,
            "Ingresos pasivos" to IncomeSource.PassiveIncome,
            "Freelance" to IncomeSource.Freelance,
            "Transferencia" to IncomeSource.Transfer
        )
        val themes = mapOf(
            "SYSTEM" to ThemePreference.System,
            "LIGHT" to ThemePreference.Light,
            "DARK" to ThemePreference.Dark
        )
        val orders = mapOf(
            "ALPHABETICAL" to CategoryOrder.Alphabetical,
            "AMOUNT_SPENT" to CategoryOrder.AmountSpent,
            "BUDGET_LIMIT" to CategoryOrder.Limit,
            "LAST_UPDATED" to CategoryOrder.LatestExpenseAdded
        )
        val directions = mapOf(
            "ASC" to CategoryOrderDirection.Ascending,
            "DESC" to CategoryOrderDirection.Descending
        )

        /** A bare JSON integer, sign allowed: `-5` but not `"5"`, `5.0` or `05`. */
        fun JsonElement.integer(): Long? {
            val primitive = this as? JsonPrimitive ?: return null
            if (primitive.isString || !integer.matches(primitive.content)) return null
            return primitive.content.toLongOrNull()
        }

        /** A version number: a bare non-negative integer. */
        fun JsonElement.whole(): Long? = integer()?.takeIf { it >= 0 }

        fun transaction(pesos: Long): TransactionAmount? =
            (TransactionAmount.of(Money.cop(pesos)) as? Outcome.Success)?.value

        fun date(text: String): LocalDate? =
            (EffectiveDates.parseDate(text) as? Outcome.Success)?.value

        fun <T> Outcome<T, *>.orNull(): T? = (this as? Outcome.Success)?.value

        fun <T> Outcome<T, *>.identifier(): T =
            orNull() ?: error("The identifier source returned a malformed identifier")

        /** [this], NFC and trimmed, cut to at most [max] code points. */
        fun String.cutTo(max: Int): String {
            val text = Normalizer.normalize(trim(), Normalizer.Form.NFC)
            if (text.codePointCount(0, text.length) <= max) return text
            return text.substring(0, text.offsetByCodePoints(0, max))
        }

        /**
         * [raw] under [of], cut to [max] code points only when it is too long (the owner's
         * repair, ADR 0019); null when it breaks another rule.
         */
        fun <T> fitted(raw: String, max: Int, of: (String) -> Outcome<T, TextError>): Fitted<T>? =
            when (val first = of(raw)) {
                is Outcome.Success -> Fitted(first.value, cut = false)

                is Outcome.Failure -> if (first.error is TextError.TooLong) {
                    of(raw.cutTo(max)).orNull()?.let { Fitted(it, cut = true) }
                } else {
                    null
                }
            }

        fun utf8(bytes: ByteArray): String? = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
