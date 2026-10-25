# SELLO-103 — Tell what Sello recorded on opening

`ScheduleRecordReads` (domain port; `RoomScheduleRecordReads` in data) reads every entry with an origin created after the last Listo, which `CatchUpNoticePreferences` keeps on the audit clock.
`feature/recurrence/CatchUpNotice.kt` holds the ViewModel, Recibo's bar "Sello anotó … · Ver" and *Lo que Sello anotó* (`CatchUpRoute`), each row opening its expense or income edit; Listo on the dock dismisses it.
Nothing new is stored but the moment of Listo, so a deleted entry simply leaves the list.
Next: SELLO-104 part 2 closes E18.
