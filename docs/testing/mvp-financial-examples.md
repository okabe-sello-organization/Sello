# MVP financial example corpus

**Version:** 32 · **Updated:** 2026-10-08 · **Ticket:** SELLO-001
**Status:** D01–D10 product policies/process and owner walkthrough approved;
independent agent technical/example review passed for version 32; see the
[review report](../tickets/E01-foundation/attachments/SELLO-001-independent-review.md). User owns physical-device
testing and release build/signing; actual evidence is still required. These are not passing app tests.
Numbers use comma grouping here for readability; amounts are integer COP unless
an exact intermediate is shown. UI grouping is es-CO (`1.234`).
Policies/assumptions: [decision record](../decisions/0001-mvp-contract.md).
All reads below succeed unless explicitly stated. MVP recurringDue = 0.

## Installation and distribution — approved D02

| ID | Action/boundary | Expected policy outcome |
| --- | --- | --- |
| Iden01 | Install customer and debug builds together | IDs `com.software.sello`, `.debug`; labels Sello / Sello Debug; each has isolated private data; debug is a separate developer artifact |
| Iden02 | Verify supported release candidate | SDK 30 floor is compatibility-verified in SELLO-002; customer deliverable is a production-signed private APK, not an AAB/store upload or debug-key substitute; actual distribution needs separate authorization |

These are SELLO-002/003/035/036 acceptance expectations, not executed installation,
toolchain, signing or distribution evidence. D10's process is approved; independent
technical review and actual verification remain pending.

## Input and exact-money boundaries — D05

| ID | Input | Expected result under approved D05 policy |
| --- | --- | --- |
| M01 | `1.234` | 1,234 COP; valid grouped original text |
| M02 | `1e3`, `-100`, `9.223.372.036.854.775.808` | Reject syntax/sign/Long overflow respectively; never repair into a different number |
| M03 | `12.34`, `1,5`, `$1234`, empty, expense `0` | Reject; zero finite budget is separately valid |
| M04 | keypad `999999999999`; one more digit | First accepted; further digit does not change value and exposes accessible limit feedback |
| M05 | portable expense or income 1,000,000,000,000; separate valid record 999,999,999,999 | First invalidates complete restore before any mutation; no clipping/skipping. Second accepted and preserved on note edit; same bound as manual entry |
| M06 | two valid expenses of 600,000,000,000 each | Exact aggregate 1,200,000,000,000; transaction cap does not cap totals; true checked Long overflow remains an explicit failure |

## Field/name/source boundaries — approved D05

| ID | Input/action | Expected outcome |
| --- | --- | --- |
| T01 | category name ` Café `, then `cafe` | First saves displayed name `Café`; second conflicts with case/accent-insensitive uniqueness |
| T02 | archived `Café`, then new `CAFE` | Duplicate rejected; archival does not release the name |
| T03 | trimmed name of 24 Unicode code points; 25; whitespace only | First valid; second over-limit rejection; third required-name rejection |
| T04 | note absent; 60 Unicode code points; 61 | First two valid; third rejected rather than truncated |
| T05 | source Otro with no name, then a trimmed 24-code-point name | First rejected; second valid; 25 is over-limit |
| T06 | each approved income source; Transferencia recorded manually | Stable keys map to the approved labels; no inferred account debit or matched bank transfer |

Code-point counting is not UTF-16 code-unit length; boundary tests must include
composed/decomposed accents and supplementary characters. Display normalization and
uniqueness must be consistent across UI/domain/storage/backup. These are policy
expectations, not evidence that the scaffold implements validation.

## Budget and verdict — D03/D04

| ID | Inputs | Expected outcome |
| --- | --- | --- |
| B01 | finite limit 100,000/spend 30,000; unlimited spend 20,000 | Total spent 50,000; remaining 50,000 |
| B02 | all limits absent; spent 50,000 | Has gastado este mes / Sin límite; no invented capacity/allowance/verdict |
| B03 | 2026-10-31; limit 100,000; spent 90,000 | Remaining 10,000; 1 available day; allowance 10,000; 1 día, incluido hoy |
| B04 | overall finite limits total 100,000; overall spent 110,000 | Overall remaining −10,000; overall allowance 0 in loss ink; Excedido; this is not a single-category overspend with other finite capacity remaining |
| B05 | explicit limit 0; spent 0, then spent 1 | Al día then Excedido; no percentage division by zero |
| B06 | configured limit 100,000; successfully loaded empty expense list | Actual 0; forecast 0; Al día; zero is not unavailable data |
| B07 | same configuration; storage read fails | Failed, not B06; optional visibly stale last-good value |
| B08 | 2026-10-22; Food/Transport/Other each has finite limit 100,000; Food spent 110,000, others spent 0 | Owner-confirmed: Food remaining −10,000 and category Excedido; overall limit 300,000/spent 110,000/remaining 190,000; 10 available days → overall allowance 19,000. A category overrun alone must not zero the overall allowance |
| V01 | limit 100,000; actual 80,000; exact forecast 95,000 | Al día |
| V02 | same actual/limit; exact forecast 95,001 or 100,000 | Justo |
| V03 | same actual/limit; exact forecast 100,001 | Pasado |
| V04 | actual 100,001/limit 100,000; hypothetical lower forecast | Pasado; independently exercise actual-overspend precedence |
| V05 | 2027-02-15; spent 50,893; limit 100,000 | Exact forecast 1,425,004/15 = 95,000 + 4/15; display 95,000; **Justo**, not Al día |

**Retired by SELLO-061:** V01–V05 describe the pace-based verdict that ADR 0012
BG1 replaced; the status now compares recorded spending with the limit, so V05 is
Al día. They stay as a record of the forecast arithmetic, which Resumen still shows.
V01–V04 are classifier unit inputs, not assertions that every actual/forecast pair
can be produced by the chosen pace model. For V05, compare
`100 × 1,425,004 = 142,500,400` against `95 × 100,000 × 15 = 142,500,000`.
Compare before display rounding; the former is larger by 400.

## Forecast and available days — approved D03

Use elapsed `d`, future `N − d`, available `N − d + 1`. Exact forecast numerator
is `spent × N`, denominator `d`; only final display rounds HALF_UP. Displayed
daily allowance floors the nonnegative rational to a multiple of COP 50:
`50 × floor(remaining / (50 × availableDays))`. No transaction, remaining-budget
or forecast value is quantized to COP 50. All examples have finite limit 100,000.

| ID | Financial date; spent | Exact forecast → displayed COP | Available days; allowance COP | Forecast verdict |
| --- | --- | --- | --- | --- |
| F01 | 2026-10-01; 10,000 | 310,000 → 310,000 | 31; 2,900 | Pasado (estimate, not actual overspend) |
| F02 | 2026-10-15; 40,000 | 1,240,000/15 → 82,667 | 17; 3,500 | Al día |
| F03 | 2026-10-31; 90,000 | 90,000 → 90,000 | 1; 10,000 | Al día |
| F04 | 2027-02-14; 30,000 | 60,000 → 60,000 | 15; 4,650 | Al día |
| F05 | 2028-02-15; 45,000 | 87,000 → 87,000 | 15; 3,650 | Al día |
| F06 | 2026-10-01; 0, with limits and successful read | 0 → 0 | 31; 3,200 | Al día |
| F07 | 2026-10-31; 110,000 | 110,000 → 110,000 | 1; 0 | Pasado |
| F08 | first-run no categories/limits/expenses | No budget forecast | No allowance | First-run state, no invented limit |

F01 intentionally reveals the early-month estimate's sensitivity: one large first-day
expense projects 310,000, not 10,000. The owner explicitly approved this model
after that consequence was explained on 2026-10-08.
F04/F05 verify 28/29-day February; both include today only in observed pace and
allowance, never in future-day multiplication. Past months suppress F-style forecast.
The "Forecast verdict" column is historical: since SELLO-061 the status ignores the
forecast, so F01 is Al día and F07 Excedido; forecast and allowance figures are unchanged.

## COP 50 daily-allowance boundaries — approved D03

These are derived planning outputs, never rules for rounding recorded money.
Use checked/widened intermediates; these cases also establish that quantization
is downward, not nearest-increment or down-to-hundreds rounding.

| ID | Remaining COP; available days | Exact unrounded allowance | Displayed allowance COP |
| --- | --- | --- | --- |
| A01 | 59,993; 17 | 3,529 | 3,500 (owner's example) |
| A02 | 60,588; 17 | 3,564 | 3,550 (owner's example) |
| A03 | 849; 17 | 849/17, below 50 | 0; remaining stays positive, not loss semantics |
| A04 | 850; 17 | 50 | 50 |
| A05 | 1,699; 17 | 1,699/17, below 100 | 50 |
| A06 | 1,700; 17 | 100 | 100 |
| A07 | 49; 1 | 49 | 0; last-day remaining still 49 |
| A08 | −10,000; 1 | Clamped to 0 | 0 in loss ink; remaining stays −10,000 |

## History, income and graph reconciliation — D04/D06

| ID | Inputs/action | Expected outcome |
| --- | --- | --- |
| H01 | September limit 100,000/spent 20,000; October default changed to 120,000 | September remaining remains 80,000; no past daily allowance |
| H02 | explicit correction: September limit becomes 90,000 | September remaining 70,000; October still 120,000; no default propagation |
| H03 | default 100,000 effective October; November unopened; December default changes to 120,000 | Later November read resolves 100,000 from effective-month history; December uses 120,000; no app-open dependency or backward copy of 120,000 |
| H04 | add backdated November expense 10,000 to H03 | November actual 10,000; limit 100,000; remaining 90,000; no past daily allowance/forecast |
| H05 | September spent 20,000/limit 100,000; unchanged default through October | Approved MVP: October limit 100,000, not 180,000; September remaining 80,000 retained in history; no claim that actual funds reset |
| H06 | user discovers past-month editing, then corrects September limit | Contextual announcement and persistent help explain capability; editor names September and configured-limit scope; committed receipt precedes completion message and refreshed totals |
| I01 | income 150,000; limits 100,000; expenses 30,000 | Ingresos menos gastos 120,000; Restante del presupuesto 70,000; Por asignar al presupuesto 50,000; none is a verified account balance |
| I02 | income 50,000; limits 100,000; expenses 30,000 | Ingresos menos gastos 20,000; Restante del presupuesto 70,000; Por asignar al presupuesto −50,000, not clamped to 0 |
| G01 | Oct 1 expense 10,000; Oct 2 +15,000; Oct 3 +5,000; limit 100,000; income 200,000 | Cumulative 10,000/25,000/30,000; remaining 70,000; Ingresos menos gastos 170,000; Por asignar al presupuesto 100,000 |
| G02 | edit Oct 2 expense in G01 to 12,000 | Cumulative 10,000/22,000/27,000; remaining 73,000; Ingresos menos gastos 173,000; Por asignar al presupuesto remains 100,000 |

H02/H06 reflect approved historical correction and guidance; B05 reflects the
approved zero/unlimited distinction. H03/H04 reflect approved skipped-month effective
configuration, independent of app opening; H05 reflects approved MVP no-carry renewal.
Future signed carryover direction and conditional C01/C02 illustrations are recorded
in [ADR 0002](../decisions/0002-signed-carryover-direction.md); they are not active
MVP test expectations. An earlier month before any
known category/default configuration stays unconfigured; H03 is not permission to
invent such history. An explicit November-only correction supersedes its resolved
default without changing October/December or the default version history.

## Adjusting a limit from Recibo — owner-approved design, 2026-10-26

**Ajustar presupuesto** on Recibo changes one category's limit for the current month
only, or from the current month on (D04). The shared fixture is October, financial
date 22 October: finite limits L 1,000,000 with Café at 50,000 and 90,000 spent. The
Excedido month has E 1,080,000; the Revisar month has E 900,000 (R 100,000) and
reserved payments Q 150,000. T is the month's total finite limit after the change;
"left" is T − E, minus Q when payments are reserved.

| ID | Inputs/action | Expected outcome |
| --- | --- | --- |
| AJ01 | Excedido; Café "Solo octubre" 150,000 | October Café 150,000, its own limit; L 1,100,000; R 20,000, Al día; November Café 50,000 |
| AJ02 | Excedido; Café "Desde octubre" 150,000 | October and November Café 150,000; no month-only limit stored |
| AJ03 | AJ01, then Café "Desde octubre" 60,000; September has its own limit 90,000 | October Café 60,000 from the default, its own limit gone; November 60,000; September still 90,000 |
| AJ04 | Excedido; shortcuts for Café, then for Salud (60,000 spent of 80,000) | Café: Lo gastado 90,000; Cubre el mes 130,000 (50,000 + 80,000). Salud: no Lo gastado; Cubre el mes 160,000 (80,000 + 80,000) |
| AJ05 | Revisar; shortcuts for Café | Lo gastado 90,000; Cubre tus pagos 100,000 (50,000 + 50,000) |
| AJ06 | Excedido; Café drafts 150,000 / 130,000 / 90,000 | T 1,100,000, left 20,000 · T 1,080,000, left 0 · T 1,040,000, left −40,000 |
| AJ07 | Revisar; Café drafts 120,000 / 100,000 / 60,000 | After payments: T 1,070,000, left 20,000 · T 1,050,000, left 0 · T 1,010,000, left −40,000 |
| AJ08 | Excedido by 80,000 (E 1,080,000 including 60,000 in Salud); Salud has no limit | Salud is offered Lo gastado 60,000 and Cubre el mes 80,000 (0 + 80,000); draft 80,000 → T 1,080,000, left 0 |
| AJ09 | Excedido; remainders Café −40,000, Comida −30,000, Hogar 0, Ropa 0, Salud 20,000; Regalos has no limit and spent 10,000; Libros has no limit and spent 0 | Categories with a limit in that order (ties by name); Regalos listed apart, without a limit; Libros not listed |

A shortcut equal to the category's current limit is not offered. A sum that does not
fit an exact amount hides the shortcut or the line; it is never approximated.

## Category ordering — approved D06

Synthetic fixture; all categories active. Times below are original real expense
creation Instants, not configuration edits or simulated financial dates. The
selected effective month filters eligible nondeleted expenses before ranking.

| ID/name | Monthly limit COP | October count/spent | September count/spent | Newest October addition (UTC) |
| --- | --- | --- | --- | --- |
| cat-a / Alimentación | 100,000 | 2 / 30,000 | 1 / 10,000 | 2026-10-03T12:00:00Z |
| cat-c / Café | 50,000 | 3 / 15,000 | 0 / 0 | 2026-10-05T12:00:00Z |
| cat-t / Transporte | 200,000 | 1 / 40,000 | 4 / 20,000 | 2026-10-04T12:00:00Z |
| cat-u / Arte | unlimited | 8 / 80,000 | 6 / 60,000 | 2026-10-06T12:00:00Z |

| Case | Order/action | Expected category IDs |
| --- | --- | --- |
| O01 | October Más usadas descending | cat-c, cat-a, cat-t, cat-u |
| O02 | September Más usadas descending | cat-t, cat-a, cat-c, cat-u |
| O03 | Alfabético ascending; reverse | cat-a, cat-c, cat-t, cat-u; cat-t, cat-c, cat-a, cat-u |
| O04 | October Monto gastado descending | cat-t, cat-a, cat-c, cat-u |
| O05 | Límite descending | cat-t, cat-a, cat-c, cat-u |
| O06 | October Último gasto agregado descending; reverse | cat-c, cat-t, cat-a, cat-u; cat-a, cat-t, cat-c, cat-u |
| O07 | October fixture but Café count becomes 2; count descending, then ascending | cat-a, cat-c, cat-t, cat-u; cat-t, cat-a, cat-c, cat-u |
| O08 | Step financial date without expense addition, keeping selected scope/fixture fixed | O06 orders unchanged; financial clock stepping is not an expense addition |
| O09 | Add Alimentación expense with September 20 effective date and October 7 creation Instant | October O06 unchanged; in September Alimentación becomes newer than Transporte's September 27 addition; cat-a, cat-t, cat-c, cat-u |
| O10 | Edit Café's newest expense amount/note, or rename/edit its category limit; effective month unchanged | October O06 recency unchanged; original creation time preserved |
| O11 | Delete Café's October 5 newest expense; next surviving Café addition October 2 | Descending: cat-t, cat-a, cat-c, cat-u |
| O12 | No surviving October Café expenses; other base fixture rows unchanged | Descending: cat-t, cat-a, cat-c, cat-u; reverse: cat-a, cat-t, cat-c, cat-u; no-expense Café stays after eligible finite rows |
| O13 | Restore original fixture backup on a later day | Original creation times and O06 ordering preserved; import time is not a fresh addition |
| O14 | Separate fixture: Alimentación newest addition equals Café October 5 at 12:00Z | Descending: cat-a, cat-c, cat-t, cat-u; reverse: cat-t, cat-a, cat-c, cat-u; alphabetical tie remains ascending |

For O09, base September newest additions are Alimentación September 25 and
Transporte September 27 at 12:00Z; Café has no September expense. O11 uses base
Café additions October 1/2/5 at 12:00Z, each 5,000 COP; other cases use independent
copies of the base fixture. October selection remains fixed for O08. These new
creation-time fixtures instantiate owner-approved policy, but independent fixture
review and actual sorting tests are still required.

Arte remains last despite highest count/spending and alphabetic placement;
reversing the metric never reverses the unlimited partition or alphabetical tie-break.
Amounts/counts/limits come from the same selected-month snapshot. Archive visibility
and budget lifecycle cases follow approved D07 below.

## Archive and undo lifecycle — approved D07

All records are synthetic. Monetary values are exact; clock times below are controlled
monotonic elapsed times, not financial calendar dates or audit timestamps.

| ID | Inputs/action | Expected outcome |
| --- | --- | --- |
| AR01 | October category limit 100,000/spent 20,000; archive in October | October remaining 80,000 and saved limit retained; row visible as archived; absent from new-entry choices |
| AR02 | AR01 remains archived throughout November | No automatic November budget; October history unchanged; no invisible expense/limit deletion |
| AR03 | Unarchive AR01 within October | Existing October limit remains 100,000; eligibility returns; no duplicate category identity |
| AR04 | Unarchive in December; latest configured default 100,000; no December override | December limit uses 100,000; explain restoration before commit; no retrospective November budget |
| AR05 | Same as AR04, but explicit December limit 90,000 already exists | Preserve December override 90,000 instead of replacing it with the default |
| AR06 | Archived category has a saved limit but no expenses; rename it | Relevant period row remains visible; historical label changes, identity/limits/amounts do not |
| U01 | Confirmed delete at elapsed t=0; undo at t=5.999s, then separate case t=6s | First eligible if versions/generation match; at deadline expired; real-Room tests prove boundaries |
| U02 | Rotate/background at t=4s within same process | Original deadline remains t=6s, never a new six-second interval |
| U03 | Process ends at t=2s; reopen | No renewed undo offer, even if reopened quickly; recover committed deletion outcome |
| U04 | Compensation commits; process ends before success feedback | Reopen recovers compensation receipt and restored original row; expiry cannot roll back committed undo |
| U05 | Advance financial clock or attempt undo after newer conflict/reset | Financial stepping does not change timer; stale/conflicting/reset-generation compensation rejects without overwrite/resurrection |

## Backup privacy and retention — approved D08

| ID | Action/boundary | Expected policy outcome |
| --- | --- | --- |
| P01 | Export manual backup | Before destination/save, explain unencrypted readable financial contents and no password protection; successful close/write precedes saved feedback |
| P02 | Choose cloud-backed destination | Explain provider may sync/store the copy; do not claim Sello performs cloud sync or can control/delete provider copies |
| P03 | Reset app after successful external backup | External copy is not deleted by Sello; private staging is cleaned on completion/failure/cancellation/startup recovery |
| P04 | Inspect portable payload and merged release backup rules | Approved history/zone/portable settings retained; permissions/debug/secrets/pending undo excluded; OS-managed cloud/device-transfer backup disabled; manual recovery responsibility disclosed |

These are acceptance expectations for SELLO-028/029/032, not executed backup/device
tests. The generated manifest's baseline backup setting still needs remediation
before financial data is stored, as required by SELLO-011.

## Restore and reset — approved D09

| ID | Action/boundary | Expected policy outcome |
| --- | --- | --- |
| R01 | Invalid/truncated/unsupported backup, or cancel validated replacement preview | Financial data, generation and preferences unchanged; no success feedback |
| R02 | Current history contains 2 expenses; confirm valid G01 backup containing 3 expenses/1 income | Preview both counts and replacement warning; final history is exactly the backed-up 3 expenses/1 income, not merged; restore backed-up zone/appearance/order, not permissions/debug/URI grants |
| R03 | R02 commits financially; preference write fails, then reopen/retry | Identify financial history as restored and name only the failed backup preferences (appearance/mode/color/category order/direction as applicable), not vague ajustes pendientes or complete Restaurado. Resume those preferences only without a second replacement or generation advance; say history will not be replaced again |
| R04 | Submit old draft or undo after restore/reset, even with matching restored record IDs | Reject stale generation; no mutation or resurrection; uncertain original replacement outcome is recovered by its original operation ID |
| R05 | Cancel reset at either confirmation or enter text other than exact BORRAR | All financial data and preferences unchanged; no reset completion |
| R06 | Confirm counted reset with BORRAR, then restart | No prior finances/categories/month limits/names/notes/portable preferences; first-run defaults Sistema/Cobalto/Más usadas; next setup uses device financial zone; retain only minimal nonfinancial fencing/completion identity; permissions unchanged; external backups survive with explicit warning |

These are acceptance expectations for SELLO-030/031, not executed recovery tests.
Real-Room rollback/reopen and device journeys must prove the eventual implementation.

## Recovery contracts to instantiate in downstream tests — D07–D09

- Same operation/input twice: one record/revision and the same durable receipt;
  same ID with changed input conflicts, with no new mutation.
- Cancellation after transaction commit: reopen and recover the original receipt;
  follow-up failure cannot relabel it as rejected or remove persisted money.
- Stale record version/generation: reject without modifying other records/budgets.
- Delete then conflicting edit/recreation: undo refuses overwrite. At six seconds
  the offer expires; financial-time stepping never changes its deadline. Rotation
  preserves remaining time; approved process-death policy expires the offer but
  recovers actual deletion/compensation outcomes.
- Archive category: history remains and no new entry can target it. Unarchive
  restores eligibility, not altered amounts/limits. Rename changes labels only.
- Invalid/truncated/over-limit backup: no financial or settings changes. Valid
  replacement plus settings failure: new data committed, visible pending settings;
  retry completes settings without replacing financial data a second time.
- Restore/reset advances generation; old draft/undo/import continuation cannot
  resurrect data. Reset clears preferences/history but external backup survives.
- Interrupted G01 sandbox replay: exactly three expenses and one income after
  resume/restart; replay and chart/receipt/Room totals agree.

## Review and verification record

### Guided owner walkthrough

Requested on 2026-10-08; review in small illustrative batches, recording the user's
response against explicit example IDs. Requesting the review is not approving its
results. Executor explanation/checks are not independent technical review or app tests.

| Batch | Examples | Review focus | Status |
| --- | --- | --- | --- |
| 1 | B01/B02/B04/B05/B06/B07/B08 | Remaining budget; unlimited expenses still count; no-limit versus zero; category versus overall overspending; failed reads are not empty histories | Owner-confirmed, including B04/B08 scope clarification; independent technical review remains separate |
| 2 | F01–F08/A01–A08/V01–V05 | Day inclusion, estimate sensitivity, COP 50 planning rounding and exact verdict boundaries | Owner-confirmed across both walkthroughs; implementation and independent technical review remain separate |
| 3 | H01–H06/I01–I02/G01–G02 | Historical stability/renewal, cash-flow versus budget allocation and graph reconciliation | Owner-confirmed, including G01/G02 after allocation-label clarification; independent technical review remains separate |
| 4 | M01–M06/T01–T06/O01–O14/AR01–AR06/U01–U05/P01–P04/R01–R06/Iden01–Iden02 | Input/order/archive/undo/file/recovery/installation edge cases | Owner walkthrough complete, including revisions to money range/recency/restore wording and Iden01/Iden02. Independent fixture/technical review and execution remain separate; O09–O14 were instantiated from approved policy, not individually independently reviewed |

2026-10-08 batch 1 feedback: owner sees the plan as a useful reflection of personal
funds and money movement, with future signed carryover important. Accepted focusing
on the MVP plan first, not claiming verified bank reconciliation. Confirmed unlimited
spending counts, zero versus no-limit and failed-read versus empty-history behavior.
Questioned zero allowance for one over-budget category when others have unspent
capacity. B04 originally described exhausted overall capacity; B08 makes the scope
distinction explicit. No blanket acceptance of B04/B08 or independent review is claimed.
Follow-up on 2026-10-08: owner confirmed the explicit distinction and B08's 19,000
overall allowance. B04's zero applies only to exhausted overall finite capacity;
it is not a bank balance or an entry prohibition. Batch 1 is owner-reviewed.

Batch 2 first walkthrough uses October's 31-day calendar: F02 has 40,000 spent by
Oct 15, 60,000 remaining and 17 available days → 3,500 displayed daily allowance;
exact forecast 40,000 × 31 / 15 → 82,667 displayed COP. F01's first-day 10,000
expense forecasts 310,000 but is not actual overspending. F03's last-day 90,000
spend leaves 10,000 for today and forecast equals actual. A01/A02 reinforce
downward COP 50 planning rounding; these walkthrough outcomes await owner response.
2026-10-08 follow-up: owner confirmed the three presented calendar examples and
rounding. This covers F01's forecast, F02/F03/B03 and A01/A02, not F01's unpresented
allowance or every F/A/V boundary. The next walkthrough covers first-day allowances
2,900/3,200 (F01/F06), 28/29-day February (F04/F05), no-budget/overspend (F08/F07),
sub-50/50/100/negative rounding boundaries (A03–A08) and exact verdicts (V01–V05).
Responses to these remaining cases are pending; full independent review is not claimed.
2026-10-08 follow-up: owner confirmed all remaining batch 2 examples, including
positive remaining money with rounded-zero allowance and exact-value verdicts.
No executed feature/device tests or independent technical review are implied.

Batch 3 walkthrough distinguishes income minus recorded expenses (net cash flow)
from income minus configured limits (Sin destinar), and both from remaining budget.
It presents stable prior limits, explicit historical correction/guidance, unopened
months/backdated expenses, MVP no-carry renewal and cumulative graph correction.
H01–H06/I01–I02/G01–G02 response is pending; future carryover examples remain
conditional directions, not a newly approved implementation formula.
2026-10-08 response: owner confirmed items 1–4, covering I01/I02 and H01–H06.
Item 5's graph recalculation/reconciled totals (G01/G02) was not answered; do not
record it as reviewed by implication. Re-present that example before closing batch 3.
2026-10-08 label/meaning clarification: owner accepted keeping both planning and
recorded movement, with Ingresos menos gastos prominent in Resumen, remaining/
allowance in Recibo and Por asignar al presupuesto secondary. Current I/G labels
reflect that approval; old Sin destinar references in this chronological log are
historical wording. Allocation meaning is confirmed, but the full G01/G02 numeric
graph review is not claimed complete from acceptance of the presentation suggestion.
2026-10-08 graph follow-up: owner explicitly confirmed the G01/G02 expense-only
correction effects and unchanged allocation. Batch 3 is owner-reviewed; this is
not execution evidence or full independent technical review.

Batch 4 money walkthrough presents plain/grouped whole-peso input, rejection rather
than repair, positive exact expenses/income versus zero budgets, 12-digit keypad
feedback, bounded overflow rejection and valid wider backup values preserved when
editing notes. M01–M05 response pending; COP 50 rounding applies only to derived
daily planning amounts, not recorded transactions. No new input policy is inferred.
2026-10-08 response: owner confirmed input items 1–3 and rejected oversized
backup-only transaction support in item 4. M05's old preservation expectation is
superseded: UI/domain/storage/backup share the 12-digit transaction cap. ADR 0003
and M06 distinguish that cap from aggregate totals; no storage/restore tests or
review of other batch 4 examples is claimed.
2026-10-08 continued walkthrough: presented T01–T06 as category trimming/duplicate
examples, archived-name retention, 24-code-point required names and optional
60-code-point notes, Otro source naming and manually recorded income labels.
Boundary violations reject rather than silently truncate; category duplicate
examples mean creating a different category, not renaming the same identity.
Owner response pending. This presentation does not ratify normalization details,
implement validation or complete independent technical review.
2026-10-08 response: owner confirmed all five naming/source items, covering T01–T06.
Next walkthrough presents AR01–AR06: retained current-month budget/history and
archived row, no new entries or automatic future-month budget while archived,
same-month restoration, later restoration with explicit override precedence and
renaming historical labels without changing amounts/identity. Explain consequences
before confirmation. Owner response pending; O01–O08 ordering is a separate next
walkthrough, not reviewed by confirming names or archive policy.
2026-10-08 response: owner explicitly confirmed all five archive/restore items,
covering AR01–AR06. Next walkthrough uses the O01–O08 synthetic fixture to show
monthly expense counts, alphabetical/spent/limit orders, actual configuration-edit
ordering (not latest expense or simulated time), reversible primary direction,
fixed alphabetical/stable-ID ties and unlimited-last partition. Owner response
pending; previously approved policy is not evidence of executed sorting tests.
2026-10-08 sorting response: owner confirmed items 1/2/4/5 but replaced configuration
update ordering with last expense addition. Editing an expense/category/budget is
not a new addition. O06's old expectation is retired; O06/O08 need clarified
scope/timestamp/deletion/empty fixtures. Proposed selected-month, original real
creation-time and nondeleted/empty rules are presented next, not silently approved.
2026-10-08 recency response: owner confirmed all three scope/time/surviving/empty
recommendations and clearer label. Updated O06/O08 and instantiated O09–O14 as
executor-checked fixtures; policy approval does not imply independent fixture review.
Next walkthrough presents U01–U05 six-real-second undo, same-process recreation,
process-death offer expiry, committed-undo recovery and conflict/reset fencing;
owner response pending.
2026-10-08 undo response: owner confirmed all five U01–U05 cases. Next walkthrough
uses P01–P04: readable unencrypted manual JSON, success only after write/close,
financial history/zone/preferences without runtime/debug/device state, manual
recovery responsibility with OS backup/transfer disabled, provider-owned cloud
sync and external-copy retention/private-temporary cleanup. Owner response pending;
actual platform/file requirements remain implementation deliverables.
2026-10-08 backup response: owner explicitly confirmed all P01–P04 items. Next
walkthrough presents R01–R06 and the general recovery contracts: 2 current expenses
replaced by 3 backup expenses/1 income, invalid/canceled replacement changes nothing,
financially committed restore with pending settings retries settings only, counted
BORRAR reset/first-run state, interrupted commit recovery and stale-draft rejection.
Duplicate submission of one 20,000 expense operation produces one expense, not
40,000 spent; two deliberately distinct 20,000 commands remain two expenses.
Unknown commit recovers the original operation, never blind resubmission with a new
identity. Owner response pending; storage/device tests and independent review remain
required, not proven by this walkthrough.
2026-10-08 recovery response: owner confirmed all six presented behaviors and
requested precise R03 wording. Added named failed-preference/committed-history/
preferences-only retry requirements; proposed literal copy lives in D09 and awaits
response/UI review. Do not treat the wording request as rejected recovery semantics
or as approval of text proposed afterward. Installation review remains outstanding.
2026-10-08 copy follow-up: owner accepted the concrete D09 partial-restore wording;
use actual failed preferences/backup values, not hardcoded sample text. Rendering
and retry behavior still need implementation tests and accessibility review.
Next walkthrough presents Iden01/Iden02: customer/debug/catalog coexistence with
isolated data, sandbox-only time/fixtures and reset effects, actual-component
catalog without financial repositories, customer-artifact tooling exclusion and
verified SDK 30/private production-signed APK delivery. Owner response pending;
coexistence/artifact/device/signing evidence is not established by this walkthrough.
2026-10-08 installation response: owner explicitly confirmed all four Iden01/Iden02
examples. Owner-facing walkthrough is complete with no further policy questions
pending in these batches. Revised fixtures are reviewed product direction, not
independent technical review or implementation/device/signing evidence. Future
executor test expectations must still be independently checked before acceptance.

Executor arithmetic verification is distinct from independent product review.
Product approved D01–D10 on 2026-10-08, including the two explicit allowance examples;
the approval log is in the decision record. Future signed carryover direction is
confirmed and deferred beyond MVP; this does not approve its full algorithm,
or claim independent arithmetic review of the full table; that review remains pending.
The approved D10 process requires an independent technical reviewer. The historical
pending-review statements above are superseded by the closure below; future
implementation/release reviewer assignments are separate.
Future tests must hardcode independently checked expectations, not compute them
with the financial implementation being tested. SELLO-001 cannot be Done until
required decisions, arithmetic review and its documentation/G0 checks are complete.

## Independent review and closure

2026-10-08: product owner authorized independent review agent Singer
(`01a11de6-b349-7b51-9d2d-300c3f9b2b39`) for SELLO-001. Its
[full-corpus review](../tickets/E01-foundation/attachments/SELLO-001-independent-review.md) passed all 88 case
IDs, C01/C02 and unnumbered illustrations, including exact arithmetic and 17
ordering comparisons, with no blocking findings. These are independently checked
documentation expectations, not passing app tests or human technical review.
SELLO-001 is Done with documentation/G0 evidence in the
[canonical ticket](../tickets/E01-foundation/SELLO-001.md).
Policies, numerical expectations and corpus version 32 are unchanged. Older
walkthrough/pending-review entries preserve chronology, not current blockers.
