# Sandbox scenario corpus

Every scenario the debug sandbox replays, with its stable ID, version, seed, steps and
expected figures. The same definitions (`app/src/debug/.../devtools/Scenarios.kt`) run
live in **Sello Herramientas** and in `TemporalScenarioCorpusTest`, and each ends with a
`Verify` step that reads the real Recibo/Resumen snapshots and, for goals and investments,
the real goal and investment boards. Expected values are copied from the approved
[financial examples](mvp-financial-examples.md), [goal examples](goal-examples.md) or
[investment examples](investment-examples.md) and name their ID;
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
| `pagos-previstos@1` | 15 | 2026-10-01 · Bogotá | Hogar 1.000.000, 400.000 spent; on 22 Oct rent of 200.000 due on the 28th, then more spending; catch-up on 28 Oct | Reserved 200.000, margin 400.000, 40.000 per day (RE25); margin 0 (RE27); −50.000 (RE28); remaining −80.000, margin −280.000 (RE29); on 28 Oct nothing reserved and the margin still −280.000 (RE26) | corpus |
| `inversiones-pagina@1` | 42 | 2025-05-03 · Bogotá | The four investments of the v2 page over 18 months, the clock moving between movements; a double tap; Cripto's only value deleted; Parqueadero and CDT deleted | Fondo 6.000.000 from the 3 May 2025 value, 0,0 % (rule 5, IN15); Parqueadero +1.350.000, +7,5 % (IN02); CDT +7,5 % (IN03); Fondo −258.000, −4,3 %, since May 2025 (IN04); Ecopetrol +7,6 %, 9 movements (IN05, IN23); total 38.038.000, +5,6 %, values 12 Jun – 14 Oct (IN06); Cripto Sin valor, total unchanged (IN17); Fondo and Ecopetrol −30.000, −0,3 % (IN07) | corpus |
| `valor-arrastrado@1` | 31 | 2026-09-01 · Bogotá | A value carried forward, a retiro above it, a backdated aporte, a dividend, recording order on one day, the sign of a gain | 6.000.000 "del 1 oct", +20,0 % (IN08); 4.000.000 (IN09); 4.000.001 refused `AboveValue`, 4.000.000 accepted, V 0 (IN10); +16,0 % (IN11); +17,0 % (IN14); V stays 3.800.000 (IN12, by hand after IN14); 4.500.000 and 4.000.000 (IN13); 0,0 % (IN15); −0,0 % Pierde (IN16); a 13-digit aporte never sent (IN26) | corpus |
| `dolares@1` | 28 | 2026-10-20 · Bogotá | The page's four entered with past dates; Acciones EE. UU. in USD; new rates | IN06 again; refused `RateRequired` without a rate (IN33); USD +50,50, +5,1 % (IN27); total 42.397.838, +5,5 %, "dólar a 4.150,25 del 20 oct" (IN28); rates 0,99 and 100.000,01 never sent, tomorrow `FutureDate` (IN32); USD 10.000.000.000,00 never sent (IN34); at 4.000,00 the total 42.240.000, own return still +5,1 % (IN29); 4.010,00 the same day replaces it (IN30); 3.900,00 dated earlier joins the history only (IN31) | corpus |
| `correcciones@1` | 27 | 2026-09-01 · Bogotá | Corrections replayed before they are saved; Deshacer of a deleted movement | Only aporte refused `LastContribution` (IN22); a value before the first aporte `BeforeFirstContribution` (IN25); tomorrow `FutureDate` (IN24); deleting the aporte a retiro needs `AboveValue` (IN19); the retiro raised to 7.000.000 `AboveValue` (IN20); retiro deleted, V 6.000.000, Deshacer back to IN09 (IN21); retiro then aporte deleted, +25,0 % (IN19, by hand); retiro corrected to 1.500.000 (by hand); an investment deleted (IN23) | corpus |
| `sin-valor@1` | 9 | 2026-10-20 · Bogotá | Every value deleted, COP and USD; a retiro without a value | Retiro refused `NoValue` (IN18); both Sin valor, no total and no rate line (IN17, IN35); with a value the retiro is accepted: −200.000, −10,0 %, no rate line (IN18, rule 13, by hand) | `InvestmentSandboxGraphTest`, corpus |
| `plan-del-mes@1` | 9 | 2026-10-01 · Bogotá | Hogar 2.800.000, 2.000.000 recorded; on 22 Oct a salary of 1.000.000 expected on the 30th; catch-up on 30 Oct | Income 2.000.000, expected 1.000.000, planned 3.000.000, unassigned in the plan 200.000, recorded unassigned −800.000 (RE35); remaining 2.800.000 and 280.000 per day, unchanged (RE37); on 30 Oct income 3.000.000, expected 0, unassigned 200.000 (RE36) | corpus |

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
incomes, categories, limits, goals, goal movements, Recibo's choice, investments, their
movements and the USD rates from both runs, and every `Verify` to match. Its backup test
restores every scenario and compares the rows; backups do not carry investments until
SELLO-108, so an investment scenario's last check is not run again after the restore.

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

## Investments

The *Inversiones* scenarios take their figures from the approved
[investment examples](investment-examples.md) and name them (IN02–IN35); a figure an
example leaves open is derived by hand in its `source`. They use only the real path:

- An investment step (`Investment`, `Movement`, `EditMovement`, `DeleteMovement`,
  `UndoMovementDelete`, `DeleteInvestment`, `SetRate`) sends the app's investment
  commands, fenced, faulted and logged like every write. Amounts are minor units of the
  investment's currency: pesos, or USD cents. Movements are dated on the sandbox's day,
  or earlier with `on`; an amount or rate outside its bounds is never sent.
- A correction names the movement a step recorded by its place: a creation's first
  aporte is 0 and its first value 1, and each saved movement step takes the next. The
  movement at that place is read through `InvestmentReads`. A deletion tried again
  after it committed is answered from its receipt, the lookup the app recovers with.
- A check reads `InvestmentReads.board()`: each investment's value and its date,
  contributed, withdrawn, dividends, gain and return (`gana`, `pierde` or `sin
  ganancia`, in tenths), and the overview's total in pesos with its rate and value
  dates, or "todavía no hay valores anotados" (IN35). After a restore an investment is
  found by its name, unique within a scenario.
- Deshacer runs on real elapsed time, six seconds as in the app: `correcciones` undoes
  right after deleting, late in the scenario, so the corpus's cut and resume never falls
  between them.

The rate's bounds at storage and in backups (IN32's second half), USD conversion bounds
(rule 16), goals beside investments (IN36) and the screens are tested outside the
sandbox, in `:domain`, `:data` and the SELLO-109–111 tests.

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
- Recibo's reservations are figure checks on the month's real snapshot: `separado` is
  `Q` (0 when nothing is set aside), `margen` is the headline `M = R − Q` (`R` when
  nothing is), below 0 when short, and `por día` the daily figure drawn from it.
- Plan del mes is three more figures, put together as income history does from the
  month's summary and the schedules: `esperado` (`F`, 0 when nothing is expected),
  `planeado` (`I + F`) and `sin asignar en el plan` (`I + F − A`).

`pagos-previstos@1` covers RE25–RE29; nothing reserved without a limit, in a past
month or for a paused schedule (RE31–RE34) and the exact total (RE40) are tested in
`ReservationPolicyTest`. The catch-up notice is checked by SELLO-103's tests:
`CatchUpNoticeJourneyTest` drives `semana-fuera@1` through Recibo's bar and its list on
the sandbox's own data (RE10's four), and `CatchUpNoticeViewModelTest` and
`ScheduleRecordReadsTest` cover RE38 and RE39. `plan-del-mes@1` covers Plan del mes (RE35–RE37). Deshacer of an occurrence's deletion (RE15's second half) is not scripted: it runs on real
elapsed time, like the goals' Deshacer; `ScheduleCatchUpContractTest` checks it on real
Room, for a payment and an income.
A catch-up cut after a batch or with an unknown commit (RE12, RE13) is tested in
`:data` and `:domain`, and the day change while open (RE14) by `CatchUpRunnerTest`; here
a failed or unknown batch stops the `CatchUp` step, and its next try runs catch-up
again.
