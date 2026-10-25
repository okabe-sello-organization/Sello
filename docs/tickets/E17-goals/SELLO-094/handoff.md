# SELLO-094 — Cover goals in the sandbox and catalog

Goal writes in debug builds go through the sandbox's fence, faults and log (`FencedGoalCommands`, `FaultyGoalCommands`, `FaultyGoalReads`), and the sandbox replays four goal scenarios (`metas-sellos`, `metas-retiros`, `metas-objetivo`, `metas-lograda`) through the real commands, each check citing its MT example ([scenario corpus](../../../testing/sandbox-scenarios.md)); the corpus test compares goals between fresh, resumed and restored runs.
The catalog shows every goal extension E17 added (relight, LOGRADA landing, chip percentage, pill description, 100 % row stacked and busy, ticket variants, icon circle and sliding pad, `goals.goal-form`), and `GoalComponentsTest` covers `GoalPill`, `MovementRow`, `StampTicket`, `GoalPinPill` and `IconChoiceStrip` in all schemes.
Still open: schedule commands are not yet fenced in debug builds (SELLO-104); a Deshacer too late (MT25) is not replayed in the sandbox, as it would need real waiting.
