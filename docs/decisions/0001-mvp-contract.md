# 0001 — MVP product and financial contract

**Owner:** project owner (user) · **Updated:** 2026-10-23
**Status:** approved product rules; replacement budget guidance has open inputs in
[ADR 0012](0012-budget-guidance-contract.md). Approval does not mean implementation
or release verification is complete.

## Authority and execution boundary

[Architecture](../../ARCHITECTURE.md) owns technical and safety rules.
[Sello v2](../design/v2/README.md), approved in [ADR 0013](0013-sello-v2-design.md),
is the visual reference. Use [v1.1](../design/sello-spec.html) only where v2 has no
coverage. Mockup numbers do not define financial rules.

[ADR 0011](0011-goal-centered-design.md) owns the goal-centered product direction;
[ADR 0012](0012-budget-guidance-contract.md) owns replacement budget guidance and
income planning. This document keeps the rules still in force and names the
remaining decisions. Changes to financial rules need approved worked examples
and scoped implementation before replacing current calculations.

Use the [financial examples](../testing/mvp-financial-examples.md) and their
[technical review](../tickets/E01-foundation/attachments/SELLO-001-independent-review.md) for calculation
and recovery cases. Feature delivery and verification belong to the
[canonical tickets](../tickets/README.md).

## D01 — Scope and first release

MVP includes manual COP expenses and income, categories, monthly budgets,
Recibo/category detail, basic Resumen, edit/delete/undo, appearance and ordering
preferences, manual backup/restore and safe reset. Ship working Recibo and Resumen
tabs. Build the isolated time-testing sandbox and separate component catalog
alongside the app; exclude developer tools and fixtures from customer artifacts.

Metas, investments, recurrence, batch entry, other currencies, analytical exports,
alerts, detailed comparisons and legacy import are outside MVP. Keep them on the
roadmap. Widgets and notification ingestion are excluded. The sandbox must use
real commands and storage without adding deferred financial features.

As capabilities ship, Metas gets a main destination and Inversiones a distinct one.
Prefer three clear destinations where feasible, with at most four working tabs.
v2 guides the presentation; showing a feature in v2 does not add it to MVP scope.
Expense and income creation use brief feedback after a confirmed save. Every amount
uses the Sello pad; text fields use the phone keyboard, with a docked note composer.
Resumen leads with the same-days comparison, with net flow in the income slip below.

## D02 — Identities and supported installation

| Build | Application ID | Launcher label |
| --- | --- | --- |
| Customer | `com.software.sello` | Sello |
| Debug | `com.software.sello.debug` | Sello Debug |
| Catalog | `com.software.sello.catalog` | Sello Catalog |

All three install side by side with isolated private data. Minimum runtime API is
30; [ADR 0005](0005-toolchain.md) records the verified toolchain. Versions live in
the version catalog and wrapper. MVP distribution is private APK only.
Production signing and release checks are required; publishing needs separate
owner authorization. The owner controls the distribution key.

## D03 — Forecast, allowance and verdict

**Current calculation:** since SELLO-061, the status follows ADR 0012 BG1 (see
"Approved design"); the pace-based forecast below remains only as Resumen's
estimate. The other rules here stay in force until ADR 0012 replaces them.
Let `S` be recorded spending through today, `L` total finite limits, `d` today's
day-of-month and `N` month length. Recurring amounts are zero in MVP.

- Daily pace is `S / d`, including days with no recorded spending.
- Forecast is `S × N / d`. Today counts in observed pace, not future days.
  It is an estimate even on day 1 and never creates recorded spending.
- Available days are `D = N − d + 1`, including today.
- Displayed daily allowance is `50 × floor(max(L − S, 0) / (50 × D))`.
  Round down to COP 50: 3,529 becomes 3,500; 3,564 becomes 3,550.
- Round the displayed forecast HALF_UP to one peso. It judges nothing: the
  status compares recorded spending with the limit (Al día, Al límite, Excedido).

Use widened exact arithmetic. Rounding affects display, never recorded money,
remaining budget or status comparisons. A positive allowance below 50 displays
zero without implying overspending. A category overrun does not erase a positive
overall allowance: overall remaining 190,000 over ten days gives 19,000 per day,
even if one category is 10,000 over its own limit. Do not reallocate limits silently.

Past months use actual spending and have no daily allowance. With no finite limit,
show spending and Sin límite, without a remaining amount, allowance or forecast
verdict. A successful empty read with finite limits means zero spending; failed
reads stay failures. Related figures must share one revision and as-of date.

**Approved design:** home status describes the recorded budget and known upcoming
payments, rather than extrapolated pace. v2 uses Al día, Al límite, Excedido,
Sin margen and Revisar; past months use Cumplido or Excedido. The headline accounts
for reserved payments, shown separately at lower priority. When an equal payment
becomes recorded, release its reservation so it is counted once. Omit reservation
context when none remains. Income does not increase spending limits or allowance.

ADR 0012 holds the confirmed scenarios and open inputs: reservation
matching/lifecycle, changed amounts and replacement rounding examples. Do not infer those rules from the mockups.

## D04 — Budget lifecycle and historical corrections

A finite zero budget is a real limit; unlimited means no limit. Spending in
unlimited categories still counts against the overall finite budget. For the
current calculation, a zero limit with zero spending is on track; positive spending
is over. Avoid division by zero in progress graphics.

Changing a default sets the current month's limit and the default for following
months. It must not rewrite earlier months. Each MVP month renews its configured
budget without carrying surplus or deficit. Keep past results; renewal does not
reset actual money. Future signed carryover is defined in
[ADR 0002](0002-signed-carryover-direction.md); the detailed policy is
[ADR 0020](0020-signed-carryover-policy.md).

An unopened month uses the configuration effective then. If October's default was
100,000 and December's became 120,000, November still gets 100,000. Keep dated
default history and explicit monthly overrides; overrides take precedence.
Materialize months atomically and idempotently when needed, without precreating
future rows or requiring a midnight run. Before known configuration or category
existence, limits are unconfigured; never copy today's default backward.

Allow past-dated entries and explicit historical limit corrections. Show the
selected date/month and change scope before submission, then confirm the affected
period after commit. A historical limit correction changes no other month's
configured limit or default. Corrected records update affected totals and charts.
Manual entries cannot be future-dated; dates use the persisted financial zone.

Explain historical correction on first relevant use and keep accessible help in
month selection and the relevant editor after dismissal. Include expenses, income
and limits. Guidance must remain discoverable and never claim success before commit.

Keep these figures distinct, with exact signed values:

| Figure | Meaning |
| --- | --- |
| Ingresos menos gastos | Recorded monthly income minus recorded expenses |
| Restante del presupuesto | Finite monthly limits minus applicable recorded expenses |
| Por asignar al presupuesto | Recorded monthly income minus configured finite category limits |

Expenses are not subtracted again from allocation. Goal/investment records do not
allocate income; a user-created saving/investment category counts through its limit.
Expected income stays separate from recorded income; planned totals and future-month
planning follow ADR 0012. None of these figures is a verified account balance.

## D05 — Fields, input grammar and income labels

Category names are trimmed, 1–24 Unicode code points, and unique ignoring case and
accents, including archived names. Preserve display accents, reject control
characters and use deterministic normalization. Notes are optional, at most 60
code points. Test Unicode boundaries and duplicate matching.

Each expense/income amount is **1–999,999,999,999 COP** across keypad, paste,
commands, storage and backup validation. Reject an entire backup with an invalid
transaction before changing data. Never truncate, skip or repair it. Valid amounts
survive edits exactly. Checked aggregate totals may exceed the per-record cap;
this cap does not apply to category limits. See
[ADR 0003](0003-mvp-transaction-amount-range.md).

Pasted amounts accept ASCII digits or correctly grouped es-CO dots (`1.234`).
Reject decimal commas, exponents, negatives, currency symbols, bad grouping and
overflow. Trim only surrounding whitespace; do not strip other characters.
The keypad emits ungrouped digits.

Income keys are `salary`, `freelance`, `passive_income`, `transfer`, `other`, shown
as Salario, Freelance, Ingresos pasivos, Transferencia, Otro. Otro requires a trimmed
1–24-code-point source name. These are manually declared inflows; Transferencia
does not imply a transfer between tracked accounts or verified bank revenue.

## D06 — Ordering

Default Más usadas counts nondeleted expenses in the selected month, descending.
Other metrics are Alfabético (normalized name, ascending), Monto gastado (selected
month, descending), Límite (that month's finite limit, descending) and Último gasto
agregado. Each supports reversing direction. Equal metrics use normalized name
ascending, then stable category ID. Unlimited categories stay last in every order.

Último gasto agregado uses each category's newest original real creation Instant
among selected-month nondeleted expenses. A backdated expense counts in its
effective month. Edits, restore and financial-clock changes do not create a new
addition. Deleting the newest expense falls back to the next surviving addition.
Reverse the comparison of newest additions, not selection of the oldest expense.
Categories without expenses stay last within each finite/unlimited group in either
direction. Archived categories follow the visibility rules in D07.
See [ADR 0004](0004-category-latest-expense-order.md) for the comparator contract.

## D07 — Archive and undo

Category removal archives it and keeps its identity, history and current-month
limit. Show archived categories in periods with a saved limit or expenses, marked
archived. They cannot accept new entries until explicitly unarchived. Renaming
updates the displayed name across history without changing dates, amounts or limits.

Stop automatic budgets in following months while archived. Unarchive in the same
month keeps that month's limit; in a later month use the last configured default
unless an explicit monthly limit exists. Explain these consequences before
confirmation. MVP has no destructive category deletion or history cascade.

Expense/income deletion offers six seconds of undo, using a real elapsed-time
clock and durable guarded compensation. Rotation, backgrounding and recreation
within the same process do not extend the deadline. Financial-time controls do
not affect it. Process death expires the offer; recover the actual delete/undo
outcome from durable receipts. Expiry never rolls back an already committed undo.
Conflicting edits or a changed data generation reject compensation.

## D08 — Privacy and portable files

Manual backup is versioned plaintext JSON. Explain its readable financial contents
before choosing a destination. Its version is independent of the Room schema.
SELLO-028 owns wire keys, validation bounds and the filename convention.

Include categories and archive state, monthly limits/default history, dated
expenses/income, original IDs/audit timestamps/order, financial zone and portable
appearance/order preferences. Exclude credentials, paths/URIs, permissions,
debug state, pending undo/jobs and local operation-recovery metadata. Restore
establishes fresh guarded runtime identities; backup IDs do not authorize replay.

Disable OS-managed cloud backup and device transfer. Explain manual-recovery
responsibility and possible loss after uninstall/device loss. A selected cloud
provider may sync the file independently; Sello does not control that lifecycle.
Reset cannot erase external copies. Clean private staging after success, failure,
cancellation and startup recovery. Keep no extra shared copies and promise no
password protection or encryption. Use the document picker; report a saved file
only after successful write and close.

## D09 — Restore and reset

Restore replaces, never merges. Validate the whole file before a counted preview
of current/replacement data and explicit confirmation that current data will be
replaced. Restore the financial zone and portable preferences, excluding system
permissions, debug controls and URI grants. Commit financial data atomically,
advance generation and invalidate stale drafts, undo offers and commands.

If settings fail after the financial commit, identify the restored history and
only the preferences that failed. Retry settings without replacing history again.
The approved message uses “Historial financiero restaurado” and explains:
“Tus gastos, ingresos, categorías y presupuestos ya se restauraron.
No pudimos aplicar estas preferencias del respaldo:”. List actual failed values,
such as appearance mode/color or category order/direction. Name the retry action
for the affected preferences: “Aplicar apariencia y orden” or “Aplicar apariencia”.
Include: “Solo se aplicarán las preferencias indicadas. Tu historial no
se reemplazará otra vez.” Do not claim a complete restore while settings failed.

Reset shows counts, requires two confirmations and typed **BORRAR**, and clears
finances, categories, budgets and portable preferences to first-run Recibo.
Defaults are Sistema/Cobalto/Más usadas, descending; next setup uses the device
financial zone. Retain only minimal nonfinancial generation/completion metadata,
with no former amounts, names or notes. Warn that external backups survive.
Neither restore nor reset changes system permissions.
For data that cannot be opened, [ADR 0008](0008-unreadable-storage-start-over.md)
defines retry and keeping the unreadable file aside. Restore must be reachable
there, and reset must also erase that private kept copy.

## D10 — Release acceptance and review

Test the verified minimum/target SDK matrix, compact/short landscape/medium/expanded
windows, font scales 1.0/1.3/2.0, all four themes, automated accessibility
semantics and reduced motion. Require production-signed installation and recovery on at
least one physical device. Assembly and debug signing do not prove these checks.
Manual TalkBack, Switch Access and hardware keyboard walks are post-MVP
(owner direction, 2026-10-23); the app still builds for them under ARCHITECTURE.md.

The project owner approves product/privacy choices and release go/no-go, performs
physical-device testing and controls release build/signing/key custody. Assign an
independent technical reviewer for financial/recovery correctness and verification
evidence; executor self-review is not that approval. Keys stay outside source and
reports. SELLO-035 owns measured startup/large-history baselines and agreed numerical
performance targets. Missing reviewer, device or signing evidence remains an
explicit blocker at the affected gate. Approval of this contract waives no checks.
