# SELLO-104 — Cover recurrence in the sandbox

- **Type:** Task
- **Priority:** P1
- **Status:** Done
- **Depends on:** SELLO-098, SELLO-004
- **Gate:** G2

### Outcome
Sandbox scenarios exercise schedules, catch-up after time travel, pause and edits.

### Deliverables
Scenarios with financial-clock jumps and checks of recorded occurrences and reservations. `Scenario.kt`, the [sandbox guide](../../../development/sandbox.md) and the [scenario corpus](../../../testing/sandbox-scenarios.md) no longer say recurrence does not exist.

### Acceptance criteria
- Scenarios use the real catch-up path.
- None ships in the release build.

### Tests
Scenario corpus tests; G2.

### Working checkpoint
Each recurrence scenario reaches its check.

### Context and starting points
ARCHITECTURE §9.

### Implementation plan
1. Scenarios.
2. Checks.
3. Tests.

### Concrete cases and pitfalls
- Keep clocks distinct.
- The corpus's "Recurrence is not covered" list asks for WorkManager worker tests; ADR 0017 has no background work, so the scenarios drive the catch-up that runs on opening instead.

### Verification recipe
Focused tests, then `./scripts/verify-ticket SELLO-104 --retain`.

### Execution progress

- **Part 1 (2026-10-25):** nine *Programados* scenarios on the real command and catch-up
  paths, each reaching its check: `semana-fuera@1` (RE10), `cafe-diario@1` (RE11, batches
  of 50), `dia-31@1` (RE01, RE09), `quincena@1` (RE05–RE07), `pausa@1` (RE21–RE22),
  `cambio-desde-manana@1` (RE17–RE20), `archivo-pausa@1` (RE23), `borrar-programado@1`
  (RE24) and `ocurrencia-borrada@1` (RE15–RE16). New steps save, change, pause, resume and
  delete schedules through the fenced commands; **Ponerse al día** runs the app's own
  `CatchUpRunner` and reports its batches (`CatchUpLog`). Checks read entries by origin and
  each schedule's checkpoint and pause. `Scenario.kt`, the sandbox guide and the corpus no
  longer say recurrence does not exist.
- **Part 2 (2026-10-25):** `pagos-previstos@1` checks Recibo's reservations on the real
  snapshot through three new figures, `separado` (`Q`), `margen` (`M`) and `por día`:
  RE25 Al día, RE27 Sin margen, RE28 and RE29 Revisar, and RE26 on the 28th, when
  catch-up records the rent and the headline stays. The notice is checked by SELLO-103's
  `CatchUpNoticeJourneyTest`, which drives `semana-fuera@1` through Recibo's bar and list.
- **Done (2026-10-25):** gate and device evidence in [evidence.md](evidence.md).
