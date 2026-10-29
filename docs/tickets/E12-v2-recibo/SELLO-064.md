# SELLO-064 — Restyle the month bar and add the past-month ribbon

- **Type:** Story
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-015, SELLO-068, SELLO-004
- **Gate:** G2

### Outcome
The month bar reads **Octubre** with a smaller **2026**, and any past month is
clearly signalled with a one-tap way back.

### Deliverables
The selected-month behaviour behind the SELLO-068 frame: the ribbon shown under the
month bar on every screen that uses the shared month while a past month is selected,
and **Volver a** selecting the current month.

### Acceptance criteria
- The month button shows the month name large and the year small; at large font the
  year may hide visually but stays in the spoken label.
- While the selected month is not the current one, the ribbon shows **Estás viendo
  septiembre** and **Volver a octubre**; one tap selects the current month.
- The ribbon survives rotation and process death with the selected month, and
  disappears in the current month.
- Back behaviour and the month picker are unchanged.

### Tests
Updated `MonthSwitcherExample`; shell tests for ribbon show/hide and "Volver a";
restoration test; G2.

### Working checkpoint
Choosing September in the month picker shows the ribbon on Recibo; tapping
**Volver a octubre** returns to October.

### Context and starting points
Shared month: `navigation/MonthSession.kt`, `ShellViewModel.kt`; frame:
`SelloScaffold.kt`, `MonthSwitcher` in `designsystem/component/Navigation.kt`;
[app shell guide](../../development/app-shell.md). The ribbon component itself is
SELLO-068; this ticket owns when it shows and what it does.

### Implementation plan
1. Show the SELLO-068 ribbon from the shell when the selected month differs from
   the current one, wired to the existing select-current-month action.
2. Check the SELLO-068 month bar keeps the spoken label and opens the same picker.

### Concrete cases and pitfalls
- The current month comes from the financial clock; a month change at midnight
  must update the ribbon without a restart.
- Resumen and income history share the shell, so they get the ribbon too; that is
  intended by v2.

### Verification recipe
Catalog and shell tests, a manual check of month change and restart, then
`./scripts/verify-ticket SELLO-064 --retain`.
