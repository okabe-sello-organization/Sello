"""G3 release checks on the release APK that `:app:assembleRelease` built.

production  The owner's machine. The APK must be signed by the certificate pinned in
            release-certificate.txt; its facts, the APK and R8's mapping file are kept
            together in build/release-candidate/.
ephemeral   CI, which holds no customer key. The unsigned APK is signed with a key made
            for this run only, to prove it can be signed and verified. This is not
            customer signing; the production proof is the owner's local G3 run.
launch      The owner's machine. Installs the production candidate on the emulator named
            by ANDROID_SERIAL, never a physical device, opens it and checks that it starts.

See docs/release/README.md and ADR 0016.
"""
import argparse
import hashlib
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

import check

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = "com.software.sello"
ACTIVITY = f"{PACKAGE}/{PACKAGE}.MainActivity"
APK_DIR = Path("app/build/outputs/apk/release")
SIGNED = APK_DIR / "app-release.apk"
UNSIGNED = APK_DIR / "app-release-unsigned.apk"
MAPPING = Path("app/build/outputs/mapping/release/mapping.txt")
CANDIDATE = Path("build/release-candidate")
PINNED = Path(__file__).with_name("release-certificate.txt")
# What the app shows once it has opened its data: a month's categories, or the first run.
STARTED = ("Tus categorías", "Dale forma a tu mes")


class ReleaseError(Exception):
    pass


def pinned_certificate(path=PINNED):
    lines = [line.strip().lower() for line in Path(path).read_text(encoding="utf-8").splitlines()
             if line.strip() and not line.startswith("#")]
    if len(lines) != 1 or not re.fullmatch(r"[0-9a-f]{64}", lines[0]):
        raise ReleaseError(f"{path} must hold exactly one SHA-256 certificate digest")
    return lines[0]


def signers(output):
    """`apksigner verify --verbose --print-certs`: whether it verifies, its schemes and certificates."""
    schemes = re.findall(r"^Verified using (v[\d.]+) scheme \([^)]*\): true$", output, re.MULTILINE)
    # apksigner writes the signer as "Signer #1", "Signer (minSdkVersion=…)" or, from 37,
    # "V3.0 Signer:", and may repeat one certificate per scheme or SDK range.
    digests = re.findall(r"^(?!Source Stamp)\S*[ ]?Signer(?: #\d+| \([^)]*\))?:? certificate SHA-256 digest: "
                         r"([0-9a-f]{64})$", output, re.MULTILINE)
    return {"verifies": re.search(r"^Verifies$", output, re.MULTILINE) is not None,
            "schemes": schemes, "certificates": sorted(set(digests))}


def badging(output):
    """`aapt2 dump badging`: identity, versions and whether the app is debuggable."""
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", output, re.MULTILINE)
    if package is None:
        raise ReleaseError("aapt2 printed no package line")
    def sdk(label):
        match = re.search(rf"^{label}:'(\d+)'", output, re.MULTILINE)
        return int(match.group(1)) if match else None
    return {"package": package.group(1), "version_code": package.group(2), "version_name": package.group(3),
            "min_sdk": sdk("minSdkVersion") or sdk("sdkVersion"), "target_sdk": sdk("targetSdkVersion"),
            "debuggable": re.search(r"^application-debuggable$", output, re.MULTILINE) is not None}


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def build_tools(root):
    sdk = check.sdk_directory(root)
    if sdk is None:
        raise ReleaseError("Android SDK not found; set ANDROID_HOME")
    candidates = sorted((sdk / "build-tools").glob("*/apksigner"),
                        key=lambda path: [int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", path.parent.name)])
    if not candidates:
        raise ReleaseError("no build-tools with apksigner in the Android SDK")
    return candidates[-1].parent


def run(argv, **options):
    result = subprocess.run([str(part) for part in argv], capture_output=True, text=True, **options)
    if result.returncode:
        raise ReleaseError(f"{Path(str(argv[0])).name} failed: {(result.stderr or result.stdout).strip()[-400:]}")
    return result.stdout


def inspect(apk, tools):
    facts = badging(run([tools / "aapt2", "dump", "badging", apk]))
    verified = run([tools / "apksigner", "verify", "--verbose", "--print-certs", apk])
    facts["signature"] = signers(verified)
    facts["build_tools"] = tools.name
    facts["apk_sha256"] = sha256(apk)
    facts["apk_bytes"] = Path(apk).stat().st_size
    problems = []
    if facts["package"] != PACKAGE:
        problems.append(f"package is {facts['package']}, not {PACKAGE}")
    if facts["debuggable"]:
        problems.append("the release APK is debuggable")
    if not facts["signature"]["verifies"] or len(facts["signature"]["certificates"]) != 1:
        problems.append("the APK does not verify with exactly one signer certificate; apksigner "
                        f"{tools.name} printed:\n{verified.strip()[-1500:]}")
    if not facts["signature"]["schemes"]:
        problems.append("no APK signature scheme verified")
    return facts, problems


def mapping_facts(root):
    mapping = Path(root) / MAPPING
    if not mapping.is_file():
        raise ReleaseError(f"no R8 mapping file at {MAPPING}; the release build must be shrunk")
    return {"mapping_sha256": sha256(mapping)}


def production(root):
    apk = Path(root) / SIGNED
    if not apk.is_file():
        raise ReleaseError(
            "no signed release APK: G3 needs the owner's key in ~/.gradle/gradle.properties "
            "(sello.signing.*). Without it G3 is blocked; a debug or throwaway key cannot stand in")
    tools = build_tools(root)
    facts, problems = inspect(apk, tools)
    facts.update(mapping_facts(root))
    expected = pinned_certificate()
    if facts["signature"]["certificates"] != [expected]:
        problems.append(f"signed by {facts['signature']['certificates']}, not the pinned certificate {expected}")
    if problems:
        raise ReleaseError("; ".join(problems))
    facts["signing"] = "production"
    candidate = Path(root) / CANDIDATE
    shutil.rmtree(candidate, ignore_errors=True)
    candidate.mkdir(parents=True)
    name = f"sello-{facts['version_name']}-{facts['version_code']}"
    shutil.copy2(apk, candidate / f"{name}.apk")
    shutil.copy2(Path(root) / MAPPING, candidate / f"{name}-mapping.txt")
    (candidate / f"{name}.json").write_text(json.dumps(facts, indent=2) + "\n", encoding="utf-8")
    return facts


def ephemeral(root):
    apk = Path(root) / UNSIGNED
    if not apk.is_file():
        raise ReleaseError(f"no unsigned release APK at {UNSIGNED}")
    tools = build_tools(root)
    with tempfile.TemporaryDirectory(prefix="sello-ephemeral-") as scratch:
        scratch = Path(scratch)
        password = secrets.token_urlsafe(24)
        keystore = scratch / "ephemeral.p12"
        run(["keytool", "-genkeypair", "-keystore", keystore, "-storetype", "PKCS12", "-alias", "ephemeral",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "1", "-dname", "CN=Sello CI ephemeral",
             "-storepass:env", "SELLO_EPHEMERAL", "-keypass:env", "SELLO_EPHEMERAL"],
            env=dict(os.environ, SELLO_EPHEMERAL=password))
        aligned, signed = scratch / "aligned.apk", scratch / "signed.apk"
        run([tools / "zipalign", "-f", "-p", "4", apk, aligned])
        run([tools / "apksigner", "sign", "--ks", keystore, "--ks-pass", "env:SELLO_EPHEMERAL",
             "--out", signed, aligned], env=dict(os.environ, SELLO_EPHEMERAL=password))
        facts, problems = inspect(signed, tools)
    facts.update(mapping_facts(root))
    if problems:
        raise ReleaseError("; ".join(problems))
    facts["signing"] = "ephemeral (CI only; not customer signing)"
    return facts


def is_emulator(adb, serial):
    return serial.startswith("emulator-") and adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() == "1"


def launch(root, serial=None, timeout=30.0):
    serial = serial or os.environ.get("ANDROID_SERIAL")
    if not serial:
        raise ReleaseError("name the emulator with ANDROID_SERIAL")
    sdk = check.sdk_directory(root)
    adb_path = shutil.which("adb") or (str(sdk / "platform-tools/adb") if sdk else "adb")

    def adb(target, *args, check=True):
        result = subprocess.run([adb_path, "-s", target, *args], capture_output=True, text=True)
        if check and result.returncode:
            raise ReleaseError(f"adb {' '.join(args)}: {(result.stderr or result.stdout).strip()[-300:]}")
        return result.stdout + result.stderr

    if not is_emulator(adb, serial):
        raise ReleaseError(f"{serial} is not an emulator; the launch check never installs on a physical device")
    apks = sorted((Path(root) / CANDIDATE).glob("*.apk"))
    if len(apks) != 1:
        raise ReleaseError("no production candidate; run the production check first")
    installed = adb(serial, "install", "-r", "--no-incremental", apks[0], check=False)
    if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in installed:
        # Another key signed the copy already there. On an emulator it is replaced.
        adb(serial, "uninstall", PACKAGE)
        installed = adb(serial, "install", "--no-incremental", apks[0], check=False)
    if "Success" not in installed:
        raise ReleaseError(f"install failed: {installed.strip()[-300:]}")
    adb(serial, "logcat", "-b", "crash", "-c")
    started = adb(serial, "shell", "am", "start-activity", "-W", "-S", "-n", ACTIVITY)
    if "Status: ok" not in started:
        raise ReleaseError(f"the app did not start: {started.strip()[-300:]}")
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        # A dump left by an earlier run must not answer for this one.
        adb(serial, "shell", "rm", "-f", "/sdcard/sello-release.xml", check=False)
        adb(serial, "shell", "uiautomator", "dump", "/sdcard/sello-release.xml", check=False)
        screen = adb(serial, "shell", "cat", "/sdcard/sello-release.xml", check=False)
        shown = [label for label in STARTED if label in screen]
        if shown:
            break
        time.sleep(1.0)
    else:
        raise ReleaseError(f"neither {' nor '.join(STARTED)} appeared within {timeout:.0f} s")
    if not adb(serial, "shell", "pidof", PACKAGE, check=False).strip():
        raise ReleaseError("the app is not running after it started")
    crashes = adb(serial, "logcat", "-b", "crash", "-d", check=False)
    if PACKAGE in crashes:
        raise ReleaseError("the app logged a crash")
    return {"device": serial, "apk": apks[0].name, "shown": shown[0]}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=("production", "ephemeral", "launch"))
    args = parser.parse_args(argv)
    try:
        facts = {"production": production, "ephemeral": ephemeral, "launch": launch}[args.mode](ROOT)
    except (ReleaseError, OSError) as error:
        print(f"FAILED: {error}", file=sys.stderr)
        return 1
    print(json.dumps(facts, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
