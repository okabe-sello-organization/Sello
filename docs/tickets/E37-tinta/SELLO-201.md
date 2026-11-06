# SELLO-201 — Any hue gives a palette as readable as Cobalto

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
`:design-system` can build a full light and dark palette from one hue, so Tuya and
Sistema can colour every part of the app without losing contrast.

### Acceptance criteria
- From a hue of 0 to 359 the theme builds a light and a dark `SelloColors` through
  `lightPalette` / `darkPalette`. The brand family, desk, paper, ink, outline, rule,
  bands and past-bar roles turn to the hue; gain, loss, warn and the fixed investment
  inks don't.
- At Cobalto's hue the generator returns Cobalto's palette exactly, checked against
  hex literals.
- At every hue from 0 to 359, in light and dark, every contrast pair that
  `SelloPaletteTest` checks meets its need, and the entry bands stay distinct.
- Each hue has a Spanish colour name for labels and TalkBack (Rosa, Rojo, Naranja, Ámbar,
  Amarillo, Lima, Verde, Turquesa, Petróleo, Azul, Añil, Violeta, Morado, Fucsia, as in
  the mock), and the theme says whether a hue is red (0–50°) or green (122–196°).
- Pure Kotlin, no new dependency; no screen changes yet.
- The foundations page's dynamic-colours box describes the generator.

### Notes
The algorithm and its parameters are prototyped in the epic's
[mock](attachments/tinta-mock.html) (`gen.js`, inlined): keep each role's luminance,
turn the OKLCH hue by the role's offset from Cobalto's brand hue, reduce chroma until
the colour fits sRGB. Rounding to 8 bits can move a ratio slightly; nudge, never weaken
a test. Stored hues are OKLCH degrees.

Generated bands differ but can look alike: near 160° the expense band nearly matches
the goal band, and near 25° the brand nearly matches loss. The owner chose no blocked
hue, so the red-and-green note in SELLO-202 and SELLO-203 is the only safeguard.
