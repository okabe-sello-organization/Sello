# Sandbox scenario corpus

Every scenario the debug sandbox replays, with its stable ID, version, seed, steps and
expected figures. The same definitions (`app/src/debug/.../devtools/Scenarios.kt`) run
live in **Sello Herramientas** and in `TemporalScenarioCorpusTest`, and each ends with a
`Verify` step that reads the real Recibo/Resumen snapshots and, for goals, the real goal
board. Expected values are copied from the approved [financial examples](mvp-financial-examples.md)
or [goal examples](goal-examples.md) and name their ID;
where a scenario has no approved example, the value is derived by hand from the steps
and says so. None is computed by the code being checked.

Live testing supplements the unit and device tests; it never replaces them. Synthetic
data only: no scenario needs personal data or a local setting.

## Replay

1. Debug build → **Sello Herramientas** → **Escenarios** → **Cargar** on the scenario,
   then confirm. The sandbox is erased and restarts at the scenario's start date and zone.
2. **Paso** runs one step; **Reproducir** runs one every 2 s. A `Verify` step lists each
   figure as `✓`/`✗ figure month: esperado X, real Y (source)` and stops on a mismatch.
3. **From a checkpoint:** progress is saved after each confirmed step. Close the app at
   any point and open the tools again; **Paso** resumes at the same step with the same
   operation, so nothing is written twice.
4. **Exportar diagnóstico** shares the session, step and operations, for a report.

## Scenarios

| ID | Steps | Start · zone | What it covers | Expected (source) | Test |
| --- | --- | --- | --- | --- | --- |
| `g01@2` | 8 | 2026-10-01 · Bogotá | Three expenses and an income | Oct spent 30.000, remaining 70.000, net 170.000, unassigned 100.000 (G01) | `ScenarioReplayTest`, corpus |
| `detalle-categoria@1` | 13 | 2026-08-01 · Bogotá | Category detail, a known empty month, weekly buckets and latest expenses | Oct spent/remaining 100.000; weekly allowance 70.000; average expense 20.000; Aug/Sep historical average 10.000 (ADR 0015) | `CategoryDetailJourneyTest`, corpus |
| `fin-de-mes@2` | 13 | 2026-10-30 · Bogotá | Over-limit category, no-limit category, 31 Oct → 1 Nov, 31 Dec → 1 Jan | Oct spent 85.000, remaining −35.000; Nov 40.000; Dec 38.000; Jan 2027 42.000 (by hand, B01/B04/H05 rules) | corpus |
| `seis-meses@1` | 67 | 2026-05-01 · Bogotá, seed 20261001 | Graph growth, May–Oct | Each month's history total equals the sum of that month's expense steps; six expenses a month | `ScenarioReplayTest` |
| `bordes@1` | 10 | 2026-10-10 · Bogotá | Double submit, stale edit, undo replaced by a later deletion, amount over the maximum | Already saved; `VersionConflict`; `UndoExpired`; `AboveMaximum` before any command; no expense left | `ReceiptRecoveryDiagnosticJourneyTest`, corpus |
| `bisiesto@1` | 7 | 2028-02-28 · Bogotá | 28 Feb → 29 Feb → 1 Mar | Feb spent 30.000, remaining 70.000; Mar spent 5.000, remaining 95.000 (by hand; not an approved example) | corpus |
| `meses-saltados@1` | 5 | 2026-10-05 · Bogotá | Default in October, November never opened, December default changed, backdated November expense | Nov limit 100.000, Dec limit 120.000 (H03); Nov spent 10.000, remaining 90.000 (H04) | corpus |
| `limite-pasado@1` | 8 | 2026-09-10 · Bogotá | Unchanged default carries; new October default; September correction | Sep remaining 80.000, Oct limit 100.000 (H05); Sep 80.000, Oct 120.000 (H01); Sep 70.000, Oct 120.000 (H02) | corpus |
| `archivada@1` | 7 | 2026-10-05 · Bogotá | Archive after spending; no new entries; next month | Oct remaining 80.000, limit 100.000; new expense `CategoryArchived` (AR01); Nov no budget, Oct unchanged (AR02) | corpus |
| `ingresos@1` | 8 | 2026-10-05 · Bogotá | Income against the budget | Oct net 120.000, remaining 70.000, unassigned 50.000 (I01); Nov net 20.000, remaining 70.000, unassigned −50.000, not clamped (I02) | corpus |
| `metas-sellos@1` | 16 | 2026-10-23 · Bogotá | Stamps from aportes, a double tap, the budget beside goals | 749.999: 24 %, no stamp (MT02); 750.000: 25 % earned (MT01); 1.600.000: 53 %, two stamps (MT04), repeat writes nothing (MT11); 37 % (MT03); 3.060.000: 102 %, Meta waiting (MT05); Oct remaining 400.000 (MT06) | corpus |
| `metas-retiros@1` | 9 | 2026-10-23 · Bogotá | Withdrawals and faded stamps | 1.710.001 refused `AboveSaved` (MT08); 1.310.000: 43 %, 50 % kept (MT07); 1.510.000: 50 %, lit again (MT10); 0: 25 % and 50 % kept (MT09) | corpus |
| `metas-objetivo@1` | 10 | 2026-10-23 · Bogotá | Target changes from 1.710.000 of 3.000.000 | 4.000.000: 42 %, 50 % kept (MT17); 2.000.000: 85 %, 75 % earned (MT18); 1.700.000: 100 %, Meta waiting (MT19) | corpus |
| `metas-lograda@1` | 15 | 2026-10-23 · Bogotá | Recibo's goal, Marcar como lograda and Deshacer | Best completion on Recibo (MT28), then the choice (MT29); Deshacer back to 102 % waiting (MT24); marked at 37 % with four stamps (MT26), Recibo falls back (MT30); completed, nothing on Recibo (MT23, rule 12) | corpus |
| `metas-tarde@1` | 5 | 2026-10-23 · Bogotá | Deshacer after its window | Marcar with a zero window, then Deshacer refused `UndoExpired`; the goal stays lograda at 100 % with four stamps (MT25) | corpus |

Restart and undo under financial time are covered outside the corpus:
`TimeControlsJourneyTest` steps the financial day a year forward with an undo offer
open, and undo still ends six real seconds after the deletion.

For `detalle-categoria@1`, finish replaying, open Alimentación and change its limit
from 200.000 to 500.000. The current October point in **Un mes normal aquí** must
immediately show 500.000; August and September stay at 200.000, and the average is
still 10.000 from two completed months. Advance to November 1: October closes with
its 500.000 limit and November starts at 500.000. The real editor/Room/observer path
is exercised by `CategoryDetailJourneyTest.editingTheCurrentLimitRefreshesItsChartPointBeforeRollover`.

`TemporalScenarioCorpusTest` runs every scenario fresh, then again interrupted after
a committed step whose checkpoint was lost, and requires the same receipts, expenses,
incomes, categories, limits, goals, goal movements and Recibo's choice from both runs,
and every `Verify` to match.

Goal checks read the stamps as the goal's page draws them: `ganado`, `guardado` (earned,
no longer reached), `vacío` and `esperando` (the Meta reached, not yet marked). Deshacer
runs on real elapsed time, as in the app: `metas-lograda` undoes right after marking, late
in the scenario, so the corpus's cut and resume never falls between them. `metas-tarde`
marks with a zero Deshacer window, so its Deshacer is too late (MT25) without any waiting.

## Adding a scenario

1. Add it to `Scenarios.all` with a new `id` and version 1, a fixed seed and zone, and
   only steps the app's commands accept. Script category versions explicitly; the
   definition test checks them against the changes before them.
2. End it with a `Verify` of the figures it is about. Copy each value from an approved
   example and name it, or derive it by hand and write the derivation in `source`.
3. Add its row here. Changing the steps or seed of an existing scenario needs a new
   version, because each step's operation is derived from ID, version, seed and index.
4. A feature that adds a financial capability adds its live scenario here (architecture §9).

## Recurrence is not covered

The app has no recurring entries, and no step pretends to be one. `meses-saltados`
tests history across months nobody opened; it does **not** show that scheduling works.
When recurrence ships it must add, before any scenario claims it:

- the production "run due work" and bounded catch-up use case, with uniqueness and
  checkpoints, reporting created, skipped, failed and pending results;
- sandbox controls that call that use case, not a debug-only engine;
- separate WorkManager worker and device tests, because virtual financial time does
  not advance Android's scheduler.

No scheduling library or engine is added to the MVP to preview this.
