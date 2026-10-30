# SELLO-062 — Rebuild the Recibo answer slip to v2

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-061, SELLO-004
- **Gate:** G2

### Outcome
The answer slip reads like the v2 Recibo: the remaining budget under one label, a
signed overrun, and the per-day figure as a clear second number.

### Deliverables
The v2 answer slip in `ReciboScreen.kt` for the current month, an overrun, no limit
and a past month, with strings, spoken descriptions, previews and screen tests.

### Acceptance criteria
- The label is **Restante del presupuesto** with the status stamp on the same line,
  then the big number. An overrun shows the signed remainder in loss ink, e.g.
  **−43.700**, not "Te pasaste por 43.700".
- With no finite limit the label is **Has gastado este mes**, with no per-day line.
- In the current month a tear line follows, then **25.350 por día** as a second
  large figure and **16 días, incluido hoy** below it; with one day left it reads
  **1 día, incluido hoy**. The per-day line is always shown in the current month,
  including **0 por día**.
- A past month shows what was left at close and `Mes cerrado · gastaste 920.000 de
  1.000.000.`, with no per-day line.
- The sign is part of the spoken amount; meaning never depends on colour alone.
- The slip stays readable at font scale 2.0 and with reduced motion.

### Tests
Screen tests for each state above, including the one-day and zero per-day cases;
updated previews; spoken-description assertions; G2.

### Working checkpoint
Recibo on a device matches the "Today's scope" and "Excedido" states of the v2
Recibo mockup, without a goal or reserved-payment line.

### Context and starting points
Today the slip says "Te quedan", shows the per-day figure as a dotted line
("Puedes gastar al día … 25.350 / Para 16 días") and shows an overrun unsigned.
Current strings: `recibo_headline_*`, `recibo_days`, `recibo_per_day`. Components:
`Slip`, `TearLine`, `MoneyText`, `Stamp` in `:design-system`. The **Ajustar
presupuesto** link is SELLO-067, not this ticket.

### Implementation plan
1. Change the render model to provide one label, the signed remainder and the
   per-day values; keep amounts formatted in presentation only.
2. Lay out the slip: label and stamp row, big number, tear line, per-day figure and
   caption, or the closed-month sentence.
3. Update strings with correct plurals, previews and screen tests.

### Concrete cases and pitfalls
- The past-month sentence needs both the spent amount and the limit from the same
  snapshot; never mix revisions.
- A long remainder must not wrap; check 12-digit amounts at large text.
- Do not show a reserved-payment line: recurrence does not exist in the MVP.

### Verification recipe
`ReciboScreenTest` and preview checks, a manual pass at font scale 1.0, 1.3 and
2.0, then `./scripts/verify-ticket SELLO-062 --retain`.
