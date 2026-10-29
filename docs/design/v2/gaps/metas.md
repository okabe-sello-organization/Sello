# Metas: what exists and what is missing

SELLO-085, checked on 2026-10-23 at `main` `d493c55` against the locked [v2 Metas
pages](../README.md#metas) (`metas.html`, `goal.html`, `aportar.html`, `create.html`,
`celebrate.html`), [ADR 0017](../../../decisions/0017-goals-recurrence-investments.md)
and the [Metas brief](../../screen-briefs/goals.md). Each item was opened in the code and,
where something is drawn, in the catalog on an API-30 emulator
(screenshots removed on 2026-10-29 with the ticket evidence; Git history before commit `02fe99b9` keeps them).

**In short:** the visual pieces exist in the design system and the catalog: stamps, goal
card, Recibo pill, completed row, movements, stamp ticket and the completion
celebration. They were built with the v2 component set (SELLO-073, #72) and no app
screen uses them yet. There is nothing else: no goal in the domain, no table, no
command, no backup field, no Metas tab, screen, route or sandbox scenario. No branch or
open pull request holds unmerged goal work.

Present = built and tested, ready to use. Partial = exists but differs as stated.
Missing = nothing exists.

## Data, rules and app

| Element | State | Where, or what is missing |
| --- | --- | --- |
| Goal rules and worked examples | Missing | Decisions only, in ADR 0017 and the brief; SELLO-086 |
| Goal, movement and milestone models, policies | Missing | No `Goal` type in `domain`; SELLO-087 |
| Room tables, migration, commands, receipts, undo | Missing | Schema has no goal table; SELLO-087 |
| Backup, restore and erase of goals | Missing | Format version 1 has no goals; SELLO-088 |
| Metas tab (third of up to four) | Missing | `ShellTab` has Recibo and Resumen; `SelloNavigationBar` accepts 2–4 destinations, so the bar is ready |
| Sandbox goal scenarios | Missing | No goal step in `ScenarioStep`; SELLO-094 |

## Metas tab

| Element | State | Where, or what differs |
| --- | --- | --- |
| Top bar "Metas", **+**, settings, no month bar | Partial | `TitleBar` exists; the tab must not show the shared `MonthBar` |
| Band "Guardado en metas", total, "+ 350.000 en octubre" | Present | `GoalsBandTotal`; `ComponentContractsTest.goalsBandTotalSpeaksTheExactSavedAmount` |
| Goal card: icon, name, %, saved, "de …", 🏁 date or "Sin fecha", bar, four places | Present | `GoalCard`, `MilestoneRow`; `activeGoalCardPutsTheDateBesideTheTargetAndSpreadsItsPlaces`; catalog *Active and completed cards* |
| Highest completion first; completed at the bottom | Missing | Ordering is the screen's job (SELLO-089) |
| Completed row "400.000, 30 sep" with LOGRADA | Present | `CompletedGoalRow`; `completedGoalRowCarriesItsStampOnTheRight` |
| LOGRADA lands as rows scroll into view | Partial | `CompletedGoalRow` has no `land`; `Stamp` has one |
| Dock **Aportar a una meta**, green, flag | Partial | `SelloDock(label, actionLabel, onClick)` has no tone or icon, so the shell's dock is always brand **+**; `EntryDock` has both but the shell does not use it |
| Empty: four empty places, "Ponle nombre…", dock **Crear una meta** | Partial | `MilestoneRow` with `Empty` places and `EmptySlip` exist; no composed empty state |
| Loading, failed read (never "no goals") | Present | `SlipSkeleton`, `ErrorSlip` |

## Goal detail

| Element | State | Where, or what differs |
| --- | --- | --- |
| Top bar ← name, edit; star pill **★ En tu Recibo** / **☆ Mostrar en Recibo** | Present | `TitleBar`; `GoalPinPill` (catalog *Icon strip and Recibo pin*); no device test of the pill |
| Main card: "Ya guardaste", %, saved, "de / faltan", bar, tear line, **Sellos 2 de 4**, next stamp | Partial | Built from `Slip`, `TearLine`, `MilestoneRow`; no composed card |
| Facts: Meta, Fecha objetivo, Desde | Present | `LeaderLine` / `KeyValue` |
| Aportes por mes, this month in brand | Present | `ColumnsChart` (current period); catalog *Split, partial-month and recorded values* |
| Movimientos, stamp in place of +, **Retirar dinero** in the header | Present | `MovementRow(milestone = …)`; catalog *Contributions and withdrawals*; no device test |
| Faded kept stamp with "sigue siendo tuyo" | Present | `MilestoneState.Kept` with `caption`; `reducedMotionRetainsTheEarnedMilestoneMeaningAfterWithdrawal` |
| Dock **Aportar a esta meta** | Partial | Same as the tab's dock |
| Completed goal, read-only, red **Borrar esta meta** | Present | `DangerSlip`, `ConfirmSlip` |

## Aportar / Retirar

| Element | State | Where, or what differs |
| --- | --- | --- |
| Entry layout, pad, note, saving, rejected, unknown outcome, restore | Present | `EntryLayout`, `AmountKeypad`, `NoteComposer` as used by expense and income entry |
| Switch Aportar / Retirar | Present | `SegmentedSwitch` |
| Green "+" amount with the goal's icon; ink "−" for Retirar | Present | `EntryAmountSlip(tone = Gain / Ink)`; catalog *Contributions and withdrawals* |
| "Más de lo guardado (1.710.000)" in red, button waits | Present | `EntryAmountSlip` label and disabled action, shown in the catalog |
| Goal chips with their % | Partial | `SelloChip` has an icon and label only; the design's % sits in its own softer span |
| "Llevas 1.710.000 guardados en esta meta." | Present | `FactLine` |
| Date pill, past dates only | Present | `FieldPill`, as in expense entry |

## Create and edit a goal

| Element | State | Where, or what differs |
| --- | --- | --- |
| ✕ **Nueva meta** / **Editar meta** | Present | `TitleBar` |
| Icon in a large green circle, Nombre with 40-character counter, red over-limit line | Partial | `FormField` and `FieldMessageText` exist; the large green icon circle beside the name is not a component |
| Row of icons to choose from | Present | `IconChoiceStrip`; catalog *Icon strip and Recibo pin*; no device test |
| Amount card "Cuánto necesitas", pad with **Listo** | Partial | `AmountKeypad(onSubmit)` and `EntryAmountSlip` exist; a pad that slides up over a form is not built |
| **Para cuándo**, Elegir mes / Sin fecha, "Son 769.231 al mes durante 26 meses" | Partial | `SelloChip`, `MonthGrid` exist; the whole-peso rule is SELLO-086's |
| Tus sellos with projected months | Present | `MilestoneSpec.caption`; catalog *Milestone states and projections* |
| **¿Ya la lograste?** and **Borrar esta meta** cards, confirm slip | Present | `Slip`, `DangerSlip`, `ConfirmSlip` |

## Celebrations

| Element | State | Where, or what differs |
| --- | --- | --- |
| Stamp lands in place with an ink ring, one haptic (S1) | Present | `MilestonePlace(land = true)`; `MilestoneRow(landingIndex)` |
| Toast adds "Ganaste el sello del 50 %" | Partial | `SelloNotice(confirmation = true)` exists; the line is the caller's |
| Paper ticket from Metas or Recibo (S2), two stamps share one | Present | `StampTicket`; catalog *Confirmation, undo, ticket and danger*; no device test |
| Back above a kept stamp lights it again | Partial | `Kept` → `Earned` changes colour; no relight motion |
| 100 % waits: dashed green Meta, **Marcar como lograda** | Present | `MilestoneState.Ready`, `ReachedRow` |
| The big moment, reduced motion, long names | Present | `CelebrationOverlay`; `CelebrationTest` (timeline, reduced motion, status bar) |
| Deshacer for about 10 s, longer with TalkBack | Partial | Corrections' undo notice exists with its own window (`ExpenseCorrections`); 10 s and the TalkBack extension are new |

## Recibo

| Element | State | Where, or what differs |
| --- | --- | --- |
| Goal pill filled to its %, **+** Aportar, opens the goal | Present | `GoalPill`; catalog *Recibo goal and reached row*; no device test |
| Green "Llegó al 100 %" row | Present | `ReachedRow` |
| Choice, fallback, hidden on a past month | Missing | Rule and state (SELLO-093) |

## Consequences for the tickets

Every later ticket builds on these components instead of drawing its own. The partial
items become small extensions inside the ticket that first needs them: the dock's tone
and icon and the empty state (SELLO-089), the chip percentage (SELLO-091), the name's
icon circle and the sliding pad (SELLO-090), the relight and the 10-second Deshacer
(SELLO-092), the LOGRADA landing in rows (SELLO-089). SELLO-094's catalog part shrinks to
those extensions and device tests for the pieces that have none (`GoalPill`,
`MovementRow`, `StampTicket`, `GoalPinPill`, `IconChoiceStrip`).
