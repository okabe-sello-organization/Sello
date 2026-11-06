# SELLO-E36 — Share the month

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person sends an image of the month's budget to a partner or family through Android's share sheet.
- **Exit:** The month's budget is shared as an image from the place the owner picks, with only the approved content, and the privacy page describes it.

## Decisions

**Owner direction (2026-11-06):**

- The image shows the budget only: the month, the headline with its stamp, and each
  category's spent and limit. Never notes, income, goals or investments.
- Nothing leaves the phone without the person choosing the app to share with.

**Planner's calls (2026-11-06), open to the owner:** the owner picks where the button
goes from the [mock](attachments/share-mock.html): A, Recibo's bar (recommended), or B,
Ajustar presupuesto's bar. A preview sheet shows the exact image first. The image always
uses the light paper with the person's ink. Older figures can't be shared. The PNG lives
in `cache/exports/` like CSV exports. Privacy change: adversarial review, and the owner
merges.
