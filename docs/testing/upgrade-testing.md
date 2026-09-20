# APK-over-APK upgrade testing — design

Install a previously shipped QA APK, put real data in it, upgrade in place to the build under
test, and verify nothing was lost or corrupted.

---

## 1. Why this test exists

Field encryption uses an `AndroidKeyStore` AES-GCM key under alias `symmetricDataKey`
([SecurityModule.kt:50-74](../../app/src/main/java/com/andryoga/safebox/di/SecurityModule.kt#L50-L74)):

```kotlin
if (!keyStore.containsAlias(alias)) {
    keyGenerator.generateKey()
}
```

If that alias is ever lost across an upgrade, the app **silently generates a fresh key**. Nothing
throws. Every record becomes permanently undecryptable, and the user sees a vault full of garbage
with no explanation.

The key does not live in `/data/data`, so it is invisible to every test you currently have.

| Layer | Room migration test | Golden `.bak` test | This test |
|---|---|---|---|
| Room schema + data | yes | no | yes |
| `EncryptedSharedPreferences` | no | no | yes |
| DataStore | no | no | yes |
| WorkManager internal DB | no | no | yes |
| **Keystore alias continuity** | **no** | **no** | **yes** |

---

## 2. Verified ground truth

All checked on 2026-09-20/21 against the real artifacts, not assumed.

| Constraint | Status | How it was verified |
|---|---|---|
| Old QA APKs are archived | **already done** | `release.yml` attaches `SafeBox-qa.apk` to every tag. 15 of 18 releases carry one, back to `v1.3.3.0` |
| Signing certificate is stable | yes | `v2.0.4.0`, `v2.1.4.0-rc3` and a local build all report SHA-256 `257ab204…7b113d` |
| Same `applicationId` | yes | `com.andryoga.safebox.qa` on all |
| `versionCode` is monotonic | yes | 23 → 28 → 9999999 (local) |
| Black-box automation works on the minified APK | yes | `uiautomator dump` returned `Welcome !`, `Password*`, `Sign Up`, `content-desc="Toggle sensitive data visibility"` |
| No app change needed for selectors | yes | production has zero `testTag`; the existing suite already uses text and content description exclusively |
| Runs without Google Play services | yes | ML Kit is the **bundled** `com.google.mlkit:barcode-scanning`, so `aosp-atd` images suffice |

### Hard constraints

Three build types mean three `applicationId`s (`.debug`, `.qa`, none), and Android refuses to
upgrade one into another. A Play Store install also cannot be upgraded by a local build, because
Play re-signs with its own key.

**`qa → qa` is the only vehicle available.** That variant is release-like — `initWith(release)`,
R8 on, resource shrinking on — but it is not byte-identical to the production APK. Close, not same.

---

## 3. Architecture

```mermaid
flowchart TD
    A["1 · Resolve + download baseline APK"] --> B["2 · Install baseline"]
    B --> C["3 · Push .bak fixtures to device"]
    C --> D["4 · Freeze the clock"]
    D --> E["5 · Seed: sign up, restore, edit settings"]
    E --> F["6 · Upgrade in place"]
    F --> G["7 · Assert on the upgraded app"]
    G --> H["8 · Collect logcat + screenshots"]

    classDef host fill:#1b3a5c,stroke:#7fb3ff,color:#eaf2ff
    classDef device fill:#3d2b56,stroke:#c49bff,color:#f4ecff
    class A,B,C,D,F,H host
    class E,G device
```

| | Steps | Runs where |
|---|---|---|
| **Host harness** (bash on the CI runner) | 1, 2, 3, 4, 6, 8 | the runner, over `adb` |
| **On-device driver** (UI Automator) | 5, 7 | inside an instrumentation process on the device |

### Why the work has to be split

Step 6 is `adb install -r`, and it happens **between** two on-device runs. A single
`connectedAndroidTest` invocation cannot upgrade the app underneath itself. So the host owns the
lifecycle and invokes the device driver twice, with a different `-e phase` argument each time.

This is also why Gradle Managed Devices cannot run this job: GMD owns its emulator lifecycle inside
one Gradle task, with no way to interleave an `adb install` in the middle.

### Why a separate Gradle module is required

You asked why this needs a module at all. Two reasons, and the first is not negotiable.

**1. UI Automator only runs on the device.** There is no host-side driver — it is an Android
library (`androidx.test.uiautomator`) that talks to the device's accessibility layer from inside an
instrumentation process. Running it therefore requires an **instrumentation APK**, and the only way
to produce one is a Gradle module. A shell script alone can do `adb shell input tap 540 1200`, but
coordinate-tapping is unmaintainable and cannot read text back to assert on it.

**2. It must not be `:app`'s `androidTest`.** That source set is already bound to
`CustomHiltTestRunner`, pulls in the Hilt test Application, and gets minified alongside the app —
which is exactly what makes it unable to run against a minified build
([ADR-0001](../decisions/0001-instrumentation-tests-run-on-debug-only.md)).

So: a new `com.android.test` module, **self-instrumenting**, with no compile dependency on `:app`.

```groovy
// upgrade-test/build.gradle
plugins { id 'com.android.test'; id 'org.jetbrains.kotlin.android' }

android {
    namespace 'com.andryoga.safebox.upgradetest'
    targetProjectPath = ':app'
    experimentalProperties["android.experimental.self-instrumenting"] = true
    defaultConfig {
        testInstrumentationRunner 'androidx.test.runner.AndroidJUnitRunner'
    }
}

dependencies {
    implementation libs.androidx.test.uiautomator
    implementation libs.androidx.test.ext.junit
}
```

`self-instrumenting` — the same mechanism Macrobenchmark uses — makes the test process target
*itself* rather than the app. Three consequences:

1. No signature match required between test APK and app APK, so this harness can later be pointed
   at a **Play-Store-signed** build.
2. Upgrading the app mid-run does not kill the test process.
3. Zero compile coupling to `:app`, so R8 can never break it.

Build-time cost of the extra module is small: it compiles a handful of Kotlin files and links no
app code.

---

## 4. Golden backup fixtures

The format, for context: a Java-serialized `HashMap<String, ByteArray>` with numeric keys,
payloads PBE-encrypted under a **backup password supplied at export time** (independent of the
vault master password). `BACKUP_VERSION` is currently 3. Full detail in
[persistence-and-crypto.md](../architecture/persistence-and-crypto.md).

**Where they get committed:** `upgrade-test/src/main/assets/fixtures/`. They are a few hundred KB
each, binary, and never edited after creation, so plain git is fine — no LFS needed.

| Fixture | Produced by | Guards |
|---|---|---|
| `v1_legacy.bak` | `v1.3.3.0` QA APK (oldest with an archived APK) | the 1-byte `creationDate` legacy path; a backup with no authenticator key at all |
| `v2_pre_totp.bak` | **`v2.1.4.0-rc3` — capture before TOTP merges** | the format the entire current user base has on disk |
| `v3_current.bak` | this branch | the new format including authenticators |
| `v3_adversarial.bak` | this branch, hand-seeded | emoji + RTL + 4000-char fields, all-optionals-empty record, max-length card number, a `SHA512`/8-digit/60s authenticator, an authenticator with an **invalid Base32 seed**, duplicate titles |
| `corrupt.bak` | `head -c 2048 v3_current.bak` | the `CORRUPT_OR_INVALID_FILE` path, and that a failed restore leaves the vault intact |

All use one fixed backup password committed in the harness. Synthetic data only — never a real
vault.

> [!CAUTION]
> `v2_pre_totp.bak` is the only irreversible item in this whole design. Once TOTP ships, the format
> your current users have on disk can no longer be produced. Capturing it takes about 30 minutes.

---

## 5. Scenarios

### Phase A — establish state on the baseline app

| # | Step | Why |
|---|---|---|
| A1 | install baseline APK, launch | |
| A2 | sign up with master password + hint | creates the password hash **and generates the `symmetricDataKey` alias** |
| A3 | restore a golden `.bak` through the real SAF picker | seeds every record type in one interaction instead of ~50 taps |
| A4 | create one extra record **of each type through the UI** | restore writes via the worker, the UI writes via the repository — two different encryption call sites |
| A5 | set the backup directory and move 2–3 settings off their defaults | a prefs/DataStore migration bug is invisible if every value is still the default |
| A6 | copy a password to the clipboard | enqueues `ClipboardClearWorker`, so WorkManager's DB is non-empty across the upgrade |
| A7 | capture the oracle: per-type counts, every field of one known record per type, current TOTP code | |
| A8 | `adb shell am force-stop` | **never** `pm clear`, **never** `uninstall` |

### Phase B — the upgrade

```bash
adb install -r new.apk     # correct: preserves /data
adb uninstall old          # wrong: this is a fresh-install test in disguise
adb install -r -d new.apk  # wrong: -d permits downgrade and hides a versionCode mistake
```

The harness asserts `newVersionCode > oldVersionCode` **before** installing, and afterwards checks
via `dumpsys package` that `firstInstallTime` is unchanged while `versionCode` changed. That single
check is what stops this decaying into a fresh-install test a year from now.

### Phase C — assertions on the upgraded app

**Group 1 — the app is usable at all**
1. Cold launch does not crash; zero `FATAL EXCEPTION` in logcat since install.
2. Lands on the **unlock** screen, not signup. Catches wiped preferences.
3. The password hint shown matches what was set pre-upgrade. Catches `EncryptedSharedPreferences` loss.

**Group 2 — authentication continuity**
4. The original master password unlocks.
5. A wrong password still fails with the normal error — guards the catastrophic inverse, a hash
   change that makes every password succeed.

**Group 3 — data integrity (the Keystore check, highest value here)**
6. Record count matches exactly, per type, across all five types.
7. Open one known record **of every type** and compare **every field** character-for-character
   against the Phase A oracle. A regenerated key shows up here as mojibake or a decrypt throw.
8. Include one long unicode field and one empty optional field — GCM tag and padding edges.
9. Search a known title and assert the record is found — a different query path from the detail
   screen.

**Group 4 — the migration ran, and ran non-destructively**
10. The add-record sheet offers **Authenticator**, proving `MIGRATION_4_5` created the table and
    Room did not fall back destructively.
11. Add a new authenticator post-upgrade and save it — writes into the freshly migrated table.
12. Re-assert Group 3 counts afterwards.

**Group 5 — TOTP across the boundary**
13. For an authenticator restored from the fixture, assert the six displayed digits equal an
    RFC 6238 value computed **inside the test** with a local Base32 + HMAC-SHA1 helper. Never call
    app code for the expected value.
14. Determinism: `adb root` works on `aosp-atd` (userdebug), so `settings put global auto_time 0`
    then set a fixed instant. Without root, assert against the current **and** previous 30-second
    window.
15. This is the only assertion that proves the **seed itself decrypted correctly**, rather than
    that a row exists.

**Group 6 — the escape hatch still works**
16. Take a fresh backup on the upgraded app; assert a `.bak` appears with a plausible size.
17. `pm clear`, sign up again, restore that new `.bak`.
18. Assert the same counts and field values as step 7. Catches "the upgrade worked but the export
    it now produces is broken", which would quietly destroy the user's only recovery path.

**Group 7 — backward-compat matrix** (same fixtures, separate job)
19. On a **fresh** install of the new app, restore each fixture and assert counts and spot-checked
    fields.
20. For `v3_adversarial.bak`, assert the invalid-Base32 authenticator is **absent** while every
    other record is present — the `filterDecodableAuthenticatorData` contract.

**Group 8 — graceful failure**
21. Restore `corrupt.bak`: expect the `CORRUPT_OR_INVALID_FILE` message, no crash, and the existing
    vault untouched.
22. Restore a valid fixture with the wrong password: expect `INCORRECT_PASSWORD`, vault untouched.

**Group 9 — crash sentinel (global gate)**
23. The job fails if logcat contains any `FATAL EXCEPTION` or `ANR in com.andryoga.safebox.qa`
    between install and teardown.

**Group 10 — downgrade guard**
24. `adb install -r old.apk` after the upgrade must fail. Catches an accidentally lowered
    `versionCode` before Play does.

---

## 6. Assertion mechanics

**Reading a field value.** Compose text surfaces to UI Automator as `text` on a leaf node. Scope to
the field's container and read it:

```kotlin
private fun UiDevice.fieldValue(label: String): String {
    val labelNode = wait(Until.findObject(By.text(label)), TIMEOUT)
        ?: error("field label '$label' not found. Screen:\n${dumpWindowHierarchy()}")
    return labelNode.parent.findObject(By.clazz("android.widget.EditText")).text
}
```

**Masked fields.** Passwords, card numbers and TOTP seeds render as bullets. Tap
`content-desc="Toggle sensitive data visibility"` first — it already exists in the shipped APK.

**Driving the SAF picker** is the flakiest interaction in the design, so isolate it behind one
helper with generous retries. Fixtures must be pushed *and* announced to the media store, or
DocumentsUI may not list them:

```bash
adb push v3_current.bak /sdcard/Download/
adb shell content call --uri content://media/external/file --method scan_volume --arg external
```

The restore picker filters on `application/octet-stream`, `application/x-trash` and
`application/x-binary`
([BackupAndRestoreScreen.kt:188-192](../../app/src/main/java/com/andryoga/safebox/ui/home/backupAndRestore/BackupAndRestoreScreen.kt#L188-L192)),
so a `.bak` in Downloads is selectable.

**Every failure must be self-describing.** Wrap all lookups so a miss dumps the window hierarchy
and the last 200 logcat lines. A CI failure reading `NullPointerException at line 47`, on an
emulator you cannot attach to, is worthless.

---

## 7. Choosing baseline versions — derived, not hardcoded

You objected to hardcoded tags in the matrix. You were right; here is the replacement.

The tag convention is `vMAJOR.MINOR.DBVERSION.FIX`, where the third component **is the Room schema
version**. That makes almost everything derivable from the release list alone. A script
(`scripts/resolve-baselines.sh`) emits the matrix at run time using three rules:

| Rule | Meaning | Resolves to today |
|---|---|---|
| `previous` | newest stable release before the tag being built, that has a `SafeBox-qa.apk` | `v2.0.4.0` |
| `schema-boundary` | for each distinct `DBVERSION` older than the current one, the newest stable release carrying it | `v1.3.3.1` (db 3) |
| `oldest` | the oldest release that still has an archived `SafeBox-qa.apk` | `v1.3.3.0` |

After de-duplication that is **two to three jobs**, and it self-adjusts as you ship — no workflow
edits, ever.

Defaults and why:

- **Stable releases only** by default. Users install from Play, which only serves stable builds, so
  an RC baseline tests a state no real user is in. Override with a flag when an RC is the only
  thing carrying a schema.
- **Not every historical pair.** That is `O(n²)` and buys nothing; a v1.5 → v2.2 upgrade exercises
  the same migration chain as v1.4 → v2.2.
- **One optional human-maintained value:** a floor in `upgrade-test/oldest-supported.txt`, if you
  ever decide you no longer care about installs older than some date. "How far back do we support"
  is a product decision and is the only thing a script cannot infer. Default: no floor.

Of the 18 releases, 15 carry a QA APK — the three that do not (`v1.0.0`, `v1.1.0`, `v1.2.2.0`)
predate the upload step, so `oldest` bottoms out at `v1.3.3.0` automatically.

---

## 8. CI wiring

```yaml
  upgrade_test:
    needs: [ qa_pipeline ]
    if: contains(github.ref_name, 'rc')
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix: ${{ fromJson(needs.resolve_baselines.outputs.matrix) }}
    steps:
      - uses: actions/checkout@v7

      - name: Download the QA APK built in this run
        uses: actions/download-artifact@v8
        with: { name: QA APK, path: new-apk/ }

      - name: Download the baseline QA APK
        env: { GH_TOKEN: '${{ github.token }}' }
        run: ./scripts/fetch-baseline-apk.sh '${{ matrix.from_tag }}' old-apk/

      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules && sudo udevadm trigger --name-match=kvm

      - uses: reactivecircus/android-emulator-runner@v2
        with:
          api-level: 34
          target: aosp_atd
          arch: x86_64
          script: ./scripts/run-upgrade-test.sh old-apk/SafeBox-qa.apk new-apk/SafeBox-qa.apk

      - uses: actions/upload-artifact@v7
        if: always()
        with:
          name: upgrade-test-${{ matrix.from_tag }}
          path: upgrade-test-out/
```

`resolve_baselines` is a tiny preceding job that runs the script from section 7 and emits the
matrix as an output.

### Is `reactivecircus/android-emulator-runner` free?

Yes, twice over:

- The action itself is **Apache-2.0 open source** (1.3k stars, actively maintained). No licence
  cost, no account.
- `Ni3verma/Safe-Box` is a **public** repository, and GitHub-hosted standard runners are free with
  no minute cap for public repos.

The only real cost is wall-clock time. You already depend on the same underlying capability —
`nightly.yml` and `release.yml` both run emulators and already contain the KVM-enabling step — so
nothing new is being introduced infrastructurally.

Expect roughly 8–10 minutes per baseline, running in parallel, so about 10 minutes added to an RC
tag build.

---

## 9. Failure triage

| Symptom | Almost certainly |
|---|---|
| Lands on **signup** instead of unlock | preferences not preserved — check nothing uninstalled |
| Correct password rejected | password hash storage or hashing changed |
| Records present but fields are mojibake, or decrypt throws | **`symmetricDataKey` regenerated — stop the release** |
| Counts are 0 but the app works | destructive Room migration |
| Authenticator missing from the add sheet | `MIGRATION_4_5` did not run |
| TOTP digits wrong, everything else right | check the clock was frozen before suspecting the seed |
| Post-upgrade backup cannot be restored | export regression — the user's recovery path is broken |

---

## 10. Delivery plan, MR by MR

Each MR is independently reviewable and leaves the repository in a working state.

### MR0 — capture the irreversible fixture (no code)

Install `v2.1.4.0-rc3`, create a representative vault, export a backup, commit it as
`v2_pre_totp.bak` along with a short runbook describing exactly what it contains.

- **Must land before the TOTP feature merges.**
- Review size: a binary fixture plus ~40 lines of markdown.
- Acceptance: the `.bak` restores cleanly on `v2.1.4.0-rc3` and the runbook lists every record.

### MR1 — harness skeleton, end to end, no assertions

`upgrade-test` module, `scripts/resolve-baselines.sh`, `scripts/fetch-baseline-apk.sh`,
`scripts/run-upgrade-test.sh`, plus a `workflow_dispatch`-only CI job. One smoke test: launch and
assert the unlock screen appears.

- Proves the hard part — install, seed, upgrade, re-run — before any assertion logic exists.
- Acceptance: a green manual run that genuinely upgrades in place, **and fails loudly if zero tests
  executed**. That guard is mandatory; a silent `tests=0` is what made the previous attempt
  worthless.

### MR2 — fixtures and seeding

Remaining fixtures, the SAF picker helper, and full Phase A automation.

- Acceptance: Phase A reliably produces an identical vault ten runs in a row.

### MR3 — data integrity assertions

Groups 1–3 and the Group 9 crash sentinel. **This is the MR that delivers the actual value** — the
Keystore continuity check.

- Acceptance: passes against `v2.0.4.0`; fails loudly if the alias is deliberately wiped.

### MR4 — migration, TOTP, backup round trip

Groups 4–6, including the independent RFC 6238 computation and clock freezing.

### MR5 — backward-compat and graceful failure

Groups 7–8 as a separate CI job, plus the Group 10 downgrade guard.

### MR6 — enable in the release pipeline

Wire into `release.yml` behind the RC condition, enable the derived matrix, upload artifacts, and
document triage ownership.

- Acceptance: a real RC tag runs it and the result is visible on the PR.

---

## 11. Open decisions

Grouped by the MR they block. Each carries a recommended default, so accepting all defaults is a
valid position.

### Blocks MR0 (time-sensitive)

1. **Capture `v2_pre_totp.bak` now.** Install `v2.1.4.0-rc3`, create records of every type, export,
   and commit the file. Roughly 30 minutes. *Recommendation: do it before the TOTP branch merges —
   it is unreproducible afterwards.*
2. **Fixture credentials.** The vault master password and the backup password are committed in
   plain text. *Recommendation: `Upgrade@Test1` and `Fixture@Backup1`.*
3. **How rich the baseline vault should be.** *Recommendation: 3 records per type, one with every
   optional field populated and one with only mandatory fields.*

### Blocks MR1

4. **Is a new Gradle module acceptable?** It adds `:upgrade-test` to every `./gradlew tasks` and a
   small configuration cost. *Recommendation: yes — section 3 explains why there is no alternative.*
5. **Module name.** *Recommendation: `upgrade-test`. Alternative if you expect to add release-build
   smoke tests later: `e2e-blackbox`, since the same module would host both.*
6. **API levels to test on.** Upgrade behaviour can differ on older platforms, and your `minSdk` is
   24. *Recommendation: start with API 34 only; add API 24 in MR5 if the runtime budget allows.*

### Blocks MR6

7. **RC tags only, or production tags too?** *Recommendation: both. Production is the last gate
   before real users, and it costs ten minutes.*
8. **What happens when it fails?** Block the release, or report and let a human decide?
   *Recommendation: block on Groups 1–3 and 9 (data loss and crashes), report-only on the rest
   until the flakiness profile is known.*
9. **Who triages a red run?** An unowned flaky job gets ignored within a month, and then deleted.

### Not blocking, but worth deciding

10. **Should there be a fresh-install control arm?** Running the same assertions on a clean install
    distinguishes "the upgrade broke it" from "it is broken generally". *Recommendation: yes, it is
    nearly free once the harness exists.*
11. **`oldest-supported` floor.** *Recommendation: none for now; revisit if the oldest baseline
    starts costing more than it catches.*
12. **Fixture location** — `upgrade-test/src/main/assets/fixtures/` as proposed, or GitHub Release
    assets. *Recommendation: in-repo. They are small, and a test fixture that can disappear from
    under CI is not a fixture.*
