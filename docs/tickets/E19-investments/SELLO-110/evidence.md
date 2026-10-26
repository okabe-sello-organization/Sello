# SELLO-110 — Record investment movements and values

- **Revision:** the gate tested commit `5f10a6b` on `sello-110-investment-detail`, which
  merges `main` after SELLO-111 (#160); the only uncommitted file was the retained report.
  The status, this file and the handoff are added after.
- **Requirement mapping:** the first value defaults to the first contribution and is
  editable → Lo mismo que aporté / Otro valor (`InvestmentEditorViewModelTest`: the first
  value equals the aporte by default, Otro valor may be 0) and on the real app
  (`InvestmentJourneyTest`: a CDT of 10.000.000 is stored with a value of 10.000.000);
  dividends add to gain, not value → the movement form's Dividendo or renta and IN14 in the
  policy and sandbox; unknown outcomes are recovered before another write → the movement
  form and the editor ask `find` with the same identifier, also after process death
  (`InvestmentMovementViewModelTest`, `InvestmentEditorViewModelTest`); the detail with its
  history, the movement form with the Sello pad and date, edit, delete and undo →
  `InvestmentDetailScreen` (values chart with months and legend, movements newest first),
  `InvestmentMovementScreen` (IN10, IN18, IN19, IN20, IN22, IN25 messages; the comma pad
  for dollars, IN34; a value of 0 pesos), corrections and deletion through the shared
  Deshacer (`ExpenseCorrectionsTest`); Agregar / editar inversión with its currency → the
  rate asked first and sent in the same command (IN33, `InvestmentJourneyTest` on real
  storage: an investment of USD 1.000,00 and the rate 415025 together), editing and deleting
  with the confirm slip (IN23); recording a movement is not a fresh valuation → the value
  keeps its date (detail "Valor anotado el …"); the working checkpoint → valued from the
  empty tab to +7,5 % (IN03) on the device.
- **Red / Green:** by the delegated agents: the over-value check, the later-retiro check,
  USD cents, recovery by the same identifier, cents across process death, the
  before-first-aporte check, undo through goal commands, Lo mismo que aporté, the rate
  not sent, a zero aporte and the unchanged edit each failed their tests when broken.
  Here: a value of 0 pesos failed until `pesoDigit`; the amount being typed sat under
  the pad (617dp under a pad at 362dp) until it was kept in view.
- **Gate results:** `./scripts/verify-ticket SELLO-110 --retain` passed G1 full on `5f10a6b`:
  1164 host tests in 104 suites, zero failures; lint 0 errors and 34 warnings, as on
  `main`. Device on `sello_e19b_api30` (API 30): the whole app suite 471/471 on
  `696b70f`; `InvestmentMovementScreenTest`, `InvestmentEditorScreenTest` and
  `InversionesScreenTest` 24/24 and the editor's pad test after; `InvestmentJourneyTest`
  2/2; `InvestmentPartsTest` 8/8. The hosted check runs every device suite on the merge
  result. The first hosted run failed `InvestmentMovementScreenTest` on CI's short screen
  (the pad covered the value link); pressing it through its action fixed it, and the 31
  investment device tests passed at 320×640 mdpi on `sello_e19b_api30`.
- **Quality run:** run 20261025T234631Z-3efd5685; SELLO-110 G1 passed; HEAD 5f10a6b, inputs sha256 60c89c6c76df, changes sha256 cbf428dabe59; report docs/tickets/E19-investments/SELLO-110/quality-report.json sha256 9270ea23bcb6d2f78bcbb81e38f3ad272e34894ab3382f0ee449c7ea6452a9af
- **Device / Artifact:** `sello_e19b_api30` (API 30 emulator); screenshots in `artifacts/`
  from the installed app; no owner device.
- **Review:** the movement form and the editor were built by delegated agents and
  reviewed and integrated here; executor self-review; owner review by merging.
