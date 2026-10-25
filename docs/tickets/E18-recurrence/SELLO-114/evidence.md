# SELLO-114 — Design the schedule screens, the upcoming-payments list and the catch-up notice

- **Revision:** the gate tested commit `2cca36d` on `sello-114-recurrence-design`, cut from
  `main` at `eb0478b` (SELLO-102 done, #129); the only uncommitted file was the retained
  report. The status, this file and the screenshots are added after that commit.
- **Requirement mapping:** every screen shows default, empty, loading, failed-read,
  large-text and dark frames with figures from SELLO-096's examples (RE10, RE21, RE23,
  RE24, RE29, RE30, RE39) → `fijos.html` sections 1–6; where schedules are reached from
  and how one is created → section 7 and Se repite in the entry (section 1); editing a
  recorded entry → section 4, Solo este / Este y los siguientes; reuse of the gap list's
  parts and what is new → `components.html` (Schedule row New, Se repite row Extend, Rule
  sheet New, Catch-up notice Extend, Choice slip Extend) and the README section; the owner
  locks each page, rejected options recorded → locked 2026-10-25 by owner delegation, with
  the rejected options in the README.
- **Red / Green:** not applicable: design pages only; no behaviour changed and no test
  was added.
- **Tests:** none changed; the figures were worked out by hand from SELLO-096's examples
  (RE29: 1.080.000 − 1.000.000 = 80.000 over, 280.000 short with 200.000 reserved;
  48.750 por día from 390.000 over 8 days).
- **Checks:** the page renders all 42 frames with no missing icon (headless Chromium);
  screenshots in `artifacts/`. Fixed during review: the title colour in light mode, plan
  lines wrapping at 200 %, the Listo dock icon, frames scrolled to show Pausar and the
  danger card.
- **Gate results:** `./scripts/verify-ticket SELLO-114 --retain` passed G1 on `2cca36d`:
  969 host tests, zero failures; tickets and link checks pass. No code changed, so no
  device suite and no red/green test.
- **Quality run:** run 20261025T060129Z-6ba084e0; SELLO-114 G1 passed; HEAD 2cca36d, inputs sha256 f9f2114cba39, changes sha256 aaf2c44b05af; report docs/tickets/E18-recurrence/SELLO-114/quality-report.json sha256 6ef9bde4fd8a5980fc64a5eabadb4b584c31ebfb50c96a2cb94331cd2876baf7
- **Device / Artifact:** none: documentation only; page screenshots in `artifacts/`.
- **Review:** executor self-review; owner answered the design questions on 2026-10-25 and
  delegated the lock; owner review by merging.
