# SELLO-107 — Store investments, movements, values and the USD rate

Investments, movements and dated USD rates live in schema 6 (`Migration5To6`), written by `RoomInvestmentCommands` and read by `RoomInvestmentReads`; `InvestmentPolicy` (domain) replays histories, carries the value forward, limits retiros and builds the overview in pesos.
Rates are exact hundredths; amounts stay in their own currency; a deleted movement shares Deshacer.
Open: backups do not carry investments until SELLO-108, so a restore leaves none; no screen uses them until SELLO-109–111.
