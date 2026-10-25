# Recurrence rules and worked examples

**Ticket:** SELLO-096 · **Updated:** 2026-10-24 · **Status:** owner decisions recorded
below; the examples are approved by merging the SELLO-096 pull request.

The rules every recurrence ticket (SELLO-097–104, SELLO-114) implements and tests
against. Decisions come from [ADR 0017](../decisions/0017-goals-recurrence-investments.md),
[ADR 0012](../decisions/0012-budget-guidance-contract.md) BG1–BG3, architecture §4 and §8,
the [gap list](../design/v2/gaps/recurrence.md) and the owner's answers of 2026-10-24
(marked **Owner**). Rules marked **Proposed** follow from those decisions; the owner
approved them by merging SELLO-096 (2026-10-24). Every figure was worked out by hand, not from code. Amounts are whole COP
pesos, grouped as the app shows them; dates are financial days in the profile's
financial zone. These are expectations, not passing tests.

## Rules

1. **A schedule** is a recurring payment (category, amount, optional note) or a recurring
   income (source, amount, optional note), a repeat rule, a start date and an optional
   end date. Each occurrence it records is an ordinary expense or income with those
   fields, dated on its occurrence date.
2. **Repeat rules:**
   - **Monthly on day d** (1–31): each month on day d, or on the month's last day when
     the month is shorter. The anchor d is kept, so a 31 returns after a 30-day month.
   - **Twice a month on days d1 and d2** (1–31, d1 < d2), chosen by the person
     (**Owner**): each day clamps like monthly. When both clamp to the same date, both
     are recorded that day (**Owner**).
   - **Every N days** (N 1–365) or **every N weeks** (N 1–52): the start date and every
     N days or 7 × N days after it. Every 15 days is not twice a month.
3. **Start and end:** the start date is today or later (**Proposed**); missed past
   payments are entered by hand. An occurrence on the start date or the end date counts;
   none comes after the end date. The end date is on or after the start date.
4. **Origin:** each recorded occurrence carries its origin `(schedule, date, slot)`, slot
   1 or 2 for twice a month and 1 otherwise. An origin is recorded at most once, ever.
   Deleting the occurrence, or undoing that deletion, keeps the origin handled, so a
   deleted occurrence is never recorded again.
5. **No matching:** a manual entry is never matched to a schedule by category, source,
   amount or note (BG2). Both stay; the notice is how a person sees a double entry.
6. **Catch-up** runs when Sello starts, when it returns to the foreground and when the
   financial day changes while it is open (**Owner**), and right after a schedule is
   saved, changed or resumed. It records every occurrence dated
   today or earlier that is not yet handled, each on its own date, with no age limit
   (**Owner**). It works in batches of at most 50 occurrences, each committed atomically
   with its checkpoint, in order of date, then schedule creation, then slot; an
   interrupted catch-up resumes after the last committed batch (**Proposed**, architecture
   §8). No background work and no notification (ADR 0017).
7. **Changes** to amount, category or source, note, rule, start or end apply to
   occurrences after today; recorded ones stay and can be edited one by one. A new rule
   starts tomorrow: occurrences it would have had today or earlier are not recorded.
   Changing an every-N rule sets a new start date after today (**Proposed**).
8. **Pause and resume:** occurrences dated from the pause day up to the day before the
   resume day are skipped, not caught up. The resume day itself counts. A paused schedule
   reserves and expects nothing. Pausing never removes an occurrence already recorded.
9. **Archived category** (**Owner**): archiving a category pauses every payment schedule
   that uses it, from the archive day, and says which. Unarchiving does not resume them.
   Nothing is ever recorded into an archived category.
10. **Deletion** of a schedule, after a confirm slip, removes the schedule only; every
    recorded occurrence stays as an ordinary entry (**Proposed**, as v1.1).
11. **Reserved `Q`** (current financial month only): the sum of payment occurrences in
    that month from schedules active on their date, not yet handled. This includes one
    due today or earlier that catch-up has not recorded yet, so the headline never rises
    and falls back while catch-up runs (**Proposed**). Next month's occurrences are not
    reserved. Past months reserve nothing. A failed read is never `Q` = 0.
12. **Recibo** (ADR 0012): `R = L − E`, `M = R − Q`, daily
    `50 × floor(max(M, 0) / (50 × D))`, `D` days left including today. With `Q` > 0:
    `M` > 0 keeps the recorded status (Al día) with the footnote; `M` = 0 is **Sin
    margen** (neutral, big number 0); `M` < 0 is **Revisar** (warn), label **Falta para
    tus pagos**, big number `−M`, **Ajustar presupuesto**, even when `E` > `L`. When no
    payment remains reserved the footnote and stamps follow BG1 again. A month with no
    finite limit shows no reservation (**Proposed**: there is no remainder to reserve
    from).
13. **Expected `F`** (current financial month only): the sum of income occurrences in that
    month from active schedules, not yet handled. Plan del mes shows only while `F` > 0:
    Anotado `I`, Esperado `F`, Planeado `I + F`, Sin asignar en el plan `I + F − A`. `F`
    never enters recorded income, net flow, Recibo, goals or investments (BG3).
14. **The notice** lists every occurrence catch-up recorded since the person last
    dismissed it, across runs, each with its name (note, else category or source), date
    and amount, opening its edit. An occurrence deleted meanwhile leaves the list. Shown
    only when the list is not empty (**Proposed**).
15. **Bounds:** an amount is 1 to 999.999.999.999 pesos, like an expense; `Q`, `F` and
    every total are checked sums that may exceed it. The note follows the expense note
    rules. There is no limit on the number of schedules.

## Repeat rules

Each schedule here was created on its start date.

| ID | Schedule | Occurrences |
| --- | --- | --- |
| RE01 | Monthly on day 31, start 2027-01-01 | 31 ene, 28 feb, 31 mar, 30 abr, 31 may 2027: the anchor returns to 31 |
| RE02 | Monthly on day 29 | 28 feb 2027; 29 feb 2028; 29 mar 2028 |
| RE03 | Monthly on day 5, start 2026-10-22 | First 5 nov 2026: 5 oct is before the start |
| RE04 | Monthly on day 22, start 2026-10-22, saved on 22 oct | Recorded on saving, dated 22 oct; next 22 nov |
| RE05 | Twice a month on 15 and 30, start 2026-10-01 | 15 oct, 30 oct, 15 nov, 30 nov … 15 feb 2027, 28 feb 2027 |
| RE06 | Twice a month on 30 and 31, February 2027 | Two occurrences on 28 feb 2027, slots 1 and 2; then 30 mar, 31 mar, 30 abr (slot 1) and 30 abr (slot 2) |
| RE07 | Every 15 days from 2026-10-15 | 15 oct, 30 oct, 14 nov, 29 nov, 14 dic, 29 dic, 13 ene 2027. Twice a month on 15 and 30 has 15 nov and 30 nov instead |
| RE08 | Every 2 weeks from vie 30 oct 2026 | 30 oct, 13 nov, 27 nov, 11 dic, 25 dic 2026, 8 ene 2027 |
| RE09 | Monthly on day 5, start 2026-10-22, end 2026-12-05 | 5 nov, 5 dic; nothing after. End 2026-12-04: 5 nov only |

## Catch-up

| ID | Situation | Result |
| --- | --- | --- |
| RE10 | Rent monthly on 5, 950.000, Hogar; bus every week from 2026-09-26, 20.000, Transporte. Last catch-up 3 oct (bus of 3 oct recorded). Sello opens on 24 oct | Records 5 oct rent, 10, 17 and 24 oct bus: four entries, 1.010.000, each on its own date. The notice lists four |
| RE11 | Café every day, 5.000; last handled 31 jul 2026; opens 24 oct | 85 occurrences (1 ago – 24 oct), 425.000. Batch 1: 1 ago – 19 sep (50); batch 2: 20 sep – 24 oct (35) |
| RE12 | RE11, Sello is closed after batch 1 commits | Next start records the 35 of batch 2 only; the notice lists all 85 |
| RE13 | A batch's commit outcome is unknown and catch-up runs again | Origins already recorded are skipped: no duplicate; the result is the same 85 |
| RE14 | Sello stays open from 23:59 on 27 oct to 00:01 on 28 oct; a payment is due 28 oct | Recorded at the day change, without reopening |
| RE15 | 5 oct rent recorded by catch-up, deleted on 6 oct | Catch-up on 7 oct does not record it again. Undoing the deletion brings back the same entry, origin unchanged |
| RE16 | The person also enters the rent by hand on 5 oct | Two expenses of 950.000 (1.900.000 in Hogar); nothing is merged. The notice shows the automatic one |

## Changes, pause, end, archive and deletion

| ID | Situation | Result |
| --- | --- | --- |
| RE17 | Rent monthly on 5, 950.000; on 10 oct the amount becomes 1.000.000 | 5 oct stays 950.000; 5 nov is 1.000.000 |
| RE18 | On 10 oct, day 5 becomes day 25 | 5 oct stays; next 25 oct |
| RE19 | On 10 oct, day 25 becomes day 5 | 5 oct is not recorded (before the change); next 5 nov |
| RE20 | Every 15 days from 15 oct; on 20 oct it becomes every 10 days from 25 oct | 15 oct stays; 30 oct is not recorded; 25 oct, 4 nov, 14 nov … |
| RE21 | Rent monthly on 5, paused 3 oct, resumed 5 dic | 5 oct and 5 nov skipped; 5 dic recorded |
| RE22 | Same, resumed 6 dic | 5 oct, 5 nov and 5 dic skipped; next 5 ene 2027 |
| RE23 | Gimnasio monthly on 15, 99.900; the category is archived on 12 oct | The schedule is paused from 12 oct; 15 oct is not recorded. Unarchived 20 oct: still paused. Resumed 20 oct: next 15 nov |
| RE24 | Rent schedule deleted on 10 oct | 5 oct rent stays as an ordinary expense; nothing more is recorded |

## Reservations on Recibo (current month, 22 oct 2026, `D` = 10, `L` 1.000.000)

| ID | Situation | Result |
| --- | --- | --- |
| RE25 | BG-E02: `E` 400.000; rent 200.000 due 28 oct | `Q` 200.000, `R` 600.000, `M` 400.000; Al día; 40.000 por día; "Incluye 200.000 separado para 1 pago previsto" |
| RE26 | BG-E03: as RE25 but the rent is due 22 oct, before catch-up finishes | `Q` 200.000, `M` 400.000, 40.000 por día. After it records: `E` 600.000, `Q` 0, `M` 400.000, 40.000 por día; the footnote disappears. The headline never moves |
| RE27 | BG-E11: `E` 900.000; `Q` 100.000 | `M` 0; Sin margen (neutral); big number 0; 0 por día; footnote 100.000 |
| RE28 | BG-E06: `E` 900.000; `Q` 150.000 | `R` 100.000, `M` −50.000; Revisar; Falta para tus pagos 50.000; "Quedan 100.000 y tus pagos previstos suman 150.000."; 0 por día; Ajustar presupuesto |
| RE29 | `E` 1.080.000; `Q` 200.000 | `R` −80.000, `M` −280.000; Revisar, not Excedido; Falta para tus pagos 280.000. Once recorded: `E` 1.280.000, `Q` 0; Excedido −280.000 |
| RE30 | Two payments: 120.000 on dom 25 oct, 80.000 on vie 30 oct | `Q` 200.000; "Incluye 200.000 separado para 2 pagos previstos" |
| RE31 | Rent due 5 nov; today 22 oct | Not reserved in October: `Q` 0 |
| RE32 | Rent due 28 oct, schedule paused | Not reserved: `Q` 0 |
| RE33 | Every category is Sin límite; rent due 28 oct | Has gastado este mes; no footnote |
| RE34 | September, a closed month | No reservation, as in every past month |

## Expected income (current month, `A` 2.800.000)

| ID | Situation | Result |
| --- | --- | --- |
| RE35 | BG-E08: `I` 2.000.000; salary 1.000.000 expected vie 30 oct | Anotado 2.000.000; Esperado 1.000.000; Planeado 3.000.000; Sin asignar en el plan 200.000; recorded Sin asignar −800.000; chip "Salario · vie 30 oct · 1.000.000" |
| RE36 | BG-E09: catch-up on 30 oct records the salary | `I` 3.000.000, `F` 0; Sin asignar 200.000; Plan del mes disappears. No double income |
| RE37 | RE35 on Recibo | Recibo's headline, status and daily figure are unchanged by `F` |

## Notice and bounds

| ID | Situation | Result |
| --- | --- | --- |
| RE38 | Catch-up at opening records 2; the notice is not dismissed; the day changes and 1 more is recorded | The notice lists 3. Dismissed: empty and hidden until something new is recorded |
| RE39 | One of the listed occurrences is deleted from its edit | It leaves the list; the other entries stay |
| RE40 | Two payments of 999.999.999.999 reserved | `Q` 1.999.999.999.998, a checked sum; an amount of 1.000.000.000.000 is refused when saving the schedule |

## Open points

None left for the owner. SELLO-114 designs the screens, including whether a recorded
occurrence can also change its schedule "from now on"; Revisar's sentence when `R` is
negative (RE29) is worded there.
