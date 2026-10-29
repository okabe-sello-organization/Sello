# SELLO-061 — Replace the pace verdict with the factual budget status

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-014, SELLO-018, SELLO-004
- **Gate:** G2

### Outcome
Recibo and category detail judge the month on what was actually recorded: Al día,
Al límite, Excedido or Sin límite, and Cumplido or Excedido for a past month.

### Deliverables
A replacement for the current-month `Verdict` in `MonthlySnapshot`/
`MonthlyBudgetPolicy` that follows ADR 0012 BG1, past-month results named Cumplido/
Excedido, the Recibo and category-detail stamps, cell and slip spoken descriptions
in the new words, and updated D03 and example-corpus text that retires the pace
verdict for Home.

### Acceptance criteria
- Below a finite overall limit is Al día, including an explicit zero limit with zero
  spending; equal to a positive limit is Al límite; above it is Excedido with the
  exact signed overrun; no finite limit is Sin límite.
- A category uses the same rule against its own limit. A category overrun never
  changes the overall status (BG-E04, B08).
- Past months are Cumplido at or below the limit and Excedido above it.
- The daily allowance keeps the approved COP 50 rounding: 25.350 for 406.300 over 16
  days; 0 at or above the limit.
- No stamp, spoken description or test in Recibo or category detail still uses Justo,
  Pasado, Bajo el límite or Sobre el límite.
- A failed read is never shown as Al día or zero; stale last-good data stays labelled.

### Tests
Domain policy tests built from BG-E01, BG-E04, BG-E05, BG-E07, B05 and B08 with
independently written expected values; render-model tests for each stamp and spoken
description; `ReciboScreenTest` and category-detail screen tests on device; G2.

### Working checkpoint
With 393.700 spent of 750.000 on 16 Oct, Recibo shows Al día and 356.300 left, not
the red Pasado the pace verdict shows today.

### Context and starting points
Evidence from 2026-10-16: with 393.700 spent of 750.000 on day 16, the forecast
(762.794) exceeds the limit, so Recibo shows "Te quedan 356.300 · Pasado"; Hogar at
200.000 of 400.000 shows "Justo". ADR 0012 BG1 and D03 "Approved design" define the
replacement; D03 "Current calculation" says the forecast stays until this ticket.

The owner confirmed BG-E01 and BG-E04 on 2026-10-17; ADR 0012 records the values.

Start at `domain/.../model/MonthlySnapshot.kt` (`Verdict`, `PastVerdict`,
`BudgetStanding`), `domain/.../policy/MonthlyBudgetPolicy.kt` (`verdict`),
`app/.../feature/recibo/ReciboRenderModel.kt` (stamp mapping) and the `recibo_stamp_*`
and cell strings in `app/src/main/res/values/strings.xml`.

### Implementation plan
1. Write failing domain tests from the reviewed examples, then replace the
   current-month verdict with the factual status. Keep the exact `remaining` sign.
2. Keep `Forecast` in the snapshot for Resumen's estimate line; only Recibo stops
   using it. Do not delete it while Resumen still displays it.
3. Rename the past-month result to Cumplido/Excedido and map both in the render model.
4. Update stamp inks (gain for Al día and Cumplido, neutral for Al límite and Sin
   límite, loss for Excedido), strings, spoken descriptions and screen tests.
5. Update D03 "Current calculation" and the B04/B05/B08 wording in the example
   corpus to the new names, without changing any approved amount.

### Concrete cases and pitfalls
- Spending exactly the limit is Al límite in the current month but Cumplido in a
  past month; do not reuse one enum for both without checking.
- Resumen's actual-result stamp also reads `PastVerdict`; check its words change
  consistently or leave its own strings untouched on purpose and say so.
- Unlimited categories still count toward overall spending (B01).
- Do not derive expected amounts with the policy under test.

### Verification recipe
Focused `:domain:test` and `:app:testDebugUnitTest` for the policy and render model,
then `ReciboScreenTest` on a device, then `./scripts/verify-ticket SELLO-061 --retain`.
