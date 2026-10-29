package com.software.sello.data.backup

import com.software.sello.domain.model.Outcome
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case starts from the valid G01 file and breaks one thing, so a rejection can
 * only come from that one thing. Paths are written out by hand.
 */
class BackupValidatorTest {
    private val codec = BackupCodec()
    private val valid = String(resource("valid-g01.json"))

    /** The valid file with [from] (which must occur exactly once) replaced by [to]. */
    private fun variant(from: String, to: String): String {
        assertEquals("'$from' must occur once in the fixture", 1, valid.split(from).size - 1)
        return valid.replace(from, to)
    }

    private fun rejection(text: String, codec: BackupCodec = this.codec): BackupRejection =
        rejection(text.toByteArray(), codec)

    private fun rejection(bytes: ByteArray, codec: BackupCodec = this.codec): BackupRejection =
        when (val read = codec.read(bytes)) {
            is Outcome.Failure -> read.error

            is Outcome.Success -> throw AssertionError(
                "expected a rejection, the file was accepted"
            )
        }

    private fun accepted(text: String): BackupPlan = codec.read(text.toByteArray()).value()

    private val firstExpenseAmount = "\"amount\": 10000,"

    @Test
    fun `each committed invalid sample is rejected for its own reason`() {
        val expected = mapOf(
            "invalid-amount-above-maximum.json" to
                BackupRejection.AmountOutOfRange("financial.expenses[1].amount"),
            "invalid-dangling-category.json" to
                BackupRejection.DanglingReference("financial.expenses[2].categoryId"),
            "invalid-newer-version.json" to BackupRejection.UnsupportedVersion(7),
            "invalid-usd-expense.json" to
                BackupRejection.UnsupportedCurrency("financial.expenses[2].currency"),
            "invalid-truncated.json" to BackupRejection.Truncated
        )
        for ((name, rejection) in expected) {
            assertEquals(name, rejection, rejection(resource(name)))
        }
    }

    // Amounts: ADR 0003 / M05.

    @Test
    fun `the largest amount is accepted and one peso more rejects the whole file`() {
        accepted(variant(firstExpenseAmount, "\"amount\": 999999999999,"))

        assertEquals(
            BackupRejection.AmountOutOfRange("financial.expenses[0].amount"),
            rejection(variant(firstExpenseAmount, "\"amount\": 1000000000000,"))
        )
    }

    @Test
    fun `zero is not an expense`() {
        assertEquals(
            BackupRejection.AmountOutOfRange("financial.expenses[0].amount"),
            rejection(variant(firstExpenseAmount, "\"amount\": 0,"))
        )
    }

    @Test
    fun `an income above the maximum rejects the file too`() {
        assertEquals(
            BackupRejection.AmountOutOfRange("financial.incomes[0].amount"),
            rejection(variant("\"amount\": 200000,", "\"amount\": 1000000000000,"))
        )
    }

    @Test
    fun `a limit is not bound by the per-record maximum`() {
        accepted(variant("\"amount\": 40000,", "\"amount\": 5000000000000,"))
    }

    @Test
    fun `an amount is a bare whole number, written one way`() {
        for (written in listOf("\"10000\"", "1e4", "10000.0", "-10000", "010000", "true")) {
            assertEquals(
                written,
                BackupRejection.InvalidValue("financial.expenses[0].amount"),
                rejection(variant(firstExpenseAmount, "\"amount\": $written,"))
            )
        }
    }

    @Test
    fun `a number too large for any amount is rejected, not wrapped`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.expenses[0].amount"),
            rejection(variant(firstExpenseAmount, "\"amount\": 99999999999999999999,"))
        )
    }

    // Currency.

    @Test
    fun `another currency, known or not, rejects the file`() {
        val firstCop = "\"amount\": 10000,\n        \"currency\": \"COP\""
        for (code in listOf("USD", "XYZ", "cop")) {
            assertEquals(
                code,
                BackupRejection.UnsupportedCurrency("financial.expenses[0].currency"),
                rejection(variant(firstCop, "\"amount\": 10000,\n        \"currency\": \"$code\""))
            )
        }
        assertEquals(
            BackupRejection.UnsupportedCurrency("financial.monthLimits[0].currency"),
            rejection(
                variant(
                    "\"amount\": 40000,\n        \"currency\": \"COP\"",
                    "\"amount\": 40000,\n        \"currency\": \"EUR\""
                )
            )
        )
    }

    // Format and version.

    @Test
    fun `a newer or older version is named, not misread`() {
        assertEquals(
            BackupRejection.UnsupportedVersion(7),
            rejection(variant("\"version\": 1,", "\"version\": 7,"))
        )
        assertEquals(
            BackupRejection.UnsupportedVersion(0),
            rejection(variant("\"version\": 1,", "\"version\": 0,"))
        )
    }

    @Test
    fun `a version 7 file is refused by version even when its shape has changed`() {
        assertEquals(
            BackupRejection.UnsupportedVersion(7),
            rejection("""{"format": "sello-backup", "version": 7, "ledger": {"rows": []}}""")
        )
    }

    @Test
    fun `JSON that is not a Sello backup is said to be so`() {
        for (text in listOf(
            "{}",
            "[]",
            "\"sello-backup\"",
            """{"format": "otra-app", "version": 1}""",
            """{"format": "sello-backup", "version": "1"}"""
        )) {
            assertEquals(text, BackupRejection.NotSelloBackup, rejection(text))
        }
        assertEquals(
            BackupRejection.NotSelloBackup,
            rejection(variant("\"format\": \"sello-backup\"", "\"format\": \"Sello-Backup\""))
        )
    }

    // Damaged files.

    @Test
    fun `a file cut short is truncated, wherever it was cut`() {
        val bytes = valid.toByteArray()
        for (length in listOf(1, bytes.size / 3, bytes.size / 2, bytes.size - 2)) {
            assertEquals(
                "cut at $length",
                BackupRejection.Truncated,
                rejection(bytes.copyOf(length))
            )
        }
    }

    @Test
    fun `bytes that are not UTF-8 are malformed`() {
        val bytes = valid.toByteArray().also { it[valid.indexOf("Mercado")] = 0xC3.toByte() }

        assertEquals(BackupRejection.Malformed(null), rejection(bytes))
    }

    @Test
    fun `an unknown key or a missing one is malformed, with where it happened`() {
        assertEquals(
            BackupRejection.Malformed("\$.financial.expenses[0]"),
            rejection(variant(firstExpenseAmount, "$firstExpenseAmount \"tip\": 500,"))
        )
        assertEquals(
            BackupRejection.Malformed("\$.preferences"),
            rejection(variant("\"ink\": \"violeta\",", ""))
        )
    }

    @Test
    fun `a malformed message never quotes the file's contents`() {
        val secret = "\"note\": \"Plaza de mercado\""
        val read = rejection(variant(secret, "\"note\": 12345"))

        assertTrue(read is BackupRejection.Malformed)
        assertTrue(!read.toString().contains("12345"))
    }

    @Test
    fun `a key repeated in one object is refused, however it is spelled`() {
        assertEquals(
            BackupRejection.DuplicateKey("amount"),
            rejection(variant(firstExpenseAmount, "$firstExpenseAmount \"amount\": 1,"))
        )
        assertEquals(
            BackupRejection.DuplicateKey("amount"),
            rejection(variant(firstExpenseAmount, "$firstExpenseAmount \"\\u0061mount\": 1,"))
        )
    }

    // Structural bounds.

    @Test
    fun `the size cap is ten MiB exactly`() {
        val limit = 10 * 1024 * 1024
        val padded = valid + " ".repeat(limit - valid.toByteArray().size)

        accepted(padded)
        assertEquals(
            BackupRejection.TooLarge(limit, limit + 1L),
            rejection("$padded ")
        )
    }

    @Test
    fun `a huge stream is refused after reading only one byte more than the cap`() {
        var served = 0L
        val endless = object : java.io.InputStream() {
            override fun read(): Int = 32.also { served++ }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill(32, off, off + len)
                served += len
                return len
            }
        }

        assertEquals(
            Outcome.Failure(BackupRejection.TooLarge(10 * 1024 * 1024, 10L * 1024 * 1024 + 1)),
            codec.read(endless)
        )
        assertEquals(10L * 1024 * 1024 + 1, served)
        assertEquals(
            codec.read(valid.toByteArray()),
            codec.read(ByteArrayInputStream(valid.toByteArray()))
        )
    }

    @Test
    fun `nesting deeper than the bound is refused before parsing`() {
        // An expense is the fourth level, so four more fit within the bound of eight.
        fun nested(levels: Int) = "[".repeat(levels) + "]".repeat(levels)

        assertEquals(
            BackupRejection.TooDeep(8),
            rejection(variant(firstExpenseAmount, "$firstExpenseAmount \"x\": ${nested(5)},"))
        )
        assertEquals(
            BackupRejection.Malformed("\$.financial.expenses[0]"),
            rejection(variant(firstExpenseAmount, "$firstExpenseAmount \"x\": ${nested(4)},"))
        )
        assertEquals(BackupRejection.TooDeep(8), rejection("[".repeat(9)))
    }

    @Test
    fun `more than one hundred thousand records are refused before parsing`() {
        val records = { count: Int -> List(count) { "0" }.joinToString(",") }

        assertEquals(
            BackupRejection.TooManyRecords(100_000),
            rejection(variant("\"incomes\": [", "\"extra\": [${records(100_001)}], \"incomes\": ["))
        )
        // With the file's own ten records this is exactly the cap, so the count
        // passes and the unknown key is what rejects it.
        assertEquals(
            BackupRejection.Malformed("\$.financial"),
            rejection(
                variant("\"incomes\": [", "\"extra\": [${records(100_000 - 10)}], \"incomes\": [")
            )
        )
    }

    @Test
    fun `a field over ten thousand characters is refused, counting escapes as one`() {
        val note = "\"note\": \"Plaza de mercado\""

        assertEquals(
            BackupRejection.FieldTooLong(10_000),
            rejection(variant(note, "\"note\": \"${"a".repeat(10_001)}\""))
        )
        // Within the structural cap, but still far over the 60-character note rule.
        assertEquals(
            BackupRejection.InvalidValue("financial.expenses[1].note"),
            rejection(variant(note, "\"note\": \"${"\\u0061".repeat(10_000)}\""))
        )
    }

    @Test
    fun `the note rule applies inside the structural cap`() {
        val note = "\"note\": \"Plaza de mercado\""

        accepted(variant(note, "\"note\": \"${"n".repeat(60)}\""))
        assertEquals(
            BackupRejection.InvalidValue("financial.expenses[1].note"),
            rejection(variant(note, "\"note\": \"${"n".repeat(61)}\""))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.expenses[1].note"),
            rejection(variant(note, "\"note\": \"\""))
        )
    }

    // References, duplicates and history.

    @Test
    fun `an expense in a category the file lacks rejects the whole file`() {
        val second = "\"id\": \"00000000-0000-4000-8000-000000000103\",\n" +
            "        \"categoryId\": \"00000000-0000-4000-8000-000000000001\""
        assertEquals(
            BackupRejection.DanglingReference("financial.expenses[2].categoryId"),
            rejection(
                variant(
                    second,
                    "\"id\": \"00000000-0000-4000-8000-000000000103\",\n" +
                        "        \"categoryId\": \"00000000-0000-4000-8000-000000000009\""
                )
            )
        )
    }

    @Test
    fun `a limit for a category the file lacks rejects the whole file`() {
        assertEquals(
            BackupRejection.DanglingReference("financial.monthLimits[0].categoryId"),
            rejection(
                variant(
                    "\"categoryId\": \"00000000-0000-4000-8000-000000000002\",\n        \"month\"",
                    "\"categoryId\": \"00000000-0000-4000-8000-000000000009\",\n        \"month\""
                )
            )
        )
    }

    @Test
    fun `two categories with one identity or one name are refused`() {
        assertEquals(
            BackupRejection.DuplicateId("financial.categories[1].id"),
            rejection(
                variant(
                    "\"id\": \"00000000-0000-4000-8000-000000000002\"",
                    "\"id\": \"00000000-0000-4000-8000-000000000001\""
                )
            )
        )
        assertEquals(
            BackupRejection.DuplicateCategoryName("financial.categories[1].name"),
            rejection(variant("\"name\": \"Café\"", "\"name\": \"mercado\""))
        )
    }

    @Test
    fun `two expenses with one identity or one sequence are refused`() {
        assertEquals(
            BackupRejection.DuplicateId("financial.expenses[1].id"),
            rejection(
                variant(
                    "\"id\": \"00000000-0000-4000-8000-000000000102\"",
                    "\"id\": \"00000000-0000-4000-8000-000000000101\""
                )
            )
        )
        assertEquals(
            BackupRejection.DuplicateEntry("financial.expenses[1].sequence"),
            rejection(variant("\"sequence\": 2,", "\"sequence\": 1,"))
        )
    }

    @Test
    fun `two limits for one category and month are refused`() {
        assertEquals(
            BackupRejection.DuplicateEntry("financial.defaultLimits[2]"),
            rejection(variant("\"from\": \"2026-11\"", "\"from\": \"2026-09\""))
        )
    }

    @Test
    fun `a default history longer than the app can hold is refused`() {
        val entries = (0 until 1_201).joinToString(",") { index ->
            val month = java.time.YearMonth.of(1900, 1).plusMonths(index.toLong())
            """{"categoryId": "00000000-0000-4000-8000-000000000001", "from": "$month",
               "kind": "unlimited", "amount": null, "currency": null}"""
        }
        val text = valid.replace(
            Regex("\"defaultLimits\": \\[.*?\\n    ],", RegexOption.DOT_MATCHES_ALL),
            "\"defaultLimits\": [$entries],"
        )

        assertEquals(
            BackupRejection.HistoryTooLong("financial.defaultLimits[1200]"),
            rejection(text)
        )
    }

    // Field values.

    @Test
    fun `dates, months and moments must be real and written one way`() {
        val cases = mapOf(
            ("\"date\": \"2026-10-03\"" to "\"date\": \"2026-02-30\"") to
                "financial.expenses[2].date",
            ("\"date\": \"2026-10-03\"" to "\"date\": \"2026-10-3\"") to
                "financial.expenses[2].date",
            ("\"month\": \"2026-09\"" to "\"month\": \"2026-13\"") to
                "financial.monthLimits[0].month",
            (
                "\"exportedAt\": \"2026-10-22T14:30:00Z\"" to
                    "\"exportedAt\": \"2026-10-22T14:30:00.000Z\""
                ) to "exportedAt",
            (
                "\"exportedAt\": \"2026-10-22T14:30:00Z\"" to
                    "\"exportedAt\": \"2026-10-22T09:30:00-05:00\""
                ) to "exportedAt",
            (
                "\"exportedAt\": \"2026-10-22T14:30:00Z\"" to
                    "\"exportedAt\": \"2026-10-22T14:30:00.000001Z\""
                ) to "exportedAt",
            ("\"zone\": \"America/Bogota\"" to "\"zone\": \"Mars/Olympus\"") to "financial.zone",
            ("\"sourceRevision\": 9" to "\"sourceRevision\": -1") to
                "financial.sourceRevision"
        )
        for ((change, path) in cases) {
            assertEquals(
                change.second,
                BackupRejection.InvalidValue(path),
                rejection(variant(change.first, change.second))
            )
        }
    }

    @Test
    fun `identifiers are canonical lower-case UUIDs`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.incomes[0].id"),
            rejection(
                variant(
                    "\"id\": \"00000000-0000-4000-8000-000000000201\"",
                    "\"id\": \"00000000-0000-4000-8000-00000000020A\""
                )
            )
        )
    }

    @Test
    fun `a category name must already be in its stored form`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.categories[0].name"),
            rejection(variant("\"name\": \"Mercado\"", "\"name\": \" Mercado\""))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.categories[0].name"),
            rejection(variant("\"name\": \"Mercado\"", "\"name\": \"${"m".repeat(25)}\""))
        )
    }

    @Test
    fun `limit kinds carry an amount exactly when finite, and only defaults pause`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.monthLimits[0].kind"),
            rejection(
                variant(
                    "\"kind\": \"finite\",\n        \"amount\": 40000,\n        \"currency\": \"COP\"",
                    "\"kind\": \"paused\",\n        \"amount\": null,\n        \"currency\": null"
                )
            )
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.monthLimits[0].amount"),
            rejection(variant("\"amount\": 40000,", "\"amount\": null,"))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.defaultLimits[1].amount"),
            rejection(
                variant(
                    "\"kind\": \"unlimited\",\n        \"amount\": null",
                    "\"kind\": \"unlimited\",\n        \"amount\": 5"
                )
            )
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.defaultLimits[1].kind"),
            rejection(variant("\"kind\": \"unlimited\"", "\"kind\": \"Unlimited\""))
        )
    }

    @Test
    fun `an income source names itself only when it is other`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.incomes[0].sourceName"),
            rejection(variant("\"sourceName\": null", "\"sourceName\": \"Empresa\""))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.incomes[0].sourceName"),
            rejection(variant("\"source\": \"salary\"", "\"source\": \"other\""))
        )
        assertEquals(
            BackupRejection.InvalidValue("financial.incomes[0].source"),
            rejection(variant("\"source\": \"salary\"", "\"source\": \"Salario\""))
        )
        accepted(
            variant(
                "\"source\": \"salary\",\n        \"sourceName\": null",
                "\"source\": \"other\",\n        \"sourceName\": \"Arriendo\""
            )
        )
    }

    @Test
    fun `preferences use the file's own words`() {
        assertEquals(
            BackupRejection.InvalidValue("preferences.theme"),
            rejection(variant("\"theme\": \"dark\"", "\"theme\": \"Dark\""))
        )
        assertEquals(
            BackupRejection.InvalidValue("preferences.categoryOrder"),
            rejection(
                variant("\"categoryOrder\": \"alphabetical\"", "\"categoryOrder\": \"MostUsed\"")
            )
        )
    }

    @Test
    fun `archived is a bare true or false`() {
        assertEquals(
            BackupRejection.InvalidValue("financial.categories[0].archived"),
            rejection(variant("\"archived\": false", "\"archived\": \"false\""))
        )
    }
}
