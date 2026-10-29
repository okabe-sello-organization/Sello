# SELLO-035 — Validate performance and optimized release composition

- **Type:** Task
- **Priority:** P0
- **Status:** Done
- **Depends on:** SELLO-004, SELLO-023, SELLO-027, SELLO-032, SELLO-033, SELLO-034
- **Gate:** G3

### Outcome
Measure a realistically populated app and make customer release packaging deliberate.

### Deliverables
Representative synthetic dataset/device profile and approved performance thresholds,
startup/scroll/graph/backup measurements; correct off-main/bounded work; optimized
release configuration/keep rules, dependency/license review and artifact inspection.
Set private-APK production signing through secure local/CI inputs with no committed secrets.

### Acceptance criteria
- Large history remains responsive without full unbounded reads or main-thread file/
  replay work. Chart exactness stays intact; optimization never substitutes rounded money.
- Approved D10: measure representative histories and obtain explicit numerical target
  approval before acceptance. Record named device-testing owner and signing custodian;
  unassigned owners/credentials cannot be substituted with agent self-approval.
- Release DI graphs and shrunk code execute real commands/serialization/recovery;
  no debug clocks, fixtures, catalog code, no-op financial services or tools survive.
- Measurements state device/build/dataset and meet reviewed thresholds; no arbitrary
  claimed benchmark. Signed release installs and launches with correct identity.

### Tests
G3 including actual optimized-release manual/automated smoke, dependency/security
reports, artifact inspection and repeatable performance runs. CI ephemeral signing
and customer-distribution signing are validated separately; lack of credentials is a blocker.

### Working checkpoint
A candidate customer artifact—not the catalog or debug variant—works with persisted
financial history and recoverable files. Reports support the final acceptance decision.

### Context and starting points
Debug responsiveness and unshrunk DI do not establish release behavior. Inputs are
approved D10 targets/device profile, all recovery/privacy/a11y evidence and actual
signing access. Read architecture §10, source-library shrinking/signing docs and
the currently unoptimized release configuration in `app/build.gradle.kts`.

### Implementation plan
1. Create `docs/testing/performance-profile.md` with reviewed targets, device/API,
   measurement method, repetitions and datasets before accepting results. Use 20
   categories, 10,000 expenses, 2,000 incomes and 12 past months as a proposed large profile.
2. Produce synthetic data through validated commands/backup, install candidate only
   on an isolated test device/profile, and measure startup/scroll/graph/backup/restore
   responsiveness, heap/main-thread work and SQL/query behavior.
3. Fix measured bottlenecks at owned boundaries: bounded/paged queries, exact summaries,
   off-main work. Add benchmarks only when their tooling scope/versions are approved.
4. Enable production optimization/shrinking and minimum tested keep rules; verify
   Koin/Room/serialization/resources/recovery actually run in the optimized artifact.
5. Build/sign using approved external inputs, inspect source/variant/runtime graphs
   and manifests/resources/dependencies; attach dependency/license/security and signing reports.

### Concrete cases and pitfalls
- Do not lower targets after seeing a slow run or replace exact money with Float
  to improve graph throughput. State warm/cold/cache/dataset conditions for each result.
- Obfuscated class-name absence alone is not proof debug tools were excluded; source
  set/dependency inspection and unshrunk customer-variant checks corroborate the artifact.
- No signing access means G3 is blocked, not “signed by debug key”. Build the
  approved private APK without uploading, tagging or publishing it. No AAB/Play
  deliverable is required for MVP; any channel change needs explicit approval.

### Verification recipe
Run host/device gates and actual `:app:assembleRelease`; execute G3 on the
production-signed optimized private APK candidate, not a debug/ephemeral CI substitute.
Record hash/version/signing certificate, independent release money/recovery smoke,
measurements/thresholds and artifact exclusions. Never write secrets in commands/reports.

### Execution progress

- **Owner decisions, 2026-10-23:** measurements through `adb` with no new libraries;
  the API-30 emulator as the reference device; the targets in the
  [performance profile](../../testing/performance-profile.md), approved before any
  result; the owner tests on physical devices and keeps the signing key, read in part 3
  from the owner's `~/.gradle/gradle.properties`, never from the repository. An
  independent technical reviewer is not assigned.
- **Part 1 of 3, measurements** (`sello-035-measure`). Delivered: a seeded synthetic
  large history (20 categories, 10,000 expenses, 2,000 incomes, 12 past months)
  restored through Respaldo; `scripts/performance/measure.py` for cold and warm start,
  Recibo scrolling, Resumen, a category's detail, backup and restore; Recibo reports
  itself fully drawn once the month is read (red first); `MainThreadWorkTest` fails on
  main-thread disk work from Sello's code. On the unshrunk release build, on top of
  #88, every target is met; Resumen has the least margin (535 ms of 700). Remaining: R8 shrinking and keep rules with the
  journeys re-measured (part 2); production signing, artifact inspection and G3
  (part 3).
- **Part 2 of 3, shrinking** (`sello-035-shrink`). R8 shrinks and optimizes the
  release build with the libraries' keep rules and none of Sello's: on the optimized
  build, saving, editing, deleting with undo, an income, a new category, a theme kept
  across a restart, a draft across process death, a past month, backup, restore and
  erasing all worked against the expected figures, with no crash. APK 28.9 MB → 4.5 MB;
  every target met with more room (Resumen 402 ms of 700). Remaining: production
  signing, artifact inspection and G3 (part 3).
- **Status stayed Backlog until G3 existed.** The runner verifies an In Progress ticket
  at its own gate, and G3 is what this ticket delivers; a lower gate may not stand in.
  Parts 1 and 2 were verified as merge candidates (G2).
- **Part 3 of 3, signing and G3** (`sello-035-signing`). The owner chose, on
  2026-10-23, to keep the key off CI ([ADR 0016](../../decisions/0016-release-gate-signing-split.md)).
  The release build signs from the owner's existing `sello.signing.*` Gradle properties
  (`~/.android/sello-release.jks`); its certificate digest is pinned. G3 adds a reviewed
  licence list (143 libraries: 142 Apache-2.0, 1 BSD-3-Clause), the production signature
  and artifact checks with R8's mapping kept beside the APK, and installing and opening
  the signed APK on the emulator; CI runs it with a throwaway key. With the owner's
  approval for this one step, osv.dev and the published POMs found no advisory and no
  licence difference ([advisories](../../release/advisories.md)). The signed APK
  restored the large history and backed it up by hand. Open for the owner: the app shows
  no third-party licence notice.
