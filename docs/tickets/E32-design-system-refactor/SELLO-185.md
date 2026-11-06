# SELLO-185 — Dialogs share one frame and one button order

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-182
- **Gate:** G2

### Outcome
ConfirmSlip, ChoiceSlip, OptionSlip and the discard slip share one dialog frame; stacked
buttons always put the action on top and Cancelar under it.

### Acceptance criteria
- Fixes M03-4 (ActionFailed inside the removal dialog), M08-2, C05, C09 and the
  hand-built stacked confirmations (Borrar datos, Restaurar, start-up).
- Re-recorded baselines; the findings page no longer lists them.
