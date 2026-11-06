# Goal rules and worked examples

**Ticket:** SELLO-086 · **Updated:** 2026-10-23 · **Status:** owner decisions recorded
below; the examples are approved by merging the SELLO-086 pull request.

The rules every Metas ticket (SELLO-087–094) implements and tests against. Decisions
come from [ADR 0017](../decisions/0017-goals-recurrence-investments.md), [ADR
0013](../decisions/0013-sello-v2-design.md), the [Metas brief](https://github.com/okabe-sello-organization/Sello/blob/90662770/docs/design/screen-briefs/goals.md)
and the owner's answers of 2026-10-23 (marked **Owner**). Every figure was worked out by
hand, not from code. Amounts are whole COP pesos, grouped here as the app shows them.
These are expectations, not passing tests.

## Rules

1. **Saved** is the sum of a goal's contributions minus its withdrawals, exact, with a
   checked total. It is never below zero: a withdrawal above what is saved is refused, and
   so is any correction that would leave less than zero (**Owner**).
2. **Percentage** is the saved amount × 100 ÷ the target, rounded down to a whole number.
   It can pass 100 % (**Owner**); the bar stops full.
3. **Stamp places** sit at 25 %, 50 % and 75 % of the target, and at the target itself
   (Meta). A place is *reached* when saved × 100 ≥ target × its percentage, with no
   rounding.
4. **A stamp is earned** the first time a recorded change reaches its place. Stamps are
   worked out from the goal's history in the order it was recorded: every movement, with
   its corrections applied in place, and every target change. A movement's date never
   changes that order. The result is the same each time it is worked out, so drawing,
   reopening, restoring or restarting never earns a stamp again.
5. **Earned, then below:** a stamp earned and no longer reached stays, faded ("sigue
   siendo tuyo"). Reaching it again lights it ("De vuelta en el 50 %") and earns nothing
   new. The next stamp to earn is the first one not yet earned.
6. **Corrections** (edit or delete a movement, with undo) replace that movement where it
   was recorded; stamps that only the mistaken figure reached are no longer earned. A
   correction never celebrates.
7. **Target changes** recompute the percentage. A stamp earned under the old target and
   no longer reached stays faded; a place newly reached is earned and gets the modest
   celebration (**Owner**).
8. **Meta:** reaching 100 % is announced, not completed. **Marcar como lograda**
   completes the goal on that financial day and earns the Meta stamp; a goal marked from
   edit below 100 % (reached outside Sello) gets all four stamps (**Owner**). Deshacer
   lasts about 10 seconds, longer with TalkBack; afterwards the goal is final and
   read-only, and can only be deleted.
9. **Dates:** a movement may be dated today or earlier, never later. Its date decides
   the month it counts in (Aportes por mes, the band's "en octubre"); it earns stamps
   when recorded, whatever its date.
10. **Bounds:** a target and each movement are 1 to 999.999.999.999 pesos, like an
    expense; totals may exceed that. A name is 1 to 40 characters (code points) after
    trimming; an icon is required; there is no limit on the number of goals.
11. **Independence:** goals never change budgets, income allocation, Recibo's remainder or
    Resumen's figures, and are not tied to a month.
12. **Recibo's goals:** up to five active goals can be starred; completing a goal removes
    its star (Deshacer puts it back while fewer than five are starred) and deleting it
    removes it. Recibo's list holds up to five active goals: the starred ones first, then
    the rest; inside each group the highest exact completion first (saved ÷ target, not
    the rounded percentage), on a tie the one created first (SELLO-143). Nothing is shown
    with no active goal, or on a past month.
13. **Pace while creating or editing:** with a target month, the months counted run from
    the month after today through the target month. The monthly amount is what is still
    missing ÷ those months, rounded **up** to a whole peso. Each stamp's projected month
    is the first month whose accumulated pace reaches it. A target month this month or
    earlier is not offered.
14. **Deletion** removes the goal and all its movements after the confirm slip, with no
    undo; backups made earlier still hold it.

## Examples

Goal *Viaje a la costa*, target 3.000.000, no earlier movements, unless stated. Today is
2026-10-23. Places: 25 % = 750.000, 50 % = 1.500.000, 75 % = 2.250.000, Meta = 3.000.000.

### Contributions and percentage

| ID | Situation | Expected |
| --- | --- | --- |
| MT01 | Aporte 750.000 | Saved 750.000, 25 %; the 25 % stamp is earned, with the modest celebration |
| MT02 | Aporte 749.999 instead | Saved 749.999, 24 %; no stamp |
| MT03 | *Celular nuevo*, target 1.200.000, saved 450.000 | 37 % (37,5 rounded down) |
| MT04 | Saved 700.000 (23 %, 0 stamps); aporte 900.000 | Saved 1.600.000, 53 %; 25 % and 50 % earned in one aporte, one ticket for both |
| MT05 | Saved 2.950.000; aporte 110.000 | Saved 3.060.000, **102 %**, bar full; Meta reached and waiting, not earned |
| MT06 | Any aporte | Recibo's remainder, category limits and Resumen's unassigned income are unchanged |

### Withdrawals

| ID | Situation | Expected |
| --- | --- | --- |
| MT07 | Saved 1.710.000 (57 %, 25 % and 50 % earned); retiro 400.000 | Saved 1.310.000, 43 %; 50 % stays faded with "El sello del 50 % sigue siendo tuyo: lo lograste en octubre."; next stamp 75 %, faltan 940.000 |
| MT08 | Saved 1.710.000; retiro 1.710.001 | Refused: "Más de lo guardado (1.710.000)", the button waits |
| MT09 | Saved 1.710.000; retiro 1.710.000 | Saved 0, 0 %; 25 % and 50 % both faded |
| MT10 | After MT07 (1.310.000); aporte 200.000 | Saved 1.510.000, 50 %; the 50 % stamp lights again, "De vuelta en el 50 %"; nothing new earned, no stamp celebration |

### Replay

| ID | Situation | Expected |
| --- | --- | --- |
| MT11 | After MT04, open the goal, leave, rotate, restart, restore a backup made after MT04 | 25 % and 50 % earned each time; no new stamp, ticket or operation |
| MT12 | Restore a backup made before MT04 | 0 stamps; MT04's aporte does not exist in that history |

### Corrections

| ID | Situation | Expected |
| --- | --- | --- |
| MT13 | Saved 700.000; an aporte of 900.000 typed as 9.000.000 (75 % and Meta reached); corrected to 900.000 | Saved 1.600.000, 53 %; 25 % and 50 % earned; 75 % never was; Meta not reached; no celebration |
| MT14 | After MT04, delete the 900.000 aporte | Saved 700.000, 23 %; 25 % and 50 % not earned; Deshacer brings back 1.600.000 with both |
| MT15 | Aporte 500.000, then retiro 300.000 dated 20 oct; delete the aporte | **Refused** (**Owner**): "No se puede: el retiro de 300.000 del 20 oct usa este aporte. Borra o corrige ese retiro primero." |
| MT16 | Same; edit the aporte to 300.000, then to 299.999 | First accepted, saved 0; second refused, as MT15 |
| MT39 | Aporte 500.000, retiro 300.000, then aporte 1.000.000; delete the first aporte | Refused, as MT15: in recording order the retiro would take what was not saved yet, although the total would be 700.000 (added in SELLO-087, rule 1 read with MT22) |

### Target changes

| ID | Situation | Expected |
| --- | --- | --- |
| MT17 | Saved 1.710.000 (25 % and 50 % earned); target to 4.000.000 | 42 %; places 1.000.000 / 2.000.000 / 3.000.000 / 4.000.000; 25 % earned, 50 % faded (earned under the old target), 75 % and Meta empty |
| MT18 | Saved 1.710.000; target to 2.000.000 | 85 %; places 500.000 / 1.000.000 / 1.500.000 / 2.000.000; 75 % newly earned, **with the modest celebration** (**Owner**) |
| MT19 | Saved 1.710.000; target to 1.700.000 | 100 %; Meta reached and waiting; 75 % newly earned and celebrated |

### Dates

| ID | Situation | Expected |
| --- | --- | --- |
| MT20 | Aporte 750.000 dated 2026-09-15 | Counts in September's Aportes por mes, not in "en octubre"; the 25 % stamp is earned now |
| MT21 | Aporte dated 2026-10-24 | Refused: a future date |
| MT22 | Aporte 500.000 dated 10 oct, then retiro 300.000 dated 1 oct | Accepted: 500.000 was saved when the retiro was recorded; saved 200.000 |

### Movement list

Each row of a goal's movements shows its balance: what the goal held once that movement
counted, reading the list from the oldest row up.

| ID | Situation | Expected |
| --- | --- | --- |
| MT40 | Aportes 200.000 (2 may), 250.000 (2 jun), 260.000 (2 jul), 300.000 (2 ago), 350.000 (2 sep), 350.000 (2 oct); retiro 400.000 (20 oct) | Newest first, balances 1.310.000, 1.710.000, 1.360.000, 1.010.000, 710.000, 450.000, 200.000; months: oct −50.000, sep 350.000, ago 300.000, jul 260.000, jun 250.000, may 200.000 |
| MT41 | Aporte 500.000 dated 10 oct, then aporte 300.000 dated 1 oct; then aportes 100.000 and 200.000, both dated 12 oct | Rows by date, the later recorded first on the same day: 200.000 → 1.100.000, 100.000 → 900.000, 500.000 → 800.000, 300.000 → 300.000 |
| MT42 | MT22: aporte 500.000 dated 10 oct, then retiro 300.000 dated 1 oct | Aporte on top with balance 200.000; the retiro shows −300.000 (**Owner**, 2026-11-02: as the list reads) |

### Completion and deletion

| ID | Situation | Expected |
| --- | --- | --- |
| MT23 | After MT05 (102 %), six aportes in all; Marcar como lograda | Completed 23 oct; Meta earned; plate "META LOGRADA", 3.060.000, "23 OCT 2026 · 6 APORTES"; a Lograda row "3.060.000, 23 oct" |
| MT24 | MT23, then Deshacer within the window | Back to 102 %, Meta waiting, not completed |
| MT25 | MT23, window over | Final: no edit, Aportar, Retirar or star; only Borrar esta meta |
| MT26 | Saved 1.710.000 (57 %); Marcar como lograda from edit | Completed at 1.710.000; all four stamps lit (**Owner**) |
| MT27 | Delete a goal with 6 movements | "Se borran la meta y sus 6 movimientos. Esto no se puede deshacer."; after Borrar, gone with its movements; no undo |

### Recibo's goal

Goals A *Viaje a la costa* 1.710.000 of 3.000.000 (57 %) and B *Celular nuevo* 450.000 of
1.200.000 (37 %).

| ID | Situation | Expected |
| --- | --- | --- |
| MT28 | No choice | A |
| MT29 | The person chooses B | B, whatever A's completion later |
| MT30 | B chosen, then B completed or deleted | A, until the person chooses again |
| MT31 | Two goals at exactly 50 %, no choice | The one created first |
| MT32 | 57,4 % and 57,9 % (both show 57 %) | The 57,9 % goal |
| MT33 | No active goal, or viewing September | No goal pill |

### Creating and editing

| ID | Situation | Expected |
| --- | --- | --- |
| MT34 | New goal 20.000.000, Para cuándo dic 2028 | 26 months (nov 2026–dic 2028); 20.000.000 ÷ 26 = 769.230,77 → **769.231 al mes**; stamps may 2027, nov 2027, jun 2028, dic 2028 |
| MT35 | Editing MT34 with 2.000.000 saved | Missing 18.000.000 ÷ 26 = 692.307,69 → 692.308 al mes; stamps mar 2027, oct 2027, may 2028, dic 2028 |
| MT36 | No target month | No pace; "Ponle fecha para ver cuándo podrías llegar a cada uno." |
| MT37 | Name of 40 characters; 41 | First accepted; second keeps the text, "Máximo 40 caracteres. Tu texto sigue aquí.", button waits |
| MT38 | Target 999.999.999.999; 1.000.000.000.000 | First accepted; second refused, as an expense |

## Proposed here, approved by merging

Not covered by the ADRs or the owner's answers, so proposed with these examples:

- A tie for Recibo's goal goes to the goal created first (MT31), and completion compares
  exact ratios (MT32).
- The completion plate and the Lograda row show the saved amount, not the target (MT23).
- A withdrawal is checked against what is saved when it is recorded, whatever its date
  (MT22).
- The pace counts from the month after today and rounds up (MT34, MT35); a target month
  this month or earlier is not offered.

Recurring contributions wait for E18.
