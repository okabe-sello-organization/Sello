# SELLO-E37 — Tinta propia y del sistema

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** The person can give Sello any colour they like, or let it follow their phone's colour, and the app still reads as clearly as Cobalto.
- **Exit:** SELLO-201 to SELLO-203 are Done: Apariencia › Tinta offers Cobalto, Violeta, Sistema (Android 12 and later) and Tuya; every palette comes from one generator that keeps Cobalto's contrast at every hue; backups carry the choice; the design pages show the result.

## Decisions

**Owner direction (2026-11-05 and 2026-11-06):** given in chat on the orchestrator's
proposal and [mock](attachments/tinta-mock.html) (serve the repository and open it; the
hue, theme and wallpaper controls at the top drive every frame).

- The mock is approved: two more swatches after Cobalto and Violeta, **Sistema** (the
  phone's colour, Android 12 and later) and **Tuya** (a rainbow + until set); Tuya opens
  the **Tu tinta** sheet with a live preview in both themes, the colour's name, a hue
  slider and Cancelar / Usar esta tinta.
- The person picks a **hue**, not any colour: Sello sets each colour's lightness.
- **No hue is blocked.** On red or green hues the sheet (and Sistema's caption) adds one
  line: "El rojo y el verde también marcan excesos y metas." Sistema uses the phone's
  hue as it is.
- One **generator** builds every new palette from a hue: Sello's own shades, so Sistema
  does not copy Material You's colours. Cobalto and Violeta keep their hand-made palettes.
  Gain, loss and warn never follow the ink.
- **Sistema is part of this epic.** Android 11 has no system colour, so Sistema is not
  offered there.
- **No catalog criterion (2026-11-06):** SELLO-202's catalog criterion is dropped; the
  catalog app was removed in #245.
- **Backups change format:** a backup made with Tuya or Sistema needs a new backup
  version, so older Sello versions refuse to restore it.
- Orchestrated run ([orchestration](../../development/orchestration.md)): subagents and
  separate review agents are approved, and the owner delegated every merge except a
  release.

**Orchestrator's calls (2026-11-06), open to the owner:**

- The generator is Sello's own code in `:design-system`; no colour library is added.
  Each colour keeps the luminance of Cobalto's colour for the same role and only turns
  its hue (OKLCH), with chroma reduced where sRGB can't hold it, so every contrast ratio
  stays Cobalto's. The mock's `gen.js` is a working prototype: at Cobalto's hue it gives
  Cobalto exactly, and swept at every degree each contrast pair stays within 1% of
  Cobalto's.
- The stored hue is a whole OKLCH degree, 0 to 359. Red is 0–50° and green 122–196° for
  the note.
- The window before the first frame stays Cobalto's desk: it is drawn before Sello can
  read the ink ([foundations](../../design/system/foundations.html#dynamic-colours)).
- E33–E36 and SELLO-192 to SELLO-200 are taken by pull request #410, so this epic is E37.
