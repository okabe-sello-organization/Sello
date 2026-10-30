# SELLO-063 — Update the Recibo categories card and first-use and failed states

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-018, SELLO-004
- **Gate:** G2

### Outcome
The categories card, first use and a failed read look and read like v2.

### Deliverables
Categories card header with a **+** action, plain card edge, and v2 first-use and
failed-read slips, with strings, previews and screen tests.

### Acceptance criteria
- The card header is **Tus categorías** with a **+** button named "Agregar
  categoría" (48dp target); the "queda en cada una" caption and the link under the
  grid are removed. The button opens the same category creation as today.
- Only the answer slip keeps the pinked bottom; the categories card uses the plain
  slip edge.
- First use shows **Dale forma a tu mes**, "Crea una categoría para lo que importa:
  la casa, la comida, el ocio o tu ahorro." and **Crear categoría**.
- A failed read shows **No pudimos leer tu mes**, "Tus registros siguen guardados.
  Vuelve a intentarlo." and **Reintentar**; it is never shown as first use or zero.
- Long press and accessibility actions on cells keep offering edit.

### Tests
`ReciboScreenTest` for header action, first use and failed read; first-category
journey still passes; G2.

### Working checkpoint
A fresh install shows the v2 first-use slip and creating a category from the header
**+** works.

### Context and starting points
Current strings: `recibo_first_run_title` ("Tu recibo está en blanco"),
`recibo_first_run_body`, `recibo_failed`, `recibo_categories_caption`. `Slip` has a
`pinked` parameter (default true). The v2 component catalog marks the pinked slip as
the slip "that carries the screen's answer".

### Implementation plan
1. Move the add action into the card header and remove the caption and bottom link.
2. Pass `pinked = false` for the categories card.
3. Replace first-use and failed copy; update tests that assert the old text.

### Concrete cases and pitfalls
- The two-pane layout (≥ 600dp) shows the grid beside detail; check the header
  action there too.
- Keep the empty, failed and stale states distinct.

### Verification recipe
Focused Recibo screen tests and `FirstCategoryJourneyTest`, then
`./scripts/verify-ticket SELLO-063 --retain`.
