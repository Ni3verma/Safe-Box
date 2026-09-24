# ADR-0002 — Upgrade and release-build testing via a black-box UI Automator module

- **Status:** Accepted. **Skeleton implemented** 2026-09-22 (MR1); assertions land in MR2–MR6
- **Date:** 2026-09-20, implementation status updated 2026-09-22
- **Supersedes:** nothing. **Depends on:** [ADR-0001](0001-instrumentation-tests-run-on-debug-only.md)

## Decision

Release-build confidence and upgrade confidence come from a **separate `com.android.test` module,
self-instrumenting, driving the app through UI Automator**, with zero compile-time dependency on
`:app`.

Design detail lives in [docs/testing/upgrade-testing.md](../testing/upgrade-testing.md); operating
it is in [upgrade-harness-operations.md](../testing/upgrade-harness-operations.md).

> [!NOTE]
> The two structural claims below are no longer predictions. Verified 2026-09-22 on the shipped
> module: `:upgrade-test:assembleDebug` executes **no `:app` task at all**, and the merged manifest
> sets `targetPackage` to the harness's own package, so the app is genuinely not the instrumentation
> target. An in-place `adb install -r` between two phases does not disturb the harness.

## Why this shape

**Zero coupling to `:app` means R8 can never break it.** That is the direct lesson of ADR-0001.

**Self-instrumenting** (`android.experimental.self-instrumenting = true`, the mechanism
Macrobenchmark uses) means the test process targets itself rather than the app. Consequences:

- no signature match required between test APK and app APK, so the same harness can later be
  pointed at a **Play-Store-signed** build;
- upgrading the app mid-run does not kill the test process.

**A host harness owns the lifecycle.** `adb install -r` happens *between* two instrumentation runs,
so a single `connectedAndroidTest` invocation cannot do it. The host script installs, pushes
fixtures, controls the clock and collects logcat; the on-device module runs assertions. This also
rules out Gradle Managed Devices for the upgrade job, since GMD owns its emulator inside one task.

## Alternatives rejected

| Option | Why not |
|---|---|
| Keep rules so the existing Hilt suite runs on `qa` | ADR-0001 — collapses into disabling minification |
| Split source sets, non-Hilt Espresso on `qa` | still couples the test APK to app internals; every keep rule erodes what is being validated; large one-time move; cannot test an already-installed or older APK |
| Maestro as the primary driver | see below |
| `/data/data` snapshot instead of driving the UI | the Keystore key is **not** in `/data/data`, so a restored snapshot cannot decrypt anything — it would test nothing and look like it passed |

## UI Automator over Maestro

Maestro is genuinely better at authoring speed and has excellent implicit waiting, which is the
main flake-killer. It loses on the thing that matters here: this is a **data-integrity** suite. Its
value is concentrated in assertions like "the decrypted card number is exactly `4111 1111 1111 1111`"
and "these six digits equal the RFC 6238 code I computed independently". Maestro's `assertVisible`
cannot capture a string into a variable and compare it later, and computing an HMAC in a
`runScript` block is awkward.

Maestro remains a good candidate for **broad smoke flows** later ("can a new user sign up, add a
login, and find it in search?"), where "does the screen appear" is the whole assertion.

## Enabling facts

Verified 2026-09-20, all of which make this cheaper than expected:

- QA APKs are **already archived** as GitHub Release assets on every tag, back to `v2.0.4.0`.
- The QA signing certificate has been **stable** across that whole range.
- `versionCode` is monotonic (23 → 28 → 9999999 local).
- Black-box automation **already works** against the installed minified QA APK — a
  `uiautomator dump` returned `Welcome !`, `Password*`, `Sign Up`, and
  `content-desc="Toggle sensitive data visibility"`.
- Production has no `testTag`, so `testTagsAsResourceId` is unnecessary; text and content
  description selectors are sufficient and already used by the existing suite. *Amended
  2026-09-24:* the text-less settings switches and sliders now carry tags, exposed as resource ids
  in `debug`/`qa` only (`ui/core/TestTags.kt`). The harness can use them only once the oldest
  supported baseline is a release that contains them; until then it keeps matching by geometry.

## Consequences

- A new Gradle module and a host script to maintain.
- Tests are selector-based, so user-visible copy changes can break them. *Amended by
  [ADR-0003](0003-ui-labels-from-resource-names.md) (MR3):* the app's own labels are now resolved
  from each installed build's string resources, so a copy edit no longer breaks them — a resource
  **rename** does, and fails naming the missing resource. System UI (DocumentsUI's `Show roots`,
  the permission dialog's `ALLOW`) is not the app's and is still matched by literal text.
- Runs at RC-tag / nightly cadence, not per PR (~10 min wall clock for three version pairs).
- Golden `.bak` fixtures must be produced and committed, including one from the **currently shipped
  app before the TOTP feature merges** — that format becomes unreproducible afterwards.
