# SELLO-E31 — Design system from the built app

- **Status:** Done
- **Increment:** Post-MVP
- **Goal:** One design reference per feature, made from the built app with real screenshots, and the app consistent where the same thing was built differently.
- **Exit:** The pages under `docs/design/system/` are the design source; the findings page documents misuse, copies and hard-coded values; the old references (v2, the v1.1 snapshot and the screen briefs) are deleted; screenshots cover every screen and state the pages list (SELLO-178).

## Decisions

**Owner direction (2026-11-05):**

- The design system is rewritten from the code as HTML pages under
  `docs/design/system/`: foundations, a components catalog and one page per feature
  (components used, why, and a detailed description of each screen and state).
- Screens are shown with the real Roborazzi screenshots (light and dark), linked, not
  copied.
- Where the app differs from v2, the app wins; no findings for v2 drift.
- Every inconsistency (same thing built twice, one component used in different ways,
  hard-coded values where a token exists, the same interaction done differently) is
  logged with evidence on one findings page; the owner decides the canonical version
  per finding; fix tickets then change the code. *(Superseded by the later owner
  direction below.)*
- Screenshots match a Samsung Galaxy S25 Ultra: 412x891 dp, drawn at mdpi.
- After the new pages land, `docs/design/v2/`, `docs/design/sello-spec.html` and
  `docs/design/screen-briefs/` are deleted; `docs/design/philosophy.md` stays. The
  AGENTS.md link change is its own pull request that the owner merges.

**Owner direction (2026-11-05, later):**

- E31 is documentation only: it documents the app as built and points out where
  components are used the wrong way. No code changes because of the findings, and
  there are no fix tickets.
- The same situation shown differently because the place differs (a save that did not
  finish shown in a sheet, next to the failed button or as a heads-up note) is not a
  finding.
- Findings are only: misuse (a component used against its own meaning, such as a
  button that always accepts used elsewhere to go back or reject); copies (a shared
  component rebuilt by hand instead of referenced); and hard-coded values where a
  token exists. Colours come first, because a future feature adds dynamic colours and
  every colour must then come from the theme.
