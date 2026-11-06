# SELLO-200 — Celebrate the year that closed

- **Type:** Story
- **Priority:** P2
- **Status:** Backlog
- **Depends on:** SELLO-196
- **Gate:** G1

### Outcome
In the first days of January, Recibo celebrates a year with Sello and invites the person
to see how it went. The invitation takes the place of the month's.

### Acceptance criteria
- Built as the owner-approved [mock](attachments/patterns-mock.html).
- From 1 to 7 January, when the year that ended has expenses, Recibo shows the year card
  in the month invitation's place: the ink's band, a stamp with the year, a few paper
  chads, "¡Cerraste el año!" and "Mira cómo te fue en 2026", with ✕ to dismiss.
- On those days it replaces "Ver cómo cerraste diciembre". December's invitation doesn't
  appear, even after the year's card is opened or dismissed.
- Tapping it opens the year's big moment, built like a goal's ¡Lo lograste! but in the
  ink's band:
  - the 12 months land as stamps, faded for a month over its limit or without one;
  - "¡Un año con Sello!";
  - a stamped plate, AÑO CERRADO and the year, with counts only: expenses recorded,
    days with something recorded, months within their limit out of months with a limit,
    and goals reached that year;
  - paper chads, then Ver tu año (opens Tu año on that year) and Listo.
- The plate never shows money. It leaves out the goals line when there are none, and
  the months line when no month had a limit.
- With reduced motion it opens on the final screen, without chads. The big moment is
  presentation only and changes nothing stored.
- Once opened or dismissed, the card is gone for that year. Restarting the app doesn't
  bring it back; it keeps its state the way the month invitation does.
- Not shown on 8 January or later, when the ended year has no expenses, or when the
  month on screen is not the current one.
- JVM tests with a controlled clock and hand-counted facts: 1 and 7 January show it, 8
  January doesn't, 31 December shows only December's invitation (when it qualifies),
  dismissal holds after a restart, a year with no expenses, the plate's lines with no
  goals and with no limits. Screenshots of the card, the final big moment and its
  reduced-motion version.
