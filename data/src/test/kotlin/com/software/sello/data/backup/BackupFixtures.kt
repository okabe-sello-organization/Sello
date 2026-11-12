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
        starredGoals = emptySet(),
        schedules = emptyList(),
        investments = emptyList(),
        usdRates = emptyList(),
        carryChains = emptyList()
    ),
    preferences = darkVioletaAlphabetical
)

/**
 * A version 3 file as Sello now writes the same data: version 7, with no investments and
 * no rates after the schedules, the default investment settings, no carry chains, its
 * chosen goal starred, and nothing else changed.
 */
fun writtenAsVersion7(version3: String): String {
    val closing = "\n  },\n  \"preferences\""
    check(version3.split(closing).size == 2 && version3.contains("\"version\": 3,"))
    return version4AsVersion7(
        version3.replace("\"version\": 3,", "\"version\": 4,").replace(
            closing,
            ",\n    \"investments\": [],\n    \"usdRates\": []$closing"
        )
    )
}

/**
 * A version 4 file as Sello now writes the same data: version 7, its investments starting
 * in pesos, largest value first, when it has preferences at all, no carry chains, its
 * chosen goal starred, and nothing else changed.
 */
fun version4AsVersion7(version4: String): String {
    check(version4.contains("\"version\": 4,"))
    val newer = version4.replace("\"version\": 4,", "\"version\": 5,")
    // A file made without preferences has none to add to.
    if (newer.endsWith("\"preferences\": null\n}\n")) return version5AsVersion7(newer)
    val last = Regex("(\"categoryDirection\": \"[a-z]+\")\n  }\n}")
    check(last.findAll(newer).count() == 1)
    return version5AsVersion7(
        newer.replace(last) {
            "${it.groupValues[1]},\n    \"investmentCurrency\": \"COP\"," +
                "\n    \"investmentOrder\": \"largest_value\"\n  }\n}"
        }
    )
}

/**
 * A version 5 file as Sello now writes the same data: version 7 with no carry chains and
 * its chosen goal starred.
 */
fun version5AsVersion7(version5: String): String {
    val closing = "\n  },\n  \"preferences\""
    check(version5.split(closing).size == 2 && version5.contains("\"version\": 5,"))
    // An older file says no financial month: it reads as the month of its export in its zone.
    val exported = Regex("\"exportedAt\": \"([^\"]+)\"").find(version5)!!.groupValues[1]
    val zone = Regex("    \"zone\": \"([^\"]+)\",\n").find(version5)!!
    val month = YearMonth.from(Instant.parse(exported).atZone(ZoneId.of(zone.groupValues[1])))
    return version6AsVersion7(
        version5.replace("\"version\": 5,", "\"version\": 6,")
            .replace(zone.value, "${zone.value}    \"financialMonth\": \"$month\",\n")
            .replace(closing, ",\n    \"carryChains\": []$closing")
    )
}

/**
 * A version 6 file as Sello now writes the same data: version 7, its one chosen goal as
 * the only starred goal. The chosen goal must be active: a completed one is dropped.
 */
fun version6AsVersion7(version6: String): String {
    check(version6.contains("\"version\": 6,"))
    val featured = Regex("\n    \"featuredGoal\": (null|\"[0-9a-f-]+\"),\n")
    val chosen = featured.findAll(version6).single().groupValues[1]
    val starred = if (chosen == "null") "[]" else "[\n      $chosen\n    ]"
    return version6.replace("\"version\": 6,", "\"version\": 7,")
        .replace(featured, "\n    \"starredGoals\": $starred,\n")
}

/**
 * A version 7 file as Sello now writes the same data: version 8, with no hue beside its
 * ink when it has preferences at all, and nothing else changed.
 */
fun version7AsVersion8(version7: String): String {
    check(version7.contains("\"version\": 7,"))
    val newer = version7.replace("\"version\": 7,", "\"version\": 8,")
    if (newer.endsWith("\"preferences\": null\n}\n")) return newer
    val ink = Regex("(\n    \"ink\": \"[a-z]+\",)\n")
    check(ink.findAll(newer).count() == 1)
    return newer.replace(ink) { "${it.groupValues[1]}\n    \"inkHue\": null,\n" }
}

/** A version 8 file as Sello now writes the same data: version 9, with the same keys. */
fun version8AsVersion9(version8: String): String {
    check(version8.contains("\"version\": 8,"))
    return version8.replace("\"version\": 8,", "\"version\": 9,")
}

/**
 * A version 9 file as Sello now writes the same data: version 10, with Gastos hormiga's
 * default line, 20000, after the investment order when it has preferences at all.
 */
fun version9AsVersion10(version9: String): String {
    check(version9.contains("\"version\": 9,"))
    val newer = version9.replace("\"version\": 9,", "\"version\": 10,")
    if (newer.endsWith("\"preferences\": null\n}\n")) return newer
    val order = Regex("(\n    \"investmentOrder\": \"[a-z_]+\")\n")
    check(order.findAll(newer).count() == 1)
    return newer.replace(order) { "${it.groupValues[1]},\n    \"smallExpenseLine\": 20000\n" }
}

/**
 * A version 10 file as Sello now writes the same contents when they hold no guidance:
 * version 11, with `"guidance": null` after the line when it has preferences at all.
 */
fun version10AsVersion11(version10: String): String {
    check(version10.contains("\"version\": 10,"))
    val newer = version10.replace("\"version\": 10,", "\"version\": 11,")
    if (newer.endsWith("\"preferences\": null\n}\n")) return newer
    val line = Regex("(\n    \"smallExpenseLine\": [0-9]+)\n")
    check(line.findAll(newer).count() == 1)
    return newer.replace(line) { "${it.groupValues[1]},\n    \"guidance\": null\n" }
}

/**
 * A version 11 file as Sello now writes the same contents: version 12, with
 * `"inkColour": null` after the hue when it has preferences at all. Before version 12 no
 * Tuya has an exact colour.
 */
fun version11AsVersion12(version11: String): String {
    check(version11.contains("\"version\": 11,"))
    val newer = version11.replace("\"version\": 11,", "\"version\": 12,")
    if (newer.endsWith("\"preferences\": null\n}\n")) return newer
    val hue = Regex("(\n    \"inkHue\": (null|[0-9]+),)\n")
    check(hue.findAll(newer).count() == 1)
    return newer.replace(hue) { "${it.groupValues[1]}\n    \"inkColour\": null,\n" }
}

fun resource(name: String): ByteArray =
    requireNotNull(BackupFixtures::class.java.getResourceAsStream("/backup/$name")) {
        "missing test resource backup/$name"
    }.use { it.readBytes() }

private object BackupFixtures
