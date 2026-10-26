# SELLO-E22 — Where the income went, on Resumen

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** Resumen's income slip answers where the month's recorded income went: what came in, the spending categories that took it and what is left.
- **Exit:** One slip reads from what came in, through every spending category as the ranking shows it, to Ingresos menos gastos, and ends in a stub that opens the income list and Pagos e ingresos fijos with one fact each; "¿Dónde se va?" is part of it.

**Owner direction (2026-10-26):** the owner chose concept A, "El recibo del ingreso",
and its tear-off stub ("Talón") from the concepts reviewed in conversation, then decided:

- Spending only. Goal and investment records are not shown as destinations of income:
  [ADR 0012](../../decisions/0012-budget-guidance-contract.md) BG4 stays as it is. The
  slip takes off spending categories, not Metas or Inversiones.
- The slip replaces the three receipt lines (Ingresos anotados, Ingresos menos gastos,
  Sin asignar a categorías) and the two links of the [v2 Resumen](../../design/v2/README.md#resumen).
  Sin asignar stays on the income screen, where it is explained.
- The stub's halves are centred (owner, after trying it on the phone).
- After SELLO-123 merged (2026-10-26): "¿Dónde se va?" and the income slip answered the
  same question, so SELLO-124 makes them one slip. The categories keep the ranking's rows
  (circle, share bar, amount, opening the category), the bars measured against
  spending, every category shown.
