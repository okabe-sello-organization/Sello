"""Failing tests must be readable in the log and the GitHub job summary."""
import os
import tempfile
import time
import unittest
from pathlib import Path

import junit

HOST = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="com.software.sello.domain.MoneyTest" tests="3" skipped="0" failures="1" errors="0">
  <testcase name="adds" classname="com.software.sello.domain.MoneyTest" time="0.01"/>
  <testcase name="overflow is typed" classname="com.software.sello.domain.MoneyTest" time="0.01">
    <failure message="expected:&lt;Overflow&gt; but was:&lt;Ok(-1)&gt;" type="java.lang.AssertionError">java.lang.AssertionError: expected:&lt;Overflow&gt; but was:&lt;Ok(-1)&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
	at org.junit.Assert.assertEquals(Assert.java:146)
	at com.software.sello.domain.MoneyTest.overflow(MoneyTest.kt:42)
	at java.base/java.lang.Thread.run(Thread.java:1583)
</failure>
  </testcase>
  <testcase name="subtracts" classname="com.software.sello.domain.MoneyTest" time="0.01"/>
</testsuite>
"""
DEVICE = """<?xml version='1.0' encoding='UTF-8' ?>
<testsuites>
  <testsuite name="com.software.sello.data.StoreTest" tests="2" failures="0" errors="1" skipped="1">
    <testcase name="keeps | pipes" classname="com.software.sello.data.StoreTest">
      <error>java.lang.IllegalStateException: closed</error>
    </testcase>
    <testcase name="ignored" classname="com.software.sello.data.StoreTest"><skipped/></testcase>
  </testsuite>
</testsuites>
"""


class JunitTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)

    def write(self, relative, text):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
        return path

    def test_host_and_device_results_are_counted_and_failures_named(self):
        results = junit.read([self.write("a/TEST-host.xml", HOST), self.write("b/TEST-device.xml", DEVICE)])
        self.assertEqual((2, 5, 1, 1, 1, 2), (results.suites, results.tests, results.failures, results.errors,
                                              results.skipped, results.broken))
        self.assertEqual(["com.software.sello.domain.MoneyTest.overflow is typed", "com.software.sello.data.StoreTest.keeps | pipes"],
                         [failure.test for failure in results.failed])
        first = results.failed[0]
        self.assertEqual("failure", first.kind)
        self.assertEqual("expected:<Overflow> but was:<Ok(-1)>", first.message[0])
        self.assertEqual(junit.MESSAGE_LINES, len(first.message))
        self.assertEqual(["java.lang.IllegalStateException: closed"], results.failed[1].message)

    def test_the_summary_is_a_markdown_table_that_survives_pipes_and_angle_brackets(self):
        results = junit.read([self.write("TEST-host.xml", HOST), self.write("TEST-device.xml", DEVICE)])
        text = junit.describe("Host tests", results)
        self.assertTrue(text.startswith("### Host tests: 2 passed, 2 failed, 1 skipped\n"))
        self.assertIn("| `com.software.sello.domain.MoneyTest.overflow is typed` | expected:&lt;Overflow> but was:&lt;Ok(-1)>", text)
        self.assertIn("`com.software.sello.data.StoreTest.keeps | pipes`", text)
        self.assertIn("java.lang.IllegalStateException: closed |", text)
        self.assertEqual(4, len([line for line in text.splitlines() if line.startswith("|")]))

    def test_the_log_lists_each_failing_test_with_its_first_lines(self):
        results = junit.read([self.write("TEST-host.xml", HOST)])
        lines = junit.log_lines(results)
        self.assertEqual("FAILED TEST: com.software.sello.domain.MoneyTest.overflow is typed", lines[0])
        self.assertEqual("    expected:<Overflow> but was:<Ok(-1)>", lines[1])

    def test_passing_results_have_no_table(self):
        results = junit.read([self.write("TEST-ok.xml", '<testsuite name="s" tests="2" failures="0" errors="0" skipped="0">'
                                                         '<testcase name="a" classname="C"/><testcase name="b" classname="C"/></testsuite>')])
        self.assertEqual("### Unit tests: 2 passed, 0 failed, 0 skipped\n", junit.describe("Unit tests", results))
        self.assertEqual([], junit.log_lines(results))

    def test_only_results_written_after_the_start_are_fresh(self):
        stale = self.write("app/build/outputs/androidTest-results/connected/debug/TEST-old.xml", DEVICE)
        old = time.time_ns() - 10_000_000_000
        os.utime(stale, ns=(old, old))
        started = time.time_ns() - 1_000_000_000
        pattern = "app/build/outputs/androidTest-results/connected/**/TEST-*.xml"
        self.assertEqual([], junit.glob(self.root, pattern, newer_than=started))
        fresh = self.write("app/build/outputs/androidTest-results/connected/debug/TEST-new.xml", DEVICE)
        self.assertEqual([fresh], junit.glob(self.root, pattern, newer_than=started))
        self.assertEqual([fresh, stale], junit.glob(self.root, pattern))


if __name__ == "__main__":
    unittest.main()
