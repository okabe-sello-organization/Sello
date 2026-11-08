# SELLO-208 — Resumen and Ingresos explain themselves

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-204, SELLO-206
- **Gate:** G2

### Outcome
On Resumen and Ingresos, a **?** after a card's title explains the card and an underlined
term explains one word, matching what the app computes.

### Acceptance criteria
- **? on these cards**, each opening a help sheet with a link to the right Guía page:
  - Resumen "¿Cómo va este mes?": what comparing the same days of each month means.
  - Resumen "Gastos hormiga": what counts as a small expense and where the line is set.
  - Resumen "¿Qué días gastas más?": how the daily average is worked out.
  - Ingresos "Lo que entró": how income is assigned, sin asignar, and the plan line
    that includes expected income.
- **Underlined terms**: **al mismo día del mes / mismos días** (Resumen), **Sin asignar**
  (Ingresos).
- Cards that already explain themselves get nothing: Mes a mes, Ver tu año, the income
  slip's totals.
- Each explanation states the rule the code applies today, with one small worked
  example in round numbers that are not anyone's data.
- Nothing else moves on these screens; states behave as in SELLO-207.
- Screenshot tests show Resumen's top card with its **?** and underline and one sheet, in
  light and dark; a ViewModel or UI test opens each sheet.

### Notes
- Lighter review. The owner reviews the copy; cite the code each explanation comes from.
