#!/usr/bin/env python3
"""Tests for check_docs.py.

Uses stdlib unittest deliberately: this repository has no Python dependency management, and the
checker runs in three places that must all work without a virtualenv - a developer's shell, the
pre-commit hook, and the CI step. A test suite that needed `pip install` would be the only part of
the pipeline that did.

Every defect this file guards was found by code review rather than by the checker failing, which is
the reason the suite exists at all:

- reading files with the platform default encoding (breaks under a C/POSIX locale),
- absolute link targets resolving against the filesystem root, which could report a real file as
  valid and silently pass,
- stray agent tool markup reaching a committed file.

Run from the repository root:

    python3 -m unittest discover -s scripts -p 'test_*.py'
"""
import importlib.util
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).resolve().parent / "check_docs.py"


def load_check_docs():
    """Imports check_docs.py by path.

    Loading by path rather than by name keeps the suite independent of the working directory and of
    sys.path, so it behaves the same from the repository root, from scripts/, and under CI.

    @return: The imported check_docs module.
    """
    spec = importlib.util.spec_from_file_location("check_docs", SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


check_docs = load_check_docs()


class LinkCheckTest(unittest.TestCase):
    """Covers check_links, which classifies targets as external, absolute, missing or fine."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)

    def write(self, name, body):
        """Writes a markdown file into the temp tree.

        @param name: File name relative to the temp directory.
        @param body: File contents.
        @return: Path to the written file.
        """
        path = self.tmp / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding="utf-8")
        return path

    def test_relative_link_to_existing_file_passes(self):
        self.write("target.md", "# target")
        source = self.write("source.md", "see [target](./target.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([], missing)

    def test_relative_link_to_missing_file_is_reported_broken(self):
        source = self.write("source.md", "see [gone](./gone.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([(source, "./gone.md")], missing)

    def test_link_with_anchor_resolves_to_the_file_not_the_anchor(self):
        self.write("target.md", "# target")
        source = self.write("source.md", "see [target](./target.md#some-heading)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], missing)

    def test_parent_directory_link_resolves(self):
        self.write("target.md", "# target")
        source = self.write("nested/source.md", "see [target](../target.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], missing)

    def test_root_relative_link_is_reported_absolute(self):
        source = self.write("source.md", "see [doc](/docs/architecture/persistence-and-crypto.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, "/docs/architecture/persistence-and-crypto.md")], absolute)
        self.assertEqual([], missing)

    def test_absolute_link_to_a_file_that_exists_is_still_reported(self):
        """The regression that matters: an absolute target must never pass by existing on disk.

        Before absolute targets were classified separately, pathlib discarded the containing
        directory and resolved the target against the filesystem root. A path that happened to
        exist there reported no problem at all, which is a false pass rather than a false failure.
        """
        real = self.write("real.md", "# real")
        source = self.write("source.md", "see [real](%s)" % real)

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, str(real))], absolute)
        self.assertEqual([], missing)

    def test_file_scheme_link_is_reported_absolute(self):
        target = "file:///Users/someone/Safe-Box/docs/README.md"
        source = self.write("source.md", "see [doc](%s)" % target)

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, target)], absolute)

    def test_external_links_are_skipped(self):
        source = self.write(
            "source.md",
            "[a](https://example.com/x.md) [b](http://example.com) "
            "[c](mailto:someone@example.com) [d](#local-anchor)",
        )

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([], missing)

    def test_reference_style_definition_to_missing_file_is_reported(self):
        """Reference links bypassed every check until their definitions were extracted."""
        source = self.write("source.md", "see [guide][docs]\n\n[docs]: ./gone.md\n")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, "./gone.md")], missing)

    def test_reference_style_definition_with_absolute_target_is_reported(self):
        source = self.write("source.md", "see [guide][docs]\n\n[docs]: /etc/hosts\n")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, "/etc/hosts")], absolute)

    def test_reference_style_definition_to_existing_file_passes(self):
        self.write("target.md", "# target")
        source = self.write("source.md", "see [guide][docs]\n\n[docs]: ./target.md\n")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([], missing)

    def test_angle_bracketed_reference_destination_is_unwrapped(self):
        self.write("target.md", "# target")
        source = self.write("source.md", "see [guide][docs]\n\n[docs]: <./target.md>\n")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], missing)

    def test_external_reference_destination_is_skipped(self):
        source = self.write("source.md", "see [spec][cm]\n\n[cm]: https://spec.commonmark.org\n")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([], missing)

    def test_windows_drive_target_is_reported_absolute(self):
        source = self.write("source.md", "see [doc](C:/docs/a.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, "C:/docs/a.md")], absolute)

    def test_windows_unc_target_is_reported_absolute(self):
        target = "\\\\server\\share\\a.md"
        source = self.write("source.md", "see [doc](%s)" % target)

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([(source, target)], absolute)

    def test_colon_in_a_relative_name_is_not_mistaken_for_a_drive(self):
        """A Windows drive is a single letter, so `notes:draft.md` stays relative."""
        source = self.write("source.md", "see [doc](./notes:draft.md)")

        absolute, missing = check_docs.check_links([source])

        self.assertEqual([], absolute)
        self.assertEqual([(source, "./notes:draft.md")], missing)


class StrayMarkupTest(unittest.TestCase):
    """Covers check_stray_markup, which catches agent harness wrappers left in a file."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)

    def write(self, name, body):
        path = self.tmp / name
        path.write_text(body, encoding="utf-8")
        return path

    def test_clean_file_reports_nothing(self):
        source = self.write("clean.md", "# title\n\nordinary prose.\n")

        self.assertEqual([], check_docs.check_stray_markup([source]))

    def test_closing_code_content_tag_is_reported_with_line_number(self):
        source = self.write("dirty.md", "# title\n\nprose\n</CodeContent>\n")

        failures = check_docs.check_stray_markup([source])

        self.assertEqual(1, len(failures))
        path, number, snippet = failures[0]
        self.assertEqual(source, path)
        self.assertEqual(4, number)
        self.assertIn("</CodeContent>", snippet)

    def test_every_known_marker_is_detected(self):
        for marker in check_docs.STRAY_MARKUP:
            with self.subTest(marker=marker):
                source = self.write("m.md", "prose\n%s\n" % marker)
                self.assertEqual(1, len(check_docs.check_stray_markup([source])))


class MarkdownDiscoveryTest(unittest.TestCase):
    """Covers markdown_files, which walks the curated directories."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        self.addCleanup(setattr, check_docs, "ROOT", check_docs.ROOT)
        self.addCleanup(setattr, check_docs, "SCAN_DIRS", check_docs.SCAN_DIRS)
        check_docs.ROOT = self.tmp

    def test_absent_directory_is_skipped_rather_than_failing(self):
        """upgrade-test is listed before the module exists, which must not be an error."""
        (self.tmp / "docs").mkdir()
        (self.tmp / "docs" / "a.md").write_text("# a", encoding="utf-8")
        check_docs.SCAN_DIRS = ("docs", "does-not-exist")

        found = list(check_docs.markdown_files())

        self.assertEqual([self.tmp / "docs" / "a.md"], found)

    def test_nested_markdown_is_discovered(self):
        (self.tmp / "docs" / "deep").mkdir(parents=True)
        (self.tmp / "docs" / "deep" / "b.md").write_text("# b", encoding="utf-8")
        check_docs.SCAN_DIRS = ("docs",)

        found = list(check_docs.markdown_files())

        self.assertEqual([self.tmp / "docs" / "deep" / "b.md"], found)

    def test_non_markdown_is_ignored(self):
        (self.tmp / "docs").mkdir()
        (self.tmp / "docs" / "notes.txt").write_text("not markdown", encoding="utf-8")
        check_docs.SCAN_DIRS = ("docs",)

        self.assertEqual([], list(check_docs.markdown_files()))


class EndToEndTest(unittest.TestCase):
    """Runs the script as the hook and CI run it: as a subprocess, against a materialised tree."""

    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, ignore_errors=True)
        (self.tmp / "scripts").mkdir()
        shutil.copy(str(SCRIPT), str(self.tmp / "scripts" / "check_docs.py"))
        (self.tmp / "docs").mkdir()

    def run_checker(self, env=None):
        """Invokes the copied script with its repository root at the temp tree.

        @param env: Optional environment overrides merged over the current environment.
        @return: The completed process.
        """
        environment = dict(os.environ)
        if env:
            environment.update(env)
        return subprocess.run(
            [sys.executable, "scripts/check_docs.py"],
            cwd=str(self.tmp),
            env=environment,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            universal_newlines=True,
        )

    def test_non_ascii_documents_pass_under_the_c_locale(self):
        """The regression for the encoding fix.

        The committed documentation contains em dashes, arrows and comparison operators. With the
        encoding left to the platform default, a C/POSIX locale raised UnicodeDecodeError and took
        out the pre-commit hook and the CI step with it.

        PYTHONCOERCECLOCALE and PYTHONUTF8 must both be disabled or this test is decorative:
        PEP 538 coerces a C locale to C.UTF-8 and PEP 540 can force UTF-8 regardless, either of
        which keeps the default encoding at UTF-8 and lets an unpinned read_text() pass anyway.
        Verified by removing the pin and watching this fail.
        """
        (self.tmp / "docs" / "unicode.md").write_text(
            "# title\n\nem dash \u2014 arrow \u2192 at least \u2265 five\n",
            encoding="utf-8",
        )

        result = self.run_checker({
            "LC_ALL": "C",
            "LANG": "C",
            "LC_CTYPE": "C",
            "PYTHONCOERCECLOCALE": "0",
            "PYTHONUTF8": "0",
            "PYTHONIOENCODING": "utf-8",
        })

        self.assertEqual(0, result.returncode, result.stdout)
        self.assertIn("0 problem(s)", result.stdout)

    def test_broken_link_makes_the_script_exit_non_zero(self):
        (self.tmp / "docs" / "a.md").write_text("[gone](./gone.md)", encoding="utf-8")

        result = self.run_checker()

        self.assertEqual(1, result.returncode)
        self.assertIn("BROKEN LINK", result.stdout)

    def test_absolute_link_makes_the_script_exit_non_zero(self):
        (self.tmp / "docs" / "a.md").write_text("[doc](/docs/a.md)", encoding="utf-8")

        result = self.run_checker()

        self.assertEqual(1, result.returncode)
        self.assertIn("ABSOLUTE LINK", result.stdout)

    def test_empty_tree_is_treated_as_a_misconfiguration(self):
        """No markdown at all almost certainly means it was run from the wrong directory."""
        result = self.run_checker()

        self.assertEqual(1, result.returncode)
        self.assertIn("no markdown found", result.stdout)


if __name__ == "__main__":
    unittest.main()
