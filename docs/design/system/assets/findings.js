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
 "misuse": [],
 "copies": [],
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
     "file": "app/src/main/java/com/software/sello/feature/goals/CelebrationOverlay.kt:485",
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
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Recurrence.kt:286",
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
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/Feedback.kt:78",
     "area": "design-system:Feedback",
     "features": [],
     "note": "DialogSlip: the window dim of every dialog slip (platform dims with black at SCRIM_ALPHA)"
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
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:599",
     "area": "design-system:BulkReceipt",
     "features": [],
     "note": "Modifier.fadeEdges: fadeEdges top mask"
    },
    {
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/BulkReceipt.kt:607",
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
     "file": "design-system/src/main/java/com/software/sello/designsystem/component/SelloButton.kt:66",
     "area": "design-system:SelloButton",
     "features": [],
     "note": "SelloButton: Outlined/Text button container"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalStarSheet.kt:174",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "ChoiceRow: ChoiceRow: unpicked row has no fill"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:393",
     "area": "goals",
     "features": [
      "goals"
     ],
     "note": "GoalPinPill: GoalPinPill: unselected has no fill"
    },
    {
     "file": "app/src/main/java/com/software/sello/feature/goals/GoalComponents.kt:394",
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
