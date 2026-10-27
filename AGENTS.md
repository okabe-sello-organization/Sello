# Sello project instructions

This file owns project permissions and execution rules. Explicit user instructions
for the current task take precedence; otherwise follow these rules.
[HANDOFF](docs/development/HANDOFF.md) is informative and grants no permissions,
even where its text says otherwise. MUST/SHOULD/COULD have the meanings defined in
[ARCHITECTURE.md](ARCHITECTURE.md), which owns technical and safety contracts.

## Before starting

- **MUST** read architecture before planning, implementation or review. For ticket
  work, also read the [tickets README](docs/tickets/README.md), the assigned ticket's
  folder, applicable nested AGENTS files and predecessor decisions/evidence.
- **MUST** use the [execution guide](docs/development/execution-guide.md) and ticket
  brief for prerequisites, owned scope and verification. Tickets own deliverables;
  the approved visual reference owns presentation. Proposed APIs may not exist yet.
- **MUST** inspect Git status, actual code, adjacent tests and available modules;
  identify affected boundaries and checks before editing. Satisfy dependencies and
  approvals first. Ask about unresolved money, privacy, destructive behavior or
  conflicting requirements; never invent policy or weaken acceptance silently.

## Scope and repository safety

- **MUST** deliver focused, complete work that preserves existing journeys. Fix the
  cause; avoid unrelated cleanup, broad renames, speculative abstractions and
  dormant features. Record scope changes before editing; report unrelated findings
  without fixing them.
- **MUST** preserve others' tracked and untracked work. Never discard, overwrite,
  stash, reset or clean it. Stop and resolve overlapping edits rather than guessing.
- **MUST NOT** commit, create/switch branches, merge, push, tag, publish or rewrite
  history without explicit current-task authorization. Inspect the diff and staged files
  before committing; stage only the intended work.
- **MUST** use a short, plain commit title with no body: `docs | Short title` for
  unticketed documentation, or `<Epic ID> - <Ticket ID> | Short title` for ticket
  work, such as `E05 - SELLO-023 | Save appearance preferences`.
- **MUST** treat dependency, toolchain, permission and schema changes as explicit
  task decisions. Use pinned catalog versions, official sources and compatibility
  checks; newest does not mean compatible.
- Delegation requires explicit user authorization. **MUST** assign disjoint write scopes;
  the coordinator owns integration, ticket status and Git operations. Never
  delegate overlapping schema, composition or policy changes.

## Pull requests and merging

- **SHOULD** choose small, reviewable branches and PRs using judgment. A ticket may
  span several PRs; explain each part and remaining work, keeping partial delivery
  In Progress. Ticket branches should use `sello-0NN-<slug>` with useful part suffixes.
- **MUST** follow the [PR template](.github/pull_request_template.md). Commits and
  PRs carry no tool attribution. The owner reviews and approves. Changes reach
  `main` through PRs; merge only with explicit owner authorization, passing
  required checks and a squash merge.
- **SHOULD** update the branch with `main` once, before the final gate. After that,
  leave the branch alone: evidence is bound to the PR's own change set, and what
  lands on `main` is verified by the push run and the nightly. Only a conflict
  GitHub reports needs a manual merge of `main`, a resolution and a rerun with
  `--affected origin/main --retain`.
- **MUST** report and resolve a failed check on `main`, from a push or from the
  nightly, before another PR merges. Work on the next ticket may proceed while the
  check runs.

## Implementation boundaries

- **MUST** follow architecture's module and composition-only data access rules:
  financial rules in domain, codecs/transactions in data, render models/formatting
  in presentation, reusable visuals in design-system. Follow existing conventions;
  use cases must earn their coordination or rule responsibility.
- **MUST** constructor-inject required collaborators, time, dispatchers and scopes.
  No service lookup, ad-hoc financial clock reads or fake-success financial paths.
- **MUST** protect exact money, original-input validation, typed read failures,
  operation IDs, generation/version checks and commit recovery. Never repair bad
  money silently, use floating-point financial truth, turn failed reads into zero
  or empty data, swallow cancellation or blindly retry an uncertain write.
- **MUST** accompany schema changes with exported schemas, retention/migration
  tests and portable-format review. No destructive fallback or invisible history
  cascade. Real Room tests prove atomicity; mocked DAO calls do not.

## Tests and verification

- **MUST** use meaningful red→green tests for behavior changes. Red must expose the
  intended defect, not an import/compile error. Assert observable contracts with
  independently reviewed expected values; never calculate expected money with the
  same logic being tested.
- **MUST** cover applicable overflow, invalid input, duplicate submit, stale versions,
  cancellation/unknown commit, read errors, restart/restoration and rollback. Use controlled
  clocks, virtual time and real Room where required; no arbitrary sleeps, live
  network, global DI or test-order dependencies.
- **MUST** run focused tests first, then the required integrated gate on final
  changes. Follow the [quality gates guide](docs/development/quality-gates.md) for profiles,
  evidence and bootstrap exceptions. For ticket completion, run
  `./scripts/verify-ticket <ticket> --retain` and record fresh Quality run evidence
  before Review/Done. The required local run is the host gate G1 for every ticket,
  G3 whole for a release ticket; a ticket's device suites run hosted, on the real
  merge result, before anything merges. Run the device tests you wrote or changed
  as focused tests. `--gate G2` or `--affected <PR base>` runs device suites locally
  too when a change is risky on a device; retain the explicit scope. Pure
  documentation advancing no ticket uses `--merge-candidate <base>` (G0), never
  a fabricated ticket or a ticket downgrade. Follow ADR 0014 and the quality gates
  guide for the selection rules. Never weaken tests, disable checks, apply a blanket warning
  baseline or describe unselected suites as executed/passed.
- **MUST** distinguish configured, compiled, executed and passed. Assembly is not a
  device test; rotation is not process-death restoration; CI signing is not upload
  signing. Missing SDK, device or credentials means an incomplete required gate.
- Agents may use, reset and wipe emulators as needed without additional permission.
  **MUST** identify the target before device commands. **MUST NOT** perform
  destructive actions on USB or other physical devices without an explicit user
  request and permission, including uninstalling apps, clearing data or resetting
  the device. Test fixtures must use synthetic data.
- **SHOULD** use the environment in the [setup guide](docs/development/setup.md),
  following the verified [toolchain decision](docs/decisions/0005-toolchain.md).

## UI, developer tools and privacy

- **MUST** reuse Sello tokens/components and immutable State/Action/Event/Root
  patterns. Cover loading/empty/error/busy and restored drafts, exact accessible
  amounts, reduced motion, adaptive layouts and large fonts. Extend actual catalog
  examples/previews for new components; do not copy implementations.
- **MUST** extend applicable sandbox scenarios with financial changes: real command/
  storage paths, isolated synthetic histories and distinct financial/audit/monotonic
  clocks under architecture §9. Developer tools, catalog code and fixtures must be
  absent from release artifacts.
- **MUST NOT** put personal financial fixtures, raw sensitive logs, credentials or
  signing material in source/reports. Follow architecture's private-storage,
  bounded-input, minimal-permission and plaintext-file disclosure rules. Review
  legacy artifacts as evidence; never copy layers or personal data wholesale.

## Ticket and documentation maintenance

- **MUST** keep each ticket's `ticket.md` canonical and edit only the ticket's own
  folder for ticket work. Never invent completed work or change acceptance to fit
  output. Require full evidence and designated review; distinguish self-review from
  independent approval.
- Only the final PR proposes the ticket Done after full acceptance and verification;
  merging is review approval. If it completes the epic, propose the epic Done too.
  Review is for waiting outside the merge. Never invent approvals, dates or hosted runs.
- **MUST** run `python3 scripts/quality/tickets.py check` after ticket edits and
  `python3 scripts/quality/links.py` after moving, renaming or deleting documents.
  Nothing generated is committed; `tickets.py status` prints the board on demand.
- **MUST** record significant architectural deviations in a short ADR and update
  affected guardrails. Unticketed work authorizes no new tickets or ticket-status changes.

## Handoff

- **MUST** write the ticket's `handoff.md` with its final PR: what changed, where,
  and what is still open, in three to six lines. Refresh `docs/development/HANDOFF.md`
  only in the PR that closes an epic, keeping its app summary and remaining work
  accurate. No copied test logs, delivery records or permission rules.
- **MUST** report changes, relevant paths, exact verification/results, warnings,
  failures, skipped gates and remaining decisions. Name the tested revision and
  relevant uncommitted changes; never present a dirty tree as a tested commit.
- **MUST** leave completed code work buildable. If blocked, state progress, the
  concrete blocker and next action; never call incomplete acceptance Done or claim
  unexecuted CI, Jira import, device tests or review.
