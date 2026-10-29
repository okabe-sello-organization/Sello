# Sandbox histories

The debug sandbox offers a few rich, synthetic histories to look at the app by hand: new
features, money over time and the look on a phone. Each one is loaded in one go, through
the app's own commands and catch-up, and ends with the financial clock on the history's
"today". From there the clock controls (+1 día, +1 mes, Ir a la fecha) move it on.

The rules themselves (month ends, leap years, pauses, carried values, refusals…) are
tested in `:domain`, `:data` and the ViewModel tests, not here. Synthetic data only.

## Load one

Debug build → **Sello Herramientas** → **Entrar al sandbox** → **Historias** →
**Cargar** on a history, then confirm. The sandbox is erased, the app restarts in the
history's zone and loads every step; the tools show "Cargando: paso N de M"
and the orange badge says "cargando historia" until it is done, then "Cargada. Hoy es
el …".

- **If the app is closed while loading,** the next start carries on from the last
  confirmed step. Each step always sends the same operation, so a step that had already
  saved is answered from its receipt and nothing is written twice.
- **If a step is refused** (or its outcome is unknown), loading stops and the tools say
  "Carga incompleta", which step and why. **Seguir cargando** tries again from there;
  **Cargar** starts over on a new sandbox. A half-loaded history never shows "Cargada".

## The histories

Zone: America/Bogota for all. Amounts in pesos.

| ID | Title | From → today | What it is for |
| --- | --- | --- | --- |
| `un-ano@1` | Un año normal | 1 Nov 2025 → 24 Oct 2026 | Resumen over twelve months; categories with and without a limit; an archived one; year end; a corrected and a deleted expense |
| `mes-apretado@1` | Un mes apretado | 1 Sep 2026 → 31 Oct 2026 | An October over budget and income below the budget; Ajustar; the month's last day (+1 día opens November) |
| `metas@1` | Ahorro para metas | 1 Apr 2026 → 24 Oct 2026 | Goals with aportes, a retiro, a faded stamp, a target change, one marked lograda and one on Recibo |
| `inversiones@1` | Inversiones con dólares | 3 May 2025 → 24 Oct 2026 | The v2 page's four investments over 18 months with values and dividends, and one in USD with a rate |
| `pagos-fijos@1` | Pagos fijos y dos semanas fuera | 1 Jul 2026 → 24 Oct 2026 | Schedules with catch-up, a day-31 payment, a weekly one, a pause, a twice-monthly salary, two weeks away, reserved payments and Plan del mes |

### Un año normal (`un-ano@1`)

Mercado 800.000 (900.000 from March), Transporte 200.000, Salidas 300.000, Salud with no
limit and Ocio 150.000, archived on 1 June. A salary of 3.500.000 on each 1st, and each
month two Mercado expenses (3rd and 17th), a bus top-up of 120.000 (10th), a meal out
(14th; 31 December and 1 January those months), Farmacia 60.000 in January, April, July
and October, and Cine 80.000 while Ocio is open. In September a Mercado expense typed as
450.000 is corrected to 45.000 and a repeated bus top-up deleted.

Checked by `HistoryLoadTest.aNormalYear`, worked out by hand:

- October to the 24th: spent 410.000 + 330.000 + 120.000 + 180.000 + 60.000 = 1.100.000
  of 1.400.000, 300.000 left; income 3.500.000, net 2.400.000, unassigned 2.100.000.
- December 2025: 1.180.000 of 1.450.000 (Ocio still open), 270.000 left. January 2026:
  970.000. September 2026: 1.185.000, 215.000 left.

### Un mes apretado (`mes-apretado@1`)

Hogar 1.200.000, Mercado 600.000, Transporte 150.000, Salidas 200.000 and Imprevistos
with no limit: 2.150.000 a month. September earns 2.300.000 and spends 2.010.000. October
earns 2.000.000 and spends Hogar 1.200.000, Mercado 380.000 + 310.000, Transporte
95.000 + 85.000, Salidas 120.000 and a car repair of 250.000.

Checked by `HistoryLoadTest.aTightMonth`: October spent 2.440.000 of 2.150.000, −290.000
left; Mercado −90.000, Transporte −30.000; net −440.000; unassigned −150.000 (not
clamped). September 140.000 left, unassigned 150.000.

### Ahorro para metas (`metas@1`)

Viaje a la costa (3.000.000): 400.000 each 1st from April to August and 300.000 on 1
September reach 2.300.000 (75 %); a retiro of 800.000 on 15 October leaves 1.500.000.
Celular nuevo (1.200.000): 300.000 each 1st from May to August, marked lograda on 2
August. Fondo de emergencia (6.000.000): 1.000.000 in April and 500.000 in June, August
and October; on 2 October its target goes down to 5.000.000 and it is chosen for
Recibo. Beside them, Mercado 700.000 and Transporte 200.000 with a salary of 3.000.000.

Checked by `HistoryLoadTest.aGoalSaver`: Viaje 1.500.000, 50 %, stamps earned, earned,
kept (faded), empty; Celular 1.200.000, lograda, all four earned; Fondo de emergencia
2.500.000, 50 %, two earned, on Recibo. October's budget is untouched by the aportes
(MT06): 900.000 − 380.000 = 520.000 left, unassigned 2.100.000.

### Inversiones con dólares (`inversiones@1`)

Fondo de inversión, CDT Banco, Parqueadero and Ecopetrol with the movements of the
[investment examples](investment-examples.md) IN02–IN06, each entered on its own day
from 3 May 2025; Acciones EE. UU. in USD on 20 October 2026, aporte USD 1.000,00, value
USD 1.050,50, at 4.150,25. A Mercado budget of 900.000 with 350.000 spent.

Checked by `HistoryLoadTest.anInvestorWithDollars`, with the approved figures: total
42.397.838, aportado 41.150.250, +2.275.588, +5,5 %, dólar a 4.150,25 del 20 oct (IN28);
Fondo 5.242.000 of 14 October, −258.000, −4,3 % (IN04); Acciones +USD 50,50, +5,1 %
(IN27).

### Pagos fijos y dos semanas fuera (`pagos-fijos@1`)

From 1 July: rent 1.200.000 on the 5th, internet 90.000 on the 28th, the gym 120.000 on
the 31st, a bus 25.000 every Wednesday, English classes 200.000 on the 10th (paused on 1
September, still paused) and a salary of 2.000.000 on the 15th and 30th. Mercado
expenses by hand. Sello was last opened on 9 October; on 24 October catch-up records the
bus of 14 and 21 October and the salary of the 15th.

Checked by `HistoryLoadTest.fixedPaymentsAndTwoWeeksAway`:

- October: spent 1.200.000 + 3 × 25.000 + 360.000 = 1.635.000 of 3.600.000, 1.965.000
  left; set aside 235.000 (internet and bus on the 28th, the gym on the 31st), margin
  1.730.000.
- Plan del mes: 2.000.000 recorded and 2.000.000 expected on the 30th, 4.000.000
  planned, 400.000 unassigned in the plan; recorded unassigned −1.600.000.
- The gym on 31 July, 31 August and 30 September; the classes only on 10 July and 10
  August. September spent 2.285.000.

## Tests

- `ScenarioDefinitionTest`: each history's steps name what an earlier step made, dates
  only go forward and end on its day, versions are the ones a screen would read, and
  this document names every history and version.
- `HistoryLoadTest` (JVM, real Room under Robolectric): each history loads to its day
  with the figures above, and a load cut after a committed step whose checkpoint was
  lost ends with the same rows as a load straight through.
- `ScenarioRunnerTest`: loading, carrying on in a new process, a refusal and an unknown
  outcome, on ledgers.

## Adding or changing a history

1. Edit `Scenarios.all` in `app/src/debug/.../devtools/Scenarios.kt`. Use only steps the
   app's commands accept, with the versions a screen would read. Changing the steps of
   an existing history needs a new `version`, because each step's operation is derived
   from ID, version, seed and place.
2. End on the history's `today`. After a date step, a new schedule or a resume, add a
   `CatchUp`.
3. Describe it here and add its key figures to `HistoryLoadTest`, worked out by hand.
