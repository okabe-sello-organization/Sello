# SELLO-200 — Be invited to see how the year closed

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-196
- **Gate:** G1

### Outcome
In the first days of January, Recibo invites the person to see how the year that ended
went, the way it already does for a month.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- From 1 to 7 January, when the year that ended has expenses, Recibo shows "Ver cómo
  cerraste 2026" in the existing invitation's place and style. Tapping it opens Tu año
  on that year.
- On those days it replaces "Ver cómo cerraste diciembre". December's invitation doesn't
  appear, even after the year's is opened or dismissed.
- Once opened or dismissed, it is gone for that year. Restarting the app or a backup
  restore doesn't bring it back; the invitation keeps its state the way the month's does.
- Not shown on 8 January or later, when the ended year has no expenses, or when the
  month on screen is not the current one.
- JVM tests with a controlled clock: 1 and 7 January show it, 8 January doesn't, 31
  December shows only December's month invitation (when it qualifies), dismissal holds
  after a restart, a year with no expenses. A screenshot of Recibo with the invitation.
