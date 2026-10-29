"""scripts/emulator: an API-30 emulator of your own, made the way CI makes its own.

    scripts/emulator start        create and cold-boot a new AVD; prints its serial
    scripts/emulator stop SERIAL  shut that emulator down and delete its AVD
    scripts/emulator list         the emulators this script started and still knows

The AVD matches .github/workflows/quality.yml's reactivecircus/android-emulator-runner
step: the pinned system image, avdmanager's default hardware (320x640 at mdpi, no
hardware keyboard), two cores, the same emulator flags, the screen unlocked and the
three animation scales at zero. Each AVD gets a unique name, its own ANDROID_AVD_HOME
and a port no other emulator uses, so it never touches someone else's device.
"""
import argparse
import json
import os
import secrets
import shutil
import signal
import socket
import subprocess
import sys
import time
from pathlib import Path

import check

API_LEVEL, TARGET, ARCH, CORES = "30", "google_apis", "x86_64", "2"
PACKAGE = f"system-images;android-{API_LEVEL};{TARGET};{ARCH}"
EMULATOR_OPTIONS = ("-no-window", "-gpu", "swiftshader_indirect", "-no-snapshot", "-noaudio", "-no-boot-anim")
ANIMATION_SCALES = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
PORTS = range(5554, 5684, 2)  # the console ports adb scans; adb uses each one plus one
BOOT_SECONDS = 600
STOP_SECONDS = 60


class Failure(Exception):
    pass


def home():
    """Where this script keeps its AVDs and the record of what it started."""
    base = os.environ.get("XDG_CACHE_HOME") or str(Path.home() / ".cache")
    return Path(base) / "sello-emulators"


def parse(argv):
    parser = argparse.ArgumentParser(prog="scripts/emulator", description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("start", help="create and cold-boot a CI-identical AVD; prints its serial")
    stop = commands.add_parser("stop", help="shut down an emulator this script started and delete its AVD")
    stop.add_argument("serial", help="the serial start printed, such as emulator-5580")
    commands.add_parser("list", help="emulators this script started")
    arguments = parser.parse_args(argv)
    if arguments.command == "stop" and port_of(arguments.serial) is None:
        parser.error(f"not an emulator serial: {arguments.serial}")
    return arguments


def port_of(serial):
    prefix = "emulator-"
    if not serial.startswith(prefix) or not serial[len(prefix):].isdigit():
        return None
    port = int(serial[len(prefix):])
    return port if port in PORTS else None


def free_port(attached, bound):
    """The first console port no attached device and no listening socket uses."""
    for port in PORTS:
        if f"emulator-{port}" not in attached and not bound(port) and not bound(port + 1):
            return port
    raise Failure("no free emulator port between 5554 and 5682")


def port_bound(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        try:
            probe.bind(("127.0.0.1", port))
        except OSError:
            return True
    return False


def avd_config(avd_home, name):
    """The hardware lines the CI action appends to avdmanager's config."""
    return Path(avd_home) / f"{name}.avd" / "config.ini", f"hw.cpu.ncore={CORES}\n"


def sdk():
    found = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or check.sdk_directory(check.ROOT)
    if not found:
        raise Failure("set ANDROID_HOME as in docs/development/setup.md")
    return Path(found)


def tool(*parts):
    path = sdk().joinpath(*parts)
    if not path.is_file():
        raise Failure(f"{path} is missing; install it as in docs/development/setup.md")
    return str(path)


def adb(serial, *arguments, timeout=30):
    return subprocess.run([check.adb_path(check.ROOT), "-s", serial, *arguments],
                          capture_output=True, text=True, timeout=timeout)


def records():
    path = home() / "started.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.is_file() else {}


def save(started):
    home().mkdir(parents=True, exist_ok=True)
    (home() / "started.json").write_text(json.dumps(started, indent=2) + "\n", encoding="utf-8")


def start(out):
    if not (sdk() / "system-images" / f"android-{API_LEVEL}" / TARGET / ARCH).is_dir():
        raise Failure(f"install {PACKAGE} as in docs/development/setup.md")
    name = f"sello_{secrets.token_hex(4)}"
    avd_home = home() / name
    avd_home.mkdir(parents=True)
    environment = {**os.environ, "ANDROID_AVD_HOME": str(avd_home)}
    created = subprocess.run([tool("cmdline-tools", "latest", "bin", "avdmanager"), "create", "avd", "--force",
                              "-n", name, "--package", PACKAGE],
                             input="no\n", capture_output=True, text=True, env=environment)
    if created.returncode != 0:
        shutil.rmtree(avd_home)
        raise Failure(f"avdmanager failed:\n{created.stdout}{created.stderr}")
    config, lines = avd_config(avd_home, name)
    with config.open("a", encoding="utf-8") as appended:
        appended.write(lines)

    port = free_port(check.attached_devices(check.ROOT), port_bound)
    serial = f"emulator-{port}"
    log = (avd_home / "emulator.log").open("w")
    process = subprocess.Popen([tool("emulator", "emulator"), "-port", str(port), "-avd", name, *EMULATOR_OPTIONS],
                               stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                               env=environment, start_new_session=True)
    started = records()
    started[serial] = {"avd": name, "home": str(avd_home), "pid": process.pid}
    save(started)
    print(f"Booting {name} on {serial} (log: {avd_home / 'emulator.log'})", file=sys.stderr)

    deadline = time.monotonic() + BOOT_SECONDS
    while True:
        if process.poll() is not None:
            stop(serial, out=sys.stderr)
            raise Failure(f"the emulator exited with {process.returncode}; see the log above")
        try:
            if adb(serial, "shell", "getprop", "sys.boot_completed", timeout=10).stdout.strip() == "1":
                break
        except subprocess.TimeoutExpired:
            pass
        if time.monotonic() > deadline:
            stop(serial, out=sys.stderr)
            raise Failure(f"{serial} did not finish booting in {BOOT_SECONDS} s")
        time.sleep(2)
    adb(serial, "shell", "input", "keyevent", "82")
    for scale in ANIMATION_SCALES:
        adb(serial, "shell", "settings", "put", "global", scale, "0.0")
    print(serial, file=out)


def stop(serial, out):
    started = records()
    record = started.get(serial)
    if record is None:
        raise Failure(f"{serial} was not started by scripts/emulator; it is left alone")
    try:
        adb(serial, "emu", "kill", timeout=STOP_SECONDS)
    except subprocess.TimeoutExpired:
        pass
    if not wait_for_exit(record["pid"], STOP_SECONDS):
        with_signal(record["pid"], signal.SIGKILL)
        wait_for_exit(record["pid"], STOP_SECONDS)
    shutil.rmtree(record["home"], ignore_errors=True)
    del started[serial]
    save(started)
    print(f"Stopped {serial} and deleted {record['avd']}", file=out)


def wait_for_exit(pid, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if not alive(pid):
            return True
        time.sleep(1)
    return not alive(pid)


def alive(pid):
    try:
        os.waitpid(pid, os.WNOHANG)
    except ChildProcessError:
        pass
    status = Path(f"/proc/{pid}/status")
    if not status.is_file():
        return False
    return "\nState:\tZ" not in status.read_text(encoding="utf-8")


def with_signal(pid, number):
    try:
        os.kill(pid, number)
    except ProcessLookupError:
        pass


def main(argv=None, out=sys.stdout):
    arguments = parse(sys.argv[1:] if argv is None else argv)
    try:
        if arguments.command == "start":
            start(out)
        elif arguments.command == "stop":
            stop(arguments.serial, out)
        else:
            for serial, record in sorted(records().items()):
                print(f"{serial}\t{record['avd']}\t{'running' if alive(record['pid']) else 'gone'}", file=out)
    except (Failure, check.Refusal) as failure:
        print(f"FAILED: {failure}", file=sys.stderr)
        return 1
    return 0
