# SELLO-E24 — Fixed payments on category detail

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** Category detail shows the category's fixed payments and works its figures out from what those still to come this month leave.
- **Exit:** The hero, Al día and the week's stripe count the category's own reserved payments, as Recibo does for the month, and a Pagos fijos slip lists what is still to come and the category's other fixed payments.

## Decisions

**Owner direction (2026-10-30):** the per-day figure must count what is still to come
instead of showing an amount that will not be there. The slip lists what is still to come
**until the end of the month** and **every fixed payment in the category**. The owner
approved the mock ([categoria-fijos.html](../../design/v2/categoria-fijos.html)), with
these choices made by the agent: the hero shows what the payments leave, as Recibo's
answer does (ADR 0012 BG2); the slip sits right under the hero; past months and
categories with no fixed payments show no slip.
