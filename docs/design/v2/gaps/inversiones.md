# Inversiones: what exists and what is missing

SELLO-105, checked on 2026-10-25 at `main` `c8d0b4c` against the locked v2
[Inversiones page](../README.md#inversiones) (`inversiones.html`), the component list
(`components.html`), [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md)
and the [Inversiones brief](../../screen-briefs/investments.md). Each item was opened in
the code and, where something is drawn, in the app and the catalog on an API-30 emulator
(screenshots removed on 2026-10-29 with the ticket evidence; Git history before commit `02fe99b9` keeps them).

**In short:** most of the drawing exists. The v2 component pass built `PerformanceLine`,
`InvestmentRow`, `investmentShade`, `RecordedValuesChart`, `SplitBar`, the ink tone for
buttons and docks, and a title bar for the tab; the catalog shows them under *Goals and
investments* and *Split, partial-month and recorded values*. No app screen uses them.
Nothing else exists: no investment or rate in the domain, no table, no command, no
backup field, no tab, route or sandbox step. No branch or open pull request holds
investment work; the `prototype` module's *Patrimonio* is the v1 concept v2 rejected
(a switch with Metas, tickets per investment, three currencies) and nothing depends on it.

**USD has no design.** The locked page is COP only ("no FX"); ADR 0017 later added USD
with one app-wide manual rate. The owner settled the rules on 2026-10-25 and delegated
the design lock (see [Consequences](#consequences-for-the-tickets)).

Present = built and tested, ready to use. Partial = exists but differs as stated.
Missing = nothing exists.

## Data, rules and app

| Element | State | Where, or what is missing |
| --- | --- | --- |
| Investment rules and worked examples | Missing | Decisions only, in ADR 0017 and the brief; SELLO-106 |
| Investment, movement and value models; return policy | Missing | No investment type in `domain`; SELLO-107 |
| USD amounts | Partial | `Currency.USD` (scale 2) and `Money` exist; `TransactionAmount` takes COP only (`isMvpEntryCurrency`); `AmountDraft` reads digits only |
| Decimal USD rate with its date | Missing | No rate type; `BigDecimal` appears only in rounding helpers |
| Room tables, migration, commands, receipts, undo | Missing | Schema 5 has no investment table; `UndoWindow.Kind` has Expense, Income and GoalMovement; SELLO-107 |
| Backup, restore and erase | Missing | [Backup format](../../../data/backup-format.md) version 3 has none; SELLO-108 |
| Inversiones tab (fourth of four) | Missing | `ShellTab` has Recibo, Resumen and Metas ([app](../../../tickets/E19-investments/attachments/SELLO-105-app-three-tabs.png)); `SelloNavigationBar` accepts 2–4 and the catalog already draws four |
| Sandbox investment scenarios | Missing | No investment step in `ScenarioStep`; SELLO-112 |

## Overview

| Element | State | Where, or what differs |
| --- | --- | --- |
| Top bar "Inversiones", **+**, settings, no month bar | Present | `TitleBar`; `ShellBarsContractsTest.titleBarNamesTheTabAsAHeadingAndKeepsActionsReachable` (uses "Inversiones"); Metas uses it the same way |
| Answer slip "Tus inversiones valen", stamp, value as the big number in ink | Partial | `AnswerSlip`; with `StampInk.Over` (Pierde) it draws the big number in loss red, the design keeps it in ink |
| Gana / Pierde stamps | Present | `Stamp` with `StampInk.Good` / `Over`; `PaperAndStampTest` |
| Signed return "▲ +5,6 % · +2.066.000 de ganancia" | Partial | `PerformanceLine`; `InvestmentContractsTest.returnShowsItsPercentageAndAmountOnOneLine`. The big percentage reads "+5 , 6 %" with gaps around the comma ([catalog](../../../tickets/E19-investments/attachments/SELLO-105-catalog-return-and-rows.png)); the row's "+7,5 %" does not |
| "En total, desde mar 2025" | Present | Caption text |
| Aportaste, Retiraste, Dividendos y rentas, Ganancia (signed, coloured) | Present | `LeaderLine` with a `MoneyText` value; `PaperAndStampTest`, `PlanPartsTest` |
| Footnote "Valores anotados entre el 12 jun y el 14 oct ›" | Partial | `Footnote` (E18, `ReservedPartsTest`) draws it; the page does not say what it opens |
| Distribution bar, one shade per investment | Partial | `SplitBar` takes any number of coloured segments, no test; `investmentShade` has four shades, a fifth investment repeats the last; no 2dp gap between segments |
| Investment row: shaded icon, name, "kind · valor del 12 jun", value, signed % | Present | `InvestmentRow`; `InvestmentContractsTest.investmentRowCarriesItsValueAndChangeOnTheRight` |
| Row without a value: **Sin valor**, "aportaste 2.000.000", dashed circle | Partial | `InvestmentRow(value = null)` draws a solid circle, not the dashed one its KDoc and the design describe; at 1.0× on a 411dp screen the caption cuts to "sin valor a…" |
| Dock **Anotar movimiento** in ink; **Agregar inversión** when empty | Present | `ButtonTone.Ink` in `EntryDock` / `SelloDock`; `DockToneTest` covers Gain and Brand, not Ink |
| Empty slip without button or chart | Present | `EmptySlip`; `FeedbackInteractionTest` |
| Loading skeleton, failed read with Reintentar | Present | `SlipSkeleton`, `EmptySlip` with its action |
| Total in loss, plain figures | Partial | As the answer slip above |

## Investment detail

| Element | State | Where, or what differs |
| --- | --- | --- |
| ← name, edit | Present | The pushed-screen bar Metas' goal detail uses |
| Slip "Vale", stamp, value, signed return, "Valor anotado el 9 oct · desde mar 2025", breakdown, **Anotar el valor de hoy** | Partial | Same parts as the overview, with the same two differences |
| Chart of recorded values: dots, thin line, dashed steps for what was put in | Partial | `RecordedValuesChart` draws dots, the line and the steps, with exact rows for TalkBack ([catalog](../../../tickets/E19-investments/attachments/SELLO-105-catalog-recorded-values.png)); no axis ("mar 25 … oct 26"), no legend ("Valor anotado", "Lo que pusiste"), no test |
| Movimientos: Valor de hoy, Dividendo, Aporte, Retiro; **Ver los 9** | Present | `MovementRow`; `GoalComponentsTest`; Metas' movement list is the pattern |

## Anotar movimiento

| Element | State | Where, or what differs |
| --- | --- | --- |
| Entry layout: pinked amount slip, date pill, action above the pad, note row, one fact | Present | `EntryLayout`, `EntryAmountSlip` (+, − or no sign through `MoneySign`), `FieldPill`, `EntryLine`, `FactLine`; assembled in `GoalMovementScreen` (`GoalMovementScreenTest`) |
| Type chips, one row, above the investment chips | Present | `SelloChip`, `ChipRows(rows = 1)`; `EntryControlsTest` |
| "Más de lo que vale (5.242.000)" in red, button waits | Present | `EntryAmountSlip(error)`, as goals' withdrawal limit |
| Sello pad | Partial | `AmountKeypad`: 1–9, 000, 0 and delete; no comma key for USD cents |

## Agregar / editar inversión

| Element | State | Where, or what differs |
| --- | --- | --- |
| Name (40), icon strip in ink | Present | `FormField`, `IconChoiceStrip(tone = Ink)`; `GoalFormPartsTest` |
| Optional description | Partial | `FormField` exists; Crear meta has no description field to copy |
| **Primer aporte** with its date | Present | Crear meta's slip and `FieldPill` |
| **Vale hoy**: Lo mismo que aporté / Otro valor | Partial | `SelloChip` pair or `SegmentedSwitch`; `ChoiceSlip` is a dialog, not this |
| Delete in the ⋮ menu and confirm slip | Present | `MoreMenu`, `ConfirmSlip`; `GoalEditorScreenTest` |

## Consequences for the tickets

Every later ticket builds on these components instead of drawing its own, and the
partial items become small extensions in the ticket that first needs them:

- **SELLO-106** writes the rules and examples with the owner's 2026-10-25 answers, and
  also designs what the page lacks, locked by owner delegation: the currency choice and
  "rate first" in Agregar inversión, how USD amounts and the rate read, the comma key,
  the rate setting behind the tab's settings, the total's rate line, what the valuation
  footnote opens, and the fifth and later shades.
- **SELLO-107** adds investments, movements, values and dated rates (schema 6), USD
  amounts beside `TransactionAmount`, a decimal rate type and an undo kind.
- **SELLO-109** adds the tab and the overview: Pierde with the value in ink, the "+5,6 %"
  comma, `SplitBar`'s gap and test, the dashed no-value circle and its caption.
- **SELLO-110** adds the detail, the movement form and Agregar / editar inversión: the
  chart's axis, legend and test, the description field, Vale hoy's choice and the comma
  key. It uses the rate setting from SELLO-111 for "rate first", so it follows it.
- **SELLO-112**'s catalog part shrinks to those extensions and device tests for the
  pieces that have none (`SplitBar`, `RecordedValuesChart`, the ink dock).
