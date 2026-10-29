package com.software.sello.data.backup

import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentDescription
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.policy.InvestmentPolicy
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Investments and USD rates in version 4 backups. The file holds approved examples IN04
 * (Fondo, with one deleted movement leaving a gap at position 3), IN05 (Ecopetrol, with
 * its dividends) and IN27 (Acciones EE. UU., in USD), and the rates 4.000,00 and
 * 4.150,25 (docs/testing/investment-examples.md). Every expected value below is written
 * out by hand; every malformed case breaks one thing of that file.
 */
class InvestmentBackupTest {
    private val codec = BackupCodec()
    private val file = String(resource("valid-investments-v4.json"))

    private fun variant(from: String, to: String, occurrence: Int = 1): String {
        val parts = file.split(from)
        assertTrue("'$from' must occur at least $occurrence times", parts.size - 1 >= occurrence)
        return parts.take(occurrence).joinToString(from) + to +
            parts.drop(occurrence).joinToString(from)
    }

    private fun rejection(text: String, bounds: BackupBounds = BackupBounds()): BackupRejection =
        when (val read = BackupCodec(bounds).read(text.toByteArray())) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    private fun accepted(text: String) = codec.read(text.toByteArray()).value()

    private fun d(text: String) = LocalDate.parse(text)

    private fun move(
        number: Int,
        sequence: Long,
        kind: InvestmentMovementKind,
        units: Long,
        currency: Currency,
        date: String,
        at: String,
        note: String? = null,
        updatedAt: String = at
    ) = InvestmentMovement(
        id = InvestmentMovementId.of(uuid(number)).value(),
        kind = kind,
        amount = if (kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(Money(units, currency)).value()
        } else {
            InvestmentAmount.of(Money(units, currency)).value()
        },
        date = d(date),
        sequence = sequence,
        note = Note.of(note).value(),
        version = 1,
        createdAt = at(at),
        updatedAt = at(updatedAt)
    )

    private fun investment(
        number: Int,
        name: String,
        description: String?,
        icon: String,
        currency: Currency,
        createdAt: String,
        updatedAt: String
    ) = Investment(
        id = InvestmentId.of(uuid(number)).value(),
        name = InvestmentName.of(name).value(),
        description = InvestmentDescription.of(description).value(),
        icon = IconKey.of(icon).value(),
        currency = currency,
        version = 1,
        createdAt = at(createdAt),
        updatedAt = at(updatedAt)
    )

    private val contribution = InvestmentMovementKind.Contribution
    private val withdrawal = InvestmentMovementKind.Withdrawal
    private val dividend = InvestmentMovementKind.Dividend
    private val valuation = InvestmentMovementKind.Valuation
    private val cop = Currency.COP
    private val usd = Currency.USD

    private val fondo = InvestmentHistory(
        investment(
            301,
            "Fondo",
            "Fondo de inversión colectiva",
            "savings",
            cop,
            "2025-05-03T14:00:00Z",
            "2026-10-14T13:00:00Z"
        ),
        listOf(
            move(401, 0, contribution, 3_000_000, cop, "2025-05-03", "2025-05-03T14:00:00Z"),
            move(402, 1, valuation, 3_000_000, cop, "2025-05-03", "2025-05-03T14:00:00Z"),
            move(403, 2, contribution, 3_000_000, cop, "2025-11-03", "2025-11-03T15:30:00Z"),
            move(
                404,
                4,
                withdrawal,
                500_000,
                cop,
                "2026-08-03",
                "2026-08-03T16:00:00Z",
                note = "Matrícula",
                updatedAt = "2026-08-04T09:15:30.250Z"
            ),
            move(405, 5, valuation, 5_242_000, cop, "2026-10-14", "2026-10-14T13:00:00Z")
        )
    )

    private val ecopetrol = InvestmentHistory(
        investment(
            302,
            "Ecopetrol",
            null,
            "trending_up",
            cop,
            "2026-03-02T15:00:00Z",
            "2026-10-09T12:00:00Z"
        ),
        listOf(
            move(411, 0, contribution, 3_000_000, cop, "2026-03-02", "2026-03-02T15:00:00Z"),
            move(412, 1, valuation, 3_000_000, cop, "2026-03-02", "2026-03-02T15:00:00Z"),
            move(
                413,
                2,
                dividend,
                89_000,
                cop,
                "2026-06-15",
                "2026-06-15T17:00:00Z",
                note = "Dividendo ordinario"
            ),
            move(414, 3, dividend, 89_000, cop, "2026-09-15", "2026-09-15T17:00:00Z"),
            move(415, 4, valuation, 3_050_000, cop, "2026-10-09", "2026-10-09T12:00:00Z")
        )
    )

    /** USD amounts in cents: USD 1.000,00 and USD 1.050,50. */
    private val acciones = InvestmentHistory(
        investment(
            303,
            "Acciones EE. UU.",
            "Broker",
            "show_chart",
            usd,
            "2026-10-20T16:00:00Z",
            "2026-10-20T16:00:00Z"
        ),
        listOf(
            move(421, 0, contribution, 100_000, usd, "2026-10-20", "2026-10-20T16:00:00Z"),
            move(422, 1, valuation, 105_050, usd, "2026-10-20", "2026-10-20T16:00:00Z")
        )
    )

    private fun rate(number: Int, hundredths: Long, date: String, sequence: Long, at: String) =
        UsdRateEntry(
            id = UsdRateId.of(uuid(number)).value(),
            rate = UsdRate.ofHundredths(hundredths).value(),
            date = d(date),
            sequence = sequence,
            createdAt = at(at)
        )

    private val rates = listOf(
        rate(501, 400_000, "2026-10-15", 0, "2026-10-15T14:00:00Z"),
        rate(502, 415_025, "2026-10-20", 1, "2026-10-20T16:00:00Z")
    )

    @Test
    fun `investments their movements and the rates read back exactly as written`() {
        val plan = accepted(file)

        assertEquals(listOf(fondo, ecopetrol, acciones), plan.contents.financial.investments)
        assertEquals(rates, plan.contents.financial.usdRates)
        assertEquals(
            listOf(400_000L, 415_025L),
            plan.contents.financial.usdRates.map { it.rate.hundredths }
        )
        assertEquals(
            Triple(3, 12, 2),
            Triple(plan.counts.investments, plan.counts.investmentMovements, plan.counts.usdRates)
        )
    }

    @Test
    fun `the file works out to the examples own figures`() {
        val figures = accepted(file).contents.financial.investments.map {
            InvestmentPolicy.figures(it).value()
        }

        // IN04: G = 5.242.000 + 500.000 − 6.000.000; IN05: 3.050.000 + 178.000 − 3.000.000;
        // IN27: USD 1.050,50 − USD 1.000,00.
        assertEquals(
            listOf(Money.cop(-258_000), Money.cop(228_000), Money(5_050, Currency.USD)),
            figures.map { it.gain }
        )
    }

    @Test
    fun `the same contents always make the same file`() {
        assertEquals(
            version4AsVersion6(file),
            String(codec.encode(accepted(file).contents).value())
        )
    }

    @Test
    fun `investments and rates built in code survive a round trip exactly`() {
        val contents = g01().let {
            it.copy(
                financial = it.financial.copy(
                    // Out of order on purpose: the file writes them in its own order.
                    investments = listOf(acciones, fondo),
                    usdRates = rates.reversed()
                )
            )
        }

        val restored = codec.read(codec.encode(contents).value()).value().contents

        assertEquals(listOf(fondo, acciones), restored.financial.investments)
        assertEquals(rates, restored.financial.usdRates)
        assertEquals(
            listOf(UsdRate.ofHundredths(400_000).value(), UsdRate.ofHundredths(415_025).value()),
            restored.financial.usdRates.map { it.rate }
        )
    }

    @Test
    fun `version 1 2 and 3 files still read, with no investments and no rates`() {
        for (
        name in listOf(
            "valid-g01.json",
            "valid-g01-v2.json",
            "valid-goals-v2.json",
            "valid-g01-v3.json",
            "valid-goals-v3.json",
            "valid-schedules-v3.json"
        )
        ) {
            val financial = codec.read(resource(name)).value().contents.financial

            assertEquals(name, emptyList<InvestmentHistory>(), financial.investments)
            assertEquals(name, emptyList<UsdRateEntry>(), financial.usdRates)
        }
    }

    @Test
    fun `a version 3 file takes no investments and a version 4 file needs both lists`() {
        val asVersion3 = variant("\"version\": 4,", "\"version\": 3,")
        val noRates = variant(
            ",\n    \"usdRates\": [",
            ",\n    \"rates\": ["
        )

        assertTrue(rejection(asVersion3) is BackupRejection.Malformed)
        assertTrue(rejection(noRates) is BackupRejection.Malformed)
    }

    @Test
    fun `a rate is whole hundredths within its bounds, never a decimal number`() {
        val path = "financial.usdRates[1]"
        val cases = listOf(
            // Three decimals cannot be written as hundredths, and are never rounded.
            "4150.255",
            "415025.5",
            "415025.0",
            "4.15025e5",
            "\"415025\"",
            // 0,99 and 100.000,01 pesos per dollar.
            "99",
            "10000001",
            "-415025"
        )
        for (literal in cases) {
            assertEquals(
                literal,
                BackupRejection.InvalidValue("$path.rateHundredths"),
                rejection(variant("\"rateHundredths\": 415025", "\"rateHundredths\": $literal"))
            )
        }
        // 1,00 and 100.000,00 are the bounds themselves.
        for (hundredths in listOf(100L, 10_000_000L)) {
            val read = accepted(
                variant("\"rateHundredths\": 415025", "\"rateHundredths\": $hundredths")
            )
            assertEquals(hundredths, read.contents.financial.usdRates[1].rate.hundredths)
        }
    }

    @Test
    fun `rates have their own identifiers entry positions and real dates`() {
        val path = "financial.usdRates[1]"
        val cases = listOf(
            variant(
                "\"sequence\": 1,\n        \"createdAt\": \"2026-10-20T16:00:00Z\"\n      }\n    ]",
                "\"sequence\": 0,\n        \"createdAt\": \"2026-10-20T16:00:00Z\"\n      }\n    ]"
            ) to
                BackupRejection.DuplicateEntry("$path.sequence"),
            variant(
                "\"id\": \"00000000-0000-4000-8000-000000000502\"",
                "\"id\": \"00000000-0000-4000-8000-000000000501\""
            ) to BackupRejection.DuplicateId("$path.id"),
            variant(
                "\"date\": \"2026-10-20\",\n        \"sequence\": 1",
                "\"date\": \"2026-02-30\",\n        \"sequence\": 1"
            ) to
                BackupRejection.InvalidValue("$path.date")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    @Test
    fun `every movement amount is within its bounds and in its investments currency`() {
        val fondoAt = "financial.investments[0].movements"
        val accionesAt = "financial.investments[2].movements"
        val cases = listOf(
            // An aporte of 0: only a recorded value may be 0 (rule 2).
            variant("\"amount\": 3000000", "\"amount\": 0") to
                BackupRejection.AmountOutOfRange("$fondoAt[0].amount"),
            variant("\"amount\": 89000", "\"amount\": 0") to
                BackupRejection.AmountOutOfRange("financial.investments[1].movements[2].amount"),
            variant("\"amount\": 3000000", "\"amount\": 1000000000000") to
                BackupRejection.AmountOutOfRange("$fondoAt[0].amount"),
            variant("\"amount\": 3000000", "\"amount\": 3000000.0") to
                BackupRejection.InvalidValue("$fondoAt[0].amount"),
            // USD 1.000,00 written as pesos in a USD investment.
            variant(
                "\"amount\": 100000,\n            \"currency\": \"USD\"",
                "\"amount\": 100000,\n            \"currency\": \"COP\""
            ) to
                BackupRejection.UnsupportedCurrency("$accionesAt[0].currency"),
            variant(
                "\"currency\": \"USD\",\n        \"createdAt\"",
                "\"currency\": \"EUR\",\n        \"createdAt\""
            ) to
                BackupRejection.UnsupportedCurrency("financial.investments[2].currency")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    @Test
    fun `a recorded value may be 0 and the largest amounts are kept`() {
        val zero = accepted(variant("\"amount\": 5242000", "\"amount\": 0"))
        val largest = accepted(variant("\"amount\": 105050", "\"amount\": 999999999999"))

        assertEquals(
            Money.cop(0),
            zero.contents.financial.investments[0].movements[4].amount.money
        )
        assertEquals(
            Money(999_999_999_999, Currency.USD),
            largest.contents.financial.investments[2].movements[1].amount.money
        )
    }

    @Test
    fun `movements keep their recording positions and identities`() {
        val at = "financial.investments[0].movements"
        val cases = listOf(
            // The retiro at position 4 moved onto the aporte's 2.
            variant(
                "\"sequence\": 4,\n            \"kind\": \"withdrawal\"",
                "\"sequence\": 2,\n            \"kind\": \"withdrawal\""
            ) to
                BackupRejection.DuplicateEntry("$at[3].sequence"),
            variant(
                "\"id\": \"00000000-0000-4000-8000-000000000405\"",
                "\"id\": \"00000000-0000-4000-8000-000000000401\""
            ) to BackupRejection.DuplicateId("$at[4].id"),
            variant(
                "\"id\": \"00000000-0000-4000-8000-000000000302\"",
                "\"id\": \"00000000-0000-4000-8000-000000000301\""
            ) to BackupRejection.DuplicateId("financial.investments[1].id"),
            variant("\"kind\": \"withdrawal\"", "\"kind\": \"retiro\"") to
                BackupRejection.InvalidValue("$at[3].kind"),
            variant("\"date\": \"2026-08-03\"", "\"date\": \"2026-02-30\"") to
                BackupRejection.InvalidValue("$at[3].date"),
            variant("\"sequence\": 5,", "\"sequence\": 9223372036854775807,") to
                BackupRejection.InvalidValue("$at[4].sequence")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    /** Nested in its investment, a movement cannot point at another one. */
    @Test
    fun `a movement names no investment of its own`() {
        val pointing = variant(
            "\"id\": \"00000000-0000-4000-8000-000000000401\",",
            "\"id\": \"00000000-0000-4000-8000-000000000401\",\n" +
                "            \"investmentId\": \"00000000-0000-4000-8000-000000000399\","
        )

        assertTrue(rejection(pointing) is BackupRejection.Malformed)
    }

    @Test
    fun `names descriptions and notes are in stored form`() {
        val cases = listOf(
            variant("\"name\": \"Fondo\"", "\"name\": \" Fondo\"") to
                BackupRejection.InvalidValue("financial.investments[0].name"),
            variant("\"name\": \"Fondo\"", "\"name\": \"${"F".repeat(41)}\"") to
                BackupRejection.InvalidValue("financial.investments[0].name"),
            variant("\"description\": \"Broker\"", "\"description\": \"\"") to
                BackupRejection.InvalidValue("financial.investments[2].description"),
            variant("\"description\": \"Broker\"", "\"description\": \"${"B".repeat(41)}\"") to
                BackupRejection.InvalidValue("financial.investments[2].description"),
            variant("\"note\": \"Matrícula\"", "\"note\": \"\"") to
                BackupRejection.InvalidValue("financial.investments[0].movements[3].note"),
            variant("\"icon\": \"savings\"", "\"icon\": \"Savings\"") to
                BackupRejection.InvalidValue("financial.investments[0].icon")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
    }

    /** A history the rules refuse is a bad file: no changes, never a repaired figure. */
    @Test
    fun `every history must work out under the investment rules`() {
        val cases = listOf(
            // IN04's retiro above the 6.000.000 it was worth on 3 August 2026 (rule 8).
            variant("\"amount\": 500000", "\"amount\": 6000001") to
                BackupRejection.InvalidValue("financial.investments[0].movements[3].amount"),
            // A dividend before Ecopetrol's first aporte (rule 3).
            variant("\"date\": \"2026-06-15\"", "\"date\": \"2026-01-15\"") to
                BackupRejection.InvalidValue("financial.investments[1].movements[2].date"),
            // Acciones with no aporte at all (rule 6).
            variant("\"kind\": \"contribution\"", "\"kind\": \"dividend\"", occurrence = 4) to
                BackupRejection.InvalidValue("financial.investments[2].movements"),
            // A retiro with no value recorded before it (rule 7).
            variant(
                "\"sequence\": 1,\n            \"kind\": \"valuation\",\n            \"amount\": 105050",
                "\"sequence\": 1,\n            \"kind\": \"withdrawal\",\n            \"amount\": 105050"
            ) to BackupRejection.InvalidValue("financial.investments[2].movements[1].kind"),
            // An investment with no movements has no aporte either.
            Regex(
                "\"movements\": \\[\n          \\{\n            \"id\": \"00000000-0000-4000-8000-000000000421\"[\\s\\S]*?\n        \\]"
            )
                .replace(file, "\"movements\": []") to
                BackupRejection.InvalidValue("financial.investments[2].movements")
        )
        for ((text, expected) in cases) assertEquals(expected, rejection(text))
        // 6.000.000 exactly is what it was worth: accepted, value 0 from then on.
        val all = accepted(variant("\"amount\": 500000", "\"amount\": 6000000"))
        assertEquals(
            Money.cop(5_242_000),
            InvestmentPolicy.figures(all.contents.financial.investments[0]).value().value?.amount
        )
    }

    @Test
    fun `investments and rates count toward the record bound`() {
        // 3 investments, 12 movements and 2 rates are the file's only array elements.
        assertEquals(
            BackupRejection.TooManyRecords(16),
            rejection(file, BackupBounds(maxRecords = 16))
        )
        assertEquals(
            3,
            BackupCodec(
                BackupBounds(maxRecords = 17)
            ).read(file.toByteArray()).value().counts.investments
        )
    }
}
