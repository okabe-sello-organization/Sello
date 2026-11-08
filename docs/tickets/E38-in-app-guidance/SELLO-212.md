# SELLO-212 — Nudges for Anotar varios, deleting, pagos fijos and arrastre

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-211
- **Gate:** G2

### Outcome
When a feature becomes useful, a single quiet line tells the person it exists and where
it is, once.

### Acceptance criteria
- Each nudge is a nudge row (no pill) with a ✕, follows SELLO-211's rules (one at a time,
  one a day, closed for good once done or dismissed) and is not shown to people already
  using Sello when the update landed.
- **Anotar varios**: in expense entry, next to the "Otro gasto" slip under the amount,
  when the person opens Anotar after saving three single expenses the same day. Text
  says the slip records several at once. Using the slip closes it.
- **Deleting**: in a category's expense list, the first time it shows an expense. Text
  says a deleted expense can be undone for a few seconds; where Borrar is already shows
  on screen (`category_detail_hint`). Deleting an expense closes it. *Changed on the
  orchestrator's ruling (2026-11-08), pending the owner's OK.*
- **Pagos fijos**: in category detail, when the same amount with the same note was
  recorded in that category in each of the two previous months and there is no fixed
  payment for it. Text says it can become a pago fijo. Creating any fixed payment
  closes it.
- **Arrastre**: in category detail, when the previous month ended over or under that
  category's limit and the category's arrastre is off. Text says what was left or
  missing can carry to this month and where to switch it on. Turning arrastre on in any
  category closes it.
- Each text is checked against the code (where the action lives, how long undo lasts)
  and stays one or two lines.
- ViewModel tests for each trigger and its "closes" rule with controlled clocks; one
  screenshot per nudge in light.

### Notes
- If a trigger needs a new storage query (the pagos fijos match may), that part gets
  the adversarial review and a real-Room test; prefer reusing existing reads (the
  repeated-expense work from E34) where they fit.
- Since #452, several entries are reached only from category detail and the slip under
  the amount; point there.
- **Arrastre text, proposed change (pending a ruling):** ADR 0020 CP2 says carry starts
  at 0 in the month it is turned on, so last month's balance can never carry into this
  month. The text says instead what last month left or missed and that, from this month,
  what is left or missing can pass to the next month (Editar categoría). Proposed
  criterion: "Text says whether the month before left or missed something and that,
  switched on in Editar categoría, what is left or missing passes to the next month."
