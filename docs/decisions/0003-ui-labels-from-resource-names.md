# 3. UI labels are resolved from the app's own resource names

Date: 2026-09-23, consequences updated 2026-09-26

Status: Accepted. Amended by [ADR-0004](0004-verify-upgrade-and-restore-by-decoded-backup.md)

## Context

The harness reads the app through its UI, so every lookup depends on text that product work is
free to change. A rename of a visible label — `User Id` to `Email`, say — is a normal, harmless
change that a data-integrity test has no business failing on.

When this was decided, it was worse than an annoying failure: the upgrade test compared a
description of the vault read off the **old** app's screens with one read off the **new** app's.
Keyed on displayed text, any cosmetic difference between two releases was indistinguishable from
data loss, and the pressure was to loosen the comparison until it passed. ADR-0004 has since moved
the data comparison off the screen entirely (decoded backups), but the harness still has to *find*
labels, buttons and messages on two different builds.

The competing demand is that a field genuinely *disappearing* must fail. No mechanism can
distinguish "renamed deliberately" from "lost accidentally" by looking at the screen, so the
question is only where the cost falls.

## Decision

**Labels are held as Android resource names and resolved against the resources of the APK
currently installed.**

The harness holds `user_id`, not `"User Id"`. At runtime it asks the installed app for that
resource and gets whatever that build renders. Steps on the previous release resolve against it,
steps after the upgrade against the build under test — the same name, resolved twice, against two
different binaries.

Resolution uses `PackageManager.getResourcesForApplication(...)` plus
`Resources.getIdentifier(name, "string", packageName)`. This needs no compile dependency on `:app`,
so [ADR-0002](0002-upgrade-testing-via-black-box-uiautomator.md)'s black-box rule is intact: the
harness reads the shipped artifact, not the project.

Three rules follow:

1. **Resolve by name, never by numeric id.** Ids are not stable across releases (evidence below).
2. **Normalise resolved text before matching** — trim at minimum. Resource text and rendered text
   are not the same string (evidence below).
3. **When a resource is renamed or removed, the harness needs an explicit mapping entry**, added in
   the same change as the rename.

## Why, and what it costs

| Change a developer makes | Harness | Why that is the right answer |
|---|---|---|
| Edits the *text* of `user_id` to "Email" | Passes, no test edit | Presentation changed, data did not |
| Renames the resource `user_id` to `email` | Fails; one mapping line fixes it | A deliberate refactor, cheaply recorded where the next reader will see it |
| Removes a screen element the harness uses | Fails | Requires a human to decide |

The cost is a one-line mapping on resource renames. The alternative — keying on visible text —
charges the same cost for every copy edit, which is what trains people to weaken the test.

## Evidence

Resource **names survive** the qa build's minification. The `qa` build type is
`initWith(buildTypes["release"])`, so it carries `minifyEnabled true` and `shrinkResources true`;
names are nonetheless present in both APKs:

```
$ aapt2 dump resources old-apk/SafeBox-qa.apk | grep string/user_id
    resource 0x7f0f0116 string/user_id          # baseline v2.0.4.0

$ aapt2 dump resources app/build/outputs/apk/qa/SafeBox-qa.apk | grep string/user_id
    resource 0x7f0f013b string/user_id          # current build
```

The same output is why rule 1 exists: the name is identical across the two releases and **the
numeric id is not** — `0x7f0f0116` against `0x7f0f013b`. Anything that resolved an id once and
reused it across the upgrade would read a different string, or none.

Rule 2 comes from the label in question:

```
$ git show v2.0.4.0:app/src/main/res/values/strings.xml | grep '"user_id"'
    <string name="user_id">User Id </string>
```

The resource carries a trailing space; the accessibility tree exposes `User Id`. An exact match on
the resolved string fails.

## Consequences

- **Controls with no text are out of scope.** The settings switches have no label, no content
  description and no id, so resource names cannot help. They carry test tags since 2026-09-24
  (`ui/core/TestTags.kt`, exposed in `debug`/`qa` only), but the previous release predates them,
  so the harness still matches them by geometry — the switch whose vertical extent overlaps its
  label — until a release containing the tags becomes N-1 (follow-ups in
  [upgrade-testing.md](../testing/upgrade-testing.md#9-risks-limits-and-follow-ups)).
- **Structure is out of scope too.** Reading a records row as one unit needs a tag, not a label;
  see ADR-0004 for which elements are tagged and why tags are used on the build under test only.
- **A name must exist in the previous release** to be resolved there. A resource introduced later
  can only be used in steps that run on the build under test.
- Locale stops mattering: a CI image that boots non-English resolves the same names.
