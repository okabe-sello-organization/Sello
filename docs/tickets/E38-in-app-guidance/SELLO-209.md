# SELLO-209 — Metas and Inversiones explain themselves

- **Type:** Story
- **Priority:** P2
- **Status:** Done
- **Depends on:** SELLO-204, SELLO-206
- **Gate:** G2

### Outcome
On Metas and Inversiones, a **?** after a card's title explains the card and an
underlined term explains one word, matching what the app records and computes.

### Acceptance criteria
- **? on these cards**, each opening a help sheet with a link to the right Guía page:
  - Metas "Guardado en metas": goals are kept apart from the budget and from
    investments; nothing is moved between them automatically.
  - Inversiones "Tus inversiones valen": values are entered by hand, not live or from a
    bank; dollar investments are added up at the rate the person entered.
  - Investment detail "Valor y lo que pusiste": money put in versus what it is worth now;
    contributions are not gains.
- **Underlined terms**: **Rendimiento / Ganancia** (Inversiones, investment detail),
  **tasa del…** (Inversiones' dollar rate line).
- Goal cards, Récords and Configuración get nothing.
- Each explanation states the rule the code applies today, with one small worked
  example in round numbers that are not anyone's data.
- Nothing else moves on these screens; states behave as in SELLO-207.
- Screenshot tests show Inversiones' top card with its **?** and one sheet, in light and
  dark; a ViewModel or UI test opens each sheet.

### Notes
- Lighter review. The owner reviews the copy; cite the code each explanation comes from.
- Rules to translate: ADR 0017 and `docs/testing/goal-examples.md`; the USD rate fact
  already in `usd_rate_fact`.
