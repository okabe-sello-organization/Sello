# SELLO-210 — Start with a suggested category

- **Type:** Story
- **Priority:** P1
- **Status:** Ready
- **Depends on:** None
- **Gate:** G2

### Outcome
On a brand-new Recibo, the person can start from a suggested category with one tap
instead of a blank form, and still decides everything before anything is saved.

### Acceptance criteria
- The empty Recibo ("Dale forma a tu mes") keeps "Crear categoría" and adds, under "O
  empieza con una de estas:", chips for Comida, Transporte, Casa, Ocio and Ahorro, each
  with an existing category icon, as in the [mock](attachments/guidance-mock.html).
- Tapping a chip opens the normal category editor with that name and icon filled in and
  **no limit**: the person sets the limit (or Sin límite) and nothing is saved until
  Guardar. Leaving the editor saves nothing.
- The chips show only in the empty state; once any category exists they are gone. A
  goal shown on the empty Recibo stays where it is today.
- The draft opened from a chip survives rotation and process death like any editor
  draft.
- TalkBack reads each chip as "Empezar con Comida" (and so on).
- Screenshot tests show the empty Recibo with chips in light and dark; a test opens the
  editor from a chip with the name and icon filled and no limit.

### Notes
- Lighter review. Never suggest an amount (owner decision).
