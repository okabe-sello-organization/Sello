---
name: sello-feature
description: Use when building or changing a Sello feature: where files go, money/write/read rules, screens, tests per layer.
---

Read `docs/development/feature-guide.md` (the worked example is Metas/goals) and `AGENTS.md` before you start.

The five rules that matter most:

1. Money is `Money` (checked minor units); `BigDecimal` only for rates with an explicit `RoundingMode`. A failed read keeps the last good figures (`ReadState.Failed(lastGood)`), never zero or empty.
2. Every money write goes through `Submission` with an operation ID, returns `CommandOutcome`, and an unknown outcome is asked about with the same ID. Cancellation is rethrown; a success stamp needs a commit actually seen.
3. Screens are immutable State, Actions in, a ViewModel and a Root/Screen split. Durable results live in State; there are no event channels. Handle loading, empty, error, busy, restored drafts and 2× font.
4. Modules: rules in `:domain`, Room in `:data`, screens in `:app`, shared visuals in `:design-system`. A schema change needs a migration, exported schema, real-Room retention test and a backup-format check.
5. Test at the cheapest layer: policies and ViewModels on the JVM, screens with `showSello(Look(...))` and Roborazzi, storage on real Room on your own emulator (`scripts/emulator`). A feature adds no device journey. Fix flaky tests at the cause.
