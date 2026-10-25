# Investment rules and worked examples

**Ticket:** SELLO-106 · **Updated:** 2026-10-25 · **Status:** owner decisions recorded
below; the examples are approved by merging the SELLO-106 pull request (owner, 2026-10-25).

The rules every investment ticket (SELLO-107–112) implements and tests against.
Decisions come from [ADR 0017](../decisions/0017-goals-recurrence-investments.md), the
[Inversiones brief](../design/screen-briefs/investments.md), the locked
[v2 page](../design/v2/README.md#inversiones), architecture §4 and §8 and the owner's
answers of 2026-10-25 (marked **Owner**). Rules marked **Proposed** follow from those
decisions and are approved with this file. Every figure was worked out by hand, not from
code. COP amounts are whole pesos; USD amounts have two decimals; both are grouped as the
app shows them. Dates are financial days in the profile's financial zone. These are
expectations, not passing tests.

## Rules

1. **An investment** has a name (1–40 characters, the goal name rules), an optional
   description (0–40, shown under the name), an icon and a currency, **COP or USD**,
   chosen when it is created and never changed (**Proposed**). It is created with its
   first contribution and a recorded value dated the same day: **Lo mismo que aporté**
   (the default) or **Otro valor** (v2 page).
2. **Movements:** **Aporte** (contribution), **Retiro** (withdrawal), **Dividendo o
   renta** and **Valor de hoy** (a recorded value). Each has an amount in the
   investment's currency, a date and an optional note (the expense note rules).
   Aportes, retiros and dividends are 1 to 999.999.999.999 minor units: 1 to
   999.999.999.999 pesos, or USD 0,01 to 9.999.999.999,99. A value may also be 0, an
   investment that lost everything (**Proposed**).
3. **Dates:** today or earlier, never in the future (as goals). No movement other than an
   aporte may be dated before the earliest aporte; an earlier aporte becomes the first
   (**Proposed**). The return is "desde" the month of the earliest aporte.
4. **Order:** movements replay by date, then by the order they were recorded
   (architecture §4: stable `(effectiveDate, logicalSequence)` replay).
5. **The value (Owner):** the last recorded value in replay order, plus the aportes,
   minus the retiros after it. It keeps that value's date ("valor del 1 oct"): recording
   a movement is not a fresh valuation. Dividends never change it. A value dated before
   later movements already includes the earlier ones; an aporte dated before the last
   value is already in that value.
6. **Totals:** `C` = aportes, `W` = retiros, `D` = dividends and rents, `V` = the value
   (rule 5). **Gain** `G = V + W + D − C`; **return** `G ÷ C`, one decimal, rounded half
   up on the exact quotient. The sign follows `G`, never the rounded figure. **Gana**
   when `G` > 0, **Pierde** when `G` < 0; at `G` = 0 no stamp and a neutral "0,0 %"
   (**Proposed**). A return needs `C` > 0, so the last aporte cannot be deleted while
   the investment exists ("Una inversión necesita al menos un aporte"); deleting the
   investment removes it (**Proposed**). Never 0 % for a missing denominator.
7. **No value:** when every recorded value is deleted, the investment has no value: no
   gain, no return, **Sin valor** and "aportaste C" on its row, and it is left out of the
   overview entirely (value, movements, bar and return), never counted as 0 (v2 page).
   A retiro then waits for a value.
8. **Withdrawal limit:** a retiro may not exceed the value on its date (rule 5, replayed
   to that point). Above it the amount slip says "Más de lo que vale (…)" and the button
   waits. Any correction (edit or delete) is refused if, replayed, a retiro would exceed
   the value on its date, naming that retiro (**Proposed**, as goals' MT15).
9. **Corrections:** a movement can be edited or deleted with Deshacer, like a goal's
   movement; figures follow the corrected history. Deleting an investment removes it and
   all its movements after the confirm slip that counts them, with no undo (as goals).
10. **Currency (Owner):** a USD investment's amounts, gain and return are in USD.
    Nothing converts inside it.
11. **The rate (Owner):** one app-wide USD rate, in COP per 1 USD, 1,00 to 100.000,00 with
    at most two decimals, dated today or earlier. Changing it records a new dated rate;
    the history is kept. The current rate is the one with the latest date; on the same
    date, the last one entered. A rate is never deleted: a mistaken one is replaced by
    entering another (**Proposed**). It is stored as an exact decimal, never floating
    point, and rejected outside its bounds at entry, in storage and in backups.
12. **Rate first (Owner):** choosing USD in Agregar inversión while no rate exists asks for
    the rate and its date before the investment can be saved.
13. **The overview (Owner):** COP investments add up in pesos. For USD investments, the
    sums of `V`, `C`, `W` and `D` each convert once to pesos at the current rate,
    rounded half up to the peso (architecture §4); the overview's `G` and return come
    from the converted figures. The slip names the rate and its date. Each USD
    investment's own row and detail stay in USD. The bar shares are by value in pesos.
    No USD investment with a value: no rate line.
14. **Value dates on the overview:** the footnote says "Valores anotados entre el *oldest*
    y el *newest*", or "el *date*" when they are all the same, over the investments in
    the total. It is a quiet line, not a link (design, SELLO-106): each row carries its
    own date.
15. **Independence:** investments never touch budgets, income, Recibo or goals, and goals
    are never added to the total (brief, BG4).
16. **Bounds:** sums and conversions are checked; a USD sum of USD 9.999.999.999,99 at
    100.000,00 is 999.999.999.999.000 pesos, which fits.

## Return and value

The four investments of the v2 page (COP).

| ID | Situation | Expected |
| --- | --- | --- |
| IN01 | Brief: aporte 100.000, value 110.000 | `G` +10.000; +10,0 %; Gana |
| IN02 | Parqueadero: aportes 18.000.000, rents 350.000, value 19.000.000 (12 jun) | `G` +1.350.000; +7,5 % exactly; Gana; "valor del 12 jun" |
| IN03 | CDT Banco: aporte 10.000.000, value 10.746.000 (30 sep) | `G` +746.000; 7,46 % → **+7,5 %** |
| IN04 | Fondo: aportes 3.000.000 (3 may 2025) and 3.000.000 (3 nov 2025), retiro 500.000 (3 ago 2026), value 5.242.000 (14 oct) | `G` −258.000; **−4,3 %**; Pierde, "−258.000 de pérdida"; desde may 2025 |
| IN05 | Ecopetrol: aportes 3.000.000, dividends 89.000 + 89.000, value 3.050.000 (9 oct) | `D` 178.000; `G` +228.000; +7,6 % |
| IN06 | The four together | `V` 38.038.000, `W` 500.000, `D` 528.000, `C` 37.000.000; `G` +2.066.000; 5,58 % → **+5,6 %**; "Valores anotados entre el 12 jun y el 14 oct" |
| IN07 | Only Fondo and Ecopetrol | `V` 8.292.000, `W` 500.000, `D` 178.000, `C` 9.000.000; `G` −30.000; **−0,3 %**; Pierde |
| IN08 | Aporte 4.000.000 on 1 sep (value the same); value 5.000.000 on 1 oct; aporte 1.000.000 on 10 oct | `V` **6.000.000**, "valor del 1 oct"; `C` 5.000.000; `G` +1.000.000; +20,0 %. Keeping 5.000.000 would show 0 %: adding money is not a loss |
| IN09 | IN08, then retiro 2.000.000 on 12 oct | `V` 4.000.000 (valor del 1 oct); `W` 2.000.000; `G` +1.000.000; +20,0 %: a withdrawal is not a loss |
| IN10 | IN09, then a retiro of 4.000.001 | Refused: "Más de lo que vale (4.000.000)". 4.000.000 is accepted: `V` 0 |
| IN11 | IN09, then value 3.800.000 on 14 oct | `V` 3.800.000 (valor del 14 oct); `G` +800.000; +16,0 % |
| IN12 | IN11, then an aporte of 300.000 dated 5 oct, recorded on 20 oct | Before the 14 oct value, so `V` stays 3.800.000; `C` 5.300.000; `G` +500.000; 9,43 % → +9,4 % |
| IN13 | On 20 oct: value 4.000.000, then an aporte of 500.000, both dated 20 oct | `V` 4.500.000. Recorded the other way round (aporte, then value): `V` 4.000.000 |
| IN14 | IN11, then a dividend of 50.000 | `V` 3.800.000 unchanged; `D` 50.000; `G` +850.000; +17,0 % |
| IN15 | Aporte 1.000.000, value 1.000.000 | `G` 0; "0,0 %" in neutral ink; no stamp |
| IN16 | Aporte 3.000.000, value 2.999.999 | `G` −1; "−0,0 %", Pierde, "−1 de pérdida": the sign follows the gain |

## No value, limits and corrections

| ID | Situation | Expected |
| --- | --- | --- |
| IN17 | Cripto: aporte 2.000.000; its only value deleted; with the four of IN06 | Row "Sin valor · aportaste 2.000.000"; the overview stays as IN06 (not in the bar, the total or the return) |
| IN18 | IN17, a retiro from Cripto | Waits for a value: "Anota el valor de hoy primero" |
| IN19 | IN08, then a retiro of 6.000.000 on 12 oct; delete the 10 oct aporte | Refused: the retiro of 6.000.000 on 12 oct would be more than the 5.000.000 it would be worth. Delete the retiro first |
| IN20 | IN09, edit the retiro to 7.000.000 | Refused: "Más de lo que vale (6.000.000)" on 12 oct |
| IN21 | IN09, delete the 12 oct retiro, then Deshacer | Deleted: `V` 6.000.000, `W` 0, `G` +1.000.000. Deshacer brings back IN09 |
| IN22 | An investment's only aporte deleted | Refused: "Una inversión necesita al menos un aporte" |
| IN23 | Delete Ecopetrol (IN05: 2 aportes, 2 dividends, 1 first value and 4 more values = 9 movements) | Confirm slip "Se borran la inversión y sus 9 movimientos"; then gone, no undo |
| IN24 | A movement dated tomorrow | Refused, as goals |
| IN25 | First aporte 3 may 2025; a value dated 1 may 2025 | Refused: before the first aporte. An aporte dated 1 may 2025 is accepted and the return reads "desde may 2025" still; dated 1 abr 2025 it reads "desde abr 2025" |
| IN26 | An aporte of 1.000.000.000.000 pesos | Refused at entry; 999.999.999.999 accepted |

## USD and the rate

Rate 4.150,25 dated 20 oct unless stated.

| ID | Situation | Expected |
| --- | --- | --- |
| IN27 | Acciones EE. UU. (USD): aporte USD 1.000,00, value USD 1.050,50 | `G` +USD 50,50; 5,05 % → **+5,1 %**; Gana; row and detail in USD |
| IN28 | Overview: IN06 plus IN27 | USD `V` 1.050,50 → 4.359.838 (4.359.837,625 rounded); `C` 1.000,00 → 4.150.250. Total `V` 42.397.838, `C` 41.150.250, `W` 500.000, `D` 528.000; `G` +2.275.588; 5,53 % → **+5,5 %**. Slip: "Dólar a 4.150,25 · tasa del 20 oct" |
| IN29 | IN28, new rate 4.000,00 dated 24 oct | Converted `V` 4.202.000, `C` 4.000.000; the investment's own return stays +5,1 % in USD; the slip says "tasa del 24 oct" |
| IN30 | 4.000,00 entered, then 4.010,00, both dated 24 oct | 4.010,00 is current: `V` 4.212.505 (4.212.505,00), `C` 4.010.000 |
| IN31 | 4.000,00 dated 24 oct is current; 3.900,00 dated 22 oct is entered after | Current stays 4.000,00 (latest date); 3.900,00 joins the history |
| IN32 | Rates 0,99, 100.000,01 or a date tomorrow | Refused at entry; 1,00 and 100.000,00 accepted. A stored or backed-up rate with three decimals, or outside the bounds, is a decoding error, never rounded |
| IN33 | No rate yet; Agregar inversión with Dólares | Asks "¿A cuánto está el dólar?" with its date before Crear inversión can be pressed |
| IN34 | USD amounts | USD 0,01 and USD 9.999.999.999,99 accepted; USD 10.000.000.000,00 refused; typed with the pad's comma key, at most two decimals |
| IN35 | Investments exist, none with a value (COP or USD) | The answer slip says "Todavía no hay valores anotados" with no number, stamp, return or breakdown; the rows say Sin valor; no bar and no rate line (**Proposed**) |
| IN36 | IN28 with a goal of 5.000.000 saved | The total is unchanged: goals are not investments |

## Open points

None left for the owner. Later return periods, annual rates, benchmarks and automatic
rates stay out of E19 (ADR 0017, the brief).
