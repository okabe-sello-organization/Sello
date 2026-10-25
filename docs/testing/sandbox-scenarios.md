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
| `semana-fuera@1` | 12 | 2026-09-26 · Bogotá | Rent on the 5th, a weekly bus; last catch-up 3 Oct, open on 24 Oct | 5 Oct rent, 10/17/24 Oct bus, 1.010.000, each on its date; checkpoints 5 and 24 Oct (RE10); Oct spent 1.030.000 (by hand: + 3 Oct bus) | `RecurrenceScenarioRunnerTest`, corpus |
| `cafe-diario@1` | 6 | 2026-07-31 · Bogotá | A café every day, handled 31 Jul, open on 24 Oct | 85 occurrences 1 Aug – 24 Oct, 425.000; batches 50 (1 Aug – 19 Sep) and 35 (20 Sep – 24 Oct) shown by the catch-up step (RE11); Aug/Sep/Oct spent 155.000/150.000/120.000 (by hand) | `RecurrenceScenarioRunnerTest`, corpus |
| `dia-31@1` | 10 | 2026-10-22 · Bogotá | An end date, then monthly on the 31st | 5 Nov and 5 Dec, nothing after (RE09); 31 Jan, 28 Feb, 31 Mar, 30 Apr, 31 May 2027 (RE01) | corpus |
| `quincena@1` | 10 | 2026-10-01 · Bogotá | Salary twice a month, every 15 days, and the 30th and 31st | 15/30 Oct … 15 Feb, 28 Feb slot 2 (RE05); 15 Oct … 13 Jan every 15 days (RE07); two on 28 Feb 2027, slots 1 and 2 (RE06); Oct income 2.000.000 (by hand) | corpus |
| `pausa@1` | 22 | 2026-10-01 · Bogotá | Two rents paused on 3 Oct, resumed on 5 and 6 Dec | 5 Oct and 5 Nov skipped, 5 Dec recorded (RE21); 5 Oct, 5 Nov, 5 Dec skipped, next 5 Jan (RE22) | corpus |
| `cambio-desde-manana@1` | 21 | 2026-10-01 · Bogotá | Changes on 10 and 20 Oct; a new rule from today is refused (`StartTooEarly`) | 950.000 then 1.000.000 (RE17); 5 → 25 Oct (RE18); 25 → 5 Nov, 5 Oct never (RE19); every 15 → every 10 days, 30 Oct never (RE20) | corpus |
| `archivo-pausa@1` | 19 | 2026-10-01 · Bogotá | Archive pauses the gym; resuming while archived is refused (`CategoryArchived`) | Paused from 12 Oct, 15 Oct not recorded; unarchived, still paused; resumed, 15 Nov (RE23) | corpus |
| `borrar-programado@1` | 11 | 2026-10-01 · Bogotá | Rent schedule deleted on 10 Oct | 5 Oct stays as an expense, nothing on 5 Nov, schedule gone (RE24) | `RecurrenceScenarioRunnerTest`, corpus |
| `ocurrencia-borrada@1` | 16 | 2026-10-01 · Bogotá | The rent also entered by hand; the recorded one deleted on 6 Oct | Oct spent 1.900.000, the manual one is no occurrence (RE16); not recorded again on 7 Oct, checkpoint still 5 Oct, 5 Nov comes (RE15) | corpus |

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

## Recurrence

The *Programados* scenarios take their figures from the approved
[recurrence examples](recurrence-examples.md) and name them (RE01–RE24); amounts an
example leaves open are the scenario's own. They use only the real path:

- A schedule step (`Schedule`, `ChangeSchedule`, `PauseSchedule`, `ResumeSchedule`,
  `DeleteSchedule`) sends the app's schedule commands, fenced like every write;
  archiving and unarchiving go through the category commands, and `DeleteOccurrence`
  deletes the entry an occurrence recorded, found by its origin, through the expense
  or income commands. No step records an entry itself or writes to a table.
- Entries come only from the app's catch-up (`CatchUpRunner` over `CatchUpSchedules`),
  which ADR 0017 runs when Sello opens, when the financial day changes and after a
  save, with no background work: no WorkManager scheduler is involved, so there is
  nothing for virtual time to fail to advance. A date step moves only the financial
  clock; audit and elapsed time stay real. `CatchUp` then runs that catch-up once more
  and waits for it, and lists the batches committed since the last one. The definition
  test requires it after every date step, new schedule and resume, so Sello's own run
  never races the next step.
- A check reads the entries a schedule recorded, by their origin `(schedule, date,
  slot)`, with their dates and amounts, and the stored schedule's checkpoint and open
  pause. A manual entry is never counted (RE16). After a restore there are no receipts:
  a schedule is found by its note, unique within a scenario, and a deleted one's
  entries by their category's name and that note.

Not covered here yet: Recibo's reserved payments `Q` (RE25–RE34) arrive with
SELLO-101 and the catch-up notice (RE38–RE39, and the notice lines of RE10–RE12) with
SELLO-103; their scenario checks come with those tickets. Expected income in Plan del
mes (RE35–RE37) has no scenario check yet. Deshacer of an occurrence's deletion (RE15's
second half) is not scripted: it runs on real elapsed time, like the goals' Deshacer.
A catch-up cut after a batch or with an unknown commit (RE12, RE13) is tested in
`:data` and `:domain`, and the day change while open (RE14) by `CatchUpRunnerTest`; here
a failed or unknown batch stops the `CatchUp` step, and its next try runs catch-up
again.
