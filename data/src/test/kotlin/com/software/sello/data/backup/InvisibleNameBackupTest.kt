package com.software.sello.data.backup

import com.software.sello.domain.model.IncomeSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SELLO-164 refuses invisible and direction-changing characters in names typed from now
 * on. Backups made before it may hold them, so an old file still reads with every
 * name exactly as written. The characters are escapes and are checked as UTF-8 bytes.
 */
class InvisibleNameBackupTest {
    private val codec = BackupCodec()

    private fun hex(text: String) =
        text.toByteArray(Charsets.UTF_8).joinToString(" ") { "%02X".format(it) }

    private fun variant(file: String, vararg changes: Pair<String, String>): ByteArray {
        var text = String(resource(file))
        for ((from, to) in changes) {
            assertEquals("'$from' must occur once in $file", 1, text.split(from).size - 1)
            text = text.replace(from, to)
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    @Test
    fun aVersion2FileWithInvisibleCharactersInItsNamesStillReads() {
        val file = variant(
            "valid-goals-v2.json",
            // A second "Caf\u00E9" that differs only by a zero-width space.
            "\"name\": \"Mercado\"" to "\"name\": \"Caf\u00E9\u200B\"",
            "\"name\": \"Viaje a la costa\"" to "\"name\": \"Viaje\u00AD a la costa\"",
            "\"source\": \"salary\",\n        \"sourceName\": null" to
                "\"source\": \"other\",\n        \"sourceName\": \"\u202EBono\""
        )

        val financial = codec.read(file).value().contents.financial

        assertEquals(
            listOf("43 61 66 C3 A9", "43 61 66 C3 A9 E2 80 8B"),
            financial.categories.map { hex(it.name.value) }.sorted()
        )
        assertEquals(
            "56 69 61 6A 65 C2 AD 20 61 20 6C 61 20 63 6F 73 74 61",
            hex(financial.goals.single().goal.name.value)
        )
        assertEquals(
            "E2 80 AE 42 6F 6E 6F",
            hex((financial.incomes.single().source as IncomeSource.Other).name.value)
        )
    }

    @Test
    fun aVersion4FileWithADirectionIsolatedInvestmentNameStillReads() {
        val file = variant(
            "valid-investments-v4.json",
            "\"name\": \"Ecopetrol\"" to "\"name\": \"\u2066Ecopetrol\u2069\""
        )

        val investments = codec.read(file).value().contents.financial.investments

        val ecopetrol = investments.single { it.investment.name.value.contains("Ecopetrol") }
        assertEquals(
            "E2 81 A6 45 63 6F 70 65 74 72 6F 6C E2 81 A9",
            hex(ecopetrol.investment.name.value)
        )
    }
}
