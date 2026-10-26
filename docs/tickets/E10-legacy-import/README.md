# SELLO-E10 — Legacy data import after MVP

- **Status:** In Progress
- **Increment:** Post-MVP
- **Goal:** Let someone bring their history from the previous app into Sello without weakening Sello's rules.
- **Exit:** A legacy export is translated into a validated Sello backup and restored through the released restore workflow, with every skipped record counted and explained; journeys without an import remain unchanged.

This is an owner-requested roadmap addition (2026-10-09), not MVP scope. The MVP
contract still says no legacy database import
([D01](../../decisions/0001-mvp-contract.md#d01--scope-and-first-release)) and
[architecture](../../../ARCHITECTURE.md) §7 allows only an isolated legacy JSON
translator after an explicit product and security decision. SELLO-044 cannot start
until MVP release acceptance (SELLO-036) is Done. SELLO-044 imports the history and
SELLO-118 the investments and dollar rate, under
[ADR 0019](../../decisions/0019-legacy-import.md).
