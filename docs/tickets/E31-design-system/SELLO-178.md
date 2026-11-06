# SELLO-178 — Screenshots cover every screen and state the pages list as gaps

- **Type:** Task
- **Priority:** P1
- **Status:** In Progress
- **Depends on:** SELLO-177
- **Gate:** G1

### Outcome
Every screen and state the design pages describe has a real screenshot, light and
dark, so no page shows a gap.

### Acceptance criteria
- Each gap listed on the design pages has a JVM screenshot test in light and dark at the
  default 412x891 dp size.
- The pages link the new screenshots and list no gaps.
- `verifyRoborazziDebug` passes for both modules.

### Progress
- First pull request: 611 new JVM baselines (four replace light-only files without a
  theme suffix), light and dark, for every screen that had
  none (Configuración, Respaldo, Nueva categoría, the Ingresos tab, Se repite's sheet,
  dialogs and confirm slips, icon sheets, date pickers, the goal detail with its band,
  the shell frames), the loading, error, empty, stale, busy and unknown states of each
  screen, and dark versions of every light-only baseline. The pages link them.
- Left for a follow-up, each listed in its page's Gaps box: Varios movimientos' states;
  the notice bar's states and the correction question's undo form; the bulk carry
  correction; category deletion's later steps, the past-month correction slip and
  detail's past month; Recibo's finished month, reserved footnote, Revisar and archived
  section; Resumen's closed month, selected column and suggestion; Lo que Sello anotó's
  states; start-up's preview and restoring.
- The state tables' Screenshot column still says "no screenshot" for states that now
  have a figure; the follow-up refreshes them.
- Not capturable on the JVM, said on the pages: system bars, the loading window at
  start-up, the compact title chip (only at widths Sello does not support).

