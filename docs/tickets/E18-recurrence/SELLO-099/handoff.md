# SELLO-099 — Carry schedules through backup, restore and erase

Backups are version 3 (`docs/data/backup-format.md`): `financial.schedules` and each entry's `origin`; versions 1 and 2 read as having neither.
Restore keeps each schedule's checkpoint, so catch-up goes on after what the backup holds; a file with an origin past its checkpoint, or a running payment into an archived category, is refused.
Backup, restore and erase count "pagos e ingresos fijos"; resuming into an archived category is refused (`CategoryArchived`).
Next: SELLO-114 (design pass) before SELLO-100, 101 and 103; SELLO-102 and 104 can start.
