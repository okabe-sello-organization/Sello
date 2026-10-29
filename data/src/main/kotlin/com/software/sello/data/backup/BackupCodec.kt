package com.software.sello.data.backup

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.EntryOrigin
import com.software.sello.domain.model.GoalEvent
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.RepeatRule
import com.software.sello.domain.model.ScheduledEntry
import com.software.sello.domain.policy.EffectiveDates
import com.software.sello.domain.port.StoredSchedule
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

/** The fixed parts of the format; see docs/data/backup-format.md. */
object BackupFormat {
    const val FORMAT = "sello-backup"

    /** What this app writes. */
    const val VERSION = 6L

    /**
     * What it reads: version 1 (no goals), version 2 (no schedules), version 3 (no
     * investments), version 4 (no investment settings), version 5 (no carry chains) and
     * its own.
     */
    val READABLE = 1L..VERSION
    const val MIME_TYPE = "application/json"

    private val stamp = DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm")

    /** `sello-respaldo-2026-10-22-1430.json`, in the backup's financial zone. */
    fun fileName(exportedAt: Instant, zone: ZoneId): String =
        "sello-respaldo-${stamp.format(exportedAt.atZone(zone))}.json"
}

/**
 * Writes version 6 backups and reads versions 1 to 6. Reading never touches storage:
 * a file either becomes a [BackupPlan] or is rejected whole with the first problem found.
 * A file whose categories carry their balance is valid, but until SELLO-041 only a codec
 * made with [CarryChainSupport.Accepted] takes it; the default refuses it.
 */
class BackupCodec(
    private val bounds: BackupBounds = BackupBounds(),
    private val carryChains: CarryChainSupport = CarryChainSupport.Refused
) {
    /**
     * The file for [contents], read back before it is returned, so the app never hands
     * out a backup it would itself refuse to restore.
     */
    fun encode(contents: BackupContents): Outcome<ByteArray, BackupRejection> {
        val text = writer.encodeToString(BackupFileV6.serializer(), contents.toWire())
        val bytes = "$text\n".toByteArray(Charsets.UTF_8)
        return when (val check = read(bytes)) {
            is Outcome.Success -> Outcome.Success(bytes)
            is Outcome.Failure -> check
        }
    }

    /** Reads at most one byte more than the size cap, so a huge stream is never held. */
    fun read(input: InputStream): Outcome<BackupPlan, BackupRejection> {
        val held = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_BYTES)
        while (held.size() <= bounds.maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, bounds.maxBytes + 1 - held.size()))
            if (read < 0) break
            held.write(buffer, 0, read)
        }
        if (held.size() > bounds.maxBytes) {
            return Outcome.Failure(BackupRejection.TooLarge(bounds.maxBytes, held.size().toLong()))
        }
        return read(held.toByteArray())
    }

    fun read(bytes: ByteArray): Outcome<BackupPlan, BackupRejection> {
        if (bytes.size > bounds.maxBytes) {
            return Outcome.Failure(BackupRejection.TooLarge(bounds.maxBytes, bytes.size.toLong()))
        }
        val text = utf8(bytes) ?: return Outcome.Failure(BackupRejection.Malformed(null))
        scanStructure(text, bounds)?.let { return Outcome.Failure(it) }
        val version = when (val header = header(text)) {
            is Outcome.Success -> header.value
            is Outcome.Failure -> return header
        }
        val file = try {
            when (version) {
                1L -> reader.decodeFromString(BackupFileV1.serializer(), text)
                    .asVersion2().asVersion3().asVersion4().asVersion5().asVersion6()

                2L -> reader.decodeFromString(BackupFileV2.serializer(), text)
                    .asVersion3().asVersion4().asVersion5().asVersion6()

                3L -> reader.decodeFromString(BackupFileV3.serializer(), text)
                    .asVersion4().asVersion5().asVersion6()

                4L -> reader.decodeFromString(BackupFileV4.serializer(), text)
                    .asVersion5().asVersion6()

                5L -> reader.decodeFromString(BackupFileV5.serializer(), text).asVersion6()

                else -> reader.decodeFromString(BackupFileV6.serializer(), text)
            }
        } catch (failure: SerializationException) {
            return Outcome.Failure(BackupRejection.Malformed(pathOf(failure)))
        } catch (failure: IllegalArgumentException) {
            return Outcome.Failure(BackupRejection.Malformed(pathOf(failure)))
        }
        return when (val contents = validate(file)) {
            is Outcome.Success ->
                if (carryChains == CarryChainSupport.Refused &&
                    contents.value.financial.carryChains.isNotEmpty()
                ) {
                    // Only a file that is otherwise valid gets this answer, and nothing
                    // has been written: the check is part of reading.
                    Outcome.Failure(BackupRejection.CarryNotSupported)
                } else {
                    Outcome.Success(BackupPlan(contents.value))
                }

            is Outcome.Failure -> contents
        }
    }

    /** A file says what it is, and which version, before the rest of it is held to that shape. */
    private fun header(text: String): Outcome<Long, BackupRejection> {
        val header = try {
            headerReader.decodeFromString(BackupHeaderWire.serializer(), text)
        } catch (_: SerializationException) {
            return Outcome.Failure(BackupRejection.NotSelloBackup)
        } catch (_: IllegalArgumentException) {
            return Outcome.Failure(BackupRejection.NotSelloBackup)
        }
        val format = header.format as? JsonPrimitive
        if (format == null || !format.isString || format.content != BackupFormat.FORMAT) {
            return Outcome.Failure(BackupRejection.NotSelloBackup)
        }
        val version = header.version?.wholeNumberOrNull()
            ?: return Outcome.Failure(BackupRejection.NotSelloBackup)
        return if (version in BackupFormat.READABLE) {
            Outcome.Success(version)
        } else {
            Outcome.Failure(BackupRejection.UnsupportedVersion(version))
        }
    }

    private companion object {
        const val BUFFER_BYTES = 64 * 1024
        val reader = Json
        val headerReader = Json { ignoreUnknownKeys = true }

        @OptIn(ExperimentalSerializationApi::class)
        val writer = Json {
            prettyPrint = true
            prettyPrintIndent = "  "
        }

        private val pathPattern = Regex("at path: (\\$[^\\s]*)")

        /** Only where the problem is: the library's message also quotes file contents. */
        fun pathOf(failure: Exception): String? =
            failure.message?.let { pathPattern.find(it)?.groupValues?.get(1) }

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

private fun BudgetLimit.amount() = (this as? BudgetLimit.Finite)?.amount

private fun BudgetLimit.kind() = when (this) {
    is BudgetLimit.Finite -> WireNames.FINITE
    BudgetLimit.Unlimited -> WireNames.UNLIMITED
}

/**
 * Records are written in a fixed order, so the same data always makes the same file:
 * categories by creation then identifier, limits by category then month, records by
 * date then sequence, goals by creation then identifier, each goal's history by
 * recording position, schedules by creation order, each schedule's pauses by first day,
 * investments by creation then identifier, each one's movements by recording position,
 * rates by the order they were entered, and carry chains by category then start month.
 */
internal fun BackupContents.toWire(): BackupFileV6 = BackupFileV6(
    format = BackupFormat.FORMAT,
    version = JsonPrimitive(BackupFormat.VERSION),
    exportedAt = exportedAt.toString(),
    financial = FinancialV6(
        sourceRevision = JsonPrimitive(financial.revision),
        zone = financial.zone.id,
        financialMonth = EffectiveDates.storedText(
            // A chain never reaches past the month the file says it was made in, whatever
            // the clock read: a clock moved back after a chain began still writes a file
            // Sello reads back.
            (
                listOf(financialMonth) +
                    financial.carryChains.flatMap { listOfNotNull(it.start, it.end) }
                )
                .max()
        ),
        categories = financial.categories
            .sortedWith(compareBy({ it.createdAt }, { it.id.value }))
            .map {
                CategoryV1(
                    id = it.id.value,
                    name = it.name.value,
                    icon = it.icon.value,
                    archived = JsonPrimitive(it.archived),
                    createdAt = it.createdAt.toString(),
                    updatedAt = it.updatedAt.toString()
                )
            },
        defaultLimits = financial.defaultLimits
            .sortedWith(compareBy({ it.categoryId.value }, { it.effectiveMonth }))
            .map {
                val limit = (it.budget as? AutomaticBudget.Limit)?.limit
                DefaultLimitV1(
                    categoryId = it.categoryId.value,
                    fromMonth = EffectiveDates.storedText(it.effectiveMonth),
                    kind = limit?.kind() ?: WireNames.PAUSED,
                    amount = JsonPrimitive(limit?.amount()?.minorUnits),
                    currency = limit?.amount()?.currency?.code
                )
            },
        monthLimits = financial.monthLimits
            .sortedWith(compareBy({ it.categoryId.value }, { it.month }))
            .map {
                MonthLimitV1(
                    categoryId = it.categoryId.value,
                    month = EffectiveDates.storedText(it.month),
                    kind = it.limit.kind(),
                    amount = JsonPrimitive(it.limit.amount()?.minorUnits),
                    currency = it.limit.amount()?.currency?.code
                )
            },
        expenses = financial.expenses
            .sortedWith(compareBy({ it.date }, { it.sequence }))
            .map {
                ExpenseV3(
                    id = it.id.value,
                    categoryId = it.categoryId.value,
                    date = EffectiveDates.storedText(it.date),
                    sequence = JsonPrimitive(it.sequence),
                    amount = JsonPrimitive(it.amount.money.minorUnits),
                    currency = it.amount.money.currency.code,
                    note = it.note?.value,
                    createdAt = it.createdAt.toString(),
                    updatedAt = it.updatedAt.toString(),
                    origin = it.origin?.toWire()
                )
            },
        incomes = financial.incomes
            .sortedWith(compareBy({ it.date }, { it.sequence }))
            .map {
                IncomeV3(
                    id = it.id.value,
                    date = EffectiveDates.storedText(it.date),
                    sequence = JsonPrimitive(it.sequence),
                    amount = JsonPrimitive(it.amount.money.minorUnits),
                    currency = it.amount.money.currency.code,
                    source = it.source.key,
                    sourceName = (it.source as? IncomeSource.Other)?.name?.value,
                    note = it.note?.value,
                    createdAt = it.createdAt.toString(),
                    updatedAt = it.updatedAt.toString(),
                    origin = it.origin?.toWire()
                )
            },
        goals = financial.goals
            .sortedWith(compareBy({ it.goal.createdAt }, { it.goal.id.value }))
            .map { history -> history.toWire() },
        featuredGoal = financial.featuredGoal?.value,
        schedules = financial.schedules.sortedBy { it.sequence }.map { it.toWire() },
        investments = financial.investments
            .sortedWith(compareBy({ it.investment.createdAt }, { it.investment.id.value }))
            .map { it.toWire() },
        usdRates = financial.usdRates.sortedBy { it.sequence }.map {
            UsdRateV4(
                id = it.id.value,
                rateHundredths = JsonPrimitive(it.rate.hundredths),
                date = EffectiveDates.storedText(it.date),
                sequence = JsonPrimitive(it.sequence),
                createdAt = it.createdAt.toString()
            )
        },
        carryChains = financial.carryChains
            .sortedWith(compareBy({ it.categoryId.value }, { it.start }))
            .map {
                CarryChainV6(
                    categoryId = it.categoryId.value,
                    start = EffectiveDates.storedText(it.start),
                    end = it.end?.let(EffectiveDates::storedText)
                )
            }
    ),
    preferences = preferences?.let {
        PreferencesV5(
            theme = WireNames.themes.getValue(it.appearance.theme),
            ink = WireNames.inks.getValue(it.appearance.ink),
            categoryOrder = WireNames.orders.getValue(it.ordering.order),
            categoryDirection = WireNames.directions.getValue(it.ordering.direction),
            investmentCurrency = WireNames.newCurrencies.getValue(it.investments.newCurrency),
            investmentOrder = WireNames.investmentOrders.getValue(it.investments.order)
        )
    }
)

private fun GoalHistory.toWire(): GoalV2 = GoalV2(
    id = goal.id.value,
    name = goal.name.value,
    icon = goal.icon.value,
    targetMonth = goal.targetMonth?.let(EffectiveDates::storedText),
    completedOn = goal.completedOn?.let(EffectiveDates::storedText),
    createdAt = goal.createdAt.toString(),
    updatedAt = goal.updatedAt.toString(),
    history = events.sortedBy(GoalEvent::sequence).map { event ->
        when (event) {
            is GoalTargetChange -> GoalEventV2(
                id = null,
                sequence = JsonPrimitive(event.sequence),
                kind = WireNames.TARGET,
                amount = JsonPrimitive(event.target.money.minorUnits),
                currency = event.target.money.currency.code,
                date = null,
                recordedOn = EffectiveDates.storedText(event.recordedOn),
                note = null,
                createdAt = null,
                updatedAt = null
            )

            is GoalMovement -> GoalEventV2(
                id = event.id.value,
                sequence = JsonPrimitive(event.sequence),
                kind = when (event.kind) {
                    GoalMovementKind.Contribution -> WireNames.CONTRIBUTION
                    GoalMovementKind.Withdrawal -> WireNames.WITHDRAWAL
                },
                amount = JsonPrimitive(event.amount.money.minorUnits),
                currency = event.amount.money.currency.code,
                date = EffectiveDates.storedText(event.date),
                recordedOn = EffectiveDates.storedText(event.recordedOn),
                note = event.note?.value,
                createdAt = event.createdAt.toString(),
                updatedAt = event.updatedAt.toString()
            )
        }
    }
)

private fun EntryOrigin.toWire() = OriginV3(
    schedule = schedule.value,
    date = EffectiveDates.storedText(occurrence.date),
    slot = JsonPrimitive(occurrence.slot)
)

private fun StoredSchedule.toWire(): ScheduleV3 {
    val entry = schedule.entry
    val rule = schedule.recurrence.rule
    return ScheduleV3(
        id = schedule.id.value,
        kind = if (entry is ScheduledEntry.Payment) WireNames.PAYMENT else WireNames.INCOME,
        categoryId = (entry as? ScheduledEntry.Payment)?.categoryId?.value,
        source = (entry as? ScheduledEntry.Income)?.source?.key,
        sourceName = ((entry as? ScheduledEntry.Income)?.source as? IncomeSource.Other)
            ?.name?.value,
        amount = JsonPrimitive(entry.amount.money.minorUnits),
        currency = entry.amount.money.currency.code,
        note = entry.note?.value,
        repeat = when (rule) {
            is RepeatRule.Monthly -> WireNames.MONTHLY
            is RepeatRule.TwiceMonthly -> WireNames.TWICE_MONTHLY
            is RepeatRule.EveryDays -> WireNames.EVERY_DAYS
            is RepeatRule.EveryWeeks -> WireNames.EVERY_WEEKS
        },
        first = JsonPrimitive(
            when (rule) {
                is RepeatRule.Monthly -> rule.day
                is RepeatRule.TwiceMonthly -> rule.first
                is RepeatRule.EveryDays -> rule.days
                is RepeatRule.EveryWeeks -> rule.weeks
            }
        ),
        second = JsonPrimitive((rule as? RepeatRule.TwiceMonthly)?.second),
        start = EffectiveDates.storedText(schedule.recurrence.start),
        end = schedule.recurrence.end?.let(EffectiveDates::storedText),
        sequence = JsonPrimitive(sequence),
        handled = handled?.let {
            HandledV3(EffectiveDates.storedText(it.date), JsonPrimitive(it.slot))
        },
        pauses = schedule.recurrence.pauses.sortedBy { it.from }.map {
            PauseV3(EffectiveDates.storedText(it.from), it.until?.let(EffectiveDates::storedText))
        },
        createdAt = createdAt.toString(),
        updatedAt = updatedAt.toString()
    )
}

private fun InvestmentHistory.toWire(): InvestmentV4 = InvestmentV4(
    id = investment.id.value,
    name = investment.name.value,
    description = investment.description?.value,
    icon = investment.icon.value,
    currency = investment.currency.code,
    createdAt = investment.createdAt.toString(),
    updatedAt = investment.updatedAt.toString(),
    movements = movements.sortedBy { it.sequence }.map {
        InvestmentMovementV4(
            id = it.id.value,
            sequence = JsonPrimitive(it.sequence),
            kind = when (it.kind) {
                InvestmentMovementKind.Contribution -> WireNames.CONTRIBUTION
                InvestmentMovementKind.Withdrawal -> WireNames.WITHDRAWAL
                InvestmentMovementKind.Dividend -> WireNames.DIVIDEND
                InvestmentMovementKind.Valuation -> WireNames.VALUATION
            },
            amount = JsonPrimitive(it.amount.money.minorUnits),
            currency = it.amount.money.currency.code,
            date = EffectiveDates.storedText(it.date),
            note = it.note?.value,
            createdAt = it.createdAt.toString(),
            updatedAt = it.updatedAt.toString()
        )
    }
)
