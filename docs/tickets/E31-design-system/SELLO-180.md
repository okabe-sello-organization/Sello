# SELLO-180 — The findings page shows where components are misused, copied or hard-coded

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-177
- **Gate:** G0

### Outcome
The owner opens one findings page that documents the app as built: where a component
is used against its meaning, where a shared component is rebuilt by hand, and where a
value is hard-coded instead of a token, with what dynamic colours would need. Nothing
on it asks for a decision.

### Acceptance criteria
- The findings page has three sections: misuse (one card per component: its meaning
  with evidence, then each place that contradicts it, sure ones first, unsure ones
  marked with their doubt), copies (one card per shared component, each copy with how
  it differs, drifted copies highlighted) and hard-coded values (a dynamic-colours box,
  colours as a table with the risky ones first, then type, shape, spacing and alpha).
- Every place gives `path:line`, its feature page where there is one and a screenshot
  when one shows it; IDs run M01…, C01…, H01… in page order; a count summary sits at
  the top.
- No choices, saved answers or copy buttons are left on the page.
- Feature pages link the new IDs instead of F01–F90, drop wording that framed
  differences as choices to make, and list the findings that touch them.
- Every `path:line` points at an existing line, every screenshot exists and every
  `src` and `href` on the pages resolves.

### Notes
- Supersedes SELLO-177's "choice per finding" criterion, which stays in that ticket as
  history (owner direction of 2026-11-05, later, in the epic README).
- Paths and lines are at main `0bf22f5e`.
