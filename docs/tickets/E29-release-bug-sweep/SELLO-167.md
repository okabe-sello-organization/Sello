# SELLO-167 — An unknown outcome shows in the design's amber warning

- **Type:** Task
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-151, SELLO-152, SELLO-153, SELLO-159
- **Gate:** G1

### Outcome
Every entry screen draws "Aún no sabemos si se guardó…" in the v2 warning tone, not error
red, and the goal movement's past-date refusal has its own wording.

### Acceptance criteria
- `FieldMessage` gains the warning tone of the v2 entry design and the Anotar varios
  mock (`docs/design/v2/varios.html`, `save-msg warn`); every single and bulk entry
  screen uses it for the unknown outcome. Screenshot baselines updated; before and
  after in the pull request.
- A goal movement's `LineInvalidDate` refusal no longer uses the future-date wording.
- Closes #289.

### Notes
Review: lighter, with screenshots.
