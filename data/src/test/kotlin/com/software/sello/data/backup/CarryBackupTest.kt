package com.software.sello.data.backup

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.CarryChain
import com.software.sello.domain.model.CategoryCarryInput
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.SettledBalance
import com.software.sello.domain.model.SpendingHistory
import com.software.sello.domain.policy.SignedCarryoverPolicy
import com.software.sello.domain.port.RestoreRejection
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Carry chains in version 6 backups (ADR 0020 CP26, examples SC42–SC45). The files hold
 * SC01's history: Comida, base 100.000 from ene 2027, spent 20.000 in ene, 70.000 in feb
 * and 250.000 in mar, exported on 5 abr 2027; one with the chain open from ene (SC43), one
 * with it ended in abr (SC22). The file holds the months of a chain, never a carry:
 * every expected figure below is copied from docs/testing/signed-carryover-examples.md.
 */
class CarryBackupTest {
    private val defaultCodec = BackupCodec()
    private val open = String(resource("valid-carry-sc01-v6.json"))
    private val ended = String(resource("valid-carry-sc22-v6.json"))

    private fun month(text: String) = YearMonth.parse(text)

    private fun chain(start: String, end: String? = null, category: Int = 1) =
        CarryChain.of(categoryId(category), month(start), end?.let(::month)).value()

    /** SC01's history as the app holds it after reading the file with the chain open. */
    private fun sc01(chains: List<CarryChain>) = BackupContents(
        exportedAt = at("2027-04-05T15:00:00Z"),
        financialMonth = month("2027-04"),
        financial = g01().financial.copy(
            revision = 5,
            categories = listOf(
                category(1, "Comida", createdAt = "2027-01-05T12:00:00Z")
            ),
            defaultLimits = listOf(
                DefaultLimit(
                    categoryId(1),
                    month("2027-01"),
                    AutomaticBudget.Limit(finite(100_000))
                )
            ),
            monthLimits = emptyList(),
            expenses = listOf(
                expense(101, 1, "2027-01-10", 1, 20_000),
                expense(102, 1, "2027-02-10", 2, 70_000),
                expense(103, 1, "2027-03-10", 3, 250_000)
            ),
            incomes = emptyList(),
            carryChains = chains
        ),
        preferences = null
    )

    private fun rejection(text: String, codec: BackupCodec = defaultCodec): BackupRejection =
        when (val read = codec.read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    private fun accepted(text: String) = defaultCodec.read(text.toByteArray()).value()

    private fun variant(from: String, to: String, file: String = open): String {
        assertEquals("'$from' must occur once", 1, file.split(from).size - 1)
        return file.replace(from, to)
    }

    private fun invalid(path: String, fault: ChainFault) = BackupRejection.InvalidChain(path, fault)

    private val chainPath = "financial.carryChains[0]"
    private val openChain = "\"start\": \"2027-01\",\n        \"end\": null"

    // The format and its compatibility.

    @Test
    fun `the hand-written SC01 file reads as the history and one open chain`() {
        val plan = accepted(open)

        assertEquals(sc01(listOf(chain("2027-01"))), plan.contents)
        assertEquals(1, plan.counts.carryChains)
        assertEquals(3, plan.counts.expenses)
    }

    @Test
    fun `the same data is written back byte for byte, with the chain and no carried amount`() {
        for ((file, chains) in listOf(
            open to listOf(chain("2027-01")),
            ended to listOf(chain("2027-01", "2027-04"))
        )) {
            val written = String(defaultCodec.encode(sc01(chains)).value())

            assertEquals(file, written)
            assertFalse(written.contains("carryIn"))
            assertFalse(written.contains("capacity"))
            assertFalse(written.contains("remaining"))
            assertEquals(sc01(chains), accepted(written).contents)
        }
    }

    @Test
    fun `chains are written by category then start, whatever order they were read in`() {
        val comida = chain("2026-10")
        val later = chain("2026-05", "2026-08", category = 2)
        val earlier = chain("2026-02", "2026-03", category = 2)
        val base = g01().financial
        val contents = g01().copy(
            financial = base.copy(
                defaultLimits = base.defaultLimits.filter { it.categoryId == categoryId(1) } +
                    DefaultLimit(
                        categoryId(2),
                        month("2026-01"),
                        AutomaticBudget.Limit(finite(1))
                    ),
                monthLimits = emptyList(),
                carryChains = listOf(later, comida, earlier)
            )
        )

        val written = String(defaultCodec.encode(contents).value())

        assertEquals(
            listOf(
                uuid(1) to "2026-10",
                uuid(2) to "2026-02",
                uuid(2) to "2026-05"
            ),
            Regex("\"categoryId\": \"([0-9a-f-]+)\",\\s+\"start\": \"([0-9-]+)\"")
                .findAll(written).map { it.groupValues[1] to it.groupValues[2] }.toList()
        )
        assertEquals(
            listOf(comida, earlier, later),
            accepted(written).contents.financial.carryChains
        )
    }

    @Test
    fun `files of versions 1 to 5 read with every category off and no starting deficit`() {
        for (name in listOf(
            "valid-g01.json", "valid-g01-v2.json", "valid-g01-v3.json", "valid-g01-v4.json",
            "valid-g01-v5.json", "valid-goals-v2.json", "valid-goals-v3.json",
            "valid-schedules-v3.json", "valid-investments-v4.json"
        )) {
            val plan = defaultCodec.read(resource(name)).value()

            assertEquals(name, emptyList<CarryChain>(), plan.contents.financial.carryChains)
            assertEquals(name, 0, plan.counts.carryChains)
        }
    }

    @Test
    fun `a chain cannot be smuggled into a file of an older version`() {
        for (version in 1..5) {
            val older = variant("\"version\": 6,", "\"version\": $version,")

            assertTrue(
                "version $version",
                rejection(older) is BackupRejection.Malformed
            )
        }
    }

    @Test
    fun `a version 6 file must say its chains, even when there are none`() {
        val without = String(resource("valid-g01-v6.json"))
            .replace(",\n    \"carryChains\": []", "")
        check(!without.contains("carryChains"))

        assertTrue(rejection(without) is BackupRejection.Malformed)
    }

    @Test
    fun `a version this app cannot read is refused by number, not by shape`() {
        assertEquals(
            BackupRejection.UnsupportedVersion(7),
            rejection(variant("\"version\": 6,", "\"version\": 7,"))
        )
    }

    // SELLO-041: the codec the app builds reads and writes chains; nothing refuses them.

    @Test
    fun `the default codec reads a file with a chain, open or ended, and writes one`() {
        for (file in listOf(open, ended)) {
            assertTrue(BackupCodec().read(file.toByteArray()) is Outcome.Success)
        }
        assertTrue(BackupCodec().encode(sc01(listOf(chain("2027-01")))) is Outcome.Success)
    }

    @Test
    fun `the check before a start-up restore takes a chain and still refuses a broken one`() {
        assertTrue(BackupCodec().check(open.toByteArray()) is Outcome.Success)
        assertEquals(
            Outcome.Failure(RestoreRejection.InvalidContent),
            BackupCodec().check(variant("\"end\": null", "\"end\": \"2026-12\"").toByteArray())
        )
        assertEquals(
            Outcome.Failure(RestoreRejection.NewerVersion),
            BackupCodec().check(variant("\"version\": 6,", "\"version\": 7,").toByteArray())
        )
    }

    // Replay after restore (SC43, SC22): the file holds the chain, not the balance.

    private fun replayOf(contents: BackupContents, through: String): List<List<Any>> {
        val financial = contents.financial
        val spending = SpendingHistory.of(
            categoryId(1),
            month("2027-01"),
            month(through),
            LocalDate.parse("2027-04-05"),
            financial.expenses
        ).value()
        val input = CategoryCarryInput(
            categoryId(1),
            financial.carryChains,
            financial.defaultLimits,
            financial.monthLimits,
            spending
        )
        val replay = SignedCarryoverPolicy.replay(input, month("2027-04"), month(through)).value()
        return replay.months.map {
            listOf(
                it.month.toString(),
                it.carryIn.minorUnits,
                it.capacity.minorUnits,
                it.remaining.minorUnits
            )
        } + replay.settled.map { listOf(it) }
    }

    @Test
    fun `SC43 an open chain replays as SC01, a deficit carried into abr`() {
        assertEquals(
            listOf(
                listOf("2027-01", 0L, 100_000L, 80_000L),
                listOf("2027-02", 80_000L, 180_000L, 110_000L),
                listOf("2027-03", 110_000L, 210_000L, -40_000L),
                listOf("2027-04", -40_000L, 60_000L, 60_000L)
            ),
            replayOf(accepted(open).contents, "2027-04")
        )
    }

    @Test
    fun `SC22 a chain ended in abr settles at minus 40000 and abr is its base alone`() {
        assertEquals(
            listOf(
                listOf("2027-01", 0L, 100_000L, 80_000L),
                listOf("2027-02", 80_000L, 180_000L, 110_000L),
                listOf("2027-03", 110_000L, 210_000L, -40_000L),
                listOf(SettledBalance(month("2027-01"), month("2027-04"), money(-40_000)))
            ),
            replayOf(accepted(ended).contents, "2027-04")
        )
    }

    @Test
    fun `a negative carry is not an expense, and an expense still cannot be negative`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.expenses[0].amount"),
            rejection(variant("\"amount\": 20000,", "\"amount\": -20000,"))
        )
        // A carry is not in the file at all, so none can be written as one.
        assertTrue(
            rejection(
                variant(
                    openChain,
                    "\"start\": \"2027-01\",\n        \"end\": null,\n        \"carryIn\": -40000"
                )
            )
                is BackupRejection.Malformed
        )
        assertTrue(
            rejection(
                variant(
                    openChain,
                    "\"start\": \"2027-01\",\n        \"carryIn\": -40000,\n        \"end\": null"
                )
            )
                is BackupRejection.Malformed
        )
    }

    // SC44: files rejected before the preview.

    @Test
    fun `overlapping chains of one category are rejected, naming the later one`() {
        val second = "{\n        \"categoryId\": \"${uuid(
            1
        )}\",\n        \"start\": \"2027-03\",\n        \"end\": \"2027-04\"\n      }"
        val overlapping = variant(
            "      }\n    ]\n  },\n  \"preferences\"",
            "      },\n      $second\n    ]\n  },\n  \"preferences\""
        )

        assertEquals(
            invalid("financial.carryChains[1]", ChainFault.Overlap),
            rejection(overlapping)
        )
        // The same two months, the first one ended in mar, touch but do not overlap.
        accepted(
            variant("\"end\": null", "\"end\": \"2027-03\"").replace(
                "      }\n    ]\n  },\n  \"preferences\"",
                "      },\n      {\n        \"categoryId\": \"${uuid(
                    1
                )}\",\n        \"start\": \"2027-03\",\n        \"end\": null\n      }\n    ]\n  },\n  \"preferences\""
            )
        )
    }

    @Test
    fun `a chain that ends before it starts or in its start month is rejected`() {
        for (end in listOf("2026-12", "2027-01")) {
            assertEquals(
                end,
                BackupRejection.InvalidValue("$chainPath.end"),
                rejection(variant("\"end\": null", "\"end\": \"$end\""))
            )
        }
    }

    @Test
    fun `a chain with the same category and start twice is rejected`() {
        val twice = variant(
            "      }\n    ]\n  },\n  \"preferences\"",
            "      },\n      {\n        \"categoryId\": \"${uuid(
                1
            )}\",\n        \"start\": \"2027-01\",\n        \"end\": \"2027-02\"\n      }\n    ]\n  },\n  \"preferences\""
        )

        assertEquals(
            BackupRejection.DuplicateEntry("financial.carryChains[1].start"),
            rejection(twice)
        )
    }

    @Test
    fun `a chain of a category the file does not hold is rejected`() {
        assertEquals(
            BackupRejection.DanglingReference("$chainPath.categoryId"),
            rejection(
                variant(
                    "\"categoryId\": \"${uuid(1)}\",\n        \"start\"",
                    "\"categoryId\": \"${uuid(9)}\",\n        \"start\""
                )
            )
        )
    }

    @Test
    fun `a month that is not a real month is rejected where it is written`() {
        for ((from, to, path) in listOf(
            Triple("\"start\": \"2027-01\"", "\"start\": \"2027-13\"", "$chainPath.start"),
            Triple("\"start\": \"2027-01\"", "\"start\": \"2027-1\"", "$chainPath.start"),
            Triple("\"end\": null", "\"end\": \"2027-04-01\"", "$chainPath.end"),
            Triple("\"end\": null", "\"end\": \"\"", "$chainPath.end")
        )) {
            assertEquals(to, BackupRejection.InvalidValue(path), rejection(variant(from, to)))
        }
    }

    @Test
    fun `a chain whose first month has an unlimited or paused limit is rejected`() {
        assertEquals(
            invalid(chainPath, ChainFault.NoFiniteBase),
            rejection(unlimited(open))
        )
        assertEquals(
            invalid(chainPath, ChainFault.NoFiniteBase),
            rejection(
                variant(
                    "\"kind\": \"finite\",\n        \"amount\": 100000,\n        \"currency\": \"COP\"\n      }\n    ],\n    \"monthLimits\"",
                    "\"kind\": \"paused\",\n        \"amount\": null,\n        \"currency\": null\n      }\n    ],\n    \"monthLimits\""
                )
            )
        )
    }

    /** The file with its one default made unlimited. */
    private fun unlimited(file: String) = file.replace(
        "\"kind\": \"finite\",\n        \"amount\": 100000,\n        \"currency\": \"COP\"",
        "\"kind\": \"unlimited\",\n        \"amount\": null,\n        \"currency\": null"
    ).also { check(it != file) }

    @Test
    fun `a first month finite only through its own limit leaves the next month without a base`() {
        // SC46 in a file: abr's own limit is finite, but the default every later month uses is not.
        val own = unlimited(open).replace(
            "\"monthLimits\": [],",
            "\"monthLimits\": [\n      {\n        \"categoryId\": \"${uuid(
                1
            )}\",\n        \"month\": \"2027-01\",\n        \"kind\": \"finite\",\n        \"amount\": 100000,\n        \"currency\": \"COP\"\n      }\n    ],"
        )

        assertEquals(invalid(chainPath, ChainFault.NoFiniteBase), rejection(own))
    }

    @Test
    fun `an open chain whose latest default is unlimited is rejected, an ended one is not`() {
        val later = listOf(
            "      },",
            "      {",
            "        \"categoryId\": \"${uuid(1)}\",",
            "        \"from\": \"2027-06\",",
            "        \"kind\": \"unlimited\",",
            "        \"amount\": null,",
            "        \"currency\": null",
            "      }",
            "    ],",
            "    \"monthLimits\""
        ).joinToString("\n")
        val closing = "      }\n    ],\n    \"monthLimits\""

        assertEquals(
            invalid(chainPath, ChainFault.NoFiniteBase),
            rejection(variant(closing, later))
        )
        // Ended in abr, the chain never reaches jun: the later default is not its business.
        accepted(variant(closing, later, ended))
    }

    @Test
    fun `a chain with a base above 999999999999 is rejected, the largest base is accepted`() {
        accepted(variant("\"amount\": 100000,", "\"amount\": 999999999999,"))
        for (amount in listOf("1000000000000", "9223372036854775807")) {
            assertEquals(
                amount,
                invalid(chainPath, ChainFault.BaseAboveLimit),
                rejection(variant("\"amount\": 100000,", "\"amount\": $amount,"))
            )
        }
        // A number that is not a signed Long at all is not read, let alone wrapped.
        assertEquals(
            BackupRejection.InvalidValue("financial.defaultLimits[0].amount"),
            rejection(variant("\"amount\": 100000,", "\"amount\": 9223372036854775808,"))
        )
    }

    @Test
    fun `a month limit above the cap is rejected only inside the months the chain carries`() {
        val inChain = listOf(
            "\"monthLimits\": [",
            "      {",
            "        \"categoryId\": \"${uuid(1)}\",",
            "        \"month\": \"2027-02\",",
            "        \"kind\": \"finite\",",
            "        \"amount\": 1000000000000,",
            "        \"currency\": \"COP\"",
            "      }",
            "    ],"
        ).joinToString("\n")
        val outside = inChain.replace("2027-02", "2027-04")

        assertEquals(
            invalid(chainPath, ChainFault.BaseAboveLimit),
            rejection(variant("\"monthLimits\": [],", inChain))
        )
        // Ended in abr: abr is outside the chain, and may hold any limit (CP25 is for chain months).
        accepted(variant("\"monthLimits\": [],", outside, ended))
        // The same limit in an open chain's month is inside it.
        assertEquals(
            invalid(chainPath, ChainFault.BaseAboveLimit),
            rejection(variant("\"monthLimits\": [],", outside))
        )
    }

    // Chains no Sello could have written (CP3, CP9, CP11).

    @Test
    fun `an open chain on an archived category is rejected, an ended one is not`() {
        val archived = "\"archived\": true"

        assertEquals(
            invalid(chainPath, ChainFault.OpenOnArchivedCategory),
            rejection(variant("\"archived\": false", archived))
        )
        // Archiving ended the chain in the month it happened: that history is possible.
        accepted(variant("\"archived\": false", archived, ended))
    }

    @Test
    fun `a chain that starts or ends after the financial month of the file is rejected`() {
        // Carry starts in the current month only, and a chain ends by a command, not ahead.
        accepted(variant("\"start\": \"2027-01\"", "\"start\": \"2027-04\""))
        val later = variant("\"start\": \"2027-01\"", "\"start\": \"2027-05\"")
        for (file in listOf(later, later.replace("\"end\": null", "\"end\": \"2027-06\""))) {
            assertEquals(invalid(chainPath, ChainFault.AfterFinancialMonth), rejection(file))
        }
        // R1: a chain ended in sep 2027 in a file made in abr 2027 would drop its balance
        // by itself, with no confirmation: it is refused. Ending in the month is allowed.
        assertEquals(
            invalid(chainPath, ChainFault.AfterFinancialMonth),
            rejection(variant("\"end\": \"2027-04\"", "\"end\": \"2027-09\"", ended))
        )
        assertEquals(
            invalid(chainPath, ChainFault.AfterFinancialMonth),
            rejection(variant("\"end\": null", "\"end\": \"2027-05\""))
        )
        accepted(ended)
    }

    @Test
    fun `the financial month decides, not the audit time the file was made at`() {
        val audit = "\"exportedAt\": \"2027-04-05T15:00:00Z\""
        // Made at 2026-10 by the audit clock, in the financial month 2027-04: accepted.
        accepted(variant(audit, "\"exportedAt\": \"2026-10-09T15:04:05.678Z\""))
        // The same chain with a financial month before it: refused whatever the audit says.
        val earlier = variant("\"financialMonth\": \"2027-04\"", "\"financialMonth\": \"2026-12\"")
        assertEquals(invalid(chainPath, ChainFault.AfterFinancialMonth), rejection(earlier))
        // A file made late in the evening of 30 abr in Bogota, already 1 may in UTC, is abr.
        accepted(variant(audit, "\"exportedAt\": \"2027-05-01T03:00:00Z\""))
    }

    @Test
    fun `a sandbox history with the financial clock ahead of the audit clock round-trips`() {
        // Financial clock jun 2027, audit clock oct 2026: a chain starts in jun 2027.
        val contents = sc01(listOf(chain("2027-06"))).copy(
            exportedAt = at("2026-10-09T15:04:05.678Z"),
            financialMonth = month("2027-06")
        )

        val written = String(defaultCodec.encode(contents).value())

        assertTrue(written.contains("\"financialMonth\": \"2027-06\""))
        assertTrue(written.contains("\"exportedAt\": \"2026-10-09T15:04:05.678Z\""))
        assertEquals(contents, accepted(written).contents)
    }

    @Test
    fun `a clock moved back still writes a month no chain passes`() {
        // The clock reads mar 2027 but a chain began in apr and one ended in may: the file
        // says may, the latest month any chain names, so Sello reads back what it wrote.
        val contents = sc01(listOf(chain("2027-04", "2027-05"))).copy(
            financialMonth = month("2027-03")
        )

        val written = String(defaultCodec.encode(contents).value())

        assertTrue(written.contains("\"financialMonth\": \"2027-05\""))
        assertEquals(
            contents.copy(financialMonth = month("2027-05")),
            accepted(written).contents
        )
    }

    @Test
    fun `a version 6 file must say a valid financial month`() {
        val said = "    \"financialMonth\": \"2027-04\",\n"
        val without = variant(said, "")
        assertTrue(rejection(without) is BackupRejection.Malformed)
        for (text in listOf("\"2027-13\"", "\"2027-4\"", "\"2027-04-01\"", "\"\"", "null")) {
            assertEquals(
                text,
                BackupRejection.InvalidValue("financial.financialMonth"),
                rejection(variant("\"financialMonth\": \"2027-04\"", "\"financialMonth\": $text"))
            )
        }
        assertTrue(
            rejection(variant("\"financialMonth\": \"2027-04\"", "\"financialMonth\": 202704"))
                is BackupRejection.Malformed
        )
    }

    @Test
    fun `an older file has no financial month, and one written into it is malformed`() {
        val older = variant("\"version\": 6,", "\"version\": 5,")

        assertTrue(rejection(older) is BackupRejection.Malformed)
        // Read from its own version, the month is that of its export in its zone.
        val plan = defaultCodec.read(resource("valid-g01-v5.json")).value()
        assertEquals(month("2026-10"), plan.contents.financialMonth)
    }

    @Test
    fun `a limit of zero is a finite base and is accepted`() {
        accepted(variant("\"amount\": 100000,", "\"amount\": 0,"))
    }

    @Test
    fun `chains count toward the limit on records`() {
        // 1 category, 1 default, 3 expenses and 1 chain are the file's only array elements.
        assertEquals(
            BackupRejection.TooManyRecords(5),
            rejection(open, BackupCodec(BackupBounds(maxRecords = 5)))
        )
        assertEquals(
            1,
            BackupCodec(BackupBounds(maxRecords = 6))
                .read(open.toByteArray()).value().counts.carryChains
        )
    }

    private fun money(pesos: Long) = Money.cop(pesos)
}
