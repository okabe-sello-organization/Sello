# SELLO-108 — Carry investments through backup, restore and erase

Backup version 4 (`BackupCodec`, `BackupWire`, `BackupValidation`, `RoomRestores`) carries investments, their movements and USD rates as exact hundredths; versions 1–3 still restore with none, and a bad file changes nothing.
The restore and erase previews count investments; `docs/data/backup-format.md` describes version 4.
Open: undo copies of deleted movements do not travel (as goals); the privacy doc's database row does not list schedules or investments yet.
