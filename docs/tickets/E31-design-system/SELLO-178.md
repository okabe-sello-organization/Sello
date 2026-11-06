# SELLO-178 — Screenshots cover every screen and state the pages list as gaps

- **Type:** Task
- **Priority:** P1
- **Status:** Done
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
  correction; Todos los gastos' loading and delete refused; category deletion's later
  steps, the past-month correction slip and detail's past month; the replace sheet's
  busy and unknown outcome; the schedule editor's restored draft; Recibo's finished
  month, reserved footnote, Revisar and archived section; Resumen's closed month,
  selected column and suggestion; Lo que Sello anotó's states; start-up's preview and
  restoring.
- The state tables' Screenshot column names the baseline of every state that has one.
- Loading and state captures are drawn in their destination's own frame (the
  ShellScreen arguments each destination passes), so the 32-colour check sees a real
  screen.
- Exception to the full 412x891 capture: 19 stems are cropped to their content, as the
  older tests of the same screens already were (category-expenses-*, income-history-*,
  upcoming-*, carry-catch-up); shell-rail-beside and shell-side-panel set their own
  landscape and tablet windows.
- Not capturable on the JVM, said on the pages: system bars, the loading window at
  start-up, the compact title chip (only at widths Sello does not support).
- Second pull request: 200 new baselines (100 states, light and dark) for what the
  first left and every table row still without one: Varios movimientos, the notice bar
  over Recibo, the correction question's undo, changed and busy forms, the bulk carry
  correction, category detail, deletion and editor states, the replace sheet, Recibo's
  finished month, reserved, Revisar, archived and reached-goal row, Resumen's closed
  month, selected columns, carry and suggestion, Lo que Sello anotó, start-up's preview
  and restoring, and Recibo and Resumen in two panes (landscape, as shell-rail-beside).
  No existing baseline changed. Every state table names a baseline; a restored draft
  drawn as its filled form points to that capture. The Gaps boxes keep only what cannot
  be captured and notes that are not about screenshots. Found on the way: #394.

