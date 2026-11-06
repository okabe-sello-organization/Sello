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
 *   dynamic:   { summary, explained: [{ ids, what, why }], notListed }: the colour findings
 *              closed or explained, and what was never listed; notListed: copies the audit left out.
 */
window.SELLO_FINDINGS = {
 "revision": "0bf22f5e",
 "dynamic": {
  "summary": "Every colour a screen or resource draws now comes from the theme (SELLO-190), so a later scheme built from the system's colours reaches every part. What such a scheme must still provide is on Foundations.",
  "explained": [
   {
    "ids": "H01–H05, H07",
    "what": "Fixed in SELLO-190",
    "why": "BandChip and PastMonthRibbon's return pill fill with paper; the white confetti chads are onBand (white in every palette); RepeatSheet's scrim is the new scrim role at SelloAlpha.scrim; the Settings ink swatches read SelloInk.swatch, a preview of an ink that may not be applied, so it can't follow the active scheme; both start-up windows (light and dark) are @color/sello_start_window, the desk; band, onBand, goalBand and investmentBand, and the investment fills' dark text, are set from the role they copy in lightPalette / darkPalette."
   },
   {
    "ids": "H06, H08, H09, H10",
    "what": "Palette source",
    "why": "These literals are the palette: the brand, paper and ink roles, the semantic colours, the 12 category colours and the investment inks. They stay in CobaltoPalette.kt, VioletaPalette.kt and SelloColors.kt only; nothing outside the theme reads them (Theme.kt is the only main-code caller of selloColors). A dynamic scheme replaces them by building SelloColors from the system's colours."
   },
   {
    "ids": "H11, H12",
    "what": "Launcher icon: not a colour the scheme should move",
    "why": "The launcher draws the icon before Sello runs, so the full-colour icon is fixed by nature. Android 13+ themed icons use the monochrome layer, whose black is only an alpha mask the system tints with the wallpaper's colours."
   },
   {
    "ids": "H13",
    "what": "Scrim and window dim",
    "why": "Material's scrim slot now reads the scrim role. DialogSlip's dim is the platform's window dim: setDimAmount takes only an amount, and its black is the same as the scrim role."
   },
   {
    "ids": "H14, H15",
    "what": "Alpha masks and transparent: no colour drawn",
    "why": "Black and transparent in a BlendMode.DstIn mask (ChipFlow, fadeEdges) give only alpha; Color.Transparent and the transparent system bars draw nothing, so the theme colour under them shows."
   },
   {
    "ids": "H16",
    "what": "White in the icon vectors",
    "why": "Every SelloIcon painter is drawn by Icon(), which tints it with a theme role or LocalContentColor, so the vectors' white never shows."
   }
  ],
  "notListed": "Color.Unspecified (it means \"inherit LocalContentColor\"), colours blended between two theme roles in animations (CelebrationOverlay.kt, Goals.kt), which follow the theme, and the sandbox badge's colours (app/src/debug), which never ship."
 },
 "misuse": [],
 "copies": [],
 "hardcoded": [],
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
