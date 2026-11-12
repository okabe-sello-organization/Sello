package com.software.sello.data.backup

import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.ExactTotal
import com.software.sello.domain.model.GuidanceMemory
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkColour
import com.software.sello.domain.model.InkHue
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.InvestmentOrder
import com.software.sello.domain.model.InvestmentPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Nudge
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ShownNudge
import com.software.sello.domain.model.SmallExpenseLine
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.TuyaColour
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /**
     * The file Sello writes now for contents with no guidance, as when the phone's could not
     * be read, and no colour for Tuya: version 12 with `"guidance": null`.
     */
    private fun currentWithoutGuidance() =
        version11AsVersion12(version10AsVersion11(String(resource("valid-g01-v10.json"))))

    /** What `valid-g01-v11.json` says was seen. */
    private val guidanceSample = GuidanceMemory(
        seen = setOf(Nudge.GuideHint, Nudge.BulkEntry),
        lastShown = ShownNudge(Nudge.GuideHint, LocalDate.of(2026, 10, 20)),
        backupReminderShown = LocalDate.of(2026, 10, 1),
        backupReminderOff = false
    )

    private fun g01WithGuidance(guidance: GuidanceMemory? = guidanceSample) =
        g01InDollars().let { it.copy(preferences = it.preferences!!.copy(guidance = guidance)) }

    /** What `valid-g01-v12.json` holds: G01 in Violeta, Tuya's kept colour #F2C200. */
    private fun g01WithColour() = g01WithGuidance().let {
        it.copy(
            preferences = it.preferences!!.copy(
                appearance = it.preferences.appearance.copy(tuya = exact(0xF2C200))
            )
        )
    }

    private fun exact(rgb: Long) = TuyaColour.Exact(InkColour.of(rgb)!!)

    @Test
    fun `G01 is written byte for byte as the documented version 12 file`() {
        assertEquals(
            String(resource("valid-g01-v12.json")),
            String(codec.encode(g01WithColour()).value())
        )
    }

    @Test
    fun `the version 12 file reads back with the same colour and guidance`() {
        assertEquals(g01WithColour(), codec.read(resource("valid-g01-v12.json")).value().contents)
    }

    @Test
    fun `the version 11 file reads back with the same guidance`() {
        assertEquals(g01WithGuidance(), codec.read(resource("valid-g01-v11.json")).value().contents)
    }

    @Test
    fun `the version 11 file is written now as version 12 with no colour`() {
        val v11 = String(resource("valid-g01-v11.json"))
        val contents = codec.read(v11.toByteArray()).value().contents

        assertEquals(version11AsVersion12(v11), String(codec.encode(contents).value()))
    }

    @Test
    fun `contents whose guidance could not be read are written with none and read back so`() {
        val written = String(codec.encode(g01WithGuidance(null)).value())

        assertEquals(currentWithoutGuidance(), written)
        assertEquals(g01WithGuidance(null), codec.read(written.toByteArray()).value().contents)
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
        val file = currentWithoutGuidance()
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
                    tuya = (ink as? InkPreference.Tuya)?.colour
                )
            )
        )
    }

    @Test
    fun `Tuya is written with its hue and reads back the same, at every edge`() {
        val file = currentWithoutGuidance()
        for (hue in listOf(0L, 22L, 359L)) {
            val contents = g01In(InkPreference.Tuya(TuyaColour.Hue(InkHue.of(hue)!!)))
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
        val file = currentWithoutGuidance()
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
        val file = currentWithoutGuidance()
        for (hue in listOf(0L, 147L, 359L)) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(
                            tuya = TuyaColour.Hue(InkHue.of(hue)!!)
                        )
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(file.replace("\"inkHue\": null", "\"inkHue\": $hue"), written)
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    /** The current file's Violeta with no Tuya, as [currentWithoutGuidance] writes it. */
    private val violetaAlone =
        "\"ink\": \"violeta\",\n    \"inkHue\": null,\n    \"inkColour\": null"

    @Test
    fun `Tuya is written with its colour as #RRGGBB and reads back the same, at every edge`() {
        val file = currentWithoutGuidance()
        for ((rgb, text) in listOf(
            0x000000L to "#000000",
            0x0A0B0CL to "#0A0B0C",
            0xF2C200L to "#F2C200",
            0xFFFFFFL to "#FFFFFF"
        )) {
            val contents = g01In(InkPreference.Tuya(exact(rgb)))
            val written = String(codec.encode(contents).value())
            assertEquals(
                text,
                file.replace(
                    violetaAlone,
                    "\"ink\": \"tuya\",\n    \"inkHue\": null,\n    \"inkColour\": \"$text\""
                ),
                written
            )
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `Tuya's colour kept beside another ink is written and reads back`() {
        val file = currentWithoutGuidance()
        for ((ink, id) in listOf(
            InkPreference.Cobalto to "cobalto",
            InkPreference.Violeta to "violeta",
            InkPreference.Sistema to "sistema"
        )) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(
                            ink = ink,
                            tuya = exact(0x808080)
                        )
                    )
                )
            }
            val written = String(codec.encode(contents).value())
            assertEquals(
                file.replace(
                    violetaAlone,
                    "\"ink\": \"$id\",\n    \"inkHue\": null,\n    \"inkColour\": \"#808080\""
                ),
                written
            )
            assertEquals(contents, codec.read(written.toByteArray()).value().contents)
        }
    }

    @Test
    fun `a colour spelled any other way, beside any ink, rejects the whole file`() {
        val file = currentWithoutGuidance()
        for (ink in listOf("violeta", "tuya")) {
            for (colour in listOf(
                "\"#f2c200\"",
                "\"#F2c200\"",
                "\"F2C200\"",
                "\"#F2C20\"",
                "\"#F2C2000\"",
                "\"#FF2C2000\"",
                "\"#GGGGGG\"",
                "\"# F2C200\"",
                "\"#F2C200 \"",
                "\"#-12345\"",
                "\"#+12345\"",
                "\"\"",
                "15909376",
                "true",
                "[]"
            )) {
                val changed = file.replace(
                    violetaAlone,
                    "\"ink\": \"$ink\",\n    \"inkHue\": null,\n    \"inkColour\": $colour"
                )
                check(changed != file)
                assertEquals(
                    "$ink $colour",
                    BackupRejection.InvalidValue("preferences.inkColour"),
                    (codec.read(changed.toByteArray()) as Outcome.Failure).error
                )
            }
        }
    }

    @Test
    fun `a colour and a hue together, beside any ink, reject the whole file, neither is picked`() {
        val file = currentWithoutGuidance()
        for (ink in listOf("tuya", "cobalto", "violeta", "sistema")) {
            val changed = file.replace(
                violetaAlone,
                "\"ink\": \"$ink\",\n    \"inkHue\": 22,\n    \"inkColour\": \"#F2C200\""
            )
            check(changed != file)
            assertEquals(
                ink,
                BackupRejection.InvalidValue("preferences.inkColour"),
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    @Test
    fun `Tuya with neither a colour nor a hue rejects the whole file`() {
        val v12 = currentWithoutGuidance().replace("\"ink\": \"violeta\"", "\"ink\": \"tuya\"")
        assertEquals(
            BackupRejection.InvalidValue("preferences.inkColour"),
            (codec.read(v12.toByteArray()) as Outcome.Failure).error
        )
        // Before version 12 only a hue could be there.
        val v11 = String(resource("valid-g01-v11.json"))
            .replace("\"ink\": \"violeta\"", "\"ink\": \"tuya\"")
        assertEquals(
            BackupRejection.InvalidValue("preferences.inkHue"),
            (codec.read(v11.toByteArray()) as Outcome.Failure).error
        )
    }

    @Test
    fun `a Tuya hue in every version from 8 reads as that hue, never converted to a colour`() {
        for (name in listOf(
            "valid-g01-v8.json",
            "valid-g01-v9.json",
            "valid-g01-v10.json",
            "valid-g01-v11.json"
        )) {
            val file = String(resource(name)).replace(
                "\"ink\": \"violeta\",\n    \"inkHue\": null",
                "\"ink\": \"tuya\",\n    \"inkHue\": 22"
            )
            check(file.contains("\"inkHue\": 22"))
            val read = codec.read(file.toByteArray()).value().contents
            val red = TuyaColour.Hue(InkHue.of(22)!!)

            assertEquals(name, InkPreference.Tuya(red), read.preferences!!.appearance.ink)
            assertEquals(name, red, read.preferences.appearance.tuya)
        }
    }

    @Test
    fun `a version 11 file cannot carry a colour, and a version 12 file must say it`() {
        val v11 = String(resource("valid-g01-v11.json"))
        val colour = v11.replace(
            "\"inkHue\": null,",
            "\"inkHue\": null,\n    \"inkColour\": \"#F2C200\","
        )
        check(colour != v11)
        assertTrue(
            (codec.read(colour.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
        )

        val v12 = String(resource("valid-g01-v12.json"))
        val missing = v12.replace("\n    \"inkColour\": \"#F2C200\",", "")
        check(missing != v12)
        assertTrue(
            (codec.read(missing.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
        )
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
        val file = currentWithoutGuidance()
        for (hue in listOf(null, 0L, 147L, 359L)) {
            val contents = g01InDollars().let {
                it.copy(
                    preferences = it.preferences!!.copy(
                        appearance = it.preferences.appearance.copy(
                            ink = InkPreference.Sistema,
                            tuya = hue?.let { TuyaColour.Hue(InkHue.of(it)!!) }
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
        val file = currentWithoutGuidance()
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
        val file = currentWithoutGuidance()
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
        val file = currentWithoutGuidance()
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

    // --- Guidance seen (version 11, SELLO-205) ---

    @Test
    fun `every nudge is written by its stable name and reads back`() {
        val all = GuidanceMemory(
            seen = Nudge.entries.toSet(),
            lastShown = ShownNudge(Nudge.ReciboGoal, LocalDate.of(2026, 12, 31)),
            backupReminderShown = LocalDate.of(2026, 2, 28),
            backupReminderOff = true
        )
        val written = String(codec.encode(g01WithGuidance(all)).value())
        val guidance = Json.parseToJsonElement(written).jsonObject.getValue("preferences")
            .jsonObject.getValue("guidance").jsonObject

        assertEquals(
            listOf(
                "backup_reminder",
                "bulk_entry",
                "carry",
                "deleting",
                "fixed_payment",
                "guide_hint",
                "investments",
                "recibo_goal"
            ),
            guidance.getValue("seen").jsonArray.map { it.jsonPrimitive.content }
        )
        assertEquals(
            """{"nudge":"recibo_goal","day":"2026-12-31"}""",
            guidance.getValue("lastShown").toString()
        )
        assertEquals("\"2026-02-28\"", guidance.getValue("backupReminderShown").toString())
        assertEquals("true", guidance.getValue("backupReminderOff").toString())
        assertEquals(g01WithGuidance(all), codec.read(written.toByteArray()).value().contents)
    }

    @Test
    fun `nothing seen is written as an empty list with no days`() {
        val written = String(codec.encode(g01WithGuidance(GuidanceMemory())).value())

        assertTrue(
            written.contains(
                "\"guidance\": {\n      \"seen\": [],\n      \"lastShown\": null,\n" +
                    "      \"backupReminderShown\": null,\n      \"backupReminderOff\": false\n    }"
            )
        )
        assertEquals(
            g01WithGuidance(GuidanceMemory()),
            codec.read(written.toByteArray()).value().contents
        )
    }

    @Test
    fun `every file before version 11 reads with no guidance`() {
        for (name in listOf(
            "valid-g01.json",
            "valid-g01-v2.json",
            "valid-g01-v3.json",
            "valid-g01-v4.json",
            "valid-g01-v5.json",
            "valid-g01-v6.json",
            "valid-g01-v7.json",
            "valid-g01-v8.json",
            "valid-g01-v9.json",
            "valid-g01-v10.json"
        )) {
            val preferences = codec.read(resource(name)).value().contents.preferences
            assertTrue(name, preferences != null)
            assertNull(name, preferences!!.guidance)
        }
    }

    @Test
    fun `guidance that cannot be read rejects the whole file, never read as seen or not`() {
        val file = String(resource("valid-g01-v11.json"))
        val seen = "\"seen\": [\n        \"bulk_entry\",\n        \"guide_hint\"\n      ]"
        val cases = listOf(
            seen to "\"seen\": [\"bulk_entry\", \"walkthrough\"]" to
                BackupRejection.InvalidValue("preferences.guidance.seen[1]"),
            seen to "\"seen\": [\"BulkEntry\"]" to
                BackupRejection.InvalidValue("preferences.guidance.seen[0]"),
            seen to "\"seen\": [\"guide_hint\", \"guide_hint\"]" to
                BackupRejection.DuplicateEntry("preferences.guidance.seen[1]"),
            "\"nudge\": \"guide_hint\"" to "\"nudge\": \"3\"" to
                BackupRejection.InvalidValue("preferences.guidance.lastShown.nudge"),
            "\"day\": \"2026-10-20\"" to "\"day\": \"2026-02-30\"" to
                BackupRejection.InvalidValue("preferences.guidance.lastShown.day"),
            "\"day\": \"2026-10-20\"" to "\"day\": \"2026-10-20T00:00\"" to
                BackupRejection.InvalidValue("preferences.guidance.lastShown.day"),
            "\"backupReminderShown\": \"2026-10-01\"" to
                "\"backupReminderShown\": \"2026-1-01\"" to
                BackupRejection.InvalidValue("preferences.guidance.backupReminderShown"),
            "\"backupReminderOff\": false" to "\"backupReminderOff\": \"false\"" to
                BackupRejection.InvalidValue("preferences.guidance.backupReminderOff"),
            "\"backupReminderOff\": false" to "\"backupReminderOff\": null" to
                BackupRejection.InvalidValue("preferences.guidance.backupReminderOff"),
            "\"backupReminderOff\": false" to "\"backupReminderOff\": 0" to
                BackupRejection.InvalidValue("preferences.guidance.backupReminderOff")
        )
        for ((change, expected) in cases) {
            val changed = file.replace(change.first, change.second)
            check(changed != file) { change.first }
            assertEquals(
                change.second,
                expected,
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
            )
        }
    }

    @Test
    fun `guidance missing a key, or with one more, is not version 11`() {
        val file = String(resource("valid-g01-v11.json"))
        for (changed in listOf(
            file.replace(",\n      \"backupReminderOff\": false", ""),
            file.replace(
                "\"backupReminderOff\": false",
                "\"backupReminderOff\": false,\n      \"walkthroughDone\": true"
            )
        )) {
            check(changed != file)
            assertTrue(
                (codec.read(changed.toByteArray()) as Outcome.Failure).error
                    is BackupRejection.Malformed
            )
        }
    }

    @Test
    fun `a version 10 file cannot carry guidance`() {
        val v10 = String(resource("valid-g01-v10.json"))
        val carrying = v10.replace(
            "\"smallExpenseLine\": 20000",
            "\"smallExpenseLine\": 20000,\n    \"guidance\": null"
        )
        check(carrying != v10)
        assertTrue(
            (codec.read(carrying.toByteArray()) as Outcome.Failure).error
                is BackupRejection.Malformed
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
                "inkColour",
                "categoryOrder",
                "categoryDirection",
                "investmentCurrency",
                "investmentOrder",
                "smallExpenseLine",
                "guidance"
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
