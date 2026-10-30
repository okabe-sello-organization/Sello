# Recurrence and reserved payments: what exists and what is missing

SELLO-095, checked on 2026-10-24 at `main` `4d16b58` against the locked v2
[Recibo](../README.md#recibo--home) (`home.html`) and [Income](../README.md#income)
(`income.html`) pages, [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md),
[ADR 0012](../../../decisions/0012-budget-guidance-contract.md) BG1–BG3, architecture §8
and the [v1.1 snapshot](../../sello-spec.html) (*Lo que se repite*), which covers what v2
does not show. Each item was opened in the code and, where something is drawn, in the
app (sandbox scenarios `detalle-categoria@1` and `ingresos@1`) and the catalog on an
API-30 emulator (screenshots removed on 2026-10-29 with the ticket evidence; Git history before commit `02fe99b9` keeps them).

**In short:** the reserved states are drawn in the catalog with real components: the
footnote, the warn ink for Revisar, and the Sin margen and Revisar answer slips. No
app screen uses them, and the catalog's Revisar slip orders its parts differently
from the design. Nothing else exists: no schedule in the domain, no table, no origin
on expenses or incomes, no catch-up, no backup field, no `Q` in the budget, no Plan
del mes, no schedule screens and no sandbox step. v2 draws only Recibo's reserved
states and the Plan del mes; managing schedules, the upcoming-payments list and the
notice after catching up have no v2 design. No branch or open pull request holds
recurrence work; `t3/e18-work` points at `main`.

**Since then (2026-10-25):** E18 is done. Every Missing or Partial row below was built
by the ticket in its last column; the rows keep what was found on 2026-10-24.

Present = built and tested, ready to use. Partial = exists but differs as stated.
Missing = nothing exists.

## Data, rules and app

| Element | State | Where, or what is missing | Delivered |
| --- | --- | --- | --- |
| Recurrence rules and worked examples | Missing | Decisions only, in ADR 0017 and ADR 0012 BG1–BG3 (BG-E02, E03, E06, E08, E09, E11 confirmed); SELLO-096 | SELLO-096 |
| Schedule model and occurrence policy (monthly, twice a month, every N days or weeks, pause, end) | Missing | No schedule type in `domain`; SELLO-097 | SELLO-097 |
| Schedule tables, commands, receipts | Missing | Schema 4 has no schedule table; SELLO-098 | SELLO-098 |
| Origin of a recorded occurrence `(scheduleId, effectiveDate)` | Missing | `expense`, `income` and their `deleted_*` tables carry no origin; SELLO-098 | SELLO-098 |
| Catch-up when Sello opens | Missing | No use case runs on opening; no WorkManager dependency, as ADR 0017 wants | SELLO-098 |
| Backup, restore and erase of schedules | Missing | [Backup format](../../../data/backup-format.md) version 2 (goals) has none; SELLO-099 | SELLO-099 |
| Sandbox schedule steps and checks | Missing | `ScenarioStep` has none; `Scenario.kt` and the [sandbox guide](../../../development/sandbox.md) say recurrence does not exist; SELLO-104 | SELLO-104, SELLO-116 |

## Recibo (reserved payments)

| Element | State | Where, or what differs | Delivered |
| --- | --- | --- | --- |
| Reserved `Q` and margin `M = R − Q` in the month's figures | Missing | `MonthlySnapshot` and `LimitedBudget` have no `Q`; `MonthlyBudgetPolicy` works the daily figure out from `R` | SELLO-101 |
| Daily figure from `M`, `0 por día` while Sin margen or Revisar | Missing | `MonthlyBudgetPolicy.allowance` takes the remaining budget; `MonthlyBudgetPolicyTest` covers `R` only | SELLO-101 |
| Stamps Sin margen (neutral) and Revisar (warn), Revisar before Excedido while payments are reserved | Partial | `Stamp` and `StampInk.Warn` exist with no test of the warn ink; `Verdict` and `ReciboStamp` have no such states | SELLO-101 |
| Label **Falta para tus pagos** with the positive shortfall | Partial | Catalog *Recibo answer states*; `ReciboAnswerLabel` has Remaining and Spent only | SELLO-101 |
| Sentence `Quedan 100.000 y tus pagos previstos suman 150.000.` above the tear line | Partial | Catalog shows it under the tear line, with no per-day line and no footnote; the design puts it under the big number, then the tear line, `0 por día`, the footnote and the link | SELLO-101 |
| Footnote `Incluye 200.000 separado para 1 pago previsto ›`, gone when nothing is reserved | Partial | `Footnote` (catalog *Recibo answer states* and *Footnotes, facts and review invitation*); no test, no app use; the catalog's Sin margen slip omits it, the design keeps it; the amount is bold in the design | SELLO-101 |
| **Ajustar presupuesto** for Revisar | Partial | `AdjustBudgetLink` in `ReciboScreen`; `ReciboSummary.adjustable` is true only for Excedido | SELLO-101 |
| Answer slip, label, big number, stamp, per day, past month without reservations | Present | `AnswerSlip`; `ComponentContractsTest` (answer slip reading); `ReciboRenderModelTest`; app screenshot | — |
| Categories never show reservations | Present | Category cells show their own remainder only; stays so | — (stays so) |
| List of upcoming payments opened by the footnote | Missing | No v2 design; v1.1 *Lo que se repite* is the nearest screen | SELLO-114, SELLO-101 |

## Income (Plan del mes)

| Element | State | Where, or what differs | Delivered |
| --- | --- | --- | --- |
| Hero, split bar, receipt lines, Lo que entró, states | Present | `IncomeHistoryScreen` with `AnswerSlip`, `SplitBar`, `LeaderLine`, `TotalLine`; `IncomeHistoryScreenTest` asserts there is no **Plan del mes** yet | — |
| Expected income `F` for the month, kept out of recorded totals | Missing | No expected-income read; BG3 | SELLO-102 |
| Dashed box with **PLAN DEL MES** and "Incluye lo que esperas recibir" | Missing | No dashed slip; `ReviewInvitation` and `AddChip` draw a dashed outline in their own code | SELLO-102 |
| **Anotado**, **Esperado** (softer), a rule, **Planeado**, **Sin asignar en el plan** in bold | Partial | `LeaderLine` has no softer or bold variant; `TotalLine` draws a double rule, the design a single one | SELLO-102 |
| Expected-income chips `📅 Salario · vie 30 oct · 1.000.000`, dashed | Missing | `SelloChip` is solid and selectable; `AddChip` is a dashed button with a plus | SELLO-102 |
| "Lo esperado pasa a anotado cuando lo registras." | Present | `FactLine`-sized text; only the line is new | SELLO-102 |
| No plan box for a past month or without expected income | Missing | Screen state; SELLO-102 | SELLO-102 |
| Plan box and expected chip in `components.html` | Missing | The component page does not list them | SELLO-102 |

## Schedules and the notice (no v2 design)

| Element | State | Where, or what differs | Delivered |
| --- | --- | --- | --- |
| List of schedules, Gastos / Ingresos, monthly total, still to come dimmed, **+** | Missing | v1.1 *Lo que se repite*: reached from Resumen and Configuración; `SegmentedSwitch`, `LeaderLine`, `ActionLine` can carry it | SELLO-114, SELLO-100 |
| Creating a schedule | Missing | v1.1 adds **Se repite** to Anotar; the locked v2 entry has no such control | SELLO-114, SELLO-100 |
| Editing, pausing (**En pausa** stamp), ending, deleting ("Los gastos ya creados no se borran.") | Missing | v1.1 only; `ConfirmSlip`, `DangerSlip`, `Stamp` exist | SELLO-114, SELLO-100 |
| Editing a recorded occurrence | Partial | `ExpenseEditRoute` and `IncomeEditRoute` edit it on its own; v1.1 offered "Solo este" / "Este y los siguientes" | SELLO-114, SELLO-100 |
| Notice after catching up, each item opening its entry | Partial | `SelloNotice` (message and one action) and the edit routes exist; no list of what was recorded | SELLO-114, SELLO-103 |
| Sello pad for schedule amounts | Present | `AmountKeypad`, `EntryAmountSlip` | — (used by SELLO-100) |

## Conflicts between v1.1 and the decisions

- v1.1 records occurrences from a daily background worker catching up up to 60 days;
  ADR 0017 records them when Sello opens, with no background work, and leaves the
  bound to SELLO-096.
- v1.1 stamps a category **Pagado**; v2 shows no reservation inside categories.
- v1.1 lets a goal repeat its contribution; v2 says automatic contributions wait for
  recurrence and ADR 0017 does not include them, so E18 does not either.
- The [scenario corpus](../../../testing/sandbox-scenarios.md)
  asks for WorkManager worker tests when recurrence ships; with no background work,
  SELLO-104 covers the catch-up that runs on opening instead.

## Consequences for the tickets

The reserved slip reuses `AnswerSlip`, `Stamp`, `Footnote` and the existing link; the
catalog's Revisar order and the Sin margen footnote are corrected where SELLO-101 first
uses them. Plan del mes needs a dashed plan slip, a softer and a bold leader line, a
single-rule total and an expected-income chip, all small (SELLO-102). Origins must exist
on recorded occurrences and survive their deletion, so a deleted occurrence is not
recorded again (SELLO-096 rule, SELLO-098 schema). The schedule screens, the
upcoming-payments list and the catch-up notice have no v2 design: the owner chose one
design pass for all three, SELLO-114, before SELLO-100, SELLO-101 and SELLO-103 build
them. SELLO-101 also waits for SELLO-100 so each upcoming payment opens its schedule.
