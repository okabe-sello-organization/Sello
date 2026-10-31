# SELLO-135 — See a category's fixed payments and a per-day figure that counts them

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
On category detail, the big number, Al día and the week's remaining allowance leave room
for the category's payments still to come this month, and a Pagos fijos slip shows them
and the category's other fixed payments.

### Acceptance criteria
- Jue 22 oct 2026, Hogar: limit 1.500.000, 950.000 spent, Internet 90.000 (23rd) and
  Servicios públicos 200.000 (28th) still to come. The hero reads **Te quedan aquí
  260.000**, Al día **26.000** for 10 days, and the footnote "Incluye 290.000 separado
  para 2 pagos previstos ›" brings Pagos fijos into view. Recibo's cell for Hogar still
  shows 550.000.
- The week of 22–28 has 182.000 still available (260.000 × 7 ÷ 10).
- With 1.210.000 spent the hero is **Sin margen**, 0, and Al día 0. With 1.300.000 spent
  it is **Revisar**, "Falta para tus pagos" **90.000**, with "Quedan 200.000 y tus pagos
  previstos suman 290.000." and **Ajustar presupuesto ›**.
- **Pagos fijos** lists **Por venir en octubre** (each payment's name, date with
  "Mañana" for tomorrow, amount) and **Más adelante** (each other fixed payment that has
  not ended: rule and next date, or "en pausa desde"), then **Ver todos los pagos fijos ›**.
  Each row opens its schedule.
- A past month and a category with no fixed payments show no slip; a category without a
  limit lists its payments and has no per-day figure. A failed read of the schedules keeps
  the last list read, or says it failed; it is never shown as none.
- The category's figures and the month's reservation come from the same read and revision.

### Notes
Design: [categoria-fijos.html](../../design/v2/categoria-fijos.html). The rule amends
[ADR 0015](../../decisions/0015-category-detail-insights.md).
