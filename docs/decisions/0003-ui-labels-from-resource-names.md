# 3. UI labels are resolved from the app's own resource names

Date: 2026-09-23

Status: Accepted

## Context

The harness reads the app through its UI, so every assertion depends on text that product work is
free to change. A rename of a visible label — `User Id` to `Email`, say — is a normal, harmless
change that a data-integrity test has no business failing on.

It is worse than an annoying failure, though, because of what the upgrade test compares. Phase A
captures an oracle from the **old** app and a later phase captures it again from the **new** one;
the two are diffed to prove the upgrade preserved the vault. If the oracle is keyed on displayed
text, then any cosmetic difference between the two releases — a renamed label, a changed separator,
a translation — is indistinguishable from data loss. The pressure at that point is to loosen the
comparison until it passes, and a loosened comparison is how the previous attempt at upgrade
testing became worthless.

The competing demand is that a field genuinely *disappearing* must fail. No mechanism can
distinguish "renamed deliberately" from "lost accidentally" by looking at the screen, so the
question is only where the cost falls.

## Decision

**The oracle is keyed by Android resource name, and visible labels are resolved from the resources
of the APK currently installed.**

The harness holds `user_id`, not `"User Id"`. At runtime it asks the installed app for that
resource and gets whatever that build renders, then locates the label on screen and reads the value
beneath it. Phase A resolves against the baseline APK, the post-upgrade phase against the build
under test — the same name, resolved twice, against two different binaries.

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

| Change a developer makes | Upgrade test | Why that is the right answer |
|---|---|---|
| Edits the *text* of `user_id` to "Email" | Passes, no test edit | Presentation changed, data did not. The harness was never looking at presentation. |
| Renames the resource `user_id` to `email` | Fails; one mapping line fixes it | A deliberate refactor, cheaply recorded where the next reader will see it |
| Stops rendering the field | Fails | A real regression: the data became unreachable |
| Changes how the value is formatted | Fails | Requires a human to decide: intended, or corruption? |

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

- **Phase A's oracle must be re-keyed.** MR2 shipped it keyed on visible labels
  (`field.ui login.User Id=ui-user`); it becomes `field.ui login.user_id=ui-user`. This changes the
  oracle's bytes, so MR2's ten-run acceptance has to be re-established. Tracked as a carried-forward
  debt row against MR3 in [upgrade-testing.md](../testing/upgrade-testing.md#carried-forward-debt).
- **This does not apply to controls with no text.** The settings switches have no label, no content
  description and no id, and are still matched by geometry — the switch whose vertical extent
  overlaps its label. Resource names cannot help there; a `testTag` exposed through
  `testTagsAsResourceId` would, and is deliberately left out of this decision because it changes
  production code.
- **The baseline can never be re-keyed to anything newer.** Resource names work for the baseline
  only because `user_id` already existed in `v2.0.4.0`. A name introduced after the floor cannot be
  resolved against it, and the harness must fall back to a literal for such fields.
- Locale stops mattering: a CI image that boots non-English resolves the same names.
