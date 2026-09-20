# ADR-0001 — Instrumentation tests run on the `debug` build only

- **Status:** Accepted
- **Date:** 2026-09-20
- **Context:** an attempt to run the existing suite against the minified `qa` APK

## Decision

The `app/src/androidTest` suite runs against `debug`. It will **not** be made to run against the
minified `qa` build. Release-build confidence comes from a separate black-box suite instead
([ADR-0002](0002-upgrade-testing-via-black-box-uiautomator.md)).

## Why — do not re-investigate this

The intuition "surely a keep rule fixes it" is wrong, and the evidence is concrete.

### Symptom

Running the suite against `qa` produced **`BUILD SUCCESSFUL` with `tests="0"`** — a silent false
pass. Underneath, the app process was crash-looping:

```
INSTRUMENTATION_RESULT: shortMsg=Process crashed.

java.lang.NoClassDefFoundError: Failed resolution of: Lcom/andryoga/safebox/HiltTestApp_Application;
	at com.andryoga.safebox.CustomHiltTestRunner.newApplication(...)
	at android.app.LoadedApk.makeApplicationInner(LoadedApk.java:1471)
Caused by: java.lang.ClassNotFoundException: com.andryoga.safebox.HiltTestApp_Application
```

It dies in `handleBindApplication`, before any test method is reached.

### First domino

`HiltTestApp_Application` **is** present in the installed test APK (confirmed by `dexdump` on the
APK pulled off the device). What fails is resolving what it points at:

`BaseTestApplication` implements `androidx.work.Configuration.Provider`, and R8 **removed that
interface from the app APK**:

| Check | Result |
|---|---|
| `Landroidx/work/Configuration$Provider;` in app `classes.dex` | absent |
| `androidx.work.Configuration$Provider` in app `mapping.txt` | absent |
| `androidx.work.Configuration$Provider` in app `usage.txt` | **present** — R8 deleted it |
| `MainApplication` interfaces in the shipped APK | only `Lpj1;` (Hilt) |

This is correct, safe R8 behaviour for the app: it proved `MainApplication` is the only implementor
and rewrote WorkManager's `instanceof` accordingly. **Production is fine.** It is fatal only for
the test APK, which was compiled against the unminified classes.

### Why keep rules cannot fix it

`-applymapping` can only rename references to classes R8 *kept*. Classes R8 *deleted* have no
mapping entry, so the test APK keeps the original name and it dangles at runtime.

Parsing the test APK's raw DEX `type_ids` table and subtracting everything resolvable (test dex +
app dex + boot classpath) gives:

```
test dex references 4321 types; 383 unresolvable
```

| Package | Count |
|---|---|
| `com/andryoga` | **112** |
| `androidx/compose` | 62 |
| `com/google` | 52 |
| `dagger/hilt` | 43 |
| `kotlinx/coroutines` | 35 |
| other | 79 |

The 112 app classes are the **entire Dagger graph**, which Hilt regenerates from scratch inside the
test APK — `CacheModule_ProvideSafeBoxAppDbFactory`, `*_HiltModules$BindsModule`,
`RestoreDataWorker_AssistedFactory`, and so on. Dagger plumbing is precisely what R8 shreds best;
the shipping APK retains almost none of it.

Making the suite run would therefore require, **in the app's own rules**:

```proguard
-keep class com.andryoga.safebox.** { *; }
-keep class dagger.hilt.** { *; }
-keep class androidx.compose.** { *; }
-keep class kotlinx.coroutines.** { *; }
```

At that point the "minified" APK is unshrunk and unobfuscated for every package that matters — a
debug build wearing a release signature. It would validate nothing, and it would stay broken
forever, since every new `@Inject` adds a fresh dangling reference discovered only as a runtime
crash.

## Consequences

- No automated UI coverage of the minified artifact until the black-box suite exists. Recorded as a
  gap in [testing-strategy.md](../testing/testing-strategy.md).
- `testBuildType` stays `debug`. A `-PtestBuildType=qa` switch was prototyped and **reverted**,
  because it shipped a `tests=0` false pass — a green CI job that runs zero tests is worse than no
  job at all.
- If anyone reintroduces such a switch, it must fail the build when the executed test count is zero.

## Notes

This is not a Safe-Box-specific problem. No reference Android app runs a Hilt instrumentation suite
against a minified build; the standard pattern — Now in Android, Macrobenchmark — is Hilt tests on
`debug` plus a separate black-box module for the release artifact.
