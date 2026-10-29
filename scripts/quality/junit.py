"""Read JUnit XML results (host and device) and describe failing tests readably."""
import xml.etree.ElementTree as ElementTree
from dataclasses import dataclass, field
from pathlib import Path

MESSAGE_LINES = 6


@dataclass
class Failure:
    test: str  # Class.method
    kind: str  # failure or error
    message: list  # the first lines of the message and stack trace


@dataclass
class Results:
    suites: int = 0
    tests: int = 0
    failures: int = 0
    errors: int = 0
    skipped: int = 0
    failed: list = field(default_factory=list)

    @property
    def broken(self):
        return self.failures + self.errors


def read(paths):
    """Totals and failing tests over every given XML file."""
    results = Results()
    for path in paths:
        root = ElementTree.parse(path).getroot()
        for suite in ([root] if root.tag == "testsuite" else root.iter("testsuite")):
            results.suites += 1
            for name in ("tests", "failures", "errors", "skipped"):
                setattr(results, name, getattr(results, name) + int(suite.get(name, 0) or 0))
            for case in suite.iter("testcase"):
                for kind in ("failure", "error"):
                    for problem in case.findall(kind):
                        text = "\n".join(filter(None, (problem.get("message", ""), problem.text or "")))
                        lines = [line.rstrip() for line in text.strip().splitlines() if line.strip()]
                        results.failed.append(Failure(
                            f"{case.get('classname', suite.get('name', '?'))}.{case.get('name', '?')}",
                            kind, lines[:MESSAGE_LINES]))
    return results


def glob(root, pattern, newer_than=None):
    """XML files under [root] matching [pattern], only those written after [newer_than] (ns) if given."""
    found = sorted(Path(root).glob(pattern))
    return [path for path in found if newer_than is None or path.stat().st_mtime_ns >= newer_than]


def describe(title, results):
    """Markdown for the GitHub job summary; plain enough to read in a log."""
    passed = results.tests - results.broken - results.skipped
    lines = [f"### {title}: {passed} passed, {results.broken} failed, {results.skipped} skipped"]
    if results.failed:
        lines += ["", "| Test | Failure |", "| --- | --- |"]
        for failure in results.failed:
            message = "<br>".join(line.replace("|", "\\|").replace("<", "&lt;") for line in failure.message)
            lines.append(f"| `{failure.test}` | {message or failure.kind} |")
    return "\n".join(lines) + "\n"


def log_lines(results):
    """The failing tests for a plain log."""
    lines = []
    for failure in results.failed:
        lines.append(f"FAILED TEST: {failure.test}")
        lines += [f"    {line}" for line in failure.message]
    return lines
