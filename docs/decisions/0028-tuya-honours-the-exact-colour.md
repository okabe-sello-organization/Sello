# 0028 — Tuya honours the exact colour

**Owner:** project owner (user) · **Date:** 2026-11-12
**Status:** owner decision in the E39 epic's *Decisions*; implemented in SELLO-214 to
SELLO-216. Narrows E37's "a hue, not any colour" and ARCHITECTURE §6's contrast and
TalkBack rules for Tuya's colour picker only.

## Context

E37 let the person pick a hue and kept every contrast ratio at Cobalto's, so some hues
could never look like themselves (yellow came out olive). The owner wants complete
control over Tuya's colour.

## Decision

- Tuya takes **any sRGB colour**. The light theme's brand and band are that colour
  exactly, always with white text, even when that is hard to read. Sello blocks nothing
  and warns about nothing; Tu tinta shows the important parts of Sello in that colour so
  the person can judge.
- Every **other** role keeps its readability: text on paper, the dark theme's button text
  and the meaning colours (gain, loss, warning, goal band), which never follow the ink.
- Near the excess red or the goal green, Tu tinta adds one line saying so.
- **TalkBack support for the colour square and bar is out of scope** for now; the code
  field is a normal text field.
- Sistema, Cobalto, Violeta and Tuya hues saved before keep E37's contrast-safe
  palettes.

## Consequences

- Contrast tests exempt only Tuya's brand and band; every other palette test stays.
- A later epic may add TalkBack to the picker; nothing here prevents it.
