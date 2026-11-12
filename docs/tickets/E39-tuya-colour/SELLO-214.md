# SELLO-214 — Any exact colour gives a whole palette

- **Type:** Task
- **Priority:** P1
- **Status:** Ready
- **Depends on:** None
- **Gate:** G1

### Outcome
`:design-system` can build Sello's light and dark palettes from one exact colour, so
Tuya can show the person's colour as it is and the rest of the app stays readable.

### Acceptance criteria
- From any sRGB colour the theme builds a light and a dark `SelloColors` through
  `lightPalette` / `darkPalette`, as the [mock](attachments/tuya-colour-mock.html)'s
  `palette` does:
  - light: brand and band are the colour exactly, with white text on them;
  - dark: the band is the colour a little deeper with white text; the brand is a lighter
    version with dark text on it;
  - every other ink role (brand soft, desk, paper, ink, outline, rule, past bars, …)
    takes the colour's OKLCH hue at Cobalto's lightness for that role, with less chroma
    for a muted colour: a grey gives grey roles;
  - gain, loss, warn, the goal band and the fixed investment inks never change.
- Checked against hex literals: for `#C72842` the light brand and band are `#C72842`
  exactly, and gain, loss and warn equal Cobalto's in both themes.
- For a sweep of colours across the whole sRGB cube, in light and dark, every contrast
  pair `SelloPaletteTest` checks that involves neither the brand nor the band meets its
  need, and the dark brand's text meets 4.5:1 on it.
- A closeness rule says whether a colour is near the excess red, the goal green or
  neither, as the mock's `nearMeaning`: hand-worked cases `#C62828` and `#E53935` red;
  `#2E7D32`, `#4CAF50` and `#13704C` green; `#E06A1F`, `#FF6FAE`, `#F4C430`, `#5F6368`
  and `#1F3BE0` neither. A grey is never near.
- Every colour has a Spanish name: E37's hue names from its OKLCH hue, or Gris, Negro or
  Blanco for a grey, as the mock's `colourName`.
- E37's hue generator and its tests are unchanged. Pure Kotlin, no new dependency, no
  screen changes yet.

### Notes
Only the brand and band roles are exempt from contrast, by the owner's choice
([ADR 0028](../../decisions/0028-tuya-honours-the-exact-colour.md)); never weaken an
existing palette test. Rounding to 8 bits can move a ratio slightly: nudge the
parameters, not the test. Pick sweep steps that keep the test fast.
