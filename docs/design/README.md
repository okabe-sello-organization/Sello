# Sello design references

**[Sello v2](v2/README.md) is the visual source of truth**
([ADR 0013](../decisions/0013-sello-v2-design.md)). It holds the six locked screens,
their states and the [shared components](v2/components.html), with every decision and
rejected alternative in its README. Serve the repository with
`python3 -m http.server 8765 --bind 127.0.0.1` and open
`http://127.0.0.1:8765/docs/design/v2/home.html`.

Behind it:

- The [design philosophy](philosophy.md): purpose, the motto **Tu presupuesto al
  servicio de tus metas** and screen principles, from the 2026-10-15 owner interview.
  [ADR 0011](../decisions/0011-goal-centered-design.md) records what it supersedes.
- The [screen and interaction briefs](screen-briefs/README.md): the intent, examples
  and open policy behind each screen.
- [Architecture](../../ARCHITECTURE.md) and ADRs 0001–0012 own financial and recovery
  contracts. v2 numbers are synthetic and approve no financial rule.
- Completed epic acceptance and evidence are historical; the code is the source of
  truth for what is delivered today.

## Preserved v1.1 visual reference

Open [sello-spec.html](sello-spec.html) in a browser. The reference is also readable
as text; its HTML anchors identify foundations, components and individual screens.
If needed, serve it from the repository with `python3 -m http.server 8000` and open
`http://localhost:8000/docs/design/sello-spec.html`.

This is a content-preserving snapshot of the user-provided Sello v1.1 reference, captured
2026-10-08. It is a **design document**, not application code. Its Google Fonts
links need internet for faithful browser typography; app fonts must be bundled
with reviewed licenses. Widgets, legacy route names, migrations, sample numbers
and Kotlin sketches do not override the [architecture](../../ARCHITECTURE.md).

Source: `/home/okabe94/.t3/worktrees/FinanceTracker/t3code-5c84db69/docs/design/ui-overhaul/sello-spec.html`.
Source SHA-256: `400683769da6d648a0c9eff8ab79e43eeb7713f75dfb0ba61527823198785c00`.
Snapshot SHA-256: `77ad0040e382c892c03ac05d1748d11e897b5e2b26e593ece5fcc697443ca097`.
The snapshot normalizes the source's missing terminal newline; content is otherwise
identical. Verify it with `sha256sum docs/design/sello-spec.html`; design-content
changes require a recorded product decision and updated provenance.

ADR 0011 and ADR 0013 supersede only the listed direction/presentation decisions; this
snapshot remains byte-for-byte unchanged, with the hashes above still valid. What v2
does not show continues to follow it. The no-peso-symbol override in
[ADR 0007](../decisions/0007-no-peso-symbol.md) also remains in force.

Tickets link this repository copy, so the original worktree is not required.
