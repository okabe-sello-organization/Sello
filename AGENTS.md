# Sello — working rules

These rules govern how agents work in this repository. The owner's instructions for
the current task win over them. [ARCHITECTURE.md](ARCHITECTURE.md) owns the technical
contracts; this file owns the process. Rules change only in their own pull request,
approved by the owner: propose a change in your report, never make it inside other work.

## Starting

- Read this file, ARCHITECTURE.md, your ticket or issue, and the *Decisions* section
  of its epic README. Look up other docs when the work touches them; don't read ahead.
- Check `git status`, then the actual code and tests around the change.
- **Clear ticket:** start. **Unclear or risky** (money, privacy, data-deleting
  behaviour, conflicting requirements, or a claim that may not be true): post a short
  brief and wait. A bug brief verifies the claim against the code first.
- **Changes to how something looks or is laid out:** show a mock for approval first.
- **Decisions the ticket leaves open:** ask only about money, privacy, data-deleting
  behaviour or conflicting requirements. Decide the rest yourself and record the
  choice in the pull request.

## Scope

- Do the ticket and fix causes. No unrelated cleanup, broad renames, speculative
  abstractions or dormant features.
- An unrelated defect you notice: don't touch it; open a GitHub issue labelled `bug`.
- Never discard, overwrite, stash, reset or clean work that isn't yours. Worktrees
  share branches and the stash.
- Dependency, toolchain, permission and schema changes are decisions to ask about.
  Use pinned catalog versions and `./scripts/update-dependency-locks`.

## Money and data

ARCHITECTURE §4–§5 have the detail. The short version:

- Money is `Money`: checked whole minor units (COP in pesos, USD in cents). Overflow is
  a typed error, never wrapped or clipped. `BigDecimal` only for rates, percentages and
  conversions, each with an explicit rounding mode. Never `Double` or `Float` money.
- Validate the original input; never repair bad money silently.
- A failed read keeps the last good figures, marked as older, with a notice; an error
  screen only when nothing ever loaded. Never turn a failure into zero or empty data.
- Money writes carry an operation ID and are safe to replay. An unknown outcome is
  checked before any retry; cancellation is rethrown, never swallowed.
- A schema change comes with a migration, the exported schema and a real-Room test
  showing old data survives. No destructive fallback.
- No personal financial data, credentials, signing material or raw sensitive logs in
  source, tests, fixtures, screenshots or reports. Synthetic data only.

## Code

- Module ownership per ARCHITECTURE §2: financial rules in `:domain`, storage in
  `:data`, screens and render models in `:app`, shared visuals in `:design-system`.
  Constructor-inject collaborators, clocks, dispatchers and scopes.
- Screens use an immutable State, Actions in, durable results kept in State (no event
  channels), a ViewModel, and a Root/Screen split.
- Every new or changed screen handles loading, empty, error and busy states, restored
  drafts and 2× font. Reduced motion and tablet/landscape layouts are separate tasks
  unless the ticket asks for them.
- Reuse a design-system component when one fits; otherwise build it in the feature and
  move it to `:design-system` when a second feature needs it.
- Add a sandbox scenario only when it helps the owner test a new flow or failure by
  hand. Nothing debug-only ships in release.

## Tests

- **Bug fix:** first a test that fails for the reported reason, then the fix.
- **Feature:** tests for the new behaviour, in any order.
- **Money, storage or recovery change:** cover overflow, invalid input, double submit,
  stale version, cancellation or unknown commit, failed read, restart or process
  death, and rollback. Say in the pull request which items don't apply.
- Expected money values are literals worked out by hand, never computed with the code
  under test. Labels and formatting may reuse production helpers.
- Test at the cheapest layer that proves it: domain rules and ViewModels on the JVM;
  storage on real Room on a device (a mocked DAO proves nothing about atomicity).
- Device tests cover critical paths only. A feature adds none by default: open a task
  for it, or add one when the feature creates a new critical path.
- Controlled clocks; no sleeps, network, global DI or test-order dependence. A flaky
  test is a defect: fix its cause. Never retry, skip, weaken or disable a test or check.
- Say exactly what ran: configured, compiled, executed and passed are different things.
- Devices: create your own new emulator for device work. Never use an emulator that is
  already running or a physical phone unless the owner asks.

## Pull requests and merging

- One pull request per ticket or part of one. Branches: `sello-0NN-<slug>` for
  tickets, `fix-<slug>` and similar for issues.
- Commit titles have no body: `<Epic> - <Ticket> | Short title` for ticket work, or
  `fix | …`, `test | …`, `docs | …`, `ci | …`, `app | …`. No tool attribution in
  commits or pull requests.
- Follow the [pull request template](.github/pull_request_template.md). A ticket's
  final pull request sets it Done (and its epic, if it's the last); merging approves it.
- The pull request's `quality` check is the proof that checks passed; see
  [quality gates](docs/development/quality-gates.md). Run focused tests while working.
- Update a branch from `main` only when GitHub reports a conflict.
- **Review by risk.** Changes to money rules, storage or schema, recovery, privacy or
  CI get an adversarial review by a separate agent ([orchestration](docs/development/orchestration.md));
  UI changes a lighter review with screenshots; documentation only the automated checks.
- **Merging** is a squash merge. The agent merges documentation, UI and test changes
  itself once checks are green, the review approves and no comment from the owner is
  open. The owner merges changes to money rules, storage or schema, privacy, CI, these
  rules and releases. Never `--admin` or bypass a ruleset.
- A red check on `main`, from a push or the nightly run, is fixed before anything else
  merges.
- Subagents only when the owner asks or in an orchestrated run, each with its own
  files to write.

## Records

- The pull request description says what changed, why, and the proof (tests,
  screenshots).
- A lasting architecture or policy decision gets a short ADR in `docs/decisions/`.
- The pull request that closes an epic refreshes [HANDOFF](docs/development/HANDOFF.md).
- Your report gives the changes, paths, exact verification results, what didn't run
  and open decisions. Never claim a run, review or approval that didn't happen.
