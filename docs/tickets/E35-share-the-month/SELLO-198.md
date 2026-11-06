# SELLO-198 — Share the month's budget as an image

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** None
- **Gate:** G2

### Outcome
From the month on screen, the person previews an image of its budget and shares it with
any app they choose.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/share-mock.html). The button sits on
  Resumen's bar, between the magnifier and Configuración, and shares the month on
  screen. The share icon is added to `:design-system`.
- A preview sheet shows the exact image and says what is in it and what isn't. Nothing
  is written or sent until Compartir imagen. Cancelar writes nothing.
- The image shows the month and its date ("al 28 de octubre"), Recibo's headline and
  stamp, spent against the total limit, and each category with its spent and limit, or
  "sin límite". It includes archived categories only when they have spending that month.
  A past month says Mes cerrado with its Cumplido or Excedido stamp.
- The image never contains notes, income, goals, investments or settings. A JVM test of
  its render model proves those fields don't exist.
- The figures come from Recibo's snapshot. While Recibo shows older figures, sharing
  waits and says why.
- The PNG is written to `cache/exports/` (the only shared folder), replaces the previous
  export, and is deleted at the next start. The share intent grants read access to that
  one file. The [data handling page](../../privacy/mvp-data-handling.md) is updated.
- Light paper with the person's ink in either app theme. Screenshots of the image with
  each ink, an overrun, an unlimited category and a past month; and of the sheet.

### Notes
Adversarial review (privacy); the owner merges.
