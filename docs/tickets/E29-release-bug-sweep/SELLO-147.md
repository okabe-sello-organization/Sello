# SELLO-147 — The catch-up notice gives this month's true Restante and status

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
When catch-up changes a carrying category's later months, the notice's figures for the
current month include the current month's payments, so it agrees with Recibo.

### Acceptance criteria
- Servicios carries, base 200.000; October spent 150.000, so November's carry-in is
  50.000 and its limit 250.000. On 3 Nov catch-up records internet 80.000 dated 30 Oct
  and electricity 190.000 dated 1 Nov. The notice shows November's limit
  250.000 → 170.000, Restante 60.000 → −20.000 and the status Al día → Excedido, as
  Recibo shows at the same moment.
- With no current-month payment the figures are unchanged from today (SC37, SC47 stay
  green).
- Hand-worked literals in JVM tests at the domain or read layer.

### Notes
Audit finding: `RoomCarryoverReads.kt:129-153` leaves out every listed payment from
`before`, and `CarryoverCorrectionReplay.kt:154` adds back only earlier-month ones, so
both sides miss current-month payments. Rules CP18/CP21 of
[signed carryover examples](../../testing/signed-carryover-examples.md). Review:
adversarial (money rules).
