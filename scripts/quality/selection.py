"""What a hosted run verifies: the gate and the device groups, from the event and the changed paths.

Documents (Markdown anywhere, recognized content under docs/) → G0, Python/Git only.
Tooling under scripts/ → host checks only, unless the device job runs it.
Module sources select the device suites of that module's consumers. Build files,
workflows, lockfiles, gradle/ and unknown paths select every device suite.
"""
import re
import subprocess

# Modules with a device suite, in run order.
MODULES = ("data", "design-system", "app", "catalog")
# A module's main sources reach the device suites of every module that consumes it.
DEPENDENTS = {
    "domain": ("data", "app"),
    "data": ("data", "app"),
    "design-system": ("design-system", "app", "catalog"),
    "app": ("app",),
    "catalog": ("catalog",),
}
# Hosted device groups and the modules each runs; gates.DEVICE_GROUPS holds the shards.
GROUPS = {"app-1": ("app",), "app-2": ("app",), "support": ("data", "design-system", "catalog")}
DOCUMENT_SUFFIXES = (".md", ".txt", ".html", ".css", ".svg", ".png", ".jpg", ".jpeg", ".webp", ".csv", ".json")
# The scripts a device job executes: a change to them is tested on the device jobs too.
DEVICE_SCRIPTS = (
    "scripts/check", "scripts/quality/check.py", "scripts/quality/gates.py",
    "scripts/quality/junit.py", "scripts/quality/selection.py",
)
EVENTS = ("pull_request", "push", "schedule", "workflow_dispatch")


def is_document(path):
    return path.endswith(".md") or (path.startswith("docs/") and path.endswith(DOCUMENT_SUFFIXES))


def device_modules(path):
    """The device suites a non-document path can affect; () for host-only paths."""
    if path in DEVICE_SCRIPTS:
        return MODULES
    if path.startswith("scripts/"):
        return ()
    module, _, relative = path.partition("/")
    if module not in DEPENDENTS:
        return MODULES
    if relative.startswith("src/"):
        source_set = relative.split("/")[1] if relative.count("/") >= 2 else ""
        if re.fullmatch(r"test(Debug|Release)?", source_set):
            return ()  # JVM unit tests never reach a device APK
        if re.fullmatch(r"androidTest(Debug|Release)?", source_set):
            return (module,) if module in MODULES else MODULES  # only this module's test APK
        return DEPENDENTS[module]
    if module == "data" and relative.startswith("schemas/"):
        return DEPENDENTS[module]
    return MODULES  # build scripts, lockfiles and anything else in a module


def groups(modules):
    return [group for group, covered in GROUPS.items() if any(module in modules for module in covered)]


def plan(event, paths, base=""):
    """The gate, selected modules and device groups for one hosted run."""
    if event not in EVENTS:
        raise ValueError(f"Unknown CI event {event!r}")
    paths = sorted(set(paths))
    code = [path for path in paths if not is_document(path)]
    if event in ("schedule", "workflow_dispatch"):
        gate, modules = "G3", list(MODULES)  # the whole of main, with CI's throwaway signing
    elif not code:
        gate, modules = "G0", []
    elif event == "push":
        gate, modules = "G1", []  # the pull request ran the device suites; main publishes caches
    else:
        impacted = {module for path in code for module in device_modules(path)}
        modules = [module for module in MODULES if module in impacted]
        gate = "G2" if modules else "G1"
    return {
        "event": event, "base": base, "gate": gate, "host": gate != "G0",
        "release": gate == "G3", "modules": modules, "groups": groups(modules),
        "changed": {path: ("document" if is_document(path) else ",".join(device_modules(path)) or "host")
                    for path in paths},
    }


def changed_paths(root, base):
    """Every path that differs from [base], committed or not, with both ends of a move."""
    def git(*arguments):
        return subprocess.run(["git", "-C", str(root), *arguments], check=True, capture_output=True, text=True).stdout

    committed = git("diff", "--name-only", "--no-renames", "-z", base, "--")
    untracked = git("ls-files", "--others", "--exclude-standard", "-z")
    return sorted(set(filter(None, (committed + untracked).split("\0"))))


def resolve(root, base):
    """The commit [base] names, or None when it is unknown here."""
    result = subprocess.run(["git", "-C", str(root), "rev-parse", "--verify", "--quiet", f"{base}^{{commit}}"],
                            capture_output=True, text=True)
    return result.stdout.strip() if result.returncode == 0 else None


def plan_for(root, event, base):
    """Plan from the repository; an unknown base selects everything rather than nothing."""
    resolved = resolve(root, base)
    if resolved is None:
        result = plan(event, ["<unknown base>"], base)
        result["changed"] = {}
        return result
    return plan(event, changed_paths(root, resolved), resolved)


def summary(result):
    """A short Markdown description of the plan for the job summary."""
    lines = [f"### Plan: {result['gate']} ({result['event']})", "",
             f"- Host checks: {'yes' if result['host'] else 'no (Python/Git checks only)'}",
             f"- Device groups: {', '.join(result['groups']) or 'none'}"
             + (f" (modules: {', '.join(result['modules'])})" if result["modules"] else ""),
             f"- Release checks with a throwaway key: {'yes' if result['release'] else 'no'}"]
    changed = result["changed"]
    if changed:
        lines += ["", f"<details><summary>{len(changed)} changed paths</summary>", "",
                  "| Path | Selects |", "| --- | --- |"]
        lines += [f"| `{path}` | {kind} |" for path, kind in list(changed.items())[:200]]
        if len(changed) > 200:
            lines.append(f"| … {len(changed) - 200} more | |")
        lines += ["", "</details>"]
    return "\n".join(lines) + "\n"
