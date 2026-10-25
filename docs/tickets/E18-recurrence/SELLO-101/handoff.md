# SELLO-101 — Show reservations on Recibo

`ReservationPolicy` (domain) reserves the month's unrecorded payments; `MonthlyBudgetPolicy` takes the margin for the headline and daily figure, and `RoomMonthlySnapshots` reads the schedules in the same transaction.
Recibo (`ReciboRenderModel`, `ReciboScreen`) shows Falta para tus pagos, Sin margen and Revisar, the shortfall sentence and the reserved footnote; the catalog's answer states follow v2.
The footnote opens Pagos previstos (`feature/recibo/UpcomingPayments.kt`); each payment opens its schedule.
Next: the sandbox reservation checks in SELLO-104 part 2.
