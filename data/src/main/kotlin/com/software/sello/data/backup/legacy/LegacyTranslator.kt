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
import com.software.sello.domain.model.Currency
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
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentHistoryError
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
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
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.policy.GoalPolicy
import com.software.sello.domain.policy.InvestmentPolicy
import com.software.sello.domain.policy.SchedulePolicy
import com.software.sello.domain.port.LegacyImportLine
import com.software.sello.domain.port.LegacyImportReport
import com.software.sello.domain.port.LegacyReason
import com.software.sello.domain.port.LegacyRecord
import com.software.sello.domain.port.RecordIdSource
import com.software.sello.domain.port.RestoreRejection
import com.software.sello.domain.port.StoredSchedule
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
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
 * Sello rule is left out and counted, and the only repairs are cutting text to fit,
 * numbering clashing category names and rounding the dollar rate to the centavo;
 * investments are carried so their figures match the old app's, every change shown.
 * Every rule is in docs/data/legacy-export-format.md.
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
            val investments = investments()
            val usdRates = usdRates()
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
                    investments = investments,
                    usdRates = usdRates
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

        // Investments.

        private val investmentIdsLeftOut = HashSet<Long>()

        private inner class PlannedInvestment(
            val legacyId: Long,
            val investment: Investment,
            val nameCut: Boolean,
            val annualRate: Boolean
        )

        private fun investments(): List<InvestmentHistory> {
            val planned = records(
                "investments",
                LegacyRecord.Investment,
                leftOut = investmentIdsLeftOut
            ) { fields ->
                val legacyId = fields.long("id")
                val rawName = fields.text("name")
                val code = fields.text("currency")
                val icon = fields.text("iconKey")
                val created = fields.text("createdDate")
                val annualRate = fields.has("annualRatePercent")
                val currency = Currency.entries
                    .firstOrNull { it.code == code && it in InvestmentAmount.currencies }
                    ?: return@records Skip(LegacyReason.UnsupportedCurrency)
                val name = fitted(rawName, InvestmentName.MAX_CODE_POINTS, InvestmentName::of)
                    ?: return@records Skip(LegacyReason.InvalidText)
                val day = when (val read = day(created)) {
                    is Keep -> read.value
                    is Skip -> return@records read
                }
                Keep(
                    PlannedInvestment(
                        legacyId = legacyId,
                        investment = Investment(
                            id = InvestmentId.of(ids.next()).identifier(),
                            name = name.value,
                            description = null,
                            icon = (IconKey.of(icon) as? Outcome.Success)?.value ?: INVESTMENT_ICON,
                            currency = currency,
                            version = 1,
                            createdAt = startOf(day),
                            updatedAt = startOf(day)
                        ),
                        nameCut = name.cut,
                        annualRate = annualRate
                    )
                )
            }
            val known = planned.associateBy { it.legacyId }
            val entries = records("investmentEntries", LegacyRecord.InvestmentEntry) { fields ->
                val legacyId = fields.long("id")
                val parent = fields.long("investmentId")
                val type = fields.text("entryType")
                val amount = fields.optionalLong("amountMinorUnits") ?: 0
                val date = fields.text("date")
                val note = fields.optionalText("notes")
                val investment = known[parent] ?: return@records Skip(
                    if (parent in investmentIdsLeftOut) {
                        LegacyReason.InvestmentLeftOut
                    } else {
                        LegacyReason.InvestmentMissing
                    }
                )
                val kind = entryKinds[type] ?: return@records Skip(
                    if (type == "NOTE") LegacyReason.NoteOnly else LegacyReason.Unreadable
                )
                val money = Money(amount, investment.investment.currency)
                val bounded = if (kind == EntryKind.Valuation) {
                    InvestmentAmount.ofValue(money)
                } else {
                    InvestmentAmount.of(money)
                }
                if (bounded is Outcome.Failure) return@records Skip(LegacyReason.AmountOutOfRange)
                val day = when (val read = day(date)) {
                    is Keep -> read.value
                    is Skip -> return@records read
                }
                val text = fitted(note, Note.MAX_CODE_POINTS, Note::of)
                    ?: return@records Skip(LegacyReason.InvalidText)
                Keep(PlannedEntry(legacyId, parent, kind, amount, day, text.value, text.cut))
            }.groupBy { it.investment }
            return planned.mapNotNull { history(it, entries[it.legacyId].orEmpty()) }
        }

        /**
         * [planned]'s history in Sello's terms, or null when the investment is left out.
         * Each entry that breaks an investment rule is left out in turn and the history
         * worked out again, so what is kept passes the released validator.
         */
        private fun history(
            planned: PlannedInvestment,
            entries: List<PlannedEntry>
        ): InvestmentHistory? {
            var kept = entries.sortedWith(compareBy({ it.date }, { it.legacyId }))
            while (true) {
                val built = when (val attempt = build(planned.investment, kept)) {
                    is Built.Dropped -> {
                        count(LegacyRecord.InvestmentEntry, attempt.reason)
                        kept = kept - attempt.entry
                        continue
                    }

                    is Built.Refused -> {
                        count(LegacyRecord.Investment, attempt.reason)
                        count(
                            LegacyRecord.InvestmentEntry,
                            LegacyReason.InvestmentLeftOut,
                            kept.size
                        )
                        return null
                    }

                    is Built.Done -> attempt
                }
                if (planned.nameCut) count(LegacyRecord.Investment, LegacyReason.Shortened)
                if (built.valueAdded) count(LegacyRecord.Investment, LegacyReason.ValueAdded)
                if (planned.annualRate) {
                    count(LegacyRecord.Investment, LegacyReason.NoAutomaticInterest)
                }
                count(
                    LegacyRecord.InvestmentEntry,
                    LegacyReason.Shortened,
                    kept.count { it.noteCut }
                )
                count(
                    LegacyRecord.InvestmentEntry,
                    LegacyReason.InterestAsValue,
                    kept.count { it.kind == EntryKind.Interest }
                )
                // Real identifiers only once the history is final.
                return InvestmentHistory(
                    planned.investment,
                    built.history.movements.map {
                        it.copy(id = InvestmentMovementId.of(ids.next()).identifier())
                    }
                )
            }
        }

        /**
         * One attempt at [entries] (in the old order) as movements, with placeholder
         * identifiers. The old app's value is followed along: an aporte or interest adds
         * to it, a retiro takes from it, a recorded value replaces it. Interest becomes a
         * recorded value of what it reached, and the first aporte is followed by a value
         * equal to it when no value is recorded that day, as Sello does on creation.
         */
        private fun build(investment: Investment, entries: List<PlannedEntry>): Built {
            val first = entries.firstOrNull { it.kind == EntryKind.Contribution }
                ?: return Built.Refused(LegacyReason.NoContribution)
            val valueAdded = entries.none {
                it.date == first.date &&
                    (it.kind == EntryKind.Valuation || it.kind == EntryKind.Interest)
            }
            val currency = investment.currency
            val movements = ArrayList<InvestmentMovement>()
            val sources = HashMap<InvestmentMovementId, PlannedEntry>()
            var value = BigInteger.ZERO
            fun add(
                kind: InvestmentMovementKind,
                units: BigInteger,
                entry: PlannedEntry,
                note: Note? = entry.note
            ): Boolean {
                val money = units.takeIf { it.bitLength() < Long.SIZE_BITS }
                    ?.let { Money(it.toLong(), currency) }
                val amount = money?.let {
                    if (kind == InvestmentMovementKind.Valuation) {
                        InvestmentAmount.ofValue(it)
                    } else {
                        InvestmentAmount.of(it)
                    }
                } as? Outcome.Success ?: return false
                val id = InvestmentMovementId.of(placeholder(movements.size)).identifier()
                sources[id] = entry
                movements += InvestmentMovement(
                    id = id,
                    kind = kind,
                    amount = amount.value,
                    date = entry.date,
                    sequence = movements.size.toLong(),
                    note = note,
                    version = 1,
                    createdAt = startOf(entry.date),
                    updatedAt = startOf(entry.date)
                )
                return true
            }
            for (entry in entries) {
                val units = BigInteger.valueOf(entry.amount)
                val kind = when (entry.kind) {
                    EntryKind.Contribution -> InvestmentMovementKind.Contribution.also {
                        value += units
                    }

                    EntryKind.Withdrawal -> InvestmentMovementKind.Withdrawal.also {
                        value -= units
                    }

                    EntryKind.Dividend -> InvestmentMovementKind.Dividend

                    EntryKind.Valuation -> InvestmentMovementKind.Valuation.also { value = units }

                    EntryKind.Interest -> InvestmentMovementKind.Valuation.also { value += units }
                }
                val amount = if (entry.kind == EntryKind.Interest) value else units
                if (!add(kind, amount, entry)) {
                    return Built.Dropped(entry, LegacyReason.AmountOutOfRange)
                }
                if (entry === first && valueAdded) {
                    if (!add(InvestmentMovementKind.Valuation, value, entry, note = null)) {
                        return Built.Refused(LegacyReason.AmountOutOfRange)
                    }
                }
            }
            val history = InvestmentHistory(investment, movements)
            return when (val figures = InvestmentPolicy.figures(history)) {
                is Outcome.Success -> Built.Done(history, valueAdded)

                is Outcome.Failure -> when (val error = figures.error) {
                    is InvestmentHistoryError.WithdrawalAboveValue ->
                        Built.Dropped(
                            sources.getValue(error.withdrawal),
                            LegacyReason.WithdrawalAboveValue
                        )

                    is InvestmentHistoryError.WithdrawalWithoutValue ->
                        Built.Dropped(
                            sources.getValue(error.withdrawal),
                            LegacyReason.WithdrawalAboveValue
                        )

                    is InvestmentHistoryError.BeforeFirstContribution ->
                        Built.Dropped(
                            sources.getValue(error.movement),
                            LegacyReason.BeforeFirstContribution
                        )

                    InvestmentHistoryError.NoContribution ->
                        Built.Refused(LegacyReason.NoContribution)

                    InvestmentHistoryError.Unrepresentable ->
                        Built.Refused(LegacyReason.AmountOutOfRange)

                    is InvestmentHistoryError.CurrencyMismatch ->
                        error("Every movement was built in its investment's currency")
                }
            }
        }

        // The dollar rate.

        private fun usdRates(): List<UsdRateEntry> {
            class Planned(val rate: UsdRate, val date: LocalDate, val rounded: Boolean)
            // A rate has no `id`: the old app kept one per currency.
            val planned = records(
                "exchangeRates",
                LegacyRecord.ExchangeRate,
                identified = false
            ) { fields ->
                val from = fields.text("fromCurrency")
                val to = fields.text("toCurrency")
                val exact = fields.decimal("rate")
                val updated = fields.text("updatedDate")
                if (from != "USD" || to != "COP") {
                    return@records Skip(LegacyReason.UnsupportedCurrency)
                }
                val day = rateDay(updated) ?: return@records Skip(LegacyReason.InvalidDate)
                if (day.isAfter(exportDay)) return@records Skip(LegacyReason.DateAfterExport)
                val rounded = exact.stripTrailingZeros().scale() > 2
                val centavos = if (rounded) exact.setScale(2, RoundingMode.HALF_UP) else exact
                val rate = UsdRate.of(centavos).orNull()
                    ?: return@records Skip(LegacyReason.AmountOutOfRange)
                Keep(Planned(rate, day, rounded))
            }
            count(LegacyRecord.ExchangeRate, LegacyReason.Rounded, planned.count { it.rounded })
            return planned.mapIndexed { index, it ->
                UsdRateEntry(
                    id = UsdRateId.of(ids.next()).identifier(),
                    rate = it.rate,
                    date = it.date,
                    sequence = index + 1L,
                    createdAt = startOf(it.date)
                )
            }
        }

        /** `updatedDate`: the local date and time the old app wrote, or a bare date. */
        private fun rateDay(text: String): LocalDate? {
            val day = try {
                LocalDateTime.parse(text, exportedFormat).toLocalDate()
            } catch (_: DateTimeException) {
                date(text)
            }
            return day?.takeIf(EffectiveDates::isStorable)
        }

        // What is not imported, and preferences.

        private fun notImportedYet() {
            count(
                LegacyRecord.MerchantRule,
                LegacyReason.NotImportedYet,
                (root["merchantRules"] as? JsonArray)?.size ?: 0
            )
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

    private class PlannedEntry(
        val legacyId: Long,
        val investment: Long,
        val kind: EntryKind,
        val amount: Long,
        val date: LocalDate,
        val note: Note?,
        val noteCut: Boolean
    )

    private sealed interface Built {
        class Done(val history: InvestmentHistory, val valueAdded: Boolean) : Built

        class Dropped(val entry: PlannedEntry, val reason: LegacyReason) : Built

        class Refused(val reason: LegacyReason) : Built
    }

    /** A FinanceTracker investment entry type Sello keeps, in Sello's terms. */
    private enum class EntryKind { Contribution, Withdrawal, Dividend, Valuation, Interest }

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

        fun has(key: String): Boolean = present(key) != null

        /** A bare JSON number read from its text, exactly: never through a `Double`. */
        fun decimal(key: String): BigDecimal {
            val primitive = present(key) as? JsonPrimitive
            if (primitive == null || primitive.isString) throw UnreadableRecord()
            return try {
                BigDecimal(primitive.content)
            } catch (_: NumberFormatException) {
                throw UnreadableRecord()
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
        val INVESTMENT_ICON = GOAL_ICON

        val entryKinds = mapOf(
            "CASH_INJECTION" to EntryKind.Contribution,
            "WITHDRAWAL" to EntryKind.Withdrawal,
            "DIVIDEND" to EntryKind.Dividend,
            "VALUE_SNAPSHOT" to EntryKind.Valuation,
            "INTEREST" to EntryKind.Interest
        )

        /** A well-formed identifier that only lives while a history is checked. */
        fun placeholder(index: Int) = "00000000-0000-4000-8000-%012d".format(index)
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
