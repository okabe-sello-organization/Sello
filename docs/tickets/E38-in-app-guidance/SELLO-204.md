# SELLO-204 — Guidance parts every screen can use

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
`:design-system` offers the four guidance parts the rest of the epic uses, drawn as in
the [mock](attachments/guidance-mock.html), so every screen explains itself the same way.

### Acceptance criteria
- **Card help (?)**: a solid soft disc in `paperDim` with a dark "?" glyph and no ring,
  placed right after a card title. Its touch target is at least
  `SelloTheme.spacing.minTouchTarget`; TalkBack reads it as a button named by the caller
  (for example "Cómo leer este resumen").
- **Help term**: text with a dotted underline in the outline grey that opens an
  explanation when tapped. TalkBack reads the word, then "Qué significa", as one button.
- **Help sheet**: a bottom sheet with a title, one or more short entries (a bold term and
  its plain explanation), an optional worked example on `paperAlt`, and an optional link
  "Ver más en la Guía de Sello". It follows the existing sheet patterns for dismissing,
  back and state restore.
- **Nudge row**: a quiet row on `paper` with an icon, one or two lines of text, and any
  of: a ✕ to close it, a quiet text link on the left, and a small tonal pill
  (`brandSoft` / `onBrandSoft`) on the right. With no pill it matches the "¿Dudas?" row;
  with a pill it matches the backup reminder (option B).
- All four use theme tokens only, follow every ink (Cobalto, Violeta, Sistema, Tuya) in
  light and dark, respect reduced motion, and keep one layout at every font size.
- Screenshot tests cover each part in light and dark; the [design system](../../design/system/components.html)
  pages describe them with real screenshots.

### Notes
- Reuse what fits: the existing sheet, `StatusSlip` styling and the tonal pill already
  used by "+ Nueva categoría" on Recibo. Check `docs/design/system/` before building.
- The parts are stateless: callers own whether a nudge shows and what a sheet says.
