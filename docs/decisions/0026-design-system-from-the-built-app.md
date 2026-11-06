# 0026 — The design system is written from the built app

**Owner:** project owner (user) · **Date:** 2026-11-05
**Status:** owner decision of 2026-11-05 (E31). Supersedes
[ADR 0013](0013-sello-v2-design.md).

## Context

Sello v2, the v1.1 snapshot and the screen briefs described screens before they were
built. The app has since moved on, so the three disagreed with it and with each other,
and agents could not tell which one to follow.

## Decision

- The pages under [docs/design/system](../design/system/index.html) are the design
  source: foundations, the components catalog and one page per feature.
- They are written from the built app and show its real JVM screenshots, 412x891 dp at
  mdpi (a Galaxy S25 Ultra, [ADR 0022](0022-jvm-screen-and-screenshot-tests.md)), linked,
  not copied. Where the app and an older design differ, the app wins.
- Inconsistencies are logged on [findings.html](../design/system/findings.html); the
  owner decides the canonical version per finding, and tickets fix the code.
- `docs/design/v2/`, the v1.1 snapshot `docs/design/sello-spec.html` and
  `docs/design/screen-briefs/` are deleted; git history keeps them. Their open questions
  moved to the [design philosophy](../design/philosophy.md#open-implementation-decisions),
  which stays.
- [Architecture](../../ARCHITECTURE.md) and the ADRs still own money, dates, recovery
  and safety; the pages approve no financial rule.

## Consequences

Links to the deleted files in Done tickets and ADRs are GitHub permalinks at `90662770`.
A visual change follows the pages; a change they do not cover needs a mock first.
