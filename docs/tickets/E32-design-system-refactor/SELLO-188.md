# SELLO-188 — Carry turns on or off when the category is saved

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-181
- **Gate:** G2

### Outcome
In the category form, turning carry on or off is saved with Guardar like every other
field; forgetting a carried balance is confirmed as a destructive action.

### Acceptance criteria
- Turning carry off asks the same confirmation, with its action in loss ink; nothing is
  stored until Guardar. Leaving the form without saving changes nothing.
- Turning carry on also waits for Guardar.
- The save stores the category and the carry change together, with one operation ID,
  safe to replay; an unknown outcome is checked before any retry.
- Tests: stale version, double submit, unknown commit, cancellation, failed read,
  process death with the change pending; which do not apply is said in the pull request.
- ADR 0020 gets a dated note; the category page describes the new flow.
