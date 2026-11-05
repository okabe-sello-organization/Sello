# 0013 — Sello v2 is the visual source of truth

**Owner:** project owner (user) · **Updated:** 2026-10-16
**Status:** superseded by [ADR 0026](0026-design-system-from-the-built-app.md) (2026-11-05); approved design; implementation requires scoped work.

[Sello v2](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/v2/README.md) is the approved visual reference for Recibo,
entry, Resumen, income, Metas, Inversiones and the
[shared components](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/v2/components.html). The
[v1.1 snapshot](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/sello-spec.html) covers only what v2 does not show.
Its sample numbers are synthetic. [Architecture](../../ARCHITECTURE.md) and
ADRs 0001–0012 own money, dates, recovery and safety; design approval adds no code,
routes, ledger or data model.

## Screen rules

- Every amount uses the Sello pad. Text uses the phone keyboard; notes use a
  composer docked to that keyboard.
- Budget stamps use **Al límite**, **Excedido**, **Sin margen** and **Revisar**;
  the link reads **Ajustar presupuesto**, and daily guidance reads **40.000 por día**.
  Status meanings follow [ADR 0012](0012-budget-guidance-contract.md).
- Past months use **Cumplido** within the limit and **Excedido** above it.
- Resumen leads with the same-days comparison. Net flow stays in the income slip
  immediately below, with its financial meaning unchanged.

## Goals and investments

- After a withdrawal, an earned milestone stamp stays faded with a line saying
  it is still yours. Percentage and bar show current progress; re-crossing lights
  the stamp again.
- Reaching 100 % is announced. **Marcar como lograda** completes the goal, earns
  the Meta stamp and plays the big celebration. A toast offers **Deshacer** for
  about 10 seconds; afterward there is no reopening.
- Completed goals are read-only: no edits, contributions or withdrawals. Deletion
  is their only action. Goal withdrawals cannot exceed the amount saved.
- Projected months appear only when creating or editing a goal, with the assumed
  pace stated. Monthly amounts use whole pesos without further rounding.
- Investment withdrawals cannot exceed the last recorded value. The first value
  defaults to the first contribution and is editable. An investment without a
  value is excluded from the total; only its row explains the missing value.

## Implementation boundary

Use v2 for presentation and its component catalog to identify existing components,
needed variants and new design-system work. Product rules above need scoped tickets
and reviewed examples before implementation. Investment ledger, return periods,
dividend and FX policies, and goal corrections remain later decisions.
