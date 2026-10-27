# Sello v2 design

**Status:** the design source of truth since 2026-10-16
([ADR 0013](../../decisions/0013-sello-v2-design.md)). Every screen was decided with the
owner and locked one at a time. Where v2 and the v1.1 snapshot differ, v2 applies; what
v2 does not show still follows v1.1. v2 is presentation, not financial specification:
[architecture](../../../ARCHITECTURE.md) and ADRs 0001–0012 still own money, dates and
recovery.

Open the pages over HTTP from the repository root:

```bash
python3 -m http.server 8765 --bind 127.0.0.1
```

Then visit `http://127.0.0.1:8765/docs/design/v2/home.html`. Pages use the bundled
fonts, the colour values of `SelloColors.kt` and icons generated from
`design-system/src/main/res/drawable`. `flag`, `close`, `trending_up`, `trending_down`,
`star` and `star_outline` are mock-only icons that the design system does not bundle yet. All figures are
synthetic.

| Screen | State |
| --- | --- |
| [Recibo / Home](home.html) | Locked 2026-10-15 (past-month state added with Resumen) |
| [Expense and income entry](entry.html) | Locked 2026-10-15 |
| [Resumen](resumen.html) | Locked 2026-10-15 |
| [Income](income.html) | Locked 2026-10-15 |
| [Metas](metas.html) · [goal detail](goal.html) · [Aportar/Retirar](aportar.html) · [create a goal](create.html) · [celebrations](celebrate.html) | Locked 2026-10-16; + Nueva meta moved to the band by the owner 2026-10-26; Nueva meta and Nueva categoría made alike by the owner 2026-10-26 (see below) |
| [Inversiones](inversiones.html) | Locked 2026-10-16; USD and the rate added 2026-10-25 by owner delegation (SELLO-106) |
| [Pagos e ingresos fijos](fijos.html) | Locked 2026-10-25 by owner delegation (SELLO-114): Se repite, the list, changing a schedule, upcoming payments, the notice; the list redesigned by the owner 2026-10-26 (SELLO-122) |
| [Ajustar presupuesto](ajustar.html) | Locked 2026-10-26 by the owner: the screen behind Recibo's **Ajustar presupuesto ›** and its limit sheet |
| [Shared components](components.html) | Catalog of every part, with its status against `design-system` (Exists / Extend / New) |

## Recibo / Home

**Order, top to bottom:** month bar → answer slip → review invitation (early month
only) → goal → categories. Anotar is a full-width button on a dock above the tabs; it is
the same on every tab except Metas (see Metas).

**Answer slip**

- Label **Restante del presupuesto** and a status stamp on one line, then the big
  number: the budget left after reserving upcoming payments.
- Under the tear line: **per day** as a clear second number (`40.000 por día`) with
  `10 días, incluido hoy` below it. Always shown in the current month, including
  `0 por día`.
- While a payment is reserved, a small footnote at the bottom:
  `Incluye 200.000 separado para 1 pago previsto ›`. It opens the list of payments
  ([Pagos e ingresos fijos](#pagos-e-ingresos-fijos)).
  It disappears when nothing is reserved.
- The bottom edge is the v1.1 pinked shape (sharp teeth, 10dp × 5dp). The tear line
  keeps its punched notches.

**Status stamps**

| Situation | Stamp | Colour | Extra |
| --- | --- | --- | --- |
| Spending below the limit | Al día | gain | — |
| Spending equal to the limit | Al límite | neutral | — |
| Spending above the limit | Excedido | loss | Signed red remainder, e.g. −80.000; link **Ajustar presupuesto**, which opens [Ajustar presupuesto](#ajustar-presupuesto) |
| Reserved payments use up what is left | Sin margen | neutral | Big number 0 |
| Reserved payments are more than what is left | Revisar | warn | Label **Falta para tus pagos**, positive shortfall (e.g. 50.000), one sentence `Quedan 100.000 y tus pagos previstos suman 150.000.`, link **Ajustar presupuesto**. Already over the limit (RE29): `Pasaste tu límite por 80.000 y tus pagos previstos suman 200.000.` |
| No finite limit | Sin límite | neutral | Label **Has gastado este mes**; no per-day line |

**Goal:** one pill, 56dp tall. The pill itself is the progress bar: it fills with
`brandSoft` up to the goal's percentage. It shows the goal's icon, name and
percentage, and ends with a round **+** button (Aportar). Tapping the pill opens
the goal. Only shown when Metas ships.

**Another goal reached 100 %:** when a goal that is not on Recibo reaches 100 % (for
example while the user was away), a green row appears under the pill: ✓ the goal's
name, "Llegó al 100 %" and **Marcar como lograda**. The goal on Recibo stays in place.

**Categories:** unchanged circle grid. The card's header offers **+ Nueva categoría**, the
same pill as Metas' **+ Nueva meta** in the soft brand fill on paper (owner, 2026-10-26),
under the title at large text. A category without a limit on purpose (Sin
límite) has a dashed ring with an ∞ badge at its lower right and **sin límite** under
its amount instead of "gastado" (owner, 2026-10-26); one never configured or paused by
archiving keeps the solid ring and "gastado". Archived categories come last, under
**Archivadas**, in grey ink (circle, fill and badge; amounts unchanged), each section in the
chosen order (owner, 2026-10-26); Ajustar presupuesto groups them the same way. A
category shows only its own remainder;
reserved payments are not shown inside categories. Overruns use the signed red
remainder.

**A past month:** the big number is what was left when the month closed, with a line
such as `Mes cerrado · gastaste 920.000 de 1.000.000.` Stamp **Cumplido** (gain) when it
closed within the limit, **Excedido** when it went over. No per-day line, no reserved
payments and no goal pill; categories show that month's final remainders.

**Review invitation:** one dashed-outline line, for example
`Ver cómo cerraste octubre ›`. Never a stack of cards.

**Settled during the review and rejected:** Anotar inside the tab bar (crowded with 4
tabs and large text), reserved amount inside a category, receipt-line, column and
sentence layouts for the lower half, and the other goal looks (ticket stub, ink pill,
punch card, goal stamp, coupon).

## Expense and income entry

**Order, top to bottom:** ✕ and the Gasto/Ingreso switch → amount slip (pinked edge,
label and date pill on top, category circle beside the amount) → category or source
chips → note row → **Anotar gasto / Anotar ingreso** → Sello pad. The amount, Anotar
and the pad never move; only the chips and note area scroll. Income shows the amount
in gain green with a `+`.

- **Amount entry:** always the Sello pad, ready the moment the form opens. The system
  keyboard is never used for amounts. Pad: the familiar 3 × 4 layout (1–9, then `000`,
  `0`, delete), 44dp keys with 4dp gaps (48dp touch pitch), 192dp in total.
- **Delete key:** soft brand tint (`brandSoft` with `onBrandSoft` icon) so it stands
  apart from the digits. Long press clears the whole amount and says so to TalkBack.
- **Amount size steps down as digits are added**, so the full amount always stays on
  one line and is never cut or wrapped:

  | Digits | Size (normal phone) | Narrow window (≈320dp) |
  | --- | --- | --- |
  | Up to 6 | 45sp (full size) | 45sp |
  | 7–9 | 35sp | 31sp |
  | 10–12 | 26sp | 21sp |

  Twelve digits is the maximum. A 13th key press shakes the amount, changes nothing and
  shows **Máximo 12 dígitos** in red in place of the "Gasto en pesos" label until the
  next valid key, so no line is reserved and nothing moves. On Android this is
  auto-sized text with these steps as the allowed sizes.
- **Category:** none selected when the form opens, so Anotar waits for one. Opening
  from a category ("Anotar aquí") pre-selects it.
- **Chips:** v1.1 SelloChip shape (36dp, 10dp corners, 1.5dp outline). At most two
  rows on any screen size; more chips scroll sideways with a 16dp fade at the edge.
- **Note:** its own row under the chips ("Agregar nota", or the note with Editar).
  It opens a composer docked to the top of the text keyboard (like a chat box),
  showing what the note belongs to, a ✓ to finish and Cancelar. Over 60 characters:
  the text is kept, the box and counter turn red and ✓ is disabled. The Otro income
  source name uses the same composer.
- **Saving:** no receipt screen. While saving, the button shows a spinner and
  "Guardando…" and everything else is locked. A definite failure says
  **No se guardó. Tu borrador sigue aquí.** and keeps the draft. An unknown outcome
  says **Aún no sabemos si se guardó. Compruébalo antes de volver a anotar.** and its
  only action is **Comprobar si se guardó** (same operation, no second write).
- **Saved:** the form closes and Home shows a short confirmation above Anotar, for
  example **Gasto anotado: 48.700 en Comida**, with the numbers already updated. Ink
  background, paper text, as v1.1's notice. No undo for new entries.
- **Se repite** (added 2026-10-25, [Pagos e ingresos fijos](#pagos-e-ingresos-fijos)):
  one more row under the note turns the entry into a schedule.

**Rejected during the review:** the system keyboard for amounts (layout jumps after the
note closes, no `000`), the four-column pad, darker-paper and outlined delete keys, the
paper-slip note dialog, and placing the note beside the date or under the amount.

## Shared: the selected month

Recibo and Resumen share one selected month, chosen in the month bar. There is no
range sheet (3, 6, 12 months, year, all): the MVP never shipped it and it has no ticket.
While the selected month is not the current one, every tab screen shows the same
ribbon under the month bar: 📅 **Estás viendo septiembre** with a **Volver a octubre**
button that returns to the current month in one tap.

## Resumen

**Order, top to bottom:** hero → income, with where the money goes (SELLO-124) → month
by month → which days → one suggestion. The month in progress is never projected.

- **Hero:** "¿Cómo va este mes?". The big number is the difference with the same days
  of the previous month (**50.000** "menos que en septiembre", ↓ 11,1 % al mismo día del
  mes). Under the tear line, "Del 1 al 22 de cada mes" and two bars on the same scale
  with exact amounts. Up and down use the same neutral ink and arrow: spending more is
  a fact, not a failure. A past month asks "¿Cómo cerró septiembre?" and compares full
  months.
- **Income** (right after the hero; redesigned by the owner 2026-10-26, SELLO-123 and
  SELLO-124):
  "¿A dónde fue lo que entró?" as a receipt, and the only place categories are ranked.
  **Entró en {mes}** and the recorded total, a bar of spent (solid) against the rest of
  the income (striped), "Lo gastado en octubre, hasta el 22", then every category as
  "¿Dónde se va?" showed it (circle, name, share bar against spending, exact amount,
  opening the category), a **Gastos** line and **Ingresos menos gastos** as the total.
  Spending above the income signs it in red with "Anotaste 1.000.000 de ingresos y
  1.120.000 de gastos."; with no income recorded the slip says "Aún no anotas ingresos
  en {mes}." and shows the rows and their **Total**, with no negative figure. Spending
  only: goal and investment records are not destinations (ADR 0012 BG4), and Sin asignar
  stays on the income screen. Under the tear line, a stub of two centred halves:
  **Ingresos** ("3 en octubre") opens the income list and **Fijos** ("Próximo: 28 oct",
  "hoy", "mañana" or "Ninguno activo") opens Pagos e ingresos fijos. The mockup is in
  SELLO-124's `artifacts/`.
- **Mes a mes:** "Un mes normal" (average of the full months shown) first, then
  v1.1 columns: past months in `pastBar`, the month in progress striped and marked
  `*`, one dashed limit line, values printed only on the highest and current
  columns. Tapping a column gives its exact value.
- **¿Qué días gastas más?** the answer first ("Los sábados", the average), then the
  weekday columns.
- **Suggestion:** at most one, as a tinted note (`brandSoft`, light-bulb circle,
  "Una sugerencia", × to dismiss). The evidence is written as a plain fact; the advice
  starts with "Podrías…" and ends in one action, e.g. **Ajustar el presupuesto de
  Café ›**. A category is eligible after going over its limit two months in a row; the
  one with the bigger budget wins, and a dismissed suggestion may return next month
  ([Resumen brief](../screen-briefs/summary-and-income.md)). The "3 of 4 months"
  example is illustrative.
- **States:** first month (says what is missing, shows only real figures, no
  comparison or suggestion), no expenses, loading, failed read (never zero).

**Rejected during the review:** the sentence-only hero, income at the bottom or as a
single line, the dashed-proposal and question-with-choices suggestions, and bringing
back the range sheet.

## Income

A screen opened from Resumen's income slip: back arrow, title **Ingresos de octubre**,
the usual tabs and a dock labelled **Anotar ingreso**. It is planning from the
person's own records, never a bank balance. Only category budgets assign income;
goal or investment records never do.

**Order, top to bottom:** hero → plan box (only with expected income) → suggestion
(only while something is unassigned) → this month's income.

- **Hero:** "Entró en octubre" and the recorded total as the big number. Under the
  tear line, a split bar (solid: in category budgets; striped: unassigned) and two
  receipt lines: **En presupuestos de categorías** and **Sin asignar**. A negative
  unassigned amount is signed and red, followed by the two facts behind it
  ("Tus presupuestos suman 2.800.000; anotaste 2.000.000."), with no suggestion.
- **Suggestion:** the Resumen tinted note: "1.000.000 de lo que entró aún no tiene un
  presupuesto. Podrías darle un destino: ahorro, ocio o una reserva, con su propia
  categoría." → **Asignar a una categoría ›**. Not shown for a closed month.
- **Plan del mes** (the month in progress, while a fixed income is still expected): a
  dashed box with "Incluye lo que esperas recibir", receipt lines **Anotado**,
  **Esperado** (softer), a total rule, **Planeado** and **Sin asignar en el plan** in
  bold, then each expected income as a dashed chip (📅 Salario · vie 30 oct · 1.000.000)
  and "Lo esperado se anota solo en su fecha." Expected money never enters recorded
  totals.
- **Lo que entró:** each income with source, date, note and amount, tap to edit; no
  divider under the last entry; a total line with a solid rule.
- **States:** no income ("Sello funciona sin ingresos"), loading, failed read (never
  zero), a past month (the shared ribbon, no suggestion).

**Rejected during the review:** leading with the unassigned amount, a balance
disclaimer under the big number, the equation layout for the plan box and a
six-month income chart.

## Metas

The v1.1 Metas screen, kept close to the original on purpose, in its own tab.

- **Top bar:** title **Metas** and settings. Goals are not tied to a month, so there is
  no month bar.
- **Band:** "Guardado en metas" with the total of active goals (never investments) and
  "+ 350.000 en octubre" for this month's contributions. A white **+ Nueva meta** chip
  at the end of the label's line creates a goal; at large text it moves under the total.
  It replaced a bare **+** in the top bar that was easy to miss, and stays quieter than
  the green dock (owner decision, 2026-10-26).
- **One pinked stamp card per goal, highest completion first:** icon and name, the
  percentage, the saved amount large, "de 3.000.000" and 🏁 target date (or "Sin fecha"),
  a green progress bar and four stamp places (25 %, 50 %, 75 %, Meta). Reached places
  are solid green tilted stamps; the rest are dashed. No dates under the stamps, so no
  projected months. Tapping a card opens the goal.
- **No Aportar on the cards.** On this tab the dock becomes **Aportar a una meta**, green
  with the flag icon, so it cannot be mistaken for Anotar; it asks which goal. Recording
  an expense is not offered from Metas: switching to spending here is a change of
  context (owner decision).
- **Completed goals** stay at the bottom as pinked rows: icon, name, "400.000, 30 sep" and
  a green **LOGRADA** stamp.
- **States:** no goals (v1.1's four empty stamp places and "Ponle nombre a algo que
  quieras"; the dock becomes **Crear una meta**, the one main action, with no button in
  the card, since there is nothing to contribute to yet), loading, failed read (never
  "no goals").

**Goal detail** (v1.1's "Detalle de meta", opened by tapping a goal):

- **Top bar:** ← goal name, edit. Under the name in the band, a small star pill chooses
  the goal shown on Recibo: **★ En tu Recibo** (filled) or **☆ Mostrar en Recibo**
  (outlined). Only one goal can be on Recibo; choosing another moves it. Not shown on a
  completed goal.
- **Main card (pinked):** "Ya guardaste" and the percentage in green, the saved amount
  large, "de 3.000.000" / "faltan 1.290.000", the green bar; under the tear line
  **Sellos 2 de 4**, the four stamps and "🏁 Próximo sello: 75 %, faltan 540.000".
- **Facts:** Meta, Fecha objetivo, Desde as receipt lines.
- **Aportes por mes:** contributions per month, this month in brand.
- **Movimientos:** contributions and withdrawals, newest first. A contribution that
  earned a stamp shows the stamp in place of its + icon, with "Ganaste el sello del
  50 %". **Retirar dinero** sits in this card's header; a withdrawal is its own entry and
  the history is kept.
- **Dock:** green **Aportar a esta meta**.
- **After a withdrawal below an earned stamp,** the stamp stays, faded, with "El sello del
  50 % sigue siendo tuyo: lo lograste en octubre." Percentage and bar show the honest
  figure; the next stamp to earn is the first one not yet earned.
- **Completed goal (read-only):** four stamps, "Meta lograda el 30 sep" and the history.
  No edit, no Aportar dock, no Retirar dinero and no star. Its only action is a red
  **Borrar esta meta** card at the bottom, with the same confirm slip as editing.
- Removed from v1.1: "Llegas en …", "Al mes …" and "Cada mes aportas más" (projections).

**Aportar / Retirar** reuses the locked entry screen almost unchanged: pinked amount slip,
Sello pad, chips in at most two rows, note row and composer, the action fixed above the pad,
no live preview, and the same saving / rejected / unknown-outcome / restore behaviour.

- The **Aportar / Retirar** switch replaces Gasto / Ingreso.
- **Aportar:** green amount with "+", the goal's icon in a green circle, a green **Aportar**
  button. Opened from a goal, that goal is chosen; opened from the Metas tab dock, no goal
  is chosen and Aportar waits. Goal chips follow the tab's order and show their %. One
  plain fact under the chips: "Llevas 1.710.000 guardados en esta meta."
- **Retirar:** ink amount with "−" and a neutral **Registrar retiro** (using savings is not
  a failure). A withdrawal is limited to what the goal has saved; above that the label
  becomes "Más de lo guardado (1.710.000)" in red and the button waits.
- No "Sello only tracks progress" disclaimer: Sello connects to no bank, so there is no
  room for confusion (owner decision).

**Create and edit a goal** (v1.1's "Nueva meta" blended with the first round):

- **Top bar:** ✕ and **Nueva meta** (or **Editar meta**).
- **Identity card:** the goal's icon in a large green circle beside **Nombre** (40
  characters, with a counter). Empty, the circle is a dashed green ring around a centred
  **+**, captioned **Elegir ícono**; chosen, it is filled with a small pencil and
  **Cambiar**. Tapping it opens **Elige un ícono**, a sheet with the shared 35 icons (the
  same for categories, goals and investments). A new goal opens on the name with the keyboard up. Too long a name keeps the
  text, turns the line red ("Máximo 40 caracteres. Tu texto sigue aquí.") and the button
  waits.
- **Amount card (pinked):** "Cuánto necesitas" and the amount as the big number, typed on
  the Sello pad, which is always under the form (the keyboard covers it only while the
  name is typed). **Para cuándo** is the slip's corner, as Anotar's date: a chip-cornered
  pill with the month or **Sin fecha**. It opens the month sheet, with **Sin fecha** as a
  green outlined button on the title line. With a date: "Son 769.231 al mes durante 26
  meses." Whole pesos, never rounded further (20.000.000 ÷ 26 = 769.230,77 → 769.231).
- **Tus sellos:** centred, each stamp in an equal share of the width, with the month each
  could arrive at that pace ("Si aportas 769.231 cada mes": may 27, nov 27, jun 28,
  dic 28). These projected months appear only while creating or editing, as a plan with
  its assumption spelled out. Without a date: empty stamps and "Ponle fecha para ver cuándo podrías llegar a cada uno".
- **Action:** green **Crear meta** (or **Guardar cambios**) with the flag; it waits for a
  name, an icon and an amount, and the line above it says which is missing first
  ("Elige un ícono.", "Escribe un nombre.", "Escribe cuánto necesitas."), as on Anotar.
- **Editing:** earned stamps show "logrado"; the rest show their month at the new pace.
  Two separate decisions follow, each in its own card: **¿Ya la lograste?** with
  **Marcar como lograda** (for goals reached outside Sello), and **Borrar esta meta**, a
  red-outlined card that says what is lost ("Se borran la meta y sus 6 movimientos. Esto no
  se puede deshacer."). **Borrar meta** opens a confirm slip: "¿Borrar Viaje a la
  costa?", **Cancelar** and a red **Borrar**.
- Automatic monthly contributions wait for recurrence.

**Nueva categoría matches it** (owner, 2026-10-26): the same identity card in Sello blue,
opening on the name with the keyboard up and the first unused icon already chosen; the
icon sheet shows icons other categories use dashed, still choosable ("Ya lo usa otra
categoría; puedes repetirlo."). **Límite mensual** is the pinked amount slip typed on the
always-present Sello pad, with **Sin límite** in its corner; with it on, the amount,
suggestions and pad grey out and what was typed comes back. Zero is a limit: 0 types it.
Pasted text that is not an amount is kept as it came beside why. The same layout serves
Editar categoría.

**One icon set of 35** (owner, 2026-10-26): the 24 categories had, the three money icons
investments had (Banco, Acciones, Fondos) and eight more for money and investments
(Efectivo, Divisas, Billetera, Portafolio, Cripto, Negocio, Apartamento, Agro). Categories
and goals list them in that order; investments list the eleven money ones first. A goal or
an investment keeps its own names where it had them ("Casa", "Finca raíz").

**Celebrations** play only after a confirmed save; an uncertain outcome never celebrates.
Reduced motion keeps the same text and final picture without movement or confetti.

- **Earning a stamp, from the goal (S1):** back on the goal, the stamp lands in its place
  with an ink ring; the save toast adds "Ganaste el sello del 50 %". One haptic tap.
- **Earning a stamp, from Metas or Recibo (S2):** the save toast becomes a paper ticket with
  notched sides and the stamp lands on it: "Ganaste el sello del 50 % · Viaje a la costa ·
  faltan 1.290.000 para la meta". Two stamps in one aporte share one ticket.
- **Back above a kept stamp:** the faded stamp lights up again in place, "De vuelta en el
  50 %". No new stamp is earned.
- **Reaching 100 % is announced, not completed.** The Meta place waits as a green dashed
  circle. On the goal: "Llegaste al 100 %. Márcala como lograda para ganar el último sello"
  and a green **Marcar como lograda**; on Metas the card goes first with the same button and
  the ticket announces it; on Recibo, the green row under the goal pill. Aportar stays
  available until the goal is marked.
- **Marcar como lograda opens the big moment:** on the desk the four stamps land one by one
  in the middle of the screen, Meta last; green floods out of the Meta stamp; the stamps
  rise, **¡Lo lograste!** appears and a rubber-stamp plate lands (META LOGRADA, icon and
  name, the amount between dashed rules, date and number of aportes); paper chads fall
  (holes, teeth, strips). **Listo** and **Ver mi meta**. The plate is a rectangle that grows
  with its content: long names wrap, long amounts step down like the entry amount.
- **Deshacer:** after **Listo** the person is back where they tapped Marcar como lograda,
  the goal is a Lograda row and a toast says "Celular nuevo, meta lograda. Deshacer" for
  about 10 seconds (longer with TalkBack). Deshacer returns the goal to 100 % waiting; once
  the toast is gone the goal is final. There is no later reopening.
- **Revisiting a completed goal:** it opens on its slip and the LOGRADA stamp lands once per
  visit; in Metas the Lograda stamps land as the rows scroll into view.

**Rejected during the review:** compact pills, a percentage-led card in brand blue, an
"En Recibo" tag, Aportar on every card, a global Anotar dock on Metas, a "Mostrar en
Recibo" toggle, a star in the top bar or a chip in the main card, a separate stamp line in
the movement list, taking a stamp back after a withdrawal, rounding the monthly amount
(769.300), creating a goal from a bare + or a labelled pill in the top bar or from a
dashed "Crear otra meta" card after the goals (below the fold with three goals), the
phone keyboard for the goal amount, Borrar meta as a link at the end of the form, and "Marcar como completada" (Lograda is the word Metas already uses). For
celebrations: one ticket everywhere, a centred milestone slip, a printed final receipt, a
round seal (it cannot hold long names or amounts) and automatic completion at 100 %.

### Owner review notes — 2026-10-15

All four notes are now applied in the mockups (2026-10-16).

- **Faded milestone stamp accepted:** after a withdrawal, keeping the past stamp
  grayed out is consistent with the owner's intent. The current percentage and bar
  stay honest; the faded stamp acknowledges a historical achievement, and the
  contribution history stays.
- **Deliberate completion acknowledgment:** **Marcar como lograda** is an earned moment
  the person takes themselves, replacing the earlier automatic-only completion. The
  transition and celebration sequence are specified under Celebrations above.
- **Completed goals are read-only:** no edit action and no contribution or withdrawal
  controls; the goal, its history and the celebratory revisit stay accessible. Deleting
  is the only action. No reopening or correction workflow.
- **No-goals dock:** with no goals, the dock is **Crear una meta**, never an invitation to
  contribute before a goal exists.

## Inversiones

The fourth tab, for the Later increment. It answers "¿Cómo van mis inversiones?". Gain
follows the architecture: value + withdrawals + dividends − contributions; the percentage
is that gain over contributions since the first contribution (simple gain, not IRR), one
decimal. Nothing is fetched or verified; every value shows when it was recorded. No
benchmark, value target or interest accrual. COP and USD with one rate the person types
(ADR 0017; [Dólares y la tasa](#dólares-y-la-tasa)). Rules: [investment examples](../../testing/investment-examples.md).

- **Top bar:** title **Inversiones**, **+** to add an investment, settings. No month bar.
- **Answer slip (pinked, as on Recibo):** "Tus inversiones valen" with a **Gana** /
  **Pierde** stamp, the value as the big number in ink, then the signed return in green or
  red with an arrow and pesos ("▲ +5,6 % · +2.066.000 de ganancia", "En total, desde mar
  2025"). Under the tear: Aportaste, Retiraste, Dividendos y rentas and Ganancia as
  receipt lines, and a quiet line "Valores anotados entre el 12 jun y el 14 oct" (a link
  until SELLO-106 dropped it: each row carries its own date).
- **Dónde está, de mayor a menor:** the income split bar with one shade of the ink per
  investment, then a row per investment: icon in its shade, name, description and "valor
  del 12 jun", value and signed %. Tapping a row opens it.
- **An investment without a recorded value:** no message on the overview (owner decision).
  Its row says **Sin valor** and "aportaste 2.000.000"; it is left out of the bar and the
  total, never counted as 0.
- **Dock:** **Anotar movimiento** in ink, so it is distinct from Anotar (brand) and the
  Metas dock (green). With no investments it becomes **Agregar inversión**; the empty
  slip has no button and no chart.
- **States:** total in loss (Pierde, plain figures, no advice), loading, failed read
  (never zero or "no investments").

**Investment detail:** ← name, edit. The same slip ("Vale", stamp, value, signed return,
"Valor anotado el 9 oct · desde mar 2025", the breakdown) with **Anotar el valor de hoy**;
a chart of recorded values only, as dots joined by a thin line, with a dashed step line
for what was put in (contributions minus withdrawals); then Movimientos (Valor de hoy,
Dividendo, Aporte, Retiro). Dock: Anotar movimiento.

**Anotar movimiento** reuses the locked entry screen: pinked amount slip, Sello pad, the
action above the pad, note row, one plain fact and no live preview.

- Top bar: ✕ and **Movimiento**. The type is a row of chips, **Aporte · Valor de hoy ·
  Retiro · Dividendo o renta**, above the investment chips (one row each).
- Aporte "+", Valor de hoy without a sign (it replaces the last value, it is not added),
  Retiro "−", Dividendo o renta "+" (counts in the gain, not in the value). Buttons in
  ink: Anotar aporte / valor / retiro / dividendo.
- A withdrawal is limited to the last recorded value: above it the label becomes "Más de
  lo que vale (5.242.000)" in red and the button waits; the fact offers **Anotar el valor
  de hoy** first.
- Opened from an investment it is chosen; from the dock none is, and the button waits.

**Agregar / editar inversión** follows Nueva meta in ink (made alike by the owner on
2026-10-26): the icon circle beside the name (40 characters), opening the shared 35 icons
with the money ones first; the optional description; **Pesos | Dólares** as a segmented
switch. A new investment opens on the name with the keyboard up. **Primer aporte** is the
big number with its date in the slip's corner, typed on the Sello pad, which is always
there; at the slip's foot, "Vale hoy: lo mismo que aportas · **Otro valor**", so a new
investment starts with a recorded value. Otro valor adds its own **Vale hoy** slip. The
figure the pad types into is edged in ink and said as such; the others step back and a
tap gives one the pad, scrolling it into view. Nothing above the figures is pinned: they
and the identity scroll together over the button and the pad. A line above **Crear
inversión** says what is missing first. Editing changes name, description and icon only,
with no pad; movements are edited from their list. **Borrar esta inversión** is its own
card with the confirm slip.

### Dólares y la tasa

Locked 2026-10-25 by owner delegation (SELLO-106): the owner answered the rules and asked
the executor to design and lock this section; the owner reviews it in the pull request.
Rules are [SELLO-106's](../../testing/investment-examples.md).

- **Currency:** **Pesos | Dólares** in Nueva inversión, under the description, with "Lo que anotes de esta inversión queda en dólares. No se puede
  cambiar después." Not shown when editing.
- **Rate first (owner):** choosing Dólares with no rate adds a pinked slip "¿A cuánto
  está el dólar?" with its date pill below the first contribution, a figure the pad types
  into like the others; Crear inversión waits for it. With a rate, one quiet line names it.
- **USD amounts** read as the app's money text already does for USD (`USD 1.050,50`),
  everywhere inside a USD investment: slip, rows, movements, the chart and entry
  ("Aporte en dólares"). Nothing inside it converts.
- **The pad:** for dollars and the rate, the **000** key becomes the decimal comma,
  spoken "Coma decimal"; at most two decimals. Pesos keep 000.
- **The overview:** converts once per sum at the current rate and says so with a
  `Footnote` "Dólar a 4.150,25 · tasa del 20 oct ›" under the dates line; it opens
  **Dólar**. No USD investment with a value, no rate line. The bar uses values in pesos.
- **Dólar:** reached from that line and from Configuración (**Tus datos → Dólar**, with the
  current rate, between Pagos e ingresos fijos and Respaldo). A pinked slip "1 dólar
  vale" with the rate as the big number and "pesos · tasa del 20 oct", one fact, **Cambiar
  la tasa**, then **Tasas anteriores** as receipt lines, newest date first. No deleting.
  Empty "Todavía no hay tasa" with **Anotar la tasa**; loading and failed read as usual.
- **Cambiar la tasa:** the entry layout, "Pesos por 1 dólar", a date pill (today or
  earlier), the comma pad and **Guardar tasa**.
- **More states:** no values at all ("Todavía no hay valores anotados", rows only, IN35);
  neither gain nor loss (no stamp, neutral "0,0 %", IN15); the four shades repeat in order
  after the fourth investment, with a 2dp gap between segments.
- Large-text and dark frames for the overview with dollars, Dólar and the rate entry.

**Rejected:** a currency switch in the top bar (a fifth control on a four-tab screen);
fetching rates (ADR 0017); a separate rate screen required before any USD investment
(the owner chose asking inside Nueva inversión); converting a USD investment's own
figures (ADR 0017); "US$" (the app's money text already writes `USD`); a fifth to eighth
shade (shades too close to tell apart; the rows carry the values).

**Rejected during the review:** the Metas-band overview with one ticket per investment,
a message on the overview for an investment without a value, a four-way switch in the
top bar, a type pill with a sheet, and withdrawals above the last recorded value.

## Ajustar presupuesto

Locked 2026-10-26 by the owner. Before, **Ajustar presupuesto ›** only scrolled Recibo to
its categories, which with few categories looked like nothing happened. The figures are
the AJ examples in the [financial examples](../../testing/mvp-financial-examples.md).

- **Reached from** the link on Recibo's answer slip, shown only in the month in progress
  while it is Excedido or Revisar. A pushed screen titled **Ajustar presupuesto**, with
  the tabs and no Anotar dock, like Pagos previstos.
- **The answer slip** repeats Recibo's label, stamp, big number and Revisar sentence, without
  the per-day line. Under the tear line: **Límite de octubre** and **Gastado** as receipt
  lines, then, while payments are reserved, Recibo's footnote, which opens Pagos previstos.
- **Tus categorías**, "Toca una para cambiar su límite de octubre.": the categories with a
  limit, overspent first (by remainder, then name), each with "gastaste 90.000 de 50.000"
  and its signed remainder; then **Sin límite**, those without one that spent something,
  since their spending counts against the month. Each row is one button read as one
  sentence. Archived ones come last under **Archivadas**, in grey; each says
  "Archivada · desarchívala para cambiar su límite" and opens its editor instead.
- **The limit sheet** is docked like Se repite, with the Sello pad: **Límite de Café** and
  "Gastaste 90.000 de 50.000 en octubre"; a switch **Solo octubre** (default) / **Desde
  octubre** with the scope in one line; the amount with "antes 50.000"; shortcuts
  **Lo gastado**, **Cubre el mes** (Excedido) or **Cubre tus pagos** (Revisar); one line
  "Tu límite total de octubre pasaría a 1.100.000. Te quedarían 20.000." (or "Quedarías
  justo en el límite.", "Aún te pasarías por …", and the "después de tus pagos" forms);
  and **Guardar solo octubre / Guardar desde octubre**. Saving, a refusal and an unknown
  outcome behave as in entry.
- **The scope (owner):** Solo octubre sets October's own limit and nothing else; Desde
  octubre sets the default from October on and replaces October's own limit (ADR 0001
  D04). A one-off overrun is fixed on Recibo; a repeated one is Resumen's suggestion.
- **Saved:** the sheet closes and the notice names the period, "Límite de Café para
  octubre: 150.000". The screen stays, re-read, so the person sees the stamp change.
- **The live line (owner):** an exception to "no live financial previews", which is about
  entry: like the category editor's total, it says what the month would have left. Every
  figure comes from the domain.
- Default, Revisar, saved, stale, loading and failed-read frames; the sheet typed, with
  each shortcut, Desde octubre and an unknown outcome.

**Rejected:** scrolling to the categories (the old behaviour), a bottom sheet for the
whole screen, opening the full category editor from each row, reserved amounts inside
categories, a shortcut that raises later months silently, and naming next month's limit
in the scope line (it would need a read of a month that has not started).

## Pagos e ingresos fijos

Locked 2026-10-25 by owner delegation (SELLO-114): the owner answered the open choices
and asked the executor to lock the page on those answers; the owner reviews it in the
pull request. Rules are [SELLO-096's](../../testing/recurrence-examples.md); ADR 0017
keeps everything on opening, with no notification or background work.

- **Reached from** Configuración (**Tus datos → Pagos e ingresos fijos**, with the counts)
  and Resumen's income slip (**Pagos e ingresos fijos ›**). Recibo's reserved footnote
  opens the upcoming payments.
- **Creating (owner):** **Se repite** in the locked entry, a row under the note, off by
  default. While off it reads **Repetir este gasto / Repetir este ingreso**, an invitation
  like Agregar nota, never a statement that it repeats (owner, 2026-10-26, #172). It opens
  a sheet: **Cada mes** (a day, 1–31), **Dos veces al mes** (two
  days), **Cada semana o más** (1–52 weeks) or **Cada cierto número de días** (1–365),
  then **Termina: Nunca / En una fecha**, and **Próximo: …** with "Se anota solo en su
  fecha." The start is the entry's date: today or later while Se repite is on, never
  earlier. The button reads **Guardar pago fijo / Guardar ingreso fijo**; a schedule
  starting today is recorded at once and shows in the notice. Confirmation: "Pago fijo
  guardado: Arriendo, el 5 de cada mes".
- **The list (owner, redesigned 2026-10-26 in SELLO-122):** title **Pagos e ingresos
  fijos** without the month, **+** opening the entry with Se repite on for the tab, a
  **Pagos / Ingresos** switch, a pinked slip **Tus pagos fijos de octubre** with the
  month's sum, a bar of what went through against what is still to come and both amounts
  under it (incomes in gain ink; no stamp), then the rows grouped **Faltan en octubre**
  (the day in a pill, Hoy or Mañana when it is), **Ya se anotaron / Ya entraron** (a
  check, "anotado lun 5 oct", the next date), **Más adelante**, **En pausa** (dimmed) and
  **Terminados**, each with its count. Empty: "Nada se repite todavía" with no button; the
  footnote says to mark Se repite when noting one.
- **Changing a schedule:** tapping a row opens the entry layout titled **Pago fijo /
  Ingreso fijo**: amount, category or source, note, Se repite, "Los cambios aplican desde
  mañana. Lo que ya se anotó no cambia.", **Pausar** (with the next date) or, while
  paused, an **En pausa** slip with **Reanudar**, and a danger card **Borrar este pago
  fijo** whose confirm slip says "Los gastos ya anotados no se borran." A schedule paused
  by archiving its category says so.
- **Editing a recorded entry (owner):** saving a new amount, category or note on an entry
  a schedule recorded asks **¿Cambiar también los siguientes?** with **Solo este** and
  **Este y los siguientes** (the schedule changes from tomorrow; recorded ones stay) and
  **Volver a editar**. A date change alone, or an entry whose schedule is gone, asks
  nothing.
- **Upcoming payments:** **Pagos previstos**, a pinked slip "Separado en octubre" with the
  reserved sum and count, then each payment (name, category, date, amount) opening its
  schedule, and **Ver todos los pagos fijos ›**.
- **The notice (owner):** after a catch-up that recorded something, a bar above Anotar on
  Recibo, "Sello anotó 4 pagos fijos" (or "ingresos fijos", or "pagos e ingresos fijos")
  with **Ver**. It stays across openings until **Listo** on **Lo que Sello anotó**, which
  lists each entry with its date and amount, opening its edit. One deleted leaves the
  list; when none is left the bar goes away. Back keeps it.
- **Revisar already over the limit (owner, RE29):** "Pasaste tu límite por 80.000 y tus
  pagos previstos suman 200.000."
- Every screen has default, empty, loading, failed-read, large-text and dark frames.

**Rejected:** a separate schedule editor reached only from the list (the owner chose Se
repite in the entry), editing a recorded entry on its own only or with a link to its
schedule (the owner chose Solo este / Este y los siguientes), a slip on Recibo or a bar
that clears when viewed (the owner chose the bar with Listo), "Te pasaste en … y aún
faltan …" and a signed "Quedan −80.000" for RE29, reaching schedules from Resumen or
Configuración alone, v1.1's swipe to pause or delete (hidden from TalkBack users), and
"Cada 15 días" as a shortcut (every 15 days is not twice a month, SELLO-096).

## Changes to the decision documents

Recorded in [ADR 0013](../../decisions/0013-sello-v2-design.md) on 2026-10-16, with
ADR 0011, ADR 0012, the philosophy and the screen briefs updated to match.

- Stamps: Límite alcanzado → **Al límite**; Sobre el límite → **Excedido**;
  Sin margen libre → **Sin margen**; Por ajustar → **Revisar**.
- Over-the-limit and shortfall link: **Ajustar presupuesto** (was Revisar presupuesto).
- Per-day line wording: **por día** (was "Puedes usar … al día", which clashed with
  the Al día stamp).
- Entry: the Sello pad is the confirmed way to type amounts. This replaces the
  system-keyboard default in [ADR 0011](../../decisions/0011-goal-centered-design.md)
  and the conditional pad wording in the philosophy and the
  [entry brief](../screen-briefs/entry.md). The note uses a composer docked to the
  keyboard rather than a centred dialog.
- Past-month status: **Cumplido** for a month that closed within its limit,
  **Excedido** for one that went over. This settles the past-month copy left open in
  ADR 0012 (v1.1 used "Bajo el límite" / "Pasado").
- Resumen: the hero is the same-days comparison instead of SELLO-022's net-flow
  emphasis; net flow (Ingresos menos gastos) moves to the income slip right below.
  This changes the delivered presentation, not the financial meaning.
- Metas: milestones stay at 25 / 50 / 75 % and the goal, as in v1.1, without projected
  dates; goals are ordered by highest completion; an earned stamp is kept after a
  withdrawal; a withdrawal is limited to what the goal has saved. This settles the
  thresholds, list order and withdrawal questions left open in the
  [Metas brief](../screen-briefs/goals.md).
- Creating a goal: the monthly amount is shown in whole pesos, never rounded further;
  projected stamp months appear only while creating or editing a goal; the goal amount
  uses the Sello pad; deleting a goal is its own card with a confirm slip; a goal reached
  outside Sello, or one that reached 100 % off Recibo, is closed with **Marcar como
  lograda**.
- Completion is manual: reaching 100 % announces the goal and **Marcar como lograda**
  completes it, with the big celebration. The Meta stamp is earned when the goal is marked.
  This reverses the automatic completion recorded in the
  [Metas brief](../screen-briefs/goals.md) and
  [ADR 0011](../../decisions/0011-goal-centered-design.md).
- A completed goal is read-only (no edit, contributions or withdrawals; it can only be
  deleted). Marking a goal as lograda can be undone only from the toast right after the
  celebration; there is no reopening workflow.
- Inversiones: the overview leads with value, a Gana / Pierde stamp and the signed simple
  return since the first contribution; each value shows its recording date; an
  investment without a value is left out of the total with only its row saying so; a
  withdrawal is limited to the last recorded value; a new investment's first value
  defaults to its first contribution. This settles presentation choices left open in the
  [Inversiones brief](../screen-briefs/investments.md); ledger, periods, dividends
  policy and FX stay open.

## Category detail

Category detail retains the [v1.1 reference](../sello-spec.html#s-categoria), with
an owner-approved order change on 2026-10-22: hero → latest expenses → En números →
Cada semana gastas → Un mes normal aquí. [ADR 0015](../../decisions/0015-category-detail-insights.md)
records the approved weekly and completed-month-history rules. Existing no-symbol,
archive-only deletion and current status decisions continue to apply.
