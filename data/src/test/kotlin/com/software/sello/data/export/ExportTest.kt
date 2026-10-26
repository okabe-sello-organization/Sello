package com.software.sello.data.export

import com.software.sello.data.backup.BackupCodec
import com.software.sello.data.backup.at
import com.software.sello.data.backup.cop
import com.software.sello.data.backup.expense
import com.software.sello.data.backup.g01
import com.software.sello.data.backup.income
import com.software.sello.data.backup.resource
import com.software.sello.data.backup.uuid
import com.software.sello.data.backup.value
import com.software.sello.domain.model.Currency
import com.software.sello.domain.model.Goal
import com.software.sello.domain.model.GoalHistory
import com.software.sello.domain.model.GoalId
import com.software.sello.domain.model.GoalMovement
import com.software.sello.domain.model.GoalMovementId
import com.software.sello.domain.model.GoalMovementKind
import com.software.sello.domain.model.GoalName
import com.software.sello.domain.model.GoalTargetChange
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.Investment
import com.software.sello.domain.model.InvestmentAmount
import com.software.sello.domain.model.InvestmentHistory
import com.software.sello.domain.model.InvestmentId
import com.software.sello.domain.model.InvestmentMovement
import com.software.sello.domain.model.InvestmentMovementId
import com.software.sello.domain.model.InvestmentMovementKind
import com.software.sello.domain.model.InvestmentName
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.SourceName
import com.software.sello.domain.model.UsdRate
import com.software.sello.domain.model.UsdRateEntry
import com.software.sello.domain.model.UsdRateId
import com.software.sello.domain.port.ExportWords
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The analytical exports (SELLO-121) of one hand-made history. Every expected line is
 * written out here, not produced by the exporter's own code.
 */
class ExportTest {
    private val words = ExportWords(
        date = "Fecha",
        kind = "Tipo",
        name = "Nombre",
        amount = "Monto",
        currency = "Moneda",
        note = "Nota",
        expense = "Gasto",
        income = "Ingreso",
        goalContribution = "Aporte a meta",
        goalWithdrawal = "Retiro de meta",
        investmentContribution = "Aporte",
        investmentWithdrawal = "Retiro",
        dividend = "Dividendo",
        investmentValue = "Valor",
        sources = mapOf("salary" to "Salario", "freelance" to "Independiente")
    )

    private fun d(text: String) = LocalDate.parse(text)

    private val viaje = GoalHistory(
        Goal(
            GoalId.of(uuid(301)).value(),
            GoalName.of("Viaje").value(),
            IconKey.of("flight").value(),
            null,
            null,
            1,
            at("2026-09-01T12:00:00Z"),
            at("2026-09-01T12:00:00Z")
        ),
        listOf(
            GoalTargetChange(cop(1_000_000), 1, d("2026-09-01")),
            goalMovement(302, GoalMovementKind.Contribution, 300_000, "2026-10-02", 2, null),
            goalMovement(303, GoalMovementKind.Withdrawal, 50_000, "2026-10-03", 3, "@retiro")
        )
    )

    private fun goalMovement(
        number: Int,
        kind: GoalMovementKind,
        pesos: Long,
        date: String,
        sequence: Long,
        note: String?
    ) = GoalMovement(
        GoalMovementId.of(uuid(number)).value(),
        kind,
        cop(pesos),
        d(date),
        sequence,
        d(date),
        Note.of(note).value(),
        1,
        at("${date}T15:00:00Z"),
        at("${date}T15:00:00Z")
    )

    private fun movement(
        number: Int,
        kind: InvestmentMovementKind,
        cents: Long,
        date: String,
        sequence: Long
    ) = InvestmentMovement(
        InvestmentMovementId.of(uuid(number)).value(),
        kind,
        if (kind == InvestmentMovementKind.Valuation) {
            InvestmentAmount.ofValue(Money(cents, Currency.USD)).value()
        } else {
            InvestmentAmount.of(Money(cents, Currency.USD)).value()
        },
        d(date),
        sequence,
        null,
        1,
        at("${date}T16:00:00Z"),
        at("${date}T16:00:00Z")
    )

    private val acciones = InvestmentHistory(
        Investment(
            InvestmentId.of(uuid(401)).value(),
            InvestmentName.of("Acciones EE. UU.").value(),
            null,
            IconKey.of("trending_up").value(),
            Currency.USD,
            1,
            at("2026-10-01T16:00:00Z"),
            at("2026-10-01T16:00:00Z")
        ),
        listOf(
            movement(402, InvestmentMovementKind.Contribution, 100_000, "2026-10-01", 1),
            movement(403, InvestmentMovementKind.Valuation, 105_050, "2026-10-03", 2),
            movement(404, InvestmentMovementKind.Dividend, 1_205, "2026-10-03", 3)
        )
    )

    /** G01's categories and limits, with every kind of movement and awkward text. */
    private val history = g01().financial.copy(
        expenses = listOf(
            expense(101, 1, "2026-10-01", 1, 10_000, note = "=SUM(A1:A9)"),
            expense(102, 1, "2026-10-02", 2, 15_000, note = "Plaza \"central\", puesto 4"),
            expense(103, 2, "2026-10-02", 3, 5_000)
        ),
        incomes = listOf(
            income(201, "2026-10-01", 1, 200_000),
            income(
                202,
                "2026-10-03",
                2,
                50_000,
                source = IncomeSource.Other(SourceName.of("+Venta bici").value()),
                note = "-descuento"
            )
        ),
        goals = listOf(viaje),
        investments = listOf(acciones),
        usdRates = listOf(
            UsdRateEntry(
                UsdRateId.of(uuid(501)).value(),
                UsdRate.of(BigDecimal("4150.25")).value(),
                d("2026-10-01"),
                1,
                at("2026-10-01T10:00:00Z")
            )
        )
    )

    @Test
    fun `the CSV lists every movement by date with exact amounts and neutralized text`() {
        val expected = "﻿" +
            "Fecha,Tipo,Nombre,Monto,Moneda,Nota\r\n" +
            "2026-10-01,Gasto,Mercado,10000,COP,'=SUM(A1:A9)\r\n" +
            "2026-10-01,Ingreso,Salario,200000,COP,\r\n" +
            "2026-10-01,Aporte,Acciones EE. UU.,1000.00,USD,\r\n" +
            "2026-10-02,Gasto,Mercado,15000,COP,\"Plaza \"\"central\"\", puesto 4\"\r\n" +
            "2026-10-02,Gasto,Café,5000,COP,\r\n" +
            "2026-10-02,Aporte a meta,Viaje,300000,COP,\r\n" +
            "2026-10-03,Ingreso,'+Venta bici,50000,COP,'-descuento\r\n" +
            "2026-10-03,Retiro de meta,Viaje,50000,COP,'@retiro\r\n" +
            "2026-10-03,Valor,Acciones EE. UU.,1050.50,USD,\r\n" +
            "2026-10-03,Dividendo,Acciones EE. UU.,12.05,USD,\r\n"

        assertEquals(expected, String(CsvExport.encode(movements(history), words), Charsets.UTF_8))
    }

    @Test
    fun `a cell that starts like a formula is shown as text and quoting follows RFC 4180`() {
        assertEquals("'=1+1", CsvExport.cell("=1+1"))
        assertEquals("'\tTab", CsvExport.cell("\tTab"))
        assertEquals("\"'\rRetorno\"", CsvExport.cell("\rRetorno"))
        assertEquals("\"dos\nlíneas\"", CsvExport.cell("dos\nlíneas"))
        assertEquals("\"\"\"citado\"\"\"", CsvExport.cell("\"citado\""))
        assertEquals("sin cambios", CsvExport.cell("sin cambios"))
        assertEquals("", CsvExport.cell(""))
    }

    @Test
    fun `the same history always makes the same files whatever order it was read in`() {
        val shuffled = history.copy(
            expenses = history.expenses.reversed(),
            incomes = history.incomes.reversed(),
            categories = history.categories.reversed()
        )
        val at = at("2026-10-26T12:00:00Z")

        assertArrayEquals(
            CsvExport.encode(movements(history), words),
            CsvExport.encode(movements(shuffled), words)
        )
        assertArrayEquals(JsonExport.encode(history, at), JsonExport.encode(shuffled, at))
    }

    private fun json(snapshot: com.software.sello.data.backup.FinancialSnapshot): JsonObject =
        Json.parseToJsonElement(
            String(JsonExport.encode(snapshot, at("2026-10-26T12:00:00Z")), Charsets.UTF_8)
        ).jsonObject

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    @Test
    fun `the JSON names its format revision zone and moment and carries every movement`() {
        val file = json(history)

        assertEquals("sello-export", file.text("format"))
        assertEquals("1", file.text("version"))
        assertEquals("9", file.text("revision"))
        assertEquals("America/Bogota", file.text("zone"))
        assertEquals("2026-10-26T12:00:00Z", file.text("exportedAt"))
        val movements = file.getValue("movements").jsonArray.map { it.jsonObject }
        assertEquals(10, movements.size)
        assertEquals(
            listOf("expense", "income", "investment_contribution"),
            movements.take(3).map { it.text("kind") }
        )
        // A fixed source by its key; "Otro" with its name.
        assertEquals("salary", movements[1].text("source"))
        assertEquals("null", movements[1]["name"].toString())
        val other = movements[6]
        assertEquals(listOf("other", "+Venta bici"), listOf(other.text("source"), other.text("name")))
        // The JSON keeps text as written: neutralizing is for spreadsheets.
        assertEquals("=SUM(A1:A9)", movements[0].text("note"))
        assertEquals(listOf("1050.50", "USD"), listOf(movements[8].text("amount"), movements[8].text("currency")))
        assertEquals("dividend", movements[9].text("kind"))
    }

    @Test
    fun `the JSON keeps categories with their limits goals investments and rates`() {
        val file = json(history)

        val cafe = file.getValue("categories").jsonArray.map { it.jsonObject }
            .single { it.text("name") == "Café" }
        assertEquals("true", cafe.text("archived"))
        assertEquals(
            listOf("2026-09|unlimited|null", "2026-11|paused|null"),
            cafe.getValue("limits").jsonArray.map {
                val limit = it.jsonObject
                "${limit.text("month")}|${limit.text("budget")}|${limit["amount"]}"
            }
        )
        assertEquals(
            listOf("2026-09|limit|40000"),
            cafe.getValue("monthLimits").jsonArray.map {
                val limit = it.jsonObject
                "${limit.text("month")}|${limit.text("budget")}|${limit.text("amount")}"
            }
        )
        val goal = file.getValue("goals").jsonArray.single().jsonObject
        assertEquals(listOf("Viaje", "1000000", "COP"), listOf("name", "target", "currency").map { goal.text(it) })
        val investment = file.getValue("investments").jsonArray.single().jsonObject
        assertEquals(listOf("Acciones EE. UU.", "USD"), listOf(investment.text("name"), investment.text("currency")))
        val rate = file.getValue("usdRates").jsonArray.single().jsonObject
        assertEquals(listOf("2026-10-01", "4150.25"), listOf(rate.text("date"), rate.text("rate")))
    }

    @Test
    fun `the JSON describes recurring payments and incomes by name and rule`() {
        // RE05 and RE10's schedules, as valid-schedules-v3.json holds them.
        val schedules = BackupCodec().read(resource("valid-schedules-v3.json")).value().contents
        val recurring = (json(schedules.financial).getValue("recurring") as JsonArray)
            .map { it.jsonObject }

        assertEquals(
            listOf(
                "payment|Hogar|950000|monthly|[5]|null|2026-09-01|null|[]",
                "payment|Transporte|20000|every_weeks|null|1|2026-09-26|null|" +
                    "[{\"from\":\"2026-11-01\",\"until\":null}]",
                "income|null|800000|twice_monthly|[15,30]|null|2026-10-01|2026-12-31|[]"
            ),
            recurring.map { entry ->
                listOf("kind", "category", "amount", "repeat", "days", "every", "start", "end", "pauses")
                    .joinToString("|") { key ->
                        entry.getValue(key).let { if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString() }
                    }
            }
        )
        assertEquals("salary", recurring[2].text("source"))
    }

    @Test
    fun `a file is named for its day in the financial zone`() {
        val late = at("2026-10-27T03:30:00Z")

        assertEquals(
            "sello-datos-2026-10-26.csv",
            RoomDataExports.fileName(com.software.sello.domain.port.ExportFormat.Csv, late, ZoneId.of("America/Bogota"))
        )
        assertEquals(
            "sello-datos-2026-10-27.json",
            RoomDataExports.fileName(com.software.sello.domain.port.ExportFormat.Json, late, ZoneId.of("UTC"))
        )
        assertEquals(
            "sello-datos.csv",
            RoomDataExports.fileName(com.software.sello.domain.port.ExportFormat.Csv, late, null)
        )
    }
}
