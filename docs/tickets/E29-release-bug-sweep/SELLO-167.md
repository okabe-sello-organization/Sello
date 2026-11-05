# SELLO-167 — An unknown outcome shows in the design's amber warning

- **Type:** Task
- **Priority:** P2
- **Status:** Done
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
- No save or delete outcome is titled with the load failure's "No se pudo cargar": Editar
  meta's save errors follow the v2 entry pattern (the message, and **Comprobar si se
  guardó** in the button's place for an unknown outcome), and the full expense list's
  delete-problem slips get a delete-specific title (orchestrator ruling, #332).
- Entry and movement forms' section headings follow the approved v2 design (uppercase,
  `--soft` ink, as `docs/design/v2/sello.css` `.entry-mid h3` draws them) on every entry
  and movement form, in all four themes (orchestrator ruling, #349).
- One rule app-wide: every unknown-outcome message ("Aún no sabemos / No sabemos si se
  guardó/borró…") uses the warning tone wherever it is drawn in an error tone, and an
  unknown delete on Editar meta or Editar inversión says so in delete words (orchestrator
  ruling on the review of #358).
- Closes #289, #332 and #349.

### Notes
Review: lighter, with screenshots.
