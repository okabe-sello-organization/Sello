# SELLO-E34 — Spending patterns

- **Status:** Backlog
- **Increment:** Post-MVP
- **Goal:** The person sees where small purchases add up, which expenses repeat inside a category, and how the whole year went.
- **Exit:** Resumen has the Gastos hormiga slip with its line in Configuración, category detail has Lo que más se repite, and Tu año opens from Resumen with its records, as in the approved mock.

## Decisions

**Owner direction (2026-11-06):** chosen from a list of feature ideas for regular use:
Gastos hormiga, what repeats inside a category, and a year view.

- The Gastos hormiga line is the person's choice: 20.000 by default, set in
  Configuración.
- Patterns are facts, not blame ([philosophy](../../design/philosophy.md): gentle,
  supported by history). They add no new warning, score or savings target.

**Planner's calls (2026-11-06), open to the owner:** the line ranges from 1.000 to
100.000, is the same in every month, and travels in backups as a portable preference.
Fixed payments count as hormiga when they are small. The slip shows once 5 small
expenses exist. A repeated note needs 2 uses in the month. Tu año opens from Mes a mes,
compares the same days of the year before, and draws its records only from finished
months. The [mock](attachments/patterns-mock.html) shows them and needs the owner's
approval before building. Build after E32 so the screens use `StatusSlip` and the shared
parts.
