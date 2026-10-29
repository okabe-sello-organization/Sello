# Release build

How the customer APK is built, signed and checked. Sello ships as a private APK, not
through a store. Decisions: [D10](../decisions/0001-mvp-contract.md#d10--release-acceptance-and-review),
[ADR 0016](../decisions/0016-release-gate-signing-split.md). Performance:
[performance profile](../testing/performance-profile.md).

## Signing

The owner keeps the key (`~/.android/sello-release.jks`) and its passwords; neither is
in the repository, a command, a log or a report. The build reads four entries from the
owner's own `~/.gradle/gradle.properties`:

```properties
sello.signing.storeFile=/path/to/sello-release.jks
sello.signing.storePassword=…
sello.signing.keyAlias=…
sello.signing.keyPassword=…
```

With all four, `./gradlew :app:assembleRelease` writes a signed
`app/build/outputs/apk/release/app-release.apk`; without them, an unsigned
`app-release-unsigned.apk`. Nothing falls back to the debug key.

The certificate's SHA-256 digest is pinned in `scripts/quality/release-certificate.txt`:

```text
abf6ccf5b58de43626daea387d5c18c67981e42c302f8a1c7e16ad5cf0b4d952
```

The owner can compare it with `keytool -list -v -keystore ~/.android/sello-release.jks`.
The digest is public; it identifies the key without revealing it.

## Release gate (G3)

`scripts/check --release` on the owner's machine, with the `sello.signing.*` entries
and the owner's API-30 emulator as `ANDROID_SERIAL`, runs the host gate and every device
suite, then the release checks of ADR 0016: the licence list, the production signature and
artifact checks, and installing and opening the signed APK on the emulator. A passed
run leaves the release candidate in `build/release-candidate/`:

- `sello-<version>-<code>.apk`, the signed APK;
- `sello-<version>-<code>-mapping.txt`, R8's mapping file. Keep it with the APK: a stack
  trace from that APK can only be read with it;
- `sello-<version>-<code>.json`: package, versions, signature schemes, certificate,
  APK and mapping SHA-256.

CI runs the same G3 with a throwaway key, nightly and on request, and leaves the
production proof to the owner's run, which the release pull request reports.

## Libraries

[`dependencies.md`](dependencies.md) lists every library in the release build with its
reviewed licence, from `app/gradle.lockfile` and `scripts/quality/release-licenses.json`.
G3 fails when a library has no reviewed licence or the list is out of date. After an
approved dependency change, review the new libraries' licences, update the table and
run `python3 scripts/quality/licenses.py write`.

[`advisories.md`](advisories.md) is a dated, one-off check with network access
approved by the owner: published advisories (osv.dev) and published licences for the
same libraries. It is not a gate; repeat it after a dependency change.

Bundled fonts and icons are listed in
[design-system/THIRD_PARTY_NOTICES.md](../../design-system/THIRD_PARTY_NOTICES.md); their
licence texts ship in the APK.

**Open for the owner:** the app does not show the libraries' licences. Apache-2.0 asks
whoever redistributes a library to give recipients a copy of the licence, and BSD-3-Clause
(the protobuf code inside DataStore) to reproduce its notice; Sello's own APK is such a
redistribution. A notices screen or file is a product decision outside SELLO-035.

## Changing the key

Android installs an update only when it is signed by the same key. To move to a new key
while keeping each person's data, rotate with the old key still in hand
(`apksigner rotate`, then sign with the lineage; Android 9 and later, and Sello needs
11), and change the pinned digest. If the old key is lost, phones refuse the new APK as
an update: each person must uninstall, which deletes Sello's data, install it and
restore a backup. Keep a copy of the key and its passwords away from this computer.
