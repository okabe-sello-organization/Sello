# Release candidate checklist

What the next candidate must pass before the owner's go/no-go
([D10](../decisions/0001-mvp-contract.md#d10--release-acceptance-and-review)). Epic
[E30](../tickets/E30-release-candidate/README.md). Distribution stays a private APK;
nothing here publishes, uploads or tags anything. The October MVP is a separate record:
[MVP checklist](mvp-checklist.md).

## Candidate

The version stays `0.1.0`, code 1 (owner, 2026-11-04), so the file is named
`sello-0.1.0-1.apk` like the MVP's. Tell them apart by SHA-256.

| | |
| --- | --- |
| Source commit | Pending |
| APK SHA-256 and size | Pending, from the G3 facts file |
| R8 mapping SHA-256 | Pending, from the G3 facts file |
| Signing | Pending |

## Checks

| Check | Who | Status |
| --- | --- | --- |
| Release check: `scripts/check --release` on the final `main` ([release](README.md#release-gate-g3)) | SELLO-175 | Pending |
| Advisory check, run again with the network ([advisories](advisories.md)) | SELLO-175 | Pending |
| Walk of the shrunk build on a new API 37 emulator, against hand-worked figures | SELLO-175 | Pending |
| Walk of the signed candidate on the owner's phone | Owner | Pending |

## Decision

| | |
| --- | --- |
| Product go/no-go | Pending, the owner's |
