package com.software.sello.data.export

import com.software.sello.domain.port.ExportWords

/**
 * The CSV export: one header and one line per money movement, in [ExportWords]. Fields
 * are separated by commas and quoted as RFC 4180 says; lines end in CRLF. The UTF-8 text
 * starts with a byte order mark, which spreadsheets need to read accents right.
 *
 * A cell a spreadsheet would run as a formula, one that starts with `=`, `+`, `-`, `@`,
 * a tab or a carriage return, gets a leading `'` so it is shown as written instead.
 */
internal object CsvExport {
    const val MIME_TYPE = "text/csv"

    fun encode(rows: List<ExportRow>, words: ExportWords): ByteArray {
        val text = StringBuilder(BYTE_ORDER_MARK)
        line(
            text,
            listOf(words.date, words.kind, words.name, words.amount, words.currency, words.note)
        )
        for (row in rows) {
            line(
                text,
                listOf(
                    row.date.toString(),
                    words.kindWord(row.kind),
                    when (val name = row.name) {
                        is ExportName.Named -> name.text
                        is ExportName.Source -> words.sources[name.key] ?: name.key
                    },
                    row.amount.exportText(),
                    row.amount.currency.code,
                    row.note.orEmpty()
                )
            )
        }
        return text.toString().toByteArray(Charsets.UTF_8)
    }

    private fun line(text: StringBuilder, cells: List<String>) {
        cells.joinTo(text, separator = ",") { cell(it) }
        text.append("\r\n")
    }

    /** One field, neutralized if a spreadsheet would run it, then quoted if it must be. */
    fun cell(raw: String): String {
        val safe = if (raw.isNotEmpty() && raw[0] in FORMULA_STARTS) "'$raw" else raw
        val quoted = safe.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (quoted) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun ExportWords.kindWord(kind: ExportKind) = when (kind) {
        ExportKind.Expense -> expense
        ExportKind.Income -> income
        ExportKind.GoalContribution -> goalContribution
        ExportKind.GoalWithdrawal -> goalWithdrawal
        ExportKind.InvestmentContribution -> investmentContribution
        ExportKind.InvestmentWithdrawal -> investmentWithdrawal
        ExportKind.Dividend -> dividend
        ExportKind.InvestmentValue -> investmentValue
    }

    private const val BYTE_ORDER_MARK = ""
    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r')
}
