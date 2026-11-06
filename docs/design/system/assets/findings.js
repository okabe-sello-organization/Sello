/*
 * Design findings for the owner to decide, rendered by findings.html (see system.js).
 * Ordered by theme, most impactful first within a theme; findings.html shows a heading
 * whenever the theme changes. Generated from the SELLO-177 audit fragments.
 * Each entry:
 *   {
 *     id: "F01", theme: "Notices & states", title: "...",
 *     category: "duplicate" | "divergent-use" | "token-bypass" | "pattern-drift",
 *     purpose: "What the part is for, in one line.",
 *     features: ["recibo", "goals"],               // feature page names
 *     variants: [
 *       { label: "A", note: "How it looks here.",
 *         files: ["app/src/main/java/.../X.kt:120"],
 *         shots: [{ module: "app", file: "recibo-cobalto-light-font1.0.png" }] }
 *     ],
 *     recommend: "A", reason: "Why."
 *   }
 */
window.SELLO_FINDINGS = [
 {
  "id": "F01",
  "theme": "Notices & states",
  "title": "Older figures after a failed refresh are announced four ways",
  "category": "pattern-drift",
  "purpose": "Keep the last good figures on screen, say they are older, and offer to read again.",
  "features": [
   "recibo",
   "resumen",
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "category",
   "recurrence",
   "settings"
  ],
  "variants": [
   {
    "label": "A",
    "note": "StaleSlip: a plain slip with one loss-ink line (\"recibo_stale\") and a text button \"Reintentar\"; the figures stay under it.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/read/StaleSlip.kt:23",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:138",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:138",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:190",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:139",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:107",
     "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:121",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:242",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:433",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:298",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:315"
    ],
    "shots": [
     {
      "module": "app",
      "file": "upcoming-stale-claro.png"
     },
     {
      "module": "app",
      "file": "expense-entry-stale-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "schedules-stale-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ErrorSlip above the figures: the Error icon, the title \"No se pudo cargar\" (or the screen's own failed-read title) and an outlined \"Reintentar\" — the same card as a screen that never loaded.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:140",
     "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:111",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:149",
     "app/src/main/java/com/software/sello/feature/goals/ReciboGoal.kt:61",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:108",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:86",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateScreen.kt:66",
     "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:182",
     "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:108"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-goals-stale-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "income-history-stale-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "A loose line and a text \"Reintentar\" inside another slip or sheet: bodySmall in loss ink in the limit sheet; bodyMedium in warn ink with its own wording (\"category_detail_fixed_stale\") in Pagos fijos.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:361",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:66"
    ],
    "shots": [
     {
      "module": "app",
      "file": "limit-sheet-stale-claro.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "A hint-tone FieldMessageText above the record button, with no way to read again.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:459"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movements-stale-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used and made for this case; B titles figures that are on screen \"No se pudo cargar\", C changes colour, D drops the retry. Move StaleSlip into :design-system so every screen can use it."
 },
 {
  "id": "F02",
  "theme": "Notices & states",
  "title": "A screen that never loaded titles its error card two ways",
  "category": "divergent-use",
  "purpose": "The ErrorSlip shown when nothing could be read and nothing was read before.",
  "features": [
   "recibo",
   "resumen",
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "A title that names what failed: \"No pudimos leer tu mes\", \"No pudimos leer tu presupuesto\", \"No pudimos leer tus pagos previstos\", \"No pudimos leer tus pagos fijos\", \"No pudimos leer tus metas\", \"No pudimos leer esta meta\". Several also pass icon = SelloIcon.Error, which is already the default.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:170",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:129",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:178",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:51",
     "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:99",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:95",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:116",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:94",
     "app/src/main/java/com/software/sello/feature/goals/ReciboGoal.kt:48"
    ],
    "shots": [
     {
      "module": "app",
      "file": "upcoming-failed-claro.png"
     },
     {
      "module": "app",
      "file": "category-fixed-failed-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "The default title \"No se pudo cargar\", with a message that names what failed.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:115",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:442",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:52",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:99",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:193",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:88",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:145",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:179"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-expenses-failed-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "The title is read first; saying what could not be read is clearer than a generic heading."
 },
 {
  "id": "F03",
  "theme": "Notices & states",
  "title": "A form whose choices never loaded: full error card or an inline line",
  "category": "pattern-drift",
  "purpose": "The categories, sources or goals a form needs could not be read, and none were read before.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ErrorSlip replaces the form (title, message, \"Reintentar\"); SlipSkeleton fills the pane while loading.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:213",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:160",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:89"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "The receipt, note and pad stay; where the chips go, an error FieldMessageText and a separate outlined \"Reintentar\" (bulk_retry); a two-line SlipSkeleton there while loading.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:322",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:342"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-expenses-failed-read-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "The shared ErrorSlip, used by every other entry form; B hand-builds an error card from a field message and a button."
 },
 {
  "id": "F04",
  "theme": "Notices & states",
  "title": "A save that did not finish is reported three ways in Metas",
  "category": "pattern-drift",
  "purpose": "Say a goal change was not saved or that its outcome is unknown, and offer the next step.",
  "features": [
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "FieldMessageText (warning tone for unknown, error tone otherwise) right above the form's one button.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:226",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:303",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:361",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:401"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-movement-unknown-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "goal-editor-unresolved-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "GoalSaveProblem: a slip titled \"No se guardó\" or \"Falta comprobar si se guardó\", a FieldMessageText and a full-width outlined button.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalSaveProblem.kt:29",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:146",
     "app/src/main/java/com/software/sello/feature/goals/ReciboGoal.kt:99"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-goals-mark-unknown-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "SheetError: a hand-built warnSoft/lossSoft banner with a 12 dp corner, an Error icon and a bold lead, inside the replace sheet.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:196"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-replace-refused-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "FieldMessageText is the :design-system part every form uses; where there is no form (B, C) it should be wrapped the same way, not rebuilt."
 },
 {
  "id": "F05",
  "theme": "Notices & states",
  "title": "The \"Comprobar si se guardó\" control changes kind, tone, icon and label",
  "category": "divergent-use",
  "purpose": "Ask whether a save whose outcome is unknown landed, without sending it twice.",
  "features": [
   "recibo",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "The submit button itself becomes \"Comprobar si se guardó\", same tone, and keeps the action's icon (Flag, Check or TrendingUp).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:335",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:280"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-movement-unknown-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "investment-editor-unresolved-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "The submit button itself becomes \"Comprobar si se guardó\", same tone, with no icon.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:368",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:494",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:455",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:198",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:481"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-bulk-unknown-claro.png"
     },
     {
      "module": "app",
      "file": "investment-movements-unknown-claro.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "A separate filled button in the default brand tone, while the form's own button is gain green.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:250"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-editor-unresolved-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "An outlined brand button inside the GoalSaveProblem slip.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalSaveProblem.kt:57"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-goals-mark-unknown-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "E",
    "note": "A filled brand button labelled only \"Comprobar\" (goal_error_check), twice as wide as \"Cancelar\" beside it, in the replace sheet.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:111"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Keeps the button the person just pressed in its place and colour, drops an icon that no longer fits the action, one label everywhere."
 },
 {
  "id": "F06",
  "theme": "Notices & states",
  "title": "An expense deletion that failed or is unknown: inline line, error card or notice bar",
  "category": "pattern-drift",
  "purpose": "Say a deletion of an expense did not happen, or that its outcome is unknown, and offer \"Comprobar si se borró\".",
  "features": [
   "shell",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Category detail: a line under the list hint in loss ink (warn ink when unknown) and, when unknown, a text button \"Comprobar si se borró\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:524",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:539"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-delete-unknown-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Todos los gastos: an ErrorSlip with its own title (\"Falta comprobar el borrado\", \"Nada que borrar\", \"No se borró\") and an outlined button; for the refused cases the button is \"Reintentar\", which only reads the list again.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:123"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-expenses-delete-unknown-claro.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "After a deletion from the expense form: a SelloNotice in the notice bar with the message and \"Comprobar si se borró\", busy while checking.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:99",
     "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:95"
    ],
    "shots": []
   }
  ],
  "recommend": "C",
  "reason": "SelloNotice is the shared part for a message with one action and a busy state, and it is where a deletion's Deshacer already shows; ErrorSlip is for failed reads."
 },
 {
  "id": "F07",
  "theme": "Notices & states",
  "title": "Checking an unknown delete, erase or restore looks four ways",
  "category": "pattern-drift",
  "purpose": "After a destructive write whose result is unknown, check it with the same operation before anything else.",
  "features": [
   "investments",
   "category",
   "recurrence",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Restaurar: the \"No sabemos si se restauró\" slip stays and its button turns into a loading \"Comprobando…\" while the check runs.",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:106"
    ],
    "shots": [
     {
      "module": "app",
      "file": "components-restore-unknown.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Borrar datos: \"No sabemos si se borró\"; its button replays the erase, so the screen goes back to the \"Escribe BORRAR\" slip with a loading \"Borrando…\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:125",
     "app/src/main/java/com/software/sello/feature/reset/ResetSession.kt:92"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Category removal: a dialog titled \"Comprobando…\" that checks by itself, says \"No se borrará dos veces.\" and offers \"Comprobar de nuevo\" after a failed check.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:155"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-checking-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Editors: the form's warning line and the main button relabelled \"Comprobar si se borró\" (investment editor); the schedule editor uses the save wording here, which is logged as a bug.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:283",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:362"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The person stays on the slip that states the doubt and its one button becomes the check; B looks like a second erase. D is the same rule inside a form."
 },
 {
  "id": "F08",
  "theme": "Notices & states",
  "title": "Waiting for a read: skeleton or a bare spinner",
  "category": "pattern-drift",
  "purpose": "Show that something is being read or counted before anything is offered.",
  "features": [
   "recibo",
   "category",
   "recurrence",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SlipSkeleton bars in place of the content (Borrar datos shows a whole-screen skeleton while counting).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:446",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:110",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:185",
     "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:104",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:133",
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:99"
    ],
    "shots": [
     {
      "module": "design-system",
      "file": "components-feedback.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Category removal: a Material CircularProgressIndicator, 20 dp beside \"Contando lo que tiene …\" while counting and 24 dp alone while the move targets load — the only bare spinners in :app.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:102",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:286"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Every other read waits on a SlipSkeleton, which also honours reduced motion; a bare spinner appears nowhere else."
 },
 {
  "id": "F09",
  "theme": "Notices & states",
  "title": "A record that no longer exists: empty slip, hand-built slip or a silent exit",
  "category": "pattern-drift",
  "purpose": "Open the detail of an item that has been deleted meanwhile.",
  "features": [
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Category detail: EmptySlip with the Block icon, a title and a body.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:127"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Goal detail: a hand-built slip, headlineSmall \"Esta meta ya no está\" and bodyLarge \"Tus otras metas siguen en Metas.\", no icon.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:103"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Investment detail: the loading skeleton, then the screen closes by itself with nothing said.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:73",
     "app/src/main/java/com/software/sello/navigation/InvestmentDestinations.kt:143"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Uses the shared EmptySlip and tells the person what happened."
 },
 {
  "id": "F10",
  "theme": "Notices & states",
  "title": "The 'figures changed while you confirmed' note differs in colour and form",
  "category": "pattern-drift",
  "purpose": "Say the counts or amounts were read again because the data changed during a confirmation.",
  "features": [
   "category",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "A bodyMedium line in loss ink with a polite live region at the top of the slip (Borrar datos above the heading; Restaurar under the backup date).",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:152",
     "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:171"
    ],
    "shots": [
     {
      "module": "app",
      "file": "components-restore-unknown.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Carry drop: a bodyMedium line in warn ink, no live region, the first line of the dialog.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:338"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Category removal: a separate ProblemSlip dialog titled \"… cambió\" with the old and new counts.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:449"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-changed-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Two of three screens already use it; one note part keeps colour, placement and announcement the same."
 },
 {
  "id": "F11",
  "theme": "Notices & states",
  "title": "Notices that go by themselves: three places and three timers",
  "category": "pattern-drift",
  "purpose": "Show a short statement or an undo for a while, then remove it.",
  "features": [
   "shell",
   "bulk",
   "goals",
   "investments",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Varios gastos undo: SelloNotice in the scaffold's notice slot, a fixed 6 s timer in the Root.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:120",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:620"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Aportar varios and Varios movimientos undo: SelloNotice over the chips in confirmation style, a fixed 4 s (timer in the ViewModel for goals, in the composable for investments).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:110",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkViewModel.kt:371",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:441",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:554"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Correction statements (\"Gasto actualizado\" and the like) in the notice bar: a fixed 4 s, not stretched for screen readers, no time-left band.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/corrections/Corrections.kt:1292",
     "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:68"
    ],
    "shots": [
     {
      "module": "design-system",
      "file": "components-feedback.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Catch-up notice (\"Sello anotó …\"): 5 s stretched by the screen reader's recommended timeout, with a band that shows the time left.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/CatchUpNotice.kt:283",
     "app/src/main/java/com/software/sello/feature/recurrence/CatchUpNotice.kt:325"
    ],
    "shots": [
     {
      "module": "app",
      "file": "catch-up-bar-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "D",
  "reason": "Respects the screen reader's recommended timeout and shows how long it stays; show it in the scaffold's notice slot, as A does."
 },
 {
  "id": "F12",
  "theme": "Notices & states",
  "title": "Metas' empty state re-draws EmptySlip by hand",
  "category": "duplicate",
  "purpose": "Say a list has nothing yet: a centred icon, a title and one sentence on a slip.",
  "features": [
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "EmptySlip from :design-system: brand-tinted 44 dp icon, headlineSmall title, bodyLarge body in inkSoft (Inversiones and nine other places).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:352",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:117"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "EmptyMetas: its own column with a 44 dp Flag tinted gain, the same title and body styles, then a row of four empty stamps.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:192"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Give EmptySlip an icon tint and an optional content slot so Metas keeps its stamps without a copy."
 },
 {
  "id": "F13",
  "theme": "Notices & states",
  "title": "'Nothing to choose' note drawn three ways",
  "category": "divergent-use",
  "purpose": "Say there is no investment or goal to put a movement in.",
  "features": [
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "FactLine (bodyLarge, inkSoft).",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:266",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:240"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "A plain bodyMedium Text in inkSoft, with the same sentence as A (investment_movement_none).",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:384"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "An error-tone FieldMessageText.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:291"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-bulk-missing-goal-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Shared component, already drawing the same sentence elsewhere."
 },
 {
  "id": "F14",
  "theme": "Notices & states",
  "title": "An answer slip with no figure is drawn two ways",
  "category": "divergent-use",
  "purpose": "The answer slip when no value is recorded yet (or a rate is missing).",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Inversiones total: the label \"Tus inversiones valen\" and one bodyLarge sentence.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:213"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Investment detail: the label \"Vale\", headlineSmall \"Sin valor anotado\", \"Desde …\", a tear line, Aportaste and the record button.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:122"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Keeps the answer slip's shape (label, large line, detail); one 'answer without a figure' form, ideally in AnswerSlip."
 },
 {
  "id": "F15",
  "theme": "Dialogs, sheets & menus",
  "title": "Discarding a draft: red or blue Descartar, 'Cancelar' or a named way back",
  "category": "pattern-drift",
  "purpose": "Ask before throwing away what the person typed.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ConfirmSlip with Descartar in loss red and a keep button that names the way back (\"Seguir editando\" / \"Seguir anotando\"); title per screen, counted on receipts (\"¿Descartar 3 renglones?\").",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:244",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:208",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:247",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:153",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:136"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "ConfirmSlip with destructive = false: Descartar in brand blue, keep button \"Seguir anotando\" / \"Seguir editando\"; a new and a corrected expense have their own title and message.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:290",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:186"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "ConfirmSlip defaults: Descartar in loss red and the keep button \"Cancelar\"; one title for new and corrected (\"¿Descartar lo que escribiste?\" on income, MovementDiscardSlip on the goal and investment movement forms and Varios movimientos).",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:222",
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:82",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:173"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Most used, ConfirmSlip's own tone for a loss that cannot be undone, and the keep label says where it leads."
 },
 {
  "id": "F16",
  "theme": "Dialogs, sheets & menus",
  "title": "Typing BORRAR to erase is laid out three ways, with the word copied three times",
  "category": "pattern-drift",
  "purpose": "Make the person type BORRAR before data is erased for good.",
  "features": [
   "startup",
   "category",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Borrar datos: a slip on the screen with RestoreHeading, the field, then a full-width loss \"Borrar todo\" above a full-width outlined \"Cancelar\"; the field turns off while erasing. Word in ResetState, label reset_word_label.",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:192",
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:208",
     "app/src/main/java/com/software/sello/feature/reset/ResetViewModel.kt:128",
     "app/src/main/res/values/strings.xml:895"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Category \"Borrar todo\": the field inside a ConfirmSlip dialog whose action stays off until the word is typed (confirmEnabled); \"Cancelar\" first, the loss action after the tear line. Word in CategoryRemovalState, label category_delete_word_label (same text).",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:136",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:433",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalState.kt:97",
     "app/src/main/res/values/strings.xml:457"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-all-typed-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Start-up \"Empezar de cero\": a slip with its own headlineSmall heading, the field, then a hand-made row with outlined \"Cancelar\" left and the loss action right. Word in StartupFailureState, label startup_start_over_word (same text).",
    "files": [
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:285",
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:325",
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureState.kt:115",
     "app/src/main/res/values/strings.xml:647"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "ConfirmSlip's confirmEnabled exists for exactly this; whichever layout wins, the word, its label string and the field become one shared part."
 },
 {
  "id": "F17",
  "theme": "Dialogs, sheets & menus",
  "title": "Destructive confirmations put Cancelar before or after the action",
  "category": "pattern-drift",
  "purpose": "The pair of buttons that confirms or cancels a destructive change.",
  "features": [
   "startup",
   "expense",
   "goals",
   "investments",
   "category",
   "recurrence",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Inline slips on a screen: the full-width loss action first, a full-width outlined \"Cancelar\" under it (Borrar datos, Restaurar preview, start-up restore preview).",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:173",
     "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:183",
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:260"
    ],
    "shots": [
     {
      "module": "app",
      "file": "reset-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "components-restore-unknown.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Every ConfirmSlip: outlined \"Cancelar\" first (left, or on top when stacked), the action second, after a tear line.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:158",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:136",
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:316"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-all-typed-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "carry-drop-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Start-up \"Empezar de cero\": a hand-made row, \"Cancelar\" left and the loss action right, no tear line.",
    "files": [
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:325"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Every dialog in the app already does it and :design-system owns it; one order means the thumb never lands on the destructive button by habit."
 },
 {
  "id": "F18",
  "theme": "Dialogs, sheets & menus",
  "title": "Deleting from an edit screen: a top-bar Borrar or the ⋮ menu",
  "category": "pattern-drift",
  "purpose": "Delete the record or item being edited, from the top bar.",
  "features": [
   "expense",
   "income",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "A text button \"Borrar\" on the band; deletes at once and offers Deshacer afterwards (expense, income, goal and investment movements).",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:200",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:149",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:100",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:120"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "MoreMenu (⋮) with a loss-red delete item that asks first with a ConfirmSlip (category, goal, investment and schedule editors).",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:175",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:121",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:152",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:111"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-menu-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "investment-editor-editing-menu-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Used more and a :design-system part, with one place for destructive actions; whether to ask first can still depend on what is deleted."
 },
 {
  "id": "F19",
  "theme": "Dialogs, sheets & menus",
  "title": "The long-press menu re-implements MoreMenu and draws Borrar in plain ink",
  "category": "duplicate",
  "purpose": "A small menu of actions on one item, one of which deletes.",
  "features": [
   "recibo",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "LineActionMenu (long press on a Recibo cell or an expense row): a DropdownMenu on paper, no icons, \"Borrar\" in ink like \"Editar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:256",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:555",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:162"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "MoreMenu in :design-system: the same DropdownMenu with an icon per item and destructive items in loss ink.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/MoreMenu.kt:41",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:171",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:123"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-menu-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "The shared menu exists; deleting should read as destructive in every menu. The anchor (top bar or the pressed row) can be a parameter."
 },
 {
  "id": "F20",
  "theme": "Dialogs, sheets & menus",
  "title": "Category removal draws its own dialog frame instead of ConfirmSlip's",
  "category": "duplicate",
  "purpose": "A paper slip over a dimmed screen with a title, scrolling content and an action under a tear line.",
  "features": [
   "shell",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "RemovalDialog (feature-local): its own Dialog and dim (0.55), Slip, a 22 sp headlineSmall title, a scrolling column, TearLine, then one action or none.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:557",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:874"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-choose-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "category-removal-checking-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ConfirmSlip in :design-system: the same frame with \"Cancelar\" and a confirm button (also hosts the shell's correction question).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:124",
     "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:477"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-empty-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "carry-delete-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Give ConfirmSlip (or a slip dialog under it) a zero- or one-action form so the frame, dim and title style live once."
 },
 {
  "id": "F21",
  "theme": "Dialogs, sheets & menus",
  "title": "What an erase or replace removes is shown as a sentence, a table or rows",
  "category": "pattern-drift",
  "purpose": "Before a destructive confirm, show what will go.",
  "features": [
   "category",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Borrar datos: one sentence (\"Se borran 3 categorías, 42 gastos y 1 ingreso, con sus límites y notas…\").",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:162",
     "app/src/main/java/com/software/sello/presentation/restore/RestoreSlips.kt:271"
    ],
    "shots": [
     {
      "module": "app",
      "file": "reset-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Restaurar preview: a feature-local Ahora / Respaldo table of counts.",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/RestoreSection.kt:210"
    ],
    "shots": [
     {
      "module": "app",
      "file": "components-restore-unknown.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Category \"Borrar todo\": one ChangeRow per kind of record with its amount or count, under \"Se borran para siempre:\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:397"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-all-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "C",
  "reason": "ChangeRow is the :design-system part for a change's rows and is easier to scan; restore's now-versus-backup columns can be a two-value ChangeRow."
 },
 {
  "id": "F22",
  "theme": "Dialogs, sheets & menus",
  "title": "Tasa del dólar closes without asking; the other typing forms ask",
  "category": "pattern-drift",
  "purpose": "Leave a form that has something typed.",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Tasa del dólar: Back and ✕ close at once even with a rate typed.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryViewModel.kt:151"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Anotar movimiento, Varios movimientos and the investment editor: a discard ConfirmSlip when something is typed.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementViewModel.kt:319",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsViewModel.kt:327",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorViewModel.kt:241"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Same rule on every typing form."
 },
 {
  "id": "F23",
  "theme": "Dialogs, sheets & menus",
  "title": "Two bottom-sheet styles, and the common one is named for recurrence",
  "category": "divergent-use",
  "purpose": "A sheet that rises over the screen for a choice.",
  "features": [
   "recibo",
   "shell",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "RepeatSheet (:design-system, Recurrence.kt): its own scrim (0xFF050814 at 55 %) and a 24 dp top gap; used by seven sheets in five features.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:60",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:141",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:307",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:444",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:259",
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:64",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:422",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:485"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-replace-sheet-claro.png"
     },
     {
      "module": "app",
      "file": "limit-sheet-stale-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Material ModalBottomSheet on paper with Material's scrim and drag handle: the month picker and the goal editor's month sheet.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:284",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:547"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-picker-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used and already Sello's look; give it a neutral name (and its own file) so other features can find it."
 },
 {
  "id": "F24",
  "theme": "Dialogs, sheets & menus",
  "title": "The icon sheet is copied in three editors",
  "category": "duplicate",
  "purpose": "Choose an icon for a category, goal or investment in a sheet titled \"Elige un ícono\".",
  "features": [
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Category editor: RepeatSheet with IconChoiceSheetContent and the in-use note.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:443"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Goal and investment editors: the same private IconSheet, each with its own icon set, note and tone.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:421",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:484"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Make the sheet one :design-system part, modelled on A, that takes the choices, note and tone."
 },
 {
  "id": "F25",
  "theme": "Dialogs, sheets & menus",
  "title": "Stopping an item without deleting it: Archivar asks, Pausar does not",
  "category": "pattern-drift",
  "purpose": "Stop using a category or a fixed payment while keeping its history.",
  "features": [
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Category: \"Archivar categoría\" in the ⋮ menu, then a ConfirmSlip that says what it does.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:159",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:303"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-menu-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Fixed payment: \"Pausar\" as a row in the form, with \"Próximo: …\" as its action text; sent with no question.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:345",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorViewModel.kt:283"
    ],
    "shots": [
     {
      "module": "app",
      "file": "schedule-editor-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Both undo from a slip in the form; starting from ⋮ with a short question keeps the form's rows for its fields. Keep B if pausing is meant to be one tap."
 },
 {
  "id": "F26",
  "theme": "Forms & entry",
  "title": "The date picker dialog is written eight times",
  "category": "duplicate",
  "purpose": "Pick an entry's day in a Material DatePickerDialog confirmed with \"Aceptar\", within an allowed range.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MovementDatePicker, shared by the goal and investment movement forms (days up to today).",
    "files": [
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:144",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:192",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:185"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Private copies of the same dialog and button; some only change the allowed range (from today while Se repite is on, from a start day).",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:673",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:498",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:566",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:738",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:219",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:450",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:267"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "One picker with an allowed-range parameter replaces seven copies."
 },
 {
  "id": "F27",
  "theme": "Forms & entry",
  "title": "The form close button is written out eight times beside a shared one",
  "category": "duplicate",
  "purpose": "The ✕ in a form's top bar, read \"Cerrar\".",
  "features": [
   "recibo",
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MovementCloseButton (presentation/movement): IconButton, SelloIcon.Close, expense_entry_close.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:45",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:92",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:82",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:112",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:122"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movement-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "The same IconButton written in the screen; the goal editor has its own goal_editor_close and the limit sheet limit_sheet_close, all \"Cerrar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:190",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:142",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:116",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:143",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:71",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:111",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:105",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:348"
    ],
    "shots": [
     {
      "module": "app",
      "file": "expense-entry-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "One close (and back) icon button in :design-system with one \"Cerrar\" string; :app has 18 bare IconButtons and :design-system has no shared one."
 },
 {
  "id": "F28",
  "theme": "Forms & entry",
  "title": "A form's loading and failed pane is padded six ways",
  "category": "divergent-use",
  "purpose": "The pane that holds the SlipSkeleton or ErrorSlip before a form can be used.",
  "features": [
   "expense",
   "income",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MovementPane: scrolls, spacing.lg padding inside the scaffold padding, spacing.lg gap; IncomePane is a line-for-line copy.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:53",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:250",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:111",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:131"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Expense Pane: scrolls, no extra padding, spacing.md gap.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:363"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Tasa del dólar: a column with spacing.lg padding, no scroll.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:80"
    ],
    "shots": []
   },
   {
    "label": "D",
    "note": "Editors: a column with a spacing.md gap and vertical padding only.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:181",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:136",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:167"
    ],
    "shots": []
   },
   {
    "label": "E",
    "note": "Schedule editor: the scaffold padding only.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:132"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Already shared, scrolls for a long failure message, and gives the skeleton and the error the same inset on every form."
 },
 {
  "id": "F29",
  "theme": "Forms & entry",
  "title": "The save button while saving: 'Guardando…' or its own label",
  "category": "divergent-use",
  "purpose": "Show that the form's one save is running and cannot be pressed again.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "The label stays (\"Guardar cambios\", \"Crear categoría\") and SelloButton shows its spinner (editors).",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:606",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:286",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:281"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "The label changes to \"Guardando…\" (expense_entry_saving) with the spinner.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:589",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:424",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:442",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:387",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:339",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:371",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:460",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:498",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:202"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movements-saving-claro.png"
     },
     {
      "module": "app",
      "file": "goal-bulk-saving-claro.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Most forms do it, and the words are read aloud where a spinner alone is not; write the choice into SelloButton once."
 },
 {
  "id": "F30",
  "theme": "Forms & entry",
  "title": "The close button while a save runs: faded on Varios gastos, active but ignored elsewhere",
  "category": "pattern-drift",
  "purpose": "The top-bar ✕ while a write is in flight or its outcome is pending.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "The ✕ is disabled (fades) while busy.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:116"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-expenses-saving-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "The ✕ stays enabled; the ViewModel drops Back while busy or pending, so a tap does nothing visible.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:190",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryViewModel.kt:486",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:142",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormViewModel.kt:324",
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:45"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "A control that cannot act should look off; the ViewModel guard stays as the backstop."
 },
 {
  "id": "F31",
  "theme": "Forms & entry",
  "title": "Icons on the main submit button follow no rule",
  "category": "divergent-use",
  "purpose": "The one filled button that records, creates or saves on a form.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Text only: \"Anotar gasto\", \"Anotar ingreso\", \"Guardar cambios\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:609",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:420"
    ],
    "shots": [
     {
      "module": "app",
      "file": "expense-entry-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "A + icon on the receipts: \"+ Anotar 3 gastos\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:456",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:377",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:518"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-expenses-three-lines-claro.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Goal editor: the Flag on both \"Crear meta\" and \"Guardar cambios\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:292"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-editor-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Investment editor: TrendingUp to create, Check to save changes.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:295"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-editor-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "E",
    "note": "Category editor: Add to create, no icon to save changes.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:616"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The label already says the action; one style for every submit button is the simplest."
 },
 {
  "id": "F32",
  "theme": "Forms & entry",
  "title": "The income record button is green on one screen and blue on the other",
  "category": "divergent-use",
  "purpose": "The one button that records income.",
  "features": [
   "income",
   "bulk"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Anotar ingreso: tone Gain (also for \"Comprobar si se guardó\"), with a gain amount slip.",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:435"
    ],
    "shots": [
     {
      "module": "app",
      "file": "income-form-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Varios ingresos: the default brand tone, although the lines and total on the same receipt are gain green.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:440"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-incomes-three-lines-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Income is told apart by gain green everywhere else on both screens."
 },
 {
  "id": "F33",
  "theme": "Forms & entry",
  "title": "Messages under a form: FieldMessageText or a plain Text",
  "category": "divergent-use",
  "purpose": "A hint, refusal or receipt shown under a form's fields.",
  "features": [
   "expense",
   "income",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "FieldMessageText with its tone (\"Corregir este gasto cambia las cifras de septiembre.\"; the category editor's carry refusal when the carry card is absent).",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:489",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:209"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "A raw Text with no style or colour (\"Esta corrección afecta los ingresos de …\"), plus an always-on bodySmall inkSoft guidance line.",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:361",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:356"
    ],
    "shots": [
     {
      "module": "app",
      "file": "income-form-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "A bodyMedium Text in warn ink for a refusal and in gain ink for a receipt, inside the carry switch.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:206",
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:218"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The shared field message already has the tones; the same refusal is drawn two ways in one form."
 },
 {
  "id": "F34",
  "theme": "Forms & entry",
  "title": "'Máximo 12 dígitos' shows in the amount slip or above the button",
  "category": "pattern-drift",
  "purpose": "Say why the last key was refused at twelve digits.",
  "features": [
   "expense",
   "income",
   "bulk"
  ],
  "variants": [
   {
    "label": "A",
    "note": "The amount slip's error: replaces the slip label, in the slip, with a shake.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:390",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:267"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Varios gastos: a hint-tone FieldMessageText above the record button, away from the line being typed.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:415"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Said where the digits are, in the error tone; a receipt line can carry it as its reason."
 },
 {
  "id": "F35",
  "theme": "Forms & entry",
  "title": "The goal editor's month uses a choice chip where every other form uses FieldPill",
  "category": "divergent-use",
  "purpose": "The date control in the corner of the amount slip, opening a picker.",
  "features": [
   "expense",
   "income",
   "goals",
   "investments",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "FieldPill with the Event icon.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:215",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:395",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:273",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:228",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:574",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:214"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-movement-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "SelloChip(selected = false) with the Event icon.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:454"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-editor-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Used by every other amount slip and made for this slot."
 },
 {
  "id": "F36",
  "theme": "Forms & entry",
  "title": "The movement form and the receipts order the chips the other way round",
  "category": "pattern-drift",
  "purpose": "Choose the type and the target (investment or goal) of a movement.",
  "features": [
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Anotar movimiento: \"Tipo\" and the type chips, then \"Inversión\" and the investment chips.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:253"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movement-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Varios movimientos and Aportar varios: the target first, then the type chips.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:376",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:283"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movements-three-claro.png"
     },
     {
      "module": "app",
      "file": "goal-bulk-three-lines-claro.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Both receipts choose the target first; the single form handing a line over to Varios then keeps its order."
 },
 {
  "id": "F37",
  "theme": "Forms & entry",
  "title": "The three receipt screens build the same layout separately",
  "category": "duplicate",
  "purpose": "A receipt screen: the receipt in the free height, the choices with the undo notice over them, the action, the pad.",
  "features": [
   "bulk",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Varios movimientos and Aportar varios: a column spaced 8.dp, the receipt weighted 1, a box with the choices and the notice, the action, AmountKeypad.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:142",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:102"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movements-three-claro.png"
     },
     {
      "module": "app",
      "file": "goal-bulk-three-lines-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Varios gastos: the same tree with a 160 dp minimum height on the receipt.",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:135"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-expenses-three-lines-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "One shared receipt-screen layout, modelled on A and spaced with SelloSpacing.sm, keeps the three aligned."
 },
 {
  "id": "F38",
  "theme": "Forms & entry",
  "title": "A haptic after a save only on Anotar and Anotar ingreso",
  "category": "pattern-drift",
  "purpose": "Physical feedback once a write is confirmed and the form closes.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "A long-press haptic once per recorded id, kept across recreation.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:135",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:102"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Closes with no haptic (Varios gastos, and the other entry forms).",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:82"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "A save of several entries deserves at least the feedback of a single one."
 },
 {
  "id": "F39",
  "theme": "Forms & entry",
  "title": "Data replaced meanwhile: the income form freezes, the others learn it on save",
  "category": "pattern-drift",
  "purpose": "A restore or erase changes the history while an entry form is open.",
  "features": [
   "expense",
   "income",
   "bulk"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Income: watches the history; on a change says \"Tus datos cambiaron. No se guardó el ingreso; sal y vuelve a abrirlo.\" and blocks typing and saving.",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormViewModel.kt:196",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormState.kt:139"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Expense and Varios: only a refused save says it (\"Tus datos cambiaron mientras tanto. No se anotó el gasto; inténtalo de nuevo.\"); the draft stays and the form reads again.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryViewModel.kt:754",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryViewModel.kt:587"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Two of three, and keeps the draft so the person can carry on instead of leaving the form."
 },
 {
  "id": "F40",
  "theme": "Forms & entry",
  "title": "The income form's Borrar and spoken labels differ from the expense form's",
  "category": "divergent-use",
  "purpose": "Delete from the band, and what a screen reader says for the date pill and the record button.",
  "features": [
   "expense",
   "income",
   "bulk"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Expense (and Varios for the date): Borrar appears only once the expense is read and is read as \"Borrar este gasto\"; the date pill is read \"Fecha: Hoy, vie 9 oct\"; the button is read as a sentence with amount and category.",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:197",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:399",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:596",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:287"
    ],
    "shots": [
     {
      "module": "app",
      "file": "expense-entry-delete-unknown-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Income: Borrar shows at once (disabled while loading) and is read only as \"Borrar\"; the date pill and the button are read as their bare text.",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:147",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:273",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:420"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Same controls, same need; nothing can be deleted before the read, and the spoken label says what goes."
 },
 {
  "id": "F41",
  "theme": "Forms & entry",
  "title": "An archived category and a paused payment show their status two ways",
  "category": "divergent-use",
  "purpose": "Say in the form that the item is stopped, what that means, and offer to undo it.",
  "features": [
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Category: the label \"Archivada\" (labelMedium inkSoft) beside a neutral stamp with the same word, body in bodyMedium ink, outlined \"Desarchivar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:264"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-archived-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Fixed payment: \"En pausa desde …\" (or why) in bodyMedium ink beside a neutral stamp \"En pausa\", body in bodySmall inkSoft, outlined \"Reanudar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:313"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "The label says something the stamp does not (since when, or why) and the note uses the soft style; one shared status slip for both."
 },
 {
  "id": "F42",
  "theme": "Navigation & screen frames",
  "title": "The category editor leaves with Back; every other form with Close",
  "category": "pattern-drift",
  "purpose": "Leave a full-screen form from its top bar (asking first when there is a draft).",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Close ✕, read \"Cerrar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:191",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:143",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:117",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:115",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:147",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:75",
     "app/src/main/java/com/software/sello/feature/recurrence/ScheduleEditorScreen.kt:106",
     "app/src/main/java/com/software/sello/presentation/movement/MovementForm.kt:47"
    ],
    "shots": [
     {
      "module": "app",
      "file": "schedule-editor-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "goal-editor-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Back arrow, read \"Volver\" (category_editor_back), on Nueva categoría and Editar categoría.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:151"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Every other form closes with ✕; Back stays for pushed screens drawn by the shell."
 },
 {
  "id": "F43",
  "theme": "Navigation & screen frames",
  "title": "Entry forms put their title on the band, receipts on the desk",
  "category": "pattern-drift",
  "purpose": "Tell an entry form apart from a tab before anything is typed.",
  "features": [
   "expense",
   "income",
   "bulk",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SelloScaffold band = colors.band with EntryLayout's band: the blue band under the title runs into the amount slip (as SelloScaffold's KDoc asks of entry forms).",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:188",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:140",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:90",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:110"
    ],
    "shots": [
     {
      "module": "app",
      "file": "expense-entry-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "No band: the title row sits on the desk colour (Varios gastos, Aportar varios).",
    "files": [
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:105",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:78"
    ],
    "shots": [
     {
      "module": "app",
      "file": "bulk-expenses-three-lines-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "What SelloScaffold documents for entry forms and what every single-entry form does."
 },
 {
  "id": "F44",
  "theme": "Navigation & screen frames",
  "title": "Three top bars: MonthBar and TitleBar copy each other and differ from the scaffold's",
  "category": "duplicate",
  "purpose": "The top bar of a screen on the band.",
  "features": [
   "recibo",
   "resumen",
   "shell",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MonthBar and TitleBar (ShellBars.kt in :app): the same band row, min 56 dp, headlineSmall raised to 24 sp; used by the four tabs.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:63",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:122"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-past-month-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "SelloScaffold's private TopBar: min 50 dp, titleLarge, a navigation icon and actions; used by pushed screens and forms.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/SelloScaffold.kt:299"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-editor-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Merge MonthBar and TitleBar into one band bar with a title slot in :design-system beside SelloScaffold, which already takes it as topBar; then align B's height and title style with it."
 },
 {
  "id": "F45",
  "theme": "Navigation & screen frames",
  "title": "Screens that do not follow the selected month are titled two ways",
  "category": "pattern-drift",
  "purpose": "Title a pushed screen whose content is not the selected month.",
  "features": [
   "recibo",
   "shell",
   "investments",
   "recurrence",
   "settings",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "The title carries the month (\"Configuración · Octubre 2026\") with a chevron that opens the month picker.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/ShellScreen.kt:236",
     "app/src/main/java/com/software/sello/navigation/SettingsDestinations.kt:32",
     "app/src/main/java/com/software/sello/navigation/BackupDestinations.kt:43",
     "app/src/main/java/com/software/sello/navigation/ResetDestinations.kt:34",
     "app/src/main/java/com/software/sello/navigation/ReciboDestinations.kt:185",
     "app/src/main/java/com/software/sello/navigation/ReciboDestinations.kt:212",
     "app/src/main/java/com/software/sello/navigation/RecurrenceDestinations.kt:83"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "The plain title, no month and no picker (\"the title has no month to pick\").",
    "files": [
     "app/src/main/java/com/software/sello/navigation/RecurrenceDestinations.kt:42",
     "app/src/main/java/com/software/sello/navigation/InvestmentDestinations.kt:212"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Configuración says it shows nothing of the month, and a month in the title of Pagos previstos or Ajustar presupuesto can disagree with what they show."
 },
 {
  "id": "F46",
  "theme": "Navigation & screen frames",
  "title": "The gap between slips is md, lg or sm",
  "category": "divergent-use",
  "purpose": "Vertical gap between the slips of a scrolling screen.",
  "features": [
   "recibo",
   "resumen",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "spacedBy(spacing.md).",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:99",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:101",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:116",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:172"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Resumen: spacedBy(spacing.lg) plus extra vertical padding inside the scaffold padding.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:81",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:123"
    ],
    "shots": [
     {
      "module": "app",
      "file": "resumen-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Todos los gastos: spacedBy(spacing.sm), one slip per expense.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryExpensesScreen.kt:62"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-expenses-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used; Resumen's own comment says the scaffold sets its margins \"as on Recibo\"."
 },
 {
  "id": "F47",
  "theme": "Navigation & screen frames",
  "title": "After a replace or erase, Back is wired two ways",
  "category": "pattern-drift",
  "purpose": "Once the history is (or may be) replaced, the arrow and system Back restart the app.",
  "features": [
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Respaldo: the arrow decides in navigation from the restore state; system Back has one handler in BackupRoot (only when replaced) and another at the destination that always pops.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/BackupDestinations.kt:36",
     "app/src/main/java/com/software/sello/navigation/BackupDestinations.kt:49",
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:55"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Borrar datos: one ResetViewModel.back() serves the arrow and the screen's BackHandler (restart, wait while erasing, close the word step, or close).",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:64",
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:72",
     "app/src/main/java/com/software/sello/navigation/ResetDestinations.kt:33"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "One function owned by the feature serves both exits; A splits the rule and relies on handler order."
 },
 {
  "id": "F48",
  "theme": "Lists, rows & charts",
  "title": "Icon-circle list rows are built five ways",
  "category": "duplicate",
  "purpose": "A tappable row: an icon in a circle, a name over a caption, an amount or chevron at the end.",
  "features": [
   "recibo",
   "resumen",
   "goals",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ScheduleRow (:design-system): a 34 dp tinted circle, name over caption, amount, a rule under each row, min 60 dp.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:122",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:252"
    ],
    "shots": [
     {
      "module": "app",
      "file": "schedules-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "LimitRow (Ajustar presupuesto): CategoryCircle 36 dp, name over caption, amount, 18 dp chevron, min 56 dp, read as one button.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:356"
    ],
    "shots": [
     {
      "module": "app",
      "file": "adjust-budget-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "SpendingRankRow (Resumen): CategoryCircle 36 dp, name over a 6 dp share bar, amount, 18 dp chevron, min 52 dp, not merged for screen readers.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:156"
    ],
    "shots": [
     {
      "module": "app",
      "file": "resumen-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Category removal: TargetRow, ChoiceRow and IconCircle (34 dp paperAlt circle, a rule drawn by hand, min 56 dp).",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:305",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:594",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:627"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-removal-pick-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "E",
    "note": "Goal replace sheet: its own ChoiceRow with the same 34 dp circle.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:135"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-replace-sheet-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "One shared row in :design-system modelled on ScheduleRow, with a caption slot, an optional chevron and B's one-node reading."
 },
 {
  "id": "F49",
  "theme": "Lists, rows & charts",
  "title": "InvestmentRow re-implements MovementRow with other sizes",
  "category": "duplicate",
  "purpose": "A ruled row: icon circle, title over detail, amount over a small figure.",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MovementRow (:design-system): min 56 dp, 32 dp circle, 18 dp icon, 8 dp vertical padding.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:77",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:327"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-detail-chart-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "InvestmentRow (feature-local): min 60 dp, 36 dp circle (dashed when there is no value), 20 dp icon, 6 dp padding, its own rule.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:126"
    ],
    "shots": [
     {
      "module": "app",
      "file": "inversiones-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Already shared; add a fill colour and a 'no value' look to it instead of a second row."
 },
 {
  "id": "F50",
  "theme": "Lists, rows & charts",
  "title": "Group headings inside a slip: sentence case or uppercase",
  "category": "divergent-use",
  "purpose": "Name a group of rows inside one slip.",
  "features": [
   "recibo",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "titleSmall in inkSoft, sentence case (\"Archivadas\", \"Sin límite\").",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:493",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:216"
    ],
    "shots": [
     {
      "module": "app",
      "file": "adjust-budget-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Uppercased in code, labelMedium raised to W800, inkSoft, 14 (or 10) dp above and 2 dp below (\"FALTAN EN OCTUBRE · 2\", \"POR VENIR EN OCTUBRE\", \"MÁS ADELANTE\"); built twice.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:319",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:163"
    ],
    "shots": [
     {
      "module": "app",
      "file": "schedules-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "category-fixed-review-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "A type token as it is, in the sentence case the rest of the app uses."
 },
 {
  "id": "F51",
  "theme": "Lists, rows & charts",
  "title": "Section headings inside slips use four text styles",
  "category": "divergent-use",
  "purpose": "The heading of a section slip (movements, chart, earlier rates, a form section).",
  "features": [
   "goals",
   "investments",
   "settings"
  ],
  "variants": [
   {
    "label": "A",
    "note": "titleMedium with an explicit W800 (titleMedium is already 800): \"Aportes por mes\", \"Movimientos\", \"Valor y lo que pusiste\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:356",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:387",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:203",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:299"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-detail-movements-claro.png"
     },
     {
      "module": "app",
      "file": "investment-detail-chart-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "titleMedium as it is: \"Tus sellos\", \"¿Ya la lograste?\", \"Para cuándo\", GoalSaveProblem's title, Configuración's section titles.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:491",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:305",
     "app/src/main/java/com/software/sello/feature/goals/GoalSaveProblem.kt:37",
     "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:325"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "titleSmall: \"Dónde está, de mayor a menor\", \"Tasas anteriores\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:248",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateScreen.kt:88"
    ],
    "shots": [
     {
      "module": "app",
      "file": "inversiones-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "usd-rate-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Body size with W800: \"Sellos\" in the goal detail hero.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:247"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-detail-movements-claro.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Most used once A's redundant weight is dropped, and a type token as it is."
 },
 {
  "id": "F52",
  "theme": "Lists, rows & charts",
  "title": "Share and progress bars are drawn four ways",
  "category": "duplicate",
  "purpose": "Show an amount as a filled share of a track.",
  "features": [
   "resumen",
   "goals"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SplitBar (:design-system), 14 dp, with height and stripe parameters.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/SummaryCharts.kt:43",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:233"
    ],
    "shots": [
     {
      "module": "app",
      "file": "resumen-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ComparisonBars: a 14 dp Box, RoundedCornerShape(4.dp), paperDim track, brand or past-bar fill.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:98"
    ],
    "shots": [
     {
      "module": "app",
      "file": "resumen-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "SpendingRankRow's track: a 6 dp Box, shapes.control, paperDim track, brand fill.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:175"
    ],
    "shots": []
   },
   {
    "label": "D",
    "note": "Goal progress: a 9 dp Box with a gain fill, drawn in GoalCard and again in the goal detail's hero (with the percentage and amount head copied too).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:304",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:239"
    ],
    "shots": [
     {
      "module": "app",
      "file": "metas-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Already shared and parameterised; B, C and D are single-segment cases of it."
 },
 {
  "id": "F53",
  "theme": "Lists, rows & charts",
  "title": "Tapping a chart column behaves three ways",
  "category": "pattern-drift",
  "purpose": "Pick one column of a ColumnsChart to read its exact figures.",
  "features": [
   "resumen",
   "goals",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Resumen: the label and amount appear above the chart and clear after 3 seconds.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/ResumenViewModel.kt:150",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:375"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Category detail: the selection stays until another column is tapped; leader lines appear under the chart.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:156",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:318"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Goal detail: columns cannot be selected.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:359"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "A figure that stays until the person moves on cannot vanish mid-read; one place for the detail everywhere."
 },
 {
  "id": "F54",
  "theme": "Lists, rows & charts",
  "title": "A limit with its base and carry is laid out five ways",
  "category": "divergent-use",
  "purpose": "Show the month's limit together with the base and the carry that make it.",
  "features": [
   "recibo",
   "resumen",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "CarryBreakdownSection over :design-system CarryBreakdown: three leader lines (Límite base, carry, Límite del mes) and an optional footnote.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/carry/Carry.kt:118",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:328",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:386"
    ],
    "shots": [
     {
      "module": "app",
      "file": "carry-detail-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Recibo (MonthCarrySum): a strong \"Límite del mes\" line, then a caption \"X base + ↻ Y sobrante\" with the carry badge, then Gastado.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:43"
    ],
    "shots": [
     {
      "module": "app",
      "file": "carry-recibo-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Ajustar presupuesto: one leader line \"Límite de octubre\" with the caption \"base X · de meses anteriores ±Y\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:181"
    ],
    "shots": []
   },
   {
    "label": "D",
    "note": "Category detail's history selection: three leader lines written by hand, a copy of A.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:323"
    ],
    "shots": []
   },
   {
    "label": "E",
    "note": "Limit sheet header: one sentence \"De meses anteriores ±X · gastaste Y en …\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:331"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Already shared and used by two features; D is a literal copy of it."
 },
 {
  "id": "F55",
  "theme": "Lists, rows & charts",
  "title": "'Un mes normal' is drawn and worded twice, with a duplicated plural",
  "category": "divergent-use",
  "purpose": "Show the average spending of complete months above a month-by-month chart.",
  "features": [
   "resumen",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Resumen: a row \"Un mes normal\" with the amount in titleMedium W900 on the right; basis from resumen_normal_month_basis.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:351",
     "app/src/main/res/values/strings.xml:771"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Category detail: \"Un mes normal aquí\" in labelMedium inkSoft over the amount in headlineSmall; basis from category_detail_history_average, whose text is the same as A's.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:230",
     "app/src/main/res/values/strings.xml:267"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Label over figure matches the answer slips; one plural instead of two identical ones."
 },
 {
  "id": "F56",
  "theme": "Lists, rows & charts",
  "title": "When a fixed payment comes: a date pill on one list, the caption on two",
  "category": "divergent-use",
  "purpose": "Show the day a fixed payment comes this month in a ScheduleRow.",
  "features": [
   "recibo",
   "category",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Pagos e ingresos fijos: a pill under the amount (\"Mañana, …\" or the day; \"próximo …\" for later ones).",
    "files": [
     "app/src/main/java/com/software/sello/feature/recurrence/SchedulesScreen.kt:394"
    ],
    "shots": [
     {
      "module": "app",
      "file": "schedules-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Pagos fijos in a category and Pagos previstos: the day in the caption under the name, no pill.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:91",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:247"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-fixed-coming-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "Two of three lists of the same payments, and it keeps the amount column alone."
 },
 {
  "id": "F57",
  "theme": "Lists, rows & charts",
  "title": "A goal's four stamps are spread three ways",
  "category": "divergent-use",
  "purpose": "Show a goal's four stamp places (25 %, 50 %, 75 %, Meta) across a card.",
  "features": [
   "goals"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Its own Row(SpaceBetween) of MilestonePlace, bypassing MilestoneRow (goal card, celebration).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:312",
     "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:272"
    ],
    "shots": [
     {
      "module": "app",
      "file": "metas-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "MilestoneRow as it is: start-aligned, 8 dp apart, scrolls (goal detail, empty Metas).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:259",
     "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:225"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-detail-movements-claro.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "MilestoneRow(evenly = true): each stamp centred in an equal share (goal editor).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:510"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-editor-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "C",
  "reason": "The shared MilestoneRow already spreads the stamps evenly; the card's own row and the detail's packed row can use it."
 },
 {
  "id": "F58",
  "theme": "Lists, rows & charts",
  "title": "Trailing chevrons are 18, 20 or 24 dp",
  "category": "divergent-use",
  "purpose": "The › that marks a row or link as opening something.",
  "features": [
   "recibo",
   "resumen"
  ],
  "variants": [
   {
    "label": "A",
    "note": "18 dp in outline ink (LimitRow, SpendingRankRow).",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:406",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:189"
    ],
    "shots": [
     {
      "module": "app",
      "file": "adjust-budget-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "20 dp, brand ink on the Ajustar link, outline ink on the invitation.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:291",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:343"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-invitation-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "The Icon default of 24 dp in :design-system Footnote.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:603"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-fixed-review-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used size for rows; one size makes the chevron read as one sign."
 },
 {
  "id": "F59",
  "theme": "Wording & formatting",
  "title": "Retry is 'Reintentar' or 'Intentar de nuevo', and messages say 'try again' three ways",
  "category": "divergent-use",
  "purpose": "The try-again button, and the end of a message that asks for it.",
  "features": [
   "recibo",
   "resumen",
   "startup",
   "settings",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Button \"Reintentar\" (sello_retry, the default of ErrorSlip and StaleSlip; recibo_stale_retry, limit_sheet_retry); messages end \"Vuelve a intentarlo.\".",
    "files": [
     "design-system/src/main/res/values/strings.xml:3",
     "app/src/main/res/values/strings.xml:228",
     "app/src/main/res/values/strings.xml:212",
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:382",
     "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:109"
    ],
    "shots": [
     {
      "module": "app",
      "file": "upcoming-stale-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Button \"Intentar de nuevo\": start-up, backup_retry, export_retry, reset_retry.",
    "files": [
     "app/src/main/res/values/strings.xml:641",
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:154",
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:259",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:229",
     "app/src/main/java/com/software/sello/feature/backup/ExportControls.kt:135",
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:104"
    ],
    "shots": [
     {
      "module": "app",
      "file": "startup-failure-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Messages ending \"Intenta de nuevo.\" (Resumen) or \"Inténtalo de nuevo.\" (more expenses, past-limit notice).",
    "files": [
     "app/src/main/res/values/strings.xml:725",
     "app/src/main/res/values/strings.xml:291",
     "app/src/main/res/values/strings.xml:1084"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Most used and the shared default; Configuración shows both button words on one screen today."
 },
 {
  "id": "F60",
  "theme": "Wording & formatting",
  "title": "The unknown-outcome sentence is worded three ways",
  "category": "divergent-use",
  "purpose": "Tell the person a write's result is not known and must be checked before trying again.",
  "features": [
   "recibo",
   "shell",
   "expense",
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "\"Aún no sabemos si se guardó. Compruébalo antes de volver a anotar.\" (expense_entry_unknown, on most forms); the limit sheet's \"Aún no sabemos si se guardó.\"; the expense form's \"Aún no sabemos si se borró…\".",
    "files": [
     "app/src/main/res/values/strings.xml:567",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:405",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:473",
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:186",
     "app/src/main/java/com/software/sello/feature/goals/GoalMovementScreen.kt:307",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:348",
     "app/src/main/res/values/strings.xml:215"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-movements-unknown-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "\"No sabemos si se guardó. Compruébalo antes de seguir.\" (investment_editor_unknown, goal_editor_unknown) and \"No sabemos si se guardó el cambio…\" (goal_error_unknown).",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:333",
     "app/src/main/java/com/software/sello/feature/goals/GoalEditorScreen.kt:236",
     "app/src/main/java/com/software/sello/feature/goals/GoalSaveProblem.kt:52"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-editor-unresolved-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "\"No sabemos si el gasto se borró. Compruébalo antes de volver a intentarlo.\" (correction_delete_unknown, expense lists and the notice bar).",
    "files": [
     "app/src/main/res/values/strings.xml:1824"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-delete-unknown-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used; one sentence shape, \"Aún no sabemos si se …\", for the same situation everywhere."
 },
 {
  "id": "F61",
  "theme": "Wording & formatting",
  "title": "The way into the several-movements receipt has three names",
  "category": "divergent-use",
  "purpose": "The text button that opens the receipt for several movements at once.",
  "features": [
   "recibo",
   "income",
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "\"Anotar varios\" (metas_record_several, inversiones_record_several, category_detail_record_several, income_record_several).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/MetasScreen.kt:158",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:314"
    ],
    "shots": [
     {
      "module": "app",
      "file": "metas-bulk-cobalto-light-font1.0.png"
     },
     {
      "module": "app",
      "file": "inversiones-foot-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "\"Aportar varios\" (goal_bulk_title) on the goal detail's foot and as that receipt's title, although it records withdrawals too.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:479",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:79"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-detail-bulk-link-claro.png"
     },
     {
      "module": "app",
      "file": "goal-bulk-three-lines-claro.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "\"Varios movimientos\" (investment_movements_title) on the investment detail's foot and as that receipt's title.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:361"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-detail-varios-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "A verb that says what happens, already on five screens."
 },
 {
  "id": "F62",
  "theme": "Wording & formatting",
  "title": "The same validation problem is worded differently on the expense, income and Varios forms",
  "category": "divergent-use",
  "purpose": "Say why a typed amount, note or name cannot be used.",
  "features": [
   "expense",
   "income",
   "bulk"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Expense (Varios reuses it): \"Eso no es un monto…\", \"El monto debe ser mayor que cero.\", \"El monto es mayor que el máximo de 999.999.999.999 pesos.\"; \"La nota puede tener hasta 60 caracteres.\"; \"La nota no puede tener saltos de línea.\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:717",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:500",
     "app/src/main/res/values/strings.xml:552"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Income: one amount message \"Escribe un monto entero entre 1 y 999.999.999.999 COP.\"; \"La nota admite hasta 60 caracteres, sin caracteres de control.\"; \"No puede tener saltos de línea ni caracteres de control.\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:269",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:478",
     "app/src/main/res/values/strings.xml:669"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Says the specific problem in plain words, and \"pesos\" rather than \"COP\"."
 },
 {
  "id": "F63",
  "theme": "Wording & formatting",
  "title": "Correcting an income reuses the new-entry wording",
  "category": "divergent-use",
  "purpose": "Say what a refused or abandoned correction leaves in place.",
  "features": [
   "expense",
   "income"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Expense corrections have their own wording: \"La fecha no puede ser posterior a hoy. El gasto quedó como estaba.\", \"No pudimos leer este gasto.\", \"¿Descartar los cambios?\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:743",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:218"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Income uses the same text for new and corrected: \"La fecha no puede ser posterior a hoy. No se anotó nada.\", \"No pudimos leer los ingresos. Inténtalo de nuevo.\", \"¿Descartar lo que escribiste?\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:381",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:166",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:224"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "A failed correction leaves the record as it was; \"No se anotó nada\" does not fit it."
 },
 {
  "id": "F64",
  "theme": "Wording & formatting",
  "title": "The limit sheet says the draft is kept in two words",
  "category": "divergent-use",
  "purpose": "After a refused save, reassure that what was typed is still there.",
  "features": [
   "recibo"
  ],
  "variants": [
   {
    "label": "A",
    "note": "\"Tu cambio sigue aquí.\" (limit_sheet_not_saved, carry_limit_cap).",
    "files": [
     "app/src/main/res/values/strings.xml:214",
     "app/src/main/res/values/strings.xml:1802"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "\"Tu borrador sigue aquí.\" (carry_unavailable_save).",
    "files": [
     "app/src/main/res/values/strings.xml:1804"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Two of the sheet's three errors already say it this way."
 },
 {
  "id": "F65",
  "theme": "Wording & formatting",
  "title": "Adding an investment or goal has two labels",
  "category": "divergent-use",
  "purpose": "Open the editor for a new investment or goal from its tab.",
  "features": [
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Title chip \"Nueva inversión\" / \"Nueva meta\" once there are items.",
    "files": [
     "app/src/main/java/com/software/sello/navigation/InvestmentDestinations.kt:85",
     "app/src/main/java/com/software/sello/navigation/GoalDestinations.kt:92"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Empty-state dock \"Agregar inversión\" / \"Crear una meta\".",
    "files": [
     "app/src/main/java/com/software/sello/navigation/InvestmentDestinations.kt:98",
     "app/src/main/java/com/software/sello/navigation/GoalDestinations.kt:105"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The editor that opens is titled \"Nueva inversión\" / \"Nueva meta\"; one name for one action."
 },
 {
  "id": "F66",
  "theme": "Wording & formatting",
  "title": "'Make a backup' has four labels",
  "category": "divergent-use",
  "purpose": "The button that starts a new backup file.",
  "features": [
   "settings",
   "backup"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Configuración's card: \"Crear\" (no backup yet) or \"Respaldar\", then a dialog whose action is \"Elegir dónde guardar\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:261",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:164"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Respaldo: \"Crear respaldo\" or \"Crear otro respaldo\", with the caption \"Luego eliges dónde guardarlo.\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:110"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "After an unfinished save: \"Crear un respaldo nuevo\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:268"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Names the object; a lone \"Crear\" on the card does not say what it creates."
 },
 {
  "id": "F67",
  "theme": "Wording & formatting",
  "title": "Month and day names are formatted outside MonthNames and DateLabels",
  "category": "duplicate",
  "purpose": "Write a month or weekday name in Spanish.",
  "features": [
   "resumen",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MonthNames and DateLabels, from the string arrays month_names, month_names_short, month_names_abbreviated and weekday_names_short.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/month/MonthNames.kt:30",
     "app/src/main/java/com/software/sello/presentation/date/DateLabels.kt:74"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-picker-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "java.time with an es-CO locale (LLLL and \"d 'de' MMMM\" patterns, hand-written day-name maps, DayOfWeek.getDisplayName).",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/ResumenRenderModel.kt:192",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenRenderModel.kt:211",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryInsightSections.kt:142"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "Reads month_names_abbreviated itself to write \"oct 2026\", which DateLabels.monthYear already does.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:813"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Names from resources in one place, so a month reads the same on every screen."
 },
 {
  "id": "F68",
  "theme": "Wording & formatting",
  "title": "A signed figure inside a sentence is written by six private helpers",
  "category": "duplicate",
  "purpose": "Write an amount with its sign (true minus U+2212, and + where shown) inside a sentence or a before → after line.",
  "features": [
   "recibo",
   "shell",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Minus only: ReciboScreen's MoneyTextValue.text() and category removal's shown().",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:608",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:858"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Plus and minus: CategoryCarry's text(), the notice bar's signedDigits(), Inversiones' signed() and Ajustar's signedText(), each with the same body.",
    "files": [
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:118",
     "app/src/main/java/com/software/sello/navigation/CorrectionNoticeBar.kt:503",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:373",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:591"
    ],
    "shots": [
     {
      "module": "app",
      "file": "carry-drop-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "One extension beside MoneyTextValue, showing the sign the value carries, so the minus stays U+2212 everywhere."
 },
 {
  "id": "F69",
  "theme": "Wording & formatting",
  "title": "A bold figure inside a soft sentence: weight only, or weight and ink",
  "category": "divergent-use",
  "purpose": "Make the figure or date stand out in a soft explanatory sentence.",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SpanStyle(fontWeight = W800) only, each screen with its own helper.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:387",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementScreen.kt:343",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentEditorScreen.kt:312"
    ],
    "shots": [
     {
      "module": "app",
      "file": "inversiones-foot-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Tasa del dólar: W800 and colors.ink.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/UsdRateEntryScreen.kt:160"
    ],
    "shots": [
     {
      "module": "app",
      "file": "usd-rate-entry-unknown-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used; one shared bold-part helper ends four copies."
 },
 {
  "id": "F70",
  "theme": "Tokens & foundations",
  "title": "Spacing is hard-coded where SelloSpacing has the value",
  "category": "token-bypass",
  "purpose": "Gaps, paddings and touch targets inside slips, rows and bars.",
  "features": [
   "recibo",
   "resumen",
   "shell",
   "income",
   "bulk",
   "goals",
   "investments",
   "category",
   "recurrence",
   "settings",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SelloTheme.spacing (xs 4, sm 8, md 12, lg 16 dp) and minTouchTarget (48 dp), as most of :app uses (265 uses).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/theme/SelloShapes.kt:24",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:272",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:179",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:315"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Literal dp: 398 in :design-system components (36 token uses there), 319 in :app. Values equal to a token (8.dp, 4.dp, 48.dp for touch targets) and off-grid ones with no token (6, 10, 14, 2, 9 dp).",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:639",
     "app/src/main/java/com/software/sello/feature/recibo/MonthCarrySum.kt:46",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:346",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:373",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:166",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:615",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:169",
     "app/src/main/java/com/software/sello/feature/bulk/BulkEntryScreen.kt:136",
     "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:268",
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:104",
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:206",
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:138",
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:117",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentMovementsScreen.kt:144",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:144",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:402",
     "app/src/main/java/com/software/sello/feature/category/CategoryCarry.kt:310",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:281",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:240",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:535",
     "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:49",
     "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:246",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:210",
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:154",
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:241",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:198",
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:316",
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:591"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Use the tokens; snap 6, 10 and 14 dp to one, or add a token where a size recurs."
 },
 {
  "id": "F71",
  "theme": "Tokens & foundations",
  "title": "Text sizes and weights are set by hand where a type token exists",
  "category": "token-bypass",
  "purpose": "Text styles for titles, figures, labels and buttons.",
  "features": [
   "recibo",
   "resumen",
   "shell",
   "goals",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MaterialTheme.typography tokens used as they are.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:442",
     "app/src/main/java/com/software/sello/navigation/MonthPicker.kt:68"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-picker-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "Sizes set by hand: every dialog title is headlineSmall at 22 sp (five in :design-system plus RemovalDialog); keypad 22 sp; button 15 sp; milestone label 9 sp, under labelSmall's 12 sp; shell bars at 24 and 21 sp; a 20 sp performance figure; celebration 48, 18, 15 and 13 sp.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:89",
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:149",
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:220",
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:297",
     "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:164",
     "app/src/main/java/com/software/sello/feature/category/CategoryRemovalSlips.kt:576",
     "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:143",
     "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:93",
     "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:106",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:96",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:142",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:98",
     "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:323"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-past-month-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Weights set by hand: labelMedium W600 for cell names, labelSmall W700 with letter spacing and titleLarge W900 on expense stubs, labelMedium W800 headings, titleMedium W900 amounts, labelSmall W400 basis.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboComponents.kt:141",
     "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:70",
     "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:79",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:166",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:230",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:90"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Use the type tokens; where a style recurs (the 22 sp dialog title, W800 labels, W900 figures), add it as a token once."
 },
 {
  "id": "F72",
  "theme": "Tokens & foundations",
  "title": "Corner radii outside SelloShapes",
  "category": "token-bypass",
  "purpose": "Round slips, lines, entry fields, segments, tags, bars and banners.",
  "features": [
   "recibo",
   "resumen",
   "goals",
   "recurrence"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SelloShapes tokens: control 10, button 13, stampSmall 8, stampLarge 10, stampHero 14 dp, bar, sheet.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/theme/SelloShapes.kt:13",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:178",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:403"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Radii set by hand: 11 dp plain slip; 12 dp on receipt lines, entry lines, the goal sheet banner and the day stepper; 8 dp segments (= stampSmall); 6 dp tag; 4 dp bars; 14 dp (= stampHero) in the celebration; MaterialTheme.shapes.small on the selected Recibo cell.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Slip.kt:80",
     "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:648",
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:344",
     "design-system/src/main/java/com/software/sello/designsystem/component/Switches.kt:108",
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:170",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:537",
     "app/src/main/java/com/software/sello/feature/recibo/ExpenseStubLine.kt:126",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:153",
     "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:364",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:476"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Use the tokens; add one for 12 dp if it is a real size."
 },
 {
  "id": "F73",
  "theme": "Tokens & foundations",
  "title": "The disabled alpha 0.38 is declared privately in eleven files",
  "category": "duplicate",
  "purpose": "How faded a control that cannot be used is.",
  "features": [
   "category",
   "recurrence",
   "settings"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Private constants in :design-system (SelloButton, Chips, SettingRow, MoreMenu, AmountKeypad, MoreSlip, Navigation).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:115",
     "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:231",
     "design-system/src/main/java/com/software/sello/designsystem/component/Settings.kt:133",
     "design-system/src/main/java/com/software/sello/designsystem/component/MoreMenu.kt:76",
     "design-system/src/main/java/com/software/sello/designsystem/component/AmountKeypad.kt:185",
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:339",
     "design-system/src/main/java/com/software/sello/designsystem/component/Navigation.kt:279"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Private constants in :app (ink swatches, category switch, category editor, repeat parts).",
    "files": [
     "app/src/main/java/com/software/sello/feature/settings/SettingsComponents.kt:93",
     "app/src/main/java/com/software/sello/feature/category/CategoryComponents.kt:114",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:699",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:529"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Make it one public token in :design-system's theme that both modules read."
 },
 {
  "id": "F74",
  "theme": "Tokens & foundations",
  "title": "Pills on the band use Color.White instead of the paper colour",
  "category": "token-bypass",
  "purpose": "The paper-coloured pill on the band (+ Nueva on the tab bars, \"Volver a …\" on the past-month ribbon).",
  "features": [
   "shell"
  ],
  "variants": [
   {
    "label": "A",
    "note": "colors.paper, as the dark theme already uses for both.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:196"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Color.White in the light theme (same as paper today in both inks, but not tied to it).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:197",
     "app/src/main/java/com/software/sello/navigation/ShellBars.kt:200"
    ],
    "shots": [
     {
      "module": "app",
      "file": "shell-past-month-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "BandChip's own KDoc calls it the paper pill; one source keeps both inks and themes in step."
 },
 {
  "id": "F75",
  "theme": "Tokens & foundations",
  "title": "Unread colour tokens: a category palette and a dividend colour",
  "category": "token-bypass",
  "purpose": "Colour a category's circle, and a dividend movement.",
  "features": [
   "recibo",
   "investments",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "As built: every category circle in brand ink (loss when over, outline when archived); dividends in gain green.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Category.kt:80",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:350"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Tokens that exist but nothing reads: twelve category colours per theme and semantic.dividend (violet).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:50",
     "design-system/src/main/java/com/software/sello/designsystem/theme/SelloColors.kt:81"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The built app reads well with one ink per state; delete the unread tokens rather than keep a second, unused source."
 },
 {
  "id": "F76",
  "theme": "Components in :design-system",
  "title": "Two renderers for a large amount",
  "category": "divergent-use",
  "purpose": "Draw a large amount that must fit its place.",
  "features": [
   "recibo",
   "resumen",
   "goals",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "MoneyText with style Hero or Total: steps from Hero down to Total when it does not fit; its KDoc calls it \"the only way an amount is drawn\". No screen uses Hero.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/MoneyText.kt:56",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:428"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "FittedMoneyText: shrinks from a start size until it fits; used by AnswerSlip and the goal, category and comparison heads.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:83",
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:559",
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:283",
     "app/src/main/java/com/software/sello/feature/goals/GoalDetailScreen.kt:223",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:291",
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:72"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "What every large answer already uses; fold it into MoneyText as a fitted style so there is one renderer."
 },
 {
  "id": "F77",
  "theme": "Components in :design-system",
  "title": "The answer header is rebuilt by hand outside AnswerSlip",
  "category": "duplicate",
  "purpose": "A slip whose first line is a label with an optional stamp and whose answer is one large amount.",
  "features": [
   "recibo",
   "resumen",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "AnswerSlip: label in inkSoft, a Stamp at −5° with 8 dp vertical padding, FittedMoneyText from 60 sp.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:534",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:190",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:164",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:227",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:474"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ComparisonSlip (Resumen with a comparison): the question in bodyLarge W600 inkSoft with 6 dp under it, then FittedMoneyText.",
    "files": [
     "app/src/main/java/com/software/sello/feature/resumen/SummaryComponents.kt:53"
    ],
    "shots": [
     {
      "module": "app",
      "file": "resumen-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Category detail's HeaderSlip: headline in bodyLarge inkSoft, a Stamp at its default angle with 6 dp padding, a 56 dp CategoryCircle beside FittedMoneyText from 44 sp.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:213"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Shared and used by four screens; a leading slot covers C's circle and its detail slot covers B."
 },
 {
  "id": "F78",
  "theme": "Components in :design-system",
  "title": "'Ajustar presupuesto ›' is a hand-built link while other in-slip links are text buttons",
  "category": "duplicate",
  "purpose": "A text action inside a slip that opens another screen.",
  "features": [
   "recibo",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "AdjustBudgetLink: a row with bodyLarge W800 brand text and a 20 dp chevron, minTouchTarget height, its own semantics.",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:265"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "SelloButton(kind = Text): \"Ver todos\", \"Ver todos los pagos fijos\", \"Anotar varios\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:500",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:150",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:283"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-varios-claro.png"
     }
    ]
   }
  ],
  "recommend": "B",
  "reason": "The shared button already handles touch target, ripple and semantics; a chevron, if wanted, belongs to it."
 },
 {
  "id": "F79",
  "theme": "Components in :design-system",
  "title": "Text buttons inside slips use four content paddings",
  "category": "divergent-use",
  "purpose": "Line a text button up with the slip's content edge.",
  "features": [
   "recibo",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Default ButtonDefaults.ContentPadding: the label sits indented from the text above (\"Reintentar\" in StaleSlip and the limit sheet, \"Ver todos los pagos fijos\" on Pagos previstos).",
    "files": [
     "app/src/main/java/com/software/sello/presentation/read/StaleSlip.kt:31",
     "app/src/main/java/com/software/sello/feature/recibo/AdjustBudgetScreen.kt:370",
     "app/src/main/java/com/software/sello/feature/recibo/UpcomingPayments.kt:283",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:75"
    ],
    "shots": [
     {
      "module": "app",
      "file": "limit-sheet-stale-claro.png"
     },
     {
      "module": "app",
      "file": "upcoming-stale-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "PaddingValues(start = md): ends at the content edge (\"Ver todos\").",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:513"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "PaddingValues(end = md): starts at the content edge (\"Comprobar si se borró\", \"Anotar varios\").",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:545",
     "app/src/main/java/com/software/sello/feature/recibo/CategoryDetailScreen.kt:571"
    ],
    "shots": [
     {
      "module": "app",
      "file": "category-detail-delete-unknown-claro.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "PaddingValues(0.dp): flush on both sides (\"Ver todos los pagos fijos\" in Pagos fijos).",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/CategoryFixedPayments.kt:155"
    ],
    "shots": []
   }
  ],
  "recommend": "C",
  "reason": "A label that starts at the content edge lines up with the text above; one rule instead of four values."
 },
 {
  "id": "F80",
  "theme": "Components in :design-system",
  "title": "Recibo's dismissible advice line is built by hand instead of SuggestionNote",
  "category": "duplicate",
  "purpose": "Offer one piece of advice that opens another screen and can be put away with ×.",
  "features": [
   "recibo",
   "resumen",
   "income"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ClosedMonthInvitation: a dashed outline, the Insights icon, bodyLarge W700 text, a 20 dp chevron and a Material IconButton × .",
    "files": [
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:300"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-invitation-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "SuggestionNote (:design-system) with title, evidence, advice, action and dismiss labels.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Suggestion.kt:30",
     "app/src/main/java/com/software/sello/feature/resumen/ResumenScreen.kt:441",
     "app/src/main/java/com/software/sello/feature/income/IncomeHistoryScreen.kt:201"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Shared and used twice; a one-line form should be a SuggestionNote variant, not a third design."
 },
 {
  "id": "F81",
  "theme": "Components in :design-system",
  "title": "ButtonPair is generic but lives in Recurrence.kt, and the replace sheet rebuilds it",
  "category": "duplicate",
  "purpose": "A secondary and a primary button side by side, the primary wider.",
  "features": [
   "goals",
   "recurrence",
   "settings",
   "backup"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ButtonPair(firstWeight, secondWeight), used by ConfirmSlip and ChoiceSlip, Tus datos and the repeat sheet.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:238",
     "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:158",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:254",
     "app/src/main/java/com/software/sello/presentation/recurrence/RepeatParts.kt:497"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "The goal replace sheet: a row spaced 10 dp with outlined \"Cancelar\" at weight 1 and a filled action at weight 2.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:99"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-replace-refused-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "The shared part already exists for this 1:2 pair; move it next to SelloButton so it can be found."
 },
 {
  "id": "F82",
  "theme": "Components in :design-system",
  "title": "Goal parts hand-build buttons instead of SelloButton",
  "category": "duplicate",
  "purpose": "A tappable filled action.",
  "features": [
   "goals"
  ],
  "variants": [
   {
    "label": "A",
    "note": "SelloButton (Filled, Outlined, Text; tones incl. Gain); loading blocks a double press.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:42"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "\"Marcar como lograda\": a gain-filled pill Box, faded to 0.6 while busy, no spinner.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:200"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "GoalPill's round + / ✓: a 48 dp clickable Box, disabled while completing with no busy sign.",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:136"
    ],
    "shots": [
     {
      "module": "app",
      "file": "recibo-goals-five-reached-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "D",
    "note": "Celebration \"Listo\" and \"Ver mi meta\": clickable Box and Row on the green flood (no SelloButton tone fits inverted colours).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:460",
     "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:476"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "SelloButton gives the touch target and the loading state; D may need an inverted tone added to it."
 },
 {
  "id": "F83",
  "theme": "Components in :design-system",
  "title": "QuietLine copies Footnote without the click",
  "category": "duplicate",
  "purpose": "A small icon-and-sentence line at the foot of an answer slip.",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Footnote (:design-system): min 48 dp, brand 18 dp icon, bodySmall inkSoft, a chevron; the rate line when it opens Dólar.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Paper.kt:583",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:200"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "QuietLine (feature-local): min 32 dp, inkSoft 18 dp icon, bodySmall inkSoft, no chevron; \"Valores anotados…\" and the rate line when it cannot open.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:400",
     "app/src/main/java/com/software/sello/feature/investments/InversionesScreen.kt:185"
    ],
    "shots": [
     {
      "module": "app",
      "file": "inversiones-foot-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Give Footnote an optional onClick; the same line then keeps its height and icon colour whether it opens or not."
 },
 {
  "id": "F84",
  "theme": "Components in :design-system",
  "title": "The in-slip outlined action is ink on the investment and brand on Dólar",
  "category": "divergent-use",
  "purpose": "The slip's own 'record a new figure' button (\"Anotar el valor de hoy\", \"Cambiar la tasa\").",
  "features": [
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ButtonKind.Outlined, tone Ink, Insights icon.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/InvestmentDetailScreen.kt:110"
    ],
    "shots": [
     {
      "module": "app",
      "file": "investment-detail-chart-claro.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ButtonKind.Outlined, default brand tone, Autorenew icon; the form it opens submits in ink.",
    "files": [
     "app/src/main/java/com/software/sello/feature/investments/UsdRateScreen.kt:129"
    ],
    "shots": [
     {
      "module": "app",
      "file": "usd-rate-cobalto-light-font1.0.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Every other investment action is ink (dock, editor, movement, rate entry)."
 },
 {
  "id": "F85",
  "theme": "Components in :design-system",
  "title": "The 'Guardado' stamp is green on Respaldo and brand on Configuración",
  "category": "divergent-use",
  "purpose": "The stamp that says the backup is saved.",
  "features": [
   "settings",
   "backup"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Respaldo's Último respaldo slip: StampInk.Good (gain green), like \"Restaurado\" and \"Borrado\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:149"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Configuración's backup card: the same string with StampInk.Done (brand).",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:308"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Every other finished-write stamp is Good; the same word should not change colour between two screens."
 },
 {
  "id": "F86",
  "theme": "Components in :design-system",
  "title": "The heading with a landing stamp is rebuilt four times",
  "category": "duplicate",
  "purpose": "A slip heading with a verdict stamp beside it that lands once.",
  "features": [
   "startup",
   "settings",
   "backup",
   "reset"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Respaldo: a FlowRow with a headlineSmall Text and Stamp(Good, land = true) padded 6 dp, live region.",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:138"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "The restored slip (Respaldo and start-up): the same FlowRow with RestoreHeading, in shared presentation code.",
    "files": [
     "app/src/main/java/com/software/sello/presentation/restore/RestoreSlips.kt:77"
    ],
    "shots": [
     {
      "module": "app",
      "file": "components-restore-chosen-set-aside.png"
     }
    ]
   },
   {
    "label": "C",
    "note": "Borrar datos done: the same FlowRow with RestoreHeading.",
    "files": [
     "app/src/main/java/com/software/sello/feature/reset/ResetScreen.kt:231"
    ],
    "shots": []
   },
   {
    "label": "D",
    "note": "Configuración's backup card: an icon and a labelLarge label with the stamp padded spacing.xs, no live region.",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:203"
    ],
    "shots": []
   }
  ],
  "recommend": "B",
  "reason": "Already shared presentation code; one part for 'heading + verdict stamp' ends three copies."
 },
 {
  "id": "F87",
  "theme": "Components in :design-system",
  "title": "Slip headings are re-typed inline next to the helpers that draw them",
  "category": "duplicate",
  "purpose": "The heading at the top of a slip or a Configuración section.",
  "features": [
   "startup",
   "settings",
   "backup"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Helpers: RestoreHeading (headlineSmall with heading semantics) and Configuración's SectionTitle (titleMedium, sm under it, heading semantics).",
    "files": [
     "app/src/main/java/com/software/sello/presentation/restore/RestoreSlips.kt:197",
     "app/src/main/java/com/software/sello/feature/settings/SettingsScreen.kt:325"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Inline copies: Respaldo's \"Último respaldo\" and \"Antes de guardar\", Tus datos' section title, start-up's \"Empezar de cero\".",
    "files": [
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:143",
     "app/src/main/java/com/software/sello/feature/backup/BackupScreen.kt:214",
     "app/src/main/java/com/software/sello/feature/backup/TusDatos.kt:119",
     "app/src/main/java/com/software/sello/feature/startup/StartupFailureScreen.kt:296"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "The helpers exist; inline copies drift in style or lose heading semantics."
 },
 {
  "id": "F88",
  "theme": "Components in :design-system",
  "title": "Dashed outlines are drawn by hand with two dash patterns",
  "category": "duplicate",
  "purpose": "A dashed edge that marks a plan, an offer or something not yet recorded.",
  "features": [
   "recibo",
   "goals",
   "investments"
  ],
  "variants": [
   {
    "label": "A",
    "note": "Modifier.dashedOutline: 5 dp dashes, 4 dp gaps, any corner (Recibo's invitation, AddChip, the plan parts); IconPicker's ring uses the same pattern by hand.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Plan.kt:44",
     "app/src/main/java/com/software/sello/feature/recibo/ReciboScreen.kt:315",
     "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:132",
     "design-system/src/main/java/com/software/sello/designsystem/component/IconPicker.kt:79"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "Hand-drawn dashed rings and borders at 4 dp dashes, 3 dp gaps (icon-in-use border, empty milestone, receipt line circle, an investment with no value).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/IconChoiceGrid.kt:132",
     "design-system/src/main/java/com/software/sello/designsystem/component/Goals.kt:288",
     "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:576",
     "app/src/main/java/com/software/sello/feature/investments/InvestmentComponents.kt:179"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "One modifier and one dash for every dashed edge; let it take a circle shape."
 },
 {
  "id": "F89",
  "theme": "Components in :design-system",
  "title": "The fading edge of a scrolling row is drawn two ways",
  "category": "duplicate",
  "purpose": "Fade the edge of a row or list that scrolls, to show there is more.",
  "features": [],
  "variants": [
   {
    "label": "A",
    "note": "A mask (offscreen layer, DstIn gradient), so the fade works on any background (ChipFlow, BulkReceipt).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:171",
     "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:621"
    ],
    "shots": []
   },
   {
    "label": "B",
    "note": "A gradient painted in the desk colour over the content (ChipRows), which shows on anything that is not the desk.",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:255"
    ],
    "shots": []
   }
  ],
  "recommend": "A",
  "reason": "Works on paper and desk alike; one fading-edge modifier for the three."
 },
 {
  "id": "F90",
  "theme": "Components in :design-system",
  "title": "A set of chips is laid out three ways",
  "category": "divergent-use",
  "purpose": "Lay out a set of choice chips.",
  "features": [
   "expense",
   "income",
   "goals",
   "category"
  ],
  "variants": [
   {
    "label": "A",
    "note": "ChipRows: up to two rows that scroll sideways together (12 uses).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Controls.kt:245",
     "app/src/main/java/com/software/sello/feature/expense/ExpenseEntryScreen.kt:442",
     "app/src/main/java/com/software/sello/feature/income/IncomeFormScreen.kt:305"
    ],
    "shots": [
     {
      "module": "app",
      "file": "expense-entry-cobalto-light-font1.0.png"
     }
    ]
   },
   {
    "label": "B",
    "note": "ChipFlow: chips that wrap onto more lines (only the category editor's limit suggestions).",
    "files": [
     "design-system/src/main/java/com/software/sello/designsystem/component/Chips.kt:156",
     "app/src/main/java/com/software/sello/feature/category/CategoryEditorScreen.kt:513"
    ],
    "shots": []
   },
   {
    "label": "C",
    "note": "A plain Row spaced 8 dp (Aportar varios' movement kinds).",
    "files": [
     "app/src/main/java/com/software/sello/feature/goals/GoalBulkScreen.kt:314"
    ],
    "shots": [
     {
      "module": "app",
      "file": "goal-bulk-three-lines-claro.png"
     }
    ]
   }
  ],
  "recommend": "A",
  "reason": "Most used and announced as one group; B and C are the only other layouts."
 }
];
