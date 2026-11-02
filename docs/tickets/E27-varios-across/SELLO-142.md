# SELLO-142 — Varios movimientos across several investments

- **Type:** Story
- **Priority:** P1
- **Status:** Backlog
- **Depends on:** SELLO-140
- **Gate:** G2

### Outcome
On Varios movimientos each line picks its investment, so the person records movements of
several investments on one receipt, and can start it from the Inversiones list.

### Acceptance criteria
- Under the highlighted line, chips name every investment, in two rows as Anotar varios
  shows categories. A line's title is its investment, with the type and the note under it.
- From an investment (its movements' foot, or the peeking slip on Anotar movimiento)
  line one starts in that investment with what was typed, as now. A new line copies the
  investment, type and date above it.
- The Inversiones list offers **Anotar varios** at its foot, opening the receipt with no
  investment chosen; hidden when there is no investment.
- A line with an amount and no investment blocks saving (**Falta 1 inversión**).
- Each line shows its amount in its investment's currency: dollars with the comma key for
  a USD investment, pesos otherwise. Changing a typed line's investment to one in another
  currency clears its amount.
- **Aportado** (aportes − retiros) is in the investment's currency while every line is in
  one currency; with COP and USD lines it is in COP at today's rate, as elsewhere (E19),
  and says so. With no rate it shows the parts in each currency instead of a made-up
  figure.
- A refused line is marked with its reason. An investment that changed or was deleted
  meanwhile asks to review and save again; it never overwrites.
- Saving, the unknown outcome, the confirmation and the restored draft (with each line's
  investment) behave as in SELLO-134, through SELLO-140's command.
- The design's Anotar varios section drops "one investment" for investments.
- ViewModel JVM tests cover choosing investments, a line without one, the list way in,
  switching currency, the mixed COP and USD total with and without a rate, a refused
  line, a changed investment and every save outcome. Screenshots show a mixed receipt.
