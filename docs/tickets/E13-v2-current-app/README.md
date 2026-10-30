# SELLO-E13 — Bring the rest of today's app to Sello v2

- **Status:** Done
- **Increment:** MVP
- **Goal:** Every screen the app already has looks and behaves as approved in Sello v2, using one set of components, before new features are added.
- **Exit:** Expense and income entry, income history and Resumen match their v2 designs for today's scope; superseded v1 components are gone from the app and catalog.

Read [ADR 0013](../../decisions/0013-sello-v2-design.md), the
[v2 README](../../design/v2/README.md) and [E12](../E12-v2-recibo/README.md), which brings Recibo and the shared frame to v2 first.

## Decisions

**Owner direction (2026-10-17):** make today's functionality look and work correctly
in v2 before adding features. Goals, investments, reservations and recurrence stay
out: they have no domain model or storage, and v2 elements that depend on them
(goal pill, reserved payments, **Sin margen**, **Revisar**, expected income) must not
appear, even as placeholders. They can be ticketed later if the owner pulls them in.
Screens v2 does not show (category detail, the category editor, Configuración) keep
their v1.1 look under ADR 0013 and pick up the v2 frame through E12.

The v2 components were built and reviewed in the catalog first (pull request #54,
`catalog-v2-components`), including `V2EntryLayout`, `EntryAmountSlip`,
`V2ChipRows`, `AddChip`, `EntryLine`, `NoteComposer`, `AnswerSlip`, `SplitBar`,
`ComparisonSlip`, `SpendingRankRow` and `V2TitleBar`. Tickets reuse them; a gap
found while wiring a screen is fixed in the shared component with a test, not
worked around in the screen.
