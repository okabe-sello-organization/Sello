# SELLO-104 — Cover recurrence in the sandbox

Ten *Programados* scenarios run schedules through the fenced commands and the app's own `CatchUpRunner` after financial-clock jumps (`Scenarios.kt`, `ScenarioRunner.kt`); checks read entries by origin, each schedule's checkpoint and pause.
`pagos-previstos@1` adds the figures `separado`, `margen` and `por día` and checks RE25–RE29 and RE26 on the month's real snapshot; the notice is checked by SELLO-103's `CatchUpNoticeJourneyTest` on `semana-fuera@1`.
Open: no scenario checks Plan del mes (RE35–RE37); the notice keeps "seen up to" in DataStore, so restoring a backup on a fresh install lists every automatic entry until Listo.
