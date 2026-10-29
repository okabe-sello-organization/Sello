#!/usr/bin/env python3
"""Architecture rules from ARCHITECTURE.md section 2, checked on source text.

Rules
  module-edge             a module's build file names a project dependency outside the allowed graph
  unknown-module          settings include a module that has no rule here
  module-boundary         a module's sources reference another Sello module they may not use
  domain-pure             :domain references Android, UI, storage, DI or HTTP libraries
  data-composition-only   :app references :data outside the composition package and directory
  composition-is-root     an :app feature references the composition package
  feature-isolation       an :app feature references another feature's package
  debug-tools-in-release  developer tools are declared or referenced outside debug source sets
  ad-hoc-time             wall clock, device zone or uptime is read outside :app's platform package
  global-coroutines       GlobalScope anywhere, or a global dispatcher named outside the platform package
  di-composition-only     Koin is used outside :app's composition directory and debug developer tools

Coverage: every .kt/.java file under <module>/src/*/{java,kotlin}, matched on any
mention of a package in code, so plain, aliased and wildcard imports and fully
qualified references are all caught. Comments and string literals are ignored.

Limits: this is a text check, not a compiler. It does not see references built by
reflection or inside string templates, a type re-exported through an allowed
package (for example a typealias in a shared presentation package), or project
dependencies added by anything other than a literal project(":name") in a module's
own build file. Test source sets of :app are exempt from the composition-only,
composition-is-root and feature-isolation rules, and test source sets of every
module from the time, coroutine and Koin rules. The time rule matches the common
JDK and Android entry points listed in AD_HOC_TIME; a clock obtained another way
(for example through a third-party library) is not seen. Gradle's own classpaths
still enforce the module edges at compile time.
"""
import re
import sys
from dataclasses import dataclass
from pathlib import Path

NAMESPACE = "com.software.sello"
# Top-level package segment owned by each library module; everything else belongs to :app.
OWNERS = {"domain": "domain", "data": "data", "designsystem": "design-system"}
ALLOWED_EDGES = {
    "domain": set(),
    "data": {"domain"},
    "design-system": set(),
    "app": {"domain", "design-system", "data"},
}
FORBIDDEN_IN_DOMAIN = re.compile(
    r"(?<![\w.])(android|androidx|com\.google\.android|org\.koin|javax\.inject|dagger|io\.ktor|okhttp3|retrofit2)\."
)
SELLO_REFERENCE = re.compile(rf"(?<![\w.]){re.escape(NAMESPACE)}\.(\w+)(?:\.(\w+))?")
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
PROJECT_EDGE = re.compile(r'project\(\s*(?:path\s*=\s*)?":([\w-]+)"\s*\)')
PROJECT_ACCESSOR = re.compile(r"(?<![\w.])projects\.\w+")
DEBUG_SOURCE_SETS = {"debug", "testDebug", "androidTest", "androidTestDebug"}
AD_HOC_TIME = re.compile(
    r"(?<![\w.])(?:"
    r"(?:Instant|LocalDate|LocalDateTime|LocalTime|ZonedDateTime|OffsetDateTime|YearMonth|Year)\.now\s*\("
    r"|Clock\.system\w*|Clock\.System\b|System\.(?:currentTimeMillis|nanoTime)\b|SystemClock\."
    r"|ZoneId\.systemDefault\b|TimeZone\.(?:getDefault|currentSystemDefault)\b|Calendar\.getInstance\b|Date\s*\(\s*\)"
    r")"
)
QUALIFIED_TIME = re.compile(r"(?<![\w.])(?:java\.time|java\.util|android\.os|kotlinx\.datetime)\.(?=[A-Z])")
GLOBAL_SCOPE = re.compile(r"(?<![\w])GlobalScope\b")
GLOBAL_DISPATCHER = re.compile(r"(?<![\w])Dispatchers\.(?:Main|Default|IO|Unconfined)\b")
KOIN = re.compile(r"(?<![\w.])org\.koin\.")


@dataclass(frozen=True)
class Violation:
    path: str
    line: int
    rule: str
    message: str

    def __str__(self):
        return f"{self.path}:{self.line}: [{self.rule}] {self.message}"


def code_only(text, keep_strings=False):
    """Blank out comments (and string literals) while keeping every line break."""
    output, index, length = [], 0, len(text)

    def blank(chunk):
        return "".join(character if character == "\n" else " " for character in chunk)

    while index < length:
        if text.startswith("//", index):
            end = text.find("\n", index)
            end = length if end == -1 else end
            output.append(blank(text[index:end]))
        elif text.startswith("/*", index):
            depth, end = 1, index + 2
            while end < length and depth:
                if text.startswith("/*", end):
                    depth, end = depth + 1, end + 2
                elif text.startswith("*/", end):
                    depth, end = depth - 1, end + 2
                else:
                    end += 1
            output.append(blank(text[index:end]))
        elif text.startswith('"""', index):
            end = text.find('"""', index + 3)
            end = length if end == -1 else end + 3
            output.append(text[index:end] if keep_strings else blank(text[index:end]))
        elif text[index] in "\"'":
            quote, end = text[index], index + 1
            while end < length and text[end] not in (quote, "\n"):
                end += 2 if text[end] == "\\" else 1
            end = min(end + 1, length)
            output.append(text[index:end] if keep_strings else blank(text[index:end]))
        else:
            output.append(text[index])
            end = index + 1
        index = end
    return "".join(output)


def line_of(text, offset):
    return text.count("\n", 0, offset) + 1


def source_violations(module, source_set, relative, text):
    code = code_only(text)
    declared = PACKAGE.search(code)
    package = declared.group(1) if declared else ""
    package_end = declared.end() if declared else 0
    is_test = source_set.startswith(("test", "androidTest"))
    found = []

    def report(offset, rule, message):
        found.append(Violation(relative, line_of(code, offset), rule, message))

    in_platform = module == "app" and package.startswith(f"{NAMESPACE}.platform") and "/com/software/sello/platform/" in relative
    in_composition_directory = module == "app" and "/com/software/sello/composition/" in relative
    in_debug_tools = module == "app" and package.startswith(f"{NAMESPACE}.devtools") and source_set in DEBUG_SOURCE_SETS
    if not is_test:
        # Fully qualified calls (java.time.LocalDate.now()) are matched after dropping the qualifier.
        unqualified = QUALIFIED_TIME.sub(lambda match: " " * len(match.group(0)), code)
        if not in_platform:
            for match in AD_HOC_TIME.finditer(unqualified, package_end):
                report(match.start(), "ad-hoc-time", f"read time through an injected clock, not {match.group(0).strip('( ')}")
            for match in GLOBAL_DISPATCHER.finditer(code, package_end):
                report(match.start(), "global-coroutines", f"inject DispatcherProvider instead of naming {match.group(0)}")
        for match in GLOBAL_SCOPE.finditer(code, package_end):
            report(match.start(), "global-coroutines", "GlobalScope has no owner; launch in an injected scope")
        if not (in_composition_directory or in_debug_tools) and module != "domain":
            for match in KOIN.finditer(code, package_end):
                report(match.start(), "di-composition-only", "Koin belongs in the composition root; pass collaborators through constructors")
    if module == "domain":
        for match in FORBIDDEN_IN_DOMAIN.finditer(code, package_end):
            report(match.start(), "domain-pure", f":domain must stay platform-neutral but references {match.group(1)}.*")
    if module == "app" and package.startswith(f"{NAMESPACE}.devtools") and source_set not in DEBUG_SOURCE_SETS:
        report(declared.start(1), "debug-tools-in-release", f"developer tools are declared in source set '{source_set}', not a debug one")
    feature = re.match(rf"{re.escape(NAMESPACE)}\.feature\.(\w+)", package)
    in_composition = package.startswith(f"{NAMESPACE}.composition") and "/com/software/sello/composition/" in relative
    for match in SELLO_REFERENCE.finditer(code, package_end):
        segment, child = match.groups()
        owner = OWNERS.get(segment, "app")
        if module != "app":
            if owner != module and owner not in ALLOWED_EDGES[module]:
                report(match.start(), "module-boundary", f":{module} must not reference :{owner} ({match.group(0)})")
            continue
        if owner == "data" and not is_test and not in_composition:
            report(match.start(), "data-composition-only", ":data may only be referenced from the composition package and directory")
        elif segment == "devtools" and source_set not in DEBUG_SOURCE_SETS:
            report(match.start(), "debug-tools-in-release", f"source set '{source_set}' references developer tools")
        elif segment == "composition" and feature and not is_test:
            report(match.start(), "composition-is-root", "features must not reference the composition root")
        elif segment == "feature" and feature and child and child != feature.group(1) and not is_test:
            report(match.start(), "feature-isolation", f"feature '{feature.group(1)}' must not reference feature '{child}'")
    return found


def build_violations(module, relative, text):
    code = code_only(text, keep_strings=True)
    found = []
    for match in PROJECT_EDGE.finditer(code):
        if match.group(1) not in ALLOWED_EDGES[module]:
            found.append(Violation(relative, line_of(code, match.start()), "module-edge", f":{module} must not depend on :{match.group(1)}"))
    for match in PROJECT_ACCESSOR.finditer(code):
        found.append(Violation(relative, line_of(code, match.start()), "module-edge", 'use project(":name") so the dependency can be checked'))
    return found


def inspect(root):
    """Return (violations, source file count, build file count)."""
    root = Path(root)
    found, sources, builds = [], 0, 0
    settings = root / "settings.gradle.kts"
    if settings.is_file():
        code = code_only(settings.read_text(encoding="utf-8"), keep_strings=True)
        for include in re.finditer(r"include\(([^)]*)\)", code):
            for name in re.finditer(r'":([\w:-]+)"', include.group(1)):
                if name.group(1) not in ALLOWED_EDGES:
                    line = line_of(code, include.start(1) + name.start())
                    found.append(Violation("settings.gradle.kts", line, "unknown-module", f":{name.group(1)} has no architecture rule; add one deliberately"))
    for module in sorted(ALLOWED_EDGES):
        for name in ("build.gradle.kts", "build.gradle"):
            build = root / module / name
            if build.is_file():
                builds += 1
                found.extend(build_violations(module, f"{module}/{name}", build.read_text(encoding="utf-8")))
        for path in sorted((root / module / "src").glob("*/*/**/*")):
            parts = path.relative_to(root / module / "src").parts
            if path.is_file() and parts[1] in ("java", "kotlin") and path.suffix in (".kt", ".java"):
                sources += 1
                relative = path.relative_to(root).as_posix()
                found.extend(source_violations(module, parts[0], relative, path.read_text(encoding="utf-8")))
    return found, sources, builds


def violations(root):
    return inspect(root)[0]


def main():
    root = Path(__file__).resolve().parents[2]
    found, sources, builds = inspect(root)
    for violation in found:
        print(violation, file=sys.stderr)
    if found:
        print(f"Architecture check failed: {len(found)} violation(s)", file=sys.stderr)
        return 1
    print(f"PASS: architecture rules, {sources} source files, {builds} build files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
