# 0023 — One layout at every font size

**Owner:** project owner (user) · **Date:** 2026-10-29
**Status:** owner decision of 2026-10-29. Supersedes the font scales 1.3 and 2.0 in
[ADR 0001](0001-mvp-contract.md)'s screen matrix.

## Context

Every screen had to work at 2× font. Screens switched layout at font scale 1.3 or
1.6 (stacked month bar, fewer category columns, stacked cards), and each look was
tested and kept as screenshots at font 2.0. That doubled the screen tests and
baselines and slowed every visual change, for a size the owner doesn't need now.

## Decision

- Large text is not supported. Screens use their normal layout at every font size;
  no code switches on font scale.
- Tests and screenshots run at font scale 1.0 only. Dark-mode looks are kept at 1.0.
- Layouts may still adapt to width (narrow slips stack, long amounts wrap), and those
  fallbacks stay.

## Consequences

- Fewer layout branches, screen tests and baselines.
- At large system font sizes text may clip or crowd; that is accepted until the owner
  asks for large-text support, which would be its own epic.
