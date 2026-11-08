# SELLO-E38 — Guía dentro de Sello

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** A new person finds their way around Sello from the real screens: quiet one-line nudges when a feature becomes useful, a **?** on cards and underlined terms that explain what the app's rules mean, and a Guía de Sello in Configuración for everything else.
- **Exit:** SELLO-204 to SELLO-213 are Done: the guidance parts live in `:design-system`; the Guía de Sello opens from Configuración; Recibo, category screens, Resumen, Ingresos, Metas and Inversiones carry their **?** and underlined terms; the empty Recibo offers starter categories; the Guía hint, the monthly backup reminder and the feature nudges show as decided; backups carry what was seen; the design pages show the new parts.

## Decisions

**Owner direction (2026-11-08):** given in chat on the orchestrator's proposals and
mocks; the approved pieces are collected in the [mock](attachments/guidance-mock.html).

- **No first-run questionnaire, no setup wizard, no welcome slides, no dimmed overlays
  with floating tips.** "If I downloaded a budgeting app I know what I'm getting into."
  Guidance lives inside the real screens, for everyone alike.
- **Starter categories** sit inside the empty Recibo as chips. They carry a name and an
  icon only, **never an amount**: Sello does not tell anyone how much to spend.
- **Nudges** are one quiet line in the page, like the "¿Dudas?" row: never floating,
  never covering anything. **No gesture tips**: long-press and swipe are shortcuts to
  actions a tap already reaches. Nudges cover: the Guía hint, the backup reminder,
  Anotar varios, pagos fijos, arrastre, **deleting**, **working with investments** and
  **the goal shown on Recibo**.
- **Card ?**: a solid soft disc (`paperDim`) with a dark glyph and no ring, right after
  the card's title, top-left (option B). Not brand-coloured, not at the card's foot.
- **Underlined terms**: a dotted underline in the outline grey, lighter than the **?**.
  The **?** explains the whole card; an underline explains one word. A card title uses
  the **?** only.
- **Explanations translate the rules the code applies today** into plain Spanish that
  is easy to digest; the owner reviews the copy in each pull request.
- **Placement:** where the **?** and underlines go, and where they don't (Anotar, Tu año, Día a
  día, Récords, goal cards, Configuración), as listed in SELLO-207 to SELLO-209.
- **Guía de Sello lives in Configuración only** (no **?** in the header). A hint tells
  people where it is. v1 has the mocked sections and **no search**; it is built to
  grow into pro tips and a full written tutorial later (not in this epic).
- **Backup reminder** is as quiet as the "¿Dudas?" row: a short line of facts, a small
  tonal pill on the right (option B) that takes the person to Respaldo, and a quiet
  "No volver a mostrar". No card, no big button. It appears about once a month.
- **People already using Sello** when the update lands see only the Guía hint and the
  backup reminder, not the new-user nudges.
- **Restablecer does not clear** what was seen or dismissed. **Backups carry it** (a
  backup-format change; older versions still restore).
- **"Al día" means two things** (Recibo's on-track stamp, category detail's per-day
  figure): a separate copy issue, not hidden behind an underline.
- Orchestrated run ([orchestration](../../development/orchestration.md)): subagents and
  separate review agents are approved, and the owner delegated every merge except a
  release.

**Orchestrator's calls (2026-11-08), open to the owner:**

- Configuración is the screen's real name (`settings_title`), so the hint says
  "Configuración", not "Ajustes". The Guía is the first row there.
- The pill says **"Ir a Respaldo"**: it opens Respaldo, it does not start a backup.
- One nudge at a time on a screen and at most one nudge a day across the app; the
  backup reminder counts as one. A nudge closes for good when its action is done or
  its ✕ is tapped.
- The backup reminder shows when there is at least one recorded movement and 30 days
  have passed since the last backup (or since the first movement, with no backup). It
  stays for that day, then waits 30 days unless a backup is made. A backup clears it.
- Restoring merges what was seen: a nudge counts as seen if the phone or the backup
  says so, and "No volver a mostrar" holds if either says so. A backup without these
  (older, or made while they could not be read) whose data has any expense, income, goal
  or investment marks the six new-user nudges seen in the same merge; the Guía hint and
  the backup reminder stay as the phone has them. Without such data it keeps what the
  phone has.
- "Already using Sello" means the phone has any expense, income, goal or investment the
  first time the new version starts.
- Thresholds for the feature nudges are in SELLO-212 and SELLO-213.
