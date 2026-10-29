# Screen frame, categories and charts

Contracts of the `:design-system` structure components. They hold no state and no
financial rule; the application supplies selection, the month, formatted exact amounts
and every word.

The app follows the approved [Sello v2](../design/v2/README.md): four tabs (Recibo,
Resumen, Metas, Inversiones) with the Anotar dock above them; its
[components](../design/v2/components.html) list the shared parts.

## Frame and navigation

- `SelloScaffold` arranges itself from the size it is given, through
  `selloWindowLayout(width, height)`:

  | Window | Navigation | Layout |
  | --- | --- | --- |
  | Under 600dp wide, at least 480dp tall | Tab bar and dock under the content | One column |
  | Under 480dp tall, any width | Rail; its "+" replaces the dock | Two even panes |
  | 600–839dp wide | Tab bar and dock | One column capped at 560dp, centred |
  | 840dp and wider | Rail with "+" | 400dp list pane, detail in the rest; side panels 420dp |

- The dock and tab bar are laid out under the content, never over it, and hide while
  the system keyboard is open. The paddings handed to `content` and `secondaryPane`
  already clear the system bars; apply them inside each pane's own scroll container.
- Two panes appear only when the app passes `secondaryPane`. Each pane scrolls alone.
- **A short window is two panes however narrow, and each screen decides whether that
  suits it.** There is no shared minimum width:
  - Recibo beside a category's detail is two columns of reading, so the app shows
    them side by side only from 600dp (`showsDetailBeside`).
  - The expense form's second pane is the keypad, which works at about 160dp. In a
    393×400dp window two panes keep the amount, category, keys and button on screen
    ([capture](images/01-two-columns-393x400.png)); one column
    hides the amount and cuts off the button
    ([capture](images/02-one-column-393x400-rejected.png)).
  Do not give the form Recibo's 600dp rule. If a window too narrow for 48dp keys
  (under about 350dp wide and under 480dp tall) ever matters, it needs its own design,
  not the one-column layout.
- `SelloNavigation` takes any list of destinations (four in the app) and reports every
  selection, including the current tab again; the app owns back stacks and what a
  reselect does. `SelloDock` is the same action for the dock and for the rail's "+".
- `MonthSwitcher` is a title that reports a tap; tabs use `MonthBar` through the
  scaffold's `topBar` slot. Both, `TitleBar` and `PastMonthRibbon` belong to the shell
  (`app/.../navigation/ShellBars.kt`). An optional `ribbon` sits on the band under the
  month bar or the titled bar. `MonthGrid` draws the cells it is
  given; the app decides which months are enabled (never a future one), have data or
  are current, using `EffectiveDates.forSelection`.

## Categories

- A category is known by its icon and name in brand ink. There is no per-category
  colour in any API.
- `CategoryCircle` (design system) clamps only what it draws. `CategoryCell` and
  `CategoryGrid` belong to Recibo (`app/.../feature/recibo/ReciboComponents.kt`).
  `CategoryCell` shows the exact amount
  the app formatted and speaks the full `description` the app wrote, so an overspend
  far beyond the limit still reads exactly. `CategoryGrid` uses two columns, three from
  560dp, one from font scale 1.3.

## Charts

- A `ChartPoint` carries an `id`, a `value` used only for geometry, and `spoken`, the
  mark in words with its exact amount. A selection reports the `id`; the app looks the
  amount up and shows it with `MoneyText`; screens place it in their own heading.
  Nothing is read back from
  a pixel or a height, so two amounts that draw identically stay distinct.
- Every mark is a selectable item with its `spoken` text, and the chart has a `summary`,
  so charts work with a screen reader, keyboard and Switch Access.
- Charts are at least 130dp tall and take at most one `ChartReference`. A period that
  has not happened is `ChartPeriod.Upcoming` and is drawn as an outline. Short figures
  such as `86 mil` are allowed only as `valueLabel` and in a reference label.

## Themes

A theme is an ink with a light and a dark palette; `SelloInk` is the one list of them.
Adding one, say Verde:

1. `design-system/.../theme/VerdePalette.kt`: `VerdeLight` and `VerdeDark`, usually a
   `CobaltoLight.copy(...)`/`CobaltoDark.copy(...)` that changes only brand, paper and
   line colours. Gain, loss, warn and the category colours stay the base ink's.
2. Register it: a `SelloInk.Verde({ VerdeLight }, { VerdeDark })` entry; an
   `InkPreference.Verde("verde")` entry with an id never used before (preferences and
   backups write it, so ids are never changed or reused); an `InkChoices` entry in
   `presentation/appearance` with its `settings_ink_verde` name.
3. Its two rows of reference colours in `SelloPaletteTest`, which checks every palette's
   values and contrast. Component tests run in the base ink only.
