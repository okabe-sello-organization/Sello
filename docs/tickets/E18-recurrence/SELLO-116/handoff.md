# SELLO-116 — Fence schedule writes in the sandbox

In debug builds, schedule commands go through the sandbox's write fence, faults and log (`FencedScheduleCommands`, `FaultyScheduleCommands`), and catch-up on opening goes through the fence (`FencedScheduleCatchUp`), marking the sandbox changed when it records; the diagnostic reads schedule receipts. Bound in `DebugComposition`.
Still open: recurrence scenarios and their sandbox controls (SELLO-104).
