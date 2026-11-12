# SELLO-216 — The person picks any colour for Tuya

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-214, SELLO-215
- **Gate:** G2

### Outcome
In Apariencia › Tinta the person opens Tu tinta, picks any colour while seeing it on the
important parts of Sello, and the whole app takes exactly that colour.

### Acceptance criteria
- Tu tinta looks and works as the [mock](attachments/tuya-colour-mock.html) shows: light
  and dark previews with the band, a selected chip, Anotar, Restante with AL DÍA, a
  category over its limit (EXCEDIDO, "Te pasaste por 40.000") and a goal band; the
  colour's name with its code; a colour square and a colour bar; Antes / Ahora tiles
  with white "Aa"; one line; Cancelar and Usar esta tinta.
- Dragging the square or the bar changes the colour and every preview at once.
- Tapping the code lets the person type one: `#RRGGBB` or `RRGGBB`, any case. A valid
  code moves the square and the bar to it; anything else changes nothing, and leaving the
  field shows the current code again.
- Antes shows the ink in use when the sheet opened, by its name; tapping it returns to
  that colour. Ahora shows the colour being made and its name.
- The line shows "Arrastra para elegir, o toca el código para escribir uno exacto." or,
  when SELLO-214's rule says so, "Se parece al rojo que marca los excesos." or "Se parece
  al verde de las metas y de lo que va bien." in a soft red box. Its height never changes,
  so nothing below moves.
- Tu tinta opens on Tuya's kept colour, else the colour Tuya's kept hue gives, else the
  ink in use.
- Every colour can be saved: nothing is blocked and there is no contrast warning. Nothing
  changes until Usar esta tinta; then every screen changes at once, the light theme shows
  the colour exactly, and the colour is stored (SELLO-215).
- The colour being chosen survives rotation and process death while the sheet is open.
  A failed save behaves as today: the sheet stays with the colour, names the ink still in
  use and offers Reintentar; a second tap while saving does nothing.
- Tinta's Tuya swatch shows the exact colour, and its caption names it.
- One layout at every font size. TalkBack is out of scope
  ([ADR 0028](../../decisions/0028-tuya-honours-the-exact-colour.md)); the code field
  stays a normal text field.
- The settings and foundations design pages show the new sheet with real screenshots.

### Notes
Screen change: lighter review with before and after screenshots. Reuse E37's
`TuyaSheet` pieces where they fit. No device test by default.
