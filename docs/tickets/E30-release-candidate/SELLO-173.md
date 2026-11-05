# SELLO-173 — Small slips: accented names sort in place, Solo este is asked once, months read alike

- **Type:** Task
- **Priority:** P2
- **Status:** Done
- **Depends on:** None
- **Gate:** G2

### Outcome
Expenses sorted by name keep accented and Ñ notes in alphabetical place. Choosing Solo
este is never asked again after the carryover list. Month abbreviations read the same
on every screen.

### Acceptance criteria
- "Nombre" order follows the folding search already uses: "Álbum" sorts between "Agua"
  and "Bus", "Ñame" sorts as "name" (between "Mesa" and "Nube", never after "Zapato"),
  and "ñame" sits next to "Ñame". Tested on real Room, because SQLite's own case folding
  is what fails.
- Editing a schedule-made expense in a closed month of a carryover chain: after
  choosing Solo este and confirming the later-months list, the expense saves without
  asking Solo este or Este y los siguientes again. A red-first JVM test.
- The Resumen month axis and the category history's "Mes a mes" use the same
  abbreviations as the rest of the app (the `strings.xml` month array), checked on a
  new emulator before the change. If the device already matches, record that in the
  pull request and leave the code alone.

### Notes
Audit findings S6, P4 and U2 (2026-11-04): `ExpenseDao.kt:27-29` (order the name branch
by the already folded `search_key`; no schema change), `ExpenseEntryViewModel.kt:745,
768, 647-660`, `ResumenRenderModel.kt:209, 232`, `CategoryInsightSections.kt:361-362`,
`strings.xml:517`. Review: adversarial for the query, lighter for the rest; the
pull request takes the adversarial one.
