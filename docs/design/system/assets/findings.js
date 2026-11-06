/*
 * Design findings: where the built app uses a component against its meaning (misuse),
 * rebuilds a shared component by hand (copies) or writes a value where a token exists
 * (hard-coded). A reference, not a list of decisions. Rendered by findings.html (see
 * system.js). Built from the SELLO-180 audit at main 0bf22f5e; every path:line is at
 * that revision.
 *
 *   misuse:    [{ id: "M01", component, ref, meaning, evidence: [{file, note?}],
 *                 places: [{ id: "M01-1", file, area, features, what, why, unsure, shots }] }]
 *   copies:    [{ id: "C01", component, ref, shared,
 *                 copies: [{ id: "C01-1", file, area, features, differs, identical, drift, unsure, doubt, shots }] }]
 *   hardcoded: [{ id: "H01", kind: "colour|type|shape|spacing|alpha", value, token, risk, note,
 *                 shots, places: [{ file, area, features, note }] }]
 *   dynamic:   the dynamic-colour picture; notListed: copies the audit left out, with why.
 */
window.SELLO_FINDINGS = {
 "revision": "0bf22f5e",
 "dynamic": {
  "today": [
   "SelloTheme(ink, darkTheme) picks one fixed SelloColors table (Cobalto or Violeta, light or dark: CobaltoPalette.kt, VioletaPalette.kt and the semantic, category and investment lists in SelloColors.kt) and provides it through LocalSelloColors.",
   "SelloMaterial.toMaterialColorScheme() copies those values into every Material slot: primary = brand, secondary = pastBar, tertiary = gain, error = loss, background = desk, surface* = paper / paperAlt / paperDim, outline / outlineVariant = outline / rule, scrim = Color.Black. Theme.kt's KDoc says wallpaper colours are never applied.",
   "App code reads SelloTheme.colors 288 times and MaterialTheme.colorScheme 0 times. Only stock Material parts use colorScheme, implicitly: 8 DatePickerDialog / DatePicker pairs, 2 default CircularProgressIndicator (CategoryRemovalSlips.kt:102 and :286), DropdownMenuItem text and the ModalBottomSheet scrim and drag handle."
  ],
  "needs": [
   "SelloColors itself must be built from the system ColorScheme (dynamicLight/DarkColorScheme to brand, brandSoft, desk, paper*, ink*, outline, rule, pastBar*, band), not just a new colorScheme.",
   "Otherwise the stock Material parts would follow the wallpaper while every Sello part stays Cobalto or Violeta."
  ],
  "noMaterial": "band / onBand (the top band), goalBand, investmentBand, warn, warnSoft, dividend, the 12 category colours, the 5 investment inks and the onPastBar pairs used as a ribbon. gain and loss sit in tertiary and error today, but they are meanings that must not take a wallpaper hue.",
  "copiesRole": "band, goalBand and investmentBand, and the investment on-colours, are literal copies of other roles (brand, ink, gain) instead of references to them.",
  "copiesRoleId": "H07",
  "outside": "Hard-coded colours outside the palette are few: Color.White in BandChip and PastMonthRibbon (light), the white confetti, the navy RepeatSheet scrim, the Settings ink swatches and the XML window backgrounds (night copies Cobalto desk; light uses the platform default).",
  "notListed": "Color.Unspecified (it means \"inherit LocalContentColor\") and colours blended between two theme roles in animations (CelebrationOverlay.kt:271, Goals.kt:251), which follow the theme."
 },
 "misuse": [
  {
   "id": "M02",
   "component": "SelloNotice(confirmation)",
   "ref": "components.html#c-SelloNotice",
   "meaning": "The confirmation check leads a notice that something was saved or recorded; deletions and removals with Deshacer carry no check.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:38"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:71"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:134"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:241"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementNotices.kt:25"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:124"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:193"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:203"
    }
   ],
   "places": [
    {
     "id": "M02-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:110",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "'Quitaste el renglón de Aporte' + Deshacer shows the confirmation check (confirmation = true).",
     "why": "A removal is not a confirmed save; every deletion notice and Varios gastos' identical removal notice (BulkEntryScreen.kt:124) have no check.",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "goal-bulk-removed-claro.png"
      }
     ]
    },
    {
     "id": "M02-2",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:444",
     "area": "investments",
     "features": [
      "investments"
     ],
     "what": "Removed line notice on Varios movimientos (investments) + Deshacer shows the confirmation check.",
     "why": "Same as GoalBulkScreen.kt:110.",
     "unsure": false,
     "shots": []
    }
   ]
  },
  {
   "id": "M03",
   "component": "ConfirmSlip(destructive)",
   "ref": "components.html#c-ConfirmSlip",
   "meaning": "A confirmation of something that cannot be undone; destructive (default true) draws the action in loss red. Discarding typed work is destructive in 7 of 9 discard slips.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:61"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:154"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:245"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:136"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:208"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:223"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:247"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:88"
    }
   ],
   "places": [
    {
     "id": "M03-1",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:292",
     "area": "expense",
     "features": [
      "expense"
     ],
     "what": "Discard slip passes destructive = false: 'Descartar' is brand blue.",
     "why": "Throwing away a typed expense cannot be undone; the same Descartar is loss red on every other form.",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-discard-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M03-2",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:187",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "what": "Discard slip passes destructive = false: 'Descartar' is brand blue.",
     "why": "Same as the expense form.",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-discard-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M03-4",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:476",
     "area": "category",
     "features": [
      "category"
     ],
     "what": "ProblemSlip uses ConfirmSlip to report that nothing was deleted or moved, with 'Revisar'/'Elegir otra'/'Reintentar' as the confirm action.",
     "why": "KDoc: a confirmation of something that cannot be undone; here it is an error report with one way forward.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "category-removal-changed-cobalto-light-font1.0.png"
      },
      {
       "module": "app",
       "file": "category-removal-storage-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M04",
   "component": "SelloChip",
   "ref": "components.html#c-SelloChip",
   "meaning": "One choice in a set (category, source, goal, kind); selected shows the choice made.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:44"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:452"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:307"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:245"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:257"
    }
   ],
   "places": [
    {
     "id": "M04-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:454",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "The goal's month is a SelloChip with selected = false always and an Event icon; tapping opens the month sheet.",
     "why": "A choice chip used as a button: it is never selected and is not one of a set. Every other form's date is a FieldPill (ExpenseEntryScreen.kt:395 and 10 more).",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "goal-editor-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M05",
   "component": "EntryLine",
   "ref": "components.html#c-EntryLine",
   "meaning": "A line that opens an editor (note, source name, repeat); filled, it shows the value and a brand action word (Editar, Cambiar).",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:296"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:464"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:319"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:290"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:81"
    }
   ],
   "places": [
    {
     "id": "M05-1",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:345",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "what": "'Pausar' is an EntryLine: one tap pauses the schedule at once (ScheduleChange.Paused), and the brand action slot holds a fact, 'Próximo: jue 5 nov'.",
     "why": "EntryLine opens an editor; here it performs a write, and its action word is a date. Its counterpart Reanudar is an outlined SelloButton (ScheduleEditorScreen.kt:335).",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M06",
   "component": "SelloIcon.Close vs SelloIcon.ArrowBack (top bar)",
   "ref": "components.html#c-SelloScaffold",
   "meaning": "A full-screen form leaves with Close (×, 'Cerrar'); a pushed screen goes back with ArrowBack.",
   "evidence": [
    {
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:191"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:143"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:117"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:115"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:147"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:106"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:41"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:229"
    }
   ],
   "places": [
    {
     "id": "M06-1",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:153",
     "area": "category",
     "features": [
      "category"
     ],
     "what": "Nueva/Editar categoría leaves with ArrowBack read 'Volver'.",
     "why": "It is a full-screen form with a draft and a discard question, like the goal, investment and schedule editors, which all use Close.",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "category-editor-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M07",
   "component": "Stamp(ink)",
   "ref": "components.html#c-Stamp",
   "meaning": "Good (gain) = a good or completed result (Guardado, Restaurado, Borrado, en meta); Over (loss) = over a limit or a loss; Warn = a review status only; Neutral = a state; Done (brand) unused elsewhere.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Stamp.kt:26"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:149"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:237"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Restore.kt:85"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:580"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:587"
    }
   ],
   "places": [
    {
     "id": "M07-1",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:308",
     "area": "settings",
     "features": [
      "settings"
     ],
     "what": "'Guardado' for a recent backup is StampInk.Done (brand).",
     "why": "The same word for the same fact is StampInk.Good on Respaldo (BackupScreen.kt:149), like every other completed stamp.",
     "unsure": false,
     "shots": [
      {
       "module": "app",
       "file": "settings-loaded-cobalto-light-font1.0.png"
      },
      {
       "module": "app",
       "file": "backup-ready-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M07-2",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:309",
     "area": "settings",
     "features": [
      "settings"
     ],
     "what": "'Pendiente' (backup is old) uses StampInk.Warn.",
     "why": "KDoc reserves Warn for a review status; an old backup is a reminder, not Recibo's Revisar. Low: 'review your backup' can be read as a review status.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "settings-data-old-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M07-3",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:307",
     "area": "settings",
     "features": [
      "settings"
     ],
     "what": "'Sin respaldo' uses StampInk.Over (loss red).",
     "why": "Over means over a limit or a loss (Recibo, Inversiones); no backup is neither.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "settings-data-never-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M08",
   "component": "ButtonPair",
   "ref": "components.html#c-ButtonPair",
   "meaning": "Two buttons side by side: first = the outlined dismiss/secondary answer, second = the filled action (stacked: first on top).",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:234"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:158"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:226"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:305"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:455"
    }
   ],
   "places": [
    {
     "id": "M08-1",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:254",
     "area": "settings",
     "features": [
      "settings"
     ],
     "what": "Filled 'Respaldar' in the first slot and outlined 'Restaurar' in the second.",
     "why": "Every other ButtonPair puts the outlined button first and the filled one second; here the slots are swapped. Both are forward actions (no cancel), so the swap may be intended.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "settings-loaded-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M08-2",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:273",
     "area": "design-system",
     "features": [],
     "what": "When ButtonPair stacks (ConfirmSlip with content, ChoiceSlip), the dismiss/Cancelar goes on top and the action under it.",
     "why": "Inline confirmations stacked by hand put the loss action on top and Cancelar under it (ResetScreen.kt:173/180, RestoreSection.kt:183/194, StartupFailureScreen.kt:260/271), so the order flips when a ConfirmSlip's labels do not fit.",
     "unsure": true,
     "shots": []
    }
   ]
  },
  {
   "id": "M09",
   "component": "SelloIcon.ArrowBack (as a mark)",
   "ref": "components.html#c-painter",
   "meaning": "ArrowBack is the navigation 'back' arrow of pushed screens.",
   "evidence": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:187"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:229"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:153"
    }
   ],
   "places": [
    {
     "id": "M09-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:448",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "A withdrawal's movement mark is the ArrowBack icon.",
     "why": "The navigation arrow reused as 'money going back'. Consistent in all five movement lists, so it may be a deliberate glyph choice; no icon for withdrawal exists in SelloIcon.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "goal-detail-movements-claro.png"
      }
     ]
    },
    {
     "id": "M09-2",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:340",
     "area": "investments",
     "features": [
      "investments"
     ],
     "what": "Withdrawal mark is ArrowBack.",
     "why": "As above.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "investment-detail-movements-all-claro.png"
      }
     ]
    },
    {
     "id": "M09-3",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:99",
     "area": "investments",
     "features": [
      "investments"
     ],
     "what": "Withdrawal mark on the bulk receipt is ArrowBack.",
     "why": "As above.",
     "unsure": true,
     "shots": []
    },
    {
     "id": "M09-4",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:239",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "Withdrawal mark on the goal bulk receipt is ArrowBack (also :401).",
     "why": "As above.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "goal-bulk-removed-claro.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M10",
   "component": "ButtonTone (the kind's tone on a record button)",
   "ref": "components.html#c-SelloButton",
   "meaning": "A form's main button takes its kind's tone: expenses Brand, income and goals Gain, investments Ink; the unknown-outcome 'Comprobar' keeps the same tone.",
   "evidence": [
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:420"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:383"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:335"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:368"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:280"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:456"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:494"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:199"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/InvestmentDestinations.kt:187"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/GoalDestinations.kt:227"
    }
   ],
   "places": [
    {
     "id": "M10-1",
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:440",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "what": "'Anotar N ingresos' (and the check-again button at :433) is Brand, while the receipt's lines and total are gain green.",
     "why": "Income records in Gain on Anotar ingreso and on an income schedule. Low because Ingresos' own dock (IncomeDestinations.kt:54) is also Brand, so income is split 2-2.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "bulk-incomes-three-lines-claro.png"
      }
     ]
    },
    {
     "id": "M10-2",
     "file": "app/src/main/java/com/software/sello/feature/investments/UsdRateScreen.kt:129",
     "area": "investments",
     "features": [
      "investments"
     ],
     "what": "'Cambiar la tasa' is Outlined Brand; the form it opens submits in Ink.",
     "why": "Investments' in-slip action is Ink (InvestmentDetailScreen.kt:110) and every investment form submits in Ink.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "usd-rate-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M10-3",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:251",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "The unknown-outcome 'Comprobar si se guardó/borró' is Brand where the editor's submit is Gain.",
     "why": "Every other goal, income and investment form keeps its kind's tone on the check-again button (GoalMovementScreen.kt:335, IncomeFormScreen.kt:420, InvestmentEditorScreen.kt:280).",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "goal-editor-unresolved-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M11",
   "component": "SelloButton(kind=Filled)",
   "ref": "components.html#c-SelloButton",
   "meaning": "The one filled button of a screen (the dock aside): its main forward action.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:26"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:609"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:110"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:153"
    }
   ],
   "places": [
    {
     "id": "M11-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:142",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "Each reached goal card carries a filled 'Marcar como lograda', so two reached goals give two filled buttons plus the filled dock.",
     "why": "'One filled button per screen'. Low: the design page documents the card's button (MetasScreen.kt:139 comment), and Configuración also shows a filled Respaldar beside the dock.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "metas-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M11-2",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:306",
     "area": "goals",
     "features": [
      "goals"
     ],
     "what": "Filled 'Marcar como lograda' in the hero while the filled 'Aportar a esta meta' dock is also shown.",
     "why": "Two filled buttons on one screen; same doubt as Metas.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "goal-detail-reached-claro.png"
      }
     ]
    },
    {
     "id": "M11-3",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:593",
     "area": "category",
     "features": [
      "category"
     ],
     "what": "On a save conflict the filled primary is 'Volver a cargar', which throws away what was typed; 'Conservar mis cambios' is outlined.",
     "why": "Elsewhere discarding typed work is never the plain primary: it is behind a loss-red confirmation. Low: the comment says the person decides, and KDoc keeps Loss for confirmations only.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "category-editor-conflict-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "M12",
   "component": "Semantic ink loss / warn / gain on text",
   "ref": "foundations.html",
   "meaning": "loss = something wrong to fix or money lost/over; warn = a doubt to check (unknown outcome, data that changed); gain = money gained or a good verdict.",
   "evidence": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/FieldMessage.kt:22"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:198"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:459"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:527"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:179"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:342"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:70"
    }
   ],
   "places": [
    {
     "id": "M12-3",
     "file": "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:156",
     "area": "reset",
     "features": [
      "reset"
     ],
     "what": "'The counts changed while you confirmed' is in loss red.",
     "why": "A doubt to check, said in warn by the carry correction list (Carry.kt:333) and the carry drop (CategoryCarry.kt:342).",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "reset-refreshed-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M12-4",
     "file": "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:175",
     "area": "backup",
     "features": [
      "backup"
     ],
     "what": "The restore preview's 'figures changed' note is in loss red.",
     "why": "Same as ResetScreen.kt:156.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "backup-restore-refreshed-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M12-5",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:211",
     "area": "category",
     "features": [
      "category"
     ],
     "what": "A carry refusal in the carry card is in warn ink.",
     "why": "The same text (carryProblemText) is a FieldTone.Error in loss red when the card is absent (CategoryEditorScreen.kt:213); a refusal is something to fix.",
     "unsure": true,
     "shots": []
    },
    {
     "id": "M12-6",
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:400",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "what": "Spending higher than the comparison month is in warn ink.",
     "why": "Warn is for a doubt or review status; Stamp's KDoc says never ordinary overspending. Low: this is text, not a stamp.",
     "unsure": true,
     "shots": [
      {
       "module": "app",
       "file": "category-detail-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "M12-7",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:230",
     "area": "category",
     "features": [
      "category"
     ],
     "what": "'Carry turned on/off' confirmation text is in gain green, including 'turned off'.",
     "why": "Gain is for money gained or a good verdict; a setting saved is not a gain, and saves are confirmed elsewhere by a SelloNotice check.",
     "unsure": true,
     "shots": []
    }
   ]
  }
 ],
 "copies": [
  {
   "id": "C01",
   "component": "MovementDatePicker",
   "ref": "components.html#c-MovementDatePicker",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:146",
   "copies": [
    {
     "id": "C01-1",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:673",
     "area": "expense",
     "features": [
      "expense"
     ],
     "differs": "DayPicker: the same DatePickerDialog with an Aceptar SelloButton; the allowed range flips to today-or-later while Se repite is on, and the confirm carries a test tag.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-date-picker-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C01-2",
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:498",
     "area": "income",
     "features": [
      "income"
     ],
     "differs": "IncomeDatePicker: the same dialog; like the expense copy, the range flips to today-or-later while Se repite is on.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "income-form-date-picker-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C01-3",
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:566",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "differs": "DayPicker: identical range (no day after today); only adds a test tag on Aceptar.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "bulk-expenses-date-picker-claro.png"
      }
     ]
    },
    {
     "id": "C01-4",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:738",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "FormDatePicker: identical range (no day after today); opens on the value date or the rate date.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "investment-editor-date-picker-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C01-5",
     "file": "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:220",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "RateDatePicker: identical (no day after today).",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    },
    {
     "id": "C01-6",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:450",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "differs": "StartPicker: the same dialog with the opposite range, from tomorrow on.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-start-picker-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C01-7",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:243",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "differs": "EndPicker (shared repeat sheet): the same dialog with the range on or after the start day; test tag on Aceptar.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-repeat-end-picker-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C02",
   "component": "MovementCloseButton",
   "ref": "components.html#c-MovementCloseButton",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:39",
   "copies": [
    {
     "id": "C02-1",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:190",
     "area": "expense",
     "features": [
      "expense"
     ],
     "differs": "identical (IconButton, SelloIcon.Close, expense_entry_close).",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C02-2",
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:142",
     "area": "income",
     "features": [
      "income"
     ],
     "differs": "identical.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "income-form-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C02-3",
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:116",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "differs": "adds enabled = !state.busy (MovementCloseButton has no enabled parameter).",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "bulk-expenses-three-lines-claro.png"
      }
     ]
    },
    {
     "id": "C02-4",
     "file": "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:72",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "identical plus a test tag.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "usd-rate-entry-ready-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C02-5",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:105",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "differs": "identical.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C02-6",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:111",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "its own string goal_editor_close (same text, Cerrar) and a test tag.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "goal-editor-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C02-7",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:143",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "identical plus a test tag.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "investment-editor-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C03",
   "component": "MovementPane",
   "ref": "components.html#c-MovementPane",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:47",
   "copies": [
    {
     "id": "C03-1",
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:250",
     "area": "income",
     "features": [
      "income"
     ],
     "differs": "IncomePane: identical, line for line.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "income-form-loading-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C03-2",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:363",
     "area": "expense",
     "features": [
      "expense"
     ],
     "differs": "Pane: the same scrolling loading/failed column, but spacing.md between items, no inner spacing.lg padding, and the scaffold padding applied inside the scroll.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-loading-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C04",
   "component": "MovementNoteLine",
   "ref": "components.html#c-MovementNoteLine",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:60",
   "copies": [
    {
     "id": "C04-1",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:464",
     "area": "expense",
     "features": [
      "expense"
     ],
     "differs": "identical EntryLine (EditNote icon, expense_entry_add_note, expense_entry_edit_note, placeholder when blank) plus a test tag.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C04-2",
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:332",
     "area": "income",
     "features": [
      "income"
     ],
     "differs": "identical plus a test tag.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "income-form-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C04-3",
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:398",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "differs": "identical plus a test tag.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "bulk-expenses-three-lines-claro.png"
      }
     ]
    },
    {
     "id": "C04-4",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:290",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "differs": "identical.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "schedule-editor-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C05",
   "component": "MovementDiscardSlip",
   "ref": "components.html#c-MovementDiscardSlip",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:81",
   "copies": [
    {
     "id": "C05-1",
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:223",
     "area": "income",
     "features": [
      "income"
     ],
     "differs": "identical: ConfirmSlip with expense_entry_discard_message and expense_entry_discard_confirm, only its own title.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "income-form-discard-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C05-2",
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:292",
     "area": "expense",
     "features": [
      "expense"
     ],
     "differs": "the same message and Descartar for a new expense, but destructive = false (brand action where MovementDiscardSlip uses loss), its own keep labels, and another message while editing.",
     "identical": false,
     "drift": "Descartar in brand ink where the shared slip uses loss (destructive = false).",
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-discard-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C08",
   "component": "MoreMenu",
   "ref": "components.html#c-MoreMenu",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/MoreMenu.kt:41",
   "copies": [
    {
     "id": "C08-1",
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:256",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "LineActionMenu (long press on a Recibo cell, ReciboComponents.kt:128, and on expense lines, :250): the same DropdownMenu on paper with bodyLarge items that close then run, but no icons, no loss ink (Borrar is drawn in plain ink like Editar) and no disabled state; opened by long press instead of the ⋮.",
     "identical": false,
     "drift": "Borrar is drawn in plain ink, not the menu's loss ink.",
     "unsure": false,
     "doubt": null,
     "shots": []
    },
    {
     "id": "C08-2",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:485",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "differs": "BulkReceipt line menu: the same DropdownMenu with one Quitar item in plain ink, no icon; opened by long press. It removes a typed line, not a record.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C09",
   "component": "ConfirmSlip",
   "ref": "components.html#c-ConfirmSlip",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:124",
   "copies": [
    {
     "id": "C09-1",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:557",
     "area": "category",
     "features": [
      "category"
     ],
     "differs": "RemovalDialog: the same Dialog, 0.55 dim (its own SCRIM_DIM copy), PlainPadding slip, 22 sp headlineSmall title, scrolling content and TearLine(20 dp); instead of Cancelar plus a confirm it takes one custom action or none, and Back or a tap outside is never blocked while busy.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "category-removal-choose-cobalto-light-font1.0.png"
      },
      {
       "module": "app",
       "file": "category-removal-checking-cobalto-light-font1.0.png"
      },
      {
       "module": "app",
       "file": "category-removal-count-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C10",
   "component": "ButtonPair",
   "ref": "components.html#c-ButtonPair",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:238",
   "copies": [
    {
     "id": "C10-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:99",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "replace sheet: a Row spaced 10 dp with outlined Cancelar at weight 1 and a filled action at weight 2 (RepeatSheetActions' 1:2 split); never stacks when a label does not fit, and the action shows only on retry.",
     "identical": false,
     "drift": "Never stacks when a label does not fit, unlike ButtonPair.",
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "goal-replace-refused-claro.png"
      }
     ]
    },
    {
     "id": "C10-2",
     "file": "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:325",
     "area": "startup",
     "features": [
      "startup"
     ],
     "differs": "Empezar de cero: a Row spaced spacing.md with outlined Cancelar and the loss action at 1:1; never stacks.",
     "identical": false,
     "drift": "Never stacks when a label does not fit, unlike ButtonPair.",
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "startup-failure-start-over-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C10-3",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:95",
     "area": "design-system (ConfirmSlip with message)",
     "features": [],
     "differs": "the message ConfirmSlip's own Row with 1:1 weights and spacing.md; never stacks (the content ConfirmSlip and both ChoiceSlips use ButtonPair).",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "expense-entry-discard-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C11",
   "component": "SelloButton(kind = Filled)",
   "ref": "components.html#c-SelloButton",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:42",
   "copies": [
    {
     "id": "C11-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:202",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "ReachedRow's Marcar como lograda: a gain-filled pill Box, 44 dp, bodySmall W800 label; while busy it fades to 0.6 with no spinner (SelloButton stays at full strength with a spinner).",
     "identical": false,
     "drift": "Busy fades the button instead of showing SelloButton's spinner.",
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "recibo-goals-five-reached-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C11-2",
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:460",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "celebration Listo: a button-shaped Box 56 dp with onGain fill and gain titleMedium W800 text; no tone gives these inverted colours; no disabled or loading look.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C12",
   "component": "SelloButton(kind = Text)",
   "ref": "components.html#c-SelloButton",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:42",
   "copies": [
    {
     "id": "C12-1",
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:267",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "AdjustBudgetLink (also used on CategoryDetailScreen.kt:321): a clickable Row with bodyLarge W800 brand text and a trailing 20 dp chevron; no button shape, ripple bounds, disabled or loading state.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "recibo-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C12-2",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:286",
     "area": "settings",
     "features": [
      "settings"
     ],
     "differs": "Ver qué contiene: a clickable Row with labelLarge brand text and an 18 dp brand chevron.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "settings-loaded-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C12-3",
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:476",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "Ver mi meta: a clickable Row with bodyMedium W800 onGain text and an 18 dp chevron, on the green flood.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C13",
   "component": "AnswerSlip",
   "ref": "components.html#c-AnswerSlip",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:534",
   "copies": [
    {
     "id": "C13-1",
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:53",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "differs": "ComparisonSlip: the question in bodyLarge W600 inkSoft with 6 dp under it, then FittedMoneyText in default ink; no stamp, no single spoken description; the explanation, rate and bars would fit AnswerSlip's detail slot.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "resumen-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C13-2",
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:213",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "HeaderSlip: headline in bodyLarge inkSoft with a Stamp at its default angle and 6 dp padding (AnswerSlip: −5°, 8 dp), then FittedMoneyText from 44 sp beside a 56 dp CategoryCircle, read as one description; a name row with edit sits above.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "category-detail-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C14",
   "component": "Footnote",
   "ref": "components.html#c-Footnote",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:583",
   "copies": [
    {
     "id": "C14-1",
     "file": "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:400",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "QuietLine: the same icon-and-bodySmall-inkSoft line without the click and chevron, the icon in inkSoft instead of brand, min 32 dp with 6 dp on top (Footnote 48 dp). The rate line switches between QuietLine (:198) and Footnote (:200) depending on whether it can open.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "inversiones-foot-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C15",
   "component": "FieldMessageText",
   "ref": "components.html#c-FieldMessageText",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/FieldMessage.kt:46",
   "copies": [
    {
     "id": "C15-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:196",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "SheetError: FieldTone's mapping by hand (warn for unknown, loss for refused), the Error icon and a polite live region, but on a warnSoft/lossSoft 12 dp banner with a 20 dp icon and bodyMedium ink text with a bold lead.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "goal-replace-refused-claro.png"
      }
     ]
    },
    {
     "id": "C15-2",
     "file": "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:458",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "limit sheet's save error: warn ink for unknown, loss otherwise, bodyMedium, no icon and no live region.",
     "identical": false,
     "drift": "No icon and no live region, unlike FieldMessageText.",
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "limit-sheet-unknown-claro.png"
      },
      {
       "module": "app",
       "file": "limit-sheet-refused-claro.png"
      }
     ]
    },
    {
     "id": "C15-3",
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:524",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "delete problem under the list: warn ink for unknown, loss otherwise, bodyMedium with a live region, no icon.",
     "identical": false,
     "drift": null,
     "unsure": true,
     "doubt": "FieldMessageText's KDoc is about a control's own value and the entry's save message; this one reports a delete from a list.",
     "shots": [
      {
       "module": "app",
       "file": "category-detail-delete-unknown-claro.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C16",
   "component": "CarryBreakdown (CarryBreakdownSection)",
   "ref": "components.html#c-CarryBreakdown",
   "shared": "app/src/main/java/com/software/sello/presentation/carry/Carry.kt:106",
   "copies": [
    {
     "id": "C16-1",
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:322",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "history selection: Límite base, the carry and Límite del mes written as three LeaderLines by hand; the capacity is not Strong, a negative carry is not in loss ink, the weights are default (CarryBreakdown: W700/W900) and the carry has no words caption.",
     "identical": false,
     "drift": "A negative carry is not in loss ink and the capacity is not strong.",
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C17",
   "component": "MilestoneRow",
   "ref": "components.html#c-MilestoneRow",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:341",
   "copies": [
    {
     "id": "C17-1",
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:316",
     "area": "goals",
     "features": [
      "goals"
     ],
     "differs": "GoalCard: a Row with SpaceBetween over MilestonePlace; no scroll, no landing or relight stagger, and the stamps spread to the card's edges (MilestoneRow's evenly centres each in an equal share).",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "metas-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C18",
   "component": "Modifier.dashedOutline",
   "ref": null,
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:46",
   "copies": [
    {
     "id": "C18-1",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:79",
     "area": "category, goals, investments (IconPickerCircle)",
     "features": [
      "goals",
      "investments",
      "category"
     ],
     "differs": "the empty icon ring drawn with drawCircle, 2.5 dp, the same 5/4 dp dashes.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "category-editor-new-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C18-2",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconChoiceGrid.kt:132",
     "area": "category (icon sheet)",
     "features": [
      "category"
     ],
     "differs": "private dashedBorder for an icon in use: follows any shape, 1.5 dp, 4/3 dp dashes.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "category-editor-icon-sheet-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C18-3",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:288",
     "area": "goals (MilestonePlace)",
     "features": [
      "goals"
     ],
     "differs": "an unearned stamp's ring: drawCircle with 4/3 dp dashes.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "metas-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C18-4",
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:576",
     "area": "bulk (BulkReceipt)",
     "features": [
      "bulk"
     ],
     "differs": "a line mark with no icon yet: a 2 dp dashed circle with 4/3 dp dashes, loss ink when missing.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "design-system",
       "file": "bulk-receipt-missing-category-claro.png"
      }
     ]
    },
    {
     "id": "C18-5",
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:179",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "InvestmentRow's circle with no value: a 2 dp dashed circle with 4/3 dp dashes in outline ink.",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C19",
   "component": "MonthNames",
   "ref": null,
   "shared": "app/src/main/java/com/software/sello/presentation/month/MonthNames.kt:30",
   "copies": [
    {
     "id": "C19-1",
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenRenderModel.kt:192",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "differs": "monthName/monthTitle use java.time's LLLL in es-CO (formatter at :211) instead of the month_names array; same words today, but they skip the resources (MonthNames has no Resources factory for a render model, as DateLabels has).",
     "identical": false,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "resumen-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C20",
   "component": "DateLabels.month",
   "ref": null,
   "shared": "app/src/main/java/com/software/sello/presentation/date/DateLabels.kt:51",
   "copies": [
    {
     "id": "C20-1",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:813",
     "area": "category",
     "features": [
      "category"
     ],
     "differs": "shortRange reads month_names_abbreviated itself to write the short month ('oct 2026'), which DateLabels.month/monthYear already give.",
     "identical": false,
     "drift": null,
     "unsure": true,
     "doubt": "not checked that the count shot shows the short range.",
     "shots": [
      {
       "module": "app",
       "file": "category-removal-count-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C21",
   "component": "MoneyTextValue.text() (recibo, true minus only)",
   "ref": null,
   "shared": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:610",
   "copies": [
    {
     "id": "C21-1",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:858",
     "area": "category",
     "features": [
      "category"
     ],
     "differs": "shown(): identical body.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": []
    }
   ]
  },
  {
   "id": "C22",
   "component": "MoneyText sign rule (signText, + and true minus)",
   "ref": "components.html#c-MoneyText",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:138",
   "copies": [
    {
     "id": "C22-1",
     "file": "app/src/main/java/com/software/sello/presentation/carry/Carry.kt:294",
     "area": "recibo, resumen (carry)",
     "features": [
      "recibo",
      "resumen"
     ],
     "differs": "text(): identical +/−/none body.",
     "identical": true,
     "drift": null,
     "unsure": true,
     "doubt": "signText is private to MoneyText.kt and :app has no shared signed-text helper, so these copy each other rather than skip a reference that exists.",
     "shots": []
    },
    {
     "id": "C22-2",
     "file": "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:591",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "signedText(): identical body; the same file also uses recibo's minus-only text().",
     "identical": true,
     "drift": null,
     "unsure": true,
     "doubt": "as above.",
     "shots": []
    },
    {
     "id": "C22-3",
     "file": "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:503",
     "area": "shell",
     "features": [
      "shell"
     ],
     "differs": "signedDigits(): identical body.",
     "identical": true,
     "drift": null,
     "unsure": true,
     "doubt": "as above.",
     "shots": []
    },
    {
     "id": "C22-4",
     "file": "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:373",
     "area": "investments",
     "features": [
      "investments"
     ],
     "differs": "signed(): identical body.",
     "identical": true,
     "drift": null,
     "unsure": true,
     "doubt": "as above.",
     "shots": []
    },
    {
     "id": "C22-5",
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:118",
     "area": "category",
     "features": [
      "category"
     ],
     "differs": "text(): the same rule as an if/else chain.",
     "identical": false,
     "drift": null,
     "unsure": true,
     "doubt": "as above.",
     "shots": [
      {
       "module": "app",
       "file": "carry-drop-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C23",
   "component": "RestoreHeading",
   "ref": "components.html#c-RestoreHeading",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/Restore.kt:28",
   "copies": [
    {
     "id": "C23-1",
     "file": "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:143",
     "area": "backup",
     "features": [
      "backup"
     ],
     "differs": "Último respaldo: identical Text (headlineSmall, heading) inline.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "backup-ready-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C23-2",
     "file": "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:214",
     "area": "backup",
     "features": [
      "backup"
     ],
     "differs": "Antes de guardar (disclosure): identical inline.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "backup-ready-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C23-3",
     "file": "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:134",
     "area": "startup",
     "features": [
      "startup"
     ],
     "differs": "the failure title: identical inline, followed by Spacer(xs) and a bodyLarge inkSoft Text, which is RestoreBody by hand.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "startup-failure-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C23-4",
     "file": "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:296",
     "area": "startup",
     "features": [
      "startup"
     ],
     "differs": "Empezar de cero title: identical inline, with RestoreBody by hand under it.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "startup-failure-start-over-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C24",
   "component": "SectionTitle (Configuración)",
   "ref": null,
   "shared": "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:325",
   "copies": [
    {
     "id": "C24-1",
     "file": "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:119",
     "area": "settings",
     "features": [
      "settings"
     ],
     "differs": "Tus datos' section title: identical inline (titleMedium, spacing.sm under it, heading) on the same screen; SectionTitle is private to SettingsScreen.kt.",
     "identical": true,
     "drift": null,
     "unsure": false,
     "doubt": null,
     "shots": [
      {
       "module": "app",
       "file": "settings-loaded-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  },
  {
   "id": "C25",
   "component": "EntryHeading",
   "ref": "components.html#c-EntryHeading",
   "shared": "design-system/src/main/java/com/software/sello/designsystem/component/EntryHeading.kt:19",
   "copies": [
    {
     "id": "C25-1",
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:163",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "differs": "Group: an uppercase labelMedium W800 inkSoft heading without EntryHeading's 0.05 em letter spacing.",
     "identical": false,
     "drift": null,
     "unsure": true,
     "doubt": "EntryHeading's KDoc names form sections; this heads a list group.",
     "shots": [
      {
       "module": "app",
       "file": "category-fixed-coming-cobalto-light-font1.0.png"
      }
     ]
    },
    {
     "id": "C25-2",
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:319",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "differs": "group heading: the same inline Text as the fixed-payments Group.",
     "identical": false,
     "drift": null,
     "unsure": true,
     "doubt": "as above.",
     "shots": [
      {
       "module": "app",
       "file": "schedules-cobalto-light-font1.0.png"
      }
     ]
    }
   ]
  }
 ],
 "hardcoded": [
  {
   "id": "H01",
   "kind": "colour",
   "value": "Color.White (visible fill)",
   "token": "SelloColors.paper (Claro paper is #FFFFFF today) or onBand",
   "risk": true,
   "note": "Under a dynamic scheme the surface is no longer white, so these pills/chips would stay white while everything around them moves.",
   "shots": [
    {
     "module": "app",
     "file": "metas-cobalto-light-font1.0.png"
    },
    {
     "module": "app",
     "file": "inversiones-cobalto-light-font1.0.png"
    },
    {
     "module": "app",
     "file": "shell-past-month-cobalto-light-font1.0.png"
    },
    {
     "module": "design-system",
     "file": "components-buttons-and-chips.png"
    }
   ],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:197",
     "area": "design-system:Controls",
     "features": [],
     "note": "BandChip: BandChip fill in Claro when not onPaper: the 'Nueva meta' / 'Nueva inversión' chip on the Metas and Inversiones band (Oscuro uses colors.paper)"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:200",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "PastMonthRibbon: PastMonthRibbon 'return to this month' pill fill in Claro (Oscuro uses colors.paper)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:501",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Confetti: Confetti: two of the five chad colours are fixed white, in both themes, over the gain flood"
    }
   ]
  },
  {
   "id": "H02",
   "kind": "colour",
   "value": "Color(0xFF050814).copy(alpha = 0.55f)",
   "token": "MaterialTheme.colorScheme.scrim (Color.Black) / the window dim the other slips use (SCRIM_ALPHA)",
   "risk": true,
   "note": "The only Color(0x...) literal outside the palette files; it keeps a Cobalto navy cast under Violeta and would under any dynamic scheme.",
   "shots": [
    {
     "module": "app",
     "file": "expense-entry-repeat-sheet-cobalto-light-font1.0.png"
    },
    {
     "module": "app",
     "file": "income-form-repeat-sheet-cobalto-light-font1.0.png"
    },
    {
     "module": "app",
     "file": "schedule-editor-repeat-days-cobalto-light-font1.0.png"
    }
   ],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:282",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "file-level constant: private val Scrim: a hand-picked near-black navy (Cobalto-tinted) at 55%"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:71",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "RepeatSheet: RepeatSheet draws it full-screen behind the repeat sheet"
    }
   ]
  },
  {
   "id": "H03",
   "kind": "colour",
   "value": "selloColors(ink, isDark = false).band (palette read directly)",
   "token": null,
   "risk": true,
   "note": "A dynamic option would need its own swatch built from the system scheme; the two fixed inks stay fixed by design.",
   "shots": [
    {
     "module": "app",
     "file": "settings-loaded-cobalto-light-font1.0.png"
    }
   ],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:73",
     "area": "settings",
     "features": [
      "settings"
     ],
     "note": "InkSwatches: InkSwatches: each swatch is the light band of its ink, bypassing the current theme (intended: a preview of an ink that is not applied)"
    }
   ]
  },
  {
   "id": "H04",
   "kind": "colour",
   "value": "#FF090D1C android:windowBackground (values-night)",
   "token": "CobaltoDark.desk (copied by hand; XML cannot read Compose tokens)",
   "risk": true,
   "note": "A dynamic scheme cannot be applied here before the first frame; the pre-frame window would show Cobalto desk, then jump.",
   "shots": [],
   "places": [
    {
     "file": "app/src/main/res/values-night/themes.xml:9",
     "area": "app:resources",
     "features": [],
     "note": "Theme.Sello (night): window colour before the first Compose frame and behind the Android 12+ splash; Cobalto dark desk even for Violeta-dark users"
    }
   ]
  },
  {
   "id": "H05",
   "kind": "colour",
   "value": "platform default android:windowBackground (values, light)",
   "token": "CobaltoLight.desk (#FFD9E1F7)",
   "risk": true,
   "note": "Not written by hand but not from the theme either; listed because it is a visible colour no Sello or dynamic scheme controls.",
   "shots": [],
   "places": [
    {
     "file": "app/src/main/res/values/themes.xml:4",
     "area": "app:resources",
     "features": [],
     "note": "Theme.Sello (light) sets no windowBackground: the window before the first frame and the splash use the platform Material.Light background, not a Sello colour"
    }
   ]
  },
  {
   "id": "H06",
   "kind": "colour",
   "value": "Palette literals for the roles Material has (brand, onBrand, brandSoft, desk, paper*, ink*, outline, rule, pastBar*)",
   "token": "these ARE the tokens: SelloColors via SelloTheme.colors",
   "risk": true,
   "note": "Every screen reads these through SelloTheme.colors (288 reads, 0 reads of MaterialTheme.colorScheme), so a dynamic scheme only reaches the app if SelloColors itself is built from it.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/CobaltoPalette.kt:6",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: CobaltoLight: 20 Color(0x...) literals, lines 9-28"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/CobaltoPalette.kt:34",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: CobaltoDark: 20 literals, lines 37-56"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/VioletaPalette.kt:6",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: VioletaLight: 14 literals over CobaltoLight, lines 8-21"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/VioletaPalette.kt:24",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: VioletaDark: 16 literals over CobaltoDark, lines 26-41"
    }
   ]
  },
  {
   "id": "H07",
   "kind": "colour",
   "value": "Role literals that copy another role instead of referencing it",
   "token": "brand / ink / semantic.gain",
   "risk": true,
   "note": "If brand/ink were mapped from a dynamic scheme these copies would not move with them.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/CobaltoPalette.kt:21",
     "area": "design-system:theme",
     "features": [],
     "note": "CobaltoLight.band = same literal as brand"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/CobaltoPalette.kt:23",
     "area": "design-system:theme",
     "features": [],
     "note": "CobaltoLight.goalBand = same literal as LightSemantic.gain"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/CobaltoPalette.kt:24",
     "area": "design-system:theme",
     "features": [],
     "note": "CobaltoLight.investmentBand = same literal as ink"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/VioletaPalette.kt:18",
     "area": "design-system:theme",
     "features": [],
     "note": "VioletaLight.band = same literal as brand"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/VioletaPalette.kt:19",
     "area": "design-system:theme",
     "features": [],
     "note": "VioletaLight.investmentBand = same literal as ink"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:146",
     "area": "design-system:theme",
     "features": [],
     "note": "selloColors: LightInvestments 'on' colours 0xFF0E1630 = Cobalto ink, also under Violeta (lines 146, 148, 150)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:156",
     "area": "design-system:theme",
     "features": [],
     "note": "selloColors: DarkInvestments slate 'on' 0xFF0E1630 = Cobalto light ink"
    }
   ]
  },
  {
   "id": "H08",
   "kind": "colour",
   "value": "Semantic literals: gain, loss, warn, warnSoft, dividend (+ on/soft)",
   "token": "SelloColors.semantic",
   "risk": true,
   "note": "Shared by both inks on purpose (KDoc: meanings stay the same). gain is pushed into Material tertiary and loss into error; warn, warnSoft and dividend have no Material role. A dynamic tertiary is not green, so gain must stay fixed or be harmonised, never taken from the scheme.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:86",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: LightSemantic: 11 literals, lines 87-97"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:100",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: DarkSemantic: 11 literals, lines 101-111"
    }
   ]
  },
  {
   "id": "H09",
   "kind": "colour",
   "value": "Category palette (12 per theme)",
   "token": "SelloColors.categories",
   "risk": true,
   "note": "No Material role; same in both inks.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:114",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: LightCategories: 12 literals, lines 115-126"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:129",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: DarkCategories: 12 literals, lines 130-141"
    }
   ]
  },
  {
   "id": "H10",
   "kind": "colour",
   "value": "Investment inks (5 fills + 5 on-colours per theme)",
   "token": "SelloColors.investments / investmentInk(rank)",
   "risk": true,
   "note": "No Material role. Rank 0 is brand/onBrand (SelloColors.kt:60), so under a dynamic primary the first investment would move and the other five would not.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:145",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: LightInvestments: 10 literals, lines 146-150"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:154",
     "area": "design-system:theme",
     "features": [],
     "note": "file-level constant: DarkInvestments: 10 literals, lines 155-159"
    }
   ]
  },
  {
   "id": "H11",
   "kind": "colour",
   "value": "Launcher icon literals #FFFFFFFF / #FF1F3BE0",
   "token": "CobaltoLight.paper / CobaltoLight.brand (copied by hand)",
   "risk": false,
   "note": "The full-colour icon is fixed by nature; Android 13+ themed icons already use the monochrome layer (mipmap-anydpi/ic_launcher.xml:5, ic_launcher_round.xml:5), which follows the wallpaper.",
   "shots": [],
   "places": [
    {
     "file": "app/src/main/res/drawable/ic_launcher_background.xml:3",
     "area": "app:resources",
     "features": [],
     "note": "adaptive icon background: white"
    },
    {
     "file": "app/src/main/res/drawable/ic_launcher_foreground.xml:10",
     "area": "app:resources",
     "features": [],
     "note": "stamp outline stroke: Cobalto brand"
    },
    {
     "file": "app/src/main/res/drawable/ic_launcher_foreground.xml:15",
     "area": "app:resources",
     "features": [],
     "note": "stamp letter fill: Cobalto brand"
    }
   ]
  },
  {
   "id": "H12",
   "kind": "colour",
   "value": "#FF000000 in the monochrome launcher layer",
   "token": null,
   "risk": false,
   "note": "The system uses only its alpha and tints it with the wallpaper colours.",
   "shots": [],
   "places": [
    {
     "file": "app/src/main/res/drawable/ic_launcher_monochrome.xml:10",
     "area": "app:resources",
     "features": [],
     "note": "stroke"
    },
    {
     "file": "app/src/main/res/drawable/ic_launcher_monochrome.xml:15",
     "area": "app:resources",
     "features": [],
     "note": "fill"
    }
   ]
  },
  {
   "id": "H13",
   "kind": "colour",
   "value": "Color.Black scrim / platform window dim",
   "token": "MaterialTheme.colorScheme.scrim",
   "risk": false,
   "note": "Black is also the scrim of Material dynamic schemes, so nothing would drift; the window dim colour is not themeable at all.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/theme/SelloMaterial.kt:44",
     "area": "design-system:theme",
     "features": [],
     "note": "SelloColors.toMaterialColorScheme: Material scrim slot fixed to black (also used by ModalBottomSheet and DatePickerDialog defaults)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:81",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: ConfirmSlip window dim (platform dims with black at SCRIM_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:139",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: ConfirmSlip (second overload) window dim"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:210",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: ChoiceSlip window dim"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:288",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: ChoiceSlip (second overload) window dim"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:154",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "OptionSlip: OptionSlip window dim"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:567",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "RemovalDialog: RemovalDialog window dim (own SCRIM_DIM copy of SCRIM_ALPHA)"
    }
   ]
  },
  {
   "id": "H14",
   "kind": "colour",
   "value": "Color.Black / Color.Transparent as an alpha mask (BlendMode.DstIn)",
   "token": null,
   "risk": false,
   "note": "Only the alpha channel is used; no colour reaches the screen.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:172",
     "area": "design-system:Chips",
     "features": [],
     "note": "ChipFlow: ChipFlow single-line trailing fade mask"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:626",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "Modifier.fadeEdges: fadeEdges top mask"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:634",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "Modifier.fadeEdges: fadeEdges bottom mask"
    }
   ]
  },
  {
   "id": "H15",
   "kind": "colour",
   "value": "Color.Transparent / android TRANSPARENT (nothing drawn)",
   "token": null,
   "risk": false,
   "note": "Listed for completeness; transparent shows whatever theme colour is under it.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:149",
     "area": "design-system:Controls",
     "features": [],
     "note": "SelloNavigationBar: SelloNavigationBar: unselected item has no pill"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:263",
     "area": "design-system:Controls",
     "features": [],
     "note": "ChipRows: ChipRows fade: from transparent to colors.desk (theme role)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:456",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: NoteComposer: no border unless error (loss)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:56",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: Outlined/Text button container"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:169",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: ChoiceRow: unpicked row has no fill"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:367",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPinPill: GoalPinPill: unselected has no fill"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:368",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPinPill: GoalPinPill: selected has no border"
    },
    {
     "file": "app/src/main/java/com/software/sello/platform/SystemBars.kt:41",
     "area": "platform",
     "features": [],
     "note": "SystemBarsFollowTheme: status bar scrim transparent (edge-to-edge)"
    },
    {
     "file": "app/src/main/java/com/software/sello/platform/SystemBars.kt:44",
     "area": "platform",
     "features": [],
     "note": "SystemBarsFollowTheme: navigation bar scrim transparent (edge-to-edge)"
    }
   ]
  },
  {
   "id": "H16",
   "kind": "colour",
   "value": "@android:color/white fill in every SelloIcon vector",
   "token": null,
   "risk": false,
   "note": "75 paths in 75 files; every use goes through Icon(), which tints with a theme role or LocalContentColor, so the white never shows.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/res/drawable/ic_sello_account_balance.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_account_balance_outlined.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_account_balance_wallet.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_add.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_agriculture.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_all_inclusive.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_apartment.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_archive.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_arrow_back.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_arrow_upward.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_autorenew.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_backspace.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_backup.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_block.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_bolt.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_check.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_checkroom.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_chevron_left.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_chevron_right.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_child_care.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_close.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_content_paste.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_contrast.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_credit_card.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_currency_bitcoin.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_currency_exchange.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_delete.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_delete_forever.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_directions_bus.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_directions_car.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_download.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_edit_note.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_error.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_event.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_event_busy.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_fitness_center.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_flag.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_flight.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_handyman.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_home.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_info.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_ink_pen.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_insights.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_keyboard_arrow_down.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_local_cafe.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_local_gas_station.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_medical_services.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_more_vert.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_pause.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_payments.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_pets.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_phone_iphone.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_pie_chart.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_play_arrow.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_receipt_long.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_receipt_long_outlined.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_redeem.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_restaurant.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_savings.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_school.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_sell.xml:8",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_settings.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_shopping_cart.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_sort.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_spa.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_sports_esports.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_star.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_star_outline.xml:2",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_storefront.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_swap_vert.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_theaters.xml:7",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_trending_down.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_trending_up.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_upload_file.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    },
    {
     "file": "design-system/src/main/res/drawable/ic_sello_wifi.xml:4",
     "area": "design-system:icons",
     "features": [],
     "note": "@android:color/white path fill"
    }
   ]
  },
  {
   "id": "H17",
   "kind": "type",
   "value": "fontSize = 9.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:106",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementRow: MilestonePlace(milestone, diameter = 34.dp, labelSize = 9.sp, innerRing = false)"
    }
   ]
  },
  {
   "id": "H18",
   "kind": "type",
   "value": "fontSize = 13.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:433",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: style = stencil.copy(fontSize = 13.sp, letterSpacing = 0.08.em),"
    }
   ]
  },
  {
   "id": "H19",
   "kind": "type",
   "value": "fontSize = 14.sp",
   "token": "bodyLarge / titleSmall (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:213",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MilestonePlace: labelSize: TextUnit = 14.sp,"
    }
   ]
  },
  {
   "id": "H20",
   "kind": "type",
   "value": "fontSize = 15.sp",
   "token": "SelloType.stampSmall (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:374",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: style = stencil.copy(fontSize = 15.sp, letterSpacing = 0.14.em),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:93",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp)"
    }
   ]
  },
  {
   "id": "H21",
   "kind": "type",
   "value": "fontSize = 18.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:389",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: fontSize = 18.sp,"
    }
   ]
  },
  {
   "id": "H22",
   "kind": "type",
   "value": "fontSize = 20.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:98",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "PerformanceLine: fontSize = 20.sp,"
    }
   ]
  },
  {
   "id": "H23",
   "kind": "type",
   "value": "fontSize = 21.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:225",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthSwitcher: style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),"
    }
   ]
  },
  {
   "id": "H24",
   "kind": "type",
   "value": "fontSize = 22.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:576",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "RemovalDialog: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:143",
     "area": "design-system:AmountKeypad",
     "features": [],
     "note": "KeyLabel: style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:89",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:149",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:220",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:297",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:164",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "OptionSlip: style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),"
    }
   ]
  },
  {
   "id": "H25",
   "kind": "type",
   "value": "fontSize = 24.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:96",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthBar: fontSize = 24.sp,"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:142",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "TitleBar: fontSize = 24.sp,"
    }
   ]
  },
  {
   "id": "H26",
   "kind": "type",
   "value": "fontSize = 29.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:347",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: size passed as a value: digits > 9 -> 29.sp"
    }
   ]
  },
  {
   "id": "H27",
   "kind": "type",
   "value": "fontSize = 37.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:348",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: size passed as a value: digits > 6 -> 37.sp"
    }
   ]
  },
  {
   "id": "H28",
   "kind": "type",
   "value": "fontSize = 48.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:323",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Title: fontSize = 48.sp,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:349",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: size passed as a value: else -> 48.sp"
    }
   ]
  },
  {
   "id": "H29",
   "kind": "type",
   "value": "fontSize = 0.56.em",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:172",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "moneyAnnotatedString: fontSize = 0.56.em,"
    }
   ]
  },
  {
   "id": "H30",
   "kind": "type",
   "value": "lineHeight = 22.sp",
   "token": "titleLarge (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:390",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: lineHeight = 22.sp"
    }
   ]
  },
  {
   "id": "H31",
   "kind": "type",
   "value": "lineHeight = 24.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:225",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthSwitcher: style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp, lineHeight = 24.sp),"
    }
   ]
  },
  {
   "id": "H32",
   "kind": "type",
   "value": "lineHeight = 26.sp",
   "token": "headlineSmall (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:143",
     "area": "design-system:AmountKeypad",
     "features": [],
     "note": "KeyLabel: style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),"
    }
   ]
  },
  {
   "id": "H33",
   "kind": "type",
   "value": "lineHeight = 48.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:324",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Title: lineHeight = 48.sp,"
    }
   ]
  },
  {
   "id": "H34",
   "kind": "type",
   "value": "letterSpacing = 0.6.sp",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:72",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ExpenseStubLine: letterSpacing = 0.6.sp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:102",
     "area": "design-system:Plan",
     "features": [],
     "note": "PlanSlip: letterSpacing = 0.6.sp"
    }
   ]
  },
  {
   "id": "H35",
   "kind": "type",
   "value": "letterSpacing = -0.04.em",
   "token": "displayLarge / displayMedium (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:426",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: letterSpacing = (-0.04).em"
    }
   ]
  },
  {
   "id": "H36",
   "kind": "type",
   "value": "letterSpacing = -0.035.em",
   "token": "displaySmall (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:326",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Title: letterSpacing = (-0.035).em"
    }
   ]
  },
  {
   "id": "H37",
   "kind": "type",
   "value": "letterSpacing = -0.02.em",
   "token": "headline* roles (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:279",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalCard: letterSpacing = (-0.02).em"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:98",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthBar: letterSpacing = (-0.02).em"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:144",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "TitleBar: letterSpacing = (-0.02).em"
    }
   ]
  },
  {
   "id": "H38",
   "kind": "type",
   "value": "letterSpacing = 0.em",
   "token": "every body/label role (default) (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:175",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "moneyAnnotatedString: letterSpacing = 0.em,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:194",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "moneyAnnotatedString: withStyle(SpanStyle(letterSpacing = 0.em)) { append(ZERO_WIDTH_SPACE) }"
    }
   ]
  },
  {
   "id": "H39",
   "kind": "type",
   "value": "letterSpacing = 0.05.em",
   "token": "SelloType.stamp* (same value)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/EntryHeading.kt:24",
     "area": "design-system:EntryHeading",
     "features": [],
     "note": "EntryHeading: letterSpacing = 0.05.em"
    }
   ]
  },
  {
   "id": "H40",
   "kind": "type",
   "value": "letterSpacing = 0.06.em",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:168",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementMonthLine: letterSpacing = 0.06.em"
    }
   ]
  },
  {
   "id": "H41",
   "kind": "type",
   "value": "letterSpacing = 0.08.em",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:433",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: style = stencil.copy(fontSize = 13.sp, letterSpacing = 0.08.em),"
    }
   ]
  },
  {
   "id": "H42",
   "kind": "type",
   "value": "letterSpacing = 0.14.em",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:374",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: style = stencil.copy(fontSize = 15.sp, letterSpacing = 0.14.em),"
    }
   ]
  },
  {
   "id": "H43",
   "kind": "type",
   "value": "fontWeight = FontWeight.Normal",
   "token": "bodyLarge/bodyMedium/bodySmall/labelMedium carry 400",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:346",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ChartDetail: fontWeight = FontWeight.Normal"
    }
   ]
  },
  {
   "id": "H44",
   "kind": "type",
   "value": "fontWeight = FontWeight.W400",
   "token": "bodyLarge/bodyMedium/bodySmall/labelMedium carry 400",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:90",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ComparisonSlip: style = typography.labelSmall.copy(fontWeight = FontWeight.W400),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:127",
     "area": "design-system:Carry",
     "features": [],
     "note": "ChangeRow: fontWeight = if (emphasized) FontWeight.W800 else FontWeight.W400,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:204",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "OptionSlip: fontWeight = if (chosen) FontWeight.W700 else FontWeight.W400"
    }
   ]
  },
  {
   "id": "H45",
   "kind": "type",
   "value": "fontWeight = FontWeight.W600",
   "token": "labelSmall carries 600",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:141",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CategoryCell: style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:613",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ResumenHeading: fontWeight = FontWeight.W600,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:68",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ComparisonSlip: fontWeight = FontWeight.W600,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:79",
     "area": "settings",
     "features": [
      "settings"
     ],
     "note": "InkSwatches: fontWeight = if (chosen) FontWeight.W700 else FontWeight.W600"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:105",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthBar: style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:311",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: fontWeight = FontWeight.W600,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:521",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineContent: fontWeight = if (line.titleSoft) FontWeight.W600 else FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:91",
     "area": "design-system:Chips",
     "features": [],
     "note": "SelloChip: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:144",
     "area": "design-system:Chips",
     "features": [],
     "note": "AddChip: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:221",
     "area": "design-system:Chips",
     "features": [],
     "note": "FieldPill: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:328",
     "area": "design-system:Controls",
     "features": [],
     "note": "EntryLine: fontWeight = if (placeholder) null else FontWeight.W600,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:432",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: fontWeight = FontWeight.W600"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:489",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: fontWeight = if (error != null) FontWeight.W600 else null,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:173",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "moneyAnnotatedString: fontWeight = FontWeight.W600,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:250",
     "area": "design-system:Navigation",
     "features": [],
     "note": "MonthGrid: fontWeight = FontWeight.W600"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:108",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "SettingRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W600),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:89",
     "area": "design-system:Switches",
     "features": [],
     "note": "SegmentedSwitch: fontWeight = if (selected) FontWeight.W700 else FontWeight.W600"
    }
   ]
  },
  {
   "id": "H46",
   "kind": "type",
   "value": "fontWeight = FontWeight.W700",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:249",
     "area": "backup",
     "features": [
      "backup"
     ],
     "note": "Cell: fontWeight = if (header) null else FontWeight.W700,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:691",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "LimitTotalLine: textStyle = style.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:174",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ReachedRow: val messageStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:269",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "MetasTotal: fontWeight = FontWeight.W700,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:252",
     "area": "income",
     "features": [
      "income"
     ],
     "note": "MonthPlan: ) { MoneyText(plan.expected, fontWeight = FontWeight.W700) }"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:71",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ExpenseStubLine: fontWeight = FontWeight.W700,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:122",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ExpenseStubLine: style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:61",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "MonthCarrySum: MoneyText(spent, color = colors.ink, fontWeight = FontWeight.W700)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:317",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "SwipeAway: style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:396",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "LimitRow: Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:341",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ClosedMonthInvitation: fontWeight = FontWeight.W700,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:191",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "note": "SummaryTitle: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:292",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "note": "Part: textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W700)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:82",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ComparisonSlip: fontWeight = FontWeight.W700,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:174",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "SpendingRankRow: Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.W700)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:79",
     "area": "settings",
     "features": [
      "settings"
     ],
     "note": "InkSwatches: fontWeight = if (chosen) FontWeight.W700 else FontWeight.W600"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:360",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "DayGrid: fontWeight = FontWeight.W700"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:412",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "IntervalStepper: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:272",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptHeader: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:531",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineContent: fontWeight = if (line.reason != null) FontWeight.W700 else null,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulletList.kt:48",
     "area": "design-system:BulletList",
     "features": [],
     "note": "BulletList: withStyle(SpanStyle(color = colors.ink, fontWeight = FontWeight.W700)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:68",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryBreakdown: FontWeight.W700"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:117",
     "area": "design-system:Carry",
     "features": [],
     "note": "ChangeRow: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:122",
     "area": "design-system:Corrections",
     "features": [],
     "note": "SelloNotice: style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:131",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementRow: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Lines.kt:128",
     "area": "design-system:Lines",
     "features": [],
     "note": "LeaderLine: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Lines.kt:172",
     "area": "design-system:Lines",
     "features": [],
     "note": "TotalLine: Text(label, style = if (bold) body.copy(fontWeight = FontWeight.W700) else body)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:118",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "MoneyStyle.textStyle: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:159",
     "area": "design-system:Plan",
     "features": [],
     "note": "ExpectedChip: fontWeight = FontWeight.W700,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:151",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "ScheduleRow: fontWeight = if (soon) FontWeight.W700 else FontWeight.W800"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:225",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "DatePill: style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W700),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:204",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "OptionSlip: fontWeight = if (chosen) FontWeight.W700 else FontWeight.W400"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:89",
     "area": "design-system:Switches",
     "features": [],
     "note": "SegmentedSwitch: fontWeight = if (selected) FontWeight.W700 else FontWeight.W600"
    }
   ]
  },
  {
   "id": "H47",
   "kind": "type",
   "value": "fontWeight = FontWeight.Bold",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:470",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "note": "NoteSheet: val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:526",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "note": "SourceNameSheet: val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:632",
     "area": "expense",
     "features": [
      "expense"
     ],
     "note": "NoteSheet: val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:450",
     "area": "income",
     "features": [
      "income"
     ],
     "note": "IncomeComposerSheet: val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Movement.kt:120",
     "area": "design-system:Movement",
     "features": [],
     "note": "MovementNoteComposer: val facts = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.Bold)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:292",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "FollowingChoice: withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SelloTheme.colors.ink)) {"
    }
   ]
  },
  {
   "id": "H48",
   "kind": "type",
   "value": "fontWeight = FontWeight.W800",
   "token": "headline*, title*, labelLarge carry 800",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:232",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "ChooseSlip: SpanStyle(color = SelloTheme.colors.brand, fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:342",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "TargetRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:347",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "TargetRow: textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:617",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "ChoiceRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:867",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "bold: withStyle(SpanStyle(fontWeight = FontWeight.W800)) { append(part) }"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:386",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:472",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Actions: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:488",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Actions: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:125",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPill: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:173",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ReachedRow: val nameStyle = LocalTextStyle.current.copy(fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:175",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ReachedRow: val actionStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:272",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalCard: style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:343",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "CompletedGoalRow: Text(name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyLarge)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:382",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPinPill: Text(label, color = colors.onBand, fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:191",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "deleteConsequencesText: addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + movements.length)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:250",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Hero: fontWeight = FontWeight.W800"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:357",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Aportes: fontWeight = FontWeight.W800"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:388",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Movements: fontWeight = FontWeight.W800"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:268",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Choices: SpanStyle(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:79",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ReplaceStarSheet: addStyle(SpanStyle(fontWeight = FontWeight.W800), at, at + goal.length)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:182",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:187",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:225",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "SheetError: withStyle(SpanStyle(fontWeight = FontWeight.W800)) { append(lead) }"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:392",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "bold: withStyle(SpanStyle(fontWeight = FontWeight.W800)) { append(part) }"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:107",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "PerformanceLine: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:199",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentRow: Text(name, fontWeight = FontWeight.W800, style = MaterialTheme.typography.bodyLarge)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:208",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentRow: Text(missingLabel, fontWeight = FontWeight.W800, color = colors.inkSoft)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:214",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentRow: fontWeight = FontWeight.W800"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:237",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentRow: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:400",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "point: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:204",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Chart: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:266",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Picked: MoneyText(point.amount, textStyle = style, fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:275",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Picked: MoneyText(it, textStyle = style, fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:300",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Movements: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:312",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "deleteConsequences: addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + movements.length)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:344",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Fact: addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + figure.length)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:161",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "Fact: val bold = SpanStyle(color = SelloTheme.colors.ink, fontWeight = FontWeight.W800)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:166",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "Group: style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:99",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CarrySumCaption: withStyle(SpanStyle(color = ink, fontWeight = FontWeight.W800)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:148",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CategoryCell: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:164",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CategoryCell: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:403",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "LimitRow: fontWeight = FontWeight.W800"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:252",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ReservedFootnote: withStyle(SpanStyle(fontWeight = FontWeight.W800, color = SelloTheme.colors.ink)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:290",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "AdjustBudgetLink: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:345",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "note": "Groups: style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:172",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "IncomeSlip: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:567",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "Weekdays: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:114",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ComparisonBars: MoneyText(bar.amount, textStyle = style.copy(fontWeight = FontWeight.W800))"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:97",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthBar: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:143",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "TitleBar: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:205",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "PastMonthRibbon: fontWeight = FontWeight.W800,"
    },
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:112",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "PastMonthNotice: addStyle(SpanStyle(fontWeight = FontWeight.W800), start, start + viewed.length)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:232",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "Label: style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:243",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptHeader: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:412",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "BulkLineRow: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:521",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineContent: fontWeight = if (line.titleSoft) FontWeight.W600 else FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:543",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineContent: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:549",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineContent: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:110",
     "area": "design-system:Carry",
     "features": [],
     "note": "ChangeRow: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:127",
     "area": "design-system:Carry",
     "features": [],
     "note": "ChangeRow: fontWeight = if (emphasized) FontWeight.W800 else FontWeight.W400,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:230",
     "area": "design-system:Controls",
     "features": [],
     "note": "BandChip: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:337",
     "area": "design-system:Controls",
     "features": [],
     "note": "EntryLine: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/EntryHeading.kt:23",
     "area": "design-system:EntryHeading",
     "features": [],
     "note": "EntryHeading: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:121",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementRow: Text(title, fontWeight = FontWeight.W800)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:137",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementRow: MoneyText(amount, color = amountColor, fontWeight = FontWeight.W800)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:167",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementMonthLine: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:450",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "StampTicket: Text(title, fontWeight = FontWeight.W800)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:113",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "IconPickerCircle: style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:319",
     "area": "design-system:Paper",
     "features": [],
     "note": "MoreSlip: style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:101",
     "area": "design-system:Plan",
     "features": [],
     "note": "PlanSlip: fontWeight = FontWeight.W800,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:151",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "ScheduleRow: fontWeight = if (soon) FontWeight.W700 else FontWeight.W800"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:208",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "ScheduleRow: style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.W800),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SlipStub.kt:109",
     "area": "design-system:SlipStub",
     "features": [],
     "note": "StubButton: Text(title, fontWeight = FontWeight.W800, color = colors.brand)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Suggestion.kt:60",
     "area": "design-system:Suggestion",
     "features": [],
     "note": "SuggestionNote: fontWeight = FontWeight.W800"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Suggestion.kt:73",
     "area": "design-system:Suggestion",
     "features": [],
     "note": "SuggestionNote: Text(advice, color = colors.onBrandSoft, fontWeight = FontWeight.W800)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SummaryCharts.kt:54",
     "area": "design-system:SummaryCharts",
     "features": [],
     "note": "SplitBar: val labelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.W800)"
    }
   ]
  },
  {
   "id": "H49",
   "kind": "type",
   "value": "fontWeight = FontWeight.W900",
   "token": "display* carry 900",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:325",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Title: fontWeight = FontWeight.W900,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:425",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: fontWeight = FontWeight.W900,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:133",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPill: style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:278",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalCard: fontWeight = FontWeight.W900,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:220",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Hero: fontWeight = FontWeight.W900"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:264",
     "area": "income",
     "features": [
      "income"
     ],
     "note": "MonthPlan: fontWeight = FontWeight.W900"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:99",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "PerformanceLine: fontWeight = FontWeight.W900,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:80",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ExpenseStubLine: fontWeight = FontWeight.W900,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:55",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "MonthCarrySum: fontWeight = FontWeight.W900"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:230",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "IncomeLines: textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W900)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:361",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "ChartSlips: fontWeight = FontWeight.W900"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:318",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: fontWeight = FontWeight.W900,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:325",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: fontWeight = FontWeight.W900,"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:583",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "LineMark: Text(\"?\", fontWeight = FontWeight.W900, color = ink)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:66",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryBreakdown: FontWeight.W900"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Lines.kt:84",
     "area": "design-system:Lines",
     "features": [],
     "note": "LeaderLine: body.copy(fontWeight = FontWeight.W900)"
    }
   ]
  },
  {
   "id": "H50",
   "kind": "shape",
   "value": "RoundedCornerShape(12.dp)",
   "token": null,
   "risk": false,
   "note": "No shape token at 12dp (control 10, button 13); five places agree on 12 without a token.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:495",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "file-level constant: StepShape: repeat step buttons (used :469)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:170",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: ChoiceRow: picked-row fill"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:210",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "SheetError: SheetError box"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:648",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "file-level constant: LineShape: bulk receipt line (used :388, :422, :426)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:344",
     "area": "design-system:Controls",
     "features": [],
     "note": "file-level constant: EntryShape: NoteComposer field and Footnote-like row (used :318, :447, :458)"
    }
   ]
  },
  {
   "id": "H51",
   "kind": "shape",
   "value": "RoundedCornerShape(14.dp) / dashedOutline(corner = 14.dp)",
   "token": "SelloShapes.stampHero (14dp, but a stamp token)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:364",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: Plate outer border of the celebration stamp (a stamp: stampHero fits)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:91",
     "area": "design-system:Plan",
     "features": [],
     "note": "PlanSlip: PlanSlip dashed outline (not a stamp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:317",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ClosedMonthInvitation: ClosedMonthInvitation dashed outline (not a stamp)"
    }
   ]
  },
  {
   "id": "H52",
   "kind": "shape",
   "value": "dashedOutline(corner = 10.dp)",
   "token": "SelloShapes.control (10dp)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:132",
     "area": "design-system:Chips",
     "features": [],
     "note": "AddChip: AddChip dashed outline"
    }
   ]
  },
  {
   "id": "H53",
   "kind": "shape",
   "value": "RoundedCornerShape(8.dp)",
   "token": "SelloShapes.stampSmall (8dp, a stamp token); control is 10dp",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:108",
     "area": "design-system:Switches",
     "features": [],
     "note": "file-level constant: SegmentShape: segmented switch segments (used :69, :79)"
    }
   ]
  },
  {
   "id": "H54",
   "kind": "shape",
   "value": "RoundedCornerShape(4.dp) / CornerRadius(4.dp)",
   "token": "SelloShapes.bar (4dp top corners)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:153",
     "area": "resumen",
     "features": [
      "resumen"
     ],
     "note": "file-level constant: BarShape: comparison bars, all four corners (used :107, :111)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:126",
     "area": "design-system:Charts",
     "features": [],
     "note": "ColumnsChart: ColumnsChart bar top corners drawn by hand in drawBehind"
    }
   ]
  },
  {
   "id": "H55",
   "kind": "shape",
   "value": "Corner 11.dp (slip)",
   "token": null,
   "risk": false,
   "note": "SelloShapes KDoc gives the slip edge to the slip component, so no token is expected; listed for completeness.",
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Slip.kt:80",
     "area": "design-system:Slip",
     "features": [],
     "note": "file-level constant: PlainSlipShape"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Slip.kt:87",
     "area": "design-system:Slip",
     "features": [],
     "note": "Slip: PinkedBottomShape default corner"
    }
   ]
  },
  {
   "id": "H56",
   "kind": "shape",
   "value": "RoundedCornerShape(6.dp)",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:126",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "ExpenseStubLine: warnSoft tag behind a future expense stub"
    }
   ]
  },
  {
   "id": "H57",
   "kind": "shape",
   "value": "RoundedCornerShape(9.dp)",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:366",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Plate: Plate inner border of the celebration stamp"
    }
   ]
  },
  {
   "id": "H58",
   "kind": "shape",
   "value": "dashedOutline(corner = 999.dp)",
   "token": "CircleShape (pill)",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:143",
     "area": "design-system:Plan",
     "features": [],
     "note": "ExpectedChip: ExpectedChip pill outline"
    }
   ]
  },
  {
   "id": "H59",
   "kind": "spacing",
   "value": "4.dp",
   "token": "SelloTheme.spacing.xs",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryComponents.kt:110",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "file-level constant: private val ThumbInset = 4.dp"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:281",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "Archived: modifier = Modifier.padding(top = 4.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:371",
     "area": "income",
     "features": [
      "income"
     ],
     "note": "CameIn: modifier = Modifier.padding(top = 4.dp).testTag(\"income.carryNote\")"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:418",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "HeaderSlip: modifier = Modifier.padding(top = 4.dp).testTag(CATEGORY_DETAIL_DEFICIT_TAG)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:46",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "MonthCarrySum: Column(modifier.fillMaxWidth().padding(top = 4.dp).testTag(RECIBO_CARRY_SUM_TAG)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:69",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "MonthCarrySum: modifier = Modifier.padding(top = 4.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:121",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CarrySumCaption: .padding(bottom = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:299",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: contentPadding = PaddingValues(horizontal = 4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:55",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryBreakdown: Column(modifier.fillMaxWidth().padding(top = 4.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:78",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryBreakdown: modifier = Modifier.padding(top = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:104",
     "area": "design-system:Carry",
     "features": [],
     "note": "ChangeRow: .padding(vertical = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:316",
     "area": "design-system:Charts",
     "features": [],
     "note": "ChartFrame: .padding(horizontal = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:349",
     "area": "design-system:Charts",
     "features": [],
     "note": "AxisLabels: modifier = Modifier.weight(1f).padding(top = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:78",
     "area": "design-system:Chips",
     "features": [],
     "note": "SelloChip: .padding(horizontal = 12.dp, vertical = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:139",
     "area": "design-system:Controls",
     "features": [],
     "note": "SelloNavigationBar: verticalArrangement = Arrangement.spacedBy(4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:224",
     "area": "design-system:Controls",
     "features": [],
     "note": "BandChip: horizontalArrangement = Arrangement.spacedBy(4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:395",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/FieldMessage.kt:55",
     "area": "design-system:FieldMessage",
     "features": [],
     "note": "FieldMessageText: horizontalArrangement = Arrangement.spacedBy(4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/FormField.kt:111",
     "area": "design-system:FormField",
     "features": [],
     "note": "FormField: Row(modifier = Modifier.padding(top = 4.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:178",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementMonthLine: .padding(top = 12.dp, bottom = 4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:55",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "IconPickerCircle: verticalArrangement = Arrangement.spacedBy(4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:90",
     "area": "design-system:Navigation",
     "features": [],
     "note": "SelloNavigationRail: verticalArrangement = Arrangement.spacedBy(4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:316",
     "area": "design-system:Paper",
     "features": [],
     "note": "MoreSlip: Spacer(Modifier.width(4.dp))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:144",
     "area": "design-system:Plan",
     "features": [],
     "note": "ExpectedChip: .padding(horizontal = 10.dp, vertical = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SlipStub.kt:52",
     "area": "design-system:SlipStub",
     "features": [],
     "note": "SlipStub: horizontalArrangement = Arrangement.spacedBy(4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Stamp.kt:76",
     "area": "design-system:Stamp",
     "features": [],
     "note": "Stamp: PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 1.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Stamp.kt:83",
     "area": "design-system:Stamp",
     "features": [],
     "note": "Stamp: PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:93",
     "area": "design-system:Switches",
     "features": [],
     "note": "SegmentedSwitch: modifier = Modifier.padding(horizontal = 4.dp)"
    }
   ]
  },
  {
   "id": "H60",
   "kind": "spacing",
   "value": "8.dp",
   "token": "SelloTheme.spacing.sm",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:136",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "note": "BulkEntryScreen: verticalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:300",
     "area": "bulk",
     "features": [
      "bulk"
     ],
     "note": "Choices: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:310",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "CarryQuestionSlip: modifier = Modifier.padding(top = 8.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:343",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "CarryQuestionSlip: modifier = Modifier.padding(bottom = 8.dp).testTag(EDITOR_CARRY_CHANGED_TAG)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:364",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "CarryQuestionSlip: Modifier.padding(top = 8.dp),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:300",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "PickSheet: Caption(stringResource(R.string.category_delete_pick_archived), top = 8.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:356",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "MoveRows: Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:104",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalBulkScreen: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:282",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Choices: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:315",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Choices: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:174",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: .padding(vertical = 6.dp, horizontal = if (picked) 8.dp else 2.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:206",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "SheetError: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:208",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "SheetError: .padding(top = 8.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:416",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "file-level constant: private val INSET = 8.dp"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:144",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentMovementsScreen: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:148",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "InvestmentMovementsScreen: Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:641",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "CarryWhyNote: modifier = modifier.padding(top = 8.dp).testTag(RECIBO_CARRY_WHY_TAG)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:180",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryCorrectionContent: modifier = Modifier.padding(bottom = 8.dp).testTag(CARRY_CORRECTION_CHANGED_TAG)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:190",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryCorrectionContent: modifier = Modifier.padding(top = 8.dp).semantics { heading() }"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:196",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryCorrectionContent: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:197",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryCorrectionContent: modifier = Modifier.padding(top = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Carry.kt:218",
     "area": "design-system:Carry",
     "features": [],
     "note": "CarryCorrectionContent: modifier = Modifier.padding(top = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:224",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "BulkReceipt: TearLine(bleed = ReceiptSide, verticalMargin = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:234",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptHeader: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:290",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:305",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "ReceiptFoot: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:478",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "BulkLineRow: .padding(start = 8.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:647",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "file-level constant: private val ReceiptPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 20.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:120",
     "area": "design-system:Controls",
     "features": [],
     "note": "UnavailableDock: Spacer(Modifier.width(8.dp))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:142",
     "area": "design-system:Controls",
     "features": [],
     "note": "SelloNavigationBar: .padding(vertical = 8.dp, horizontal = 2.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:222",
     "area": "design-system:Controls",
     "features": [],
     "note": "BandChip: .padding(start = 8.dp, end = 14.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:321",
     "area": "design-system:Controls",
     "features": [],
     "note": "EntryLine: .padding(horizontal = 14.dp, vertical = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:399",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:425",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: horizontalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:482",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:101",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementRow: .padding(vertical = 8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:359",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MilestoneRow: Row(modifier.fillMaxWidth().padding(vertical = 8.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:368",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MilestoneRow: modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:369",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MilestoneRow: horizontalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Lines.kt:167",
     "area": "design-system:Lines",
     "features": [],
     "note": "TotalLine: .padding(top = 10.dp, bottom = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:97",
     "area": "design-system:Navigation",
     "features": [],
     "note": "SelloNavigationRail: .padding(vertical = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:205",
     "area": "design-system:Navigation",
     "features": [],
     "note": "MonthGrid: Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:467",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:473",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:483",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:491",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:496",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:506",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:509",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryPanes: verticalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:556",
     "area": "design-system:Paper",
     "features": [],
     "note": "AnswerSlip: Stamp(stamp, ink, Modifier.padding(vertical = 8.dp), angle = -5f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:594",
     "area": "design-system:Paper",
     "features": [],
     "note": "Footnote: horizontalArrangement = Arrangement.spacedBy(8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:95",
     "area": "design-system:Plan",
     "features": [],
     "note": "PlanSlip: horizontalArrangement = Arrangement.spacedBy(8.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:85",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: Spacer(Modifier.width(8.dp))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:88",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: Spacer(Modifier.width(8.dp))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:182",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "SelloScaffold: .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:260",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "SelloScaffold: .padding(horizontal = ContentMargin, vertical = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:382",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "file-level constant: private val TitleGap = 8.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:385",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "file-level constant: private val NoticeGap = 8.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SlipStub.kt:96",
     "area": "design-system:SlipStub",
     "features": [],
     "note": "StubButton: .padding(horizontal = 8.dp, vertical = 6.dp)"
    }
   ]
  },
  {
   "id": "H61",
   "kind": "spacing",
   "value": "12.dp",
   "token": "SelloTheme.spacing.md",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:138",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "CarrySwitch: TearLine(verticalMargin = 12.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:242",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "ChooseSlip: .padding(vertical = 12.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:211",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "SheetError: .padding(horizontal = 12.dp, vertical = 10.dp)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:182",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "note": "Summary: PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:646",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "file-level constant: private val ReceiptSide = 12.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:647",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "file-level constant: private val ReceiptPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 20.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:78",
     "area": "design-system:Chips",
     "features": [],
     "note": "SelloChip: .padding(horizontal = 12.dp, vertical = 4.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:134",
     "area": "design-system:Chips",
     "features": [],
     "note": "AddChip: .padding(horizontal = 12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:211",
     "area": "design-system:Chips",
     "features": [],
     "note": "FieldPill: .padding(horizontal = 12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:395",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:460",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: .padding(horizontal = 14.dp, vertical = 12.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:178",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MovementMonthLine: .padding(top = 12.dp, bottom = 4.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:422",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "StampTicket: .padding(start = 18.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:424",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "StampTicket: horizontalArrangement = Arrangement.spacedBy(12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:447",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "StampTicket: }.padding(start = 12.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:137",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "IconChoiceSheetContent: Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:204",
     "area": "design-system:Paper",
     "features": [],
     "note": "EntryAmountSlip: horizontalArrangement = Arrangement.spacedBy(12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:310",
     "area": "design-system:Paper",
     "features": [],
     "note": "MoreSlip: .padding(top = MoreSlipTuck, bottom = MoreSlipTeeth, start = 12.dp, end = 12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:328",
     "area": "design-system:Paper",
     "features": [],
     "note": "file-level constant: private val MoreSlipTuck = 12.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:440",
     "area": "design-system:Paper",
     "features": [],
     "note": "file-level constant: private val PaneGap = 12.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:96",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "RepeatSheet: .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:384",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "file-level constant: private val ContentMargin = 12.dp"
    }
   ]
  },
  {
   "id": "H62",
   "kind": "spacing",
   "value": "16.dp",
   "token": "SelloTheme.spacing.lg",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:182",
     "area": "recurrence",
     "features": [
      "recurrence"
     ],
     "note": "Summary: PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 12.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:230",
     "area": "design-system:Chips",
     "features": [],
     "note": "file-level constant: private val TrailingFade = 16.dp"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:422",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "StampTicket: .padding(start = 18.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:92",
     "area": "design-system:Plan",
     "features": [],
     "note": "PlanSlip: .padding(horizontal = 16.dp, vertical = 14.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:182",
     "area": "design-system:SelloScaffold",
     "features": [],
     "note": "SelloScaffold: .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Slip.kt:35",
     "area": "design-system:Slip",
     "features": [],
     "note": "val Padding = PaddingValues(start = 16.dp, top = 15.dp, end = 16.dp, bottom = 20.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Stamp.kt:76",
     "area": "design-system:Stamp",
     "features": [],
     "note": "Stamp: PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 1.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/TearLine.kt:22",
     "area": "design-system:TearLine",
     "features": [],
     "note": "TearLine: fun TearLine(modifier: Modifier = Modifier, bleed: Dp = 16.dp, verticalMargin: Dp = 14.dp) {"
    }
   ]
  },
  {
   "id": "H63",
   "kind": "spacing",
   "value": "48.dp (touch target)",
   "token": "SelloTheme.spacing.minTouchTarget",
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:493",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "file-level constant: DayTarget: day chips min size (used :374, :384, :467)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:240",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "ChooseSlip: heightIn(min = 48.dp) on a pick row"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:316",
     "area": "design-system:Controls",
     "features": [],
     "note": "EntryLine: heightIn(min = 48.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:446",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: NoteComposer field heightIn(min = 48.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:470",
     "area": "design-system:Controls",
     "features": [],
     "note": "NoteComposer: NoteComposer done button size(48.dp)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:591",
     "area": "design-system:Paper",
     "features": [],
     "note": "Footnote: Footnote row heightIn(min = 48.dp)"
    }
   ]
  },
  {
   "id": "H64",
   "kind": "alpha",
   "value": "0.08f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:73",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "IconPickerCircle: uses EMPTY_TINT: drawCircle(fill.copy(alpha = EMPTY_TINT))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:162",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "file-level constant: EMPTY_TINT defined"
    }
   ]
  },
  {
   "id": "H65",
   "kind": "alpha",
   "value": "0.14f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:74",
     "area": "design-system:Corrections",
     "features": [],
     "note": "SelloNotice: uses TIME_BAND_ALPHA: colors.paper.copy(alpha = TIME_BAND_ALPHA),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:136",
     "area": "design-system:Corrections",
     "features": [],
     "note": "file-level constant: TIME_BAND_ALPHA defined"
    }
   ]
  },
  {
   "id": "H66",
   "kind": "alpha",
   "value": "0.15f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:171",
     "area": "design-system:Charts",
     "features": [],
     "note": "ColumnsChart: drawRect(ink.copy(alpha = 0.15f))"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SummaryCharts.kt:71",
     "area": "design-system:SummaryCharts",
     "features": [],
     "note": "SplitBar: segment.color.copy(alpha = 0.15f),"
    }
   ]
  },
  {
   "id": "H67",
   "kind": "alpha",
   "value": "0.16f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:177",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "PastMonthRibbon: .background(colors.onBand.copy(alpha = 0.16f), CircleShape)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:104",
     "area": "design-system:Switches",
     "features": [],
     "note": "bandTrough: internal fun bandTrough(band: Color, onBand: Color): Color = lerp(band, onBand, 0.16f)"
    }
   ]
  },
  {
   "id": "H68",
   "kind": "alpha",
   "value": "0.18f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:367",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPinPill: ).background(if (selected) colors.onBand.copy(alpha = 0.18f) else Color.Transparent)"
    }
   ]
  },
  {
   "id": "H69",
   "kind": "alpha",
   "value": "0.38f",
   "token": null,
   "risk": false,
   "note": "No alpha token exists in the theme; 0.38 is Material's disabled-content convention.",
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryComponents.kt:56",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "SwitchRow: uses DISABLED_SWITCH_ALPHA: .alpha(if (enabled) 1f else DISABLED_SWITCH_ALPHA)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryComponents.kt:114",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "file-level constant: DISABLED_SWITCH_ALPHA defined"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:511",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "Limit: uses GREYED_OUT_ALPHA: amountAlpha = if (state.unlimited) GREYED_OUT_ALPHA else 1f"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:699",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "file-level constant: GREYED_OUT_ALPHA defined"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:56",
     "area": "settings",
     "features": [
      "settings"
     ],
     "note": "InkSwatches: uses DISABLED_ROW_ALPHA: .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:93",
     "area": "settings",
     "features": [
      "settings"
     ],
     "note": "file-level constant: DISABLED_ROW_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:350",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "DayGrid: uses DISABLED_ALPHA: .alpha(if (enabled) 1f else DISABLED_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:426",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "StepButton: uses DISABLED_ALPHA: .alpha(if (enabled) 1f else DISABLED_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/RepeatParts.kt:487",
     "area": "design-system:RepeatParts",
     "features": [
      "recurrence"
     ],
     "note": "file-level constant: DISABLED_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:181",
     "area": "design-system:AmountKeypad",
     "features": [],
     "note": "RowScope.Key: uses DISABLED_KEY_ALPHA: .alpha(if (enabled) 1f else DISABLED_KEY_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:192",
     "area": "design-system:AmountKeypad",
     "features": [],
     "note": "file-level constant: DISABLED_KEY_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:67",
     "area": "design-system:Chips",
     "features": [],
     "note": "SelloChip: uses DISABLED_CHIP_ALPHA: .alpha(if (enabled) 1f else DISABLED_CHIP_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:128",
     "area": "design-system:Chips",
     "features": [],
     "note": "AddChip: uses DISABLED_CHIP_ALPHA: .alpha(if (enabled) 1f else DISABLED_CHIP_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:231",
     "area": "design-system:Chips",
     "features": [],
     "note": "file-level constant: DISABLED_CHIP_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoreMenu.kt:55",
     "area": "design-system:MoreMenu",
     "features": [],
     "note": "MoreMenu: uses DISABLED_ALPHA: val tint = if (item.enabled) color else color.copy(alpha = DISABLED_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoreMenu.kt:76",
     "area": "design-system:MoreMenu",
     "features": [],
     "note": "file-level constant: DISABLED_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:234",
     "area": "design-system:Navigation",
     "features": [],
     "note": "MonthGrid: uses DISABLED_MONTH_ALPHA: .alpha(if (month.enabled) 1f else DISABLED_MONTH_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:279",
     "area": "design-system:Navigation",
     "features": [],
     "note": "file-level constant: DISABLED_MONTH_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:306",
     "area": "design-system:Paper",
     "features": [],
     "note": "MoreSlip: uses MORE_SLIP_DISABLED_ALPHA: .alpha(if (enabled) 1f else MORE_SLIP_DISABLED_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:339",
     "area": "design-system:Paper",
     "features": [],
     "note": "file-level constant: MORE_SLIP_DISABLED_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:59",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: uses DISABLED_ALPHA: val fade = if (loading) 1f else DISABLED_ALPHA"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:115",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "file-level constant: DISABLED_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:100",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "SettingRow: uses DISABLED_ROW_ALPHA: .alpha(if (enabled) 1f else DISABLED_ROW_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:133",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "file-level constant: DISABLED_ROW_ALPHA defined"
    }
   ]
  },
  {
   "id": "H70",
   "kind": "alpha",
   "value": "0.42f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:247",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "MilestonePlace: uses KEPT_ALPHA: val keptInk = colors.semantic.gain.copy(alpha = KEPT_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:331",
     "area": "design-system:Goals",
     "features": [
      "goals"
     ],
     "note": "file-level constant: KEPT_ALPHA defined"
    }
   ]
  },
  {
   "id": "H71",
   "kind": "alpha",
   "value": "0.5f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/ReciboGoal.kt:231",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "Dots: .background(if (on) colors.brand else colors.outline.copy(alpha = 0.5f))"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:342",
     "area": "recibo",
     "features": [
      "recibo"
     ],
     "note": "file-level constant: BUSY_ALPHA defined (no use found in the file: dead constant)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:119",
     "area": "design-system:Charts",
     "features": [],
     "note": "ColumnsChart: uses DIMMED_ALPHA: }.copy(alpha = if (dimmed) DIMMED_ALPHA else 1f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Charts.kt:365",
     "area": "design-system:Charts",
     "features": [],
     "note": "file-level constant: DIMMED_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:317",
     "area": "design-system:Controls",
     "features": [],
     "note": "EntryLine: .alpha(if (enabled) 1f else 0.5f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:123",
     "area": "design-system:Corrections",
     "features": [],
     "note": "SelloNotice: uses BUSY_ALPHA: color = colors.paper.copy(alpha = if (busy) BUSY_ALPHA else 1f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Corrections.kt:140",
     "area": "design-system:Corrections",
     "features": [],
     "note": "file-level constant: BUSY_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:58",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "IconPickerCircle: uses DISABLED_ALPHA: .alpha(if (enabled) 1f else DISABLED_ALPHA)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:163",
     "area": "design-system:IconPicker",
     "features": [],
     "note": "file-level constant: DISABLED_ALPHA defined"
    }
   ]
  },
  {
   "id": "H72",
   "kind": "alpha",
   "value": "0.55f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:567",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "RemovalDialog: uses SCRIM_DIM: SideEffect { window?.setDimAmount(SCRIM_DIM) }"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:874",
     "area": "category",
     "features": [
      "category"
     ],
     "note": "file-level constant: SCRIM_DIM defined"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:538",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "FigureSlip: uses IDLE_ALPHA: amountAlpha = if (active) 1f else IDLE_ALPHA,"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:718",
     "area": "investments",
     "features": [
      "investments"
     ],
     "note": "file-level constant: IDLE_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:81",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: uses SCRIM_ALPHA: SideEffect { window?.setDimAmount(SCRIM_ALPHA) }"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:139",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ConfirmSlip: uses SCRIM_ALPHA: SideEffect { window?.setDimAmount(SCRIM_ALPHA) }"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:210",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: uses SCRIM_ALPHA: SideEffect { window?.setDimAmount(SCRIM_ALPHA) }"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:288",
     "area": "design-system:Feedback",
     "features": [],
     "note": "ChoiceSlip: uses SCRIM_ALPHA: SideEffect { window?.setDimAmount(SCRIM_ALPHA) }"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:345",
     "area": "design-system:Feedback",
     "features": [],
     "note": "file-level constant: SCRIM_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:282",
     "area": "design-system:Recurrence",
     "features": [
      "recurrence"
     ],
     "note": "file-level constant: private val Scrim = Color(0xFF050814).copy(alpha = 0.55f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:154",
     "area": "design-system:Settings",
     "features": [
      "settings"
     ],
     "note": "OptionSlip: uses SCRIM_ALPHA from Feedback.kt"
    }
   ]
  },
  {
   "id": "H73",
   "kind": "alpha",
   "value": "0.6f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:207",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ReachedRow: .alpha(if (busy) 0.6f else 1f)"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Lines.kt:188",
     "area": "design-system:Lines",
     "features": [],
     "note": "LeaderLayout: val dots = SelloTheme.colors.outline.copy(alpha = 0.6f)"
    }
   ]
  },
  {
   "id": "H74",
   "kind": "alpha",
   "value": "0.7f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:355",
     "area": "design-system:Feedback",
     "features": [],
     "note": "SlipSkeleton: uses SKELETON_REST_ALPHA: SKELETON_REST_ALPHA"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:388",
     "area": "design-system:Feedback",
     "features": [],
     "note": "file-level constant: SKELETON_REST_ALPHA defined"
    }
   ]
  },
  {
   "id": "H75",
   "kind": "alpha",
   "value": "0.72f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:100",
     "area": "design-system:Chips",
     "features": [],
     "note": "SelloChip: uses TRAILING_ALPHA: color = content.copy(alpha = TRAILING_ALPHA),"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:108",
     "area": "design-system:Chips",
     "features": [],
     "note": "file-level constant: TRAILING_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:133",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "file-level constant: SYMBOL_ALPHA defined"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:176",
     "area": "design-system:MoneyText",
     "features": [],
     "note": "moneyAnnotatedString: uses SYMBOL_ALPHA: color = if (ink.isSpecified) ink.copy(alpha = ink.alpha * SYMBOL_ALPHA) else ink"
    }
   ]
  },
  {
   "id": "H76",
   "kind": "alpha",
   "value": "0.8f",
   "token": null,
   "risk": false,
   "note": null,
   "shots": [],
   "places": [
    {
     "file": "app/src/main/java/com/software/sello/navigation/ShellBars.kt:106",
     "area": "shell",
     "features": [
      "shell"
     ],
     "note": "MonthBar: color = colors.onBand.copy(alpha = 0.8f)"
    }
   ]
  }
 ],
 "notListed": [
  {
   "id": "F16",
   "what": "BORRAR field ×3",
   "why": "all three call FormField with the same options; there is no shared confirm-word part to copy."
  },
  {
   "id": "F24",
   "what": "icon sheet ×3",
   "why": "each editor calls the shared RepeatSheet and IconChoiceSheetContent with its own icons, note and tone; nothing is redrawn."
  },
  {
   "id": "F27 (part)",
   "what": "limit sheet × (AdjustBudgetScreen.kt:348)",
   "why": "it closes a sheet from inside its header row, not a form's top bar, which is MovementCloseButton's place."
  },
  {
   "id": "F37",
   "what": "receipt layout ×3",
   "why": "no shared receipt-screen layout exists; each screen arranges the shared BulkReceipt, notice and AmountKeypad."
  },
  {
   "id": "F44",
   "what": "MonthBar/TitleBar vs SelloScaffold TopBar",
   "why": "different bars (month button, title with chip, pushed-screen bar) that share only the band style."
  },
  {
   "id": "F48",
   "what": "icon-circle rows ×5",
   "why": "ScheduleRow, LimitRow, SpendingRankRow and the choice/target rows each show something else; none rebuilds a shared row."
  },
  {
   "id": "F49",
   "what": "InvestmentRow vs MovementRow",
   "why": "InvestmentRow lists a holding (rank colour, value and change, opens it); MovementRow is one movement. Same row grammar, different component."
  },
  {
   "id": "F52",
   "what": "share and progress bars ×4",
   "why": "SplitBar fills the whole bar with segments; the others are a fill on a track, for which no shared part exists (the two goal bars copy each other inside goals)."
  },
  {
   "id": "F67 (part)",
   "what": "full weekday names and 'd de MMMM' dates",
   "why": "neither MonthNames nor DateLabels has them, so they copy nothing."
  },
  {
   "id": "F73",
   "what": "disabled alpha 0.38 ×11",
   "why": "a repeated constant, not a component; it belongs to hard-coded values."
  },
  {
   "id": "F80",
   "what": "ClosedMonthInvitation vs SuggestionNote",
   "why": "a one-line invitation, not a suggestion with evidence and advice; a different component."
  },
  {
   "id": "F82 (part)",
   "what": "GoalPill round + / ✓",
   "why": "SelloButton has no icon-only form to copy."
  },
  {
   "id": "F86",
   "what": "heading with a landing stamp ×4",
   "why": "no shared stamped-heading part exists; each FlowRow wraps the shared RestoreHeading or a Text and Stamp."
  },
  {
   "id": "F89",
   "what": "fading edges ×2 ways",
   "why": "all three are private implementations inside :design-system; no shared fading-edge part exists."
  }
 ]
};
