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
import contextlib
import fcntl
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
# The emulator step's `with:` in quality.yml, without its script; a test keeps them equal.
WORKFLOW_SETTINGS = {
    "api-level": API_LEVEL, "target": TARGET, "arch": ARCH, "cores": CORES,
    "force-avd-creation": "true", "emulator-options": " ".join(EMULATOR_OPTIONS),
    "disable-animations": "true",
}
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


def free_port(attached, bound, reserved=()):
    """The first console port no attached device, no listening socket and no emulator
    this script is still booting ([reserved] serials) uses."""
    for port in PORTS:
        serial = f"emulator-{port}"
        if serial not in attached and serial not in reserved and not bound(port) and not bound(port + 1):
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
    (home() / "started.json").write_text(json.dumps(started, indent=2) + "\n", encoding="utf-8")


@contextlib.contextmanager
def locked():
    """Holds the lock every reader and writer of the records takes, so that two agents
    starting at once neither pick the same port nor lose each other's record."""
    home().mkdir(parents=True, exist_ok=True)
    with (home() / "lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield


def forget(serial):
    with locked():
        started = records()
        started.pop(serial, None)
        save(started)


def start(out):
    if not (sdk() / "system-images" / f"android-{API_LEVEL}" / TARGET / ARCH).is_dir():
        raise Failure(f"install {PACKAGE} as in docs/development/setup.md")
    name = f"sello_{secrets.token_hex(4)}"
    avd_home = home() / name
    avd_home.mkdir(parents=True)
    environment = {**os.environ, "ANDROID_AVD_HOME": str(avd_home)}
    process = serial = None
    try:
        created = subprocess.run([tool("cmdline-tools", "latest", "bin", "avdmanager"), "create", "avd",
                                  "--force", "-n", name, "--package", PACKAGE],
                                 input="no\n", capture_output=True, text=True, env=environment)
        if created.returncode != 0:
            raise Failure(f"avdmanager failed:\n{created.stdout}{created.stderr}")
        config, lines = avd_config(avd_home, name)
        with config.open("a", encoding="utf-8") as appended:
            appended.write(lines)

        # From choosing the port until the record holds it, nobody else may choose.
        with locked():
            started = records()
            port = free_port(check.attached_devices(check.ROOT), port_bound, started)
            serial = f"emulator-{port}"
            log = (avd_home / "emulator.log").open("w")
            process = subprocess.Popen([tool("emulator", "emulator"), "-port", str(port), "-avd", name,
                                        *EMULATOR_OPTIONS],
                                       stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL,
                                       env=environment, start_new_session=True)
            started[serial] = {"avd": name, "home": str(avd_home), "pid": process.pid}
            save(started)
        print(f"Booting {name} on {serial} (log: {avd_home / 'emulator.log'})", file=sys.stderr)
        wait_for_boot(serial, name, process)
    except BaseException:
        # Only what this run made goes: the process it spawned, its record and its AVD.
        # The serial is never asked to quit: another emulator may answer on that port.
        if process is not None:
            if process.poll() is None:
                process.kill()
            process.wait()
        if serial is not None:
            forget(serial)
        shutil.rmtree(avd_home, ignore_errors=True)
        raise
    adb(serial, "shell", "input", "keyevent", "82")
    for scale in ANIMATION_SCALES:
        adb(serial, "shell", "settings", "put", "global", scale, "0.0")
    print(serial, file=out)


def wait_for_boot(serial, name, process):
    deadline = time.monotonic() + BOOT_SECONDS
    while True:
        if process.poll() is not None:
            raise Failure(f"the emulator exited with {process.returncode}; see its log")
        try:
            if adb(serial, "shell", "getprop", "sys.boot_completed", timeout=10).stdout.strip() == "1":
                break
        except subprocess.TimeoutExpired:
            pass
        if time.monotonic() > deadline:
            raise Failure(f"{serial} did not finish booting in {BOOT_SECONDS} s")
        time.sleep(2)
    if adb_avd_name(serial) != name:
        raise Failure(f"{serial} answers as another emulator, not {name}")


def adb_avd_name(serial):
    """The AVD the emulator on [serial] says it runs, or None."""
    try:
        answer = adb(serial, "emu", "avd", "name", timeout=10)
    except subprocess.TimeoutExpired:
        return None
    lines = answer.stdout.split()
    return lines[0] if answer.returncode == 0 and lines else None


def runs_avd(pid, name):
    """Whether process [pid] is an emulator started with `-avd [name]`."""
    try:
        arguments = Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
    except OSError:
        return False
    wanted = name.encode()
    return any(a == b"-avd" and b == wanted for a, b in zip(arguments, arguments[1:]))


def ours(serial, record):
    """Both the emulator on [serial] and the recorded process are still this record's AVD:
    after a crash the port or the process ID may belong to someone else."""
    return runs_avd(record["pid"], record["avd"]) and adb_avd_name(serial) == record["avd"]


def stop(serial, out):
    with locked():
        record = records().get(serial)
    if record is None:
        raise Failure(f"{serial} was not started by scripts/emulator; it is left alone")
    if ours(serial, record):
        try:
            adb(serial, "emu", "kill", timeout=STOP_SECONDS)
        except subprocess.TimeoutExpired:
            pass
        if not wait_for_exit(record["pid"], STOP_SECONDS) and runs_avd(record["pid"], record["avd"]):
            with_signal(record["pid"], signal.SIGKILL)
            wait_for_exit(record["pid"], STOP_SECONDS)
        what = f"Stopped {serial} and deleted {record['avd']}"
    else:
        what = f"{record['avd']} no longer runs on {serial}; nothing was stopped, its AVD was deleted"
    forget(serial)
    shutil.rmtree(record["home"], ignore_errors=True)
    print(what, file=out)


def wait_for_exit(pid, seconds):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if not alive(pid):
            return True
        time.sleep(1)
    return not alive(pid)


def alive(pid):
    status = Path(f"/proc/{pid}/status")
    try:
        return "\nState:\tZ" not in status.read_text(encoding="utf-8")
    except OSError:
        return False


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
            with locked():
                started = records()
            for serial, record in sorted(started.items()):
                state = "running" if runs_avd(record["pid"], record["avd"]) else "gone"
                print(f"{serial}\t{record['avd']}\t{state}", file=out)
    except (Failure, check.Refusal) as failure:
        print(f"FAILED: {failure}", file=sys.stderr)
        return 1
    return 0
