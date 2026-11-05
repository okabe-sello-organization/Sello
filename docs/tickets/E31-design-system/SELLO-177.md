# SELLO-177 — Design system pages describe every feature from the built app

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-176
- **Gate:** G0

### Outcome
The owner opens one set of HTML pages under `docs/design/system/` and sees how every
feature of the built app looks and why, with its real screenshots, plus every
inconsistency found, ready to decide.

### Acceptance criteria
- An index page links every other page.
- A foundations page covers the tokens the code defines: colour, type, spacing, shape
  and motion.
- A components catalog lists each shared component, its variants and where it is used.
- One page per feature names the components it uses and why, and describes each screen
  and state in detail, with the light and dark screenshots linked, not copied. Screens
  or states without a screenshot are listed as gaps.
- A findings page logs each inconsistency with evidence (files, screenshots), offers a
  choice per finding and gives a copyable answer for each choice.
- Every `src` and `href` on the pages resolves.

### Progress
- Pages written from the code by seven parallel authors (one per feature group) after
  a design-system pass; 136 raw findings plus 10 design-system candidates merged into
  90 (F01–F90) in eight themes. Unrelated defects went to issues #374–#387.
