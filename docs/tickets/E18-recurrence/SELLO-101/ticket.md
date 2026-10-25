# SELLO-101 — Show reservations on Recibo

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-098, SELLO-100, SELLO-114, SELLO-004
- **Gate:** G2

### Outcome
Recibo's headline is after reserving upcoming payments, with Sin margen and Revisar, and the reserved footnote.

### Deliverables
ADR 0012's reserved states in the answer slip, the footnote and the list of upcoming payments as designed in SELLO-114, each payment opening its schedule (SELLO-100). Reserved `Q`, margin `M` and the daily figure from `M` in the domain; the Sin margen and Revisar stamps and the **Falta para tus pagos** label in Recibo's state. The catalog's *Recibo answer states* corrected to the design: Revisar's sentence under the big number, then the tear line, `0 por día`, the footnote and **Ajustar presupuesto**; Sin margen with its footnote.

### Acceptance criteria
- Headline = remaining − reserved, per ADR 0012 and its examples.
- Sin margen and Revisar appear exactly when their rows say; Revisar comes before Excedido while payments are reserved.
- No reservation line when nothing is reserved.
- The answer slip follows the v2 order in every reserved state, in the app and the catalog.

### Tests
Domain example tests (ADR 0012 BG rows) and screen tests; device tests for `Footnote` and the warn stamp ink, which have none; G2.

### Working checkpoint
Recibo shows the reserved states correctly.

### Context and starting points
ADR 0012; v2 Recibo; the [gap list](../../../design/v2/gaps/recurrence.md): reuses `AnswerSlip`, `Stamp` (`StampInk.Warn`), `Footnote` and `ReciboScreen`'s **Ajustar presupuesto** link, which today shows only for Excedido.

### Implementation plan
1. Domain changes.
2. Slip states and footnote.
3. Payments list.

### Concrete cases and pitfalls
- Categories never show reservations.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-101 --retain`.

### Execution progress

- **Rule:** `ReservationPolicy` reserves the payment occurrences of the month in progress
  not yet handled, a due one catch-up has not recorded included (rule 11), and works out
  `Q`, `M = R − Q`, its standing (Al día, Sin margen, Revisar) and, while short, `−M` and
  `−R` exactly. `MonthlyBudgetPolicy` takes them for the month in progress with a finite
  limit only and draws the daily figure from `M`. `RoomMonthlySnapshots` reads the
  schedules in the month's transaction, so `Q` is of the same revision as the spending.
- **Recibo:** **Falta para tus pagos** with Revisar (warn) before Excedido, Sin margen
  (neutral) at 0, the sentence under the big number ("Quedan …" or, past the limit,
  "Pasaste tu límite por …", owner's RE29 wording), the footnote "Incluye … separado para
  … pagos previstos ›" and **Ajustar presupuesto** for Revisar. Categories never show it.
- **Pagos previstos:** opened by the footnote, from the same snapshot; each payment
  opens its schedule, and **Ver todos los pagos fijos ›**.
- **Catalog:** *Recibo answer states* in the v2 order, Sin margen with its footnote and
  Revisar past the limit.
- **Sandbox:** the reservation checks come with SELLO-104's second part, after this.
- **Done (2026-10-25):** gate and device evidence in [evidence.md](evidence.md).
