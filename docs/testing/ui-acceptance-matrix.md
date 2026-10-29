# UI acceptance matrix

Which windows, text sizes and themes the assembled daily-use app was checked in, what
each check proves, and the test or walk that proves it. SELLO-033 assembled this
matrix under [D10](../decisions/0001-mvp-contract.md#d10--release-acceptance-and-review).
Manual TalkBack, Switch Access and hardware keyboard walks are post-MVP
([SELLO-074](../tickets/E14-assistive-technology/README.md)); the accessibility
checks a test can read are here. Synthetic data only.

## Configurations

| ID | Window (dp) | Text | Theme | Where | Layout it exercises |
| --- | --- | --- | --- | --- | --- |
| C1 | 320 × 640 | 2.0 | Cobalto light | Sweep | Compact phone: tab bar, dock, one pane |
| P1 | 393 × 851 | 1.0 and 2.0 | Cobalto light | Walk | A real phone window with system bars and keyboard |
| P2 | 851 × 393 | 2.0 | Cobalto light | Walk | The same phone sideways, keyboard open, navigation bar on the right |
| L1 | 640 × 360 | 1.3 | Violeta dark | Sweep | Short landscape: rail, two panes |
| L2 | 640 × 360 | 2.0 | Cobalto dark | Sweep | Short landscape at the largest text |
| M1 | 700 × 900 | 1.0 | Violeta light | Sweep | Medium: tab bar, side-by-side detail |
| E1 | 1000 × 700 | 1.0 | Cobalto light | Sweep | Expanded: rail, list and detail panes |
| M2 | 700 × 900 | 1.0 | Cobalto light | Walk | Medium, a real window |
| E2 | 1080 × 756 | 1.0 | Cobalto light | Walk | Expanded, a real window |

The sweep is `UiAcceptanceSweepTest`: the real app root with its own database and view
models, run on the API 30 gate emulator in a window of the given size, one dp drawn
as one pixel. A window larger than the emulator's screen proves what exists and
where it is laid out, not what is visible; the walks below cover that.

The walks ran on the isolated API 30 phone emulator `sello022_api30` (1080 × 2340 at
440 dpi, three-button navigation, `emulator-5584`) with a debug build of the code under
test, driven through `adb` and `uiautomator`: P1 and P2 as the device is; M2 and E2 by
overriding its display (`wm size` 1925 × 2475 at 440 dpi; 2160 × 1512 at 320 dpi).
The screenshots were removed on 2026-10-29 with the ticket evidence; Git history before commit `02fe99b9` keeps them.

Every theme appears once in the sweep; colour contrast for all four is checked
separately, token by token, by `SelloColorsTest` (WCAG AA: 4.5:1 for text, 3:1 for
marks and large text), including the stamps, soft fills and past-month bar.

Font scales 1.0 and 1.3 on a compact phone are covered by each screen's own tests
(`*ScreenTest`, `ShellScreenLayoutTest`, `ExpenseEntryGeometryTest` and the
design-system suites); the sweep takes the riskiest corners rather than all 48
combinations.

## Screens and checks

Each window opens every screen below through the app's own buttons, from a history
with two categories (one over its limit), two expenses and an income. The history
is the app's own graph on the sandbox's data with its financial clock fixed at 30
October 2026, so the weekly chart always has five columns, whatever the real date:

Recibo · category detail · expense edit · category edit · expense entry · new
category · Resumen · Resumen's category · income history · income entry ·
Configuración · Respaldo · Borrar todos los datos.

On each screen the sweep records, then reports together:

| Check | Rule |
| --- | --- |
| Touch targets | Every control answers a touch at least 48 × 48 dp (Compose's widened touch area included). A control a list has scrolled out of view is measured where it is whole. |
| Text not cut short | No text is shortened with an ellipsis or stopped at its line limit. |
| Amounts spoken in full | Every amount shown is read with its currency, such as "48.700 pesos". |
| Main action reachable | Save, delete, Respaldar, Continuar and the Configuración rows scroll into the window. |

## Findings and fixes

| Found | Where | Fix | Regression test |
| --- | --- | --- | --- |
| Screen titles cut short ("Editar gasto", "Ingresos de octubre", "Configuración · Octubre 2026") | C1 | Titles and the month title take a second line before any ellipsis | `LargeTextFitTest`, sweep |
| "Editar categoría" did not fit beside **Archivar** even on two lines | C1 | When the title has no room beside its actions it moves under them, full width; otherwise unchanged | `LargeTextFitTest.aTitleWithNoRoomBesideItsActionMovesUnderItAndStaysWhole`, `aTitleThatFitsStaysBesideItsAction` |
| Rail labels "Recibo", "Resumen" cut short | L2 | The rail widens to its longest label (at least 80dp) | `LargeTextFitTest.theRailWidensSoEveryTabLabelIsWhole` |
| Chip label "Último gasto agregado" cut short | C1 | Chips take a second line before an ellipsis, as the v1.1 spec's "chips wrap" says | `LargeTextFitTest.aChipsLabelTakesASecondLineInsteadOfBeingShortened` |
| Limit suggestions read "200.000" without pesos | all | Each suggestion is read as "200.000 pesos" | `CategoryEditorViewModelTest`, `CategoryManagementViewModelTest`, `CategoryEditorScreenTest` |
| Resumen's limit line read "Límite 350.000" | all | The chart's reference line is read as "Límite 350.000 pesos" | `ChartExactSelectionTest`, `ResumenScreenTest` |
| Weekly chart labels "sem 2", "sem 3", "sem 4" cut short from the 29th of a month, when the chart has five columns (39dp each) | L2 | The chart's axis shows the week number alone ("2") when a column cannot hold "sem 2" in two lines at the label's own size; every column, the current week included, shows its number, and the spoken text stays as it is | `LargeTextFitTest.aChartsFiveWeekLabelsShowTheNumberAloneWhereTheWordsDoNotFit`, `aPastMonthsFiveWeekLabelsShowTheNumberAlone`, sweep (pinned to 30 Oct 2026; also fails a week label broken inside its word) |
| The note composer ran under the navigation bar on the right: **Listo** sat behind it and had no bounds for accessibility | P2 | The composer's content keeps clear of the side system bars; its paper still reaches the edge | `ComponentContractsTest.aNavigationBarAtTheSideCoversNeitherCancelNorDone`; screenshots 2 before and after |

No touch target under 48dp and no main action outside its window was found.

## Process restoration

Rotation, process death and cold start are separate journeys, each checked against
the database copied off the emulator (`run-as`), not only against the screen.

| Journey | Steps | Result |
| --- | --- | --- |
| New expense, process death | Draft 48.700 in Mercado with the note "Plaza" → Home → `am kill com.software.sello.debug` → process ID gone, task kept → back through Recents | A new process (5384 → 6666) shows the same amount, category and note (screenshots 6, 7); Anotar saves one expense and one `expense.create` receipt |
| Saved expense, process death | Right after the save → Home → kill → Recents | Recibo, still one expense and one receipt: the save is not repeated |
| Expense edit, process death | 48.700 changed to 52.000 → Home → kill → Recents | The edit returns with 52.000 (screenshot 8); saving gives version 2 and one `expense.edit` receipt, unchanged after another kill |
| New income, process death | Salary draft → Home → kill → Recents | The amount and Salario return; one income and one `income.create` receipt, unchanged after another kill |
| New category, process death | Name "Transporte", limit 500.000 → Home → kill → Recents | Name, limit and selected suggestion return; leaving asks before discarding |
| Rotation | Draft 12.000 with a note, portrait → landscape → portrait | Same process (8806); the draft stays; one save, unchanged by two more rotations |
| Cold start | Draft 1.000 open → `am force-stop` → launch | Recibo on the saved figures (236.000 left), the unsaved draft gone and nothing written (screenshot 9). A force-stop proves the data reopens, not that a task is restored |

The operation identifiers themselves are kept in saved state; host tests prove it for
each form across a simulated process death, including a save cut off mid-way being
looked up rather than sent again (`ExpenseFormViewModelTest`, `ExpenseEditViewModelTest`,
`IncomeFormViewModelTest`, `CategoryEditorViewModelTest`, `CategoryManagementViewModelTest`).
The walks show the observable result on a device: one receipt per confirmed action.

## Real windows

| Walk | Result |
| --- | --- |
| P2, note composer with the keyboard open at 2.0 | The note, Cancelar and Listo stay above the keyboard and, after the fix, clear of the navigation bar (screenshot 2) |
| P1, "Editar categoría" at 2.0 | Fits beside **Archivar** on two lines (screenshot 1) |
| M2 | One centred column with the tab bar and the full-width dock (screenshot 3) |
| E2 | The rail with Anotar, Recibo and Resumen, the list and the category's detail side by side (screenshot 4) |
| Reduced motion | With the system's animation scales at 0, Recibo shows the new remainder at once after a save, with its notice (screenshot 5) |

## Not covered

- Manual TalkBack, Switch Access and hardware keyboard walks: post-MVP, SELLO-074.
- Physical devices: none used. D10's signed installation on a physical device belongs
  to SELLO-036.
- API 37: the only image is a 16 KB-page one; the gate and walks use API 30, the
  minimum supported version.
- The sweep's windows are larger than the gate emulator's screen; visibility in those
  sizes rests on the walks above.
