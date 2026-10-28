# Signed carryover rules and worked examples

**Ticket:** SELLO-037 · **Updated:** 2026-10-28 · **Status:** the rules are in
[ADR 0020](../decisions/0020-signed-carryover-policy.md) (CP1–CP28); these examples are
approved by merging the SELLO-037 pull request.

The inputs and expected values SELLO-038–043 implement and test against. Every figure was
worked out by hand, then rechecked with a throwaway calculation outside the repository that
used no app code. Amounts are whole COP pesos grouped as the app shows them; months are
financial months. They are expectations, not passing tests: tests copy these values and
never calculate them with the code under test.

## How to read them

`B` base, `K` carry-in, `C = B + K` capacity (Límite con arrastre), `S` spent,
`R = C − S` remaining; a closed month's `R` is the next month's `K`. `D` is the days left
in the month, today included, and the allowance is `50 × floor(max(R − Q, 0) / (50 × D))`
(`Q` is 0 unless stated). Past months read Cumplido when `S ≤ C`, otherwise Excedido; the
current month reads Excedido when `S > C`, Al límite when `S = C > 0`, otherwise Al día.
Unless a row says otherwise, **Comida** has base 100.000 in every month and carry on
from ene 2027, and months not mentioned have no expenses.

## Chains of months (CP4–CP8)

| ID | Situation | Result |
| --- | --- | --- |
| SC01 | Spent: ene 20.000, feb 70.000, mar 250.000 | ene `K` 0, `C` 100.000, `R` 80.000, Cumplido. feb `K` 80.000, `C` 180.000, `R` 110.000, Cumplido. mar `K` 110.000, `C` 210.000, `R` −40.000, Excedido by 40.000. abr `K` −40.000, `C` 60.000. Surplus and deficit both carry |
| SC02 | Spent: ene 120.000, feb 70.000 | feb `K` −20.000, `C` 80.000, `R` 10.000. mar `K` 10.000, `C` 110.000, not 130.000: that is 100.000 + (100.000 − 70.000) and forgets the earlier deficit |
| SC03 | Spent: ene 250.000, mar 20.000. A deficit larger than the next base | feb `K` −150.000, `C` −50.000, nothing spent, `R` −50.000: Excedido by 50.000 (past: `S` 0 > −50.000). mar `K` −50.000, `C` 50.000, `R` 30.000, Cumplido |
| SC04 | Spent: ene 30.000, then nothing recorded in feb, mar or abr (skipped months) | feb `K` 70.000, `C` 170.000. mar `K` 170.000, `C` 270.000. abr `K` 270.000, `C` 370.000. A month without expenses is zero spent: its whole base carries |
| SC05 | Spent: ene 400.000, then nothing in feb–abr | ene `R` −300.000. feb `K` −300.000, `C` −200.000. mar `K` −200.000, `C` −100.000. abr `K` −100.000, `C` 0: nothing spent is Al día, 1 spent is Excedido by 1. As a closed month `C` 0 with 0 spent is Cumplido. The deficit is repaid only by later bases |
| SC06 | A mar snapshot where feb cannot be read | A typed failure for every figure of mar that needs carry (CP8); no `K` of 0 and no base-only figure. A kept older snapshot is labelled older with its own date |

## Start and refusals (CP2, CP3, CP25)

| ID | Situation | Result |
| --- | --- | --- |
| SC07 | Carry off. ene spent 140.000 (40.000 over). Turned on 17 feb; feb spent 30.000 on days 1–16 and 20.000 on days 17–28 | `K` feb 0, `C` 100.000: the ene overrun never counts. All of feb counts from day 1: `S` 50.000, `R` 50.000, mar `K` 50.000, `C` 150.000. Not 180.000 (counting only the 20.000 after the 17th) and not 60.000 (carrying ene's overrun) |
| SC08 | Turning on for: an unlimited category; an archived one; a category with no limit this month; one already on; one with base 1.000.000.000.000 | Each refused with its own reason and nothing changed. Base 0 and base 999.999.999.999 are accepted |

## Verdicts and the daily allowance (CP14, CP15)

feb 2027 is the current month, today 22 feb, `D` = 28 − 22 + 1 = 7.

| ID | Situation | Result |
| --- | --- | --- |
| SC09 | ene spent 20.000; feb spent 30.000 | `C` 180.000, `R` 150.000, Al día, allowance 50 × floor(150.000 / 350) = 21.400. With the base alone it would be `R` 70.000 and 10.000 |
| SC10 | As SC09; feb spent 180.000, then 180.001 | 180.000: `R` 0, Al límite (`S = C > 0`), allowance 0. 180.001: Excedido by 1 |
| SC11 | ene spent 250.000 (`K` feb −150.000); feb spent 0, then 30.000 | `C` −50.000: `R` −50.000, Excedido by 50.000 with nothing spent this month, allowance 0, the −50.000 shown. After 30.000: `R` −80.000, Excedido by 80.000 |
| SC12 | Turned on in feb, base 100.000; 15 mar 2027 (31 days), `D` = 17. feb spent 199.151 | mar `K` −99.151, `C` 849, `R` 849 with nothing spent: Al día, allowance 0 (849 < 850). With feb spent 199.150: `C` 850, allowance 50. With feb 199.151 and mar spent 969: `R` −120, Excedido, remaining −120 and allowance 0, never −50: only the allowance is rounded and clamped |

## The overall budget and income (CP16, CP17)

Fixtures for SC13–SC14, feb 2027, today 22 feb (`D` = 7): **Comida** base 100.000 on
from ene, **Transporte** base 50.000 off, **Ocio** unlimited. ene: Comida spent 20.000.
feb: Comida 30.000, Transporte 60.000, Ocio 10.000; income 200.000.

| ID | Situation | Result |
| --- | --- | --- |
| SC13 | The overall month | Σ base 150.000, Σ carry 80.000 (Comida only), `C` 230.000, `S` 100.000, `R` 130.000, Al día, allowance 50 × floor(130.000 / 350) = 18.550. Comida `C` 180.000, `R` 150.000. Transporte `C` 50.000, `R` −10.000, Excedido on its own; its overrun carries nowhere. Ocio: Sin límite |
| SC14 | Income in SC13 | Por asignar 200.000 − 150.000 = 50.000 (base only; not −30.000 from 230.000). Ingresos menos gastos 100.000. Turning carry on or off changes neither |
| SC15 | Comida and Hogar, base 100.000 each, both on from ene. ene: Comida 160.000, Hogar 40.000. feb: Comida 40.000, Hogar 0, then Comida 41.000 | ene `R` −60.000 and 60.000. feb Comida `C` 40.000, Hogar `C` 160.000. Overall `C` 200.000, `S` 40.000, `R` 160.000, Al día. Comida Al límite; at 41.000 Excedido by 1.000 while the overall `R` is 159.000. Hogar's surplus never covers Comida |
| SC16 | Comida (on) spent 250.000 in ene; Transporte (off) base 50.000; feb, nothing spent | Comida `C` −50.000, Excedido by 50.000. Overall `C` 150.000 − 150.000 = 0: Al día with 0 spent. Transporte spending 1: overall Excedido by 1 while Transporte, `R` 49.999, is Al día |

## Transitions (CP9–CP13)

Fixture: ene spent 120.000, so feb `K` is −20.000; today 10 feb (`D` = 19), spent so far 30.000.

| ID | Situation | Result |
| --- | --- | --- |
| SC17 | Turn off | Sello shows −20.000 and: Límite con arrastre 80.000 → 100.000, Restante 50.000 → 70.000. Cancel: nothing changes. Confirm: feb `C` 100.000, `R` 70.000. ene stays `C` 100.000, spent 120.000, Excedido by 20.000. feb closes with 50.000 spent: mar `C` is 100.000, not 130.000 (carry kept: 80.000 − 50.000 = 30.000 carried) |
| SC18 | Turn off with a surplus (ene spent 20.000, `K` feb 80.000); or turn off with `K` 0 | Surplus: the confirmation shows 80.000; `C` 180.000 → 100.000, `R` 150.000 → 70.000. With `K` 0, or off in the start month, there is nothing to confirm and no chain remains |
| SC19 | SC17 confirmed on 10 feb; turned on again 3 abr | Chains: ene–feb (ended feb) and abr onwards. abr `K` 0, `C` 100.000; mar had no carry. ene and feb keep their figures. Off and on again in feb: feb `K` 0 |
| SC20 | Archive on 12 feb with `K` −20.000 | The confirmation shows −20.000. feb keeps its base 100.000, no new expenses; mar has no limit (paused). Unarchived in jun: finite again from jun, carry off; turned on in jun, `K` 0 |
| SC21 | ene spent 20.000 (`K` feb 80.000); on 5 feb the default becomes unlimited | The confirmation shows 80.000; carry ends in feb; feb is Sin límite. Back to 100.000 in jun: carry still off until turned on, `K` 0 |
| SC22 | Chain ene–abr (SC01, dropped −40.000 on 5 abr). Set mar's own limit to unlimited; then to 150.000 | Unlimited: refused, the chain would have a hole. 150.000: mar `C` 260.000, `R` +10.000, Cumplido; abr is outside the chain, still base 100.000; the list names the settled balance −40.000 → +10.000 (CP22) |
| SC23 | Base 0 on from ene. ene spent 0, or 30.000; or base set to 0 in feb after `K` 80.000 | Spent 0: feb `K` 0, `C` 0: Al día, 1 spent is Excedido by 1. Spent 30.000: feb `K` −30.000, `C` −30.000, Excedido by 30.000 with nothing spent. Base 0 with `K` 80.000: `C` 80.000, spending up to 80.000 is allowed |
| SC24 | ene spent 20.000, feb 70.000; on 3 mar the default becomes 120.000 (or 60.000) | ene and feb keep base 100.000 and `C` 100.000 and 180.000. mar `K` 110.000, `B` 120.000, `C` 230.000 (or `B` 60.000, `C` 170.000). Only the current month and later use the new base |

## Historical corrections (CP20–CP22)

Fixture for SC25–SC34: SC01's spending and chain; the current month is abr 2027, nothing
spent in it yet. "Lists" is what Sello shows before saving.

| ID | Situation | Result |
| --- | --- | --- |
| SC25 | Delete a 20.000 expense of ene | Lists: feb `C` 180.000 → 200.000 (+20.000); mar 210.000 → 230.000 (+20.000), `R` −40.000 → −20.000 (Excedido by 40.000 → 20.000); abr 60.000 → 80.000 (+20.000). Every base stays 100.000 |
| SC26 | A 70.000 expense of feb, recorded in Comida, moves to Transporte (base 50.000, on from ene, ene spent 10.000, nothing later). Two chains change | Comida: feb `R` 110.000 → 180.000; mar `C` 210.000 → 280.000 (+70.000), `R` −40.000 → +30.000, Excedido → Cumplido; abr 60.000 → 130.000. Transporte: feb `R` 90.000 → 20.000; mar `C` 140.000 → 70.000 (−70.000); abr 190.000 → 120.000. The overall `C` is unchanged (mar 350.000, abr 250.000); each category is listed |
| SC27 | The 20.000 expense of ene moves to mar (same category) | Lists feb 180.000 → 200.000 (+20.000) and mar 210.000 → 230.000 (+20.000) with `S` 270.000, so mar `R` stays −40.000. abr stays 60.000 and is not listed |
| SC28 | ene's own limit is corrected from 100.000 to 120.000 | ene `R` 80.000 → 100.000; lists feb 200.000, mar 230.000 (`R` −20.000), abr 80.000, each +20.000. feb's and mar's limits stay 100.000 |
| SC29 | A 40.000 expense of feb is deleted | feb `R` 110.000 → 150.000; lists mar 210.000 → 250.000 (+40.000), `R` −40.000 → 0, Excedido → Cumplido; abr 60.000 → 100.000 |
| SC30 | An expense of 200.000 dated in ene is added late | ene `R` 80.000 → −120.000. Lists feb `C` 180.000 → −20.000, `R` 110.000 → −90.000, Cumplido → Excedido by 90.000; mar 210.000 → 10.000, `R` −240.000; abr 60.000 → −140.000. Each shifts by −200.000 and the abr deficit is larger than its base |
| SC31 | 10 abr: a 15.000 expense is added to abr | abr `C` 60.000, `R` 45.000. No list: the current month's own figures update |
| SC32 | SC01; on 5 abr carry is turned off and −40.000 confirmed (chain ene–abr). On 20 abr the 20.000 expense of ene is deleted | Lists feb 180.000 → 200.000, mar 210.000 → 230.000 (`R` −40.000 → −20.000) and "balance not carried on abr 2027: −40.000 → −20.000". abr stays `C` 100.000 (base only) and is not listed |
| SC33 | A 30.000 expense dated dic 2026 (before the chain) or an old expense of a category with carry off | No list: no month of any chain changes; only the D04 scope confirmation |
| SC34 | SC25 is previewed, then another change lands before the person saves | Saving is refused with nothing changed; the preview is shown again with the new values |

## Reservations, catch-up and the forecast (CP18, CP19)

**Hogar**, base 1.000.000, on from ene, 15 mar 2027 (`D` = 17).

| ID | Situation | Result |
| --- | --- | --- |
| SC35 | ene spent 800.000, feb 1.000.000; mar spent 900.000; rent 150.000 reserved (`Q`) | mar `K` 200.000, `C` 1.200.000, `R` 300.000, `M` 150.000, allowance 50 × floor(150.000 / 850) = 8.800, Al día with 150.000 separated |
| SC36 | ene spent 1.200.000, feb 1.000.000; mar spent 900.000; `Q` 150.000 | feb `K` −200.000, `C` 800.000, `R` −200.000; mar `K` −200.000, `C` 800.000, `R` −100.000, `M` −250.000: Revisar, not Excedido; Falta para tus pagos 250.000; allowance 0. Once recorded: spent 1.050.000, `Q` 0, `R` −250.000, Excedido by 250.000 |
| SC37 | ene spent 1.000.000; feb recorded 800.000 and a 200.000 payment due 5 feb that catch-up has not recorded; 10 mar (`D` = 22), mar spent 500.000 | Before: feb `R` 200.000, mar `K` 200.000, `C` 1.200.000, `R` 700.000, `Q` 200.000 (pending in an earlier month of the chain), `M` 500.000, allowance 22.700. Catch-up records it in feb: feb `R` 0, mar `K` 0, `C` 1.000.000, `R` 500.000, `Q` 0, `M` 500.000, allowance 22.700. The headline never moves; no preview is asked |
| SC38 | SC37, mar 10, `S` 500.000, `N` 31, `d` 10 | Forecast 500.000 × 31 / 10 = 1.550.000, labelled an estimate, drawn against `C` (1.200.000, then 1.000.000); carry does not change it and the status stays Al día from recorded spending |

## Order, bounds and time (CP19, CP23–CP25)

| ID | Situation | Result |
| --- | --- | --- |
| SC39 | Category A: base 100.000, `K` +200.000 (`C` 300.000). Category B: base 200.000, `K` −50.000 (`C` 150.000). Order by limit, descending | B before A: ordering uses the base, not `C` |
| SC40 | The calculation is given base 9.223.372.036.854.775.807 for ene and feb, nothing spent (only damaged data or a bad file can reach it: CP25 refuses it as a command) | ene `C` 9.223.372.036.854.775.807 fits. feb `K` is the same and `C` = 18.446.744.073.709.551.614 does not fit a signed `Long`: the typed unrepresentable failure, never clipped to the maximum. A deficit past −9.223.372.036.854.775.808 fails the same way |
| SC41 | Chain from dic 2026, base 100.000, dic spent 90.000. And Comida in feb 2028 (29 days), 22 feb, `R` 160.000 | ene 2027 `K` 10.000, `C` 110.000: the year boundary is just the next month. `D` = 29 − 22 + 1 = 8, allowance 50 × floor(160.000 / 400) = 20.000 |

## Backup, restore and reset (CP26)

| ID | Situation | Result |
| --- | --- | --- |
| SC42 | A version 5 file with SC01's expenses and base 100.000 is restored | Every category is off: ene `C` 100.000; mar `R` −150.000; abr `C` 100.000, not 60.000. No starting deficit is guessed |
| SC43 | A file with SC01's expenses and a Comida chain open from ene 2027 | After restore the replay gives SC01: abr `K` −40.000, `C` 60.000. The file holds the chain, not the −40.000 |
| SC44 | Files rejected before the preview, each changing nothing: overlapping chains; a chain ending before it starts; a chain whose first month has an unlimited limit; a chain with a base of 1.000.000.000.000; a version this app cannot read | Each rejected whole, naming where, never what the file says |
| SC45 | Reset; or restoring a file with no chains over one that has them | All chains are gone and every category is off |

## Open points

None left for the owner. SELLO-041 designs the screens, including the layout of the
breakdown and the confirmations; the wording in ADR 0020 sets what each figure means.
