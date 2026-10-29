# Sello — working rules

These rules govern how agents work in this repository. The owner's instructions for
the current task win over them. [ARCHITECTURE.md](ARCHITECTURE.md) owns the technical
contracts; this file owns the process. Rules change only in their own pull request,
approved by the owner: propose a change in your report, never make it inside other work.

## Starting

- Read this file, ARCHITECTURE.md, your ticket or issue, and the *Decisions* section
  of its epic README. Look up other docs when the work touches them; don't read ahead.
- Check `git status`, then the actual code and tests around the change.
- Say in one line whether the ticket is clear or unclear/risky. **Clear:** start.
  **Unclear or risky** (money, privacy, data-deleting behaviour, conflicting
  requirements, or a claim that may not be true): post a short brief in simple English
  and wait. A bug brief verifies the claim against the code, then says whether it's
  real, whether to fix it, the effort from 0 to 10, and challenges the proposed fix. A
  feature brief gives the gist, old versus new, the approach, and each pitfall with how
  to avoid it.
- **Changes to how something looks or is laid out:** show a mock for approval first.
- **Decisions the ticket leaves open:** ask only about money, privacy, data-deleting
  behaviour or conflicting requirements. Decide the rest yourself and record the
  choice in the pull request.

## Scope

- Do the ticket and fix causes. No unrelated cleanup, broad renames, speculative
  abstractions or dormant features.
- An unrelated defect you notice: don't touch it; open a GitHub issue labelled `bug`.
- Never discard, overwrite, stash, reset or clean work that isn't yours; stop and ask
  when edits overlap. Worktrees share branches and the stash.
- Changes reach `main` only through pull requests. Never push to `main`, force-push a
  shared branch, rewrite published history, tag or publish without the owner's go.
  Check the diff and stage only the intended files before committing.
- Never change a ticket's acceptance criteria to fit what you built; propose the change.
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
- A schema change comes with a migration, the exported schema, a real-Room test
  showing old data survives and a check of the backup format (ARCHITECTURE §7). No
  destructive fallback and no history deleted as a side effect.
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
- **Review by risk.** Money rules, storage or schema, backup and restore, recovery,
  privacy, and CI or gates get an adversarial review by a separate agent
  ([orchestration](docs/development/orchestration.md)). Other code changes (screens,
  components, ViewModels, tests, sandbox, build) get a lighter review by a separate
  agent, never the author, with screenshots for visible changes. Documentation gets
  only the automated checks.
- **Who merges.** The owner merges changes to money rules, storage schema, privacy,
  CI, gates, build or dependencies, these rules, and releases. The agent merges
  everything else. A pull request takes the strictest review and merge of anything it
  touches. In an orchestrated run the owner delegates every merge except releases.
- **Merging** is a squash merge, once every check is green, the required review
  approves and no comment from the owner is open. Never `--admin` or bypass a ruleset.
- A red check on `main`, from a push or the nightly run, is fixed before anything else
  merges.
- Subagents only when the owner asks or in an orchestrated run, each with its own
  files to write.

## Records

- The pull request description says what changed, why, and the proof (tests,
  screenshots).
- A lasting architecture or policy decision gets a short ADR in `docs/decisions/`.
- The pull request that closes an epic refreshes [HANDOFF](docs/development/HANDOFF.md).
- Share the pull request's details (what changed, what the owner will see, the proof,
  what isn't covered) as soon as it opens; report the checks' result afterwards.
- Your report gives the changes, paths, exact verification results with the tested
  revision (a dirty tree is not a tested commit), what didn't run and open decisions.
  Never claim a run, review or approval that didn't happen.
- Leave finished code buildable. If blocked, say what's done, the blocker and the
  next step.
