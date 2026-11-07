package com.software.sello.data.backup

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SmallExpenseLine
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
                usdRates = 0,
                carryChains = 0
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
    fun `G01 is written byte for byte as the documented version 10 file`() {
        assertEquals(
            String(resource("valid-g01-v10.json")),
            String(codec.encode(g01InDollars()).value())
        )
    }

    @Test
    fun `the version 5 file reads with its investment settings and no carry chains`() {
        val contents = codec.read(resource("valid-g01-v5.json")).value().contents

        assertEquals(g01InDollars(), contents)
        assertEquals(emptyList<Any>(), contents.financial.carryChains)
    }

    @Test
    fun `the version 6 file reads back as the same G01 history`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v6.json")).value().contents)
    }

    @Test
    fun `the version 7 file reads back as the same G01 history`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v7.json")).value().contents)
    }

    @Test
    fun `the version 8 file reads back as the same G01 history`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v8.json")).value().contents)
    }

    @Test
    fun `the version 9 file reads back as the same G01 history`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v9.json")).value().contents)
    }

    @Test
    fun `the version 10 file reads back as the same G01 history`() {
        assertEquals(g01InDollars(), codec.read(resource("valid-g01-v10.json")).value().contents)
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
    fun `every ink is written as the id it has always had, and reads back`() {
        val file = String(resource("valid-g01-v10.json"))
        for ((ink, id) in listOf(
            InkPreference.Cobalto to "cobalto",
            InkPreference.Violeta to "violeta",
            InkPreference.Sistema to "sistema"
        )) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(ink = ink)
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(file.replace("\"ink\": \"violeta\"", "\"ink\": \"$id\""), written)
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    /** G01 with [ink] in place of its Violeta. */
    private fun g01In(ink: InkPreference) = g01InDollars().let {
        it.copy(
            preferences = it.preferences!!.copy(
                appearance = it.preferences.appearance.copy(
                    ink = ink,
                    tuyaHue = (ink as? InkPreference.Tuya)?.hue
                )
            )
        )
    }

    @Test
    fun `Tuya is written with its hue and reads back the same, at every edge`() {
        val file = String(resource("valid-g01-v10.json"))
        for (hue in listOf(0L, 22L, 359L)) {
            val contents = g01In(InkPreference.Tuya(InkHue.of(hue)!!))
            val written = String(codec.encode(contents).value())
            assertEquals(
                file.replace(
                    "\"ink\": \"violeta\",\n    \"inkHue\": null",
                    "\"ink\": \"tuya\",\n    \"inkHue\": $hue"
                ),
                written
            )
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `a hue outside the circle or as text, beside any ink, rejects the whole file`() {
        val file = String(resource("valid-g01-v10.json"))
        val tuya = "\"ink\": \"tuya\",\n    \"inkHue\": "
        for ((from, to) in listOf(
            "\"inkHue\": null" to "\"inkHue\": 360",
            "\"inkHue\": null" to "\"inkHue\": -1",
            "\"inkHue\": null" to "\"inkHue\": \"22\"",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}360",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "$tuya-1",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}22.5",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}2.2e1",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}\"22\"",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}022",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}99999999999999999999",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}null",
            "\"ink\": \"violeta\",\n    \"inkHue\": null" to "${tuya}true"
        )) {
            val changed = file.replace(from, to)
            check(changed != file)
            assertEquals(
                to,
                BackupRejection.InvalidValue("preferences.inkHue"),
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    @Test
    fun `Tuya's hue kept beside another ink is written and reads back`() {
        val file = String(resource("valid-g01-v10.json"))
        for (hue in listOf(0L, 147L, 359L)) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(tuyaHue = InkHue.of(hue)!!)
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(file.replace("\"inkHue\": null", "\"inkHue\": $hue"), written)
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `a version 8 file without its hue is malformed`() {
        val file = String(resource("valid-g01-v8.json"))
        val missing = file.replace("\n    \"inkHue\": null,", "")
        check(missing != file)

        assertTrue(
            (codec.read(missing.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
        )
    }

    @Test
    fun `Sistema is written with Tuya's kept hue or none, and reads back the same`() {
        val file = String(resource("valid-g01-v10.json"))
        for (hue in listOf(null, 0L, 147L, 359L)) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(
                            ink = InkPreference.Sistema,
                            tuyaHue = hue?.let(InkHue::of)
                        )
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(
                file.replace(
                    "\"ink\": \"violeta\",\n    \"inkHue\": null",
                    "\"ink\": \"sistema\",\n    \"inkHue\": $hue"
                ),
                written
            )
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `a file before version 9 that says Sistema is refused, never read as another ink`() {
        for (name in listOf(
            "valid-g01-v5.json",
            "valid-g01-v6.json",
            "valid-g01-v7.json",
            "valid-g01-v8.json"
        )) {
            val file = String(resource(name))
            val sistema = file.replace("\"ink\": \"violeta\"", "\"ink\": \"sistema\"")
            check(sistema != file)
            assertEquals(
                name,
                BackupRejection.InvalidValue("preferences.ink"),
                (codec.read(sistema.toByteArray()) as Outcome.Failure).error
            )
        }
        // A version 8 file with Tuya's hue beside it is refused just the same.
        val v8 = String(resource("valid-g01-v8.json")).replace(
            "\"ink\": \"violeta\",\n    \"inkHue\": null",
            "\"ink\": \"sistema\",\n    \"inkHue\": 22"
        )
        assertEquals(
            BackupRejection.InvalidValue("preferences.ink"),
            (codec.read(v8.toByteArray()) as Outcome.Failure).error
        )
    }

    @Test
    fun `Sistema spelled any other way rejects the whole file`() {
        val file = String(resource("valid-g01-v10.json"))
        for (to in listOf("\"Sistema\"", "\"system\"", "\"sistema \"", "\"SISTEMA\"")) {
            val changed = file.replace("\"ink\": \"violeta\"", "\"ink\": $to")
            check(changed != file)
            assertEquals(
                to,
                BackupRejection.InvalidValue("preferences.ink"),
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    // --- Gastos hormiga's line (version 10, SELLO-194) ---

    @Test
    fun `Gastos hormiga's line is written in pesos and reads back the same, at every edge`() {
        val file = String(resource("valid-g01-v10.json"))
        for (pesos in listOf(1_000L, 15_000L, 100_000L)) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        smallExpenseLine = SmallExpenseLine.of(pesos)!!
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(
                file.replace("\"smallExpenseLine\": 20000", "\"smallExpenseLine\": $pesos"),
                written
            )
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `every file before version 10 reads with the line at 20000`() {
        for (name in listOf(
            "valid-g01.json",
            "valid-g01-v4.json",
            "valid-g01-v5.json",
            "valid-g01-v6.json",
            "valid-g01-v7.json",
            "valid-g01-v8.json",
            "valid-g01-v9.json"
        )) {
            assertEquals(
                name,
                SmallExpenseLine.of(20_000),
                codec.read(resource(name)).value().contents.preferences?.smallExpenseLine
            )
        }
    }

    @Test
    fun `a line outside 1000 to 100000, or not a bare whole number, rejects the whole file`() {
        val file = String(resource("valid-g01-v10.json"))
        for (to in listOf(
            "999",
            "100001",
            "0",
            "-20000",
            "\"20000\"",
            "20000.0",
            "2e4",
            "020000",
            "null",
            "9223372036854775808"
        )) {
            val changed = file.replace("\"smallExpenseLine\": 20000", "\"smallExpenseLine\": $to")
            check(changed != file)
            assertEquals(
                to,
                BackupRejection.InvalidValue("preferences.smallExpenseLine"),
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    @Test
    fun `a version 10 file without the line is not a version 10 file`() {
        val file = String(resource("valid-g01-v10.json"))
        val missing = file.replace(",\n    \"smallExpenseLine\": 20000", "")
        check(missing != file)
        assertTrue(
            (
                codec.read(
                    missing.toByteArray()
                ) as Outcome.Failure
                ).error is BackupRejection.Malformed
        )
    }

    @Test
    fun `a version 9 file cannot carry a line`() {
        val v9 = String(resource("valid-g01-v9.json"))
        val line = v9.replace(
            "\"investmentOrder\": \"latest_update\"",
            "\"investmentOrder\": \"latest_update\",\n    \"smallExpenseLine\": 15000"
        )
        check(line != v9)
        assertTrue(
            (codec.read(line.toByteArray()) as Outcome.Failure).error is BackupRejection.Malformed
        )
    }

    @Test
    fun `an older version cannot carry Tuya or a hue`() {
        val v7 = String(resource("valid-g01-v7.json"))
        val tuya = v7.replace("\"ink\": \"violeta\"", "\"ink\": \"tuya\"")
        check(tuya != v7)
        assertEquals(
            BackupRejection.InvalidValue("preferences.inkHue"),
            (codec.read(tuya.toByteArray()) as Outcome.Failure).error
        )

        // Version 7 had no inkHue key: a file that adds one is not a version 7 file.
        val hue = v7.replace("\"ink\": \"violeta\",", "\"ink\": \"tuya\",\n    \"inkHue\": 22,")
        assertTrue(
            (codec.read(hue.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
        )
    }

    @Test
    fun `an ink the app never wrote rejects the whole file`() {
        val file = String(resource("valid-g01-v5.json"))
        for (to in listOf("\"purpura\"", "\"Violeta\"", "\"\"")) {
            val changed = file.replace("\"violeta\"", to)
            check(changed != file)
            assertEquals(
                to,
                BackupRejection.InvalidValue("preferences.ink"),
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
                "financialMonth",
                "categories",
                "defaultLimits",
                "monthLimits",
                "expenses",
                "incomes",
                "goals",
                "starredGoals",
                "schedules",
                "investments",
                "usdRates",
                "carryChains"
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
                "inkHue",
                "categoryOrder",
                "categoryDirection",
                "investmentCurrency",
                "investmentOrder",
                "smallExpenseLine"
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
