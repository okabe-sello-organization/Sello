# Feature page contract

How to write `docs/design/system/features/<name>.html`. This file is a working note for the
page authors and is deleted before the pull request opens.

## Rules

- **The code is the source.** Read the feature's screens, ViewModels and `strings.xml`;
  never copy from `docs/design/v2/` or reuse its `kit.js`/`sello.css`.
- **English prose; Spanish UI text quoted exactly** as in
  `app/src/main/res/values/strings.xml` (or `design-system/src/main/res/values/strings.xml`),
  in quotes, e.g. "Guardar gasto". Synthetic amounts only.
- **Screenshots are linked, never copied**, with `<figure data-shot>` (below). Use the
  baseline names in `app/src/test/screenshots/` and `design-system/src/test/screenshots/`
  as they are; don't guess a name that has no file. A state with no baseline goes in
  *Gaps*, not in a figure.
- **One file**, `features/<name>.html`, named as in the nav: `recibo`, `resumen`,
  `startup`, `shell` (navigation, tabs, composition), `expense`, `income`, `bulk`,
  `goals`, `investments`, `category`, `recurrence`, `settings`, `backup`, `reset`.
  Set `<body data-page="features/<name>">` so the nav marks it.
- **Sources as `path:line`**, relative to the repository root, e.g.
  `app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:491`.
- **Link shared components** to `../components.html#c-<ComposableName>` (every public
  `@Composable` in `:design-system` has one, e.g. `#c-SelloButton`, `#c-LeaderLine`).
- Use only `../assets/system.css` and `../assets/system.js`; no other scripts, styles,
  CDNs or web fonts.

## Sections, in this order

1. **Purpose** — one line: what the feature lets a person do.
2. **Entry points** — where it opens from (tab, dock, a row, a menu, a deep link,
   start-up), each with the source `path:line` of the navigation call.
3. **Screens** — one `<section>` per screen (or sheet or dialog that has its own state).
   For each:
   - **Screenshots**: one figure per state that has a baseline, light and dark side by
     side (the script pairs them).
   - **Layout**: top to bottom, what is on the screen and which component draws it.
   - **States**: every state that exists in the code — loading, empty, error (nothing
     ever loaded), stale (older figures kept with a notice), busy (a write running),
     unknown outcome (a save whose result is not known), restored draft (after process
     death), refused (the write was rejected) — plus any the feature adds. For each:
     what the person sees, the source `path:line` of the state, and its screenshot or
     "no screenshot". Say when a state does not exist for this screen.
   - **Controls**: each control and what it does (the Action it sends and the result).
   - **Key copy**: the strings a person reads, quoted exactly, with their resource name.
4. **Components** — one table for the feature:

   | Component | Source | How used here | Why |
   | --- | --- | --- | --- |
   | [`SelloButton`](../components.html#c-SelloButton) | `:design-system` `SelloButton.kt` | `kind = Filled`, `loading` while saving | One filled action per screen; loading blocks a double submit |
   | `CategoryGrid` (feature-local) | `app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:184` | 2 or 3 columns from `categoryColumns` | Recibo's own grid of circles |

   *Why* comes from the code (KDoc, comments, parameters), not from taste.
5. **Feature-local parts that look like shared ones** — each feature-local composable
   that resembles a `:design-system` component (or another feature's part): both
   `path:line`s, what differs, and screenshots of both if they exist. These feed
   [findings](../findings.html); add a candidate in the report, don't edit
   `findings.js` unless asked.
6. **Gaps** — screens and states with no screenshot, and anything the page could not
   confirm from the code. Use the `gaps` callout.

## Markup

```html
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Recibo · Sello design system</title>
  <link rel="stylesheet" href="../assets/system.css">
  <script src="../assets/system.js" defer></script>
</head>
<body data-page="features/recibo">
  <header class="page-header">
    <div class="inner">
      <p class="kicker">Feature</p>
      <h1>Recibo</h1>
      <p class="lede">One line: the purpose.</p>
    </div>
  </header>
  <nav id="sello-nav"></nav>
  <main>
    <section id="purpose">
      <h2>Purpose</h2>
      <p>…</p>
    </section>

    <section id="entry-points">
      <h2>Entry points</h2>
      <ul><li>… <code>app/src/main/java/…/ShellScreen.kt:123</code></li></ul>
    </section>

    <section id="screen-recibo">
      <h2>Screen: Recibo</h2>

      <!-- Name only: the script finds -claro/-oscuro, else -cobalto-light/dark-font1.0, else NAME.png -->
      <figure data-shot="recibo" data-module="app" data-caption="Loaded"></figure>

      <!-- Explicit suffixes, e.g. to add Violeta -->
      <figure data-shot="recibo" data-module="app"
              data-variants="cobalto-light-font1.0,cobalto-dark-font1.0,violeta-light-font1.0"
              data-caption="Loaded, both inks"></figure>

      <!-- Explicit files, for names that follow no pattern -->
      <figure data-module="app" data-files="inversiones-foot-cobalto-light.png"
              data-caption="Foot"></figure>

      <!-- A design-system capture -->
      <figure data-shot="bulk-receipt-three-lines" data-module="design-system"
              data-caption="Bulk receipt"></figure>

      <h3>Layout</h3>
      <ol><li>…</li></ol>

      <h3>States</h3>
      <div class="table-wrap">
        <table>
          <thead><tr><th>State</th><th>What the person sees</th><th>Source</th><th>Screenshot</th></tr></thead>
          <tbody>
            <tr><td>Loading</td><td>…</td><td><code>…:12</code></td><td>no screenshot</td></tr>
          </tbody>
        </table>
      </div>

      <h3>Controls</h3>
      <ul><li>…</li></ul>

      <h3>Key copy</h3>
      <ul><li>"Nueva categoría" (<code>recibo_new_category</code>)</li></ul>
    </section>

    <section id="components">
      <h2>Components</h2>
      <div class="table-wrap">
        <table>
          <thead><tr><th>Component</th><th>Source</th><th>How used here</th><th>Why</th></tr></thead>
          <tbody>
            <tr>
              <td><a href="../components.html#c-AnswerSlip"><code>AnswerSlip</code></a></td>
              <td><code>:design-system</code> <code>Paper.kt</code></td>
              <td>…</td>
              <td>…</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section id="look-alikes">
      <h2>Feature-local parts that look like shared ones</h2>
      <p>…</p>
    </section>

    <section id="gaps">
      <h2>Gaps</h2>
      <div class="gaps">
        <ul><li>Loading: no screenshot.</li></ul>
      </div>
    </section>
  </main>
</body>
</html>
```

A figure whose file does not exist renders a red **missing screenshot** box; a page
must have none when it is handed over. The name-only form shows a light and a dark
image, so a state recorded in light only (e.g. `carry-detail-cobalto-light-font1.0.png`)
needs `data-variants="cobalto-light-font1.0"`, with "no dark screenshot" under *Gaps*.
