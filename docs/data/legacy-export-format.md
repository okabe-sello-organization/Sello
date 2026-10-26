# FinanceTracker backup and how Sello imports it

The previous app, FinanceTracker, writes a manual backup as plain JSON. Sello reads it
only through Restaurar, translates it into an ordinary [Sello backup](backup-format.md)
and restores that, under [ADR 0019](../decisions/0019-legacy-import.md). This page
describes the FinanceTracker file as its code writes it (`core/backup/BackupData.kt`,
backup versions 1 and 2) and every rule of the translation. It was checked against a
real export locally; no value from it is in the repository.

## Recognising the file

A FinanceTracker backup is a JSON object with no `format` key and with `exportedAt`
(text), `preferences` (object), `categories`, `expenses` and `income` (arrays).
`version` is absent in files written before version 2, because the app leaves default
values out, or `1` or `2`. A higher number is refused as a newer file; anything else is
not a FinanceTracker backup. Keys Sello does not use are ignored. The file is refused
whole, and nothing changes, when it is not UTF-8 JSON in this shape, its `exportedAt`
is not a date and time, or it breaks the structural bounds of architecture §7 (10 MiB,
100,000 records, 10,000 characters per field, depth 8, no repeated key).

## The file

Amounts are whole Colombian pesos as JSON integers (`amountCop`, `monthlyLimitCop`,
`targetAmountCop`, `currentAmountCop`); an investment entry's `amountMinorUnits` is in
its investment's currency, so cents for USD. A rate is a JSON number, written from a
floating-point value, often with six or more decimals. Days are `yyyy-MM-dd` with no zone; `exportedAt`
is the local date and time of the export, `yyyy-MM-dd HH:mm`, also with no zone.
Identifiers are integers local to the file. A value equal to its default can be missing.

| Key | Fields Sello reads | Ignored |
| --- | --- | --- |
| `categories` | `id`, `name`, `iconKey`, `monthlyLimitCop` (null: no limit), `updatedAt` (epoch ms) | `colorArgb` |
| `expenses` | `id`, `categoryId`, `amountCop`, `description`, `date`, `recurringExpenseId` | |
| `income` | `id`, `amountCop`, `source`, `date`, `notes`, `recurringIncomeId` | |
| `recurringExpenses` | `id`, `categoryId`, `amountCop`, `description`, `recurrenceType`, `startDate`, `nextDueDate`, `isActive` | |
| `recurringIncome` | `id`, `amountCop`, `source`, `notes`, `recurrenceType`, `startDate`, `nextDueDate`, `isActive` | |
| `goals` | `name`, `targetAmountCop`, `currentAmountCop`, `deadlineDate`, `isAchieved` | `id`, `colorArgb` |
| `preferences` | `themeMode`, `homeSortField`, `homeSortDirection` | currency, rates, notifications, capture, investment sorting |
| `investments` | `id`, `name`, `currency`, `iconKey`, `createdDate`, `annualRatePercent` (present or not) | `colorArgb`, `maturityDate`, `targetValueMinorUnits`, `targetDate`, `interestFrequency`, `lastInterestDate`, `updatedAt` |
| `investmentEntries` | `id`, `investmentId`, `entryType`, `amountMinorUnits`, `date`, `notes` | |
| `exchangeRates` | `fromCurrency`, `toCurrency`, `rate`, `updatedDate` (`yyyy-MM-dd HH:mm` or a day) | |
| `merchantRules` | counted only | |

`recurrenceType` is `DAILY`, `WEEKLY`, `BIWEEKLY` (the 1st and the 15th), `MONTHLY`
(the 1st of each month, whatever the start), `MONTHLY_ON_DAY` (the start's day, clamped
to short months) or `CUSTOM:n` (every n days). `nextDueDate` is the first occurrence the
old app had not recorded yet. An income's `source` is the label of one of the old
app's sources (`Salario`, `Ingresos pasivos`, `Freelance`, `Transferencia`, `Otro`) or
free text typed for Otro. A link to a repeating entry may point to one deleted since.

An investment is in `COP`, `USD`, `EUR` or `GBP`. `entryType` is `CASH_INJECTION`
(aporte), `VALUE_SNAPSHOT` (a recorded value, may be 0), `WITHDRAWAL`, `DIVIDEND`,
`INTEREST` (written by the old app each period from `annualRatePercent`) or `NOTE` (no
amount). FinanceTracker's value starts at 0 and replays by day, then `id`: an aporte or
interest adds to it, a retiro takes from it, a recorded value replaces it; dividends
stay apart. There is one rate per currency, to `COP`.

## The translation

The current financial zone is the zone of the result: `exportedAt` and each day are
read in it. Records are taken in the old app's order (by `id`) and get new identifiers.

- **Categories.** Same name and icon key; Sello draws the icons it knows and a generic
  receipt for the others, which the person can change; a key Sello cannot store becomes
  `receipt_long`. None is archived. Creation and
  change time are `updatedAt`, the only time the old app kept.
- **Limits.** Each kept category gets one default limit from the earliest month of any
  imported expense or income (the export's month when there is none): finite with
  `monthlyLimitCop`, or no limit when it is null. No month override is made.
- **Expenses and incomes.** Same amount and day; the description or `notes` becomes the
  note (empty is no note). Positions follow day, then the old `id`. Creation time is
  the start of the record's day. A known source label becomes that source; any other
  becomes Otro with that text as its name.
- **Repeating entries** become schedules with the same amount, note and category or
  source: `DAILY` every 1 day, `WEEKLY` every 1 week, `BIWEEKLY` twice a month on 1 and
  15, `MONTHLY` monthly on day 1, `MONTHLY_ON_DAY` monthly on the start's day,
  `CUSTOM:n` every n days. They start on `startDate` and count as handled up to the last
  occurrence before `nextDueDate`, so catch-up continues where the old app stopped and
  records nothing twice. An inactive one is paused from `nextDueDate`. An expense or
  income linked to a kept schedule on one of its handled occurrences keeps the link
  (the first one, when two claim the same occurrence); any other stays as entered by hand.
- **Goals.** Same name, the target as the first step of the history, and
  `currentAmountCop`, when above zero, as one contribution dated and recorded on the
  export day. The deadline's month is the target month. An achieved goal is completed
  on the export day. The icon is `savings`.
- **Investments.** Same name, icon key (`savings` when Sello cannot store it) and
  currency, no description, created at the start of `createdDate`. Entries keep their
  day, amount and note in the old order and become aportes, retiros, dividends and
  recorded values. FinanceTracker's value is followed along so that Sello's matches it:
  `INTEREST` becomes a recorded value of what the value reached, and when no value is
  recorded on the first aporte's day, a value equal to that aporte follows it. An annual
  rate adds no more interest. Value, aportes and dividends then match the old app; the
  gain differs only where there were retiros.
- **The dollar rate.** `USD` to `COP` becomes Sello's dollar rate dated on the day of
  `updatedDate`, read from the number's exact text (never as floating point); more than
  two decimals are rounded half up to the centavo.
- **Preferences.** `themeMode` gives the theme (`SYSTEM`, `LIGHT`, `DARK`) with the
  Cobalto ink; `homeSortField` the category order (`ALPHABETICAL`, `AMOUNT_SPENT`,
  `BUDGET_LIMIT` as limit, `LAST_UPDATED` as latest expense added) and
  `homeSortDirection` (`ASC`, `DESC`) its direction. An unknown value keeps the
  current preferences.

## Changed, and shown as changed

- **Shortened:** a category, goal or Otro source name or a note longer than Sello allows
  (24, 40, 24 and 60 code points) is cut to that length.
- **Renamed:** a category whose name clashes with an earlier one under Sello's
  uniqueness rule (case, accents and repeated spaces ignored) becomes `Nombre_2`,
  `Nombre_3`, …, the first free number, cut to fit.

- **Rounded:** a dollar rate with more than two decimals.
- **Value added:** an investment with no recorded value on its first aporte's day.
- **Interest as a value:** each `INTEREST` entry.
- **No automatic interest:** an investment with an annual rate.

## Left out, and why

| Reason | Applies to |
| --- | --- |
| Not in the expected shape | any record whose fields are missing or of the wrong type |
| Repeated identifier | the second and later records with one `id` |
| Amount out of range | amounts outside 1 to 999.999.999.999 (a recorded value may be 0); a negative limit, which leaves its category without a limit; a rate outside 1,00 to 100.000,00 after rounding |
| Invalid date | a day that is not a real date or that Sello cannot store; a `nextDueDate` before the start |
| Date after the export | an expense or income dated after the export day |
| Invalid text | a name that is empty or text with control characters such as line breaks |
| Category missing | an expense or repeating expense pointing to no category in the file |
| Its category was left out | an expense or repeating expense whose category was left out |
| Unknown repeat | a `recurrenceType` not listed above, or `CUSTOM` beyond 365 days |
| Currency Sello does not keep | an investment in `EUR` or `GBP`; a rate other than `USD` to `COP` |
| Investment missing or left out | an entry pointing to no investment in the file, or to one left out |
| Note without an amount | a `NOTE` entry |
| Before the first aporte | an entry other than an aporte dated before the investment's first one |
| Retiro above the value | a retiro above the value on its date, which FinanceTracker allowed |
| No aporte | an investment with no aporte kept; its entries go with it |
| Not imported yet | merchant rules |

An entry left out for an investment rule is taken out and the history worked out again
before the next one is judged.

The preview names each count with its reason before anything is replaced; when anything
is left out, Sello's totals will not match FinanceTracker's, and the preview says so.
