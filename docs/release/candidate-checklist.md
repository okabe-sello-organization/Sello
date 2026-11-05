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
| Source commit | `a20ae90f` (`main` on 2026-11-05) |
| APK SHA-256 and size | Pending, from the facts file of the owner's G3 run |
| R8 mapping SHA-256 | Pending, from the facts file of the owner's G3 run |
| Signing | Pending, the owner's key in the owner's G3 run |

## Checks

| Check | Who | Status |
| --- | --- | --- |
| Release check: `scripts/check --release` on the final `main` ([release](README.md#release-gate-g3)) | SELLO-175 | Passed up to the owner's key on 2026-11-05, on a new API 30 AVD from `scripts/emulator start` (`sello_1cae5906`). The gate run passed the fast checks, the host gate (3,245 tests), every device suite (data 668, design-system 3, app 114; none failed or skipped) and the licence list (143 libraries); its `release-production` step then failed because `java` was not on PATH for apksigner, so launch did not run in it. Run alone with the JDK on PATH, `release-production` passed the package, not-debuggable, single-signer and signature-scheme checks and refused the throwaway certificate, as it must. `release-launch`, run alone on the same AVD with the throwaway-signed APK placed by hand in `build/release-candidate/` (deleted afterwards), installed and opened it |
| Release check with the owner's key: the production signature and the launch | Owner | Pending |
| Advisory check, run again with the network ([advisories](advisories.md)) | SELLO-175 | Passed on 2026-11-05: 143 libraries, no advisory, no licence difference |
| Walk of the shrunk build on a new API 37 emulator, against hand-worked figures | SELLO-175 | Passed on 2026-11-05, nothing broke ([walk](../testing/performance-profile.md#release-candidate-walk-on-api-37-sello-175)) |
| Walk of the signed candidate on the owner's phone | Owner | Pending |

## Decision

| | |
| --- | --- |
| Product go/no-go | Pending, the owner's |
