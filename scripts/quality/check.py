"""scripts/check: quick feedback while working, and the exact gates the Quality workflow runs.

    scripts/check MODULE... [--tests PATTERN] [--lint]   ktlint and unit tests of some modules
    scripts/check --fast                                 G0: the Python/Git checks
    scripts/check --host [PART]                          G1: --fast, ktlint, then the Gradle host gate (or one CI part)
    scripts/check --device [GROUP...] [--scope SCOPE]    device suites on the emulator in ANDROID_SERIAL
    scripts/check --release                              G3 with the owner's production signing
    scripts/check --plan BASE [--event EVENT]            what a hosted run would verify (JSON)

Cheap stages run first and a failed stage stops the run. Failing tests are listed
in the log and, on GitHub, in the job summary. Nothing written here is committed.
See docs/development/quality-gates.md.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

import gates
import junit
import selection

ROOT = Path(__file__).resolve().parents[2]
DEVICE_QUERY_SECONDS = 20


class Refusal(Exception):
    """The run cannot start; nothing was executed."""


def sdk_directory(root):
    for variable in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(variable):
            return Path(os.environ[variable])
    properties = Path(root) / "local.properties"
    if properties.is_file():
        match = re.search(r"^sdk\.dir=(.+)$", properties.read_text(encoding="utf-8"), re.MULTILINE)
        if match:
            return Path(match.group(1).strip())
    return None


def adb_path(root):
    sdk = sdk_directory(root)
    if shutil.which("adb"):
        return shutil.which("adb")
    if sdk is not None and (sdk / "platform-tools/adb").is_file():
        return str(sdk / "platform-tools/adb")
    raise Refusal("adb not found; set ANDROID_HOME as in docs/development/setup.md")


def attached_devices(root):
    listing = subprocess.run([adb_path(root), "devices"], capture_output=True, text=True).stdout
    return [line.split()[0] for line in listing.splitlines()[1:] if line.split()[1:2] == ["device"]]


def device_api(root, serial):
    # Only the selected device is asked, with a timeout: a busy one must not hold the run.
    try:
        return subprocess.run([adb_path(root), "-s", serial, "shell", "getprop", "ro.build.version.sdk"],
                              capture_output=True, text=True, timeout=DEVICE_QUERY_SECONDS).stdout.strip()
    except subprocess.TimeoutExpired:
        return ""


def select_device(root, environ, devices=attached_devices, api=device_api):
    """The one API-30 device to test on: ANDROID_SERIAL, or on CI the only one attached."""
    serial = environ.get("ANDROID_SERIAL")
    if not serial:
        attached = devices(root)
        if environ.get("GITHUB_ACTIONS") != "true" or len(attached) != 1:
            raise Refusal("name your own emulator with ANDROID_SERIAL (docs/development/setup.md); "
                          "assembling test APKs is not a substitute")
        serial = attached[0]
    elif serial not in devices(root):
        raise Refusal(f"{serial} is not connected")
    if api(root, serial) != "30":
        raise Refusal(f"{serial} is not an API-30 device, or did not answer")
    return serial


def included_modules(root):
    settings = (Path(root) / "settings.gradle.kts").read_text(encoding="utf-8")
    return re.findall(r'include\(":([\w-]+)"\)', settings)


def default_execute(argv, root, environment):
    sys.stdout.flush()
    return subprocess.run(argv, cwd=root, env=environment).returncode


class Check:
    """Runs stages in order, prints what fails and writes the GitHub job summary."""

    def __init__(self, root=ROOT, execute=default_execute, out=sys.stdout, environ=None):
        self.root, self.execute, self.out = Path(root), execute, out
        self.environ = dict(os.environ if environ is None else environ)
        self.failures = []

    def say(self, text):
        print(text, file=self.out, flush=True)

    def summarize(self, markdown):
        path = self.environ.get("GITHUB_STEP_SUMMARY")
        if path:
            with open(path, "a", encoding="utf-8") as summary:
                summary.write(markdown + "\n")

    def fail(self, problem):
        self.failures.append(problem)
        self.say(f"FAILED: {problem}")
        return False

    def run(self, name, argv):
        self.say(f"==> {name}: {' '.join(argv)}")
        code = self.execute(list(argv), self.root, self.environ)
        return True if code == 0 else self.fail(f"{name} exited {code}")

    def results(self, title, paths):
        results = junit.read(paths)
        for line in junit.log_lines(results):
            self.say(line)
        self.say(f"{title}: {results.tests} tests, {results.broken} failed, {results.skipped} skipped")
        self.summarize(junit.describe(title, results))
        return results

    # Stages. Each returns whether it passed.

    def fast(self):
        failed = [name for name, argv in gates.FAST_CHECKS if not self.run(name, argv)]
        if failed:
            self.summarize("### Fast checks failed: " + ", ".join(f"`{name}`" for name in failed))
        return not failed

    def host_gradle(self, part=None):
        """The whole host gate, or one of the parts CI runs in parallel jobs."""
        if part in (None, gates.FORMAT_PART) and not self.run("ktlint", gates.gradle(*gates.FORMAT_TASKS)):
            self.summarize("### ktlint failed: run `./gradlew ktlintFormat`, then review the diff")
            return False
        ran = self.run(f"gradle-host-{part}" if part else "gradle-host",
                       gates.gradle(*(gates.HOST_PARTS[part] if part else gates.HOST_TASKS)))
        if part not in (None, gates.TESTS_PART):
            return ran
        results = self.results("Host tests", junit.glob(self.root, gates.HOST_RESULTS))
        if results.tests == 0:
            return self.fail("the host gate produced no test results")
        if results.broken:
            return self.fail(f"{results.broken} host tests failed")
        return ran

    def release_checks(self, ci):
        return all(self.run(name, argv) for name, argv in gates.release_checks(ci))  # stops at the first failure

    def device_runs(self, groups, modules):
        """[(label, modules, shard)] to run: hosted groups, or every selected suite whole."""
        if not groups:
            return [("device", tuple(modules or selection.MODULES), ())]
        runs = []
        for name in groups:
            group = gates.DEVICE_GROUPS[name]
            selected = tuple(module for module in group.modules if not modules or module in modules)
            if not selected:
                raise Refusal(f"device group {name} has none of the selected modules {', '.join(modules)}")
            runs.append((name, selected, group.shard))
        return runs

    def filesystem_now(self):
        """Now, on the clock file times use: it can run a tick behind time.time_ns()."""
        marker = self.root / "build" / "device-run-started"
        marker.parent.mkdir(parents=True, exist_ok=True)
        marker.touch()
        return marker.stat().st_mtime_ns

    def device(self, runs, build_only=False, scope="pull-request"):
        built = [module for module in selection.MODULES if any(module in selected for _, selected, _ in runs)]
        if not self.run("device-build", gates.device_build(built)):
            return False
        if build_only:
            return True
        passed = True
        for label, modules, shard in runs:
            started = self.filesystem_now()
            passed = self.run(label, gates.device_tests(modules, shard, scope)) and passed
            for module in modules:
                paths = junit.glob(self.root, gates.DEVICE_RESULTS.format(module=module), newer_than=started)
                results = self.results(f"Device tests: {module}" + (f" (shard {shard[0] + 1} of {shard[1]})" if shard else ""), paths)
                if results.tests == 0:
                    passed = self.fail(f"{module} produced no fresh device test results")
                if results.skipped:
                    passed = self.fail(f"{module} skipped {results.skipped} device tests")
                if results.broken:
                    passed = self.fail(f"{results.broken} {module} device tests failed")
        return passed

    def modules(self, modules, tests=None, lint=False):
        tasks = []
        for module in modules:
            tasks += [f":{module}:ktlintCheck", gates.unit_test_task(module)]
            if tests:
                tasks += ["--tests", tests]
            if lint and module != "domain":  # a plain Kotlin module has no Android lint
                tasks.append(f":{module}:lintDebug")
        ran = self.run("modules", gates.gradle(*tasks))
        paths = [path for module in modules
                 for path in junit.glob(self.root, f"{module}/build/test-results/**/TEST-*.xml")]
        results = self.results("Unit tests", paths)
        return ran and not results.broken


def plan(root, base, event, environ):
    result = selection.plan_for(root, event, base)
    print(json.dumps(result, indent=2))
    if environ.get("GITHUB_OUTPUT"):
        with open(environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            for key in ("gate", "host", "release", "scope"):
                print(f"{key}={str(result[key]).lower() if isinstance(result[key], bool) else result[key]}", file=output)
            print(f"device={'true' if result['groups'] else 'false'}", file=output)
            print(f"groups={json.dumps(result['groups'])}", file=output)
            print(f"modules={','.join(result['modules'])}", file=output)
    if environ.get("GITHUB_STEP_SUMMARY"):
        with open(environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as summary:
            summary.write(selection.summary(result))
    return 0


def parser():
    parser = argparse.ArgumentParser(prog="scripts/check", description=__doc__.split("\n\n")[0],
                                     formatter_class=argparse.RawDescriptionHelpFormatter,
                                     epilog="See docs/development/quality-gates.md.")
    parser.add_argument("module", nargs="*", help="Gradle modules to check quickly, for example domain app")
    parser.add_argument("--tests", metavar="PATTERN", help="With modules: only the matching unit tests")
    parser.add_argument("--lint", action="store_true", help="With modules: Android lint too")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--fast", action="store_true", help="G0: tickets, tool tests, architecture, links")
    mode.add_argument("--host", nargs="?", const="all", choices=["all", *gates.HOST_PARTS], metavar="PART",
                      help="G1: --fast, ktlint, then the Gradle host gate; CI runs one part per job: "
                           f"{', '.join(gates.HOST_PARTS)}")
    mode.add_argument("--device", nargs="*", metavar="GROUP", choices=list(gates.DEVICE_GROUPS),
                      help="Device suites on ANDROID_SERIAL: hosted groups, or every suite whole when none is given")
    mode.add_argument("--release", action="store_true",
                      help="G3 with the owner's production signing and the emulator in ANDROID_SERIAL")
    mode.add_argument("--plan", metavar="BASE", help="Print what a hosted run would verify against BASE")
    parser.add_argument("--event", choices=selection.EVENTS, default="pull_request", help="With --plan")
    parser.add_argument("--ci-release", action="store_true",
                        help=f"With --host or --host {gates.RELEASE_PART}: CI's G3 release checks "
                             "(licences, throwaway signing)")
    parser.add_argument("--modules", metavar="M,M", help="With --device: only these modules (the plan's selection)")
    parser.add_argument("--build-only", action="store_true",
                        help="With --device: ktlint and the test APKs only, before an emulator exists")
    parser.add_argument("--scope", choices=gates.SCOPES,
                        help="With --device: pull-request (the default) leaves out @NightlyOnly tests; "
                             "nightly runs every test")
    return parser


def main(argv=None, root=ROOT, execute=default_execute, out=sys.stdout, environ=None,
         devices=attached_devices, api=device_api):
    arguments = parser().parse_args(argv)
    environ = dict(os.environ if environ is None else environ)
    usage = parser().error
    modes = [arguments.fast, arguments.host is not None, arguments.device is not None, arguments.release,
             arguments.plan is not None]
    if any(modes) and arguments.module:
        usage("give modules or one mode, not both")
    if not any(modes) and not arguments.module:
        usage("give modules, or one of --fast, --host, --device, --release, --plan")
    if (arguments.tests or arguments.lint) and not arguments.module:
        usage("--tests and --lint go with modules")
    if arguments.ci_release and arguments.host not in ("all", gates.RELEASE_PART):
        usage(f"--ci-release goes with --host or --host {gates.RELEASE_PART}, which makes the release APK")
    if (arguments.modules or arguments.build_only or arguments.scope) and arguments.device is None:
        usage("--modules, --build-only and --scope go with --device")
    if arguments.event != "pull_request" and arguments.plan is None:
        usage("--event goes with --plan")
    if arguments.plan is not None:
        return plan(root, arguments.plan, arguments.event, environ)

    check = Check(root, execute, out, environ)
    try:
        if arguments.module:
            unknown = sorted(set(arguments.module) - set(included_modules(root)))
            if unknown:
                raise Refusal(f"unknown modules {', '.join(unknown)}; known: {', '.join(included_modules(root))}")
            passed = check.modules(arguments.module, arguments.tests, arguments.lint)
        elif arguments.fast:
            passed = check.fast()
        elif arguments.host is not None:
            part = None if arguments.host == "all" else arguments.host
            passed = (check.fast() and check.host_gradle(part)
                      and (not arguments.ci_release or check.release_checks(ci=True)))
        elif arguments.device is not None:
            modules = [module for module in (arguments.modules or "").split(",") if module]
            if any(module not in selection.MODULES for module in modules):
                raise Refusal(f"--modules takes {', '.join(selection.MODULES)}")
            runs = check.device_runs(arguments.device, modules)
            if not arguments.build_only:
                check.environ["ANDROID_SERIAL"] = select_device(root, environ, devices, api)
            passed = check.device(runs, arguments.build_only, arguments.scope or "pull-request")
        else:
            check.environ["ANDROID_SERIAL"] = select_device(root, environ, devices, api)
            passed = (check.fast() and check.host_gradle()
                      and check.device(check.device_runs([], []), scope="nightly")
                      and check.release_checks(ci=False))
    except Refusal as error:
        print(f"REFUSED: {error}", file=out)
        return 2
    print("PASSED" if passed else "FAILED: " + "; ".join(check.failures), file=out)
    return 0 if passed else 1
