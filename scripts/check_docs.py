#!/usr/bin/env python3
"""Validates the committed documentation set.

Two checks, both cheap and both guarding defects that have actually happened:

1. **Stray tool markup.** Files authored by an AI agent can end up with the agent harness's own
   XML-ish wrappers (`</CodeContent>`, `<parameter name="...">`) appended after the real content.
   It renders as garbage and is easy to miss in review.
2. **Broken relative links.** Cross-references are how this documentation set stays navigable, and
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
SCAN_DIRS = (".agents", "docs")

STRAY_MARKUP = (
    "</CodeContent>",
    "<CodeContent>",
    '<parameter name="',
    "</parameter>",
    "<ArtifactMetadata>",
)

LINK = re.compile(r"\[[^\]]*\]\(([^)\s]+)\)")
EXTERNAL = ("http://", "https://", "mailto:", "#")


def markdown_files():
    for directory in SCAN_DIRS:
        yield from sorted((ROOT / directory).rglob("*.md"))


def check_stray_markup(files):
    failures = []
    for path in files:
        for number, line in enumerate(path.read_text().splitlines(), start=1):
            if any(marker in line for marker in STRAY_MARKUP):
                failures.append((path, number, line.strip()[:80]))
    return failures


def check_links(files):
    failures = []
    for path in files:
        for target in LINK.findall(path.read_text()):
            if target.startswith(EXTERNAL):
                continue
            resolved = (path.parent / target.split("#")[0]).resolve()
            if not resolved.exists():
                failures.append((path, target))
    return failures


def main():
    files = list(markdown_files())
    if not files:
        print("no markdown found - is this being run from the repository root?")
        return 1

    markup = check_stray_markup(files)
    links = check_links(files)

    for path, number, snippet in markup:
        print(f"STRAY MARKUP  {path.relative_to(ROOT)}:{number}  {snippet}")
    for path, target in links:
        print(f"BROKEN LINK   {path.relative_to(ROOT)} -> {target}")

    total = len(markup) + len(links)
    print(f"\nchecked {len(files)} markdown files, {total} problem(s)")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main())
