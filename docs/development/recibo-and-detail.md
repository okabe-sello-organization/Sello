# Recibo and category detail

Decisions: [D03, D06](../decisions/0001-mvp-contract.md) ·
Design: [Sello v2 Recibo](../design/v2/home.html); [Detalle de
categoría](../design/sello-spec.html#s-categoria) (v1.1; not redesigned in v2) ·
Builds on the [monthly snapshot](monthly-snapshot.md), [Anotar](expense-entry.md) and
the [app shell](app-shell.md).

The screens that answer "how much is left, and where did it go". Their rule: every
figure comes from one snapshot of the month, and no screen adds, subtracts or judges
anything itself.

## One snapshot, one mapper

`ReciboRenderModel` turns a `SnapshotState` into what both screens draw. It picks the
sentence, formats the amounts the snapshot already holds and names the verdict:

Recibo's answer slip (the v2 `AnswerSlip`):

| Month | With limits | Without any limit |
| --- | --- | --- |
| In progress | "Restante del presupuesto" and the status Al día, Al límite or Excedido; the remainder, signed when over (−43.700); a tear line, then what can be spent a day ("25.350 por día", 0 when nothing is left) and "16 días, incluido hoy" ("1 día" on the last day) | "Has gastado este mes" the total, "Sin límite", nothing per day |
| Finished | "Restante del presupuesto", Cumplido or Excedido, the signed remainder and "Mes cerrado · gastaste 920.000 de 1.000.000."; nothing per day | the same as above |

The categories and their detail keep sentences that say which way the amount goes
("Te quedan", "Te pasaste por", "Te sobraron"), so their amount carries no sign; a
category's cell keeps the minus.

A finished month uses the limits that applied in that month, because the snapshot
does; its closing line takes the spent amount and the limit from the same budget as
the remainder.

The verdict is always a word, on screen and in what a screen reader says, never a
colour alone, and a minus is read as "menos". Each block is read as one sentence with
its exact amounts: "Restante del presupuesto: menos 43.700 pesos. Excedido.", "0 pesos
por día. 1 día, incluido hoy.", "Alimentación. Te quedan 287.600 pesos de 900.000
pesos. Al día."

In the month in progress, an Excedido answer slip ends with **Ajustar presupuesto ›**
(SELLO-067), read "Ajustar presupuesto, ir a tus categorías". It is never offered for a
finished month. It opens nothing: Recibo scrolls so the categories card is at the top
(at once with reduced motion) and focus moves to its **Tus categorías** heading; each
category opens its detail and limit editor as before. Beside a detail in a wide
window, where the card is already in view, it only moves focus. A month can be over
its limit with no category over its own, through categories without a limit; the card
lists every category, so the link still helps.

How full a category's circle is drawn is the only number that is not exact. It is
geometry: it is never shown as a figure, compared or turned back into money.

The approved [v2 Recibo](../design/v2/home.html) also adds a review invitation early
in the month; it is not implemented yet.

## Order of the categories

`CategoryOrderPolicy` (D06). Configuración offers Más usadas (the default),
Alfabético, Monto gastado, Límite and Último gasto agregado, each from highest to lowest or
lowest to highest, using only the expenses of the month on screen. Whatever the choice,
categories with a finite limit that month come first, those with no expenses that
month come last within each group, and ties go by name ignoring case and accents,
then by identifier. If the setting cannot be read, the last good one is used, or Más
usadas.

## Category detail

### Owner-approved revision — 2026-10-22

The current task expands the original budget-and-list detail to the reference's
real category insights. Reading order is **hero → latest expenses → En números →
Cada semana gastas → Un mes normal aquí**. The owner explicitly changed the
reference's section order. The immutable v1.1 snapshot is not edited.

Latest expenses is a four-row preview with a separate searchable, sortable,
bounded full list. Editing moves to the toolbar. Exact financial rules and
worked examples are recorded in [ADR 0015](../decisions/0015-category-detail-insights.md).
This is owner-authorized unticketed work; no ticket or status is created or changed.

The hero follows Recibo's financial rules, scoped to the category. Its label and
stamp sit above the category circle and exact amount; spent/limit share a line.
An archived category is stamped **Archivada** in place of its verdict.
Below the tear line are the category daily allowance (current finite budget only)
and a same-days comparison when a previous positive baseline is known.
They use equal-width columns: **Para / 9 días**, **Al día / amount**, and
**Frente a sep / percentage**. The day count includes today in the approved
calculation, but its visible text has no extra phrase. A higher recorded comparison
uses the warning ink, a lower one the gain ink, and zero the ordinary ink. The
direction comes from the domain comparison, never by parsing the displayed text.

The category-detail type audit follows the v1.1 CSS, using the bundled Schibsted
font and Android sp at the system's selected scale:

| Use | Reference size | App role |
| --- | --- | --- |
| Category toolbar title | 19 | titleLarge |
| Hero amount | 44, fitted only when required by width | displayMedium |
| Spent/limit and lower hero labels | 12.5 | labelMedium |
| Lower hero values | 16, weight 800 | titleMedium |
| Latest-expense and En números headings | 16, weight 800 | titleMedium |
| En números values | 17, weight 800 | labelLarge |
| Chart headings | 12.5 | labelMedium |
| Weekly/historical totals | 23, weight 800 | headlineSmall |
| Expense rows / dates | 14 / 12 | bodyLarge / labelSmall |
| Chart legends / axis labels | 12 | labelSmall |

Global type definitions already match their reference roles. Feature roles are
verified separately; system font scaling remains enabled.
`MoneyText` retains tabular digits even with a caller-supplied text style, as the
reference's `.mny` rule requires; custom label sizing must not crowd group separators.

`CategoryInsightsPolicy` owns the statistics, week buckets, planning allowance and
completed-month history under ADR 0015. `RoomCategoryInsightsSnapshots` reads seven
months of category budget states and bounded expense pages in one transaction and
computes off main. Historical months retain their own limits. The feature only
attaches insights to a hero with the same month, generation, revision, date and
zone; an unmatched refresh remains loading. A statistics failure is explicit and
retryable, rather than zero or missing-success content.

The owner-approved section order is:

1. Hero.
2. **Últimos gastos**: four newest expenses, with tap to edit, swipe/accessible
   actions to delete and the existing durable undo. **Ver los N** opens the complete
   expense browser; the preview does not expand in place.
3. **En números**: a two-column grid of count/days and **En promedio** (average
   expense), then largest expense and weekday with the highest recorded total.
   Each label sits above its value; the largest expense's note/date sits below its
   amount. It uses the reference's receipt, price-tag, upward-arrow and calendar icons.
4. **Cada semana gastas**: completed-bucket average, actual weekly spending and the
   current bucket's still-available allowance drawn as a separate striped portion.
5. **Un mes normal aquí**: average of up to six completed configured months,
   including known zeros, and a chart with each month's own limit. When the selected
   month is current, append its actual spending and live limit from the same snapshot
   as the hero; edits refresh that point without changing completed months. This point
   is marked current and does not enter the average or its completed-month count.
   Hidden until two completed months are known; no extrapolation before configuration.

The preview's rows still come from `ExpenseReads.inCategory`; neither its totals
nor its count come from a page. A finished month or archived category offers no
“Anotar aquí”. Empty, failed, loading and stale reads retain their distinct states.

The full browser uses `CategoryExpenseSearch`: search by note and date/value/name
ordering apply in storage **before** bounded pagination. The query (at most 60
characters) and order survive process recreation through SavedStateHandle. A new
filter starts a new page; a failure never reuses rows from another filter, category,
month or generation. Pages whose revision no longer matches the observed budget
are withheld until refreshed. The list is lazy, and more-page failures retain the
already listed rows with retry. Deletion carries the listed generation and record
version through the same `ExpenseCorrections` recovery/undo path as the preview.
The matching record count is read in the same transaction as each search page;
the footer and accessibility state say how many are loaded. Stable lazy-list keys
retain the reading position while a new save refreshes the loaded rows.

## Changing the category

The edit icon in the detail's toolbar (or its heading in a two-pane layout) opens
[the form on that category](category-editor.md#changing-an-existing-category) for the
month on screen. A long press on a category's cell in Recibo offers the same in a
menu, and a screen reader is offered it by name; pressing a cell still only opens it.

The detail of a month that is over always says that its limit can still be corrected
and that other months do not change. Until dismissed, it also shows the first-time
announcement "Puedes corregir el límite de este mes anterior.", alone or beside Recibo. An archived category stays listed, marked
"Archivada", in every month where it has a limit or expenses.

## Correcting and deleting an expense

Each listed expense is an `ActionLine`:

| How | What it does |
| --- | --- |
| Press | Opens [the form on that expense](expense-entry.md#correcting-a-recorded-expense) |
| Long press | A menu with "Editar" and "Borrar" |
| Swipe towards the start, past 40 % of the width | Deletes it |
| Screen reader | The actions "Editar" and "Borrar", by name |

Nothing depends on a gesture alone. A line under the count always says how: "Toca un
gasto para corregirlo. Para borrarlo, deslízalo o mantenlo presionado."

- **A deletion is of the row as listed.** The view model keeps each listed expense's
  version and the generation it was listed from, and `ExpenseCorrections.delete`
  sends exactly those. A row that is out of date is refused and said so above the
  rows ("Ese gasto cambió mientras tanto. No se borró; revísalo."); it is not deleted
  as something it no longer is.
- **Nothing is taken off the screen by hand.** The row leaves, and comes back after an
  undo, when the month is read again, like any other change. The figures move with it.
- **The offer to undo** is the frame's, not this screen's: see
  [the app shell](app-shell.md#what-is-said-about-a-correction).

## When a read fails

| What happened | Recibo | Detail |
| --- | --- | --- |
| Nothing could be read | error with retry, no figure, never the first-run slip | error with retry |
| A refresh failed after a good read | the last good figures under a notice that they may be out of date, with retry | the same |
| The category has nothing that month | (not listed) | "Nada aquí este mes" |

## One screen or two

`showsDetailBeside`: in a window laid out in two columns and at least 600dp wide,
the detail sits beside Recibo, the chosen category is marked as selected, and before
any is chosen the first one is shown. Anywhere else an opened category takes the
screen under its own title, Back returns to Recibo, and the dock records in that
category. For an archived one the dock stays in place, disabled, and reads
**Archivada** beside a prohibited sign; on a rail its "+" becomes that sign. A window that is short and narrow, such as half a phone's screen, shows
one at a time even though the layout rules call it two-column.

The title over a detail, and the detail under it, are always of the category that
was chosen (`navigation/DetailIdentity.kt`). The detail is asked for by identifier
and read a moment later, so meanwhile the name comes from Recibo's own list, which
is how the category was chosen in the first place; once the detail is read its own
name is used. A detail still holding the category shown before is treated as
loading. "Categoría" appears only when neither screen has read the category yet,
such as just after the app is restored. Nothing is cached and no name travels
through navigation.

`SelloScaffold` keeps both panes and the dock clear of a system bar or cutout at the
side of the screen, as it already did at the top and bottom.

## Not here yet

Swiping between months and the count-up and stamp-landing motion remain deferred.
Category comparison, weekly spending and historical charts are described above.
