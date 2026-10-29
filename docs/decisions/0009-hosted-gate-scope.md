# 0009 — What the hosted check runs, and when

**Owner:** project owner (user) · **Updated:** 2026-10-29
**Status:** superseded by [ADR 0021](0021-ci-is-the-only-proof.md).

From 2026-10-22 to 2026-10-29 this record set the hosted gate per event: G0 for
documentation advancing no ticket, G2 with dependency-aware device scope for other
pull requests, G1 for code pushes to `main` (to publish trusted caches), and G3 on
the nightly and manual runs. The app device suite ran as two shards beside a
`support` group. A merge queue ran the check on the real merge result from
2026-10-24 to 2026-10-26; its amendment retired the queue, let a green pull request
merge as it is, and made the nightly verify `main` at G3 only when `main` moved.
`scripts/verify-ticket` planned, executed and collected parts into retained reports.
ADR 0021 keeps the events, the shards, the cache policy and the nightly, and drops
the reports and the ticket-advancement rule. The full text is in Git history.
