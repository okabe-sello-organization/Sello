# 0015 — Category detail insights

**Status:** owner-approved rules, implemented · **Date:** 2026-10-22

The owner requested the complete category detail and explicitly approved its order:
hero, latest expenses, En números, Cada semana gastas, Un mes normal aquí.
The v1.1 reference still owns presentation except for that order and later ADRs.

Weeks are day-of-month buckets 1–7, 8–14, 15–21, 22–28 and 29–month-end.
Only fully completed buckets enter the weekly average. Current-week remaining
allowance is max(category remainder, 0) times days left in this bucket, including
today, divided by days left in the month, including today. Round down once to whole
pesos, cap at the remaining category budget, and draw it above recorded spending
with stripes. It is a planning amount, never an expense or an overall allowance.
On October 22, 100,000 remaining and 10 available days gives 70,000 through October
28. With 5,000 remaining on October 31 the one-day bucket has 5,000 available.
An unlimited or past month has no remaining allowance. The daily figure reuses
MonthlyBudgetPolicy.allowance (down to multiples of COP 50), scoped to this category.

The historical average uses up to six completed months before the selected current
month (including the selected month when it is already closed), since known category
configuration. Include recorded zero-spending months; do not extrapolate before
configuration. Show history after two completed months are available. Two configured
months with 20,000 and 0 give an average of 10,000; an unconfigured earlier month is
excluded. The owner approved both the weekly and historical rules in this session.

The completed-month average and the chart are distinct: the chart also shows the
selected month while it is current, using actual spending up to today and the
category limit from the same live budget snapshot as the hero. A current limit
edit updates this point immediately; previous points keep their own limits. The
current point never enters the completed-month average or its month count. This
clarifies the owner's current-month limit regression report; it adds no forecast.

Average expense and completed-period averages round HALF_UP once to whole pesos.
The largest expense retains its note/date, with newest date/sequence breaking ties.
The most expensive weekday is based on total actual spending (Monday first on ties).
Previous-month comparison uses the same elapsed day number, clamped to the previous
month's end; past months compare whole months. A zero or unknown comparison baseline
has no percentage. Percentage display rounds HALF_UP to one decimal and conveys a
recorded difference, not a forecast or verdict.

All totals use exact widened arithmetic in domain. Insights and their category
budget share a transaction revision, generation, financial date and zone. Failed
reads stay failures. No schema, dependency, financial write or deletion policy changes.

**Amendment (2026-10-30, SELLO-135):** in the month in progress, the category's own
payments still to come are reserved as Recibo reserves the month's (ADR 0012 BG1–BG2):
the remainder behind the daily figure and the current week's allowance is the margin
`M = R − Q` of this category alone, read in the same transaction. With 550.000 left and
290.000 to come over 10 days, Al día is 26.000, not 55.000, and the week of 22–28 has
182.000. A negative margin gives 0. Recibo's category cells keep the recorded remainder.
