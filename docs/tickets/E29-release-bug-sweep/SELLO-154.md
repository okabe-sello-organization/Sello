# SELLO-154 — A category limit saved after its month ends never lands on another month

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** None
- **Gate:** G1

### Outcome
Editar categoría saves a limit or turns carry on only for the month it shows; once that
month has ended it refuses and reads again, as Ajustar límite already does.

### Acceptance criteria
- Opened on 31 Oct showing "Vale desde Octubre 2026 en adelante", saved on 1 Nov: the
  save is refused with the month-ended message, nothing is written, and the form reads
  November. November's own limit is untouched.
- The same for turning carry on, and for a draft restored after the month changed.
- Saving within the same month behaves as today.
- Red-first JVM tests with a controlled financial clock.

### Notes
Audit findings: `CategoryManagementViewModel.kt:138-141` fixes `currentMonth` at open;
`:745-764` sends `SetDefaultLimit`, applied to the clock's month; `CarryQuestion.Enable`
at `:328` and `:394`. `LimitSheetViewModel.save()` guards with `MonthEnded`. Review: lighter.
