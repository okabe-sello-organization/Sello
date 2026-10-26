package com.software.sello.data.backup

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SourceName
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRoundTripTest {
    private val codec = BackupCodec()

    private fun roundTrip(contents: BackupContents): BackupContents =
        codec.read(codec.encode(contents).value()).value().contents

    @Test
    fun `the hand-written G01 file reads as exactly the G01 history`() {
        val plan = codec.read(resource("valid-g01.json")).value()

        assertEquals(g01(), plan.contents)
        assertEquals(
            BackupCounts(
                categories = 2,
                archivedCategories = 1,
                defaultLimits = 3,
                monthLimits = 1,
                expenses = 3,
                incomes = 1,
                goals = 0,
                goalEvents = 0,
                schedules = 0,
                investments = 0,
                investmentMovements = 0,
                usdRates = 0
            ),
            plan.counts
        )
    }

    /** G01 with its investments starting in dollars, the latest update first. */
    private fun g01InDollars() = g01().let {
        it.copy(
            preferences = it.preferences!!.copy(
                investments = InvestmentPreference(Currency.USD, InvestmentOrder.LatestUpdate)
            )
        )
    }

    @Test
    fun `G01 is written byte for byte as the documented version 5 file`() {
        assertEquals(
            String(resource("valid-g01-v5.json")),
            String(codec.encode(g01InDollars()).value())
        )
    }

    @Test
    fun `the version 5 file reads back with its investment settings`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v5.json")).value().contents)
    }

    @Test
    fun `a version 4 file reads with investments in pesos, largest value first`() {
        val contents = codec.read(resource("valid-g01-v4.json")).value().contents

        assertEquals(g01(), contents)
        assertEquals(
            InvestmentPreference(Currency.COP, InvestmentOrder.LargestValue),
            contents.preferences?.investments
        )
    }

    @Test
    fun `an investment setting the app does not know rejects the whole file`() {
        val file = String(resource("valid-g01-v5.json"))
        for ((from, to, path) in listOf(
            Triple("\"USD\"", "\"EUR\"", "preferences.investmentCurrency"),
            Triple("\"USD\"", "\"usd\"", "preferences.investmentCurrency"),
            Triple("\"latest_update\"", "\"LatestUpdate\"", "preferences.investmentOrder")
        )) {
            val changed = file.replace(from, to)
            check(changed != file)
            assertEquals(
                to,
                BackupRejection.InvalidValue(path),
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    @Test
    fun `a version 5 file without its investment settings is malformed`() {
        val file = String(resource("valid-g01-v5.json"))
        val missing = file.replace(",\n    \"investmentOrder\": \"latest_update\"", "")
        check(missing != file)

        assertTrue(
            (codec.read(missing.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
        )
    }

    @Test
    fun `the same data always makes the same file, whatever order it was read in`() {
        val g01 = g01()
        val shuffled = g01.copy(
            financial = g01.financial.copy(
                categories = g01.financial.categories.reversed(),
                defaultLimits = g01.financial.defaultLimits.reversed(),
                monthLimits = g01.financial.monthLimits.reversed(),
                expenses = g01.financial.expenses.reversed(),
                incomes = g01.financial.incomes.reversed()
            )
        )

        assertArrayEquals(codec.encode(g01).value(), codec.encode(shuffled).value())
    }

    @Test
    fun `H01 limits, an archived category and its pause come back unchanged`() {
        val h01 = g01().copy(
            financial = g01().financial.copy(
                categories = listOf(category(1, "Mercado"), category(2, "Café", archived = true)),
                defaultLimits = listOf(
                    DefaultLimit(
                        categoryId(1),
                        YearMonth.of(2026, 9),
                        AutomaticBudget.Limit(finite(100_000))
                    ),
                    DefaultLimit(
                        categoryId(1),
                        YearMonth.of(2026, 10),
                        AutomaticBudget.Limit(finite(120_000))
                    ),
                    DefaultLimit(
                        categoryId(2),
                        YearMonth.of(2026, 9),
                        AutomaticBudget.Limit(finite(0))
                    ),
                    DefaultLimit(categoryId(2), YearMonth.of(2026, 10), AutomaticBudget.Paused)
                ),
                monthLimits = listOf(
                    MonthLimit(categoryId(1), YearMonth.of(2026, 8), BudgetLimit.Unlimited)
                ),
                expenses = listOf(expense(101, 1, "2026-09-15", 1, 20_000)),
                incomes = emptyList()
            )
        )

        assertEquals(h01, roundTrip(h01))
    }

    @Test
    fun `the largest amount survives exactly and two may total more than one record`() {
        val largest = g01().let {
            it.copy(
                financial = it.financial.copy(
                    expenses = listOf(
                        expense(101, 1, "2026-10-01", 1, 999_999_999_999),
                        expense(102, 1, "2026-10-01", 2, 999_999_999_999)
                    ),
                    incomes = listOf(income(201, "2026-10-01", 1, 999_999_999_999))
                )
            )
        }

        val restored = roundTrip(largest)

        assertEquals(largest, restored)
        assertEquals(
            Money(1_999_999_999_998, Currency.COP),
            ExactTotal.of(Currency.COP, restored.financial.expenses.map { it.amount.money })
                .toMoney().value()
        )
    }

    @Test
    fun `sequences, zone, other-income names and notes come back as they were`() {
        val other = IncomeSource.Other(SourceName.of("Venta de bicicleta").value())
        val contents = g01().let {
            it.copy(
                financial = it.financial.copy(
                    zone = ZoneId.of("Asia/Kathmandu"),
                    expenses = listOf(
                        expense(101, 1, "2026-10-01", 7, 10_000, note = "Ñame y ají"),
                        expense(102, 1, "2026-10-01", 30, 1),
                        expense(103, 1, "2026-10-02", 12, 5_000)
                    ),
                    incomes = listOf(income(201, "2026-10-01", 4, 80_000, source = other))
                )
            )
        }

        val restored = roundTrip(contents)

        assertEquals(contents, restored)
        assertEquals(listOf(7L, 30L, 12L), restored.financial.expenses.map { it.sequence })
    }

    @Test
    fun `a backup made without preferences says so and still reads`() {
        val contents = g01().copy(preferences = null)
        val bytes = codec.encode(contents).value()

        assertNull(codec.read(bytes).value().contents.preferences)
        assertEquals(
            "null",
            Json.parseToJsonElement(String(bytes)).jsonObject["preferences"].toString()
        )
    }

    @Test
    fun `a record carries only its portable fields, never versions, generations or receipts`() {
        val file = Json.parseToJsonElement(String(codec.encode(g01()).value())).jsonObject
        val financial = file.getValue("financial").jsonObject

        fun keysOf(list: String) = (financial.getValue(list) as JsonArray).first().jsonObject.keys

        assertEquals(
            setOf("format", "version", "exportedAt", "financial", "preferences"),
            file.keys
        )
        assertEquals(
            setOf(
                "sourceRevision",
                "zone",
                "categories",
                "defaultLimits",
                "monthLimits",
                "expenses",
                "incomes",
                "goals",
                "featuredGoal",
                "schedules",
                "investments",
                "usdRates"
            ),
            financial.keys
        )
        assertEquals(
            setOf("id", "name", "icon", "archived", "createdAt", "updatedAt"),
            keysOf("categories")
        )
        assertEquals(
            setOf(
                "id",
                "categoryId",
                "date",
                "sequence",
                "amount",
                "currency",
                "note",
                "createdAt",
                "updatedAt",
                "origin"
            ),
            keysOf("expenses")
        )
        assertEquals(
            setOf("categoryId", "from", "kind", "amount", "currency"),
            keysOf("defaultLimits")
        )
        assertEquals(
            setOf(
                "theme",
                "ink",
                "categoryOrder",
                "categoryDirection",
                "investmentCurrency",
                "investmentOrder"
            ),
            (file.getValue("preferences") as JsonObject).keys
        )
    }

    @Test
    fun `the file name carries the moment in the backup's financial zone`() {
        // 03:05 UTC on the 23rd is still 22:05 on the 22nd in Bogotá.
        assertEquals(
            "sello-respaldo-2026-10-22-2205.json",
            BackupFormat.fileName(
                Instant.parse("2026-10-23T03:05:59Z"),
                ZoneId.of("America/Bogota")
            )
        )
    }
}
