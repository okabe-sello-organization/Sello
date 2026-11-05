# SELLO-179 — The new pages are the only design reference

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-177
- **Gate:** G1

### Outcome
Agents and the owner find the design in one place, the pages under
`docs/design/system/`, with no older reference left to contradict them.

### Acceptance criteria
- ADR 0026 records the new pages as the design source and supersedes ADR 0013.
- `docs/design/v2/`, `docs/design/sello-spec.html` and `docs/design/screen-briefs/` are
  deleted; `docs/design/philosophy.md` stays.
- Open questions from the screen briefs move to the *Open implementation decisions* of
  `docs/design/philosophy.md`.
- Living docs link the new pages instead of the deleted ones.
- Links to deleted files in Done tickets and ADRs become GitHub permalinks at the last
  commit that had the file.
- Code comments that cite v2 point at the new pages.
- The AGENTS.md link change is a separate pull request that the owner merges.
