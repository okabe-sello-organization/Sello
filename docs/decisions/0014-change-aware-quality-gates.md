# 0014 — Change-aware quality gates

**Owner:** project owner (user) · **Date:** 2026-10-22, amended 2026-10-24
**Status:** superseded by [ADR 0021](0021-ci-is-the-only-proof.md), which keeps its
device selection and drops its evidence and local-gate rules.

This record introduced the Python/Git-only G0 for documentation and the reverse
dependency map for device suites (app → app; catalog → catalog; data and domain →
data, app; design-system → design-system, app, catalog; shared or unknown paths →
all). Its amendments of 2026-10-24 bound retained reports to a change fingerprint so
a moving `main` did not stale them, and made the required local run the host gate G1,
leaving device suites to the hosted check. ADR 0021 removes the retained reports, the
fingerprints and the local requirement. The full text is in Git history.
