# SELLO-E39 — Tuya con cualquier color

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person can give Tuya any colour they like, see it in the important parts of Sello while they pick it, and the light theme shows exactly that colour.
- **Exit:** SELLO-214 to SELLO-216 are Done: one generator builds a palette from an exact colour; Tuya stores, backs up and restores that colour while hues saved before keep working; the Tu tinta sheet picks any colour as the mock shows; the design pages show the result.

## Decisions

**Owner direction (2026-11-12):** given in chat on the orchestrator's options and mocks;
the approved design is the [mock](attachments/tuya-colour-mock.html). It replaces E37's
"the person picks a hue, not any colour" for Tuya only
([ADR 0028](../../decisions/0028-tuya-honours-the-exact-colour.md)).

- **Complete control.** The person picks any colour and Sello **honours it exactly**.
  Readability is their call: white text on yellow is allowed, nothing is blocked or
  corrected, and there is no contrast warning.
- **Light first.** The colour picked is the light theme's band and buttons, exactly, with
  white text. Sello makes the dark theme from it.
- **The most important examples show while picking**, so the person checks the result
  themselves: band, Anotar, Restante with AL DÍA, a category over its limit, a goal band,
  a selected chip, in light and dark.
- **Red and green: the note and the examples only** (options 1 and 2). The note shows when
  the colour is close to the excess red or the goal green; the meaning colours (gain,
  loss, warning, goal band) never change.
- **The picker** is the first mock's sheet (previews, colour square, colour bar) with
  **option E** below the bar: the code in the title, editable; **Antes / Ahora** tiles
  with white "Aa"; one line holding a hint or the note.
- **TalkBack is out of scope** for this feature for now.
- **Backups store the new colour** (a new backup version).
- **People already using Tuya keep their look**: their stored hue is not converted and
  still draws as today. Tu tinta starts on the colour that hue gives; saving stores a
  colour.
- **Sistema stays as it is** (the phone's hue through E37's generator).
- Orchestrated run ([orchestration](../../development/orchestration.md)): the owner made
  the orchestrator responsible for the epic and approved subagents and adversarial
  reviewers; merges are delegated except a release.

**Orchestrator's calls (2026-11-12), open to the owner:**

- The new generator lives in `:design-system` beside E37's, which stays for Sistema and
  for hues saved before. Its parameters are the mock's prototype (`palette`,
  `nearMeaning`, `colourName` in the mock's script).
- A colour is stored as 24-bit sRGB and written to backups as `"#RRGGBB"`. A colour that
  doesn't parse is rejected, never repaired.
- Greys are named Gris, Negro or Blanco; every other colour keeps E37's hue names.
- **Antes** is the ink in use when the sheet opens, by its name; Tu tinta opens on Tuya's
  kept colour, else the colour of Tuya's kept hue, else the ink in use.
- The note's two lines replace E37's single red-and-green line in Tu tinta; Sistema's
  caption keeps E37's line.
