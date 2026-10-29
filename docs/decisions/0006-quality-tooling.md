# 0006 — Quality tooling and evidence retention

**Owner:** project owner (user) · **Updated:** 2026-10-29
**Status:** approved tooling choices.

## Tools

| Purpose | Choice |
| --- | --- |
| Local and CI gate runner | `scripts/check`, Python standard library |
| Proof that checks passed | The pull request's required `quality` check ([ADR 0021](0021-ci-is-the-only-proof.md)); nothing is retained in the repository |
| Architecture boundaries | `scripts/quality/architecture.py`, with no rule-engine dependency |
| Formatting | ktlint 1.8.0 through ktlint-gradle 14.2.0; `.editorconfig`, Android Studio style |
| Static analysis | Android Lint and ktlint; Detekt deferred pending stable toolchain compatibility |
| Dependency versions | Strict Gradle locking, one `gradle.lockfile` per module |
| Hosted checks | GitHub Actions for the public `okabe-sello-organization/Sello` repository (`Okabe94/Sello` until 2026-10-24; the old URL redirects) |

Until 2026-10-29 each ticket committed its passed report; ADR 0021 retired that. See
the [quality gates guide](../development/quality-gates.md).

## Limits and maintenance

- The architecture checker reads source text; it is not compiler proof. Its header
  documents coverage and limits, and tests exercise both. Gradle enforces module
  dependencies at compile time. Register new modules in the checker explicitly.
- Lockfiles expose changes to direct and indirect dependency versions, not whether
  those versions are safe. Plugin classpaths are catalog-pinned but not locked.
  After an approved dependency change, run `./scripts/update-dependency-locks` and
  review the diff. Adopting Detekt needs a separate compatibility check.
- Keep gate tasks in `scripts/quality/gates.py` and the
  [quality gates guide](../development/quality-gates.md) aligned; a test checks they agree.

## Hosted checks and verification

[ADR 0021](0021-ci-is-the-only-proof.md) owns gate selection and what proves a pass:
G0 for documents, the host gate for Python tooling, G2 with the affected device
suites for other pull requests, G1 for code pushes to `main`, and G3 nightly once
`main` moved, or on request. PR and merge permissions are defined in
[AGENTS.md](../../AGENTS.md). Tool and workflow tests live in `scripts/quality/`.
