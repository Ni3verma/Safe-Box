#!/usr/bin/env python3
"""Validates the committed documentation set.

Three checks, all cheap and all guarding defects that have actually happened:

1. **Stray tool markup.** Files authored by an AI agent can end up with the agent harness's own
   XML-ish wrappers (`</CodeContent>`, `<parameter name="...">`) appended after the real content.
   It renders as garbage and is easy to miss in review.
2. **Absolute link targets.** A link beginning with `/` or `file://` depends on one machine's
   filesystem layout. Left to `pathlib` these resolve against the *filesystem* root, so
   `/docs/x.md` is reported missing while `/etc/hosts` silently passes — a false pass in a link
   checker is worse than a false failure, so these are rejected as their own category.
3. **Broken relative links.** Cross-references are how this documentation set stays navigable, and
   they rot silently whenever a file is renamed or moved.

Run from the repository root:

    python3 scripts/check_docs.py

Exits non-zero on the first category of failure found, so it is safe to wire into CI or a
pre-commit hook.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent

# Directories whose markdown is part of the curated knowledge base.
SCAN_DIRS = (".agents", "docs", "upgrade-test")

STRAY_MARKUP = (
    "</CodeContent>",
    "<CodeContent>",
    '<parameter name="',
    "</parameter>",
    "<ArtifactMetadata>",
)

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
EXTERNAL = ("http://", "https://", "mailto:", "#")

# Targets that are neither external nor usefully relative. Left to pathlib these silently resolve
# against the *filesystem* root: "/docs/x.md" becomes "/docs/x.md", and "/etc/hosts" would resolve
# to a real file and pass. A committed doc must not depend on a machine's absolute layout, so these
# are rejected outright with an explanation rather than link-checked.
ABSOLUTE = ("/", "file://")


def read_markdown(path):
    """Reads a markdown file as UTF-8 regardless of the ambient locale.

    Encoding is pinned rather than left to the platform default: these documents contain em
    dashes, arrows and comparison operators, so under a C/POSIX locale the default encoding
    raises UnicodeDecodeError and takes out the pre-commit hook and the CI step along with it.

    @param path: Path to the markdown file.
    @return: File contents as str.
    """
    return path.read_text(encoding="utf-8")


def markdown_files():
    for directory in SCAN_DIRS:
        root = ROOT / directory
        # A listed directory may legitimately be absent, for example before the upgrade-test
        # module is created. That is not a documentation failure.
        if not root.is_dir():
            continue
        yield from sorted(root.rglob("*.md"))


def check_stray_markup(files):
    failures = []
    for path in files:
        for number, line in enumerate(read_markdown(path).splitlines(), start=1):
            if any(marker in line for marker in STRAY_MARKUP):
                failures.append((path, number, line.strip()[:80]))
    return failures


def check_links(files):
    """Resolves every non-external link target relative to the file containing it.

    @param files: Markdown paths to inspect.
    @return: Two lists - (path, target) for absolute targets, and for targets that do not exist.
    """
    absolute = []
    missing = []
    for path in files:
        for target in LINK.findall(read_markdown(path)):
            if target.startswith(EXTERNAL):
                continue
            if target.startswith(ABSOLUTE):
                absolute.append((path, target))
                continue
            resolved = (path.parent / target.split("#")[0]).resolve()
            if not resolved.exists():
                missing.append((path, target))
    return absolute, missing


def main():
    files = list(markdown_files())
    if not files:
        print("no markdown found - is this being run from the repository root?")
        return 1

    markup = check_stray_markup(files)
    absolute, missing = check_links(files)

    for path, number, snippet in markup:
        print(f"STRAY MARKUP  {path.relative_to(ROOT)}:{number}  {snippet}")
    for path, target in absolute:
        print(
            f"ABSOLUTE LINK {path.relative_to(ROOT)} -> {target}\n"
            f"              use a path relative to the file instead; an absolute target depends "
            f"on one machine's layout"
        )
    for path, target in missing:
        print(f"BROKEN LINK   {path.relative_to(ROOT)} -> {target}")

    total = len(markup) + len(absolute) + len(missing)
    print(f"\nchecked {len(files)} markdown files, {total} problem(s)")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
