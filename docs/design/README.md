# Sello design references

- **[The design system](system/index.html)** is the design source
  ([ADR 0026](../decisions/0026-design-system-from-the-built-app.md)): the
  [foundations](system/foundations.html), the [components](system/components.html), one
  page per feature and the [findings](system/findings.html) the owner decides. It is
  written from the built app with its real JVM screenshots; where an older design and
  the app differ, the app wins. Serve the repository with
  `python3 -m http.server 8765 --bind 127.0.0.1` and open
  `http://127.0.0.1:8765/docs/design/system/index.html`; the screenshots load from the
  modules' test folders, so open the pages from the repository, not a copy.
- **[The design philosophy](philosophy.md)**: purpose, the motto **Tu presupuesto al
  servicio de tus metas**, screen principles and the open implementation questions.
  [ADR 0011](../decisions/0011-goal-centered-design.md) records what it supersedes.
- **[Architecture](../../ARCHITECTURE.md) and the ADRs** own money, dates, recovery and
  safety. Figures in the pages are synthetic and approve no financial rule; the
  no-peso-symbol rule is [ADR 0007](../decisions/0007-no-peso-symbol.md).

Sello v2, the v1.1 snapshot and the screen briefs were deleted on 2026-11-05; git
history keeps them.
