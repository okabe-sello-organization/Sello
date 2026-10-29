# SELLO-E12 — Bring Recibo to the approved Sello v2 design

- **Status:** Done
- **Increment:** MVP
- **Goal:** Make Home answer "how am I doing this month" with the factual status, wording and frame approved in Sello v2.
- **Exit:** Recibo's status, answer slip, categories, month bar, dock, tabs and save feedback match the v2 Recibo states in today's scope, with existing journeys, recovery and accessibility intact.

Read [ADR 0012](../../decisions/0012-budget-guidance-contract.md),
[ADR 0013](../../decisions/0013-sello-v2-design.md), the
[v2 README](../../design/v2/README.md#recibo--home), the
[v2 Recibo mockup](../../design/v2/home.html) (its "Today's scope" state shows
the MVP) and the [Home brief](../../design/screen-briefs/home.md). D01 keeps
v2 as presentation guidance only: goals, recurrence and reservations are not MVP,
so the goal pill, "Llegó al 100 %" row, reserved-payment footnote, **Sin margen**
and **Revisar** are out of this epic and must not appear as placeholders.

## Decisions

**Current focus (owner decision, 2026-10-16):** bring Recibo to a working v2 state
before new features and before MVP release work; SELLO-033 therefore depends on this
epic. Read dependency links for order: SELLO-068 and SELLO-069 wire the built
components into the app, SELLO-061 must precede SELLO-062 and SELLO-067.

**Components already built (owner decision, 2026-10-17):** the v2 components were
built and reviewed in the catalog first (pull request #54, `catalog-v2-components`):
`AnswerSlip`, `V2MonthBar`, `PastMonthRibbon`, `V2Dock`, `V2NavigationBar`, the
confirmation `SelloNotice`, `V2ChipRows`, `AddChip` and the rest of
`designsystem/component/v2`. Tickets here reuse them instead of building their own,
and SELLO-068 and SELLO-069 are reduced to wiring them into the app. Retiring the v1
components they replace is SELLO-073.

These tickets come from a 2026-10-16 comparison of the running app (`98df571`,
debug build on an API 33 emulator, synthetic data) with v2. The early-month review
invitation ("Ver cómo cerraste octubre ›") is not ticketed: its timing and dismissal
are still open in the Home brief.
