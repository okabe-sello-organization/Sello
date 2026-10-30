# SELLO-E16 — Finish Sello v2 on today's screens

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** Add the parts of the approved v2 Recibo, Resumen and income screens that the MVP left out, which need no new kind of data.
- **Exit:** Resumen leads with the same-days comparison and shows the average month, the weekday answer and one suggestion; income history suggests assigning unassigned income; Recibo invites a look at the month just closed.

## Decisions

**Owner direction (2026-10-23):** E13 built only what existing data supports and left
these for separate tickets. Decisions for their open points are in [ADR 0017](../../decisions/0017-goals-recurrence-investments.md); the
rest of each rule is in the [v2 README](../../design/v2/README.md) and the
[Resumen and income brief](../../design/screen-briefs/summary-and-income.md).

**Owner decision (2026-10-23):** SELLO-080, SELLO-081 and SELLO-082 were built before
the SELLO-079 audit, in owner-directed design work on the running app, and are
delivered without waiting for it; they no longer depend on SELLO-079, which credits
them. The weekday period is the last six months (ADR 0017, amended).

**Owner decisions (2026-10-29):**

- SELLO-079 is dropped and its file removed; SELLO-083 and SELLO-084 no longer depend
  on it. Each checks the existing code and catalog itself before building.
- SELLO-083: **Asignar a una categoría ›** prefills the new category's limit with the
  unassigned amount only when that amount is a valid limit; otherwise the limit starts
  empty. The amount is never clipped to fit.
- SELLO-084: the invitation is dismissed with a small × at the end of the dashed line,
  like the Resumen suggestion; tapping the line opens the previous month in Resumen.

**Owner decisions (2026-10-29, SELLO-083 review):**

- A "valid limit" for the prefill means one that fits the limit keypad: at most 12
  digits (999.999.999.999), from one definition shared with the keypad. Whether
  category limits should cap at 12 digits everywhere is
  [#256](https://github.com/okabe-sello-organization/Sello/issues/256).
- The income suggestion gets the design's ×: dismissing hides it for that month,
  across restarts, as a nonfinancial preference cleared by reset (no Room or backup
  change); it may return next month while something is still unassigned.
