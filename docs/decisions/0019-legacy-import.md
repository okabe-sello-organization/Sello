# 0019 — Import from FinanceTracker keeps what fits and says what it changed

**Owner:** project owner (user) · **Date:** 2026-10-25
**Status:** owner-approved; implemented by SELLO-044 and SELLO-118 (investments).

## Context

Sello's own backups are all or nothing: the first bad record rejects the file (D05, D09,
architecture §7). Someone moving from the previous app, FinanceTracker, has a backup
written under different rules: one limit per category with no history, free-text income
sources, goals without contribution history, its own repeat rules and investments Sello
cannot hold yet. Rejecting such a file whole would leave that person nothing.
[Architecture](../../ARCHITECTURE.md) §7 allows an isolated legacy JSON translator after
an explicit product decision; this is that decision.

## Decision

- **Translate, then restore.** A FinanceTracker backup chosen in Restaurar is
  recognised by its shape and translated in memory into an ordinary Sello backup. The
  released validator must accept that backup unchanged, and the released restore stages,
  previews and applies it: same replace warning, generation advance and receipt. The
  translator never writes to the database. The old database file is never read.
- **A bad legacy record is left out, not the file.** Each record that breaks a Sello rule
  is skipped and counted under its reason; a record that depends on a skipped one is
  skipped with it under its own reason. No expense moves to another category to survive.
  A file that is not a FinanceTracker export, is newer than the versions known, or
  exceeds the §7 structural bounds is still rejected whole.
- **Three repairs the owner chose (2026-10-25), always shown.** Text longer than Sello
  allows is cut to fit, counted as shortened. Category names that clash under Sello's
  uniqueness rule keep the first (in FinanceTracker's order) and number the others
  `Nombre_2`, `Nombre_3`, … cut to fit, counted as renamed. A dollar rate with more than
  two decimals, as FinanceTracker fetched it, is rounded half up to the centavo from its
  exact decimal text, counted as rounded. No other value is repaired, rounded or clipped.
- **Mappings the owner chose.** The single limit becomes the category's default limit
  from the earliest imported month, so past months read as they did in FinanceTracker.
  Goals are imported with their saved amount as one contribution on the export day.
  Repeating entries become Sello schedules that continue where FinanceTracker stopped.
  A source without a Sello match becomes Otro with that name. Importing again is allowed:
  like any restore it replaces everything.
- **Investments keep the old app's figures (SELLO-118, owner 2026-10-25).** Aportes,
  retiros, dividends and recorded values keep their kind, amount in minor units,
  currency and day. Interest, which FinanceTracker added to the value by itself, becomes
  a recorded value of what the value reached; an investment with no value on its first
  aporte's day gets one equal to that aporte, as Sello does on creation; an annual rate
  stops adding interest. Each is shown as changed. An entry that breaks an investment
  rule (before the first aporte, a retiro above the value) is left out and the history
  worked out again; an investment with no aporte is left out with its entries. Only
  pesos and dollars, and only the dollar rate, come in. Merchant rules are not imported.
- The exact mapping and every skip reason are in the
  [legacy export format](../data/legacy-export-format.md).

## Alternatives

Reject the file whole like a Sello backup: safest, but useless for real exports.
Skip text that is too long, as the ticket first proposed: honest, but the owner judged a
lost expense worse than a shortened note. Merge into the current history: refused by D09.

## Consequences

The preview shows what changed and what was left out before anything is replaced, and a
legacy import's totals can differ from FinanceTracker's; the preview says so. An
investment's gain can differ where it had retiros: FinanceTracker counted a retiro as a
loss, Sello as money taken out. The
skip-whole-record rule of D05/D09 still governs every Sello backup. The skip report is
kept beside the staged file so it survives the app closing before confirmation; it is not
saved anywhere else.

## Verification

Translator table tests over synthetic FinanceTracker exports (every skip reason,
dependants, duplicates, clashes, cuts, bounds, an unusable file) and a real-Room restore
of a translated file with independently counted results (SELLO-044 and SELLO-118
evidence).
