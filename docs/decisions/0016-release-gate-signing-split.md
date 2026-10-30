# 0016 — Release gate: the owner's signing proof locally, the rest also on CI

**Owner:** project owner (user) · **Date:** 2026-10-23
**Status:** owner-approved (option 1 of three, 2026-10-23); delivered by SELLO-035.
Supersedes the G3 refusal in ADRs 0006, 0009 and 0014. Commands updated for
[ADR 0021](0021-ci-is-the-only-proof.md) on 2026-10-29.

## Context

G3 must prove that the customer APK is signed with the owner's key, while the key stays
with the owner: CI signing and customer-distribution signing are validated separately
([D10](0001-mvp-contract.md#d10--release-acceptance-and-review), SELLO-035). CI verifies
every active or newly completed ticket at the ticket's own gate, and a lower gate may
not stand in. So CI cannot run G3 the way it runs G2, and without a decision a G3
ticket could never pass its pull request.

## Alternatives

1. **Split (chosen).** The owner's machine runs all of G3 with the key; CI runs G3 with
   a throwaway key and leaves the production proof to the owner's run.
2. Production key in GitHub secrets, so CI runs all of G3. Rejected: a second copy of
   the key, outside the owner's custody.
3. No CI verification of G3. Rejected: merging needs passing required checks.

## Decision

G3 is the host gate and every device suite, then the release checks
(`scripts/quality/gates.py`, `release_checks`). The owner runs it with
`scripts/check --release`; CI runs `scripts/check --host --ci-release` and every
device group on the nightly and manual runs:

| Check | Owner's machine | CI |
| --- | --- | --- |
| `release-licenses`: each locked release library has a reviewed licence and `docs/release/dependencies.md` is current | yes | yes |
| `release-production`: the APK verifies, is not debuggable, is `com.software.sello`, has R8's mapping file, and is signed by exactly the certificate pinned in `scripts/quality/release-certificate.txt` | yes | — |
| `release-ephemeral`: the same artifact checks after signing a copy with a key made for that run | — | yes |
| `release-launch`: the production APK installs on the API-30 emulator named by `ANDROID_SERIAL` and starts; a physical device is refused | yes | — |

The build signs the release APK only from the owner's `sello.signing.*` Gradle
properties; without them it stays unsigned and `release-production` fails: G3 is
blocked, never satisfied by a debug or throwaway key. CI's run never completes a
release. Since [ADR 0021](0021-ci-is-the-only-proof.md) the production-signing proof is
the owner's local `scripts/check --release` result, stated in the release pull request;
nothing in CI checks it. The owner merges releases (AGENTS.md, "Who merges").

## Consequences

- The owner's G3 runs only for release work; pull requests keep the plan's gate, and
  CI's G3 runs nightly on `main`.
- CI's G3 host part also needs `keytool` and build-tools' `apksigner`, `zipalign` and
  `aapt2`, already present with the JDK and Android SDK.
- Changing the key is a one-line change to the pinned digest. Users keep their data
  only with APK key rotation (`apksigner rotate`), which needs the old key: losing it
  forces uninstall and restore from a backup. See [release](../release/README.md).
- Performance runs and configuration sweeps stay ticket evidence, not G3 checks.

## Verification

`test_gates`, `test_check`, `test_selection`, `test_workflow`, `test_release` and
`test_licenses` cover both check lists, the order of the owner's run, CI's G3 plan and
device jobs, certificate mismatch, refusal without a key and refusal of a physical
device. SELLO-035 recorded the first local G3 run.
