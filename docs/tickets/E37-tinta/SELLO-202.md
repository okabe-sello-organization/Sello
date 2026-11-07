# SELLO-202 — The person picks their own ink

- **Type:** Story
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-201
- **Gate:** G2

### Outcome
In Apariencia › Tinta the person picks Tuya, chooses a hue in the Tu tinta sheet, and
the whole app takes that colour; backups and restores carry it.

### Acceptance criteria
- Tinta shows Cobalto, Violeta and Tuya. Tuya is a rainbow + until it has a hue; then it
  shows that ink's colour with an edit badge, and a caption names the colour.
- Tapping Tuya opens Tu tinta as in the [mock](attachments/tinta-mock.html): a light and
  dark preview of the hue, its name, a slider drawn in the colours it gives, Cancelar and
  Usar esta tinta. Nothing changes until Usar esta tinta; then every screen changes at
  once.
- Every hue can be saved. On red or green hues the sheet shows "El rojo y el verde
  también marcan excesos y metas."
- TalkBack reads the slider as the colour's name and can move it.
- The hue being chosen survives rotation and process death while the sheet is open.
- If saving fails, the sheet stays open with the chosen hue, says "No se pudo guardar.
  Sigues con <tinta actual>." and offers Reintentar; the app keeps its ink. A second tap
  while saving does nothing.
- The choice is stored as Tuya plus its hue. A stored or restored hue outside 0–359 is
  rejected, never clipped.
- Backups carry Tuya and its hue in a new backup version. Every older version still
  restores, and a restore keeps or applies the ink as it does today. Restore and reset
  texts name Tuya and its colour; reset returns to Cobalto.
- The settings and foundations pages show the new swatch and sheet with their real
  screenshots.

### Notes
Storage and backup change: adversarial review. Checklist items that don't apply (money
overflow, Room) are named in the pull request.

The owner dropped the criterion "The catalog's ink control can show a hue" on
2026-11-06: the catalog was removed in #245.
