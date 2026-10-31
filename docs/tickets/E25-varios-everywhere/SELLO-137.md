# SELLO-137 — Varios on the goal and investment entry sheets

- **Type:** Story
- **Priority:** P1
- **Status:** Ready
- **Depends on:** None
- **Gate:** G1

### Outcome
From Aportar/Retirar or Anotar movimiento, the person taps Varios and keeps typing on the
goal's or investment's bulk receipt, with what they typed as line one.

### Acceptance criteria
- Aportar/Retirar and Anotar movimiento show the Varios pill of Anotar's bar for a new
  movement. It waits until a goal or an investment is chosen, and is not offered when
  correcting a movement.
- Varios opens Aportar varios for the chosen goal, or Varios movimientos for the chosen
  investment. The amount, note, date and Aportar/Retirar or movement type typed so far
  become line one, checked like any input; a hint that is not valid is dropped.
- A USD investment's amount keeps its cents.
- A restored receipt never takes the hints again, and nothing is saved until the receipt's
  own button is pressed.
- ViewModel JVM tests cover the hints, a bad hint and the restored receipt.
