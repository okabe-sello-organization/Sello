# SELLO-094 — Cover goals in the sandbox and catalog

- **Revision:** delivered in two pull requests. Part 1 (#124, the sandbox) was merged
  after its own G1 run and hosted checks. The final gate tested `287ca90` on
  `sello-094-goal-catalog`: part 2's commits merged with `main` at `79b40a5` (part 1
  merged), plus a style fix the first run found. The tree was clean; the status lines,
  this file, the handoff and the retained report are added afterwards.
- **Requirement mapping:** scenarios use real commands and storage with synthetic data →
  goal steps send `GoalCommands` through the fenced debug graph
  (`GoalScenarioRunnerTest`, `GoalSandboxGraphTest`), checks read the real goal board, and
  `TemporalScenarioCorpusTest` runs `metas-sellos`, `metas-retiros`, `metas-objetivo` and
  `metas-lograda` on real Room fresh, cut off and resumed, and restored from a backup,
  comparing goals, movements and Recibo's choice; every goal check names its MT example
  (`ScenarioDefinitionTest`). Goal writes share the fence, faults and log →
  `GoalSandboxPortsTest`. The catalog shows every extended component in all themes and
  states → `goals.milestones` (relight), `goals.goal-cards` (LOGRADA landing),
  `inputs.chip` (percentage), `goals.goal-pill` (description, 0 %, stacked, busy),
  `feedback.danger-and-ticket` (relit, Meta waiting), `goals.goal-form` (new), each with
  previews in the four themes; `CatalogExamplesTest` lists the new id. Device tests for
  the five components → `GoalComponentsTest`, in all four colour schemes. None of it in the
  release build → the sandbox lives in `src/debug` and the catalog in `:catalog`; the
  gate's release assembly and architecture checks passed.
- **Red / Green:** `GoalSandboxPortsTest` 5/5 failed against pass-through placeholders,
  then passed. `GoalScenarioRunnerTest` failed while goal steps stopped as unsupported,
  then passed; it also caught my wrong peso unit in the goal check (COP's minor unit is the
  peso). The corpus run caught two wrong expectations of mine, corrected from the rules,
  not the code: *Celular nuevo* at 450.000 of 1.200.000 has its 25 % stamp (rule 3), and a
  restored history has no receipts, so a goal is checked by its unique name.
  `GoalComponentsTest` was written after the components; its target check was corrected to
  measure touch bounds, as the other component tests do.
- **Gate results:** `./scripts/verify-ticket SELLO-094 --retain` passed all six G1 checks on
  `287ca90`: 944 host tests, zero failures, errors or skips; lint zero errors and 34
  warnings, as on `main`. The first run failed ktlint on one long line, fixed. Device
  suites run in the hosted `quality` check. Local focused device runs on `sello092_api30`:
  the sandbox package (27, every scenario fresh = resumed and restored), `feature.goals`,
  `navigation` and `feature.backup` with goal writes fenced, and the full design-system
  (157) and catalog (21) suites, all passed.
- **Quality run:** run 20261025T012708Z-44d4db05; SELLO-094 G1 passed; HEAD 287ca90, inputs sha256 d989befafd66, changes sha256 9e1974528d20; report docs/tickets/E17-goals/SELLO-094/quality-report.json sha256 74b5cf24d6ecce3ec8e3306fe9be15ec5d52701fd021e402f985467b134f2f69
- **Device / Artifact:** synthetic AVD `sello092_api30` (API 30, 320 × 640). Catalog
  screenshots: `artifacts/catalog-pill.png`, `catalog-reached-row.png`,
  `catalog-tickets.png`, `catalog-goal-form.png`.
- **Review:** executor self-review; owner review by merging the pull request, which also
  proposes E17 Done.
