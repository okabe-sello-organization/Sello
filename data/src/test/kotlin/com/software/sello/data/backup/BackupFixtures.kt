package com.software.sello.data.backup

import com.software.sello.domain.model.AppearancePreference
import com.software.sello.domain.model.AutomaticBudget
import com.software.sello.domain.model.BudgetLimit
import com.software.sello.domain.model.Category
import com.software.sello.domain.model.CategoryId
import com.software.sello.domain.model.CategoryName
import com.software.sello.domain.model.CategoryOrder
import com.software.sello.domain.model.CategoryOrderDirection
import com.software.sello.domain.model.CategoryOrderingPreference
import com.software.sello.domain.model.DefaultLimit
import com.software.sello.domain.model.Expense
import com.software.sello.domain.model.ExpenseId
import com.software.sello.domain.model.IconKey
import com.software.sello.domain.model.Income
import com.software.sello.domain.model.IncomeId
import com.software.sello.domain.model.IncomeSource
import com.software.sello.domain.model.InkPreference
import com.software.sello.domain.model.Money
import com.software.sello.domain.model.MonthLimit
import com.software.sello.domain.model.Note
import com.software.sello.domain.model.Outcome
import com.software.sello.domain.model.ThemePreference
import com.software.sello.domain.model.TransactionAmount
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/*
 * Synthetic backups. `g01()` is, value for value, what src/test/resources/backup/
 * valid-g01.json says, written out here by hand rather than read from that file.
 */

fun <T> Outcome<T, *>.value(): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> throw AssertionError("expected a value, got failure $error")
}

fun uuid(number: Int): String = "00000000-0000-4000-8000-%012d".format(number)

fun categoryId(number: Int) = CategoryId.of(uuid(number)).value()

fun at(text: String): Instant = Instant.parse(text)

fun cop(pesos: Long) = TransactionAmount.of(Money.cop(pesos)).value()

fun finite(pesos: Long): BudgetLimit = BudgetLimit.finite(Money.cop(pesos)).value()

fun category(
    number: Int,
    name: String,
    icon: String = "shopping_cart",
    archived: Boolean = false,
    createdAt: String = "2026-09-01T12:00:00Z",
    updatedAt: String = createdAt
) = Category(
    id = categoryId(number),
    name = CategoryName.of(name).value(),
    icon = IconKey.of(icon).value(),
    archived = archived,
    version = 1,
    createdAt = at(createdAt),
    updatedAt = at(updatedAt)
)

fun expense(
    number: Int,
    category: Int,
    date: String,
    sequence: Long,
    pesos: Long,
    note: String? = null,
    createdAt: String = "${date}T13:00:00Z",
    updatedAt: String = createdAt
) = Expense(
    id = ExpenseId.of(uuid(number)).value(),
    categoryId = categoryId(category),
    date = LocalDate.parse(date),
    sequence = sequence,
    amount = cop(pesos),
    note = Note.of(note).value(),
    version = 1,
    createdAt = at(createdAt),
    updatedAt = at(updatedAt)
)

fun income(
    number: Int,
    date: String,
    sequence: Long,
    pesos: Long,
    source: IncomeSource = IncomeSource.Salary,
    note: String? = null,
    createdAt: String = "${date}T12:30:00Z"
) = Income(
    id = IncomeId.of(uuid(number)).value(),
    date = LocalDate.parse(date),
    sequence = sequence,
    amount = cop(pesos),
    source = source,
    note = Note.of(note).value(),
    version = 1,
    createdAt = at(createdAt),
    updatedAt = at(createdAt)
)

val darkVioletaAlphabetical = PortablePreferences(
    AppearancePreference(ThemePreference.Dark, InkPreference.Violeta),
    CategoryOrderingPreference(CategoryOrder.Alphabetical, CategoryOrderDirection.Ascending)
)

/** G01: 10,000 + 15,000 + 5,000 spent against 100,000, and 200,000 of income. */
fun g01() = BackupContents(
    exportedAt = at("2026-10-22T14:30:00Z"),
    financialMonth = YearMonth.of(2026, 10),
    financial = FinancialSnapshot(
        zone = ZoneId.of("America/Bogota"),
        revision = 9,
        categories = listOf(
            category(1, "Mercado", updatedAt = "2026-10-01T12:00:00Z"),
            category(
                2,
                "Café",
                icon = "local_cafe",
                archived = true,
                createdAt = "2026-09-02T08:15:30.250Z",
                updatedAt = "2026-10-05T09:00:00Z"
            )
        ),
        defaultLimits = listOf(
            DefaultLimit(
                categoryId(1),
                YearMonth.of(2026, 10),
                AutomaticBudget.Limit(finite(100_000))
            ),
            DefaultLimit(
                categoryId(2),
                YearMonth.of(2026, 9),
                AutomaticBudget.Limit(BudgetLimit.Unlimited)
            ),
            DefaultLimit(categoryId(2), YearMonth.of(2026, 11), AutomaticBudget.Paused)
        ),
        monthLimits = listOf(MonthLimit(categoryId(2), YearMonth.of(2026, 9), finite(40_000))),
        expenses = listOf(
            expense(101, 1, "2026-10-01", 1, 10_000),
            expense(
                102,
                1,
                "2026-10-02",
                2,
                15_000,
                note = "Plaza de mercado",
                updatedAt = "2026-10-04T18:45:10.500Z"
            ),
            expense(103, 1, "2026-10-03", 3, 5_000)
        ),
        incomes = listOf(income(201, "2026-10-01", 1, 200_000)),
        goals = emptyList(),
        featuredGoal = null,
        schedules = emptyList(),
        investments = emptyList(),
        usdRates = emptyList(),
        carryChains = emptyList()
    ),
    preferences = darkVioletaAlphabetical
)

/**
 * A version 3 file as Sello now writes the same data: version 6, with no investments and
 * no rates after the schedules, the default investment settings, no carry chains, and
 * nothing else changed.
 */
fun writtenAsVersion6(version3: String): String {
    val closing = "\n  },\n  \"preferences\""
    check(version3.split(closing).size == 2 && version3.contains("\"version\": 3,"))
    return version4AsVersion6(
        version3.replace("\"version\": 3,", "\"version\": 4,").replace(
            closing,
            ",\n    \"investments\": [],\n    \"usdRates\": []$closing"
        )
    )
}

/**
 * A version 4 file as Sello now writes the same data: version 6, its investments starting
 * in pesos, largest value first, when it has preferences at all, no carry chains, and
 * nothing else changed.
 */
fun version4AsVersion6(version4: String): String {
    check(version4.contains("\"version\": 4,"))
    val newer = version4.replace("\"version\": 4,", "\"version\": 5,")
    // A file made without preferences has none to add to.
    if (newer.endsWith("\"preferences\": null\n}\n")) return version5AsVersion6(newer)
    val last = Regex("(\"categoryDirection\": \"[a-z]+\")\n  }\n}")
    check(last.findAll(newer).count() == 1)
    return version5AsVersion6(
        newer.replace(last) {
            "${it.groupValues[1]},\n    \"investmentCurrency\": \"COP\"," +
                "\n    \"investmentOrder\": \"largest_value\"\n  }\n}"
        }
    )
}

/** A version 5 file as Sello now writes the same data: version 6 with no carry chains. */
fun version5AsVersion6(version5: String): String {
    val closing = "\n  },\n  \"preferences\""
    check(version5.split(closing).size == 2 && version5.contains("\"version\": 5,"))
    // An older file says no financial month: it reads as the month of its export in its zone.
    val exported = Regex("\"exportedAt\": \"([^\"]+)\"").find(version5)!!.groupValues[1]
    val zone = Regex("    \"zone\": \"([^\"]+)\",\n").find(version5)!!
    val month = YearMonth.from(Instant.parse(exported).atZone(ZoneId.of(zone.groupValues[1])))
    return version5.replace("\"version\": 5,", "\"version\": 6,")
        .replace(zone.value, "${zone.value}    \"financialMonth\": \"$month\",\n")
        .replace(closing, ",\n    \"carryChains\": []$closing")
}

fun resource(name: String): ByteArray =
    requireNotNull(BackupFixtures::class.java.getResourceAsStream("/backup/$name")) {
        "missing test resource backup/$name"
    }.use { it.readBytes() }

private object BackupFixtures
