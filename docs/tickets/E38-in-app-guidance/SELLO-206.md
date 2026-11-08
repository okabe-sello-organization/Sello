# SELLO-206 — Guía de Sello in Configuración

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-204
- **Gate:** G2

### Outcome
From Configuración the person opens the Guía de Sello: short, plain pages for each part
of the app that work offline, and that every help sheet can open at the right section.

### Acceptance criteria
- "Guía de Sello" is the first row in Configuración and opens the Guía.
- The Guía lists, as in the [mock](attachments/guidance-mock.html):
  - **Empieza aquí:** "Tu primer mes en 3 pasos" (categorías, límites, tu primer gasto)
    and "Gestos que ahorran tiempo" (the shortcuts: long-press a category to edit it,
    swipe an expense to delete it, long-press the pad's delete key to clear the amount).
  - **Por pantalla:** Recibo; Anotar y Anotar varios; Resumen y Tu año; Metas;
    Inversiones y dólar.
  - **Tus datos:** Respaldo (the data lives only on this phone; how to back up and
    restore).
- Each page is short Spanish built from the rules the code applies today (see SELLO-207
  to SELLO-209 for the terms), with no figures from real data. "Anotar varios" names the
  two ways in that exist: the "Otro gasto / ingreso / movimiento" slip under the amount
  and Anotar varios in a category.
- Help sheets can open the Guía at a given page; back returns to where the person was.
- No search in this version. The structure leaves room for pro tips and a full written
  tutorial later without changing the existing pages.
- Screenshot tests cover the Guía's list and one page in light and dark; the page and
  scroll position survive rotation and process death.

### Notes
- Content is static string resources; nothing is stored. Lighter review.
- Check each statement against the code before writing it; the owner reviews the copy.
