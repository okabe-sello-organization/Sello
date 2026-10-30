# 0020 — Signed carryover policy

**Owner:** project owner (user) · **Date:** 2026-10-28
**Status:** the owner's answers of 2026-10-28 (marked **Owner**) and the rules that fill
what they leave open (marked **Proposed**, each with its reason and the alternative
rejected) are approved by merging the SELLO-037 pull request. Nothing here is implemented.
This record stands in for the `0003-signed-carryover-policy.md` named in the ticket:
number 0003 is taken by [the transaction amount range](0003-mvp-transaction-amount-range.md).

[ADR 0002](0002-signed-carryover-direction.md) set the direction: surplus and overspending
both survive from month to month, never disappear silently, and are neither income nor
expense. This record turns it into rules the E09 tickets (SELLO-038–043) implement without
inventing any financial choice. The worked examples are in
[signed carryover examples](../testing/signed-carryover-examples.md) (SC01–SC47); rule
numbers CP1–CP28 are cited there. [Architecture](../../ARCHITECTURE.md), the
[product contract](0001-mvp-contract.md) (D03, D04), [ADR 0012](0012-budget-guidance-contract.md)
and ADR 0003 still own money, dates, recovery, guidance and bounds.

## Figures

For one category and one calendar month (the financial month, in the financial zone):

| Figure | Meaning | Spanish label |
| --- | --- | --- |
| Base `B` | The configured limit for that month (explicit month limit, else default), as today | Límite base |
| Carry-in `K` | Signed balance arriving from earlier months of an enabled chain | Arrastre |
| Capacity `C = B + K` | What the month may spend, signed | Límite con arrastre |
| Spent `S` | Recorded expenses of the category in that month (up to the as-of day in the current month) | Gastado |
| Remaining `R = C − S` | Signed, never clamped. A closed month's `R` is the next month's `K` | Restante del presupuesto |

All whole COP pesos; carry is never rounded. Carry is a planning figure, not money, not a
transaction and not a verified balance: it is never income, never an expense, never in
net flow and never in the 1–999.999.999.999 transaction range, which it does not touch.

## Scope, start and formula

- **CP1 (Owner)** Carry is **per category, opt-in and off by default**. Every category
  starts off, including existing users' categories, new categories and those restored from
  an MVP-era backup. The overall budget includes the carry of the enabled categories (CP16).
- **CP2 (Owner)** Carry starts at 0 in the month the owner turns it on; past months never
  count. **Proposed:** that month is the current financial month at the time of the
  command, and the **whole month counts from day 1**: its capacity is its base, so entries
  already recorded in it are spending of the month. *Rejected:* counting only from the day
  it is turned on (needs a prorated base nobody configured); choosing an earlier month
  (the owner: past never counts) or a later one (future months cannot be viewed).
- **CP3 (Proposed)** Turning on needs a **finite base this month** (zero is allowed) of at
  most 999.999.999.999 (CP25) **and a finite default in force after this month**, because
  every later month takes its base from that default (a month's own limit can only be set
  for a month that has started). It is refused for an unlimited, paused, archived or
  unconfigured category, when this month is finite only through its own limit while the
  default is unlimited, and when already on. *Reason:* without a base there is no capacity
  to carry from, and a chain must never reach a month with none through no command that
  could ask for the CP9 confirmation. *Rejected:* letting the chain end silently at that
  month (it would forgive the balance), reading the missing base as 0, or failing the
  snapshot.
- **CP4 (Proposed)** The setting is a history of **chains**: a chain starts in one month
  with carry-in 0 and ends in the first month that no longer carries (open while it
  carries). A month is in a chain when start ≤ month < end. Turning on again is a new
  chain with carry-in 0; earlier chains and their months keep their figures. Turning off
  and on in the same month leaves carry-in 0 that month. A chain turned off in its start
  month is removed: nothing was carried.
- **CP5 (Owner)** **Next carry = (base + carry) − spending**, signed, **every calendar
  month**. A month with no expenses counts as zero spent, so its whole base carries. There
  is no cap on a surplus or a deficit.
- **CP6 (Proposed)** Only recorded expenses are spending. Income and reservations never
  enter carry; a month's `R` is exactly `C − S` with `S` as in D03. Skipped months are
  months of zero spending (a successful read of nothing), never unknown ones.
- **CP7 (Proposed)** Carry is **derived on read** from the recorded expenses, the base
  history and the chain history, at one revision and as-of day, so it cannot depend on
  when the app was opened. No month-boundary job writes anything. Whether SELLO-039 caches
  is its decision; a cache never shows a mixed or older revision as current.
- **CP8 (Proposed)** A failed or unrepresentable read of any month of a chain fails every
  figure that needs it (the typed failure of the snapshot); carry is **never 0, empty or
  the previous value** instead. *Rejected:* showing the base alone on failure, which would
  hide a deficit.

## Dropping a balance

- **CP9 (Owner)** **Turning carry off, archiving the category, or making it unlimited while
  carry is not zero:** Sello shows the exact balance and asks to confirm dropping it; this
  is the explicit settlement. Turning on again later starts at 0. Nothing is dropped
  silently, and cancelling changes nothing. With carry 0 there is nothing to confirm.
- **CP10 (Proposed)** What is dropped is **the carry-in of the current month `K`**, and the
  chain ends **in that month**: from then on the month's capacity is its base, its recorded
  spending stays, and past months of the chain keep their figures. The confirmation shows
  `K`, the base, and this month's `C` and `R` before and after. The command carries the
  confirmed `K`; if it differs when committed, nothing changes and the person sees the new
  one. *Rejected:* ending at month's end (the balance is not final until then and moves
  with every expense); dropping part of it.
- **CP11 (Proposed)** The triggers: turning off; `ArchiveCategory` (which also keeps its
  existing effect: the month keeps its limit, later months are paused); setting the default
  limit, or this month's limit, to unlimited. While a chain is open no other command can
  leave one of its months, or the default after it, without a finite base: CP3 refuses
  turning on otherwise and CP13 refuses the past. **Unarchiving and returning from unlimited to a finite limit never turn carry
  back on**: the person turns it on, starting at 0 in that month. *Rejected:* resuming
  automatically (it would restart a chain nobody confirmed).
- **CP12 (Proposed)** The dropped balance is **not stored as a number**: it is the derived
  carry-in of the month the chain ended, so a later correction shows up in it (CP20). The
  chain's end is what is stored; the command receipt keeps what was confirmed. *Rejected:*
  a stored settlement amount, which would be a second truth that corrections must not edit.
- **CP13 (Proposed)** A **past month inside a chain** cannot be made unlimited by a monthly
  limit correction: it is refused, because the chain would have a hole. Finite corrections
  are allowed (CP20). *Rejected:* splitting the chain in the past, which would drop a
  balance with no confirmation at the time.

## Verdicts and guidance

- **CP14 (Owner, thresholds from D04 and ADR 0012 BG1)** The owner chose that status,
  remaining and daily allowance use **base + carry** (`C`) in place of the limit. The
  thresholds are the existing ones, unchanged: current month **Excedido** when `S > C`,
  **Al límite** when `S = C > 0`, otherwise **Al día** (so `C = 0` with nothing spent is
  Al día, D04); past month **Cumplido** when `S ≤ C`, otherwise **Excedido**.
  **Proposed consequence:** a negative `C` is always Excedido, even with nothing spent this
  month (Revisar instead while reservations leave a shortfall, CP18), showing the exact
  overrun `−R`, because the earlier deficit is real. *Rejected:* judging the base alone,
  which forgives a deficit.
- **CP15 (Owner, formula from D03 and ADR 0012 BG2)** The owner chose that **a deficit shows
  even when the allowance is 0.** The allowance is the existing
  `50 × floor(max(M, 0) / (50 × D))` with `M = R − Q` (CP18); only this figure is clamped
  and rounded. `R`, carry and capacity are exact, so a remaining of −120 shows −120 and an
  allowance of 0, never −50.
- **CP16 (Proposed)** The **overall** budget is one sum: `C = Σ base of finite categories +
  Σ carry-in of categories in a chain that month`, against all spending, including
  unlimited and non-carrying categories (D04 unchanged). The overall verdict, remaining and
  allowance follow CP14–CP15 on that `C`. It has no chain of its own. **No category borrows
  from another**: a deficit lowers the overall capacity by exactly itself and a surplus
  raises it by exactly itself, while each category is judged on its own `C`.
- **CP17 (Owner)** **Por asignar uses base limits only** (income minus finite base limits);
  carry is never income and never enters Ingresos menos gastos. Neither figure changes
  when carry is turned on or off.
- **CP18 (Proposed)** Reservations (ADR 0012): `R = C − S`, `M = R − Q`, and Sin margen /
  Revisar / Excedido follow BG1 on those values. Reservations are never spending, so a
  closed month carries `R` without them. Unrecorded schedule payments dated in an earlier
  **month of an enabled chain** are reserved in `Q` of the current month until catch-up
  records them, so the headline does not move when they land (they leave `Q` and lower
  carry-in by the same amount). Catch-up recalculates the chain in the same commit and
  asks for **no pre-save preview**, because no person is making a correction then; this
  narrows CP20 for automatic catch-up only. It never changes availability silently: the
  opening notice of the recurrence rules (rule 14) also names, for each category in a
  chain that a listed occurrence reaches, the month of the occurrence with its remaining
  and any verdict that changed, and each later month with its capacity before and after.
  The change is derived from the listed occurrences, not stored: a month's capacity falls
  by the sum of that category's listed occurrences dated in earlier months of the chain,
  and an occurrence deleted from the list leaves the notice. Deleting it from the notice is
  a correction (CP21): the CP20 list of later months is shown before saving. *Rejected:* a preview before
  catch-up (it would block recording payments that are already due) and a silent
  recalculation (it breaks CP9 and CP20).
- **CP19 (Proposed)** The D03 forecast `S × N / d` is spending only; carry never enters it,
  and no verdict uses it. Wherever it is drawn against a limit, that limit is `C`, labelled
  an estimate. Category detail (ADR 0015) uses the category's `C` and `R` for its remaining
  week and day figures; its averages of completed months are spending and are unchanged.
  The two-months-over suggestion judges `S > C`. Ordering by limit sorts by base, which
  does not change with spending. Charts mark `C`; the exact base, carry and capacity are
  always in the accessible value.

## Corrections

- **CP20 (Owner)** **Before saving a historical correction, Sello lists each later month
  whose availability changes and by how much; after confirmation all later months
  recalculate at once.** Base limits never change. (Automatic catch-up is the one exception,
  CP18.) *Rejected by the owner:* only the edited
  month, which would forgive deficits.
- **CP21 (Proposed)** *Correction* is any change that reaches a closed month of a chain:
  adding, editing, deleting or undoing an expense dated there; a changed date, amount or
  category (a move touches two chains, or two months of one); and a finite limit
  correction for a past month. *Availability* of a later month is its carry-in and
  capacity; the list also gives its remaining and any verdict that flips. Because
  `K(m+1) = K(m) + B(m) − S(m)`, a change of `Δ` in one month shifts **every later month
  of that chain by exactly `Δ`**; the overall figures move by the sum over categories. A
  month whose carry-in does not change is not listed. When the list is empty the usual D04
  scope confirmation is all there is.
- **CP22 (Proposed)** The list also names a **settled balance** (CP12) the correction
  changes, as "the balance not carried on <month>: −40.000 → −20.000"; months after the
  chain's end do not change. It is read at one revision; committing with a moved revision is
  refused and previewed again. Months before the edited one, other chains and months
  outside any chain never change. An edit in the current month needs no preview: the
  month's own figures update and next month's carry is not yet a month anyone can view.

## Failures and bounds

- **CP23 (Proposed)** Every derived value (carry-in, capacity, remaining, overall totals)
  must fit a signed `Long`, computed with widened arithmetic. Otherwise the read fails with
  the typed unrepresentable failure of the snapshot; nothing is clipped, saturated or
  zeroed. A command whose result would make any affected month unrepresentable is refused
  with nothing changed.
- **CP24 (Proposed)** Time is the financial month in the persisted financial zone; a month
  closes with its last financial day; travel rewrites nothing. A month before a chain's
  start never carries, whatever the clock.
- **CP25 (Proposed)** A base limit of a category in a chain is at most **999.999.999.999**
  (the transaction cap, no new number). Limits are otherwise unbounded, so without this
  months passing could overflow a chain with no command to refuse. Enabling and limit edits
  above it are refused; a backup with such a chain is rejected. *Rejected:* no bound.

## Recovery and reports

- **CP26 (Proposed)** A backup holds the **chains** (category, start month, end month or
  open) beside the records and the base history, and **never** carry, capacity or remaining:
  they are derived, and a stored value would be a cache to distrust. Backups of versions 1–5
  read as every category off; no starting deficit is guessed. Validation before the
  preview: no overlap, start ≤ end, a finite base in every month of a chain, an open chain's latest default finite (CP3),
  base within CP25; a bad file changes nothing. Restore replaces the chains; reset removes them. Until
  SELLO-041 the customer app refuses a file with an enabled chain before any change.
- **CP27 (Proposed)** Report provenance: any on-screen report of a carry month gives base,
  carry-in, capacity, spent and remaining, the chain's start month, and the revision and
  as-of day they were read at. Carry is never labelled a balance, income or expense. The
  CSV and JSON data exports (SELLO-121, `JsonExport` version 1) stay as they are: records
  and base limits, no carry, capacity or remaining; adding them would need its own decision
  and ticket.
- **CP28 (Proposed)** Every setting change is a category command with operation ID,
  generation and the category version, like the other category commands; its receipt
  survives restart and an identical replay returns it.

## Presentation wording (Proposed)

Spanish, amounts grouped with dots and a true minus (−). SELLO-041 designs the layout and
may refine phrases without changing what a figure means; "Disponible" alone and "saldo"
are not used (ADR 0012 and architecture §4).

| Meaning | Wording |
| --- | --- |
| Setting | **Arrastrar al mes siguiente**; help: "Lo que te sobra o te pasas en el mes pasa al siguiente. Empieza en 0 este mes." |
| Base | **Límite base** 100.000 |
| Carry-in, positive | **Arrastre** +80.000 · "Te sobró 80.000 de meses anteriores" |
| Carry-in, negative | **Arrastre** −20.000 · "Te pasaste 20.000 en meses anteriores" |
| Effective capacity | **Límite con arrastre** 80.000 (always with base and arrastre beside it) |
| Remaining | **Restante del presupuesto** (signed; Excedido by the exact amount when negative) |
| Drop | "¿Dejar de arrastrar −20.000?" · "Este mes el límite vuelve a ser el base. No se borra ningún gasto." · **Dejar de arrastrar** / **Cancelar** |
| Catch-up notice | "Al registrar tus pagos pendientes cambió el límite con arrastre" · "mar 2027: 1.200.000 → 1.000.000 (−200.000)" |
| Correction | "Esto cambia el límite con arrastre de 3 meses" · "mar 2027: 210.000 → 230.000 (+20.000)" · **Guardar** / **Cancelar** |

## Effect on existing meanings

D04's "each MVP month renews its configured budget without carrying" stays true for every
category that is off, which is every category until a person turns it on. `MonthlyBudgetPolicy`
keeps its no-carry answers when no chain exists. Code that reads a category's limit
(`LimitedBudget.limit`, Resumen's allocation, the category order) must keep the base
separate from `C`; CP17 and CP19 say which each uses. ADR 0002's open decisions point here.

## Mapping to the tickets

| Ticket | Implements | Examples |
| --- | --- | --- |
| SELLO-038 domain | CP4–CP8, CP10, CP14–CP19, CP21–CP25: chain replay, base/carry/capacity/remaining, verdicts, allowance, overall, affected range, typed failures | SC01–SC07, SC09–SC16, SC23–SC33, SC35–SC41, SC46, SC47 |
| SELLO-039 storage | CP1–CP4, CP9–CP13, CP20–CP22, CP25, CP26, CP28: chain storage, commands and refusals, atomic correction and preview fence, no activation before recovery and UI | SC08, SC17–SC22, SC25–SC34, SC46, SC47 |
| SELLO-040 portable | CP25–CP27: chains in the file, version rules, validation, replay equals SC01, reset | SC40, SC42–SC45 |
| SELLO-041 UI | CP9–CP11, CP14–CP22, CP27 and the wording above: opt-in, drop and correction confirmations, breakdowns | SC08–SC22, SC25–SC34, SC46, SC47 |
| SELLO-042 sandbox | The named histories through real commands, never stored carry arrays | SC01–SC05, SC17–SC21, SC25–SC30, SC37, SC41, SC47 |

## Approval

Product: the owner's answers of 2026-10-28, given to the orchestrator of the E09 run, and
approval of the Proposed rules by merging the SELLO-037 pull request. Independent review:
an adversarial reviewer agent (Claude Opus 5.5) reviews the pull request; its verdicts and
the answers to them are the pull request's comments. It is an agent, never a human approval.
